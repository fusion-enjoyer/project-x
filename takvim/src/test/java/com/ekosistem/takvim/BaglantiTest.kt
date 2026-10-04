package com.ekosistem.takvim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaglantiTest {
    @Test fun meet() {
        assertEquals("https://meet.google.com/abc-defg-hij", Baglanti.bul("Toplantı: https://meet.google.com/abc-defg-hij."))
        assertEquals("Meet", Baglanti.hizmet("https://meet.google.com/abc-defg-hij"))
    }

    @Test fun zoom() {
        assertEquals("https://us02web.zoom.us/j/123456789?pwd=AbC", Baglanti.bul(null, "Katıl: https://us02web.zoom.us/j/123456789?pwd=AbC, şifre 1"))
        assertEquals("Zoom", Baglanti.hizmet("https://us02web.zoom.us/j/1"))
    }

    @Test fun teams() {
        val url = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_XYZ%40thread.v2/0?context=%7b%7d"
        assertEquals(url, Baglanti.bul("<$url>"))
    }

    @Test fun konumOnceAciklamaSonra() {
        assertEquals("https://meet.jit.si/Oda", Baglanti.bul("https://meet.jit.si/Oda", "https://meet.google.com/aaa-bbbb-ccc"))
    }

    @Test fun ayniMetindeEnErkenKazanir() {
        assertEquals("https://zoom.us/j/1", Baglanti.bul("önce https://zoom.us/j/1 sonra https://meet.google.com/aaa-bbbb-ccc"))
    }

    @Test fun baglantiYoksa() {
        assertNull(Baglanti.bul("Ofis, 3. kat", "Gündem: bütçe https://example.com/rapor"))
        assertNull(Baglanti.bul(null, ""))
    }
}
