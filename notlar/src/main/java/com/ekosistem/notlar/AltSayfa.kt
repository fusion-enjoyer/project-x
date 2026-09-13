package com.ekosistem.notlar

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Ekranın altından açılan menü. Açılır menüler ekranın üstünde kalıyordu;
 * alt sayfa başparmakla rahat erişilir (Obsidian'ın da yaptığı gibi).
 */
class AltSayfa(private val activity: Activity) {

    private data class Madde(
        val ikon: Int,
        val baslik: String,
        val secili: Boolean,
        val tehlikeli: Boolean,
        val tikla: () -> Unit
    )

    private val maddeler = mutableListOf<Madde>()
    private var basligi: String? = null

    fun baslik(metin: String): AltSayfa {
        basligi = metin
        return this
    }

    fun madde(
        ikon: Int,
        baslik: String,
        secili: Boolean = false,
        tehlikeli: Boolean = false,
        tikla: () -> Unit
    ): AltSayfa {
        maddeler.add(Madde(ikon, baslik, secili, tehlikeli, tikla))
        return this
    }

    fun goster() {
        val y = activity.resources.displayMetrics.density
        val dialog = Dialog(activity, R.style.AltSayfaTemasi)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val kok = LinearLayout(activity)
        kok.orientation = LinearLayout.VERTICAL
        kok.setBackgroundResource(R.drawable.bg_alt_sayfa)
        kok.setPadding(0, (10 * y).toInt(), 0, (16 * y).toInt())

        // Tutamak
        val tutamak = View(activity)
        tutamak.setBackgroundResource(R.drawable.bg_tutamak)
        val tutamakLp = LinearLayout.LayoutParams((36 * y).toInt(), (4 * y).toInt())
        tutamakLp.gravity = Gravity.CENTER_HORIZONTAL
        tutamakLp.bottomMargin = (10 * y).toInt()
        kok.addView(tutamak, tutamakLp)

        basligi?.let { metin ->
            val tv = TextView(activity)
            tv.text = metin
            tv.textSize = 13f
            tv.setTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
            tv.setPadding((24 * y).toInt(), (4 * y).toInt(), (24 * y).toInt(), (8 * y).toInt())
            kok.addView(tv)
        }

        val vurgu = Renkler.vurgu(activity)
        val metinRengi = ContextCompat.getColor(activity, R.color.metin)
        val tehlikeRengi = 0xFFE24B4A.toInt()

        for (madde in maddeler) {
            val satir = LinearLayout(activity)
            satir.orientation = LinearLayout.HORIZONTAL
            satir.gravity = Gravity.CENTER_VERTICAL
            satir.setPadding((24 * y).toInt(), 0, (24 * y).toInt(), 0)
            satir.isClickable = true
            satir.setBackgroundResource(secilebilirZemin())

            val renk = when {
                madde.tehlikeli -> tehlikeRengi
                madde.secili -> vurgu
                else -> metinRengi
            }

            val ikon = ImageView(activity)
            ikon.setImageResource(madde.ikon)
            ikon.imageTintList = ColorStateList.valueOf(renk)
            satir.addView(ikon, LinearLayout.LayoutParams((24 * y).toInt(), (24 * y).toInt()))

            val tv = TextView(activity)
            tv.text = madde.baslik
            tv.textSize = 16f
            tv.setTextColor(renk)
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.leftMargin = (18 * y).toInt()
            satir.addView(tv, tvLp)

            if (madde.secili) {
                val onay = ImageView(activity)
                onay.setImageResource(R.drawable.ic_onay_isaret)
                onay.imageTintList = ColorStateList.valueOf(vurgu)
                satir.addView(
                    onay,
                    LinearLayout.LayoutParams((20 * y).toInt(), (20 * y).toInt())
                )
            }

            satir.setOnClickListener {
                dialog.dismiss()
                madde.tikla()
            }
            kok.addView(
                satir,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (56 * y).toInt()
                )
            )
        }

        // Alt sayfayı ekranın altına yasla, üstündeki boşluğa dokununca kapansın.
        val sarmal = LinearLayout(activity)
        sarmal.orientation = LinearLayout.VERTICAL
        sarmal.gravity = Gravity.BOTTOM
        sarmal.setOnClickListener { dialog.dismiss() }
        kok.isClickable = true
        sarmal.addView(
            kok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(sarmal)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
    }

    private fun secilebilirZemin(): Int {
        val deger = android.util.TypedValue()
        activity.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            deger,
            true
        )
        return deger.resourceId
    }
}
