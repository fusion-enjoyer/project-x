package com.ekosistem.saat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** Uygulamanın kendi kurduğu olaylar: çalma, yaklaşan bildirim, "bu sefer çalmasın". */
class AlarmAlici : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmKurucu.EK_ID, -1)
        when (intent.action) {
            AlarmKurucu.EYLEM_CAL -> {
                // Ön plan hizmeti tam zamanlı alarm muafiyetiyle başlar (Android 12+).
                val hizmet = Intent(context, CalmaHizmeti::class.java)
                    .setAction(CalmaHizmeti.EYLEM_BASLA)
                    .putExtra(AlarmKurucu.EK_ID, id)
                ContextCompat.startForegroundService(context, hizmet)
            }
            AlarmKurucu.EYLEM_YAKLASAN -> {
                val alarm = Depo.alarm(context, id) ?: return
                if (alarm.acik && alarm.kurulanZaman > System.currentTimeMillis()) {
                    Bildirimler.yaklasan(context, alarm, alarm.kurulanZaman)
                }
            }
            EYLEM_ERTELEMEYI_BITIR -> {
                val alarm = Depo.alarm(context, id) ?: return
                Depo.yaz(context, Zamanlama.calmaBitti(alarm))
                Bildirimler.ertelemeyiKaldir(context, id)
                AlarmKurucu.hepsiniKur(context)
            }
            EYLEM_BU_SEFER_ATLA -> {
                val alarm = Depo.alarm(context, id) ?: return
                Depo.yaz(context, buSeferAtla(alarm))
                Bildirimler.yaklasaniKaldir(context, id)
                AlarmKurucu.hepsiniKur(context)
            }
        }
    }

    companion object {
        const val EYLEM_BU_SEFER_ATLA = "com.ekosistem.saat.BU_SEFER_ATLA"
        const val EYLEM_ERTELEMEYI_BITIR = "com.ekosistem.saat.ERTELEMEYI_BITIR"

        /** Tekrarlı alarm o günü atlar; tek seferlik alarm kapanır. */
        fun buSeferAtla(alarm: Alarm): Alarm {
            val simdi = System.currentTimeMillis()
            return if (alarm.tekrarli) {
                Zamanlama.atlamayiDegistir(alarm.copy(atla = 0), simdi, TimeZone.getDefault())
            } else {
                alarm.copy(acik = false, tarih = 0, kurulanZaman = 0)
            }
        }
    }
}

/**
 * Sistemin olayları: açılış (kilit açılmadan da), saat ya da saat dilimi
 * değişimi, uygulama güncellemesi, tam zamanlı alarm izninin değişmesi.
 * Hepsinde alarmlar baştan kurulur; Fossify'da yaz saati geçişinden sonra
 * alarm bir saat kayıyordu çünkü bu yapılmıyordu.
 */
class SistemAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in OLAYLAR) return
        val sonuc = goAsync()
        Thread {
            try {
                AlarmKurucu.hepsiniKur(context)
            } finally {
                sonuc.finish()
            }
        }.start()
    }

    private companion object {
        val OLAYLAR = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
