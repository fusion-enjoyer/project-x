package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

class SabitDuzeniTest {

    @Test
    fun tekNotButunWidgetiKaplar() {
        // Önceki hata: tek sabit not 3x2 widget'ın sol üstünde küçük kart olarak kalıyordu.
        assertEquals(listOf(1), SabitDuzeni.sec(1, 250, 200))
    }

    @Test
    fun enKucukBoyuttaTekKart() {
        assertEquals(1, SabitDuzeni.kapasite(70, 70))
        assertEquals(listOf(1), SabitDuzeni.sec(4, 70, 70))
    }

    @Test
    fun enFazlaDortKart() {
        assertEquals(4, SabitDuzeni.kapasite(600, 600))
        assertEquals(listOf(2, 2), SabitDuzeni.sec(9, 250, 200))
    }

    @Test
    fun ucNotUsteIkiAltaBirGenis() {
        assertEquals(listOf(2, 1), SabitDuzeni.sec(3, 250, 200))
    }

    @Test
    fun genisAlcakWidgetTekSatir() {
        // 4x1: kartlar yan yana; sığmayanlar gösterilmez.
        assertEquals(listOf(3), SabitDuzeni.sec(4, 330, 80))
        assertEquals(listOf(2), SabitDuzeni.sec(2, 330, 80))
    }

    @Test
    fun darUzunWidgetAltAlta() {
        assertEquals(listOf(1, 1), SabitDuzeni.sec(2, 90, 200))
    }

    @Test
    fun notYoksaKartYok() {
        assertEquals(emptyList<Int>(), SabitDuzeni.sec(0, 250, 200))
    }
}
