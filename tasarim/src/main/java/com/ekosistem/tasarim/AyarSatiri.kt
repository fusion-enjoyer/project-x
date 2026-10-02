package com.ekosistem.tasarim

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** Ayarlar ekranının satırı (res/layout/item_ayar.xml): ikon kartı, başlık, özet. */
object AyarSatiri {

    fun kur(satir: View, ikon: Int, rozetRengi: Int, baslik: String, ozet: String?) {
        val ikonGorunum = satir.findViewById<ImageView>(R.id.ayarIkon)
        ikonGorunum.setImageResource(ikon)
        ikonGorunum.backgroundTintList = ColorStateList.valueOf(rozetRengi)
        satir.findViewById<TextView>(R.id.ayarBaslik).text = baslik
        ozet(satir, ozet)
    }

    fun ozet(satir: View, ozet: String?) {
        val ozetGorunum = satir.findViewById<TextView>(R.id.ayarOzet)
        if (ozet.isNullOrBlank()) {
            ozetGorunum.visibility = View.GONE
        } else {
            ozetGorunum.visibility = View.VISIBLE
            ozetGorunum.text = ozet
        }
    }

    /** Ok yerine iOS oranlarında anahtar; satırın tamamına dokunmak çevirir. */
    fun anahtar(satir: View, acik: Boolean, degisti: (Boolean) -> Unit) {
        val c = satir.context
        val anahtar = satir.findViewById<SwitchCompat>(R.id.ayarAnahtar)
        satir.findViewById<View>(R.id.ayarChevron).visibility = View.GONE
        anahtar.visibility = View.VISIBLE
        anahtar.isChecked = acik
        val durumlar = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        anahtar.trackTintList = ColorStateList(
            durumlar,
            intArrayOf(Tasarim.vurgu(c), ContextCompat.getColor(c, R.color.anahtar_kapali))
        )
        anahtar.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        satir.setOnClickListener {
            anahtar.toggle()
            degisti(anahtar.isChecked)
        }
        ViewCompat.setAccessibilityDelegate(satir, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Switch::class.java.name
                info.isCheckable = true
                info.isChecked = anahtar.isChecked
            }
        })
    }

    /** Satır okunu gizler (sürüm satırı gibi dokunulmayan satırlar). */
    fun oksuz(satir: View) {
        satir.findViewById<View>(R.id.ayarChevron).visibility = View.INVISIBLE
        satir.isClickable = false
    }
}
