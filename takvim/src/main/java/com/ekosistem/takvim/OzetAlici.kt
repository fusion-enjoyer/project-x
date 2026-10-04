package com.ekosistem.takvim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Günlük özet (isteğe bağlı): her sabah seçilen saatte "Bugün 3 etkinlik" bildirimi, satırlarda saat ve başlık.
 * Etkinlik yoksa bildirim çıkmaz. Alarm tam zamanlı değildir (birkaç dakika sapabilir); telefon yeniden
 * açılınca, saat/dilim değişince ve her özetten sonra yeniden kurulur.
 */
class OzetAlici : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bekleyen = goAsync()
        Thread {
            try {
                val c = context.applicationContext
                if (intent.action == ACTION_OZET) goster(c)
                kur(c)
            } finally {
                bekleyen.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_OZET = "com.ekosistem.takvim.GUNLUK_OZET"
        private const val KANAL = "ozet"
        private const val BILDIRIM_NO = 5001
        private const val ISTEK = 72_500

        private fun bayrak(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

        private fun islem(c: Context) = PendingIntent.getBroadcast(
            c, ISTEK, Intent(c, OzetAlici::class.java).setAction(ACTION_OZET), bayrak()
        )

        /** Bir sonraki özet anını kurar; ayar kapalıysa varolan alarmı iptal eder. */
        fun kur(c: Context) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!Depo.ozetAcik(c)) {
                am.cancel(islem(c))
                return
            }
            val tz = TimeZone.getDefault()
            val simdi = System.currentTimeMillis()
            val bugun = Gun.yerelGun(simdi, tz)
            var an = Gun.yerelAn(bugun, Depo.ozetDk(c), tz)
            if (an <= simdi + 5_000L) an = Gun.yerelAn(bugun + 1, Depo.ozetDk(c), tz)
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, islem(c))
            else am.set(AlarmManager.RTC_WAKEUP, an, islem(c))
        }

        /** Bugünün etkinliklerinden özet bildirimi; etkinlik yoksa hiçbir şey göstermez. */
        fun goster(c: Context) {
            if (!Depo.ozetAcik(c) || !TakvimDeposu.izinVar(c)) return
            val tz = TimeZone.getDefault()
            val simdi = System.currentTimeMillis()
            val bugun = Gun.bugun(simdi, tz)
            val liste = TakvimDeposu.ornekler(c, bugun, bugun)
                .filter { it.gunuIcerir(bugun) }
                .sortedWith(compareBy<Ornek>({ !it.tumGun }, { it.gunBaslangicDk(bugun) }, { it.baslik }))
            if (liste.isEmpty()) return
            kanalKur(c)
            val stil = NotificationCompat.InboxStyle()
            val gosterilen = liste.take(6)
            for (o in gosterilen) {
                val ad = o.baslik.ifBlank { c.getString(R.string.basliksiz) }
                stil.addLine(if (o.tumGun) ad else Metinler.saat(c, o.baslangicDk) + "  " + ad)
            }
            if (liste.size > gosterilen.size) stil.setSummaryText(c.getString(R.string.ozet_daha, liste.size - gosterilen.size))
            val ilk = gosterilen.first()
            val ozet = (if (ilk.tumGun) "" else Metinler.saat(c, ilk.baslangicDk) + "  ") + ilk.baslik.ifBlank { c.getString(R.string.basliksiz) }
            val ac = PendingIntent.getActivity(
                c, ISTEK + 1, Intent(c, MainActivity::class.java).setAction("com.ekosistem.takvim.GUNDEM")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), bayrak()
            )
            val b = NotificationCompat.Builder(c, KANAL)
                .setSmallIcon(R.drawable.ic_bildirim)
                .setColor(ContextCompat.getColor(c, R.color.vurgu))
                .setContentTitle(c.getString(R.string.ozet_baslik, liste.size))
                .setContentText(ozet)
                .setStyle(stil)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(ac)
            try {
                NotificationManagerCompat.from(c).notify(BILDIRIM_NO, b.build())
            } catch (_: SecurityException) {
            }
        }

        private fun kanalKur(c: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val kanal = NotificationChannel(KANAL, c.getString(R.string.kanal_ozet), NotificationManager.IMPORTANCE_DEFAULT)
            kanal.description = c.getString(R.string.kanal_ozet_ozet)
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
        }
    }
}
