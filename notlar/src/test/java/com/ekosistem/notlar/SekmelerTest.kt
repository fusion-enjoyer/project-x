package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SekmelerTest {

    @Test
    fun ilkAcilisSekmeKurar() {
        val d = SekmeDurumu()
        d.ac("a")
        assertEquals(1, d.sekmeler.size)
        assertEquals("a", d.etkin?.adres)
    }

    @Test
    fun bagliNotaGidipGeriDonulur() {
        val d = SekmeDurumu()
        d.ac("a")
        d.git("b")
        d.git("c")
        assertEquals("b", d.gez(-1)?.adres)
        assertEquals("a", d.gez(-1)?.adres)
        assertNull(d.gez(-1))
        assertEquals("b", d.gez(1)?.adres)
    }

    @Test
    fun geridenYeniNotaGidinceIleriSilinir() {
        val d = SekmeDurumu()
        d.ac("a")
        d.git("b")
        d.gez(-1)
        d.git("c")
        assertEquals(listOf("a", "c"), d.etkin?.gecmis)
        assertFalse(d.etkin!!.ileriVar)
    }

    @Test
    fun baskaSekmedeAcikNotaGecilir() {
        val d = SekmeDurumu()
        d.ac("a")
        d.yeniSekme("b")
        d.ac("a")
        assertEquals(2, d.sekmeler.size)
        assertEquals("a", d.etkin?.adres)
    }

    @Test
    fun kapananEtkinSekmeninYerineYanindaki() {
        val d = SekmeDurumu()
        val a = d.ac("a")
        val b = d.yeniSekme("b")
        d.yeniSekme("c")
        d.sec(b.id)
        d.kapat(b.id)
        assertEquals("c", d.etkin?.adres)
        d.digerleriniKapat(a.id)
        assertEquals(listOf("a"), d.sekmeler.map { it.adres })
    }

    @Test
    fun silinenNotGecmistenCikarArdisikTekrarKalmaz() {
        val d = SekmeDurumu()
        d.ac("a")
        d.git("b")
        d.git("a")
        d.kaldir("b")
        assertEquals(listOf("a"), d.etkin?.gecmis)
        assertEquals(0, d.etkin?.konum)
    }

    @Test
    fun yalnizSilinenNotuOlanSekmeKapanir() {
        val d = SekmeDurumu()
        d.ac("a")
        d.yeniSekme("b")
        d.kaldir("b")
        assertEquals(listOf("a"), d.sekmeler.map { it.adres })
        assertEquals("a", d.etkin?.adres)
    }

    @Test
    fun yeniNotKaydedilinceAdresiGelir() {
        val d = SekmeDurumu()
        d.ac("a")
        d.git("yeni:1")
        assertTrue(d.adresDegistir(mapOf("yeni:1" to "b")))
        assertEquals(listOf("a", "b"), d.etkin?.gecmis)
    }

    @Test
    fun yazilipOkununcaAyni() {
        val d = SekmeDurumu()
        d.ac("content://a")
        d.git("content://b")
        d.yeniSekme("content://c")
        val okunan = SekmeDurumu.oku(d.yaz())
        assertEquals(d.etkinId, okunan.etkinId)
        assertEquals(d.sekmeler.map { it.gecmis }, okunan.sekmeler.map { it.gecmis })
        assertEquals(d.sekmeler.map { it.konum }, okunan.sekmeler.map { it.konum })
    }

    @Test
    fun bozukKayitBosDurum() {
        assertTrue(SekmeDurumu.oku("x\n1\t\n\tq").sekmeler.isEmpty())
        assertTrue(SekmeDurumu.oku(null).sekmeler.isEmpty())
    }

    @Test
    fun gecmisSinirli() {
        val d = SekmeDurumu()
        d.ac("0")
        for (i in 1..80) d.git(i.toString())
        assertEquals(SekmeDurumu.EN_UZUN_GECMIS, d.etkin?.gecmis?.size)
        assertEquals("80", d.etkin?.adres)
    }
}
