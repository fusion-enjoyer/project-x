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

/**
 * Süresi dolan zamanlayıcıyı çaldırır: alarm kanalından ses (sessiz modda da
 * duyulur, Fossify'da en çok şikâyet edilen eksik), titreşim, kilit ekranında
 * açılan ekran. "Durdur" zamanlayıcıyı ilk süresine döndürür, "+1 dk" uzatır.
 */
class ZamanlayiciHizmeti : Service() {

    private var sesi: AlarmSesi? = null
    private var uyanik: PowerManager.WakeLock? = null
    private val isleyici = Handler(Looper.getMainLooper())
    private val zamanAsimi = Runnable { durdur() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmKurucu.EK_ID, -1) ?: -1
        when (intent?.action) {
            EYLEM_BASLA -> basla(id)
            EYLEM_DURDUR -> durdur()
            EYLEM_EKLE -> ekle()
            else -> if (calanlar.isEmpty()) {
                onPlana(null)
                bitir()
            }
        }
        return START_NOT_STICKY
    }

    private fun basla(id: Int) {
        val z = Depo.zamanlayici(this, id)
        onPlana(z)
        if (z == null || !z.calisiyor) {
            if (calanlar.isEmpty()) bitir()
            return
        }
        calanlar.add(id)
        CalisanBildirim.guncelle(this)
        if (sesi == null) {
            sesi = AlarmSesi(this).also {
                it.baslat(Alarm(id = -1, saat = 0, dakika = 0, kademeliSn = 0, titresim = true))
            }
        }
        if (uyanik?.isHeld != true) {
            uyanik = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saat:zamanlayici")
                .apply { acquire((Depo.susmaDk(this@ZamanlayiciHizmeti) + 1) * Zamanlama.DAKIKA) }
        }
        isleyici.removeCallbacks(zamanAsimi)
        isleyici.postDelayed(zamanAsimi, Depo.susmaDk(this) * Zamanlama.DAKIKA)
        if (Build.VERSION.SDK_INT < 29) {
            startActivity(Intent(this, ZamanDolduActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        dinleyici?.invoke()
    }

    /** Çalan bütün zamanlayıcılar ilk sürelerine döner. */
    private fun durdur() {
        for (id in calanlar.toList()) Depo.zamanlayici(this, id)?.let { Depo.zamanlayiciYaz(this, it.sifirla()) }
        calanlar.clear()
        bitir()
    }

    /** Çalanlar bir dakika uzar ve yeniden işlemeye başlar. */
    private fun ekle() {
        val simdi = System.currentTimeMillis()
        for (id in calanlar.toList()) {
            Depo.zamanlayici(this, id)?.let { Depo.zamanlayiciYaz(this, it.ekle(60_000, simdi)) }
        }
        calanlar.clear()
        bitir()
    }

    private fun bitir() {
        sesi?.durdur()
        sesi = null
        isleyici.removeCallbacks(zamanAsimi)
        ZamanlayiciKurucu.hepsiniKur(this)
        dinleyici?.invoke()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun onPlana(z: Zamanlayici?) {
        Bildirimler.kanallariKur(this)
        val tamEkran = PendingIntent.getActivity(
            this, 3, Intent(this, ZamanDolduActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun eylem(e: String, istek: Int) = PendingIntent.getService(
            this, istek, Intent(this, ZamanlayiciHizmeti::class.java).setAction(e),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, Bildirimler.KANAL_CALMA)
            .setSmallIcon(R.drawable.ic_zamanlayici)
            .setColor(ContextCompat.getColor(this, R.color.vurgu))
            .setContentTitle(getString(R.string.sure_doldu))
            .setContentText(z?.etiket?.ifBlank { null } ?: Zamanlayici.bicim(z?.sure ?: 0))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(tamEkran)
            .setFullScreenIntent(tamEkran, true)
            .addAction(0, getString(R.string.bir_dk_ekle), eylem(EYLEM_EKLE, 4))
            .addAction(0, getString(R.string.durdur), eylem(EYLEM_DURDUR, 5))
        val bildirim = b.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NO_BILDIRIM, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NO_BILDIRIM, bildirim)
        }
    }

    override fun onDestroy() {
        sesi?.durdur()
        sesi = null
        isleyici.removeCallbacks(zamanAsimi)
        uyanik?.let { if (it.isHeld) it.release() }
        calanlar.clear()
        dinleyici?.invoke()
        super.onDestroy()
    }

    companion object {
        const val EYLEM_BASLA = "com.ekosistem.saat.ZAMAN_BASLA"
        const val EYLEM_DURDUR = "com.ekosistem.saat.ZAMAN_DURDUR"
        const val EYLEM_EKLE = "com.ekosistem.saat.ZAMAN_EKLE"
        private const val NO_BILDIRIM = 2

        /** Şu an çalan zamanlayıcılar. */
        val calanlar: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf())
        var dinleyici: (() -> Unit)? = null

        fun eylem(context: Context, e: String) =
            context.startService(Intent(context, ZamanlayiciHizmeti::class.java).setAction(e))
    }
}
