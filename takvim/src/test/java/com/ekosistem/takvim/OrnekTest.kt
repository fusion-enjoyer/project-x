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

class AramaTest {
    private val tz = java.util.TimeZone.getTimeZone("Europe/Istanbul")
    private fun o(id: Long, ad: String, gun: Int, tekrarli: Boolean = false, konum: String = "", aciklama: String = "") =
        Ornek.olustur(id, 1, ad, konum, Gun.yerelAn(gun, 600, tz), Gun.yerelAn(gun, 660, tz), false, 0, tekrarli, tz, aciklama)
    private val bugun = Gun.gun(2026, 10, 4)
    private val simdi = Gun.yerelAn(bugun, 700, tz)

    @Test fun turkceHarfDuyarsiz() {
        val toplanti = o(1, "Toplantı", bugun + 1, konum = "Işık Sokak")
        assertTrue(Arama.eslesir(Arama.kelimeler("toplanti"), toplanti))
        assertTrue(Arama.eslesir(Arama.kelimeler("TOPLANTI"), toplanti))
        assertTrue(Arama.eslesir(Arama.kelimeler("isik sokak"), toplanti))
        assertFalse(Arama.eslesir(Arama.kelimeler("toplanti ankara"), toplanti))
    }

    @Test fun aciklamadaDaAranir() {
        assertTrue(Arama.eslesir(Arama.kelimeler("ilaç"), o(1, "Doktor", bugun, aciklama = "İlaçları unutma")))
    }

    @Test fun bosSorguHicbirSeyBulmaz() {
        assertFalse(Arama.eslesir(emptyList(), o(1, "x", bugun)))
        assertEquals(emptyList<String>(), Arama.kelimeler("   "))
    }

    @Test fun siralamaYaklasanSonraGecmis() {
        val l = listOf(o(1, "eski", bugun - 5), o(2, "yarin", bugun + 1), o(3, "sonra", bugun + 9), o(4, "dun", bugun - 1))
        val (y, g) = Arama.sirala(l, simdi)
        assertEquals(listOf("yarin", "sonra"), y.map { it.baslik })
        assertEquals(listOf("dun", "eski"), g.map { it.baslik })
    }

    @Test fun tekrarlayanBirKezGorunur() {
        val l = (0 until 5).map { o(7, "haftalik", bugun + 1 + it * 7, tekrarli = true) } +
            (1..3).map { o(7, "haftalik", bugun - it * 7, tekrarli = true) } + o(8, "tek", bugun + 2)
        val (y, g) = Arama.sirala(l, simdi)
        assertEquals(listOf("haftalik", "tek"), y.map { it.baslik })
        assertEquals(0, g.size)   // geçmiş örnekleri yaklaşanda zaten var
    }
}
