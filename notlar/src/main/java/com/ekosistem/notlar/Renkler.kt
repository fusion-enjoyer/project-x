package com.ekosistem.notlar

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Vurgu rengi kullanıcı tarafından seçilebilir. Her seçenek açık ve koyu tema
 * için ayrı tonda tutulur; böylece saf siyah zeminde de okunur kalır.
 *
 * Android 12+ cihazlarda [SISTEM] seçilirse renk duvar kağıdından gelen
 * Material You paletinden alınır (`vurgu_sistem`, values-v31).
 */
object Renkler {

    /** "Sistemle aynı" seçeneğinin kayıtlı değeri; renk listesinde yer almaz. */
    const val SISTEM = -1

    /** Sistem paleti (dinamik renk) yalnızca Android 12 ve sonrasında var. */
    fun sistemRengiVar(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Sistem seçili ama cihaz desteklemiyorsa (yedekten geldiyse) ilk renge düşer. */
    fun sistemSecili(context: Context): Boolean =
        Prefs.vurguIndeksi(context) == SISTEM && sistemRengiVar()

    data class Secenek(val adKaynagi: Int, val acik: Int, val koyu: Int)

    val SECENEKLER = listOf(
        Secenek(R.string.renk_amber, 0xFFB05E00.toInt(), 0xFFE8930C.toInt()),
        Secenek(R.string.renk_turuncu, 0xFFC2410C.toInt(), 0xFFF97316.toInt()),
        Secenek(R.string.renk_kirmizi, 0xFFA32D2D.toInt(), 0xFFE24B4A.toInt()),
        Secenek(R.string.renk_pembe, 0xFF993556.toInt(), 0xFFD4537E.toInt()),
        Secenek(R.string.renk_mor, 0xFF534AB7.toInt(), 0xFF7F77DD.toInt()),
        Secenek(R.string.renk_mavi, 0xFF185FA5.toInt(), 0xFF378ADD.toInt()),
        Secenek(R.string.renk_deniz, 0xFF0F6E56.toInt(), 0xFF1D9E75.toInt()),
        Secenek(R.string.renk_yesil, 0xFF3B6D11.toInt(), 0xFF639922.toInt()),
        Secenek(R.string.renk_gri, 0xFF5F5E5A.toInt(), 0xFFB4B2A9.toInt())
    )

    private val TEMALAR = intArrayOf(
        R.style.Theme_Notlar_Vurgu0,
        R.style.Theme_Notlar_Vurgu1,
        R.style.Theme_Notlar_Vurgu2,
        R.style.Theme_Notlar_Vurgu3,
        R.style.Theme_Notlar_Vurgu4,
        R.style.Theme_Notlar_Vurgu5,
        R.style.Theme_Notlar_Vurgu6,
        R.style.Theme_Notlar_Vurgu7,
        R.style.Theme_Notlar_Vurgu8
    )

    /**
     * Seçilen vurgu rengini taşıyan tema. Diyalog radyo düğmeleri, metin imleci
     * ve seçim tutamakları rengi buradan alır; kod içinden boyanamıyorlar.
     */
    fun temaStili(context: Context): Int {
        if (sistemSecili(context)) return R.style.Theme_Notlar_VurguSistem
        val indeks = Prefs.vurguIndeksi(context)
        return TEMALAR[indeks.coerceIn(0, TEMALAR.size - 1)]
    }

    fun geceMi(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun vurgu(context: Context): Int {
        if (sistemSecili(context)) return ContextCompat.getColor(context, R.color.vurgu_sistem)
        val indeks = Prefs.vurguIndeksi(context)
        if (indeks < 0 || indeks >= SECENEKLER.size) {
            return ContextCompat.getColor(context, R.color.vurgu)
        }
        val secenek = SECENEKLER[indeks]
        return if (geceMi(context)) secenek.koyu else secenek.acik
    }

    /** Vurgu zemini üzerine gelecek metin/ikon rengi. */
    fun vurguUzeri(context: Context): Int {
        if (sistemSecili(context)) return ContextCompat.getColor(context, R.color.vurgu_sistem_uzeri)
        return uzeriRengi(vurgu(context))
    }

    /** Verilen zemin renginin üstünde okunacak siyah ya da beyaz. */
    fun uzeriRengi(renk: Int): Int {
        val r = (renk shr 16) and 0xFF
        val g = (renk shr 8) and 0xFF
        val b = renk and 0xFF
        val parlaklik = (r * 299 + g * 587 + b * 114) / 1000
        return if (parlaklik > 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
