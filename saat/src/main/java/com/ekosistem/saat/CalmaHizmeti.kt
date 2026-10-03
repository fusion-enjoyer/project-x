package com.ekosistem.saat

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Date

/**
 * Alarm çalarken ayakta duran ön plan hizmeti: ses, titreşim, kilit ekranında
 * tam ekran açılan bildirim, erteleme ve kapatma. Kimse dokunmazsa ayarlanan
 * sürede susar ve kaçırılan alarm bildirimi bırakır.
 */
class CalmaHizmeti : Service() {

    private var sesi: AlarmSesi? = null
    private var uyanik: PowerManager.WakeLock? = null
    private val isleyici = Handler(Looper.getMainLooper())
    private val zamanAsimi = Runnable { sustur() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmKurucu.EK_ID, calanId) ?: calanId
        when (intent?.action) {
            EYLEM_BASLA -> basla(id)
            EYLEM_ERTELE -> ertele(intent.getIntExtra(EK_DAKIKA, 0))
            EYLEM_KAPAT -> kapat()
            else -> if (calanId < 0) {
                // Sistem hizmeti yeniden başlattı ama çalan alarm yok.
                onPlana(null)
                kendiniDurdur()
            }
        }
        return START_NOT_STICKY
    }

    private fun basla(id: Int) {
        val alarm = Depo.alarm(this, id)
        if (alarm == null || !alarm.acik) {
            // startForegroundService'ten sonra ne olursa olsun öne geçilmeli;
            // başka bir alarm çalıyorsa onun bildirimi korunur.
            if (calanId >= 0) {
                onPlana(Depo.alarm(this, calanId))
            } else {
                onPlana(null)
                kendiniDurdur()
            }
            return
        }
        onPlana(alarm)
        // Başka bir alarm çalarken yenisi gelirse eskisi biter, yenisi çalar.
        if (calanId >= 0 && calanId != id) bitir(calanId, kacirildi = false)
        sesi?.durdur()

        calanId = id
        uyanikTut()
        sesi = AlarmSesi(this).also { it.baslat(alarm) }
        isleyici.removeCallbacks(zamanAsimi)
        isleyici.postDelayed(zamanAsimi, Depo.susmaDk(this) * Zamanlama.DAKIKA)
        Bildirimler.yaklasaniKaldir(this, id)
        Bildirimler.ertelemeyiKaldir(this, id)
        // Android 10 öncesinde arka plandan ekran açılabiliyor; sonrasında tam ekran bildirim açar.
        if (Build.VERSION.SDK_INT < 29) {
            startActivity(CalmaActivity.niyet(this, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        dinleyici?.invoke()
    }

    private fun ertele(dakika: Int) {
        val id = calanId
        val alarm = Depo.alarm(this, id) ?: return kapat()
        val ertelenmis = Zamanlama.ertele(
            alarm, System.currentTimeMillis(), if (dakika > 0) dakika else alarm.ertelemeDk
        ) ?: return // sınır doldu: çalmaya devam
        Depo.yaz(this, ertelenmis)
        Bildirimler.ertelendi(this, ertelenmis)
        durdurVeKur()
    }

    private fun kapat() {
        if (calanId >= 0) bitir(calanId, kacirildi = false)
        durdurVeKur()
    }

    /** Kimse dokunmadı: susar, kaçırılan bildirimi bırakır, alarm sıradakine geçer. */
    private fun sustur() {
        if (calanId >= 0) bitir(calanId, kacirildi = true)
        durdurVeKur()
    }

    private fun bitir(id: Int, kacirildi: Boolean) {
        val alarm = Depo.alarm(this, id) ?: return
        if (kacirildi) Bildirimler.kacirildi(this, alarm, System.currentTimeMillis())
        Depo.yaz(this, Zamanlama.calmaBitti(alarm))
        Bildirimler.ertelemeyiKaldir(this, id)
    }

    private fun durdurVeKur() {
        sesi?.durdur()
        sesi = null
        isleyici.removeCallbacks(zamanAsimi)
        calanId = -1
        AlarmKurucu.hepsiniKur(this)
        dinleyici?.invoke()
        kendiniDurdur()
    }

    private fun kendiniDurdur() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun uyanikTut() {
        if (uyanik?.isHeld == true) return
        val guc = getSystemService(Context.POWER_SERVICE) as PowerManager
        uyanik = guc.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saat:calma").apply {
            acquire((Depo.susmaDk(this@CalmaHizmeti) + 1) * Zamanlama.DAKIKA)
        }
    }

    private fun onPlana(alarm: Alarm?) {
        Bildirimler.kanallariKur(this)
        val bildirim = calmaBildirimi(this, alarm)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                Bildirimler.NO_CALMA, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(Bildirimler.NO_CALMA, bildirim)
        }
    }

    override fun onDestroy() {
        sesi?.durdur()
        sesi = null
        isleyici.removeCallbacks(zamanAsimi)
        uyanik?.let { if (it.isHeld) it.release() }
        if (calanId >= 0) {
            // Sistem hizmeti öldürdüyse alarm sessizce yutulmasın.
            calanId = -1
            AlarmKurucu.hepsiniKur(this)
        }
        dinleyici?.invoke()
        super.onDestroy()
    }

    companion object {
        const val EYLEM_BASLA = "com.ekosistem.saat.BASLA"
        const val EYLEM_ERTELE = "com.ekosistem.saat.ERTELE"
        const val EYLEM_KAPAT = "com.ekosistem.saat.KAPAT"
        const val EK_DAKIKA = "dakika"

        /** Şu an çalan alarmın kimliği; çalan yoksa -1. */
        @Volatile
        var calanId = -1
            private set

        /** Çalma ekranı durum değişince (başladı, ertelendi, bitti) haberdar olsun. */
        var dinleyici: (() -> Unit)? = null

        fun eylem(context: Context, eylem: String, dakika: Int = 0) {
            val niyet = Intent(context, CalmaHizmeti::class.java).setAction(eylem)
            if (dakika > 0) niyet.putExtra(EK_DAKIKA, dakika)
            context.startService(niyet)
        }

        private fun hizmetNiyeti(context: Context, eylem: String, istek: Int): PendingIntent =
            PendingIntent.getService(
                context, istek,
                Intent(context, CalmaHizmeti::class.java).setAction(eylem),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun calmaBildirimi(context: Context, alarm: Alarm?): android.app.Notification {
            val tamEkran = PendingIntent.getActivity(
                context, 0, CalmaActivity.niyet(context, alarm?.id ?: -1),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val saat = android.text.format.DateFormat.getTimeFormat(context).format(Date())
            val etiket = alarm?.etiket?.ifBlank { null } ?: context.getString(R.string.alarm)
            val b = NotificationCompat.Builder(context, Bildirimler.KANAL_CALMA)
                .setSmallIcon(R.drawable.ic_alarm)
                .setColor(ContextCompat.getColor(context, R.color.vurgu))
                .setContentTitle(etiket)
                .setContentText(saat)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(tamEkran)
                .setFullScreenIntent(tamEkran, true)
            if (alarm != null) {
                if (alarm.ertelemeSiniri == 0 || alarm.ertelemeSayisi < alarm.ertelemeSiniri) {
                    b.addAction(
                        0, context.getString(R.string.ertele_dk, alarm.ertelemeDk),
                        hizmetNiyeti(context, EYLEM_ERTELE, 1)
                    )
                }
                // Görevli alarm bildirimden görevsiz kapatılamaz: ekran görev paneliyle açılır.
                val kapat = if (alarm.gorev > Gorev.YOK) {
                    PendingIntent.getActivity(
                        context, 3, CalmaActivity.niyet(context, alarm.id, gorev = true),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    hizmetNiyeti(context, EYLEM_KAPAT, 2)
                }
                b.addAction(0, context.getString(R.string.kapat_eylem), kapat)
            }
            return b.build()
        }
    }
}
