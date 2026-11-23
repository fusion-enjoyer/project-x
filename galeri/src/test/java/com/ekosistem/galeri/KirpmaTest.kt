package com.ekosistem.galeri

import org.junit.Assert.assertEquals
import org.junit.Test

class KirpmaTest {

    private val solUstCeyrek = Kutu(0f, 0f, 0.5f, 0.5f)

    @Test fun donmeyenKutuAyni() {
        assertEquals(solUstCeyrek, Kirpma.hamaCevir(solUstCeyrek, 0))
    }

    @Test fun doksanDerece() {
        // Saat yönünde 90° dönmüş resmin sol üstü, ham resmin sol altıdır.
        assertEquals(Kutu(0f, 0.5f, 0.5f, 1f), Kirpma.hamaCevir(solUstCeyrek, 90))
    }

    @Test fun yuzSeksenDerece() {
        assertEquals(Kutu(0.5f, 0.5f, 1f, 1f), Kirpma.hamaCevir(solUstCeyrek, 180))
    }

    @Test fun ikiYuzYetmisDerece() {
        // 270°: ekranın sol üstü ham resmin sağ üstü.
        assertEquals(Kutu(0.5f, 0f, 1f, 0.5f), Kirpma.hamaCevir(solUstCeyrek, 270))
        assertEquals(Kirpma.hamaCevir(solUstCeyrek, 270), Kirpma.hamaCevir(solUstCeyrek, -90))
    }

    @Test fun pikselBolgesiSinirIcinde() {
        val p = Kirpma.hamPiksel(Kutu(0f, 0f, 1f, 1f), 90, 4000, 3000)
        assertEquals(PikselKutu(0, 0, 4000, 3000), p)
        val k = Kirpma.hamPiksel(Kutu(0.25f, 0f, 0.75f, 1f), 90, 4000, 3000)
        // Dönmüş resmin ortadaki dikey şeridi, hamda ortadaki yatay şerittir.
        assertEquals(PikselKutu(0, 750, 4000, 2250), k)
    }

    @Test fun donmusBoyut() {
        assertEquals(3000 to 4000, Kirpma.donmusBoyut(4000, 3000, 90))
        assertEquals(4000 to 3000, Kirpma.donmusBoyut(4000, 3000, 180))
    }

    @Test fun ornekleme() {
        assertEquals(1, Kirpma.ornekleme(4000, 2000))
        // 48 MP: yarısı 12 MP (> 8 MP), dörtte biri 3 MP.
        assertEquals(4, Kirpma.ornekleme(8000, 6000))
        assertEquals(4, Kirpma.ekranOrneklemesi(8000, 6000, 1920))
        assertEquals(1, Kirpma.ekranOrneklemesi(1000, 800, 1920))
    }

    @Test fun oranliOrtaKutu() {
        val k = Kirpma.ortalaKutu(Kutu(0f, 0f, 400f, 200f), 1f)
        assertEquals(Kutu(100f, 0f, 300f, 200f), k)
        assertEquals(Kutu(0f, 0f, 400f, 200f), Kirpma.ortalaKutu(Kutu(0f, 0f, 400f, 200f), null))
    }

    @Test fun kaydirmaSinirdaDurur() {
        val sinir = Kutu(0f, 0f, 100f, 100f)
        val k = Kirpma.kaydir(Kutu(10f, 10f, 50f, 50f), 200f, -200f, sinir)
        assertEquals(Kutu(60f, 0f, 100f, 40f), k)
    }

    @Test fun serbestBoyutlamaEnAzBoyutuKorur() {
        val sinir = Kutu(0f, 0f, 100f, 100f)
        val sagAlt = Kirpma.Tutamak(sol = false, ust = false, sag = true, alt = true)
        val k = Kirpma.boyutla(Kutu(0f, 0f, 100f, 100f), sagAlt, 5f, 150f, sinir, null, 20f)
        assertEquals(Kutu(0f, 0f, 20f, 100f), k)
    }

    @Test fun oranliKoseOraniKorur() {
        val sinir = Kutu(0f, 0f, 400f, 400f)
        val sagAlt = Kirpma.Tutamak(sol = false, ust = false, sag = true, alt = true)
        val k = Kirpma.boyutla(Kutu(0f, 0f, 160f, 90f), sagAlt, 320f, 100f, sinir, 16f / 9f, 20f)
        assertEquals(16f / 9f, k.genislik / k.yukseklik, 0.001f)
        assertEquals(0f, k.sol, 0f)
        assertEquals(320f, k.genislik, 0.01f)
        // Kenar tutamağı oran kilitliyken bir şey yapmaz.
        val kenar = Kirpma.Tutamak(sol = false, ust = false, sag = true, alt = false)
        assertEquals(k, Kirpma.boyutla(k, kenar, 10f, 10f, sinir, 16f / 9f, 20f))
    }

    @Test fun oranliKoseSinirdanTasmaz() {
        val sinir = Kutu(0f, 0f, 400f, 300f)
        val sagAlt = Kirpma.Tutamak(sol = false, ust = false, sag = true, alt = true)
        val k = Kirpma.boyutla(Kutu(0f, 0f, 100f, 100f), sagAlt, 1000f, 1000f, sinir, 1f, 20f)
        assertEquals(Kutu(0f, 0f, 300f, 300f), k)
    }

    @Test fun tutamakBulma() {
        val k = Kutu(100f, 100f, 300f, 300f)
        assertEquals(Kirpma.Tutamak(true, true, false, false), Kirpma.tutamakBul(k, 105f, 98f, 24f))
        assertEquals(null, Kirpma.tutamakBul(k, 200f, 200f, 24f))
        assertEquals(Kirpma.Tutamak(false, false, true, false), Kirpma.tutamakBul(k, 298f, 200f, 24f))
    }
}
