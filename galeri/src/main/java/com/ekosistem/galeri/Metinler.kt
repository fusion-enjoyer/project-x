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

    /** Aynı ayın anahtarı: 2026*12+9. */
    fun ayAnahtari(an: Long): Int {
        val t = Calendar.getInstance().apply { timeInMillis = an }
        return t.get(Calendar.YEAR) * 12 + t.get(Calendar.MONTH)
    }
}
