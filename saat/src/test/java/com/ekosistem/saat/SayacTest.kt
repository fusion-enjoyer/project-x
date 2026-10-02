package com.ekosistem.saat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SayacTest {

    @Test
    fun kronometreDurupDevamEder() {
        var k = Kronometre().basla(1_000)
        assertEquals(500L, k.gecen(1_500))
        k = k.durdur(2_000)
        assertEquals(1_000L, k.gecen(9_999))
        k = k.basla(10_000)
        assertEquals(1_500L, k.gecen(10_500))
        assertFalse(k.sifirda)
        assertTrue(k.sifirla().sifirda)
    }

    @Test
    fun turSureleri() {
        // 0 "çalışmıyor" demek; açılıştan beri geçen süre hiç 0 olmaz.
        var k = Kronometre().basla(1_000)
        k = k.tur(4_000).tur(6_500).tur(11_000)
        assertEquals(listOf(3_000L, 5_500L, 10_000L), k.turlar)
        assertEquals(listOf(3_000L, 2_500L, 4_500L), k.turSureleri())
        // Dururken tur eklenmez
        assertEquals(3, k.durdur(12_000).tur(13_000).turlar.size)
    }

    @Test
    fun yenidenBaslatmadanSonraSureKorunur() {
        // 10:00:00'da başladı (açılıştan 500 sn sonra); telefon yeniden başladı,
        // şimdi açılıştan 30 sn geçti ve duvar saati 10:05:00.
        val duvar = 36_000_000L
        val k = Kronometre(baslangic = 500_000, birikmis = 2_000, duvar = duvar)
        val kurtarilan = k.yenidenBaslatmaSonrasi(simdi = 30_000, duvarSimdi = duvar + 300_000)
        assertEquals(302_000L, kurtarilan.gecen(30_000))
        // Yeniden başlatma yoksa dokunulmaz
        assertEquals(k, k.yenidenBaslatmaSonrasi(simdi = 600_000, duvarSimdi = duvar + 100_000))
    }

    @Test
    fun kronometreBicimi() {
        assertEquals("00:00.0", Kronometre.bicim(0))
        assertEquals("01:02.3", Kronometre.bicim(62_345))
        assertEquals("1:00:05.0", Kronometre.bicim(3_605_000))
    }

    @Test
    fun kronometreJson() {
        val k = Kronometre(5, 1200, listOf(300, 900), 77)
        assertEquals(k, Kronometre.jsondan(k.json()))
        assertEquals(Kronometre(), Kronometre.jsondan("bozuk"))
    }

    @Test
    fun zamanlayiciDuraklatVeEkle() {
        var z = Zamanlayici(1, 60_000).baslat(0)
        assertEquals(40_000L, z.kalan(20_000))
        z = z.duraklat(20_000)
        assertEquals(40_000L, z.kalan(99_000))
        z = z.ekle(60_000, 99_000)
        assertEquals(100_000L, z.kalan(0))
        z = z.baslat(100_000)
        assertEquals(200_000L, z.bitis)
        assertTrue(z.bitti(200_000))
        assertEquals(60_000L, z.sifirla().kalan(0))
    }

    @Test
    fun bitenZamanlayiciyaEkleSimdidenSayar() {
        // Süre dolmuş (bitis geçmişte) ve çalarken "+1 dk"
        val z = Zamanlayici(1, 60_000, bitis = 1_000).ekle(60_000, 5_000)
        assertEquals(65_000L, z.bitis)
    }

    @Test
    fun rakamlaSure() {
        assertEquals(90_000L, Zamanlayici.rakamlardanMs("130"))
        assertEquals(3_600_000L, Zamanlayici.rakamlardanMs("10000"))
        assertEquals(5_000L, Zamanlayici.rakamlardanMs("5"))
        assertEquals((99 * 3600 + 59 * 60 + 59) * 1000L, Zamanlayici.rakamlardanMs("995959"))
        assertEquals(0L, Zamanlayici.rakamlardanMs(""))
    }

    @Test
    fun zamanlayiciBicimi() {
        assertEquals("5:00", Zamanlayici.bicim(300_000))
        assertEquals("5:00", Zamanlayici.bicim(299_500))
        assertEquals("0:00", Zamanlayici.bicim(-10))
        assertEquals("1:00:00", Zamanlayici.bicim(3_600_000))
    }

    @Test
    fun zamanlayiciListesiJson() {
        val l = listOf(Zamanlayici(1, 60_000, 5_000, 60_000, "Çay"), Zamanlayici(2, 300_000))
        assertEquals(l, Zamanlayici.jsondanListe(Zamanlayici.listedenJson(l)))
    }
}
