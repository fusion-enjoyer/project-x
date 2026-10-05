package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Google Keep'ten geçiş: takeout.google.com'dan alınan zip'teki notları
 * Markdown notlara çevirir. Google'dan ayrılan birinin ilk işi notlarını
 * Keep'ten çıkarmak; bu ekran o yüzden var.
 *
 * Takeout her not için bir JSON verir (Takeout/Keep/<ad>.json), görseller
 * aynı klasörde durur. Eşleme:
 *
 *   title                 → ilk satır (başlık)
 *   textContent           → gövde
 *   listContent           → "- [ ] madde" / "- [x] madde"
 *   annotations (WEBLINK) → metinde yoksa bağlantı satırı
 *   attachments (görsel)  → ekler/ klasörüne kopya + ![](ekler/ad)
 *   labels                → son satırda #etiket
 *   isPinned / isArchived → sabitlenir / "Arşiv" klasörüne gider
 *   isTrashed             → alınmaz
 *   userEditedTimestampUsec → notun tarihi
 *   createdTimestampUsec  → oluşturma tarihi (Not hakkında)
 *
 * Zip iki kez okunur: önce notlar, sonra yalnızca gereken görseller. Görseller
 * zip'te notlardan önce de gelebilir ve hepsini belleğe almak eski telefonda
 * yetmez. Aynı zip ikinci kez seçilirse daha önce alınan notlar atlanır.
 */
object KeepAktarma {

    data class KeepNotu(
        /** Tekrar aktarmada aynı notu tanımak için. */
        val anahtar: String,
        val baslik: String,
        val govde: String,
        val etiketler: List<String>,
        val sabit: Boolean,
        val arsivde: Boolean,
        /** Milisaniye; bilinmiyorsa 0. */
        val degistirilme: Long,
        /** Milisaniye; bilinmiyorsa 0. */
        val olusturma: Long,
        /** Görsel eklerin zip'teki dosya adları. */
        val gorseller: List<String>,
        /** Ses kaydı gibi alınamayan ekler. */
        val atlananEk: Int
    )

    data class Sonuc(
        val not: Int,
        val gorsel: Int,
        val zatenVardi: Int,
        val atlananEk: Int,
        /** Zip'te hiç Keep notu yoksa false: yanlış dosya seçilmiş olabilir. */
        val keepBulundu: Boolean
    )

    private val GORSEL_UZANTILARI = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

    // --- Çözümleme (Android'e bağlı değil, testlenir) ---

    /** Tek bir Keep JSON'unu çözer. Keep notu değilse ya da çöpteyse null. */
    fun cozumle(json: String, dosyaAdi: String): KeepNotu? {
        val o = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        // Takeout'ta başka ürünlerin JSON'ları da olabilir; Keep notunu içeriğinden tanı.
        if (!o.has("textContent") && !o.has("listContent")) return null
        if (o.optBoolean("isTrashed", false)) return null

        val govde = mutableListOf<String>()
        metin(o, "textContent").trimEnd().takeIf { it.isNotEmpty() }?.let { govde.add(it) }
        o.optJSONArray("listContent")?.let { liste ->
            for (i in 0 until liste.length()) {
                val madde = liste.optJSONObject(i) ?: continue
                val yazi = metin(madde, "text").replace('\n', ' ').trim()
                if (yazi.isEmpty()) continue
                val isaret = if (madde.optBoolean("isChecked", false)) "x" else " "
                govde.add("- [$isaret] $yazi")
            }
        }
        val tumu = govde.joinToString("\n")
        nesneler(o.optJSONArray("annotations"))
            .map { metin(it, "url").trim() }
            .filter { it.isNotEmpty() && !tumu.contains(it) }
            .distinct()
            .forEach { govde.add(it) }

        val gorseller = mutableListOf<String>()
        var atlanan = 0
        for (ek in nesneler(o.optJSONArray("attachments"))) {
            val yol = metin(ek, "filePath").substringAfterLast('/').trim()
            if (yol.isEmpty()) continue
            val tur = metin(ek, "mimetype")
            if (tur.startsWith("image/") || uzanti(yol) in GORSEL_UZANTILARI) gorseller.add(yol)
            else atlanan++
        }

        val etiketler = nesneler(o.optJSONArray("labels"))
            .map { etiketAdi(metin(it, "name")) }
            .filter { it.isNotEmpty() }
            .distinct()

        var baslik = metin(o, "title").replace('\n', ' ').trim()
        val govdeMetni = govde.joinToString("\n")
        if (baslik.isEmpty() && govdeMetni.isBlank()) {
            // Yalnızca görsel içeren not: Takeout dosya adı (başlık ya da tarih) başlık olur.
            baslik = dosyaAdi.substringAfterLast('/').removeSuffix(".json").trim()
        }
        if (baslik.isEmpty() && govdeMetni.isBlank() && gorseller.isEmpty()) return null

        val duzenleme = o.optLong("userEditedTimestampUsec", 0L) / 1000
        val olusturma = o.optLong("createdTimestampUsec", 0L)
        return KeepNotu(
            anahtar = ozet("${if (olusturma > 0) olusturma else duzenleme}|${metin(o, "title")}|$dosyaAdi"),
            baslik = baslik,
            govde = govdeMetni,
            etiketler = etiketler,
            sabit = o.optBoolean("isPinned", false),
            arsivde = o.optBoolean("isArchived", false),
            degistirilme = duzenleme,
            olusturma = olusturma / 1000,
            gorseller = gorseller,
            atlananEk = atlanan
        )
    }

