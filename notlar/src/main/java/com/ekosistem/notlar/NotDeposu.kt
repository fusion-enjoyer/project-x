package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

data class Not(
    val uri: Uri,
    val ad: String,
    val baslik: String,
    val ozet: String,
    val degistirilme: Long,
    val sabit: Boolean,
    val klasor: String? = null
)

/**
 * Notlar düz Markdown dosyası olarak saklanır: kullanıcı klasör seçtiyse SAF
 * üzerinden o klasörde, seçmediyse uygulamanın kendi deposunda. Alt klasörler
 * uygulamada "klasör" olarak görünür; çöp kutusu gizli ".trash" klasörüdür.
 */
class NotDeposu(private val context: Context) {

    private val tr: Locale = Locale.forLanguageTag("tr-TR")

    fun kok(): DocumentFile {
        val uriStr = Prefs.klasorUri(context)
        if (uriStr != null) {
            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                if (doc != null && doc.canWrite()) return doc
            } catch (_: Exception) {
            }
        }
        val dir = File(context.filesDir, "notlar")
        if (!dir.exists()) dir.mkdirs()
        return DocumentFile.fromFile(dir)
    }

    private fun copKlasoru(olustur: Boolean): DocumentFile? {
        val k = kok()
        val mevcut = k.findFile(".trash")
        if (mevcut != null && mevcut.isDirectory) return mevcut
        return if (olustur) k.createDirectory(".trash") else null
    }

    fun klasorAdlari(): List<String> =
        kok().listFiles()
            .filter { it.isDirectory && !(it.name ?: ".").startsWith(".") }
            .mapNotNull { it.name }
            .sortedWith(compareBy(java.text.Collator.getInstance(tr)) { it })

    fun klasorBul(ad: String): DocumentFile? =
        kok().listFiles().firstOrNull { it.isDirectory && it.name == ad }

    fun klasorOlustur(ad: String): Boolean {
        val temiz = ad.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").take(40)
        if (temiz.isEmpty() || temiz.startsWith(".")) return false
        if (klasorBul(temiz) != null) return true
        return kok().createDirectory(temiz) != null
    }

    fun notlariListele(sorgu: String?, klasorAdi: String? = null): List<Not> {
        val baslangic = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        val temizSorgu = sorgu?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(tr)
        topla(baslangic, sonuc, Prefs.sabitler(context), temizSorgu, null)
        return sonuc.sortedWith(
            compareByDescending<Not> { it.sabit }.thenByDescending { it.degistirilme }
        )
    }

    private fun topla(
        dir: DocumentFile,
        sonuc: MutableList<Not>,
        sabitler: Set<String>,
        sorgu: String?,
        etiket: String?
    ) {
        for (f in dir.listFiles()) {
            val ad = f.name ?: continue
            if (f.isDirectory) {
                if (!ad.startsWith(".")) topla(f, sonuc, sabitler, sorgu, etiket ?: ad)
                continue
            }
            if (!notDosyasi(ad)) continue
            val icerik = oku(f.uri, 4096)
            if (sorgu != null && !(ad + "\n" + icerik).lowercase(tr).contains(sorgu)) continue
            sonuc.add(notYap(f, icerik, sabitler, etiket))
        }
    }

    fun copListele(): List<Not> {
        val cop = copKlasoru(false) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        for (f in cop.listFiles()) {
            val ad = f.name ?: continue
            if (!f.isFile || !notDosyasi(ad)) continue
            sonuc.add(notYap(f, oku(f.uri, 1024), emptySet(), null))
        }
        return sonuc.sortedByDescending { it.degistirilme }
    }

    private fun notDosyasi(ad: String): Boolean =
        ad.endsWith(".md", true) || ad.endsWith(".txt", true)

    private fun notYap(
        f: DocumentFile,
        icerik: String,
        sabitler: Set<String>,
        klasor: String?
    ): Not {
        val satirlar = icerik.lines()
        val ilkIndex = satirlar.indexOfFirst { it.isNotBlank() }
        val ilk = if (ilkIndex >= 0) mdTemizle(satirlar[ilkIndex]) else ""
        val dosyaAdi = f.name ?: "not"
        val baslik = ilk.ifBlank { dosyaAdi.removeSuffix(".md").removeSuffix(".txt") }
        val ozet = if (ilkIndex >= 0) {
            satirlar.drop(ilkIndex + 1)
                .filter { it.isNotBlank() }
                .joinToString(" ") { mdTemizle(it) }
                .take(150)
        } else ""
        return Not(
            uri = f.uri,
            ad = dosyaAdi,
            baslik = baslik.take(80),
            ozet = ozet,
            degistirilme = f.lastModified(),
            sabit = sabitler.contains(f.uri.toString()),
            klasor = klasor
        )
    }

    private fun mdTemizle(satir: String): String =
        satir.trim()
            .trimStart('#', '>', ' ')
            .removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- ")
            .replace("**", "").replace("__", "")
            .trim()

    fun oku(uri: Uri, limit: Int = Int.MAX_VALUE): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { akis ->
                val bytes = if (limit == Int.MAX_VALUE) {
                    akis.readBytes()
                } else {
                    val tampon = ByteArray(limit)
                    var toplam = 0
                    while (toplam < limit) {
                        val n = akis.read(tampon, toplam, limit - toplam)
                        if (n < 0) break
                        toplam += n
                    }
                    tampon.copyOf(toplam)
                }
                String(bytes, Charsets.UTF_8)
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun yaz(uri: Uri, metin: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(metin.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun notOlustur(icerik: String, klasorAdi: String? = null): Uri? {
        val hedef = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: kok()
        return dosyaOlustur(hedef, icerik)
    }

    private fun dosyaOlustur(hedef: DocumentFile, icerik: String, istenenAd: String? = null): Uri? {
        var ad = istenenAd ?: icerik.lines().firstOrNull { it.isNotBlank() }
            ?.let { mdTemizle(it) }
            ?.take(40)
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "")
            ?.trim()
            ?: ""
        if (ad.isBlank()) ad = "not"
        ad = ad.removeSuffix(".md").removeSuffix(".txt")
        var tekilAd = ad
        var i = 2
        while (hedef.findFile("$tekilAd.md") != null || hedef.findFile(tekilAd) != null) {
            tekilAd = "$ad-$i"
            i++
        }
        val f = hedef.createFile("text/markdown", tekilAd) ?: return null
        val sonAd = f.name ?: tekilAd
        if (!sonAd.endsWith(".md", true) && !sonAd.endsWith(".txt", true)) {
            f.renameTo("$tekilAd.md")
        }
        return if (yaz(f.uri, icerik)) f.uri else null
    }

    fun docGetir(uri: Uri): DocumentFile? =
        if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }

    private fun hedefeTasi(uri: Uri, hedef: DocumentFile): Boolean {
        val f = docGetir(uri) ?: return false
        val icerik = oku(uri)
        val ad = (f.name ?: "not.md").removeSuffix(".md").removeSuffix(".txt")
        if (dosyaOlustur(hedef, icerik, ad) == null) return false
        sabitTemizle(uri)
        return f.delete()
    }

    fun copeTasi(uri: Uri): Boolean {
        val cop = copKlasoru(true) ?: return false
        return hedefeTasi(uri, cop)
    }

    fun geriYukle(uri: Uri): Boolean = hedefeTasi(uri, kok())

    fun klasoreTasi(uri: Uri, klasorAdi: String?): Boolean {
        val hedef = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return false
        return hedefeTasi(uri, hedef)
    }

    fun kaliciSil(uri: Uri): Boolean {
        sabitTemizle(uri)
        return docGetir(uri)?.delete() ?: false
    }

    private fun sabitTemizle(uri: Uri) {
        val id = uri.toString()
        if (Prefs.sabitler(context).contains(id)) Prefs.sabitDegistir(context, id)
    }
}
