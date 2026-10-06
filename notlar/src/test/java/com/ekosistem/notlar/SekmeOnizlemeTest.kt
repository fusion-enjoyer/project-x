package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

class SekmeOnizlemeTest {

    @Test
    fun markdownIsaretleriSadelesir() {
        assertEquals("Plan" to true, SekmeEkrani.sadelestir("## Plan"))
        assertEquals("☐ süt al" to false, SekmeEkrani.sadelestir("- [ ] süt al"))
        assertEquals("☑ ekmek" to false, SekmeEkrani.sadelestir("- [x] ekmek"))
        assertEquals("• madde" to false, SekmeEkrani.sadelestir("- madde"))
        assertEquals("  • alt madde" to false, SekmeEkrani.sadelestir("    - alt madde"))
        assertEquals("kalın ve kod" to false, SekmeEkrani.sadelestir("**kalın** ve `kod`"))
        assertEquals("Fatura notu" to false, SekmeEkrani.sadelestir("[[Fatura]] notu"))
        assertEquals("site" to false, SekmeEkrani.sadelestir("[site](https://ornek.com)"))
        assertEquals("🖼" to false, SekmeEkrani.sadelestir("![](ekler/a.jpg)"))
    }

    @Test
    fun obsidianKutusununTuruGorunmez() {
        assertEquals("│ Nereden başlamalı" to false, SekmeEkrani.sadelestir("> [!tip] Nereden başlamalı"))
        assertEquals("│ alıntı" to false, SekmeEkrani.sadelestir("> alıntı"))
    }

    @Test
    fun diyezBasliksizSatirBaslikSayilmaz() {
        assertEquals("#etiket" to false, SekmeEkrani.sadelestir("#etiket"))
    }
}
