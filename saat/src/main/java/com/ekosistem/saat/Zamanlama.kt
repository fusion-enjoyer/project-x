package com.ekosistem.saat

import java.util.Calendar
import java.util.TimeZone

/**
 * Alarmın ne zaman çalacağını hesaplayan saf mantık (Android'e dokunmaz,
 * testlerle doğrulanır). Kurallar (bkz. arastirma/saat-rakipler.md §3):
 *
 * - Hesap her zaman **duvar saatine** göre ve verilen saat diliminde yapılır.
 *   Yaz saati geçişinde 06:30 yine yerel 06:30'dur; saat dilimi değişince
 *   yeniden hesaplamak doğru anı verir.
 * - Bir sonraki çalma "şu andan **sonraki** ilk uygun an"dır; az önce çalan
 *   dakika ikinci kez üretilmez.
 */
object Zamanlama {

    /** Pazartesi = 0 … pazar = 6. */
    fun haftaGunu(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun gunAnahtari(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    fun gunAnahtari(ms: Long, tz: TimeZone): Int = gunAnahtari(takvim(ms, tz))

    private fun takvim(ms: Long, tz: TimeZone): Calendar =
        Calendar.getInstance(tz).apply { timeInMillis = ms }

    /** Verilen günün (yyyyAAgg) saat:dakika anı. Yaz saati boşluğuna denk gelirse ileri kayar. */
    fun gununAni(gunAnahtari: Int, saat: Int, dakika: Int, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(gunAnahtari / 10000, (gunAnahtari / 100) % 100 - 1, gunAnahtari % 100, saat, dakika, 0)
        }.timeInMillis

    /**
     * Ertelemeyi saymadan bir sonraki olağan çalma. Tek seferlik ve tarihi
     * geçmiş alarm için null (kapatılmalı).
     */
    fun sonrakiOlagan(alarm: Alarm, simdi: Long, tz: TimeZone): Long? {
        if (!alarm.tekrarli && alarm.tarih != 0) {
            val an = gununAni(alarm.tarih, alarm.saat, alarm.dakika, tz)
            return if (an > simdi) an else null
        }
        val bugun = takvim(simdi, tz)
        // İki hafta: haftada tek gün çalan alarmın o günü atlanırsa bir sonraki
        // haftaya geçebilsin.
        for (fark in 0..14) {
            val gun = (bugun.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, fark) }
            if (alarm.tekrarli && !alarm.gunAcik(haftaGunu(gun))) continue
            val anahtar = gunAnahtari(gun)
            if (anahtar == alarm.atla) continue
            val an = gununAni(anahtar, alarm.saat, alarm.dakika, tz)
            if (an > simdi) return an
        }
        return null
    }

    /** Ertelenmişse erteleme anı, değilse olağan çalma. Kapalı alarm için null. */
    fun sonrakiCalma(alarm: Alarm, simdi: Long, tz: TimeZone): Long? {
        if (!alarm.acik) return null
        val olagan = sonrakiOlagan(alarm, simdi, tz)
        val erteleme = alarm.ertelemeZamani.takeIf { it > simdi }
        return listOfNotNull(olagan, erteleme).minOrNull()
    }

    /**
     * "Bir sonrakini atla": şu an kurulu olan olağan çalmanın günü atlanır.
     * Zaten atlanmışsa geri alınır (aynı düğme iki iş görür).
     */
    fun atlamayiDegistir(alarm: Alarm, simdi: Long, tz: TimeZone): Alarm {
        if (alarm.atla != 0 && gununAni(alarm.atla, alarm.saat, alarm.dakika, tz) > simdi) {
            return alarm.copy(atla = 0)
        }
        val sonraki = sonrakiOlagan(alarm.copy(atla = 0), simdi, tz) ?: return alarm
        return alarm.copy(atla = gunAnahtari(sonraki, tz))
    }

    /** Atlanan gün geçti mi (gösterimden ve kayıttan temizlemek için). */
    fun atlamaGecti(alarm: Alarm, simdi: Long, tz: TimeZone): Boolean =
        alarm.atla != 0 && gununAni(alarm.atla, alarm.saat, alarm.dakika, tz) <= simdi

    /**
     * Kurulmuş bir çalma, uygulama çalıştıramadan geçip gittiyse (telefon
     * kapalıydı, pil bitti) kullanıcıya söylenmeli. Bir dakika pay bırakılır.
     */
    fun kacirildi(alarm: Alarm, simdi: Long): Boolean =
        alarm.acik && alarm.kurulanZaman in 1 until (simdi - KACIRMA_PAYI)

    /**
     * Çalma bittiğinde (kapatıldı ya da kaçırıldı) alarmın yeni hali: tek
     * seferlik alarm kapanır, tekrarlı alarm sıradaki güne geçer.
     */
    fun calmaBitti(alarm: Alarm): Alarm = alarm.copy(
        acik = alarm.tekrarli,
        ertelemeSayisi = 0,
        ertelemeZamani = 0,
        kurulanZaman = 0,
        tarih = if (alarm.tekrarli) alarm.tarih else 0
    )

    /** Ertele: yeni çalma anı ve sayaç. Sınır dolduysa null. */
    fun ertele(alarm: Alarm, simdi: Long, dakika: Int = alarm.ertelemeDk): Alarm? {
        if (alarm.ertelemeSiniri in 1..alarm.ertelemeSayisi) return null
        return alarm.copy(
            ertelemeSayisi = alarm.ertelemeSayisi + 1,
            ertelemeZamani = simdi + dakika * DAKIKA,
            kurulanZaman = 0
        )
    }

    /**
     * "Alarmı dene"nin anı: saniyesiyle kurulmuş tek çalma ya da ertelemesi.
     * Duvar saatinden hesaplanmaz (10 saniye sonrası dakikaya sığmaz).
     */
    fun denemeAni(alarm: Alarm, simdi: Long): Long? {
        if (!alarm.acik) return null
        val an = if (alarm.ertelemeZamani > simdi) alarm.ertelemeZamani else alarm.kurulanZaman
        return an.takeIf { it > simdi }
    }

    /** Kalan süre: gün, saat, dakika (dakika yukarı yuvarlanır; "0 dk" görünmesin). */
    fun kalanParcalar(fark: Long): Triple<Int, Int, Int> {
        val toplamDk = ((fark + DAKIKA - 1) / DAKIKA).coerceAtLeast(1)
        return Triple((toplamDk / (24 * 60)).toInt(), ((toplamDk / 60) % 24).toInt(), (toplamDk % 60).toInt())
    }

    const val DAKIKA = 60_000L
    const val KACIRMA_PAYI = 60_000L
}
