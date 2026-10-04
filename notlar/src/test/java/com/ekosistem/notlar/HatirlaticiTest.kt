package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class HatirlaticiTest {

    private fun zaman(yil: Int, ay: Int, gun: Int, saat: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(yil, ay - 1, gun, saat, 0, 0)
        }.timeInMillis

    @Test
    fun herGunBirSonrakiGuneKurulur() {
        val ilk = zaman(2026, 3, 10)
        assertEquals(zaman(2026, 3, 11), Hatirlatici.sonrakiZaman(ilk, Hatirlatici.HER_GUN, ilk))
    }

    @Test
    fun kacanTekrarlarAtlanir() {
        // Telefon üç gün kapalı kaldı: geçmiş tekrarlar değil, ilk gelecek zaman.
        val ilk = zaman(2026, 3, 10)
        val simdi = zaman(2026, 3, 13, saat = 12)
        assertEquals(zaman(2026, 3, 14), Hatirlatici.sonrakiZaman(ilk, Hatirlatici.HER_GUN, simdi))
    }

    @Test
    fun herHaftaYediGunSonra() {
        val ilk = zaman(2026, 3, 10)
        assertEquals(zaman(2026, 3, 17), Hatirlatici.sonrakiZaman(ilk, Hatirlatici.HER_HAFTA, ilk))
    }

    @Test
    fun ayinSonGunuKaymaz() {
        val ilk = zaman(2026, 1, 31)
        // Şubatta 28'ine düşer...
        assertEquals(zaman(2026, 2, 28), Hatirlatici.sonrakiZaman(ilk, Hatirlatici.HER_AY, ilk))
        // ...ama ilk zamandan sayıldığı için martta yine 31'i.
        assertEquals(
            zaman(2026, 3, 31),
            Hatirlatici.sonrakiZaman(ilk, Hatirlatici.HER_AY, zaman(2026, 2, 28))
        )
    }
}
