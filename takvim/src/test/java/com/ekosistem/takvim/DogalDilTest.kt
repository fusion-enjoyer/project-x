package com.ekosistem.takvim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DogalDilTest {
    private val bugun = Gun.gun(2026, 10, 4)   // Pazar
    private val simdi = 10 * 60

    private fun coz(s: String) = DogalDil.coz(s, bugun, simdi)
    private fun g(y: Int, a: Int, d: Int) = Gun.gun(y, a, d)

    @Test fun duzMetinBulunmaz() {
        val s = coz("Diş randevusu")
        assertFalse(s.bulundu)
        assertEquals("Diş randevusu", s.baslik)
    }

    @Test fun yarinVeSaat() {
        val s = coz("Yarın 14:30 diş randevusu")
        assertEquals(bugun + 1, s.gun)
        assertEquals(14 * 60 + 30, s.baslangicDk)
        assertEquals("diş randevusu", s.baslik)
    }

    @Test fun sonundaTarih() {
        val s = coz("Toplantı yarın saat 15:00")
        assertEquals(bugun + 1, s.gun)
        assertEquals(15 * 60, s.baslangicDk)
        assertEquals("Toplantı", s.baslik)
    }

    @Test fun ekliYarinVeSaat() {
        val s = coz("Yarın saat 14'te toplantı")
        assertEquals(bugun + 1, s.gun)
        assertEquals(14 * 60, s.baslangicDk)
        assertEquals("toplantı", s.baslik)
    }

    @Test fun oburGun() {
        assertEquals(bugun + 2, coz("öbür gün spor").gun)
        assertEquals("spor", coz("öbür gün spor").baslik)
    }

    @Test fun haftaninGunu() {
        // Bugün Pazar; Cuma = 5 gün sonra
        assertEquals(bugun + 5, coz("cuma akşam yemeği").gun)
        assertEquals(bugun + 2, coz("Salı toplantı").gun)
        assertEquals(bugun, coz("pazar kahvaltı").gun)   // bugün Pazar, saat yok → bugün
    }

    @Test fun bugunKiGunSaatGectiyseGelecekHafta() {
        assertEquals(bugun + 7, DogalDil.coz("pazar 09:00 koşu", bugun, 10 * 60).gun)
        assertEquals(bugun, DogalDil.coz("pazar 18:00 koşu", bugun, 10 * 60).gun)
    }

    @Test fun haftayaVeGelecek() {
        // Gelecek hafta = 5 Ekim Pazartesi'den başlar; Salı = 6 Ekim?? Bugün 4 Ekim Pazar (haftanın son günü) → gelecek hafta 5–11 Ekim.
        assertEquals(g(2026, 10, 6), coz("haftaya salı toplantı").gun)
        assertEquals(g(2026, 10, 9), coz("next friday lunch").gun)
    }

    @Test fun tarihYazili() {
        val s = coz("15 Ekim doğum günü")
        assertEquals(g(2026, 10, 15), s.gun)
        assertEquals("doğum günü", s.baslik)
        assertEquals(g(2026, 11, 3), coz("3 Kasım'da kontrol").gun)
        assertEquals("kontrol", coz("3 Kasım'da kontrol").baslik)
    }

    @Test fun gecmisTarihGelecekYila() {
        assertEquals(g(2027, 3, 1), coz("1 Mart tatil").gun)
        assertEquals(g(2028, 2, 29), coz("29 Şubat 2028 etkinlik").gun)
    }

    @Test fun sayisalTarih() {
        assertEquals(g(2026, 12, 25), coz("25/12 yılbaşı").gun)
        assertEquals(g(2027, 1, 5), coz("5.1.2027 randevu").gun)
        assertNull(coz("5.1.2027 randevu").baslangicDk)
    }

    @Test fun ingilizce() {
        val s = coz("Lunch with Sam tomorrow at 3pm")
        assertEquals(bugun + 1, s.gun)
        assertEquals(15 * 60, s.baslangicDk)
        assertEquals("Lunch with Sam", s.baslik)
        assertEquals(g(2026, 10, 15), coz("Oct 15 dentist").gun)
        assertEquals(12 * 60, coz("noon 12pm sync").baslangicDk)
        assertEquals(0, coz("12am party").baslangicDk)
    }

    @Test fun saatAraligi() {
        val s = coz("yarın 14:00-15:30 workshop")
        assertEquals(14 * 60, s.baslangicDk)
        assertEquals(15 * 60 + 30, s.bitisDk)
        assertNull(s.sureDk)
        assertEquals("workshop", s.baslik)
    }

    @Test fun sure() {
        assertEquals(120, coz("yarın 10:00 toplantı 2 saat").sureDk)
        assertEquals(90, coz("antrenman 1 saat 30 dk").sureDk)
        assertEquals(45, coz("45 dk yürüyüş").sureDk)
    }

    @Test fun gunOzeti() {
        // "akşam 8" → 20:00; "sabah 9" → 09:00; "gece 11" → 23:00
        assertEquals(20 * 60, coz("yarın akşam saat 8 yemek").baslangicDk)
        assertEquals(9 * 60, coz("yarın sabah 9:00 koşu").baslangicDk)
        assertEquals(23 * 60, coz("cumartesi gece saat 11 film").baslangicDk)
        assertEquals(12 * 60, coz("yarın öğle yemek").baslangicDk)
    }

    @Test fun yalnizSaat() {
        assertEquals(3 * 60 + 30 + 12 * 60, coz("saat 3 buçuk çay").baslangicDk)
        assertEquals(15 * 60, coz("saat 3 çay").baslangicDk)        // 1–6 arası öğleden sonra
        assertEquals(9 * 60, coz("saat 9 toplantı").baslangicDk)    // 7+ olduğu gibi
        assertEquals(14 * 60, coz("14'te kontrol").baslangicDk)
    }

    @Test fun tumGun() {
        val s = coz("15 Ekim tüm gün doğum günü")
        assertTrue(s.tumGun)
        assertEquals(g(2026, 10, 15), s.gun)
        assertEquals("doğum günü", s.baslik)
        assertTrue(coz("holiday all day").tumGun)
    }

    @Test fun tekrar() {
        assertEquals(Kural(Sik.GUNLUK), coz("her gün ilaç").kural)
        assertEquals("ilaç", coz("her gün ilaç").baslik)
        val h = coz("her pazartesi 09:00 toplantı")
        assertEquals(Kural(Sik.HAFTALIK), h.kural)
        assertEquals(bugun + 1, h.gun)            // Pazartesi = yarın
        assertEquals(9 * 60, h.baslangicDk)
        assertEquals("toplantı", h.baslik)
        assertEquals(Kural(Sik.AYLIK), coz("every month rent").kural)
        assertEquals(Kural(Sik.YILLIK), coz("her yıl 15 Ekim doğum günü").kural)
        assertEquals(Kural(Sik.HAFTALIK), coz("haftalık sync").kural)
    }

    @Test fun tarihliSayiSaatKarismaz() {
        val s = coz("15.10.2026 14.30 sunum")
        assertEquals(g(2026, 10, 15), s.gun)
        assertEquals(14 * 60 + 30, s.baslangicDk)
        assertEquals("sunum", s.baslik)
    }

    @Test fun turkceBuyukHarfler() {
        assertEquals(bugun + 1, coz("YARIN toplantı").gun)
        assertEquals(bugun + 2, coz("SALI toplantı").gun)
        assertEquals(g(2026, 10, 12), coz("12 EKİM sunum").gun)
    }

    @Test fun baslikBosKalabilir() {
        val s = coz("yarın 14:00")
        assertEquals("", s.baslik)
        assertTrue(s.bulundu)
    }

    @Test fun gecersizTarihYoksayilir() {
        assertNull(coz("31 Nisan toplantı").gun)
        assertEquals("31 Nisan toplantı", coz("31 Nisan toplantı").baslik)
    }
}
