package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonTarihTest {

    @Test
    fun gunNumarasiVeGeriCevirme() {
        assertEquals(0L, SonTarih.gunNumarasi(1970, 1, 1))
        assertEquals(1L, SonTarih.gunNumarasi(1970, 1, 2))
        assertEquals(-1L, SonTarih.gunNumarasi(1969, 12, 31))
        // Artık yıl: 29 Şubat'tan sonraki gün 1 Mart.
        assertEquals(SonTarih.gunNumarasi(2028, 2, 29) + 1, SonTarih.gunNumarasi(2028, 3, 1))
        for (g in listOf(-800L, 0L, 20000L, 20730L, 60000L)) {
            val (y, a, d) = SonTarih.tarih(g)
            assertEquals(g, SonTarih.gunNumarasi(y, a, d))
        }
        assertEquals(Triple(2026, 10, 4), SonTarih.tarih(SonTarih.gunNumarasi(2026, 10, 4)))
    }

    @Test
    fun satirdanTarihOkunur() {
        assertEquals(SonTarih.gunNumarasi(2026, 10, 10), SonTarih.gun("- [ ] Rapor 📅 2026-10-10"))
        assertNull(SonTarih.gun("- [ ] Rapor 2026-10-10"))
        assertNull(SonTarih.gun("- [ ] Rapor 📅 2026-02-30"))
    }

    @Test
    fun tarihYazilirYaDaDegistirilir() {
        assertEquals("- [ ] Rapor 📅 2026-10-10", SonTarih.yaz("- [ ] Rapor ", 2026, 10, 10))
        assertEquals("- [ ] Rapor 📅 2026-11-01 #iş", SonTarih.yaz("- [ ] Rapor 📅 2026-10-10 #iş", 2026, 11, 1))
    }

    @Test
    fun metindenTarihAtilir() {
        assertEquals("Rapor #iş", SonTarih.temizle("Rapor 📅 2026-10-10 #iş"))
    }
}
