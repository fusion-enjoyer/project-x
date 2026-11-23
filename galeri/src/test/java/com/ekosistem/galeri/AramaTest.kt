package com.ekosistem.galeri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AramaTest {

    private val ekim = medya(id = 1, tarih = an(2024, 10, 5))
    private val agustos = medya(id = 2, ad = "Deniz.jpg", album = "Tatil", tarih = an(2023, 8, 20))
    private val video = medya(id = 3, ad = "VID_1.mp4", video = true, tarih = an(2024, 10, 9))
    private val ekran = medya(id = 4, ad = "Screenshot_2024.png", album = "Screenshots", mime = "image/png")
    private val hepsi = listOf(ekim, agustos, video, ekran)

    private fun ara(s: String) = Arama.ara(s, hepsi, UTC).map { it.id }

    @Test fun ayVeYil() {
        assertEquals(listOf(1L, 3L, 4L), ara("ekim 2024"))
        assertEquals(listOf(1L, 3L, 4L), ara("October 2024"))
        assertEquals(listOf(2L), ara("2023"))
    }

    @Test fun turkceHarflerVeBuyukKucuk() {
        assertEquals(listOf(2L), ara("AĞUSTOS"))
        assertEquals(listOf(2L), ara("agustos"))
        assertEquals("istanbul", Arama.sade("İSTANBUL"))
        assertEquals("isik", Arama.sade("Işık"))
    }

    @Test fun kelimeBasindanEslesir() {
        assertEquals(listOf(1L, 3L, 4L), ara("eki"))
        // "kim" "ekim"in ortasında: eşleşmemeli.
        assertEquals(emptyList<Long>(), ara("kim"))
    }

    @Test fun turVeAlbum() {
        assertEquals(listOf(3L), ara("video"))
        assertEquals(listOf(4L), ara("ekran görüntüsü"))
        assertEquals(listOf(4L), ara("screenshot"))
        assertEquals(listOf(2L), ara("tatil"))
        assertEquals(listOf(2L), ara("deniz"))
    }

    @Test fun bosSorguHicbirSeyDondurmez() {
        assertTrue(ara("   ").isEmpty())
        assertFalse(Arama.eslesir(emptyList(), "x"))
    }
}
