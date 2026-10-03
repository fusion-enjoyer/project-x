package com.ekosistem.takvim

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts

/**
 * Hatırlatıcı yolu. Takvim deposu vakti gelince `EVENT_REMINDER` yayınlar ve
 * bildirimi göstermeyi takvim uygulamasına bırakır; o uygulama biziz. Yayın
 * gelince "zamanı gelmiş" uyarılar okunur, "tetiklendi" işaretlenir ve bildirim
 * çıkar. Ertelemeler kendi alarmımızla kurulur ve telefon yeniden başlayınca
 * yeniden kurulur.
 *
 * Telefonda Google Takvim de varsa aynı yayını o da alır; bildirimi iki
 * uygulama da gösterebilir (hangisi uyarıyı önce işaretlerse onunki gelir).
 */
class HatirlaticiAlici : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        val bekleyen = goAsync()
        Thread {
            try {
                isle(c.applicationContext, intent)
            } finally {
                bekleyen.finish()
            }
        }.start()
    }

    private fun isle(c: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ERTELE -> ertele(c, intent)
            ACTION_ERTELEME_BITTI -> ertelemeBitti(c, intent)
            ACTION_KAPATILDI -> uyariyiKapat(c, intent.getLongExtra(EK_ETKINLIK, 0), intent.getLongExtra(EK_BAS, 0))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ertelemeleriKur(c)
                uyarilariIsle(c)
            }
            else -> uyarilariIsle(c)   // EVENT_REMINDER
        }
    }

    private fun ertele(c: Context, intent: Intent) {
        val e = intent.getLongExtra(EK_ETKINLIK, 0)
        val b = intent.getLongExtra(EK_BAS, 0)
        val n = intent.getLongExtra(EK_BIT, 0)
        val baslik = intent.getStringExtra(EK_BASLIK).orEmpty()
        val konum = intent.getStringExtra(EK_KONUM).orEmpty()
        val tumGun = intent.getBooleanExtra(EK_TUM_GUN, false)
        Bildirimler.kaldir(c, e, b)
        uyariyiKapat(c, e, b)
        val an = System.currentTimeMillis() + Depo.ertelemeDk(c) * 60_000L
        Depo.ertelemeEkle(c, e, b, n, an, baslik, konum, tumGun)
        alarmKur(c, e, b, n, an, baslik, konum, tumGun)
    }

    private fun ertelemeBitti(c: Context, intent: Intent) {
        val e = intent.getLongExtra(EK_ETKINLIK, 0)
        val b = intent.getLongExtra(EK_BAS, 0)
        Depo.ertelemeCikar(c, e, b)
        Bildirimler.goster(
            c, e, b, intent.getLongExtra(EK_BIT, 0), intent.getStringExtra(EK_BASLIK).orEmpty(),
            intent.getStringExtra(EK_KONUM).orEmpty(), intent.getBooleanExtra(EK_TUM_GUN, false)
        )
    }

    companion object {
        const val ACTION_ERTELE = "com.ekosistem.takvim.ERTELE"
        const val ACTION_ERTELEME_BITTI = "com.ekosistem.takvim.ERTELEME_BITTI"
        const val ACTION_KAPATILDI = "com.ekosistem.takvim.KAPATILDI"
        const val EK_ETKINLIK = "etkinlik"
        const val EK_BAS = "baslangic"
        const val EK_BIT = "bitis"
        const val EK_BASLIK = "baslik"
        const val EK_KONUM = "konum"
        const val EK_TUM_GUN = "tum_gun"

        /** Zamanı gelmiş (SCHEDULED) uyarıları "tetiklendi" yapıp bildirimini gösterir. */
        fun uyarilariIsle(c: Context) {
            if (!TakvimDeposu.izinVar(c)) return
            try {
                val simdi = System.currentTimeMillis()
                data class Uyari(val id: Long, val etkinlik: Long, val bas: Long, val bit: Long, val baslik: String, val konum: String, val tumGun: Boolean)
                val liste = ArrayList<Uyari>()
                c.contentResolver.query(
                    CalendarAlerts.CONTENT_URI,
                    arrayOf(
                        CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
                        CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY
                    ),
                    "${CalendarAlerts.STATE}=? AND ${CalendarAlerts.ALARM_TIME}<=?",
                    arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), simdi.toString()), null
                )?.use { k ->
                    while (k.moveToNext()) {
                        liste.add(
                            Uyari(
                                k.getLong(0), k.getLong(1), k.getLong(2), k.getLong(3),
                                k.getString(4).orEmpty(), k.getString(5).orEmpty(), k.getInt(6) != 0
                            )
                        )
                    }
                }
                for (u in liste) {
                    val v = ContentValues().apply {
                        put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED)
                        put(CalendarAlerts.RECEIVED_TIME, simdi)
                        put(CalendarAlerts.NOTIFY_TIME, simdi)
                    }
                    c.contentResolver.update(ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, u.id), v, null, null)
                    Bildirimler.goster(c, u.etkinlik, u.bas, u.bit, u.baslik, u.konum, u.tumGun)
                }
            } catch (_: RuntimeException) {
            }
        }

        /** Bildirim kaydırılınca/ertelenince uyarı depoda "kapatıldı" olur, yeniden çıkmaz. */
        fun uyariyiKapat(c: Context, etkinlikId: Long, baslangic: Long) {
            if (!TakvimDeposu.izinVar(c)) return
            try {
                val v = ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED) }
                c.contentResolver.update(
                    CalendarAlerts.CONTENT_URI, v,
                    "${CalendarAlerts.EVENT_ID}=? AND ${CalendarAlerts.BEGIN}=? AND ${CalendarAlerts.STATE}=?",
                    arrayOf(etkinlikId.toString(), baslangic.toString(), CalendarAlerts.STATE_FIRED.toString())
                )
            } catch (_: RuntimeException) {
            }
        }

        private fun alarmKur(c: Context, e: Long, b: Long, n: Long, an: Long, baslik: String, konum: String, tumGun: Boolean) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val islem = PendingIntent.getBroadcast(
                c, Bildirimler.bildirimNo(e, b) + 2,
                Intent(c, HatirlaticiAlici::class.java).setAction(ACTION_ERTELEME_BITTI)
                    .putExtra(EK_ETKINLIK, e).putExtra(EK_BAS, b).putExtra(EK_BIT, n)
                    .putExtra(EK_BASLIK, baslik).putExtra(EK_KONUM, konum).putExtra(EK_TUM_GUN, tumGun),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            try {
                when {
                    Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, islem)
                    Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, islem)
                    else -> am.setExact(AlarmManager.RTC_WAKEUP, an, islem)
                }
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, an, islem)
            }
        }

        /** Yeniden başlatma sonrası bekleyen ertelemeleri yeniden kurar; geçmişte kalanlar hemen çıkar. */
        fun ertelemeleriKur(c: Context) {
            for (j in Depo.ertelemeler(c)) {
                alarmKur(
                    c, j.optLong("e"), j.optLong("b"), j.optLong("n"), maxOf(j.optLong("a"), System.currentTimeMillis() + 2000L),
                    j.optString("t"), j.optString("k"), j.optBoolean("g")
                )
            }
        }
    }
}
