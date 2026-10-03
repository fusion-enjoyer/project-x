package com.ekosistem.tasarim

import android.content.Context
import android.util.TypedValue

/**
 * Vurgu rengi her uygulamada farklı (Notlar amber, Saat kor turuncu);
 * bileşenler onu temadan (colorAccent) okur, uygulamaya bağımlı olmaz.
 */
object Tasarim {

    fun vurgu(context: Context): Int {
        val deger = TypedValue()
        val bulundu = context.theme.resolveAttribute(
            androidx.appcompat.R.attr.colorAccent, deger, true
        )
        return if (bulundu && deger.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            deger.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            deger.data
        } else {
            androidx.core.content.ContextCompat.getColor(context, R.color.vurgu)
        }
    }

    /** Vurgu zemini üzerine gelecek metin/ikon rengi (siyah ya da beyaz). */
    fun vurguUzeri(context: Context): Int = uzerindekiRenk(vurgu(context))

    /**
     * Zemin üzerine siyah mı beyaz mı yazılsın: WCAG kontrastı yüksek olan
     * kazanır. (Eskiden parlaklık eşiği vardı; koyu turuncu #F97316 üzerine
     * beyaz seçiliyor, kontrast 2,8:1 kalıyordu.)
     */
    fun uzerindekiRenk(renk: Int): Int {
        fun kanal(k: Int): Double {
            val c = k / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val l = 0.2126 * kanal((renk shr 16) and 0xFF) +
            0.7152 * kanal((renk shr 8) and 0xFF) + 0.0722 * kanal(renk and 0xFF)
        val beyazKontrast = 1.05 / (l + 0.05)
        val siyahKontrast = (l + 0.05) / 0.05
        return if (siyahKontrast > beyazKontrast) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** Vurgunun pastel tonu: seçili çip, ikon kartı zemini (%12). */
    fun pastel(renk: Int): Int = (renk and 0x00FFFFFF) or 0x1F000000
}
