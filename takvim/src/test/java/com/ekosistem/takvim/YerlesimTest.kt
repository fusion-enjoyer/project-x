package com.ekosistem.takvim

import org.junit.Assert.assertEquals
import org.junit.Test

class YerlesimTest {
    private fun p(no: Int, bas: Int, bit: Int) = Yerlesim.Parca(no, bas, bit)

    private fun al(l: List<Yerlesim.Sonuc>, no: Int) = l.first { it.no == no }

    @Test fun cakismayanlarTamGenislik() {
        val s = Yerlesim.yerlestir(listOf(p(0, 60, 120), p(1, 120, 180)))
        assertEquals(1, al(s, 0).sutunSayisi); assertEquals(0, al(s, 0).sutun)
        assertEquals(1, al(s, 1).sutunSayisi); assertEquals(0, al(s, 1).sutun)
    }

    @Test fun ikiCakisanYanYana() {
        val s = Yerlesim.yerlestir(listOf(p(0, 60, 150), p(1, 90, 180)))
        assertEquals(2, al(s, 0).sutunSayisi); assertEquals(2, al(s, 1).sutunSayisi)
        assertEquals(setOf(0, 1), setOf(al(s, 0).sutun, al(s, 1).sutun))
    }

    @Test fun obekBitinceSutunSayisiSifirlanir() {
        // 0 ve 1 çakışır; 2 sonra tek başına.
        val s = Yerlesim.yerlestir(listOf(p(0, 60, 150), p(1, 90, 180), p(2, 200, 260)))
        assertEquals(2, al(s, 0).sutunSayisi)
        assertEquals(1, al(s, 2).sutunSayisi)
    }

    @Test fun bosSutunYenidenKullanilir() {
        // 0: 60-240 uzun; 1: 60-120, 2: 120-180 aynı ikinci sütunu paylaşır.
        val s = Yerlesim.yerlestir(listOf(p(0, 60, 240), p(1, 60, 120), p(2, 120, 180)))
        assertEquals(2, al(s, 0).sutunSayisi)
        assertEquals(al(s, 1).sutun, al(s, 2).sutun)
        assertEquals(1 - al(s, 0).sutun, al(s, 1).sutun)
    }

    @Test fun ucuCakisan() {
        val s = Yerlesim.yerlestir(listOf(p(0, 60, 180), p(1, 70, 180), p(2, 80, 180)))
        assertEquals(3, s.map { it.sutunSayisi }.distinct().single())
        assertEquals(3, s.map { it.sutun }.distinct().size)
    }

    @Test fun bosListe() {
        assertEquals(0, Yerlesim.yerlestir(emptyList()).size)
    }
}
