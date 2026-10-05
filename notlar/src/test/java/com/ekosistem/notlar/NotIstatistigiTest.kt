package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

class NotIstatistigiTest {

    @Test
    fun markdownIsaretleriKelimeSayilmaz() {
        assertEquals(0, NotIstatistigi.kelime(""))
        assertEquals(3, NotIstatistigi.kelime("## Alışveriş listesi\n- [ ] süt"))
        assertEquals(2, NotIstatistigi.kelime("---\n**kalın** *italik*"))
        assertEquals(2, NotIstatistigi.kelime("e-posta: ali@ornek.com"))
    }

    @Test
    fun okumaSuresiYukariYuvarlanir() {
        assertEquals(0, NotIstatistigi.okumaDakikasi(50))
        assertEquals(1, NotIstatistigi.okumaDakikasi(100))
        assertEquals(1, NotIstatistigi.okumaDakikasi(200))
        assertEquals(2, NotIstatistigi.okumaDakikasi(201))
    }
}
