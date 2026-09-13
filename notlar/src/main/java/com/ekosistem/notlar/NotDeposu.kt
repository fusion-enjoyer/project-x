package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.Collator
import java.util.Locale

data class Not(
    val uri: Uri,
    val ad: String,
    val baslik: String,
    val ozet: String,
    val degistirilme: Long,
    val sabit: Boolean,
    val klasor: String? = null,
    val eslesme: String? = null
)

/** Bir notun içindeki tek bir görev satırı. */
data class Gorev(
    val notUri: Uri,
    val notBasligi: String,
    val satirNo: Int,
    val metin: String,
    val isaretli: Boolean
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

    // --- Klasörler ---

    fun klasorAdlari(): List<String> =
        kok().listFiles()
            .filter { it.isDirectory && !(it.name ?: ".").startsWith(".") }
            .mapNotNull { it.name }
            .sortedWith(compareBy(Collator.getInstance(tr)) { it })

    fun klasorBul(ad: String): DocumentFile? =
        kok().listFiles().firstOrNull { it.isDirectory && it.name == ad }

    fun klasorOlustur(ad: String): Boolean {
        val temiz = adTemizle(ad)
        if (temiz.isEmpty()) return false
        if (klasorBul(temiz) != null) return true
        return kok().createDirectory(temiz) != null
    }

    fun klasorYenidenAdlandir(eski: String, yeni: String): Boolean {
        val temiz = adTemizle(yeni)
        if (temiz.isEmpty() || temiz == eski) return false
        val klasor = klasorBul(eski) ?: return false
        return try {
            klasor.renameTo(temiz)
        } catch (_: Exception) {
            false
        }
    }

    /** Klasörü siler; içindeki notlar ana klasöre taşınır. */
    fun klasorSil(ad: String): Boolean {
        val klasor = klasorBul(ad) ?: return false
        val hedef = kok()
        for (f in klasor.listFiles()) {
            val dosyaAdi = f.name ?: continue
            if (f.isFile && notDosyasi(dosyaAdi)) {
                hedefeTasi(f.uri, hedef)
            }
        }
        return try {
            klasor.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun adTemizle(ad: String): String =
        ad.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").take(40).let {
            if (it.startsWith(".")) "" else it
        }

    // --- Listeleme ---

    fun notlariListele(sorgu: String?, klasorAdi: String? = null): List<Not> {
        val baslangic = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        val temizSorgu = sorgu?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(tr)
        // Klasör içindeyken de notun klasörü bilinsin ki taşıma geri alınabilsin.
        topla(baslangic, sonuc, Prefs.sabitler(context), temizSorgu, klasorAdi)
        return sirala(sonuc)
    }

    private fun sirala(notlar: List<Not>): List<Not> {
        val collator = Collator.getInstance(tr)
        val karsilastirici = when (Prefs.siralama(context)) {
            1 -> compareBy<Not> { it.degistirilme }
            2 -> Comparator<Not> { a, b -> collator.compare(a.baslik, b.baslik) }
            3 -> Comparator<Not> { a, b -> collator.compare(b.baslik, a.baslik) }
            else -> compareByDescending { it.degistirilme }
        }
        return notlar.sortedWith(compareByDescending<Not> { it.sabit }.then(karsilastirici))
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
            val icerik = oku(f.uri, 8192)
            var eslesmeSatiri: String? = null
            if (sorgu != null) {
                if (!(ad + "\n" + icerik).lowercase(tr).contains(sorgu)) continue
                eslesmeSatiri = icerik.lines()
                    .firstOrNull { it.lowercase(tr).contains(sorgu) }
                    ?.let { mdTemizle(it) }
                    ?.takeIf { it.isNotBlank() }
            }
            sonuc.add(notYap(f, icerik, sabitler, etiket, eslesmeSatiri))
        }
    }

    fun copListele(): List<Not> {
        val cop = copKlasoru(false) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        for (f in cop.listFiles()) {
            val ad = f.name ?: continue
            if (!f.isFile || !notDosyasi(ad)) continue
            sonuc.add(notYap(f, oku(f.uri, 1024), emptySet(), null, null))
        }
        return sonuc.sortedByDescending { it.degistirilme }
    }

    /** Tüm notlardaki onay kutusu satırlarını toplar. */
    fun gorevleriListele(tamamlananlar: Boolean): List<Gorev> {
        val sonuc = mutableListOf<Gorev>()
        for (not in notlariListele(null, null)) {
            val satirlar = oku(not.uri).lines()
            satirlar.forEachIndexed { indeks, satir ->
                val eslesme = MarkdownBicimci.ONAY.find(satir) ?: return@forEachIndexed
                val isaretli = !eslesme.groupValues[2].equals(" ", true)
                if (isaretli && !tamamlananlar) return@forEachIndexed
                val metin = satir.substring(eslesme.value.length).trim()
                if (metin.isEmpty()) return@forEachIndexed
                sonuc.add(Gorev(not.uri, not.baslik, indeks, metin, isaretli))
            }
        }
        return sonuc
    }

    /** Bir görev satırının işaretini değiştirir. */
    fun gorevDegistir(gorev: Gorev): Boolean {
        val satirlar = oku(gorev.notUri).lines().toMutableList()
        if (gorev.satirNo !in satirlar.indices) return false
        val satir = satirlar[gorev.satirNo]
        val eslesme = MarkdownBicimci.ONAY.find(satir) ?: return false
        val girinti = eslesme.groupValues[1].length
        val isaretli = !eslesme.groupValues[2].equals(" ", true)
        val yeniIsaret = if (isaretli) " " else "x"
        satirlar[gorev.satirNo] =
            satir.substring(0, girinti + 3) + yeniIsaret + satir.substring(girinti + 4)
        return yaz(gorev.notUri, satirlar.joinToString("\n"))
    }

    private fun notDosyasi(ad: String): Boolean =
        ad.endsWith(".md", true) || ad.endsWith(".txt", true)

    private fun notYap(
        f: DocumentFile,
        icerik: String,
        sabitler: Set<String>,
        klasor: String?,
        eslesme: String?
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
            klasor = klasor,
            eslesme = eslesme
        )
    }

    /** Kart önizlemesi için satırdaki Markdown işaretlerini söker. */
    private fun mdTemizle(satir: String): String =
        satir.trim()
            .trimStart('#', '>', ' ')
            .removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- [X]").removePrefix("- ")
            .replace(ISARETLER, "")
            .trim()

    // --- Okuma / yazma ---

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

    // --- Taşıma / silme (yeni konumun adresini döndürür ki geri alınabilsin) ---

    private fun hedefeTasi(uri: Uri, hedef: DocumentFile): Uri? {
        val f = docGetir(uri) ?: return null
        val icerik = oku(uri)
        val ad = (f.name ?: "not.md").removeSuffix(".md").removeSuffix(".txt")
        val yeni = dosyaOlustur(hedef, icerik, ad) ?: return null
        val sabitti = Prefs.sabitler(context).contains(uri.toString())
        sabitTemizle(uri)
        if (sabitti) Prefs.sabitDegistir(context, yeni.toString())
        return if (f.delete()) yeni else yeni
    }

    /** Silinen notun hangi klasörden geldiği kaydedilir ki geri alınca oraya dönsün. */
    fun copeTasi(uri: Uri): Uri? {
        val kaynakKlasor = notunKlasoru(uri)
        val cop = copKlasoru(true) ?: return null
        val yeni = hedefeTasi(uri, cop) ?: return null
        Prefs.copKaynagiKaydet(context, yeni.toString(), kaynakKlasor)
        return yeni
    }

    fun geriYukle(uri: Uri): Uri? {
        val kaynakKlasor = Prefs.copKaynagi(context, uri.toString())
        val hedef = kaynakKlasor?.let { klasorBul(it) } ?: kok()
        val yeni = hedefeTasi(uri, hedef)
        Prefs.copKaynagiSil(context, uri.toString())
        return yeni
    }

    fun klasoreTasi(uri: Uri, klasorAdi: String?): Uri? {
        val hedef = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return null
        return hedefeTasi(uri, hedef)
    }

    fun kaliciSil(uri: Uri): Boolean {
        sabitTemizle(uri)
        Prefs.copKaynagiSil(context, uri.toString())
        return docGetir(uri)?.delete() ?: false
    }

    /** Bir notun hangi klasörde olduğunu bulur (ana klasördeyse null). */
    fun notunKlasoru(uri: Uri): String? {
        for (klasor in klasorAdlari()) {
            val dizin = klasorBul(klasor) ?: continue
            if (dizin.listFiles().any { it.uri == uri }) return klasor
        }
        return null
    }

    private fun sabitTemizle(uri: Uri) {
        val id = uri.toString()
        if (Prefs.sabitler(context).contains(id)) Prefs.sabitDegistir(context, id)
    }

    private companion object {
        val ISARETLER = Regex("\\*{1,3}|~~|__|`|\\[\\[|]]")
    }
}
