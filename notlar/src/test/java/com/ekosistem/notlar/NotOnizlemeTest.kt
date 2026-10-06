package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotOnizlemeTest {

    private fun metin(satir: String) = NotOnizleme.coz(satir).metin

    @Test
    fun kutuIsaretiOzetteGorunmez() {
        // Önceki hata: kartta ve widget'ta "[!tip] Nereden başlamalı" yazıyordu.
        assertEquals("Nereden başlamalı", NotOnizleme.sade("> [!tip] Nereden başlamalı"))
        assertEquals("Uyarı", NotOnizleme.sade("> [!warning]- Uyarı"))
        // Başlıksız kutuda editördeki gibi türün adı başlıktır.
        assertEquals("tip", NotOnizleme.sade("> [!tip]"))
        assertEquals("tip", NotOnizleme.coz("> [!tip] Başlık").kutuTuru)
    }

    @Test
    fun ozetteIsaretlerGizli() {
        assertEquals("kalın ve eğik", NotOnizleme.sade("**kalın** ve *eğik*"))
        assertEquals("çizik kod", NotOnizleme.sade("~~çizik~~ `kod`"))
        assertEquals("Fatura notu", NotOnizleme.sade("[[Fatura]] notu"))
        assertEquals("Ödemeler", NotOnizleme.sade("[[Fatura|Ödemeler]]"))
        assertEquals("site", NotOnizleme.sade("[site](https://ornek.com)"))
        assertEquals("süt al", NotOnizleme.sade("- [ ] süt al"))
        assertEquals("madde", NotOnizleme.sade("- madde"))
        assertEquals("alıntı", NotOnizleme.sade("> alıntı"))
        assertEquals("Plan", NotOnizleme.sade("## Plan"))
        assertEquals("", NotOnizleme.sade("![](ekler/a.jpg)"))
        assertEquals("", NotOnizleme.sade("---"))
    }

    @Test
    fun etiketVeAdresMetindeKalir() {
        assertEquals("#proje toplantı", NotOnizleme.sade("#proje toplantı"))
        assertEquals("bkz https://ornek.com", NotOnizleme.sade("bkz https://ornek.com"))
    }

    @Test
    fun alttanCizgiliDosyaAdiBozulmaz() {
        assertEquals("dosya_adi_yeni.md", NotOnizleme.sade("dosya_adi_yeni.md"))
        assertEquals("vurgu", NotOnizleme.sade("_vurgu_"))
    }

    @Test
    fun onizlemedeOnekler() {
        assertEquals("☐ süt al", metin("- [ ] süt al"))
        assertEquals("☑ ekmek", metin("- [x] ekmek"))
        assertEquals("• madde", metin("- madde"))
        assertEquals("  • alt madde", metin("    - alt madde"))
        assertEquals("▍ alıntı", metin("> alıntı"))
        assertEquals("1. birinci", metin("1. birinci"))
    }

    @Test
    fun bicimBayraklari() {
        val parcalar = NotOnizleme.coz("a **b *c* e** [[d]]").parcalar
        assertEquals(NotOnizleme.KALIN, parcalar.first { it.metin == "b " }.bicim)
        assertEquals(NotOnizleme.KALIN or NotOnizleme.EGIK, parcalar.first { it.metin == "c" }.bicim)
        assertEquals(NotOnizleme.BAGLANTI, parcalar.first { it.metin == "d" }.bicim)
        assertEquals(2, NotOnizleme.coz("## Plan").baslik)
        assertNull(NotOnizleme.coz("## Plan").kutuTuru)
    }
}
