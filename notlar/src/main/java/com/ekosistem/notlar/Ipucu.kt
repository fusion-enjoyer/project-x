package com.ekosistem.notlar

import android.view.View
import androidx.appcompat.widget.TooltipCompat

/**
 * Yalnız ikonlu düğmelere basılı tutunca adını gösteren ipucu. Ekran okuyucu
 * etiketi (contentDescription) zaten var; bu, gören kullanıcı için. Android
 * 8'den eskilerde AppCompat kendi balonunu çizer.
 */
fun ipucuVer(vararg dugmeler: View) {
    for (d in dugmeler) TooltipCompat.setTooltipText(d, d.contentDescription)
}
