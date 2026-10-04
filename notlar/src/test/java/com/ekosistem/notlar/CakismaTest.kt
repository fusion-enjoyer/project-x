package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CakismaTest {

    @Test
    fun syncthingKopyasiTaninir() {
        val b = Cakisma.coz("Alışveriş.sync-conflict-20261004-153012-ABCDEF7.md")!!
        assertEquals("Alışveriş.md", b.asilAd)
        assertEquals("20261004", b.tarih)
        assertEquals("153012", b.saat)
        assertEquals("ABCDEF7", b.cihaz)
    }

    @Test
    fun noktaliAdlarDaCalisir() {
        assertEquals("v1.2 notları.md", Cakisma.coz("v1.2 notları.sync-conflict-20260101-000000-1234567.md")!!.asilAd)
    }

    @Test
    fun siradanNotCakismaDegil() {
        assertNull(Cakisma.coz("Alışveriş.md"))
        assertNull(Cakisma.coz("sync-conflict hakkında.md"))
        assertNull(Cakisma.coz(".sync-conflict-20261004-153012-ABCDEF7.md"))
    }

    @Test
    fun birlestirmedeSatirKaybolmaz() {
        val bu = "# Liste\nsüt\nekmek\nyumurta"
        val diger = "# Liste\nsüt\npeynir\nyumurta"
        val sonuc = Cakisma.birlestir(bu, diger).lines()
        assertEquals("# Liste", sonuc.first())
        assertEquals("yumurta", sonuc.last())
        assertEquals(setOf("# Liste", "süt", "ekmek", "peynir", "yumurta"), sonuc.toSet())
        assertEquals(5, sonuc.size)
    }
}
