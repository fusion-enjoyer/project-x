package com.ekosistem.galeri

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ekranda görünen tarih, süre ve boyut metinleri. */
object Metinler {

    private fun bicim(iskelet: String, an: Long): String {
        val yerel = Locale.getDefault()
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(yerel, iskelet), yerel).format(Date(an))
    }

    private fun String.basHarfBuyuk() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    /** Bölüm başlığı: "Ekim 2026" (bu yıl da yıl yazılır; kaydırırken yön kaybolmasın). */
    fun ayYil(an: Long) = bicim("LLLLy", an).basHarfBuyuk()

    /** Görüntüleyici başlığı: "4 Ekim 2026" */
    fun tamTarih(an: Long) = bicim("dMMMMy", an)

    /** "4 Ekim 2026 Pazar, 14:05" */
    fun tarihSaat(c: Context, an: Long): String =
        bicim("EEEEdMMMMy", an) + ", " + DateFormat.getTimeFormat(c).format(Date(an))

    /** Video süresi: "0:42", "12:05", "1:02:03". */
    fun sure(ms: Long): String {
        val s = (ms + 500) / 1000
        val sa = s / 3600
        val dk = (s % 3600) / 60
        val sn = s % 60
        return if (sa > 0) "%d:%02d:%02d".format(sa, dk, sn) else "%d:%02d".format(dk, sn)
    }

    fun boyut(c: Context, bayt: Long): String = Formatter.formatShortFileSize(c, bayt)

    /** Aynı günün anahtarı (yıl * 400 + yılın günü). */
    fun gunAnahtari(an: Long): Int {
        val t = Calendar.getInstance().apply { timeInMillis = an }
        return t.get(Calendar.YEAR) * 400 + t.get(Calendar.DAY_OF_YEAR)
    }

    /** Izgara bölümü: "Bugün", "Dün", "21 Kasım", geçen yıllar için "21 Kasım 2024". */
    fun gunBaslik(c: Context, an: Long, simdi: Long = System.currentTimeMillis()): String {
        val g = gunAnahtari(an)
        val bugun = gunAnahtari(simdi)
        val dun = gunAnahtari(simdi - 86_400_000L)
        return when {
            g == bugun -> c.getString(R.string.bugun)
            g == dun -> c.getString(R.string.dun)
            g / 400 == bugun / 400 -> bicim("dMMMM", an)
            else -> bicim("dMMMMy", an)
        }
    }

    /** Görüntüleyici başlığı: "Bugün", "Dün" ya da haftanın günü ("Pazartesi") / tarih. */
    fun gunAdi(c: Context, an: Long, simdi: Long = System.currentTimeMillis()): String {
        val g = gunAnahtari(an)
        return when {
            g == gunAnahtari(simdi) -> c.getString(R.string.bugun)
            g == gunAnahtari(simdi - 86_400_000L) -> c.getString(R.string.dun)
            simdi - an in 0..6 * 86_400_000L -> bicim("EEEE", an).basHarfBuyuk()
            else -> gunBaslik(c, an, simdi)
        }
    }

    /** "12:33" (telefonun 12/24 saat ayarına göre). */
    fun saat(c: Context, an: Long): String = DateFormat.getTimeFormat(c).format(Date(an))

    /** Aynı ayın anahtarı: 2026*12+9. */
    fun ayAnahtari(an: Long): Int {
        val t = Calendar.getInstance().apply { timeInMillis = an }
        return t.get(Calendar.YEAR) * 12 + t.get(Calendar.MONTH)
    }
}
