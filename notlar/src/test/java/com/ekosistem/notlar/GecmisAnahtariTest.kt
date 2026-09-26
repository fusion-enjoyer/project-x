package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GecmisAnahtariTest {

    @Test
    fun anaKlasordekiNotEskiAnahtariKorur() {
        // Önceki sürümlerin kaydettiği geçmiş kaybolmasın.
        assertEquals("fikir", NotDeposu.gecmisAnahtari(listOf("fikir.md")))
        assertEquals("liste", NotDeposu.gecmisAnahtari(listOf("liste.txt")))
    }

    @Test
    fun ayniAdliNotlarFarkliKlasordeAyrilir() {
        val is_ = NotDeposu.gecmisAnahtari(listOf("İş", "fikir.md"))
        val kisisel = NotDeposu.gecmisAnahtari(listOf("Kişisel", "fikir.md"))
        val kok = NotDeposu.gecmisAnahtari(listOf("fikir.md"))
        assertNotEquals(is_, kisisel)
        assertNotEquals(is_, kok)
        assertEquals("İş__fikir", is_)
    }

    @Test
    fun icIceKlasorlerDeAyrilir() {
        assertEquals("İş__2026__plan", NotDeposu.gecmisAnahtari(listOf("İş", "2026", "plan.md")))
    }
}
