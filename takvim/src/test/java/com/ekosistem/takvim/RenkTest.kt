package com.ekosistem.takvim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenkTest {
    @Test fun yeterliKontrastDegismez() {
        assertEquals(0xFF0F766E.toInt(), Renk.yuzey(0xFF0F766E.toInt(), false))
        assertEquals(0xFF2DD4BF.toInt(), Renk.yuzey(0xFF2DD4BF.toInt(), true))
    }

    @Test fun koyuRenkGecedeAcilir() {
        val lacivert = 0xFF1A237E.toInt()
        assertTrue(Renk.kontrast(lacivert, 0xFF000000.toInt()) < 3.0)
        val y = Renk.yuzey(lacivert, true)
        assertTrue(Renk.kontrast(y, 0xFF000000.toInt()) >= 3.0)
    }

    @Test fun acikRenkGunduzKoyulasir() {
        val sari = 0xFFFFEB3B.toInt()
        assertTrue(Renk.kontrast(sari, 0xFFFFFFFF.toInt()) < 3.0)
        val y = Renk.yuzey(sari, false)
        assertTrue(Renk.kontrast(y, 0xFFFFFFFF.toInt()) >= 3.0)
    }

    @Test fun saydamlikAtilir() {
        assertEquals(0xFF0F766E.toInt(), Renk.yuzey(0x000F766E, false))
    }

    @Test fun kontrastBilinenDegerler() {
        assertEquals(21.0, Renk.kontrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, Renk.kontrast(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0001)
    }
}
