package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcindekilerTest {

    @Test
    fun basliklariSeviyeVeKonumuylaBulur() {
        val metin = "# Ana\nyazı\n## Alt başlık\n### Derin ##\n"
        val b = Icindekiler.basliklar(metin)
        assertEquals(listOf(1, 2, 3), b.map { it.seviye })
        assertEquals(listOf("Ana", "Alt başlık", "Derin"), b.map { it.metin })
        assertEquals(0, b[0].konum)
        assertEquals(metin.indexOf("## Alt"), b[1].konum)
        assertEquals(metin.indexOf("### Derin"), b[2].konum)
    }

    @Test
    fun kodBlogundakiDiyezSayilmaz() {
        val metin = "# Kurulum\n```\n# yorum satırı\n```\n## Sonrası"
        assertEquals(listOf("Kurulum", "Sonrası"), Icindekiler.basliklar(metin).map { it.metin })
    }

    @Test
    fun etiketVeBosBaslikBaslikDegil() {
        assertTrue(Icindekiler.basliklar("#etiket\n#\n#   \nmetin").isEmpty())
    }
}
