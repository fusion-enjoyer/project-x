package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SifrelemeTest {

    // Testte hızlı olsun diye az tekrar; uygulama Sifreleme.TEKRAR kullanır.
    private fun anahtar(parola: String) = Sifreleme.yeniAnahtar(parola.toCharArray(), tekrar = 1000)

    private val kapak = "# 🔒 Gizli\n\nBu not şifreli."

    @Test
    fun sifrelenenGeriCozulur() {
        val metin = "# Gizli\nhesap: 1234 — çğüşöı 🙂\n- [ ] görev"
        val dosya = Sifreleme.sifrele(metin, anahtar("parola123"), kapak)
        assertTrue(Sifreleme.sifreliMi(dosya))
        assertFalse(dosya.contains("1234"))
        assertTrue(dosya.startsWith(kapak))
        val sonuc = Sifreleme.coz(dosya, "parola123".toCharArray()) as Sifreleme.Sonuc.Acildi
        assertEquals(metin, sonuc.metin)
    }

    @Test
    fun yanlisParolaAcmaz() {
        val dosya = Sifreleme.sifrele("gizli", anahtar("dogru"), kapak)
        assertTrue(Sifreleme.coz(dosya, "yanlis".toCharArray()) is Sifreleme.Sonuc.YanlisParola)
    }

    @Test
    fun ayniAnahtarlaHerKayitFarkliIv() {
        val a = anahtar("parola")
        assertNotEquals(Sifreleme.sifrele("aynı", a, kapak), Sifreleme.sifrele("aynı", a, kapak))
    }

    @Test
    fun acilinanAnahtarlaYenidenSifrelenir() {
        val dosya = Sifreleme.sifrele("ilk", anahtar("parola"), kapak)
        val acik = Sifreleme.coz(dosya, "parola".toCharArray()) as Sifreleme.Sonuc.Acildi
        val yeni = Sifreleme.sifrele("ikinci", acik.anahtar, kapak)
        assertEquals("ikinci", (Sifreleme.coz(yeni, "parola".toCharArray()) as Sifreleme.Sonuc.Acildi).metin)
    }

    @Test
    fun tekrarSayisiDegistirilinceAcilmaz() {
        val dosya = Sifreleme.sifrele("gizli", anahtar("parola"), kapak)
        val oynanmis = dosya.replace(":1000:", ":1001:")
        assertFalse(Sifreleme.coz(oynanmis, "parola".toCharArray()) is Sifreleme.Sonuc.Acildi)
    }

    @Test
    fun bozukVeriTanınır() {
        assertTrue(Sifreleme.coz("düz not", "p".toCharArray()) is Sifreleme.Sonuc.Bozuk)
        assertTrue(Sifreleme.coz("notlar-sifreli:9:1:a:b:c", "p".toCharArray()) is Sifreleme.Sonuc.Bozuk)
    }

    @Test
    fun onizlemeVeriSatiriniGostermez() {
        val dosya = Sifreleme.sifrele("gizli içerik", anahtar("p"), kapak)
        val (baslik, ozet) = NotDeposu.onizlemeCikar(dosya, "Gizli.md")
        assertEquals("🔒 Gizli", baslik)
        assertEquals("Bu not şifreli.", ozet)
    }
}
