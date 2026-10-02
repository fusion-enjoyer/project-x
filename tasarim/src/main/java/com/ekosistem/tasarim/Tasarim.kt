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

    fun uzerindekiRenk(renk: Int): Int {
        val r = (renk shr 16) and 0xFF
        val g = (renk shr 8) and 0xFF
        val b = renk and 0xFF
        val parlaklik = (r * 299 + g * 587 + b * 114) / 1000
        return if (parlaklik > 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** Vurgunun pastel tonu: seçili çip, ikon kartı zemini (%12). */
    fun pastel(renk: Int): Int = (renk and 0x00FFFFFF) or 0x1F000000
}
