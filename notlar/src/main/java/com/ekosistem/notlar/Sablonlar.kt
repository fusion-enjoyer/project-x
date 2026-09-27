package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Not şablonları. Şablon ayrı bir veri türü değil, `sablonlar` klasöründeki
 * sıradan bir nottur: kullanıcı onu her not gibi açıp düzenleyebilir, silebilir.
 * Klasör listede kendi çipiyle görünür ama "Tümü" listesini kirletmez.
 *
 * Şablondan not oluşturulurken içerik olduğu gibi kopyalanır, yalnızca
 * `{{tarih}}` gibi yer tutucular doldurulur.
 */
object Sablonlar {

    const val KLASOR = "sablonlar"

    /** Örnek şablon seti değiştikçe artırılır; eksikler bir kez tamamlanır. */
    const val ORNEK_SURUMU = 2

    /** Günlük notun gövdesini veren şablonun dosya adı. */
    private const val GUNLUK = "gunluk"

    /**
     * `{{tarih}}`, `{{saat:HH.mm}}`, `{{baslik}}` — Türkçe ve İngilizce adlar.
     * Kapanış süslü parantezleri kaçırılmalı: Android'in ICU motoru kaçışsız
     * `}` kabul etmiyor ve deseni çalışma anında reddediyor.
     */
    private val YER_TUTUCU = Regex(
        "\\{\\{\\s*(tarih|date|saat|time|baslik|title)(?::([^}]*))?\\s*\\}\\}",
        RegexOption.IGNORE_CASE
    )

    fun listele(depo: NotDeposu): List<Not> = try {
        depo.notlariListele(null, KLASOR)
    } catch (_: Exception) {
        emptyList()
    }

    fun uygula(context: Context, icerik: String, baslik: String): String {
        val simdi = Date()
        return YER_TUTUCU.replace(icerik) { eslesme ->
            val ad = eslesme.groupValues[1].lowercase(Locale.ROOT)
            val desen = eslesme.groupValues[2].trim()
            when (ad) {
                "baslik", "title" -> baslik
                "saat", "time" -> bicimle(desen.ifEmpty { "HH:mm" }, simdi)
                else -> if (desen.isEmpty()) uzunTarih(context, simdi) else bicimle(desen, simdi)
            }
        }
    }

    private fun uzunTarih(context: Context, tarih: Date): String =
        DateUtils.formatDateTime(
            context,
            tarih.time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
        )

    /** Geçersiz desende çuvallamak yerine deseni olduğu gibi bırakır. */
    private fun bicimle(desen: String, tarih: Date): String = try {
        SimpleDateFormat(desen, Locale.getDefault()).format(tarih)
    } catch (_: Exception) {
        desen
    }

    // --- Günlük not ---

    /** Günlük notun başlığı ve dosya adı: sıralanabilir olsun diye ISO biçim. */
    fun bugununBasligi(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Bugünün notunun içeriği. `gunluk` şablonu varsa gövde ondan gelir.
     *
     * İlk satır her hâlükârda tarihtir: hem notun başlığı hem dosya adı odur,
     * yoksa ertesi gün "bugünün notu" mevcut notu bulamaz, ikinci bir tane açardı.
     * Şablonun kendi ilk satırı onun adıdır (listede o görünür), bu yüzden
     * günlük notta kullanılmaz.
     */
    fun gunlukIcerik(context: Context, depo: NotDeposu, baslik: String): String {
        val sablon = gunlukSablonu(depo) ?: return "$baslik\n"
        val govde = uygula(context, depo.oku(sablon.uri), baslik)
        return (listOf(baslik) + govde.lines().drop(1)).joinToString("\n")
    }

    /** Bugünün notu: varsa bulunur, yoksa (şablonuyla) oluşturulur. */
    fun bugununNotu(context: Context, depo: NotDeposu): Uri? {
        val baslik = bugununBasligi()
        return depo.baslikIleBul(baslik)?.uri
            ?: depo.notOlustur(gunlukIcerik(context, depo, baslik), null, baslik)
    }

    /**
     * Metni bugünün notunun sonuna ekler ("Notlara ekle" → "Bugünün notuna").
     * Önceki hali sürüm geçmişine düşer; ekleme geri alınabilir.
     */
    fun bugununNotunaEkle(context: Context, depo: NotDeposu, metin: String): Uri? {
        val adres = bugununNotu(context, depo) ?: return null
        val mevcut = depo.okuKesin(adres) ?: return null
        val ayrac = if (mevcut.isEmpty() || mevcut.endsWith("\n\n")) "" else if (mevcut.endsWith("\n")) "\n" else "\n\n"
        depo.gecmiseYaz(adres, mevcut)
        return if (depo.yaz(adres, mevcut + ayrac + metin.trim() + "\n")) adres else null
    }

    private fun gunlukSablonu(depo: NotDeposu): Not? = listele(depo).firstOrNull {
        it.ad.removeSuffix(".md").removeSuffix(".txt").equals(GUNLUK, ignoreCase = true)
    }

    // --- Oluşturma ---

    /** Açık notu şablon klasörüne kopyalar. */
    fun kaydet(depo: NotDeposu, icerik: String): Uri? {
        if (icerik.isBlank()) return null
        depo.klasorOlustur(KLASOR)
        return depo.notOlustur(icerik, KLASOR)
    }

    /**
     * Şablon klasörü boşken sunulan üç başlangıç şablonu. İçerikleri çeviriden
     * gelir; dosya adları sabittir ki `gunluk` şablonu dilden bağımsız bulunsun.
     */
    fun ornekleriOlustur(context: Context, depo: NotDeposu): Int {
        depo.klasorOlustur(KLASOR)
        val ornekler = listOf(
            GUNLUK to context.getString(R.string.sablon_gunluk),
            "toplanti" to context.getString(R.string.sablon_toplanti),
            "alisveris" to context.getString(R.string.sablon_alisveris),
            "haftalik" to context.getString(R.string.sablon_haftalik),
            "kitap" to context.getString(R.string.sablon_kitap)
        )
        var sayi = 0
        val mevcutlar = listele(depo).map {
            it.ad.removeSuffix(".md").removeSuffix(".txt").lowercase(Locale.ROOT)
        }.toSet()
        for ((ad, icerik) in ornekler) {
            if (mevcutlar.contains(ad)) continue
            if (depo.notOlustur(icerik, KLASOR, ad) != null) sayi++
        }
        return sayi
    }
}
