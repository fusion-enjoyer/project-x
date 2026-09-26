package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google Takeout'un Keep JSON'larından alınmış örneklerle. */
class KeepAktarmaTest {

    private val metinNotu = """
        {
          "color": "DEFAULT",
          "isTrashed": false,
          "isPinned": true,
          "isArchived": false,
          "textContent": "Pazartesi 10:00\nOda 3",
          "title": "Toplantı",
          "userEditedTimestampUsec": 1700000000123456,
          "createdTimestampUsec": 1690000000000000,
          "labels": [{"name": "İş"}, {"name": "Proje & Plan"}],
          "annotations": [{"source": "WEBLINK", "url": "https://ornek.com/a", "title": "Örnek"}]
        }
    """.trimIndent()

    private val listeNotu = """
        {
          "isTrashed": false, "isPinned": false, "isArchived": true,
          "title": "Market",
          "listContent": [
            {"text": "Süt", "isChecked": false},
            {"text": "Ekmek\nkepekli", "isChecked": true},
            {"text": "", "isChecked": false}
          ],
          "userEditedTimestampUsec": 1700000000000000
        }
    """.trimIndent()

    @Test
    fun metinNotuCevrilir() {
        val n = KeepAktarma.cozumle(metinNotu, "Takeout/Keep/Toplantı.json")!!
        assertEquals("Toplantı", n.baslik)
        assertTrue(n.sabit)
        assertFalse(n.arsivde)
        assertEquals(1700000000123L, n.degistirilme)
        assertEquals(listOf("İş", "Proje-Plan"), n.etiketler)
        assertEquals(
            "Toplantı\nPazartesi 10:00\nOda 3\nhttps://ornek.com/a\n\n#İş #Proje-Plan\n",
            KeepAktarma.metinOlustur(n, emptyList())
        )
    }

    @Test
    fun listeOnayKutusunaDoner() {
        val n = KeepAktarma.cozumle(listeNotu, "Market.json")!!
        assertTrue(n.arsivde)
        assertEquals("- [ ] Süt\n- [x] Ekmek kepekli", n.govde)
        // Oluşan metin uygulamanın kendi görev deseniyle tanınmalı.
        val satirlar = KeepAktarma.metinOlustur(n, emptyList()).lines()
        assertEquals(2, satirlar.count { MarkdownBicimci.ONAY.containsMatchIn(it) })
    }

    @Test
    fun copKutusundakiAlinmaz() {
        assertNull(KeepAktarma.cozumle("""{"isTrashed": true, "textContent": "x", "title": "a"}""", "a.json"))
    }

    @Test
    fun keepOlmayanJsonAtlanir() {
        assertNull(KeepAktarma.cozumle("""{"name": "takvim", "events": []}""", "takvim.json"))
        assertNull(KeepAktarma.cozumle("bozuk {", "x.json"))
    }

    @Test
    fun gorselAlinirSesAtlanir() {
        val json = """
            {"isTrashed": false, "title": "", "textContent": "",
             "attachments": [
               {"filePath": "1a2b3c.jpeg", "mimetype": "image/jpeg"},
               {"filePath": "ses.3gp", "mimetype": "audio/3gpp"}
             ],
             "userEditedTimestampUsec": 1}
        """.trimIndent()
        val n = KeepAktarma.cozumle(json, "Takeout/Keep/2023-05-01T10_00_00.000Z.json")!!
        assertEquals(listOf("1a2b3c.jpeg"), n.gorseller)
        assertEquals(1, n.atlananEk)
        // Yalnızca görsel içeren not dosya adını başlık alır.
        assertEquals("2023-05-01T10_00_00.000Z", n.baslik)
        assertEquals(
            "2023-05-01T10_00_00.000Z\n![](ekler/1a2b3c.jpg)\n",
            KeepAktarma.metinOlustur(n, listOf("ekler/1a2b3c.jpg"))
        )
    }

    @Test
    fun nullAlanlarMetneSizmaz() {
        val n = KeepAktarma.cozumle("""{"isTrashed": false, "title": null, "textContent": "gövde"}""", "a.json")!!
        assertEquals("", n.baslik)
        assertFalse(KeepAktarma.metinOlustur(n, emptyList()).contains("null"))
    }

    @Test
    fun metindeOlanBaglantiTekrarlanmaz() {
        val json = """
            {"isTrashed": false, "title": "t", "textContent": "bkz https://a.com",
             "annotations": [{"url": "https://a.com"}]}
        """.trimIndent()
        assertEquals("bkz https://a.com", KeepAktarma.cozumle(json, "t.json")!!.govde)
    }

    @Test
    fun ayniNotAyniAnahtariVerir() {
        val a = KeepAktarma.cozumle(metinNotu, "Takeout/Keep/Toplantı.json")!!
        val b = KeepAktarma.cozumle(metinNotu, "Takeout/Keep/Toplantı.json")!!
        val c = KeepAktarma.cozumle(listeNotu, "Market.json")!!
        assertEquals(a.anahtar, b.anahtar)
        assertNotEquals(a.anahtar, c.anahtar)
    }

    @Test
    fun etiketAdiTemizlenir() {
        assertEquals("İş-Proje", KeepAktarma.etiketAdi("  İş   Proje "))
        assertEquals("a-b", KeepAktarma.etiketAdi("a & b"))
        assertEquals("", KeepAktarma.etiketAdi("!!!"))
        // Temizlenen ad uygulamanın etiket deseniyle tam eşleşmeli.
        val etiket = KeepAktarma.etiketAdi("Okul / Ödev #1")
        assertEquals(etiket, MarkdownBicimci.ETIKET.find("#$etiket")!!.groupValues[1])
    }

    @Test
    fun ekAnahtariUzantidanBagimsiz() {
        assertEquals(KeepAktarma.ekAnahtari("Takeout/Keep/1A2B.jpg"), KeepAktarma.ekAnahtari("1a2b.jpeg"))
    }
}
