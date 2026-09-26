package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TaslaklarTest {

    @get:Rule
    val klasor = TemporaryFolder()

    private fun taslaklar() = Taslaklar(File(klasor.root, "taslaklar"))

    private val adres = "content://com.android.externalstorage.documents/tree/primary%3ANotlar/document/primary%3ANotlar%2Ffikir.md"

    @Test
    fun yazilanTaslakGeriOkunur() {
        val t = taslaklar()
        assertTrue(t.yaz(adres, "Başlık\n- [ ] görev\n\nson satır"))
        val okunan = t.oku(adres)!!
        assertEquals(adres, okunan.adres)
        assertEquals("Başlık\n- [ ] görev\n\nson satır", okunan.metin)
    }

    @Test
    fun yeniNotTaslagiAdressizSaklanir() {
        val t = taslaklar()
        t.yaz(null, "henüz dosyası yok")
        assertEquals("henüz dosyası yok", t.oku(null)!!.metin)
        assertNull(t.oku(null)!!.adres)
        // Yeni not taslağı başka bir notun taslağı gibi okunmamalı.
        assertNull(t.oku(adres))
    }

    @Test
    fun baskaNotunTaslagiOkunmaz() {
        val t = taslaklar()
        t.yaz(adres, "a")
        assertNull(t.oku(adres + "x"))
    }

    @Test
    fun bosIcerikDeKorunur() {
        val t = taslaklar()
        t.yaz(adres, "")
        assertEquals("", t.oku(adres)!!.metin)
    }

    @Test
    fun silinenTaslakKalmaz() {
        val t = taslaklar()
        t.yaz(adres, "a")
        t.sil(adres)
        assertNull(t.oku(adres))
    }

    @Test
    fun tasininaYeniAdreseGecer() {
        val t = taslaklar()
        val yeni = "$adres-tasindi"
        t.yaz(adres, "içerik")
        t.tasi(adres, yeni)
        assertNull(t.oku(adres))
        assertEquals("içerik", t.oku(yeni)!!.metin)
    }

    @Test
    fun taslakYoksaNullDoner() {
        assertNull(taslaklar().oku(adres))
        assertNull(taslaklar().oku(null))
    }
}
