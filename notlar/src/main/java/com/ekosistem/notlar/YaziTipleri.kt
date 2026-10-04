package com.ekosistem.notlar

import android.content.Context
import android.graphics.Typeface

/**
 * Not yazısının yazı tipi. Hepsi telefonun kendi yazı tipleri (Android 5'ten
 * beri her cihazda bu adlarla bulunur): uygulamaya tek bayt eklemez, internet
 * gerektirmez. Üretici bir aileyi kaldırmışsa Android sessizce sistem yazısına
 * düşer; uygulama bozulmaz.
 *
 * Seçilen yazı tipi notun göründüğü her yerde kullanılır: editör, not kartları,
 * görevler, çöp kutusu, sürüm geçmişi. Menüler ve düğmeler sistem yazısında kalır.
 */
object YaziTipleri {

    /** [kimlik] ayarlara yazılan sayıdır; sıra değişse de kayıtlı seçim bozulmaz. */
    class Secenek(val kimlik: Int, val adKaynagi: Int, val aile: String?)

    /** Seçicide görünen sıra: sade olandan süslüye. */
    val SECENEKLER = listOf(
        Secenek(0, R.string.yazi_sistem, null),
        Secenek(3, R.string.yazi_ince, "sans-serif-light"),
        Secenek(4, R.string.yazi_dar, "sans-serif-condensed"),
        Secenek(1, R.string.yazi_serif, "serif"),
        Secenek(2, R.string.yazi_mono, "monospace"),
        Secenek(5, R.string.yazi_daktilo, "serif-monospace"),
        Secenek(6, R.string.yazi_el, "casual"),
        Secenek(7, R.string.yazi_kaligrafi, "cursive")
    )

    private val onbellek = HashMap<Int, Typeface>()

    fun secenek(kimlik: Int): Secenek = SECENEKLER.firstOrNull { it.kimlik == kimlik } ?: SECENEKLER[0]

    fun tip(kimlik: Int): Typeface = onbellek.getOrPut(kimlik) {
        val aile = secenek(kimlik).aile
        if (aile == null) Typeface.DEFAULT else Typeface.create(aile, Typeface.NORMAL)
    }

    /** Ayarlarda seçili yazı tipi. */
    fun yazi(c: Context): Typeface = tip(Prefs.yaziTipi(c))
}
