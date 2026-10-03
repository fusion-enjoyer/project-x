package com.ekosistem.saat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/**
 * Listenin sağ kenarındaki harf dizini: parmağı harflerin üstünde gezdirince
 * liste o harfe atlar ([harfSecildi]). Yalnız listede olan harfler çizilir.
 */
class HarfDizini @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var harfler: List<String> = emptyList()
        set(deger) {
            field = deger
            seciliHarf = null
            invalidate()
        }
    var harfSecildi: ((String) -> Unit)? = null
    var birakildi: (() -> Unit)? = null

    private var seciliHarf: String? = null
    private val d = resources.displayMetrics.density
    private val boya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.configuration.fontScale.coerceAtMost(1.2f) * d
        isFakeBoldText = true
    }
    private val normal = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val vurgu = Tasarim.vurgu(context)

    override fun onDraw(canvas: Canvas) {
        if (harfler.isEmpty()) return
        val adim = height.toFloat() / harfler.size
        // Çok harf sığmazsa seyrek çiz (her ikincisi); dokunma yine tümünü bulur.
        val atla = if (adim < boya.textSize * 1.15f) 2 else 1
        for ((i, h) in harfler.withIndex()) {
            if (i % atla != 0 && h != seciliHarf) continue
            boya.color = if (h == seciliHarf) vurgu else normal
            val y = adim * i + adim / 2 - (boya.descent() + boya.ascent()) / 2
            canvas.drawText(h, width / 2f, y, boya)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (harfler.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val sira = (e.y / height * harfler.size).toInt().coerceIn(0, harfler.lastIndex)
                val h = harfler[sira]
                if (h != seciliHarf) {
                    seciliHarf = h
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    harfSecildi?.invoke(h)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                seciliHarf = null
                birakildi?.invoke()
                invalidate()
            }
        }
        return true
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(resolveSize((28 * d).toInt(), w), resolveSize(0, h))
    }
}
