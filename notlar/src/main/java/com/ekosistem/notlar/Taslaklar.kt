package com.ekosistem.notlar

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Yazılmakta olan notun uygulama içindeki güvenlik kopyası.
 *
 * Editör yazarken metni birkaç saniyede bir buraya atar; not asıl dosyasına
 * başarıyla yazılınca taslak silinir. Uygulama çökerse, sistem onu bellek
 * yetmediği için kapatırsa ya da kayıt yarıda kalırsa taslak durur ve not
 * yeniden açıldığında kullanıcıya geri yükleme önerilir.
 *
 *   yazarken ──(1,5 sn)──▶ taslak ──kaydet──▶ asıl dosya ──başarılı──▶ taslak silinir
 *                                                   └──başarısız──▶ taslak kalır
 *
 * Henüz dosyası olmayan yeni not "yeni" anahtarıyla saklanır; uygulama bir
 * sonraki açılışta onu kendiliğinden not olarak kurtarır.
 *
 * Dosya biçimi: ilk satır notun adresi (yeni notta boş), gerisi içerik.
 */
class Taslaklar(private val dizin: File) {

    constructor(context: Context) : this(File(context.filesDir, "taslaklar"))

    data class Taslak(val adres: String?, val metin: String)

    fun yaz(adres: String?, metin: String): Boolean {
        if (!dizin.exists() && !dizin.mkdirs()) return false
        val icerik = (adres ?: "") + "\n" + metin
        return DosyaYazici.atomikYaz(dosya(adres), icerik.toByteArray(Charsets.UTF_8))
    }

    fun oku(adres: String?): Taslak? {
        val f = dosya(adres)
        if (!f.isFile) return null
        return try {
            val ham = f.readText(Charsets.UTF_8)
            val ayrac = ham.indexOf('\n')
            if (ayrac < 0) return null
            val kayitliAdres = ham.substring(0, ayrac).ifEmpty { null }
            // Anahtar çakışmasına karşı: taslak gerçekten bu nota mı ait?
            if (kayitliAdres != adres) return null
            Taslak(kayitliAdres, ham.substring(ayrac + 1))
        } catch (_: Exception) {
            null
        }
    }

    fun sil(adres: String?) {
        dosya(adres).delete()
    }

    /** Not başka bir yere taşındıysa taslağı da yeni adrese geçer. */
    fun tasi(eski: String, yeni: String) {
        val t = oku(eski) ?: return
        if (yaz(yeni, t.metin)) sil(eski)
    }

    private fun dosya(adres: String?): File =
        File(dizin, (if (adres == null) YENI else ozet(adres)) + ".taslak")

    private fun ozet(metin: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(metin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val YENI = "yeni"
    }
}
