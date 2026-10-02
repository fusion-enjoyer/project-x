package com.ekosistem.saat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Çalışan zamanlayıcıların bitişini sisteme kurar. Alarmla aynı güvenilir yol:
 * setAlarmClock (pil tasarrufunda gecikmez, ön plan hizmeti muafiyeti verir).
 */
object ZamanlayiciKurucu {

    const val EYLEM_DOLDU = "com.ekosistem.saat.ZAMAN_DOLDU"
    private const val KAYMA = 2_000_000

    fun hepsiniKur(context: Context) {
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val simdi = System.currentTimeMillis()
        for (z in Depo.zamanlayicilar(context)) {
            val niyet = niyet(context, z.id)
            if (!z.calisiyor || ZamanlayiciHizmeti.calanlar.contains(z.id)) {
                yonetici.cancel(niyet)
                continue
            }
            // Telefon kapalıyken dolmuşsa hemen çalsın.
            val an = maxOf(z.bitis, simdi + 1000)
            val goster = PendingIntent.getActivity(
                context, KAYMA + z.id,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EK_SEKME, MainActivity.SEKME_ZAMANLAYICI),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (AlarmKurucu.tamZamanliKurulabilir(yonetici)) {
                yonetici.setAlarmClock(AlarmManager.AlarmClockInfo(an, goster), niyet)
            } else {
                yonetici.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, niyet)
            }
        }
        CalisanBildirim.guncelle(context)
    }

    fun iptal(context: Context, id: Int) {
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        yonetici.cancel(niyet(context, id))
    }

    private fun niyet(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, KAYMA + id,
        Intent(context, AlarmAlici::class.java).setAction(EYLEM_DOLDU).putExtra(AlarmKurucu.EK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
