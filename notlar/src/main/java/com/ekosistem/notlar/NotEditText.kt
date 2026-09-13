package com.ekosistem.notlar

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * İmleç satır değiştirdiğinde ve metin kaydırıldığında haber veren metin alanı.
 * Kaydırma bilgisi, üstteki ikonları aşağı inince gizleyip yukarı çıkınca geri
 * getirmek için gerekiyor (Obsidian'daki davranış).
 */
class NotEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var secimDegisti: ((Int, Int) -> Unit)? = null

    /** (yeniKonum, oncekiKonum) — dikey kaydırma konumu piksel cinsinden. */
    var kaydirildi: ((Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        secimDegisti?.invoke(selStart, selEnd)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        kaydirildi?.invoke(t, oldt)
    }
}
