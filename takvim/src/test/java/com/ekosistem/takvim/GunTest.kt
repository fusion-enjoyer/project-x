package com.ekosistem.takvim

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GunTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun epokGunuSifir() {
        assertEquals(0, Gun.gun(1970, 1, 1))
        assertEquals(1970, Gun.yil(0)); assertEquals(1, Gun.ay(0)); assertEquals(1, Gun.ayinGunu(0))
    }

    @Test fun gidisDonus() {
        for (g in -800_000..800_000 step 997) {
            assertEquals(g, Gun.gun(Gun.yil(g), Gun.ay(g), Gun.ayinGunu(g)))
        }
    }

    @Test fun bilinenGunler() {
        // 4 Ekim 2026 Pazar, 29 Şubat 2028 Salı
        assertEquals(6, Gun.haftaGunu(Gun.gun(2026, 10, 4)))
        assertEquals(1, Gun.haftaGunu(Gun.gun(2028, 2, 29)))
        assertEquals(0, Gun.haftaGunu(Gun.gun(1970, 1, 5)))
        assertEquals(3, Gun.haftaGunu(0))
    }

    @Test fun ayinGunSayisi() {
        assertEquals(29, Gun.ayinGunSayisi(2028, 2))
        assertEquals(28, Gun.ayinGunSayisi(2026, 2))
        assertEquals(29, Gun.ayinGunSayisi(2000, 2))
        assertEquals(28, Gun.ayinGunSayisi(1900, 2))
        assertEquals(31, Gun.ayinGunSayisi(2026, 12))
    }

    @Test fun haftaBasi() {
        val pazar = Gun.gun(2026, 10, 4)
        assertEquals(Gun.gun(2026, 9, 28), Gun.haftaBasi(pazar, 0))   // Pazartesi başlar
        assertEquals(Gun.gun(2026, 10, 4), Gun.haftaBasi(pazar, 6))   // Pazar başlar
        assertEquals(Gun.gun(2026, 10, 3), Gun.haftaBasi(pazar, 5))   // Cumartesi başlar
        val carsamba = Gun.gun(2026, 10, 7)
        assertEquals(Gun.gun(2026, 10, 5), Gun.haftaBasi(carsamba, 0))
        assertEquals(Gun.gun(2026, 10, 4), Gun.haftaBasi(carsamba, 6))
    }

    @Test fun ayEkle() {
        assertEquals(Gun.gun(2026, 2, 28), Gun.ayEkle(Gun.gun(2026, 1, 31), 1))
        assertEquals(Gun.gun(2027, 1, 15), Gun.ayEkle(Gun.gun(2026, 12, 15), 1))
        assertEquals(Gun.gun(2025, 12, 15), Gun.ayEkle(Gun.gun(2026, 1, 15), -1))
        assertEquals(Gun.gun(2024, 2, 29), Gun.ayEkle(Gun.gun(2023, 1, 29), 13))
    }

    @Test fun isoHafta() {
        assertEquals(40, Gun.isoHafta(Gun.gun(2026, 10, 4)))
        assertEquals(41, Gun.isoHafta(Gun.gun(2026, 10, 5)))
        assertEquals(1, Gun.isoHafta(Gun.gun(2026, 1, 1)))
        assertEquals(1, Gun.isoHafta(Gun.gun(2025, 12, 29)))
        assertEquals(53, Gun.isoHafta(Gun.gun(2020, 12, 31)))
    }

    @Test fun yerelGunVeDakika() {
        // 2026-10-04 23:30 Berlin (CEST, +2) = 21:30 UTC
        val an = Gun.gun(2026, 10, 4) * Gun.GUN_MS + (21 * 60 + 30) * 60_000L
        assertEquals(Gun.gun(2026, 10, 4), Gun.yerelGun(an, berlin))
        assertEquals(23 * 60 + 30, Gun.yerelDakika(an, berlin))
        // 30 dk sonra ertesi gün
        assertEquals(Gun.gun(2026, 10, 5), Gun.yerelGun(an + 30 * 60_000L, berlin))
        assertEquals(0, Gun.yerelDakika(an + 30 * 60_000L, berlin))
    }

    @Test fun yerelAnGidisDonus() {
        val g = Gun.gun(2026, 7, 15)
        val an = Gun.yerelAn(g, 9 * 60 + 15, berlin)
        assertEquals(g, Gun.yerelGun(an, berlin))
        assertEquals(9 * 60 + 15, Gun.yerelDakika(an, berlin))
        assertEquals(g * Gun.GUN_MS + (9 * 60 + 15) * 60_000L, Gun.yerelAn(g, 9 * 60 + 15, utc))
    }

    @Test fun yazSaatiBoslugu() {
        // Berlin 29 Mart 2026: 02:00 → 03:00; 02:30 diye bir an yok, 03:30'a düşer.
        val g = Gun.gun(2026, 3, 29)
        val an = Gun.yerelAn(g, 2 * 60 + 30, berlin)
        assertEquals(3 * 60 + 30, Gun.yerelDakika(an, berlin))
        assertEquals(g, Gun.yerelGun(an, berlin))
    }

    @Test fun yazSaatiCiftGecis() {
        // 25 Ekim 2026: 03:00 → 02:00; 02:30 iki kez geçer, ilkine (yaz saati) düşer.
        val g = Gun.gun(2026, 10, 25)
        val an = Gun.yerelAn(g, 2 * 60 + 30, berlin)
        assertEquals(2 * 60 + 30, Gun.yerelDakika(an, berlin))
        assertEquals(7200_000, berlin.getOffset(an))
    }

    @Test fun julian() {
        assertEquals(2_440_588, Gun.julian(0))
    }
}
