package com.ekosistem.galeri

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumlerTest {

    @Test fun kameraBastaSonraEnYeni() {
        val liste = listOf(
            medya(id = 1, album = "WhatsApp Images", tarih = an(2024, 11, 1)),
            medya(id = 2, album = "Camera", tarih = an(2024, 10, 1)),
            medya(id = 3, album = "WhatsApp Images", tarih = an(2024, 9, 1)),
            medya(id = 4, album = "Download", tarih = an(2024, 8, 1))
        )
        val a = Albumler.kur(liste)
        assertEquals(listOf("Camera", "WhatsApp Images", "Download"), a.map { it.ad })
        assertEquals(2, a[1].adet)
        assertEquals(1L, a[1].kapak.id)
    }

    @Test fun kaynakKodlama() {
        for (k in listOf(Kaynak.Hepsi, Kaynak.Klasor("-1739773001"), Kaynak.Sorgu("ekim 2024: deniz"))) {
            assertEquals(k, Kaynak.coz(k.kodla()))
        }
        assertEquals(Kaynak.Hepsi, Kaynak.coz(null))
    }

    @Test fun yeniDosyaAdi() {
        assertEquals("IMG_1_duzenleme_1700000000.jpg", Kaydedici.yeniAd("IMG_1.jpg", "jpg", 1_700_000_000_000))
        assertEquals("IMG_duzenleme_1.png", Kaydedici.yeniAd("", "png", 1000))
    }
}
