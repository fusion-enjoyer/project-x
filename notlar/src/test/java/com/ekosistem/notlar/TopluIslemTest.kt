package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopluIslemTest {

    @Test
    fun notlarBosSatirlaBirlesir() {
        assertEquals(
            "# Bir\nmetin\n\n# İki\n",
            TopluIslem.birlestir(listOf("# Bir\nmetin\n\n\n", "", "# İki"))
        )
    }

    @Test
    fun etiketAyriSatiraEklenir() {
        assertEquals("# Not\nmetin\n\n#iş\n", TopluIslem.etiketEkle("# Not\nmetin\n", "iş"))
    }

    @Test
    fun etiketSatiriVarsaYaninaEklenir() {
        assertEquals("# Not\n\n#ev #iş\n", TopluIslem.etiketEkle("# Not\n\n#ev\n", "iş"))
    }

    @Test
    fun ayniEtiketTekrarEklenmez() {
        assertNull(TopluIslem.etiketEkle("# Not #İŞ", "iş"))
    }

    @Test
    fun etiketTemizlenir() {
        assertEquals("işplanı", TopluIslem.etiketTemizle(" #iş planı! "))
    }
}
