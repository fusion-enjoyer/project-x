package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

class FarkTest {

    private fun turler(fark: List<Fark.Satir>) = fark.map { it.tur to it.metin }

    @Test
    fun ayniMetinlerdeFarkYok() {
        val satirlar = listOf("a", "b", "c")
        val fark = Fark.hesapla(satirlar, satirlar)
        assertEquals(0 to 0, Fark.sayac(fark))
        assertEquals(3, fark.size)
    }

    @Test
    fun ortadakiDegisiklikBulunur() {
        val fark = Fark.hesapla(listOf("a", "b", "c"), listOf("a", "x", "c"))
        assertEquals(
            listOf(
                Fark.AYNI to "a",
                Fark.SILINEN to "b",
                Fark.EKLENEN to "x",
                Fark.AYNI to "c"
            ),
            turler(fark)
        )
    }

    @Test
    fun bosNottanSurumeHepsiEklenir() {
        val fark = Fark.hesapla(emptyList(), listOf("a", "b"))
        assertEquals(2 to 0, Fark.sayac(fark))
    }

    @Test
    fun surumBossaHepsiSilinir() {
        val fark = Fark.hesapla(listOf("a", "b"), emptyList())
        assertEquals(0 to 2, Fark.sayac(fark))
    }

    @Test
    fun devasaNottaBlokFarkaDuser() {
        val a = List(1000) { "a$it" }
        val b = List(1000) { "b$it" }
        val fark = Fark.hesapla(a, b)
        assertEquals(1000 to 1000, Fark.sayac(fark))
    }
}
