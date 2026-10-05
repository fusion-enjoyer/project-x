package com.ekosistem.notlar

/**
 * "Geçmişte bugün" widget'ının notu seçen saf kısmı (Android'e bağlı değil,
 * testli). Günler [SonTarih] gün numarasıyla verilir.
 *
 * Notun günü: başlığı `YYYY-AA-GG` olan günlük notta o tarih, değilse
 * oluşturma günü, o da yoksa son değiştirilme günü. Öncelik:
 *  1. Önceki bir yılda bugünün tarihi: en yakın yıl ("1 yıl önce bugün").
 *  2. Bir ay önce bugün.
 *  3. Hiçbiri yoksa 30 günden eski notlardan biri; gün boyunca aynı kalır,
 *     ertesi gün değişir.
 */
object GecmisSecici {

    class Aday(val anahtar: String, val baslik: String, val gun: Long)

    sealed class Secim(val aday: Aday) {
        class YilOnce(aday: Aday, val yil: Int) : Secim(aday)
        class AyOnce(aday: Aday) : Secim(aday)
        class Eski(aday: Aday) : Secim(aday)
    }

    private val GUNLUK_BASLIK = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /** Günlük notun başlığındaki tarih; günlük not değilse null. */
    fun gunlukGunu(baslik: String): Long? {
        val m = GUNLUK_BASLIK.matchEntire(baslik.trim()) ?: return null
        return SonTarih.gun("📅 ${m.value}")
    }

    fun sec(adaylar: List<Aday>, bugun: Long): Secim? {
        val (yil, ay, gun) = SonTarih.tarih(bugun)
        val gecmis = adaylar.filter { it.gun < bugun }
        if (gecmis.isEmpty()) return null

        // 1. Önceki yıllarda aynı ay ve gün; en yakın yıl önce.
        gecmis.asSequence()
            .mapNotNull { a ->
                val (y, m, g) = SonTarih.tarih(a.gun)
                if (m == ay && g == gun && y < yil) a to (yil - y) else null
            }
            .minByOrNull { it.second }
            ?.let { (a, fark) -> return Secim.YilOnce(a, fark) }

        // 2. Bir ay önce bugün (o ayda bugünün günü yoksa atlanır: 31 Mart → 31 Şubat yok).
        val (oncekiYil, oncekiAy) = if (ay == 1) (yil - 1) to 12 else yil to (ay - 1)
        if (gun <= SonTarih.ayinGunleri(oncekiYil, oncekiAy)) {
            val birAyOnce = SonTarih.gunNumarasi(oncekiYil, oncekiAy, gun)
            gecmis.firstOrNull { it.gun == birAyOnce }?.let { return Secim.AyOnce(it) }
        }

        // 3. 30 günden eski notlardan, güne bağlı sabit bir seçim.
        val eskiler = gecmis.filter { it.gun <= bugun - 30 }.sortedBy { it.anahtar }
        if (eskiler.isEmpty()) return null
        return Secim.Eski(eskiler[Math.floorMod(bugun, eskiler.size.toLong()).toInt()])
    }
}
