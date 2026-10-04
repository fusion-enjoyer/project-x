package com.ekosistem.takvim

import android.content.Context
import kotlin.math.pow

/**
 * Takvim/etkinlik renkleri kullanıcıdan ya da sunucudan gelir ve çoğu zaman zemine göre
 * okunmaz (koyu lacivert, siyah zeminde; açık sarı, beyazda). Küçük renk işaretleri (nokta, çubuk)
 * için zeminle en az 3:1 kontrast sağlanana dek renk zemine zıt yönde açılır/koyulaşır (WCAG 1.4.11).
 * Saf mantık; birim testle sınanır.
 */
object Renk {
    private const val ESIK = 3.0

    private fun kanal(k: Int): Double {
        val c = k / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun parlaklik(renk: Int): Double =
        0.2126 * kanal((renk shr 16) and 0xFF) + 0.7152 * kanal((renk shr 8) and 0xFF) + 0.0722 * kanal(renk and 0xFF)

    fun kontrast(a: Int, b: Int): Double {
        val la = parlaklik(a)
        val lb = parlaklik(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun karistir(a: Int, b: Int, t: Double): Int {
        fun k(s: Int) = Math.round(((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (k(16) shl 16) or (k(8) shl 8) or k(0)
    }

    /** [renk]i [gece] zeminine (beyaz/siyah) karşı en az 3:1 olacak biçimde ayarlar; yeterliyse dokunmaz. */
    fun yuzey(renk: Int, gece: Boolean): Int {
        val opak = renk or (0xFF shl 24)
        val zemin = if (gece) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        if (kontrast(opak, zemin) >= ESIK) return opak
        val hedef = if (gece) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        var t = 0.0
        var sonuc = opak
        while (t < 1.0 && kontrast(sonuc, zemin) < ESIK) {
            t += 0.05
            sonuc = karistir(opak, hedef, t)
        }
        return sonuc
    }

    fun yuzey(c: Context, renk: Int): Int = yuzey(renk, ZamanOlcusu.gece(c))
}
