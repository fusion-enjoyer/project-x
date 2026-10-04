package com.ekosistem.takvim

import android.app.Activity
import android.content.res.ColorStateList
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/** Etkinlik ve takvim rengi için ortak palet ve alt sayfadaki renk daireleri. */
object RenkSecici {

    /** Tasarım dilinin vurguları + yaygın tonlar. */
    val PALET = intArrayOf(
        0xFF0F766E.toInt(), 0xFF2563EB.toInt(), 0xFF4F46E5.toInt(), 0xFF9333EA.toInt(), 0xFFDB2777.toInt(),
        0xFFDC2626.toInt(), 0xFFEA580C.toInt(), 0xFFCA8A04.toInt(), 0xFF16A34A.toInt(), 0xFF52525B.toInt()
    )

    /**
     * Başlığı [baslik] olan, renk daireleri dizili bir alt sayfa kurar (gösterilmemiş;
     * çağıran madde ekleyip `goster()` der). Daireye dokununca sayfa kapanır, [sec] çağrılır.
     */
    fun sayfa(activity: Activity, baslik: String, secili: Int, sec: (Int) -> Unit): AltSayfa {
        val d = activity.resources.displayMetrics.density
        val sira = LinearLayout(activity)
        sira.orientation = LinearLayout.HORIZONTAL
        val sayfa = AltSayfa(activity).baslik(baslik)
        for ((i, renk) in PALET.withIndex()) {
            val daire = ImageView(activity)
            daire.setBackgroundResource(R.drawable.bg_nokta)
            daire.backgroundTintList = ColorStateList.valueOf(renk)
            daire.scaleType = ImageView.ScaleType.CENTER
            if (renk == secili) {
                daire.setImageResource(TR.drawable.ic_onay_isaret)
                daire.imageTintList = ColorStateList.valueOf(Tasarim.uzerindekiRenk(renk))
            }
            daire.contentDescription = activity.getString(R.string.renk) + " " + (i + 1)
            daire.setOnClickListener { sayfa.kapat(); sec(renk) }
            sira.addView(daire, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply { rightMargin = (8 * d).toInt() })
        }
        val kaydirici = HorizontalScrollView(activity)
        kaydirici.isHorizontalScrollBarEnabled = false
        kaydirici.addView(sira)
        return sayfa.icerik(kaydirici)
    }
}
