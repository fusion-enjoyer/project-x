package com.ekosistem.notlar

/**
 * Eşitleme çakışması. Not iki cihazda aynı anda değişince Syncthing birini
 * asıl dosyada bırakır, ötekini yanına `Not.sync-conflict-20261004-153012-ABCDEF7.md`
 * adıyla koyar. Uygulama bu kopyayı tanır ve asıl notla karşılaştırıp çözdürür.
 */
object Cakisma {

    data class Bilgi(
        /** Asıl notun dosya adı ("Not.md"). */
        val asilAd: String,
        /** Çakışmanın tarihi ve saati, Syncthing'in yazdığı gibi (yyyyMMdd, HHmmss). */
        val tarih: String,
        val saat: String,
        /** Değişikliği yapan cihazın kısa kimliği. */
        val cihaz: String
    )

    private val SYNCTHING =
        Regex("^(.*)\\.sync-conflict-(\\d{8})-(\\d{6})-([A-Z0-9]{7})(\\.[^./]+)?$")

    /** Dosya adı bir çakışma kopyasıysa bilgisi, değilse null. */
    fun coz(dosyaAdi: String): Bilgi? {
        val m = SYNCTHING.matchEntire(dosyaAdi) ?: return null
        val govde = m.groupValues[1]
        if (govde.isEmpty()) return null
        return Bilgi(govde + m.groupValues[5], m.groupValues[2], m.groupValues[3], m.groupValues[4])
    }

    /**
     * İki sürümün satır satır birleşimi: ortak satırlar bir kez, yalnızca
     * birinde olanlar ikisinden de, sırası korunarak. Aynı satırın iki farklı
     * hâli varsa ikisi de kalır; kullanıcı sonra düzenler. Hiçbir satır kaybolmaz.
     */
    fun birlestir(bu: String, diger: String): String =
        Fark.hesapla(bu.lines(), diger.lines()).joinToString("\n") { it.metin }
}
