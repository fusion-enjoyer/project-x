package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GecmisSeciciTest {

    private fun g(yil: Int, ay: Int, gun: Int) = SonTarih.gunNumarasi(yil, ay, gun)
    private fun aday(ad: String, gun: Long) = GecmisSecici.Aday(ad, ad, gun)

    @Test
    fun enYakinYilOnceSecilir() {
        val bugun = g(2026, 10, 5)
        val s = GecmisSecici.sec(
            listOf(aday("iki yıl", g(2024, 10, 5)), aday("bir yıl", g(2025, 10, 5)), aday("dün", g(2026, 10, 4))),
            bugun
        )
        assertTrue(s is GecmisSecici.Secim.YilOnce)
        assertEquals("bir yıl", s!!.aday.anahtar)
        assertEquals(1, (s as GecmisSecici.Secim.YilOnce).yil)
    }

    @Test
    fun yilYoksaBirAyOnce() {
        val s = GecmisSecici.sec(listOf(aday("eylül", g(2026, 9, 5))), g(2026, 10, 5))
        assertTrue(s is GecmisSecici.Secim.AyOnce)
    }

    @Test
    fun ocaktaBirAyOnceAralik() {
        val s = GecmisSecici.sec(listOf(aday("aralık", g(2025, 12, 15))), g(2026, 1, 15))
        assertTrue(s is GecmisSecici.Secim.AyOnce)
    }

    @Test
    fun hicbiriYoksaEskiNotGunBoyuncaAyni() {
        val adaylar = listOf(aday("a", g(2026, 1, 1)), aday("b", g(2026, 2, 2)), aday("c", g(2026, 3, 3)))
        val bugun = g(2026, 10, 5)
        val s1 = GecmisSecici.sec(adaylar, bugun)
        val s2 = GecmisSecici.sec(adaylar.reversed(), bugun)
        assertTrue(s1 is GecmisSecici.Secim.Eski)
        assertEquals(s1!!.aday.anahtar, s2!!.aday.anahtar)
        // Ertesi gün başka bir not.
        val yarin = GecmisSecici.sec(adaylar, bugun + 1)
        assertTrue(yarin!!.aday.anahtar != s1.aday.anahtar)
    }

    @Test
    fun yeniNotlarSecilmez() {
        assertNull(GecmisSecici.sec(listOf(aday("dün", g(2026, 10, 4)), aday("bugün", g(2026, 10, 5))), g(2026, 10, 5)))
    }

    @Test
    fun gunlukBasligiTarihtir() {
        assertEquals(g(2025, 10, 5), GecmisSecici.gunlukGunu("2025-10-05"))
        assertNull(GecmisSecici.gunlukGunu("Toplantı 2025-10-05"))
    }
}
