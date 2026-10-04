package com.ekosistem.takvim

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {
    private val ist = TimeZone.getTimeZone("Europe/Istanbul")
    private val simdi = 1_791_000_000_000L

    private fun saatli(ad: String, gun: Int, bas: Int, bit: Int, kural: String? = null, alarm: List<Int> = emptyList()) = IcsEtkinlik(
        "u-$ad@t", ad, "", "", Gun.yerelAn(gun, bas, ist), Gun.yerelAn(gun, bit, ist), false, ist.id, kural, alarm
    )

    @Test fun gidisDonusSaatli() {
        val g = Gun.gun(2026, 10, 4)
        val e = saatli("Toplantı", g, 540, 600, alarm = listOf(10, 60))
            .copy(aciklama = "Satır 1\nSatır 2; virgül, ters\\", konum = "Ofis, Kat 3")
        val metin = Ics.yaz(listOf(e), simdi)
        val oku = Ics.oku(metin, ist)
        assertEquals(1, oku.size)
        val o = oku[0]
        assertEquals(e.baslik, o.baslik)
        assertEquals(e.aciklama, o.aciklama)
        assertEquals(e.konum, o.konum)
        assertEquals(e.baslangic, o.baslangic)
        assertEquals(e.bitis, o.bitis)
        assertEquals(listOf(10, 60), o.hatirlaticilar)
        assertEquals(false, o.tumGun)
        assertEquals(e.uid, o.uid)
    }

    @Test fun tumGunTarihOlarakYazilir() {
        val g = Gun.gun(2026, 10, 7)
        val e = IcsEtkinlik("d@t", "Doğum günü", "", "", Gun.utcGunBasi(g), Gun.utcGunBasi(g + 1), true, "UTC")
        val metin = Ics.yaz(listOf(e), simdi)
        assertTrue(metin.contains("DTSTART;VALUE=DATE:20261007"))
        assertTrue(metin.contains("DTEND;VALUE=DATE:20261008"))
        val o = Ics.oku(metin, ist)[0]
        assertTrue(o.tumGun)
        assertEquals(e.baslangic, o.baslangic)
        assertEquals(e.bitis, o.bitis)
    }

    @Test fun tekrarlayanSaatliDilimliYazilir() {
        val e = saatli("Haftalık", Gun.gun(2026, 9, 28), 600, 660, kural = "FREQ=WEEKLY;BYDAY=MO")
        val metin = Ics.yaz(listOf(e), simdi)
        assertTrue(metin.contains("DTSTART;TZID=Europe/Istanbul:20260928T100000"))
        assertTrue(metin.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
        val o = Ics.oku(metin, TimeZone.getTimeZone("UTC"))[0]
        assertEquals(e.baslangic, o.baslangic)
        assertEquals("Europe/Istanbul", o.zamanDilimi)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", o.kural)
    }

    @Test fun muafVeOncekiOrnek() {
        val g = Gun.gun(2026, 10, 5)
        val ana = saatli("Seri", g, 600, 660, kural = "FREQ=DAILY").copy(muaf = listOf(Gun.yerelAn(g + 2, 600, ist)))
        val degisen = saatli("Seri yeni", g + 1, 700, 760).copy(uid = ana.uid, oncekiOrnek = Gun.yerelAn(g + 1, 600, ist))
        val oku = Ics.oku(Ics.yaz(listOf(ana, degisen), simdi), ist)
        assertEquals(2, oku.size)
        assertEquals(ana.muaf, oku[0].muaf)
        assertEquals(degisen.oncekiOrnek, oku[1].oncekiOrnek)
        assertEquals(ana.uid, oku[1].uid)
        assertNull(oku[0].oncekiOrnek)
    }

    @Test fun uzunSatirKatlanirVeBirlesir() {
        val uzun = "ğ".repeat(200) + "x".repeat(200)
        val e = saatli("Uzun", Gun.gun(2026, 10, 4), 600, 660).copy(aciklama = uzun)
        val metin = Ics.yaz(listOf(e), simdi)
        for (satir in metin.split("\r\n")) assertTrue(satir.toByteArray(Charsets.UTF_8).size <= 75)
        assertEquals(uzun, Ics.oku(metin, ist)[0].aciklama)
    }

    @Test fun disaridanGelenDosya() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:abc@google.com
            DTSTART;TZID=Europe/Berlin:20261012T150000
            DURATION:PT1H30M
            SUMMARY:Sunum\, kısa
            LOCATION:Berlin
            BEGIN:VALARM
            TRIGGER:-PT15M
            ACTION:DISPLAY
            END:VALARM
            BEGIN:VALARM
            TRIGGER:-P1D
            ACTION:DISPLAY
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            UID:iptal@x
            DTSTART:20261013T090000Z
            STATUS:CANCELLED
            SUMMARY:İptal
            END:VEVENT
            BEGIN:VEVENT
            UID:yok@x
            SUMMARY:Tarihsiz
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val oku = Ics.oku(ics, ist)
        assertEquals(1, oku.size)
        val o = oku[0]
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        assertEquals(Gun.yerelAn(Gun.gun(2026, 10, 12), 900, berlin), o.baslangic)
        assertEquals(o.baslangic + 90 * 60_000L, o.bitis)
        assertEquals("Sunum, kısa", o.baslik)
        assertEquals(listOf(15, 1440), o.hatirlaticilar)
        assertEquals("Europe/Berlin", o.zamanDilimi)
    }

    @Test fun yuzenSaatCihazDilimindedir() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:y@x\r\nDTSTART:20261004T100000\r\nDTEND:20261004T110000\r\nSUMMARY:Y\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val o = Ics.oku(ics, ist)[0]
        assertEquals(Gun.yerelAn(Gun.gun(2026, 10, 4), 600, ist), o.baslangic)
    }

    @Test fun bilinmeyenDilimCihazDilimineDuser() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:y@x\r\nDTSTART;TZID=W. Europe Standard Time:20261004T100000\r\nSUMMARY:Y\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val o = Ics.oku(ics, ist)[0]
        assertEquals(Gun.yerelAn(Gun.gun(2026, 10, 4), 600, ist), o.baslangic)
        assertEquals(o.baslangic, o.bitis)
    }

    @Test fun tumGunBitisYoksaBirGun() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:t@x\r\nDTSTART;VALUE=DATE:20261010\r\nSUMMARY:T\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val o = Ics.oku(ics, ist)[0]
        assertEquals(Gun.GUN_MS, o.bitis - o.baslangic)
    }

    @Test fun bosVeBozuk() {
        assertEquals(0, Ics.oku("", ist).size)
        assertEquals(0, Ics.oku("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:bozuk\r\nEND:VEVENT\r\nEND:VCALENDAR", ist).size)
    }
}
