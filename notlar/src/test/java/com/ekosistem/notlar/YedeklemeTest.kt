package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YedeklemeTest {

    @Test
    fun icIceKlasorlerKorunur() {
        assertEquals(listOf("İş", "2026") to "plan.md", Yedekleme.notYolu("İş/2026/plan.md"))
        assertEquals(emptyList<String>() to "fikir.txt", Yedekleme.notYolu("fikir.txt"))
    }

    @Test
    fun gizliVeTehlikeliYollarAtlanir() {
        assertNull(Yedekleme.notYolu(".trash/eski.md"))
        assertNull(Yedekleme.notYolu("İş/.gecmis/a.md"))
        assertNull(Yedekleme.notYolu("../../disari.md"))
        assertNull(Yedekleme.notYolu("ekler/foto.jpg"))
        assertNull(Yedekleme.notYolu("İş/"))
    }

    @Test
    fun numaraliKopyalarAyniNotSayilir() {
        val adlar = listOf("plan.md", "plan-2.md", "plan-10.md", "planlama.md", "plan-a.md", "İş")
        assertEquals(
            listOf("plan.md", "plan-2.md", "plan-10.md"),
            Yedekleme.ayniNotAdaylari("plan.md", adlar)
        )
    }

    @Test
    fun txtNotMdOlarakAcildiysaBulunur() {
        assertEquals(listOf("fikir.md"), Yedekleme.ayniNotAdaylari("fikir.txt", listOf("fikir.md")))
    }

    @Test
    fun adlardakiOzelKarakterDesenBozmaz() {
        val adlar = listOf("a+b (1).md", "aab (1).md")
        assertEquals(listOf("a+b (1).md"), Yedekleme.ayniNotAdaylari("a+b (1).md", adlar))
    }

    @Test
    fun korunanTarihDisaridanDuzenleninceEskir() {
        val yedekTarihi = 1_700_000_000_000L
        val yazilma = 1_800_000_000_000L
        // Dosya damgayla aynı anda yazıldı: yedekteki tarih görünür.
        assertEquals(yedekTarihi, Prefs.korunanZaman(yedekTarihi, yazilma, yazilma - 500))
        assertEquals(yedekTarihi, Prefs.korunanZaman(yedekTarihi, yazilma, yazilma + 5_000))
        // Obsidian ya da Syncthing bir gün sonra düzenledi: dosyanın tarihi geçer.
        val sonra = yazilma + 86_400_000L
        assertEquals(sonra, Prefs.korunanZaman(yedekTarihi, yazilma, sonra))
        // Yazılma anı bilinmeyen eski damga eskisi gibi geçerli.
        assertEquals(yedekTarihi, Prefs.korunanZaman(yedekTarihi, 0L, sonra))
    }

    @Test
    fun windowsAyraciDaCalisir() {
        assertEquals(listOf("Okul") to "ödev.md", Yedekleme.notYolu("Okul\\ödev.md"))
    }
}
