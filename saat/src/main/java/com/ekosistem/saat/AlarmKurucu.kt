package com.ekosistem.saat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.TimeZone

/**
 * Alarmları sisteme kuran tek yer. Açılış, kilit açılmadan açılış, saat ya da
 * saat dilimi değişimi, uygulama güncellemesi, izin değişimi, alarm
 * düzenleme… hepsi buraya gelir ve bütün alarmlar baştan kurulur. Her alarm
 * için iki kayıt: asıl çalma (setAlarmClock) ve yaklaşan alarm bildirimi.
 */
object AlarmKurucu {

    const val EYLEM_CAL = "com.ekosistem.saat.CAL"
    const val EYLEM_YAKLASAN = "com.ekosistem.saat.YAKLASAN"
    const val EK_ID = "id"

    private const val YAKLASAN_KAYMASI = 1_000_000

    fun hepsiniKur(context: Context) {
        val simdi = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val eski = Depo.alarmlar(context)
        val yeni = eski.map { kurulacakHal(context, it, simdi, tz) }
        // Şu an çalan alarm yeniden kurulmaz: eski anı geçmişte kalır, hemen
        // ikinci kez çalardı. Kapatılınca ya da ertelenince yeniden kurulur.
        for (a in yeni) if (a.id != CalmaHizmeti.calanId) kur(context, a, simdi, tz)
        if (yeni != eski) Depo.kaydet(context, yeni)
    }

    /**
     * Kaçırılan çalmayı bildirir, geçmiş atlamayı temizler, tarihi geçen tek
     * seferlik alarmı kapatır; dönen alarmın kurulanZaman'ı yeni çalma anıdır.
     */
    private fun kurulacakHal(context: Context, alarm: Alarm, simdi: Long, tz: TimeZone): Alarm {
        // Şu an çalan alarm "kaçırılmış" sayılmasın.
        if (CalmaHizmeti.calanId == alarm.id) return alarm
        // "Alarmı dene": saniyesi belli tek seferlik çalma; hesaplanmaz, kaçırılmaz.
        if (alarm.id == MainActivity.DENEME_ID) {
            val an = Zamanlama.denemeAni(alarm, simdi)
            return if (an != null) alarm.copy(kurulanZaman = an) else alarm.copy(acik = false, kurulanZaman = 0)
        }
        var a = alarm
        if (Zamanlama.kacirildi(a, simdi)) {
            Bildirimler.kacirildi(context, a, a.kurulanZaman)
            a = Zamanlama.calmaBitti(a)
        }
        if (Zamanlama.atlamaGecti(a, simdi, tz)) a = a.copy(atla = 0)
        val sonraki = Zamanlama.sonrakiCalma(a, simdi, tz)
        if (sonraki == null && a.acik) a = a.copy(acik = false, tarih = 0)
        return a.copy(kurulanZaman = sonraki ?: 0)
    }

    private fun kur(context: Context, alarm: Alarm, simdi: Long, tz: TimeZone) {
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calma = calmaNiyeti(context, alarm.id)
        val yaklasan = yaklasanNiyeti(context, alarm.id)
        val an = alarm.kurulanZaman
        if (!alarm.acik || an <= 0) {
            yonetici.cancel(calma)
            yonetici.cancel(yaklasan)
            Bildirimler.yaklasaniKaldir(context, alarm.id)
            return
        }
        // Durum çubuğundaki alarm simgesine dokununca uygulama açılsın.
        val goster = PendingIntent.getActivity(
            context, alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (tamZamanliKurulabilir(yonetici)) {
            yonetici.setAlarmClock(AlarmManager.AlarmClockInfo(an, goster), calma)
        } else {
            // İzin geri alınmış (yalnız Android 12+'da olur): en iyi çaba. Kontrol sayfası uyarır.
            yonetici.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, calma)
        }

        // Yaklaşan alarm bildirimi: ertelenmiş alarm için gösterilmez.
        val onceDk = Depo.yaklasanDk(context)
        val bildirimAni = an - onceDk * Zamanlama.DAKIKA
        val ertelenmis = alarm.ertelemeZamani == an
        if (onceDk <= 0 || ertelenmis || alarm.id == MainActivity.DENEME_ID) {
            yonetici.cancel(yaklasan)
            Bildirimler.yaklasaniKaldir(context, alarm.id)
        } else if (bildirimAni <= simdi) {
            yonetici.cancel(yaklasan)
            Bildirimler.yaklasan(context, alarm, an)
        } else {
            Bildirimler.yaklasaniKaldir(context, alarm.id)
            // Esnek kurulunca sistem bir saate kadar geciktirebiliyordu; "1 saat
            // önce" bildirimi alarmla aynı anda gelirdi.
            when {
                Build.VERSION.SDK_INT < 23 ->
                    yonetici.setExact(AlarmManager.RTC_WAKEUP, bildirimAni, yaklasan)
                tamZamanliKurulabilir(yonetici) ->
                    yonetici.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bildirimAni, yaklasan)
                else -> yonetici.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bildirimAni, yaklasan)
            }
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("SaatKur", "alarm ${alarm.id} → ${java.util.Date(an)}")
        }
    }

    /** Silinen alarmın kayıtlarını sistemden kaldırır. */
    fun iptal(context: Context, id: Int) {
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        yonetici.cancel(calmaNiyeti(context, id))
        yonetici.cancel(yaklasanNiyeti(context, id))
        Bildirimler.yaklasaniKaldir(context, id)
    }

    fun tamZamanliKurulabilir(yonetici: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < 31 || yonetici.canScheduleExactAlarms()

    fun tamZamanliKurulabilir(context: Context): Boolean =
        tamZamanliKurulabilir(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private fun calmaNiyeti(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, id,
        Intent(context, AlarmAlici::class.java).setAction(EYLEM_CAL).putExtra(EK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun yaklasanNiyeti(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, YAKLASAN_KAYMASI + id,
        Intent(context, AlarmAlici::class.java).setAction(EYLEM_YAKLASAN).putExtra(EK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
