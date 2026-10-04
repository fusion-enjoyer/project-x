package com.ekosistem.takvim

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Etkinlik hatırlatıcısı bildirimi: başlık, saat/konum, "Ertele" ve kapatma. */
object Bildirimler {
    const val KANAL = "etkinlik"

    fun kanalKur(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val kanal = NotificationChannel(KANAL, c.getString(R.string.kanal_etkinlik), NotificationManager.IMPORTANCE_HIGH)
        kanal.description = c.getString(R.string.kanal_etkinlik_ozet)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
    }

    /** Aynı etkinliğin aynı örneği hep aynı bildirimi günceller. */
    fun bildirimNo(etkinlikId: Long, baslangic: Long): Int =
        ((etkinlikId * 31 + baslangic) and 0x7fffffff).toInt()

    private fun bayrak(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    fun goster(c: Context, etkinlikId: Long, baslangic: Long, bitis: Long, baslik: String, konum: String, tumGun: Boolean, baglanti: String? = null) {
        kanalKur(c)
        val no = bildirimNo(etkinlikId, baslangic)
        val tz = java.util.TimeZone.getDefault()
        val ornek = Ornek.olustur(etkinlikId, 0, baslik, konum, baslangic, bitis, tumGun, 0, false, tz)
        val gun = ornek.ilkGun
        val saat = Metinler.ornekSaati(c, ornek, gun)
        val altYazi = if (konum.isBlank()) saat else "$saat · ${konum.lines().first()}"

        val ac = PendingIntent.getActivity(
            c, no,
            Intent(c, DetayActivity::class.java)
                .putExtra(DetayActivity.EK_ID, etkinlikId).putExtra(DetayActivity.EK_BAS, baslangic)
                .putExtra(DetayActivity.EK_BIT, bitis)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            bayrak()
        )
        fun yayin(eylem: String, istek: Int) = PendingIntent.getBroadcast(
            c, istek,
            Intent(c, HatirlaticiAlici::class.java).setAction(eylem)
                .putExtra(HatirlaticiAlici.EK_ETKINLIK, etkinlikId).putExtra(HatirlaticiAlici.EK_BAS, baslangic)
                .putExtra(HatirlaticiAlici.EK_BIT, bitis).putExtra(HatirlaticiAlici.EK_BASLIK, baslik)
                
                .putExtra(HatirlaticiAlici.EK_KONUM, konum).putExtra(HatirlaticiAlici.EK_TUM_GUN, tumGun)
                .putExtra(HatirlaticiAlici.EK_BAGLANTI, baglanti.orEmpty()),
            bayrak()
        )
        val dk = Depo.ertelemeDk(c)
        val b = NotificationCompat.Builder(c, KANAL)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setColor(ContextCompat.getColor(c, R.color.vurgu))
            .setContentTitle(baslik.ifBlank { c.getString(R.string.basliksiz) })
            .setContentText(altYazi)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(ac)
            .setDeleteIntent(yayin(HatirlaticiAlici.ACTION_KAPATILDI, no))
            .addAction(0, c.getString(R.string.ertele_n, dk), yayin(HatirlaticiAlici.ACTION_ERTELE, no + 1))
        if (baglanti != null) {
            // Toplantı bağlantısı varsa bildirimden tek dokunuşla katıl.
            val katil = PendingIntent.getActivity(
                c, no + 3, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(baglanti)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), bayrak()
            )
            b.addAction(0, c.getString(R.string.katil_bildirim) + " " + Baglanti.hizmet(baglanti), katil)
        }
        if (!tumGun) b.setWhen(baslangic).setShowWhen(true)
        try {
            NotificationManagerCompat.from(c).notify(no, b.build())
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş (Android 13+); ana ekrandaki şerit bunu söyler.
        }
    }

    fun kaldir(c: Context, etkinlikId: Long, baslangic: Long) {
        NotificationManagerCompat.from(c).cancel(bildirimNo(etkinlikId, baslangic))
    }
}
