package com.ekosistem.takvim

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrnekTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    private fun zamanli(bas: Long, bit: Long) =
        Ornek.olustur(1, 1, "x", "", bas, bit, false, 0, false, berlin)

    private fun tumGun(ilk: Int, sonDahil: Int) =
        Ornek.olustur(1, 1, "x", "", Gun.utcGunBasi(ilk), Gun.utcGunBasi(sonDahil + 1), true, 0, false, berlin)

    @Test fun ayniGunIcinde() {
        val g = Gun.gun(2026, 10, 4)
        val o = zamanli(Gun.yerelAn(g, 9 * 60, berlin), Gun.yerelAn(g, 10 * 60 + 30, berlin))
        assertEquals(g, o.ilkGun); assertEquals(g, o.sonGun)
        assertEquals(540, o.baslangicDk); assertEquals(630, o.bitisDk)
        assertFalse(o.cokGunlu)
    }

    @Test fun geceYarisindaBitenErtesiGunuAlmaz() {
        val g = Gun.gun(2026, 10, 4)
        val o = zamanli(Gun.yerelAn(g, 22 * 60, berlin), Gun.yerelAn(g + 1, 0, berlin))
        assertEquals(g, o.sonGun)
        assertEquals(1440, o.bitisDk)
    }

    @Test fun geceyiAsan() {
        val g = Gun.gun(2026, 10, 4)
        val o = zamanli(Gun.yerelAn(g, 23 * 60, berlin), Gun.yerelAn(g + 1, 90, berlin))
        assertEquals(g, o.ilkGun); assertEquals(g + 1, o.sonGun)
        assertTrue(o.cokGunlu)
        assertEquals(1380, o.gunBaslangicDk(g)); assertEquals(1440, o.gunBitisDk(g))
        assertEquals(0, o.gunBaslangicDk(g + 1)); assertEquals(90, o.gunBitisDk(g + 1))
    }

    @Test fun suresiz() {
        val g = Gun.gun(2026, 10, 4)
        val an = Gun.yerelAn(g, 600, berlin)
        val o = zamanli(an, an)
        assertEquals(g, o.sonGun); assertEquals(600, o.baslangicDk); assertEquals(600, o.bitisDk)
    }

    @Test fun tumGunTekGun() {
        val g = Gun.gun(2026, 10, 4)
        val o = tumGun(g, g)
        assertEquals(g, o.ilkGun); assertEquals(g, o.sonGun)
        assertEquals(0, o.baslangicDk); assertEquals(1440, o.bitisDk)
    }

    @Test fun tumGunBirdenCokGun() {
        val g = Gun.gun(2026, 12, 30)
        val o = tumGun(g, g + 3)
        assertEquals(g, o.ilkGun); assertEquals(g + 3, o.sonGun)
        assertTrue(o.gunuIcerir(g + 2)); assertFalse(o.gunuIcerir(g + 4))
    }

    @Test fun tumGunDilimdenEtkilenmez() {
        // UTC gece yarısı Berlin'de 02:00; yine de aynı takvim günü kalmalı.
        val g = Gun.gun(2026, 7, 1)
        val o = Ornek.olustur(1, 1, "x", "", Gun.utcGunBasi(g), Gun.utcGunBasi(g + 1), true, 0, false,
            TimeZone.getTimeZone("Pacific/Auckland"))
        assertEquals(g, o.ilkGun); assertEquals(g, o.sonGun)
    }
}
