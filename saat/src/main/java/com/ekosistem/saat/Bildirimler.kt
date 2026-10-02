package com.ekosistem.saat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/** Bildirim kanalları ve alarm dışındaki bildirimler (yaklaşan, kaçırılan). */
object Bildirimler {

    const val KANAL_CALMA = "calma"
    const val KANAL_YAKLASAN = "yaklasan"
    const val KANAL_KACIRILAN = "kacirilan"

    const val NO_CALMA = 1
    private const val NO_YAKLASAN = 10_000
    private const val NO_KACIRILAN = 20_000
    private const val NO_ERTELEME = 30_000

    fun kanallariKur(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val yonetici = context.getSystemService(NotificationManager::class.java)
        val calma = NotificationChannel(
            KANAL_CALMA, context.getString(R.string.kanal_calma), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            // Sesi hizmet kendisi çalar (alarm kanalından, kademeli); bildirim sessiz.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val yaklasan = NotificationChannel(
            KANAL_YAKLASAN, context.getString(R.string.kanal_yaklasan), NotificationManager.IMPORTANCE_LOW
        )
        val kacirilan = NotificationChannel(
            KANAL_KACIRILAN, context.getString(R.string.kanal_kacirilan), NotificationManager.IMPORTANCE_DEFAULT
        )
        yonetici.createNotificationChannels(listOf(calma, yaklasan, kacirilan))
    }

    private fun yonetici(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun saatMetni(context: Context, an: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(an))

    private fun uygulamayiAc(context: Context, istek: Int): PendingIntent = PendingIntent.getActivity(
        context, istek, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** "İş · 06:30 — 1 saat sonra çalacak" ve "Bu sefer çalmasın" düğmesi. */
    fun yaklasan(context: Context, alarm: Alarm, an: Long) {
        kanallariKur(context)
        val atla = PendingIntent.getBroadcast(
            context, NO_YAKLASAN + alarm.id,
            Intent(context, AlarmAlici::class.java)
                .setAction(AlarmAlici.EYLEM_BU_SEFER_ATLA)
                .putExtra(AlarmKurucu.EK_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val baslik = listOf(alarm.etiket.ifBlank { context.getString(R.string.alarm) }, saatMetni(context, an))
            .joinToString(" · ")
        val b = NotificationCompat.Builder(context, KANAL_YAKLASAN)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(ContextCompat.getColor(context, R.color.vurgu))
            .setContentTitle(baslik)
            .setContentText(context.getString(R.string.yaklasan_metin))
            .setContentIntent(uygulamayiAc(context, NO_YAKLASAN + alarm.id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(false)
            .setOngoing(false)
            .addAction(0, context.getString(R.string.bu_sefer_calmasin), atla)
        gonder(context, NO_YAKLASAN + alarm.id, b.build())
    }

    fun yaklasaniKaldir(context: Context, id: Int) = yonetici(context).cancel(NO_YAKLASAN + id)

    /** "Ertelendi · 06:40'ta çalacak" ve "Şimdi kapat" düğmesi. */
    fun ertelendi(context: Context, alarm: Alarm) {
        kanallariKur(context)
        val bitir = PendingIntent.getBroadcast(
            context, NO_ERTELEME + alarm.id,
            Intent(context, AlarmAlici::class.java)
                .setAction(AlarmAlici.EYLEM_ERTELEMEYI_BITIR)
                .putExtra(AlarmKurucu.EK_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(context, KANAL_YAKLASAN)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(ContextCompat.getColor(context, R.color.vurgu))
            .setContentTitle(alarm.etiket.ifBlank { context.getString(R.string.alarm) })
            .setContentText(context.getString(R.string.ertelendi_metin, saatMetni(context, alarm.ertelemeZamani)))
            .setContentIntent(uygulamayiAc(context, NO_ERTELEME + alarm.id))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, context.getString(R.string.simdi_kapat), bitir)
        gonder(context, NO_ERTELEME + alarm.id, b.build())
    }

    fun ertelemeyiKaldir(context: Context, id: Int) = yonetici(context).cancel(NO_ERTELEME + id)

    /** Bir şey ters gittiyse kullanıcı en azından bilsin. */
    fun kacirildi(context: Context, alarm: Alarm, an: Long) {
        kanallariKur(context)
        val metin = context.getString(
            R.string.kacirilan_metin,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(an)),
            saatMetni(context, an)
        )
        val b = NotificationCompat.Builder(context, KANAL_KACIRILAN)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(ContextCompat.getColor(context, R.color.vurgu))
            .setContentTitle(context.getString(R.string.kacirilan_baslik, alarm.etiket.ifBlank { context.getString(R.string.alarm) }))
            .setContentText(metin)
            .setContentIntent(uygulamayiAc(context, NO_KACIRILAN + alarm.id))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
        gonder(context, NO_KACIRILAN + alarm.id, b.build())
    }

    fun gonder(context: Context, no: Int, bildirim: Notification) {
        // Android 13+ izin yoksa sessizce düşer; kontrol sayfası bunu söyler.
        runCatching { yonetici(context).notify(no, bildirim) }
    }

    fun izinVar(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 24) yonetici(context).areNotificationsEnabled() else true

    /** Android 14+: kilit ekranında tam ekran açılma izni. */
    fun tamEkranVar(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || yonetici(context).canUseFullScreenIntent()
}
