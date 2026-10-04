package com.ekosistem.takvim

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetVerisiTest {
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private val bugun = Gun.gun(2026, 10, 4)

    private fun saatli(gun: Int, bas: Int, bit: Int, ad: String = "x") = Ornek.olustur(
        1, 1, ad, "", Gun.yerelAn(gun, bas, tz), Gun.yerelAn(gun, bit, tz), false, 0, false, tz
    )

    private fun tumGun(gun: Int, ad: String = "t") = Ornek.olustur(
        1, 1, ad, "", Gun.utcGunBasi(gun), Gun.utcGunBasi(gun + 1), true, 0, false, tz
    )

    private fun an(gun: Int, dk: Int) = Gun.yerelAn(gun, dk, tz)

    @Test fun gundemSirasiVeGunBasi() {
        val l = listOf(saatli(bugun, 600, 660, "b"), tumGun(bugun, "gun"), saatli(bugun, 540, 570, "a"), saatli(bugun + 2, 480, 540, "c"))
        val s = WidgetVerisi.gundemSatirlari(l, bugun, an(bugun, 0), 10)
        assertEquals(listOf("gun", "a", "b", "c"), s.map { it.ornek.baslik })
        assertEquals(listOf(true, false, false, true), s.map { it.gunBasi })
    }

    @Test fun bugunBitenAtlanir() {
        val l = listOf(saatli(bugun, 540, 570, "bitti"), saatli(bugun, 600, 660, "suruyor"), saatli(bugun, 900, 960, "gelecek"))
        val s = WidgetVerisi.gundemSatirlari(l, bugun, an(bugun, 620), 10)
        assertEquals(listOf("suruyor", "gelecek"), s.map { it.ornek.baslik })
    }

    @Test fun adetSiniri() {
        val l = (0 until 8).map { saatli(bugun + it, 600, 660, "e$it") }
        assertEquals(3, WidgetVerisi.gundemSatirlari(l, bugun, an(bugun, 0), 3).size)
    }

    @Test fun cokGunluGunBasinaGirer() {
        val coklu = Ornek.olustur(1, 1, "konf", "", Gun.utcGunBasi(bugun + 1), Gun.utcGunBasi(bugun + 4), true, 0, false, tz)
        val s = WidgetVerisi.gundemSatirlari(listOf(coklu), bugun, an(bugun, 0), 10)
        assertEquals(3, s.size)
        assertEquals(listOf(bugun + 1, bugun + 2, bugun + 3), s.map { it.gun })
    }

    @Test fun siradakiSurenVeyaYaklasan() {
        val l = listOf(saatli(bugun, 540, 600, "bitti"), saatli(bugun, 660, 720, "suruyor"), saatli(bugun, 900, 960, "sonra"))
        assertEquals("suruyor", WidgetVerisi.siradaki(l, an(bugun, 700), bugun)?.baslik)
        assertEquals("sonra", WidgetVerisi.siradaki(l, an(bugun, 730), bugun)?.baslik)
        assertNull(WidgetVerisi.siradaki(l, an(bugun, 1000), bugun))
    }

    @Test fun siradakiTumGunYalnizSaatliYoksa() {
        val l = listOf(tumGun(bugun, "dogum"), saatli(bugun, 900, 960, "toplanti"))
        assertEquals("toplanti", WidgetVerisi.siradaki(l, an(bugun, 600), bugun)?.baslik)
        assertEquals("dogum", WidgetVerisi.siradaki(listOf(tumGun(bugun, "dogum")), an(bugun, 600), bugun)?.baslik)
        assertNull(WidgetVerisi.siradaki(listOf(tumGun(bugun + 1, "yarin")), an(bugun, 600), bugun))
    }
}
