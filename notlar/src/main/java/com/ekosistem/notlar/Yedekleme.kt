package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Notların tamamını tek zip dosyasına yazar ve geri okur. İnternet gerekmez.
 *
 * Zip'teki yol notun klasör yolunu ("İş/2026/plan.md"), girdinin tarihi de
 * notun değiştirilme tarihini taşır. Geri yüklerken ikisi de korunur; aynı
 * yerde aynı içerikle duran not atlanır, böylece aynı yedeği iki kez yüklemek
 * notları çiftlemez.
 */
object Yedekleme {

    /** [yarim]: zip'in bir kısmı okunamadı; okunabilen notlar yine de yazıldı. */
    data class Sonuc(val yeni: Int, val zatenVardi: Int, val yarim: Boolean = false)

    fun dosyaAdi(): String {
        val bicim = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return "notlar-yedek-${bicim.format(Date())}.zip"
    }

    /** Başarılıysa yazılan not sayısı, hata olduysa -1. */
    fun disaAktar(context: Context, depo: NotDeposu, hedef: Uri): Int {
        return try {
            var sayi = 0
            context.contentResolver.openOutputStream(hedef)?.use { akis ->
                ZipOutputStream(akis.buffered()).use { zip ->
                    sayi = klasoruYaz(context, depo, depo.kok().uri, "", zip)
                }
            } ?: return -1
            sayi
        } catch (_: Exception) {
            -1
        }
    }

    private fun klasoruYaz(
        context: Context,
        depo: NotDeposu,
        dizin: Uri,
        onek: String,
        zip: ZipOutputStream
    ): Int {
        var sayi = 0
        // Klasör başına tek sorgu (DocumentFile her dosya için ayrı soruyordu).
        for (g in depo.girdiler(dizin)) {
            if (g.ad.startsWith(".")) continue
            if (g.dizin) {
                sayi += klasoruYaz(context, depo, g.uri, "${onek}${g.ad}/", zip)
                continue
            }
            // Görseller metin değil: ham bayt kopyalanır ve not sayılmaz.
            if (onek == Gorseller.EKLER + "/") {
                baytYaz(context, g.uri, "$onek${g.ad}", g.degistirilme, zip)
                continue
            }
            if (!g.ad.endsWith(".md", true) && !g.ad.endsWith(".txt", true)) continue
            val icerik = depo.okuKesin(g.uri) ?: continue
            val girdi = ZipEntry("$onek${g.ad}")
            // Kopyalanarak taşınmış notun gerçek tarihi ayrıca saklanıyor olabilir.
            val zaman = Prefs.gosterilenZaman(context, g.uri.toString(), g.degistirilme)
            if (zaman > 0) girdi.time = zaman
            zip.putNextEntry(girdi)
            zip.write(icerik.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            sayi++
        }
        return sayi
    }

    private fun baytYaz(context: Context, kaynak: Uri, yol: String, zaman: Long, zip: ZipOutputStream) {
        try {
            context.contentResolver.openInputStream(kaynak)?.use { giris ->
                val girdi = ZipEntry(yol)
                if (zaman > 0) girdi.time = zaman
                zip.putNextEntry(girdi)
                giris.copyTo(zip, 64 * 1024)
                zip.closeEntry()
            }
        } catch (_: Exception) {
            // Tek bir görsel okunamazsa yedeğin tamamı yanmasın.
        }
    }

    /** Başarılıysa sonuç, zip'ten tek not bile okunamadıysa null. */
    fun iceAktar(context: Context, depo: NotDeposu, kaynak: Uri): Sonuc? {
        var yeni = 0
        var ayni = 0
        val yukleyici = Yukleyici(depo)
        try {
            context.contentResolver.openInputStream(kaynak)?.use { akis ->
                ZipInputStream(akis.buffered()).use { zip ->
                    var girdi = zip.nextEntry
                    while (girdi != null) {
                        val yol = girdi.name
                        if (!girdi.isDirectory && yol.startsWith(Gorseller.EKLER + "/")) {
                            // Yoldaki dizin kısmı atılır; zip'ten çıkış yapılamasın.
                            Gorseller.geriYukle(
                                context,
                                depo,
                                yol.substringAfterLast('/'),
                                zip.readBytes()
                            )
                        } else if (!girdi.isDirectory && notYolu(yol) != null) {
                            val (klasorler, dosyaAdi) = notYolu(yol)!!
                            val icerik = zip.readBytes().toString(Charsets.UTF_8)
                            when (yukleyici.yaz(klasorler, dosyaAdi, icerik, girdi.time)) {
                                true -> yeni++
                                false -> ayni++
                                null -> {}
                            }
                        }
                        zip.closeEntry()
                        girdi = zip.nextEntry
                    }
                }
            } ?: return null
        } catch (_: Exception) {
            // Bozuk zip ya da Android 14+'ün "../" içeren girdide okumayı kesmesi:
            // o ana kadar yazılanlar kalır, kullanıcıya yarım kaldığı söylenir.
            if (yeni + ayni == 0) return null
            return Sonuc(yeni, ayni, yarim = true)
        }
        return Sonuc(yeni, ayni)
    }

    /**
     * Zip içindeki yolu klasörlere ve dosya adına ayırır. Not değilse, gizli
     * bir yoldaysa (.trash, .gecmis) ya da zip dışına çıkmaya çalışıyorsa null.
     */
    fun notYolu(yol: String): Pair<List<String>, String>? {
        val parcalar = yol.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parcalar.isEmpty() || parcalar.any { it == ".." || it.startsWith(".") }) return null
        val ad = parcalar.last()
        if (!ad.endsWith(".md", true) && !ad.endsWith(".txt", true)) return null
        return parcalar.dropLast(1) to ad
    }

