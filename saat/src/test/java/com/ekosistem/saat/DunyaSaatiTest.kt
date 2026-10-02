package com.ekosistem.saat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DunyaSaatiTest {

    private fun tz(id: String) = TimeZone.getTimeZone(id)

    private fun an(id: String, yil: Int, ay: Int, gun: Int, saat: Int): Long =
        Calendar.getInstance(tz(id)).apply { clear(); set(yil, ay - 1, gun, saat, 0, 0) }.timeInMillis

    @Test
    fun sehirOlanlarSecilir() {
        assertTrue(DunyaSaati.sehirMi("Europe/Istanbul"))
        assertTrue(DunyaSaati.sehirMi("America/Argentina/Buenos_Aires"))
        assertFalse(DunyaSaati.sehirMi("Etc/GMT+3"))
        assertFalse(DunyaSaati.sehirMi("US/Pacific"))
        assertFalse(DunyaSaati.sehirMi("UTC"))
        assertFalse(DunyaSaati.sehirMi("Australia/ACT"))
        assertTrue(DunyaSaati.sehirMi("Australia/Sydney"))
    }

    @Test
    fun kimliktenAd() {
        assertEquals("Buenos Aires", DunyaSaati.kimliktenAd("America/Argentina/Buenos_Aires"))
        assertEquals("Istanbul", DunyaSaati.kimliktenAd("Europe/Istanbul"))
    }

    @Test
    fun turkceAramaSadelesir() {
        assertEquals("istanbul", DunyaSaati.sadelestir("İstanbul"))
        assertEquals("istanbul", DunyaSaati.sadelestir("Istanbul"))
        assertEquals("cesme sogut", DunyaSaati.sadelestir("Çeşme Söğüt"))
    }

    @Test
    fun saatFarki() {
        val simdi = an("Europe/Istanbul", 2026, 10, 3, 12)
        // Ekim'de Londra yaz saatinde: İstanbul +3, Londra +1 → −2 sa
        assertEquals(-120, DunyaSaati.farkDk(tz("Europe/London"), tz("Europe/Istanbul"), simdi))
        // Hindistan +5:30 → +2 sa 30 dk
        assertEquals(150, DunyaSaati.farkDk(tz("Asia/Kolkata"), tz("Europe/Istanbul"), simdi))
        assertEquals(Triple(1, 2, 30), DunyaSaati.farkParcalari(150))
        assertEquals(Triple(-1, 2, 0), DunyaSaati.farkParcalari(-120))
        assertEquals(Triple(0, 0, 0), DunyaSaati.farkParcalari(0))
    }

    @Test
    fun gunFarki() {
        // İstanbul'da gece 23:00 → Tokyo'da ertesi gün, New York'ta aynı gün
        val gece = an("Europe/Istanbul", 2026, 10, 3, 23)
        assertEquals(1, DunyaSaati.gunFarki(tz("Asia/Tokyo"), tz("Europe/Istanbul"), gece))
        assertEquals(0, DunyaSaati.gunFarki(tz("America/New_York"), tz("Europe/Istanbul"), gece))
        // Sabah 02:00 → New York'ta önceki gün
        val sabah = an("Europe/Istanbul", 2026, 10, 3, 2)
        assertEquals(-1, DunyaSaati.gunFarki(tz("America/New_York"), tz("Europe/Istanbul"), sabah))
        // Yılbaşı gecesi: Tokyo yeni yılda
        val yilbasi = an("Europe/Istanbul", 2026, 12, 31, 22)
        assertEquals(1, DunyaSaati.gunFarki(tz("Asia/Tokyo"), tz("Europe/Istanbul"), yilbasi))
    }
}
