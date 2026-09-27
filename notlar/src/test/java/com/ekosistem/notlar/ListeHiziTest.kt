package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ListeHiziTest {

    @get:Rule
    val klasor = TemporaryFolder()

    // --- Kart başlığı ve özeti ---

    @Test
    fun ilkDoluSatirBaslikGerisiOzet() {
        val (baslik, ozet) = NotDeposu.onizlemeCikar("\n\n## Toplantı\n\n- [ ] süt al\n**kalın** yazı\n", "a.md")
        assertEquals("Toplantı", baslik)
        assertEquals("süt al kalın yazı", ozet)
    }

    @Test
    fun ozet150KarakterdeKesilir() {
        val icerik = "Başlık\n" + List(400) { "satır $it biraz daha uzun bir cümle" }.joinToString("\n")
        val (_, ozet) = NotDeposu.onizlemeCikar(icerik, "a.md")
        assertEquals(150, ozet.length)
        assertTrue(ozet.startsWith("satır 0 biraz"))
    }

    @Test
    fun eskiYontemleAyniSonuc() {
        // Hızlı yol, önceki "hepsini temizle sonra kırp" yöntemiyle aynı metni vermeli.
        val icerik = "# Proje\n> alıntı\n- [x] bitti\n![](ekler/a.jpg)\n~~çizik~~ `kod` [[bağ]]\n" +
            List(50) { "uzun satır $it" }.joinToString("\n")
        val satirlar = icerik.lines()
        val ilk = satirlar.indexOfFirst { it.isNotBlank() }
        // Eski yöntem görsel satırından kalan boşluğu da sayıp çift boşluk
        // bırakıyordu; yeni yöntem boş kalan satırı atlar. Boşluklar tekleştirilince aynı.
        val eskiOzet = satirlar.drop(ilk + 1).filter { it.isNotBlank() }
            .joinToString(" ") { NotDeposu.mdTemizle(it) }
            .replace(Regex(" +"), " ").trim().take(150)
        val (baslik, ozet) = NotDeposu.onizlemeCikar(icerik, "a.md")
        assertEquals("Proje", baslik)
        assertEquals(eskiOzet, ozet)
    }

    @Test
    fun bosNottaDosyaAdiBaslikOlur() {
        assertEquals("fikir" to "", NotDeposu.onizlemeCikar("\n  \n", "fikir.md"))
        assertEquals("liste", NotDeposu.onizlemeCikar("", "liste.txt").first)
        // Yalnızca görsel içeren ilk satır da boş sayılır.
        assertEquals("tatil", NotDeposu.onizlemeCikar("![](ekler/a.jpg)", "tatil.md").first)
    }

    @Test
    fun baslik80KarakterdeKesilir() {
        assertEquals(80, NotDeposu.onizlemeCikar("a".repeat(200), "x.md").first.length)
    }

    // --- Önbellek ---

    private fun kayit(tarih: Long = 1000, boyut: Long = 10, baslik: String = "B", ozet: String = "Ö") =
        ListeOnbellegi.Kayit(tarih, boyut, baslik, ozet, "içerik")

    @Test
    fun tarihVeBoyutAyniysaKayitDoner() {
        val o = ListeOnbellegi()
        o.koy("a", kayit())
        assertNotNull(o.al("a", 1000, 10))
        assertNull("tarih değişti", o.al("a", 1001, 10))
        assertNull("boyut değişti", o.al("a", 1000, 11))
        assertNull("bilinmeyen not", o.al("b", 1000, 10))
    }

    @Test
    fun yazilanNotunKaydiAtilir() {
        val o = ListeOnbellegi()
        o.koy("a", kayit())
        o.sil("a")
        assertNull(o.al("a", 1000, 10))
    }

    @Test
    fun diskeYazilipGeriOkunur() {
        val dosya = File(klasor.root, "onbellek")
        val o = ListeOnbellegi()
        o.koy("content://x/ağaç/Not%201.md", kayit(baslik = "Çok özel başlık", ozet = "özet ğüşiöç"))
        o.koy("file:///b.md", kayit(tarih = 5, boyut = -1))
        assertTrue(o.kirli)
        assertTrue(o.diskeYaz(dosya))
        assertFalse(o.kirli)

        val yeni = ListeOnbellegi()
        yeni.disktenOku(dosya)
        val k = yeni.al("content://x/ağaç/Not%201.md", 1000, 10)!!
        assertEquals("Çok özel başlık", k.baslik)
        assertEquals("özet ğüşiöç", k.ozet)
        // İçerik diske yazılmaz; arama gerekince dosyadan okunur.
        assertNull(k.icerik)
        assertNotNull(yeni.al("file:///b.md", 5, -1))
    }

    @Test
    fun bozukDosyaYokSayilir() {
        val dosya = File(klasor.root, "onbellek").apply { writeBytes(byteArrayOf(0, 0, 0, 1, 7)) }
        val o = ListeOnbellegi()
        o.disktenOku(dosya)
        assertEquals(0, o.boyut)
    }

    @Test
    fun olmayanNotlarAtilir() {
        val o = ListeOnbellegi()
        o.koy("a", kayit())
        o.koy("b", kayit())
        o.diskeYaz(File(klasor.root, "x"))
        o.yalnizcaBunlarKalsin(setOf("a"))
        assertEquals(1, o.boyut)
        assertTrue(o.kirli)
    }

    @Test
    fun ayniKaydiTekrarKoymakKirletmez() {
        val o = ListeOnbellegi()
        o.koy("a", kayit())
        o.diskeYaz(File(klasor.root, "x"))
        o.koy("a", kayit())
        assertFalse(o.kirli)
    }
}