    /**
     * Klasörde aynı notun durabileceği adlar: "plan.md" için plan.md,
     * plan.txt, plan-2.md... Not sonradan düzenlendiyse ilk geri yükleme
     * "plan-2.md" açar; sonrakiler onu da görmezse her seferinde çiftlerdi.
     * Seçilen klasör sağlayıcısı ".txt" notu ".md" olarak da açabilir.
     */
    fun ayniNotAdaylari(dosyaAdi: String, adlar: Collection<String>): List<String> {
        val kok = dosyaAdi.substringBeforeLast('.')
        val desen = Regex(Regex.escape(kok) + "(-\\d+)?\\.(md|txt)", RegexOption.IGNORE_CASE)
        return adlar.filter { desen.matches(it) }
    }

    /** Geri yüklenen bir klasör ve içindeki adlar (ad → adres). */
    private class Hedef(val dizin: DocumentFile, val adlar: MutableMap<String, Uri>)

    /**
     * Klasörleri bir kez bulup listeler. Önceden her not için kök klasör ve
     * hedef klasör yeniden listeleniyordu; 1.000 notluk yedekte bu, seçilen
     * klasörde (SAF) 1.000 × 1.000 girdilik sorgu demekti.
     */
    private class Yukleyici(private val depo: NotDeposu) {
        private val hedefler = HashMap<List<String>, Hedef>()

        /** true: yeni not yazıldı; false: aynısı zaten vardı; null: yazılamadı. */
        fun yaz(klasorler: List<String>, dosyaAdi: String, icerik: String, zaman: Long): Boolean? {
            val anahtar = klasorler.map(depo::adTemizle).filter { it.isNotEmpty() }
            val hedef = hedefler.getOrPut(anahtar) {
                val dizin = depo.dizinZinciri(anahtar)
                Hedef(dizin, depo.girdiler(dizin.uri).associateTo(HashMap()) { it.ad to it.uri })
            }
            val ayni = ayniNotAdaylari(dosyaAdi, hedef.adlar.keys).any { ad ->
                hedef.adlar[ad]?.let { depo.okuKesin(it) } == icerik
            }
            if (ayni) return false
            val ad = dosyaAdi.substringBeforeLast('.')
            val (uri, sonAd) = depo.dizindeOlustur(hedef.dizin, icerik, ad, hedef.adlar.keys)
                ?: return null
            hedef.adlar[sonAd] = uri
            depo.tarihiKoru(uri, zaman)
            return true
        }
    }
}
