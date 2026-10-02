package com.ekosistem.saat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ZamanlamaTest {

    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    /** Yerel duvar saatinden an. */
    private fun an(tz: TimeZone, yil: Int, ay: Int, gun: Int, saat: Int, dakika: Int = 0): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(yil, ay - 1, gun, saat, dakika, 0)
        }.timeInMillis

    private fun yerel(ms: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return "%04d-%02d-%02d %02d:%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    @Test
    fun tekSeferlikBugunGectiyseYarin() {
        val alarm = Alarm(1, 6, 30)
        // 2 Ekim 2026 perşembe 22:00
        val simdi = an(istanbul, 2026, 10, 2, 22)
        assertEquals("2026-10-03 06:30", yerel(Zamanlama.sonrakiCalma(alarm, simdi, istanbul)!!, istanbul))
        // Saat daha gelmediyse bugün
        val sabah = an(istanbul, 2026, 10, 2, 5)
        assertEquals("2026-10-02 06:30", yerel(Zamanlama.sonrakiCalma(alarm, sabah, istanbul)!!, istanbul))
    }

    @Test
    fun yarinAsla_gelmez_hatasiYok() {
        // Fossify #293: gece 22'de "yarın 06:00" kuruldu, telefon gece kilitlendi,
        // 03:00'te yeniden hesaplandı → alarm bir gün kaymıştı. Bizde aynı an çıkar.
        val alarm = Alarm(1, 6, 0)
        val kurulus = an(istanbul, 2026, 10, 2, 22)
        val gece = an(istanbul, 2026, 10, 3, 3)
        val ilk = Zamanlama.sonrakiCalma(alarm, kurulus, istanbul)
        val yeniden = Zamanlama.sonrakiCalma(alarm, gece, istanbul)
        assertEquals(ilk, yeniden)
        assertEquals("2026-10-03 06:00", yerel(yeniden!!, istanbul))
    }

    @Test
    fun azOnceCalanDakikaIkinciKezUretilmez() {
        // Fossify #317: hızlı kapatılınca tekrarlı alarm iki kez çalıyordu.
        val alarm = Alarm(1, 7, 0, gunler = Alarm.HER_GUN)
        val calma = an(istanbul, 2026, 10, 5, 7, 0)
        assertEquals("2026-10-06 07:00", yerel(Zamanlama.sonrakiCalma(alarm, calma, istanbul)!!, istanbul))
        assertEquals("2026-10-06 07:00", yerel(Zamanlama.sonrakiCalma(alarm, calma + 5_000, istanbul)!!, istanbul))
    }

    @Test
    fun haftaIciCumaAksamiPazartesiyeGecer() {
        val alarm = Alarm(1, 6, 30, gunler = Alarm.HAFTA_ICI)
        // 2 Ekim 2026 cuma 20:00
        val cuma = an(istanbul, 2026, 10, 2, 20)
        assertEquals("2026-10-05 06:30", yerel(Zamanlama.sonrakiCalma(alarm, cuma, istanbul)!!, istanbul))
    }

    @Test
    fun yazSaatineGeciste_yerelSaatKorunur() {
        // Fossify #61: yaz saati geçişinden sonra alarm bir saat erken çalıyordu.
        // Berlin'de 29 Mart 2026 02:00 → 03:00.
        val alarm = Alarm(1, 6, 30, gunler = Alarm.HER_GUN)
        val oncekiAksam = an(berlin, 2026, 3, 28, 22)
        val sonraki = Zamanlama.sonrakiCalma(alarm, oncekiAksam, berlin)!!
        assertEquals("2026-03-29 06:30", yerel(sonraki, berlin))
        // Geçişten önceki günle arası 23 saat (bir saat kayboldu).
        val oncekiGun = an(berlin, 2026, 3, 28, 6, 30)
        assertEquals(23 * 3_600_000L, sonraki - oncekiGun)
    }

    @Test
    fun kisSaatineGeciste_yerelSaatKorunur() {
        // Berlin'de 25 Ekim 2026 03:00 → 02:00.
        val alarm = Alarm(1, 6, 30, gunler = Alarm.HER_GUN)
        val sonraki = Zamanlama.sonrakiCalma(alarm, an(berlin, 2026, 10, 24, 22), berlin)!!
        assertEquals("2026-10-25 06:30", yerel(sonraki, berlin))
        assertEquals(25 * 3_600_000L, sonraki - an(berlin, 2026, 10, 24, 6, 30))
    }

    @Test
    fun yazSaatiBosluguIleriKayar() {
        // 02:30 o gece hiç yaşanmıyor; alarm kaybolmaz, 03:30'da çalar.
        val alarm = Alarm(1, 2, 30)
        val sonraki = Zamanlama.sonrakiCalma(alarm, an(berlin, 2026, 3, 28, 23), berlin)!!
        assertEquals("2026-03-29 03:30", yerel(sonraki, berlin))
    }

    @Test
    fun saatDilimiDegisinceYeniDilimdeAyniDuvarSaati() {
        val alarm = Alarm(1, 7, 0, gunler = Alarm.HER_GUN)
        val simdi = an(istanbul, 2026, 10, 2, 23)
        val londra = TimeZone.getTimeZone("Europe/London")
        assertEquals("2026-10-03 07:00", yerel(Zamanlama.sonrakiCalma(alarm, simdi, londra)!!, londra))
    }

    @Test
    fun belirliTarih() {
        val alarm = Alarm(1, 9, 0, tarih = 20261012)
        val simdi = an(istanbul, 2026, 10, 2, 12)
        assertEquals("2026-10-12 09:00", yerel(Zamanlama.sonrakiCalma(alarm, simdi, istanbul)!!, istanbul))
        // Tarih geçtiyse çalmaz (kapatılması gerekir)
        assertNull(Zamanlama.sonrakiCalma(alarm, an(istanbul, 2026, 10, 12, 9, 1), istanbul))
    }

    @Test
    fun birSonrakiniAtla_veGeriAl() {
        val alarm = Alarm(1, 7, 15, gunler = 0b0010101) // pzt, çar, cum
        // 5 Ekim 2026 pazartesi 08:00 → sıradaki çarşamba
        val simdi = an(istanbul, 2026, 10, 5, 8)
        val atlanmis = Zamanlama.atlamayiDegistir(alarm, simdi, istanbul)
        assertEquals(20261007, atlanmis.atla)
        assertEquals("2026-10-09 07:15", yerel(Zamanlama.sonrakiCalma(atlanmis, simdi, istanbul)!!, istanbul))
        val geriAlinmis = Zamanlama.atlamayiDegistir(atlanmis, simdi, istanbul)
        assertEquals(0, geriAlinmis.atla)
    }

    @Test
    fun haftadaTekGunAtlanincaSonrakiHafta() {
        val alarm = Alarm(1, 10, 0, gunler = 0b1000000) // yalnız pazar
        val simdi = an(istanbul, 2026, 10, 2, 12)
        val atlanmis = Zamanlama.atlamayiDegistir(alarm, simdi, istanbul)
        assertEquals("2026-10-11 10:00", yerel(Zamanlama.sonrakiCalma(atlanmis, simdi, istanbul)!!, istanbul))
    }

    @Test
    fun ertelemeOlagandanOnceyse_oncelikli() {
        val alarm = Alarm(1, 7, 0, gunler = Alarm.HER_GUN)
        val calma = an(istanbul, 2026, 10, 5, 7)
        val ertelenmis = Zamanlama.ertele(alarm, calma)!!
        assertEquals(calma + 10 * 60_000L, Zamanlama.sonrakiCalma(ertelenmis, calma + 1000, istanbul))
        assertEquals(1, ertelenmis.ertelemeSayisi)
    }

    @Test
    fun ertelemeSiniriDolunca_null() {
        var alarm: Alarm? = Alarm(1, 7, 0, ertelemeSiniri = 2)
        val t = an(istanbul, 2026, 10, 5, 7)
        alarm = Zamanlama.ertele(alarm!!, t)
        alarm = Zamanlama.ertele(alarm!!, t)
        assertNull(Zamanlama.ertele(alarm!!, t))
        // Sınırsız
        assertTrue(Zamanlama.ertele(Alarm(1, 7, 0, ertelemeSiniri = 0, ertelemeSayisi = 50), t) != null)
    }

    @Test
    fun calmaBitince_tekSeferlikKapanir_tekrarliDevamEder() {
        assertFalse(Zamanlama.calmaBitti(Alarm(1, 7, 0, ertelemeSayisi = 2)).acik)
        val tekrarli = Zamanlama.calmaBitti(Alarm(1, 7, 0, gunler = Alarm.HER_GUN, ertelemeSayisi = 2, ertelemeZamani = 5))
        assertTrue(tekrarli.acik)
        assertEquals(0, tekrarli.ertelemeSayisi)
        assertEquals(0L, tekrarli.ertelemeZamani)
    }

    @Test
    fun kacirilanAlarmFarkEdilir() {
        val t = an(istanbul, 2026, 10, 5, 7)
        assertTrue(Zamanlama.kacirildi(Alarm(1, 7, 0, kurulanZaman = t), t + 10 * 60_000L))
        assertFalse(Zamanlama.kacirildi(Alarm(1, 7, 0, kurulanZaman = t), t + 30_000L))
        assertFalse(Zamanlama.kacirildi(Alarm(1, 7, 0, kurulanZaman = 0), t))
        assertFalse(Zamanlama.kacirildi(Alarm(1, 7, 0, acik = false, kurulanZaman = t), t + 10 * 60_000L))
    }

    @Test
    fun denemeAlarmiErtelenincedeKurulur() {
        // Ertele kurulanZaman'ı sıfırlar; deneme alarmı erteleme anıyla kurulmalı.
        val t = an(istanbul, 2026, 10, 2, 20, 55)
        val deneme = Alarm(0, 20, 55, ertelemeSiniri = 1, kurulanZaman = t)
        assertEquals(t, Zamanlama.denemeAni(deneme, t - 5_000))
        val ertelenmis = Zamanlama.ertele(deneme, t)!!
        assertEquals(t + 10 * 60_000L, Zamanlama.denemeAni(ertelenmis, t + 1_000))
        assertNull(Zamanlama.denemeAni(Zamanlama.calmaBitti(ertelenmis), t + 1_000))
    }

    @Test
    fun kalanSure() {
        assertEquals(Triple(0, 8, 12), Zamanlama.kalanParcalar((8 * 60 + 12) * 60_000L))
        // 8 sa 11 dk 10 sn → 8 sa 12 dk (yukarı yuvarlanır)
        assertEquals(Triple(0, 8, 12), Zamanlama.kalanParcalar((8 * 60 + 11) * 60_000L + 10_000))
        assertEquals(Triple(0, 0, 1), Zamanlama.kalanParcalar(5_000))
        assertEquals(Triple(2, 3, 0), Zamanlama.kalanParcalar((2 * 24 * 60 + 3 * 60) * 60_000L))
    }

    @Test
    fun jsonGidisDonus_veBozukKayitAtlanir() {
        val a = Alarm(3, 6, 30, gunler = Alarm.HAFTA_ICI, etiket = "İş", atla = 20261007, ses = Alarm.SES_YERLESIK)
        val metin = Alarm.listedenJson(listOf(a))
        assertEquals(listOf(a), Alarm.jsondanListe(metin))
        val bozuk = """{"alarmlar":[{"id":1,"saat":7,"dakika":0},{"saat":"x"}]}"""
        assertEquals(1, Alarm.jsondanListe(bozuk).size)
    }
}
