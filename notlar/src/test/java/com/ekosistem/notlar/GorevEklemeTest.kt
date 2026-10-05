package com.ekosistem.notlar

import org.junit.Assert.assertEquals
import org.junit.Test

/** Widget'taki "+" ile bugünün notuna görev ekleme. */
class GorevEklemeTest {

    @Test
    fun bosNotaGorevYazilir() {
        assertEquals("- [ ] süt al\n", Sablonlar.gorevEkle("", "süt al"))
    }

    @Test
    fun sonSatirGorevseAltinaDizilir() {
        assertEquals(
            "2026-10-05\n- [ ] ekmek\n- [ ] süt\n",
            Sablonlar.gorevEkle("2026-10-05\n- [ ] ekmek\n\n", "süt")
        )
    }

    @Test
    fun duzMetinlerdenBosSatirlaAyrilir() {
        assertEquals("2026-10-05\nGüzel gün\n\n- [ ] koş\n", Sablonlar.gorevEkle("2026-10-05\nGüzel gün", "koş"))
        assertEquals("başlık\n\n- [ ] koş\n", Sablonlar.gorevEkle("başlık\n", "koş"))
        assertEquals("başlık\n\n- [ ] koş\n", Sablonlar.gorevEkle("başlık\n\n", "koş"))
    }

    @Test
    fun gorevlerBolumundekiBosYerTutucuDoldurulur() {
        val gunluk = "2026-10-05\n## Bugün\n- \n## Görevler\n- [ ] \n## Notlar\n"
        assertEquals(
            "2026-10-05\n## Bugün\n- \n## Görevler\n- [ ] Kira öde\n## Notlar\n",
            Sablonlar.gorevEkle(gunluk, "Kira öde")
        )
    }

    @Test
    fun gorevlerBolumundeSonGorevinAltinaEklenir() {
        val gunluk = "2026-10-05\n## GÖREVLER\n- [ ] Kira öde\n- [x] Koş\n\n## Notlar\nyazı\n"
        assertEquals(
            "2026-10-05\n## GÖREVLER\n- [ ] Kira öde\n- [x] Koş\n- [ ] Süt\n\n## Notlar\nyazı\n",
            Sablonlar.gorevEkle(gunluk, "Süt")
        )
    }

    @Test
    fun bosBolumBasligininAltinaEklenir() {
        assertEquals("# Tasks\n- [ ] a\n\nmetin\n", Sablonlar.gorevEkle("# Tasks\n\nmetin\n", "a"))
    }

    @Test
    fun cokSatirliMetinTekSatiraIner() {
        assertEquals("- [ ] a b\n", Sablonlar.gorevEkle("", "  a\nb  "))
    }
}
