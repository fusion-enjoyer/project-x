package com.ekosistem.takvim

/**
 * Rehberdeki doğum günleri (saf mantık: tarih çözme ve gün aralığına yayma). Rehber kaydı
 * ([DogumGunleri]) buraya yalnız ham tarih metnini verir; birim testle sınanır.
 */
object DogumGunu {

    class Kisi(val id: Long, val ad: String, val ay: Int, val gun: Int, val yil: Int?)

    /** [kisi]nin [gun] günündeki doğum günü; [yas] doğum yılı biliniyorsa dolu. */
    class Gecis(val kisi: Kisi, val gun: Int, val yas: Int?)

    /**
     * Rehberin tarih metnini çözer: "1990-05-17", "--05-17" (yılsız), "19900517", "1990-5-7".
     * Google yılsız tarihlere 1604 yazar; 1900 öncesi yıl "yok" sayılır. Çözülemezse null.
     */
    fun coz(metin: String?): Triple<Int?, Int, Int>? {
        val s = metin?.trim().orEmpty()
        val yilsiz = Regex("^--(\\d{1,2})-?(\\d{1,2})$").matchEntire(s)
        val m = yilsiz ?: Regex("^(\\d{4})-?(\\d{1,2})-?(\\d{1,2})(?:[T ].*)?$").matchEntire(s) ?: return null
        val yil: Int?
        val ay: Int
        val gun: Int
        if (yilsiz != null) {
            yil = null
            ay = m.groupValues[1].toInt()
            gun = m.groupValues[2].toInt()
        } else {
            yil = m.groupValues[1].toInt().takeIf { it >= 1900 }
            ay = m.groupValues[2].toInt()
            gun = m.groupValues[3].toInt()
        }
        if (ay !in 1..12 || gun !in 1..31) return null
        // 30 Şubat gibi geçersiz tarih (29 Şubat artık yılda geçerli olabilir, yıl bilinmese de kabul).
        if (gun > Gun.ayinGunSayisi(2024, ay)) return null
        return Triple(yil, ay, gun)
    }

    /** [ilkGun]..[sonGun] (dahil) arasında düşen doğum günleri; 29 Şubat artık olmayan yılda 28 Şubat'ta kutlanır. */
    fun gecisler(kisiler: List<Kisi>, ilkGun: Int, sonGun: Int): List<Gecis> {
        val sonuc = ArrayList<Gecis>()
        val y0 = Gun.yil(ilkGun)
        val y1 = Gun.yil(sonGun)
        for (k in kisiler) {
            for (y in y0..y1) {
                val gun = if (k.ay == 2 && k.gun == 29 && Gun.ayinGunSayisi(y, 2) < 29) Gun.gun(y, 2, 28) else Gun.gun(y, k.ay, k.gun)
                if (gun in ilkGun..sonGun) {
                    sonuc.add(Gecis(k, gun, k.yil?.let { y - it }?.takeIf { it > 0 }))
                }
            }
        }
        return sonuc.sortedWith(compareBy({ it.gun }, { it.kisi.ad }))
    }
}
