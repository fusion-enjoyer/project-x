package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

class YardimTest {

    @Test
    fun anlatimVeOrneklerAyrilir() {
        val parcalar = Yardim.bol(
            "# Nasıl yazılır\nGiriş.\n\n~~~ornek\n**kalın**\n~~~\nArada.\n~~~yalin\n```\nkod\n```\n~~~\n"
        )
        assertEquals(listOf(Yardim.METIN, Yardim.ORNEK, Yardim.METIN, Yardim.YALIN), parcalar.map { it.tur })
        assertEquals("# Nasıl yazılır\nGiriş.", parcalar[0].icerik)
        assertEquals("**kalın**", parcalar[1].icerik)
        // Örneğin içindeki ``` kod bloğu çit sayılmaz.
        assertEquals("```\nkod\n```", parcalar[3].icerik)
    }

    @Test
    fun kapanmayanOrnekKaybolmaz() {
        assertEquals(listOf(Yardim.Parca(Yardim.ORNEK, "- [ ] görev")), Yardim.bol("~~~ornek\n- [ ] görev"))
    }
}
