package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Not içeriğinden görev, etiket, bağlantı ve görsel ayıklayan desenler. */
class MarkdownDesenTest {

    @Test
    fun onayKutusuBulunur() {
        assertEquals(" ", MarkdownBicimci.ONAY.find("- [ ] süt al")!!.groupValues[2])
        assertEquals("x", MarkdownBicimci.ONAY.find("  - [x] bitti")!!.groupValues[2])
        assertNull(MarkdownBicimci.ONAY.find("- sade madde"))
        assertNull(MarkdownBicimci.ONAY.find("metin - [ ] ortada"))
    }

    @Test
    fun etiketlerAyiklanir() {
        val bulunan = MarkdownBicimci.ETIKET.findAll("#iş toplantı #proje-2026 ve #çğüşöı")
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("iş", "proje-2026", "çğüşöı"), bulunan)
    }

    @Test
    fun adresParcasiEtiketSayilmaz() {
        assertEquals(0, MarkdownBicimci.ETIKET.findAll("site.com/#bolum ve a#b").count())
    }

    @Test
    fun wikiBaglantisiBulunurGorselGommeSayilmaz() {
        val bulunan = MarkdownBicimci.BAGLANTI.findAll("bkz. [[Toplantı notu]] ve ![[resim.png]]")
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("Toplantı notu"), bulunan)
    }

    @Test
    fun gorselBaglantilariBulunur() {
        assertEquals("../ekler/a b.jpg", MarkdownBicimci.GORSEL.find("![](../ekler/a b.jpg)")!!.groupValues[2])
        assertEquals("foto.png", MarkdownBicimci.GORSEL_WIKI.find("![[foto.png]]")!!.groupValues[1])
    }

    @Test
    fun webAdresiSondakiNoktalamaHaric() {
        val bul = { m: String -> MarkdownBicimci.URL.findAll(m).map { it.value }.toList() }
        assertEquals(listOf("https://ornek.com/a?b=1"), bul("bkz. https://ornek.com/a?b=1."))
        assertEquals(listOf("http://x.org"), bul("(http://x.org), sonra"))
        assertEquals(listOf("https://tr.wikipedia.org/wiki/Ankara"), bul("https://tr.wikipedia.org/wiki/Ankara!"))
        assertEquals(emptyList<String>(), bul("e-posta: ali@https://x.com yok, ftp://x.com de yok"))
    }

    @Test
    fun markdownBaglantisiMetinVeAdresVerir() {
        val m = MarkdownBicimci.MD_BAGLANTI.find("oku: [Güzel yazı](https://blog.org/y) bitti")!!
        assertEquals("Güzel yazı", m.groupValues[1])
        assertEquals("https://blog.org/y", m.groupValues[2])
        // Görsel sözdizimi bağlantı sayılmaz.
        assertNull(MarkdownBicimci.MD_BAGLANTI.find("![](https://x.org/a.png)"))
    }

    @Test
    fun kodCitiTaninir() {
        assertTrue(MarkdownBicimci.KOD_CITI.containsMatchIn("```"))
        assertTrue(MarkdownBicimci.KOD_CITI.containsMatchIn("  ```kotlin"))
        assertFalse(MarkdownBicimci.KOD_CITI.containsMatchIn("metin ``` ortada"))
    }
}
