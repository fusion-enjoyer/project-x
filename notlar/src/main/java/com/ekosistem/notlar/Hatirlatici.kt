package com.ekosistem.notlar

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Not hatırlatıcıları. AlarmManager.setAlarmClock kullanılır: kesin zamanlıdır
 * ve ek izin istemez (SCHEDULE_EXACT_ALARM gerekmez).
 */
object Hatirlatici {

    private const val KANAL = "hatirlaticilar"

    fun kur(context: Context, uri: String, zaman: Long) {
        Prefs.hatirlaticiKaydet(context, uri, zaman)
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val niyet = PendingIntent.getBroadcast(
            context,
            uri.hashCode(),
            Intent(context, HatirlaticiAlicisi::class.java).setData(Uri.parse(uri)),
            NotWidget.bayrak(false)
        )
        try {
            yonetici.setAlarmClock(AlarmManager.AlarmClockInfo(zaman, niyet), niyet)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            yonetici.set(AlarmManager.RTC_WAKEUP, zaman, niyet)
        }
    }

    fun kaldir(context: Context, uri: String) {
        Prefs.hatirlaticiKaydet(context, uri, 0L)
        val yonetici = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val niyet = PendingIntent.getBroadcast(
            context,
            uri.hashCode(),
            Intent(context, HatirlaticiAlicisi::class.java).setData(Uri.parse(uri)),
            NotWidget.bayrak(false)
        )
        yonetici.cancel(niyet)
        niyet.cancel()
    }

    /** Cihaz yeniden başladığında alarmlar silinir; hepsini yeniden kurar. */
    fun hepsiniYenidenKur(context: Context) {
        val simdi = System.currentTimeMillis()
        for ((uri, zaman) in Prefs.tumHatirlaticilar(context)) {
            if (zaman > simdi) kur(context, uri, zaman) else Prefs.hatirlaticiKaydet(context, uri, 0L)
        }
    }

    fun bildirimGoster(context: Context, uri: String) {
        val yonetici = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                KANAL,
                context.getString(R.string.hatirlaticilar),
                NotificationManager.IMPORTANCE_HIGH
            )
            yonetici.createNotificationChannel(kanal)
        }

        val depo = NotDeposu(context)
        val icerik = depo.oku(Uri.parse(uri), 512)
        val baslik = icerik.lines().firstOrNull { it.isNotBlank() }
            ?.trimStart('#', '-', '>', ' ')
            ?.take(60)
            ?: context.getString(R.string.app_name)

        val acNiyeti = PendingIntent.getActivity(
            context,
            uri.hashCode(),
            Intent(context, EditorActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(uri))
                .putExtra("uri", uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            NotWidget.bayrak(false)
        )

        val kurucu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, KANAL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val bildirim = kurucu
            .setContentTitle(baslik)
            .setContentText(context.getString(R.string.hatirlatici))
            .setSmallIcon(R.drawable.ic_bildirim)
            .setAutoCancel(true)
            .setContentIntent(acNiyeti)
            .build()
        try {
            yonetici.notify(uri.hashCode(), bildirim)
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş; sessizce geçilir.
        }
        Prefs.hatirlaticiKaydet(context, uri, 0L)
    }
}

class HatirlaticiAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Hatirlatici.hepsiniYenidenKur(context)
            return
        }
        val uri = intent.data?.toString() ?: return
        Hatirlatici.bildirimGoster(context, uri)
    }
}
