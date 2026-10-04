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
import java.util.Calendar

/**
 * Not hatırlatıcıları. AlarmManager.setAlarmClock kullanılır: kesin zamanlıdır
 * ve ek izin istemez (SCHEDULE_EXACT_ALARM gerekmez).
 *
 * Tekrarlı hatırlatıcıda alarm her seferinde tek kurulur; çalınca bir sonraki
 * zaman hesaplanıp yeniden kurulur.
 */
object Hatirlatici {

    private const val KANAL = "hatirlaticilar"

    const val TEKRAR_YOK = 0
    const val HER_GUN = 1
    const val HER_HAFTA = 2
    const val HER_AY = 3

    /**
     * Tekrarı da kaydeder; [kur] yalnızca zamanı kurar, tekrara dokunmaz.
     * [capa], tekrarların sayıldığı ilk zamandır (not taşınırken korunur).
     */
    fun kur(context: Context, uri: String, zaman: Long, tekrar: Int, capa: Long = zaman) {
        Prefs.hatirlaticiTekrariKaydet(context, uri, tekrar, capa)
        kur(context, uri, zaman)
    }

    /** Bir sonraki tekrar, her zaman ilk zamandan sayılarak. */
    private fun sonraki(context: Context, uri: String, zaman: Long, tekrar: Int): Long {
        val capa = Prefs.hatirlaticiCapasi(context, uri).takeIf { it > 0 } ?: zaman
        return sonrakiZaman(capa, tekrar, maxOf(zaman, System.currentTimeMillis()))
    }

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
        Prefs.hatirlaticiTekrariKaydet(context, uri, TEKRAR_YOK, 0L)
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

    /**
     * Cihaz yeniden başladığında alarmlar silinir; hepsini yeniden kurar.
     * Kapalıyken kaçan tekrarlı hatırlatıcı bir sonraki zamana kurulur.
     */
    fun hepsiniYenidenKur(context: Context) {
        val simdi = System.currentTimeMillis()
        for ((uri, zaman) in Prefs.tumHatirlaticilar(context)) {
            val tekrar = Prefs.hatirlaticiTekrari(context, uri)
            when {
                zaman > simdi -> kur(context, uri, zaman)
                tekrar != TEKRAR_YOK -> kur(context, uri, sonraki(context, uri, zaman, tekrar))
                else -> Prefs.hatirlaticiKaydet(context, uri, 0L)
            }
        }
    }

    /**
     * [zaman]dan sonra gelen ve [simdi]yi geçen ilk tekrar. Takvimle ilerlenir:
     * yaz saati geçişinde saat kaymaz. Ayda bir tekrar her seferinde ilk
     * zamandan sayılır; 31'inde kurulan hatırlatıcı şubatta 28'ine düşse de
     * martta yine 31'ine döner.
     */
    fun sonrakiZaman(zaman: Long, tekrar: Int, simdi: Long): Long {
        val alan = when (tekrar) {
            HER_GUN -> Calendar.DAY_OF_YEAR
            HER_HAFTA -> Calendar.WEEK_OF_YEAR
            HER_AY -> Calendar.MONTH
            else -> return zaman
        }
        var adim = 1
        while (true) {
            val takvim = Calendar.getInstance()
            takvim.timeInMillis = zaman
            takvim.add(alan, adim)
            if (takvim.timeInMillis > simdi) return takvim.timeInMillis
            adim++
        }
    }

    fun bildirimGoster(context: Context, uri: String) {
        val yonetici = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        val depo = NotDeposu(context)
        val adres = Uri.parse(uri)
        // Silinmiş ya da çöpteki notun hatırlatıcısı çalmaz; tekrarlıysa da durur.
        // (Çöpten zamanında geri alınan notun hatırlatıcısı yerinde kalır.)
        if (depo.docGetir(adres)?.exists() != true || depo.copteMi(adres)) {
            kaldir(context, uri)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                KANAL,
                context.getString(R.string.hatirlaticilar),
                NotificationManager.IMPORTANCE_HIGH
            )
            yonetici.createNotificationChannel(kanal)
        }

        val icerik = depo.oku(adres, 512)
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
            .setContentText(context.getString(R.string.hatirlatici) + tekrarEki(context, uri))
            .setSmallIcon(R.drawable.ic_bildirim)
            .setAutoCancel(true)
            .setContentIntent(acNiyeti)
            .build()
        try {
            yonetici.notify(uri.hashCode(), bildirim)
        } catch (_: SecurityException) {
            // Bildirim izni verilmemiş; sessizce geçilir.
        }
        val tekrar = Prefs.hatirlaticiTekrari(context, uri)
        val zaman = Prefs.hatirlatici(context, uri)
        if (tekrar != TEKRAR_YOK && zaman > 0) {
            kur(context, uri, sonraki(context, uri, zaman, tekrar))
        } else {
            Prefs.hatirlaticiKaydet(context, uri, 0L)
        }
    }

    /** Bildirimde "Hatırlatıcı · her gün" gibi görünen ek. */
    private fun tekrarEki(context: Context, uri: String): String {
        val ad = tekrarAdi(Prefs.hatirlaticiTekrari(context, uri)) ?: return ""
        return " · " + context.getString(ad)
    }

    /** Tekrarın görünen adı; tekrarsızsa null. */
    fun tekrarAdi(tekrar: Int): Int? = when (tekrar) {
        HER_GUN -> R.string.tekrar_her_gun
        HER_HAFTA -> R.string.tekrar_her_hafta
        HER_AY -> R.string.tekrar_her_ay
        else -> null
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
