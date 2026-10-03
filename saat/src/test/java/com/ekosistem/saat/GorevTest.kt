package com.ekosistem.saat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GorevTest {

    @Test
    fun zorlukKadarSoruUretilir() {
        assertEquals(0, Gorev.sorular(Gorev.YOK).size)
        assertEquals(1, Gorev.sorular(Gorev.KOLAY).size)
        assertEquals(2, Gorev.sorular(Gorev.ORTA).size)
        assertEquals(3, Gorev.sorular(Gorev.ZOR).size)
    }

    @Test
    fun cevaplarMetinleUyusur() {
        val r = Random(7)
        repeat(200) {
            for (z in Gorev.KOLAY..Gorev.ZOR) for (s in Gorev.sorular(z, r)) {
                val parca = s.metin.split(" ")
                val beklenen = when (z) {
                    Gorev.KOLAY -> parca[0].toInt() + parca[2].toInt()
                    Gorev.ORTA -> parca[0].toInt() * parca[2].toInt()
                    else -> parca[0].toInt() * parca[2].toInt() + parca[4].toInt()
                }
                assertEquals(beklenen, s.cevap)
            }
        }
    }

    @Test
    fun dogruluKontrolu() {
        val s = Gorev.Soru("12 + 30", 42)
        assertTrue(Gorev.dogruMu(s, "42"))
        assertFalse(Gorev.dogruMu(s, ""))
        assertFalse(Gorev.dogruMu(s, "43"))
        assertFalse(Gorev.dogruMu(s, "99999999999"))
    }

    @Test
    fun alarmGorevKaydiYuklenir() {
        val a = Alarm(1, 7, 0, gorev = Gorev.ORTA)
        assertEquals(Gorev.ORTA, Alarm.jsondan(a.json()).gorev)
        // Eski kayıtta alan yok → görevsiz; bozuk değer sınırlanır.
        assertEquals(Gorev.YOK, Alarm.jsondan(Alarm(2, 7, 0).json().apply { remove("gorev") }).gorev)
        assertEquals(Gorev.ZOR, Alarm.jsondan(a.json().put("gorev", 99)).gorev)
    }
}
