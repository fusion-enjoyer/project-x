package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DosyaYaziciTest {

    @get:Rule
    val klasor = TemporaryFolder()

    @Test
    fun yeniDosyayiYazar() {
        val f = File(klasor.root, "not.md")
        assertTrue(DosyaYazici.atomikYaz(f, "merhaba".toByteArray()))
        assertEquals("merhaba", f.readText())
    }

    @Test
    fun varOlaninUstuneTamamenYazar() {
        val f = File(klasor.root, "not.md")
        f.writeText("çok daha uzun eski içerik")
        assertTrue(DosyaYazici.atomikYaz(f, "kısa".toByteArray()))
        assertEquals("kısa", f.readText())
    }

    @Test
    fun geriyeGeciciDosyaBirakmaz() {
        val f = File(klasor.root, "not.md")
        DosyaYazici.atomikYaz(f, "a".toByteArray())
        assertEquals(listOf("not.md"), klasor.root.list()!!.toList())
    }

    @Test
    fun yazamazsaEskiDosyayaDokunmaz() {
        // Hedefin yerinde bir klasör varsa yeniden adlandırma başarısız olur.
        val f = File(klasor.root, "not.md")
        f.mkdir()
        File(f, "icerik").writeText("korunmalı")
        assertFalse(DosyaYazici.atomikYaz(f, "yeni".toByteArray()))
        assertEquals("korunmalı", File(f, "icerik").readText())
        assertEquals(listOf("not.md"), klasor.root.list()!!.toList())
    }

    @Test
    fun olmayanKlasordeBasarisizOlur() {
        val f = File(klasor.root, "yok/not.md")
        assertFalse(DosyaYazici.atomikYaz(f, "a".toByteArray()))
        assertFalse(f.exists())
    }
}