    /** Notun Markdown metni. [gorselYollari] nota yazılacak göreli yollar. */
    fun metinOlustur(not: KeepNotu, gorselYollari: List<String>): String {
        val satirlar = mutableListOf<String>()
        if (not.baslik.isNotEmpty()) satirlar.add(not.baslik)
        if (not.govde.isNotEmpty()) satirlar.add(not.govde)
        gorselYollari.forEach { satirlar.add("![]($it)") }
        if (not.etiketler.isNotEmpty()) {
            satirlar.add("")
            satirlar.add(not.etiketler.joinToString(" ") { "#$it" })
        }
        return satirlar.joinToString("\n") + "\n"
    }

    /**
     * Keep etiketinde boşluk ve işaret olabilir ("İş & Proje"); Notlar'ın
     * `#etiket`i harf, rakam, _ ve - kabul eder.
     */
    fun etiketAdi(ham: String): String =
        ham.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .take(40)

    /**
     * Keep ek kaydında ".jpeg" yazıp zip'e ".jpg" koyabiliyor; eşleştirme
     * uzantısız, küçük harfli adla yapılır.
     */
    fun ekAnahtari(ad: String): String =
        ad.substringAfterLast('/').substringBeforeLast('.').lowercase()

    private fun uzanti(ad: String): String = ad.substringAfterLast('.', "").lowercase()

    /** JSON'daki null değer "null" metni olarak gelmesin. */
    private fun metin(o: JSONObject, ad: String): String =
        if (o.isNull(ad)) "" else o.optString(ad, "")

    private fun nesneler(dizi: JSONArray?): List<JSONObject> {
        if (dizi == null) return emptyList()
        return (0 until dizi.length()).mapNotNull { dizi.optJSONObject(it) }
    }

    private fun ozet(metin: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(metin.toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { "%02x".format(it) }

    // --- Aktarma ---

    /** Seçilen zip'leri (Takeout büyükse birden fazla parça olur) aktarır; okunamazsa null. */
    fun aktar(context: Context, depo: NotDeposu, kaynaklar: List<Uri>, arsivKlasoru: String): Sonuc? {
        // 1. geçiş: notlar
        val notlar = mutableListOf<KeepNotu>()
        val okundu = kaynaklar.all { kaynak ->
            zipGez(context, kaynak) { ad, akis ->
                if (ad.endsWith(".json", true)) {
                    cozumle(akis.readBytes().toString(Charsets.UTF_8), ad)?.let { notlar.add(it) }
                }
            }
        }
        if (!okundu) return null
        if (notlar.isEmpty()) return Sonuc(0, 0, 0, 0, keepBulundu = false)

        val onceki = Prefs.keepAktarilanlar(context)
        val yeniler = notlar.filter { it.anahtar !in onceki }

        // 2. geçiş: yalnızca yeni notların görselleri
        val gerekli = yeniler.flatMap { it.gorseller }.map { ekAnahtari(it) }.toSet()
        val kaydedilen = mutableMapOf<String, String>()
        if (gerekli.isNotEmpty()) {
            for (kaynak in kaynaklar) {
                zipGez(context, kaynak) { ad, akis ->
                    val anahtar = ekAnahtari(ad)
                    if (anahtar in gerekli && anahtar !in kaydedilen && uzanti(ad) in GORSEL_UZANTILARI) {
                        Gorseller.disaridanAl(context, depo, ad, akis.readBytes())
                            ?.let { kaydedilen[anahtar] = it }
                    }
                }
            }
        }

        // 3. notları yaz
        var notSayisi = 0
        var gorselSayisi = 0
        val alinan = mutableListOf<String>()
        if (yeniler.any { it.arsivde }) depo.klasorOlustur(arsivKlasoru)
        for (n in yeniler) {
            val klasor = if (n.arsivde) arsivKlasoru else null
            val onek = if (klasor == null) "" else "../"
            val yollar = n.gorseller.mapNotNull { kaydedilen[ekAnahtari(it)] }
                .map { "$onek${Gorseller.EKLER}/${Uri.encode(it, "")}" }
            val uri = depo.notOlustur(metinOlustur(n, yollar), klasor) ?: continue
            depo.tarihiKoru(uri, n.degistirilme, n.olusturma)
            // sabitDegistir aç/kapa yapar; aynı adreste eski bir kayıt kalmışsa notu çözerdi.
            if (n.sabit && uri.toString() !in Prefs.sabitler(context)) {
                Prefs.sabitDegistir(context, uri.toString())
            }
            alinan.add(n.anahtar)
            notSayisi++
            gorselSayisi += yollar.size
        }
        Prefs.keepAktarilanlarEkle(context, alinan)
        return Sonuc(
            not = notSayisi,
            gorsel = gorselSayisi,
            zatenVardi = notlar.size - yeniler.size,
            atlananEk = yeniler.sumOf { it.atlananEk },
            keepBulundu = true
        )
    }

    private fun zipGez(context: Context, kaynak: Uri, isle: (String, InputStream) -> Unit): Boolean =
        try {
            context.contentResolver.openInputStream(kaynak)?.use { akis ->
                ZipInputStream(akis.buffered()).use { zip ->
                    var girdi = zip.nextEntry
                    while (girdi != null) {
                        if (!girdi.isDirectory) isle(girdi.name, zip)
                        zip.closeEntry()
                        girdi = zip.nextEntry
                    }
                }
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
}
