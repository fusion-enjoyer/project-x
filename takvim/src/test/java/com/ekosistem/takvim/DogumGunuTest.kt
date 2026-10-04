package com.ekosistem.takvim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DogumGunuTest {
    @Test fun tarihCozme() {
        assertEquals(Triple(1990, 5, 17), DogumGunu.coz("1990-05-17"))
        assertEquals(Triple(null, 5, 17), DogumGunu.coz("--05-17"))
        assertEquals(Triple(1990, 5, 7), DogumGunu.coz("1990-5-7"))
        assertEquals(Triple(1985, 12, 1), DogumGunu.coz("19851201"))
        assertEquals(Triple(null, 3, 9), DogumGunu.coz("1604-03-09"))      // Google'ın "yıl yok" işareti
        assertEquals(Triple(1992, 2, 29), DogumGunu.coz("1992-02-29"))
        assertEquals(Triple(2001, 7, 4), DogumGunu.coz(" 2001-07-04T00:00:00Z "))
    }

    @Test fun gecersizTarih() {
        assertNull(DogumGunu.coz(null)); assertNull(DogumGunu.coz("")); assertNull(DogumGunu.coz("yarın"))
        assertNull(DogumGunu.coz("1990-13-01")); assertNull(DogumGunu.coz("1990-02-30")); assertNull(DogumGunu.coz("--04-31"))
    }

    @Test fun aralikVeYas() {
        val ayse = DogumGunu.Kisi(1, "Ayşe", 10, 7, 1990)
        val can = DogumGunu.Kisi(2, "Can", 10, 7, null)
        val g = DogumGunu.gecisler(listOf(can, ayse), Gun.gun(2026, 10, 1), Gun.gun(2026, 10, 31))
        assertEquals(listOf("Ayşe", "Can"), g.map { it.kisi.ad })
        assertEquals(36, g[0].yas)
        assertNull(g[1].yas)
        assertEquals(Gun.gun(2026, 10, 7), g[0].gun)
    }

    @Test fun yilSinirindaIkiYil() {
        val k = DogumGunu.Kisi(1, "Z", 1, 2, 2000)
        val g = DogumGunu.gecisler(listOf(k), Gun.gun(2026, 12, 20), Gun.gun(2027, 1, 10))
        assertEquals(1, g.size)
        assertEquals(Gun.gun(2027, 1, 2), g[0].gun)
        assertEquals(27, g[0].yas)
    }

    @Test fun yirmiDokuzSubat() {
        val k = DogumGunu.Kisi(1, "S", 2, 29, 2000)
        assertEquals(Gun.gun(2027, 2, 28), DogumGunu.gecisler(listOf(k), Gun.gun(2027, 2, 1), Gun.gun(2027, 3, 1))[0].gun)
        assertEquals(Gun.gun(2028, 2, 29), DogumGunu.gecisler(listOf(k), Gun.gun(2028, 2, 1), Gun.gun(2028, 3, 1))[0].gun)
    }

    @Test fun dogumYiliGelecekteyseYasYok() {
        val k = DogumGunu.Kisi(1, "B", 3, 3, 2026)
        assertNull(DogumGunu.gecisler(listOf(k), Gun.gun(2026, 3, 1), Gun.gun(2026, 3, 31))[0].yas)
    }

    @Test fun aralikDisiGelmez() {
        val k = DogumGunu.Kisi(1, "A", 6, 15, 1980)
        assertEquals(0, DogumGunu.gecisler(listOf(k), Gun.gun(2026, 10, 1), Gun.gun(2026, 10, 31)).size)
    }
}
