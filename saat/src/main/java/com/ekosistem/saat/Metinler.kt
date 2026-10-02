package com.ekosistem.saat

import android.content.Context
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Ekranda görünen saat, gün ve kalan süre metinleri. */
object Metinler {

    fun saat(context: Context, an: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(an))

    /** Alarmın kendi saati (06:30), cihazın 12/24 saat tercihine göre. */
    fun alarmSaati(context: Context, saat: Int, dakika: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, saat)
            set(Calendar.MINUTE, dakika)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(c.time)
    }

    /**
     * 12 saat biçiminde "9:00 PM": büyük punto saatin yanında "PM" küçülür,
     * yoksa satıra sığmıyor. 24 saat biçiminde metne dokunmaz.
     */
    fun kucukOgleEki(metin: String): CharSequence {
        val ek = Regex("[\\s\\u00A0\\u202F]?([AaPp]\\.?[\\s\\u00A0\\u202F]?[Mm]\\.?|ÖÖ|ÖS)$").find(metin) ?: return metin
        return android.text.SpannableString(metin).apply {
            setSpan(android.text.style.RelativeSizeSpan(0.4f), ek.range.first, metin.length, 0)
        }
    }

    /** "8 sa 12 dk", "2 gün 3 sa", "12 dk". */
    fun kalan(context: Context, fark: Long): String {
        val (gun, saat, dakika) = Zamanlama.kalanParcalar(fark)
        return when {
            gun > 0 -> context.getString(R.string.kalan_gun, gun, saat)
            saat > 0 -> context.getString(R.string.kalan_saat, saat, dakika)
            else -> context.getString(R.string.kalan_dakika, dakika)
        }
    }

    /** "Bugün", "Yarın", hafta içindeyse gün adı ("Cumartesi"), değilse "12 Eki". */
    fun gun(context: Context, an: Long, simdi: Long = System.currentTimeMillis()): String {
        val tz = TimeZone.getDefault()
        val hedef = Zamanlama.gunAnahtari(an, tz)
        val bugun = Calendar.getInstance(tz).apply { timeInMillis = simdi }
        if (hedef == Zamanlama.gunAnahtari(bugun)) return context.getString(R.string.bugun)
        bugun.add(Calendar.DAY_OF_MONTH, 1)
        if (hedef == Zamanlama.gunAnahtari(bugun)) return context.getString(R.string.yarin)
        val fark = an - simdi
        val bicim = if (fark < 6 * 24 * 3_600_000L) "EEEE" else
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM")
        return SimpleDateFormat(bicim, Locale.getDefault()).format(Date(an)).replaceFirstChar { it.titlecase() }
    }

    /** yyyyAAgg → "12 Ekim Pazar". */
    fun tarih(gunAnahtari: Int): String {
        val c = Calendar.getInstance().apply {
            clear()
            set(gunAnahtari / 10000, (gunAnahtari / 100) % 100 - 1, gunAnahtari % 100)
        }
        val bicim = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMEEEE")
        return SimpleDateFormat(bicim, Locale.getDefault()).format(c.time)
    }

    fun kisaTarih(gunAnahtari: Int): String {
        val c = Calendar.getInstance().apply {
            clear()
            set(gunAnahtari / 10000, (gunAnahtari / 100) % 100 - 1, gunAnahtari % 100)
        }
        val bicim = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM")
        return SimpleDateFormat(bicim, Locale.getDefault()).format(c.time)
    }

    /** Pazartesiden başlayan gün kısaltmaları: tek harf (P S Ç…) ya da iki harf (Pt Sa…). */
    fun gunHarfleri(ikiHarf: Boolean): List<String> {
        val semboller = DateFormatSymbols.getInstance(Locale.getDefault())
        val kisa = semboller.shortWeekdays // 1 pazar … 7 cumartesi
        val sira = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        return sira.map { g ->
            val ad = kisa[g].trim('.')
            (if (ikiHarf) ad.take(2) else ad.take(1)).replaceFirstChar { it.titlecase() }
        }
    }

    /** Atlanan günün adı: "Çarşamba", "Yarın". */
    fun atlananGun(context: Context, alarm: Alarm): String {
        val an = Zamanlama.gununAni(alarm.atla, alarm.saat, alarm.dakika, TimeZone.getDefault())
        return gun(context, an)
    }
}
