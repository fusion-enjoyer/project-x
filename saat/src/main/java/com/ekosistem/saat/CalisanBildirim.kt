package com.ekosistem.saat

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Zamanlayıcı ya da kronometre çalışırken bildirim alanında canlı süre.
 * Sayacı Android'in kendisi işletir (setUsesChronometer): uygulama arka planda
 * hiçbir şey çalıştırmaz, pil harcamaz. Durum her değiştiğinde [guncelle].
 */
object CalisanBildirim {

    private const val NO_ZAMANLAYICI = 40_000
    private const val NO_KRONOMETRE = 40_001

    fun guncelle(context: Context) {
        zamanlayici(context)
        kronometre(context)
    }

    private fun yonetici(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ac(c: Context, sekme: Int) = PendingIntent.getActivity(
        c, NO_ZAMANLAYICI + sekme,
        Intent(c, MainActivity::class.java).putExtra(MainActivity.EK_SEKME, sekme)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** En yakında bitecek çalışan zamanlayıcı; birden çoksa sayısı da yazılır. */
    private fun zamanlayici(c: Context) {
        val simdi = System.currentTimeMillis()
        val calisanlar = Depo.zamanlayicilar(c)
            .filter { it.calisiyor && it.bitis > simdi && it.id !in ZamanlayiciHizmeti.calanlar }
        val ilk = calisanlar.minByOrNull { it.bitis }
        if (ilk == null) {
            yonetici(c).cancel(NO_ZAMANLAYICI)
            return
        }
        Bildirimler.kanallariKur(c)
        val bitisSaati = Metinler.saat(c, ilk.bitis)
        val metin = buildString {
            append(c.getString(R.string.bitis_saati, bitisSaati))
            if (calisanlar.size > 1) append(" · ").append(c.getString(R.string.n_zamanlayici, calisanlar.size))
        }
        val b = temel(c, R.drawable.ic_zamanlayici, ilk.etiket.ifBlank { c.getString(R.string.zamanlayici) }, metin)
            .setContentIntent(ac(c, MainActivity.SEKME_ZAMANLAYICI))
        if (Build.VERSION.SDK_INT >= 24) {
            // Geri sayan sayaç; Android 7 öncesi yalnız bitiş saatini gösterir.
            b.setWhen(ilk.bitis).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
        }
        Bildirimler.gonder(c, NO_ZAMANLAYICI, b.build())
    }

    private fun kronometre(c: Context) {
        val k = Depo.kronometre(c)
        if (!k.calisiyor) {
            yonetici(c).cancel(NO_KRONOMETRE)
            return
        }
        Bildirimler.kanallariKur(c)
        // Sayacın başlangıcı duvar saatine çevrilir: şimdi − geçen süre.
        val baslangic = System.currentTimeMillis() - k.gecen(SystemClock.elapsedRealtime())
        val metin = if (k.turlar.isEmpty()) "" else c.getString(R.string.tur_n, k.turlar.size)
        val b = temel(c, R.drawable.ic_kronometre, c.getString(R.string.kronometre), metin)
            .setContentIntent(ac(c, MainActivity.SEKME_KRONOMETRE))
            .setWhen(baslangic).setUsesChronometer(true).setShowWhen(true)
        Bildirimler.gonder(c, NO_KRONOMETRE, b.build())
    }

    private fun temel(c: Context, ikon: Int, baslik: String, metin: String) =
        NotificationCompat.Builder(c, Bildirimler.KANAL_YAKLASAN)
            .setSmallIcon(ikon)
            .setColor(ContextCompat.getColor(c, R.color.vurgu))
            .setContentTitle(baslik)
            .setContentText(metin)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
}
