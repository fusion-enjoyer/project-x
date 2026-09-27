package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AramaTest {

    @Test
    fun turkceKarakterlerdenBagimsizBulur() {
        val metin = Arama.sadelestir("Pazartesi TOPLANTI, Işık ve Çiğdem'le görüşme")
        assertTrue(metin.contains(Arama.ifade("toplanti")!!))
        assertTrue(metin.contains(Arama.ifade("toplantı")!!))
        assertTrue(metin.contains(Arama.ifade("isik")!!))
        assertTrue(metin.contains(Arama.ifade("ciğdem")!!))
        assertTrue(metin.contains(Arama.ifade("GORUSME")!!))
    }

    @Test
    fun uzunlukDegismez() {
        // Vurgulama, sadeleşmiş metinde bulunan konumu asıl metne uygular.
        for (s in listOf("İstanbul", "ISPARTA", "straße", "ǅemal", "Ünlü Şarkı", "")) {
            assertEquals(s, s.length, Arama.sadelestir(s).length)
        }
    }

    @Test
    fun bosAramaYokSayilir() {
        assertNull(Arama.ifade(null))
        assertNull(Arama.ifade("   "))
        assertEquals("kod", Arama.ifade("  Kod "))
    }
}
