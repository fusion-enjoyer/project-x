package com.ekosistem.takvim

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TekrarTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val pazar = Gun.gun(2026, 10, 4)     // Pazar
    private val sali = Gun.gun(2026, 10, 13)     // ayın 2. Salısı

    private fun coz(r: String?, gun: Int = pazar) = Tekrar.coz(r, gun, berlin)

    @Test fun yokVeBos() {
        assertNull(coz(null)); assertNull(coz("")); assertNull(coz("  "))
    }

    @Test fun gunluk() {
        assertEquals(Kural(Sik.GUNLUK), coz("FREQ=DAILY"))
        assertEquals(Kural(Sik.GUNLUK, aralik = 3), coz("FREQ=DAILY;INTERVAL=3"))
        assertEquals(Kural(Sik.GUNLUK, sayi = 5), coz("RRULE:FREQ=DAILY;COUNT=5"))
    }

    @Test fun haftalik() {
        assertEquals(Kural(Sik.HAFTALIK), coz("FREQ=WEEKLY"))
        assertEquals(Kural(Sik.HAFTALIK, gunler = setOf(0, 2)), coz("FREQ=WEEKLY;BYDAY=MO,WE"))
        // yalnız başlangıç günü (Pazar) = belirtilmemiş
        assertEquals(Kural(Sik.HAFTALIK), coz("FREQ=WEEKLY;BYDAY=SU"))
        assertEquals(Kural(Sik.HAFTALIK, aralik = 2), coz("FREQ=WEEKLY;WKST=SU;INTERVAL=2"))
    }

    @Test fun aylikAyinGunu() {
        assertEquals(Kural(Sik.AYLIK), coz("FREQ=MONTHLY"))
        assertEquals(Kural(Sik.AYLIK), coz("FREQ=MONTHLY;BYMONTHDAY=4"))
        assertNull(coz("FREQ=MONTHLY;BYMONTHDAY=15"))        // başlangıçla uyuşmuyor → özel
        assertNull(coz("FREQ=MONTHLY;BYMONTHDAY=1,15"))
    }

    @Test fun aylikNinciHafta() {
        assertEquals(Kural(Sik.AYLIK, gunler = setOf(1), aySirasi = 2), coz("FREQ=MONTHLY;BYDAY=2TU", sali))
        assertEquals(Kural(Sik.AYLIK, gunler = setOf(1), aySirasi = -1), coz("FREQ=MONTHLY;BYDAY=-1TU", sali))
        assertNull(coz("FREQ=MONTHLY;BYDAY=TU", sali))        // sıra yok
        assertNull(coz("FREQ=MONTHLY;BYDAY=MO,TU", sali))
        assertNull(coz("FREQ=MONTHLY;BYDAY=2TU;BYSETPOS=1", sali))
    }

    @Test fun yillik() {
        assertEquals(Kural(Sik.YILLIK), coz("FREQ=YEARLY"))
        assertEquals(Kural(Sik.YILLIK), coz("FREQ=YEARLY;BYMONTH=10;BYMONTHDAY=4"))
        assertNull(coz("FREQ=YEARLY;BYMONTH=3"))
    }

    @Test fun desteklenmeyenler() {
        assertNull(coz("FREQ=HOURLY"))
        assertNull(coz("FREQ=WEEKLY;BYSETPOS=1"))
        assertNull(coz("FREQ=DAILY;BYDAY=MO"))
        assertNull(coz("FREQ=DAILY;COUNT=3;UNTIL=20261231"))
        assertNull(coz("FREQ=DAILY;INTERVAL=0"))
        assertNull(coz("DAILY"))
    }

    @Test fun bitisler() {
        assertEquals(Gun.gun(2026, 12, 31), coz("FREQ=DAILY;UNTIL=20261231")!!.bitisGun)
        // UTC'de 22:59:59 Berlin'de (CET, +1) 23:59:59, yani aynı gün
        assertEquals(Gun.gun(2026, 12, 31), coz("FREQ=DAILY;UNTIL=20261231T225959Z")!!.bitisGun)
        // 23:00:00 UTC ertesi gün 00:00 Berlin
        assertEquals(Gun.gun(2027, 1, 1), coz("FREQ=DAILY;UNTIL=20261231T230000Z")!!.bitisGun)
    }

    @Test fun yazCozGidisDonus() {
        val kurallar = listOf(
            Kural(Sik.GUNLUK), Kural(Sik.GUNLUK, aralik = 2, sayi = 10),
            Kural(Sik.HAFTALIK, gunler = setOf(0, 2, 4)),
            Kural(Sik.HAFTALIK, aralik = 2, bitisGun = Gun.gun(2027, 5, 1)),
            Kural(Sik.YILLIK, sayi = 3)
        )
        for (k in kurallar) {
            for (tumGun in listOf(false, true)) {
                assertEquals(k, Tekrar.coz(Tekrar.yaz(k, tumGun, berlin), pazar, berlin))
            }
        }
        val aylik = Kural(Sik.AYLIK, gunler = setOf(1), aySirasi = 2)
        assertEquals(aylik, Tekrar.coz(Tekrar.yaz(aylik, false, berlin), sali, berlin))
        assertEquals(Kural(Sik.AYLIK, aralik = 3), Tekrar.coz(Tekrar.yaz(Kural(Sik.AYLIK, aralik = 3), false, berlin), pazar, berlin))
    }

    @Test fun yazBicimi() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=4",
            Tekrar.yaz(Kural(Sik.HAFTALIK, 2, setOf(2, 0), sayi = 4), false, berlin))
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR",
            Tekrar.yaz(Kural(Sik.AYLIK, gunler = setOf(4), aySirasi = -1), false, berlin))
        assertEquals("FREQ=DAILY;UNTIL=20261231",
            Tekrar.yaz(Kural(Sik.GUNLUK, bitisGun = Gun.gun(2026, 12, 31)), true, berlin))
        // zamanlı: yerel günün sonu UTC'ye çevrilir (Berlin kış +1 → 22:59:59Z)
        assertEquals("FREQ=DAILY;UNTIL=20261231T225959Z",
            Tekrar.yaz(Kural(Sik.GUNLUK, bitisGun = Gun.gun(2026, 12, 31)), false, berlin))
    }

    @Test fun kesmeTumGun() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261003",
            Tekrar.kes("FREQ=WEEKLY;BYDAY=MO;COUNT=10", 0, Gun.gun(2026, 10, 3), true))
        assertEquals("FREQ=DAILY;UNTIL=20261003",
            Tekrar.kes("RRULE:FREQ=DAILY;UNTIL=20271231", 0, Gun.gun(2026, 10, 3), true))
    }

    @Test fun kesmeZamanli() {
        val an = Gun.yerelAn(Gun.gun(2026, 10, 5), 9 * 60, berlin) - 1000   // 08:59:59 Berlin = 06:59:59Z
        assertEquals("FREQ=DAILY;UNTIL=20261005T065959Z", Tekrar.kes("FREQ=DAILY", an, 0, false))
    }

    @Test fun sayiOku() {
        assertEquals(7, Tekrar.sayi("FREQ=DAILY;COUNT=7"))
        assertEquals(0, Tekrar.sayi("FREQ=DAILY"))
        assertEquals(0, Tekrar.sayi(null))
        assertEquals("FREQ=DAILY;COUNT=2", Tekrar.sayiyiDegistir("FREQ=DAILY;COUNT=7", 2))
        assertNotNull(Tekrar.sayiyiDegistir("FREQ=DAILY", 2))
    }
}

class SureTest {
    @Test fun sureMs() {
        assertEquals(3_600_000L, Tekrar.sureMs("P3600S"))
        assertEquals(5_400_000L, Tekrar.sureMs("PT1H30M"))
        assertEquals(86_400_000L, Tekrar.sureMs("P1D"))
        assertEquals(14 * 86_400_000L, Tekrar.sureMs("P2W"))
        assertEquals(0L, Tekrar.sureMs(null))
        assertEquals(0L, Tekrar.sureMs("saçma"))
    }

    @Test fun sureYaz() {
        assertEquals("P3600S", Tekrar.sureYaz(0, 3_600_000L, false))
        assertEquals("P2D", Tekrar.sureYaz(0, 2 * 86_400_000L, true))
        assertEquals("P1D", Tekrar.sureYaz(0, 0, true))
        assertEquals("P0S", Tekrar.sureYaz(5, 5, false))
    }
}
