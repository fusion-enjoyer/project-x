package com.ekosistem.takvim

import java.util.TimeZone

/**
 * Takvimin bütün tarih hesabı burada. "Gün" = 1970-01-01'den beri geçen gün
 * sayısı (Int); saat dilimi yoktur, yalnız takvim tarihidir. Böylece ay ızgarası,
 * hafta başı, tekrar bitişi gibi işler yaz saati ve dilim karmaşasına girmez.
 * Android'e bağlı değil, birim testle sınanır (java.time Android 5'te yok).
 *
 * Haftanın günleri 0 = Pazartesi … 6 = Pazar.
 */
object Gun {
    const val GUN_MS = 86_400_000L

    /** Ay 1..12. */
    fun gun(yil: Int, ay: Int, gun: Int): Int {
        val y = if (ay <= 2) yil - 1 else yil
        val cag = Math.floorDiv(y, 400)
        val yoe = y - cag * 400
        val mp = (ay + 9) % 12
        val doy = (153 * mp + 2) / 5 + gun - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return cag * 146097 + doe - 719468
    }

    private fun coz(g: Int): IntArray {
        val z = g + 719468
        val cag = Math.floorDiv(z, 146097)
        val doe = z - cag * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + cag * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return intArrayOf(if (m <= 2) y + 1 else y, m, d)
    }

    fun yil(g: Int) = coz(g)[0]
    /** 1..12 */
    fun ay(g: Int) = coz(g)[1]
    fun ayinGunu(g: Int) = coz(g)[2]

    /** 0 = Pazartesi … 6 = Pazar (1970-01-01 Perşembe'ydi). */
    fun haftaGunu(g: Int): Int = Math.floorMod(g + 3, 7)

    fun ayinGunSayisi(yil: Int, ay: Int): Int {
        val sonraki = if (ay == 12) gun(yil + 1, 1, 1) else gun(yil, ay + 1, 1)
        return sonraki - gun(yil, ay, 1)
    }

    /** [g]'yi içeren haftanın ilk günü; [haftaBasi] 0 = Pzt, 6 = Paz, 5 = Cmt. */
    fun haftaBasi(g: Int, haftaBasi: Int): Int = g - Math.floorMod(haftaGunu(g) - haftaBasi, 7)

    /** [adet] ay ileri/geri; ayın günü hedef ayda yoksa (31 Ocak + 1 ay) o ayın son günü olur. */
    fun ayEkle(g: Int, adet: Int): Int {
        val c = coz(g)
        val toplam = c[0] * 12 + (c[1] - 1) + adet
        val yil = Math.floorDiv(toplam, 12)
        val ay = Math.floorMod(toplam, 12) + 1
        return gun(yil, ay, minOf(c[2], ayinGunSayisi(yil, ay)))
    }

    /** ISO-8601 hafta numarası (Pazartesi başlar, ilk hafta ocak ayının ilk Perşembesini içerir). */
    fun isoHafta(g: Int): Int {
        val persembe = g - haftaGunu(g) + 3
        val yil = yil(persembe)
        val ilkPersembe = gun(yil, 1, 4).let { it - haftaGunu(it) + 3 }
        return (persembe - ilkPersembe) / 7 + 1
    }

    /** Gece yarısından beri geçen dakika (duvar saati). */
    fun yerelDakika(ms: Long, tz: TimeZone): Int =
        (Math.floorMod(ms + tz.getOffset(ms), GUN_MS) / 60_000L).toInt()

    fun yerelGun(ms: Long, tz: TimeZone): Int =
        Math.floorDiv(ms + tz.getOffset(ms), GUN_MS).toInt()

    fun bugun(simdi: Long, tz: TimeZone): Int = yerelGun(simdi, tz)

    /**
     * Duvar saatini (gün + dakika) anına çevirir. Yaz saati boşluğundaki saat
     * (ör. 02:30 diye bir an yoksa) ileri, çift geçen saat ilk geçişe düşer.
     */
    fun yerelAn(g: Int, dakika: Int, tz: TimeZone): Long {
        val saf = g * GUN_MS + dakika * 60_000L
        val once = tz.getOffset(saf - GUN_MS)
        val sonra = tz.getOffset(saf + GUN_MS)
        // Büyük sapma önce denenir: saat geri alınırken çift geçen saatin ilk geçişi seçilir.
        for (sapma in setOf(maxOf(once, sonra), minOf(once, sonra))) {
            val an = saf - sapma
            if (tz.getOffset(an) == sapma) return an
        }
        return saf - once   // boşluk: ileri düşer
    }

    /** Tüm gün etkinlikleri UTC gece yarısında saklanır; o günün başlangıç anı. */
    fun utcGunBasi(g: Int): Long = g * GUN_MS

    fun utcGun(ms: Long): Int = Math.floorDiv(ms, GUN_MS).toInt()

    /** Android takvim deposunun gün numarası (Julian). */
    fun julian(g: Int): Int = g + 2_440_588
}
