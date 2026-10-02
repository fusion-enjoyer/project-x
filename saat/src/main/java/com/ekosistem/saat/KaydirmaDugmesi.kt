package com.ekosistem.saat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 * "Kapatmak için kaydır": yuvarlak topuz sağa sürüklenince [kaydirildi] çağrılır.
 * Dokunup geçmek kapatmaz; uyku sersemliğiyle yanlışlıkla kapanmasın diye.
 * Ekran okuyucuyla çift dokunma (performClick) kapatır.
 */
class KaydirmaDugmesi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var kaydirildi: (() -> Unit)? = null
    var ipucu: String = ""
        set(deger) {
            field = deger
            contentDescription = deger
            invalidate()
        }
    var vurgu: Int = 0xFFF97316.toInt()
        set(deger) {
            field = deger
            topuzBoya.color = deger
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val rayBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1B18.toInt() }
    private val topuzBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vurgu }
    private val okBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val yaziBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B867D.toInt()
        textSize = 16f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val ray = RectF()
    private val ok = Path()

    private var kayma = 0f
    private var tutuyor = false
    private var dokunmaX = 0f
    private var geriDonus: ValueAnimator? = null

    private val bosluk get() = 6f * d
    private val topuzCapi get() = height - 2 * bosluk
    private val enFazla get() = (width - topuzCapi - 2 * bosluk).coerceAtLeast(1f)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val yukseklik = (72 * d).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(yukseklik, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        ray.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(ray, h / 2, h / 2, rayBoya)
        val oran = kayma / enFazla
        yaziBoya.alpha = ((1f - oran * 1.6f).coerceIn(0f, 1f) * 255).toInt()
        val y = h / 2 - (yaziBoya.descent() + yaziBoya.ascent()) / 2
        canvas.drawText(ipucu, width / 2f + topuzCapi / 4, y, yaziBoya)

        val r = topuzCapi / 2
        val cx = bosluk + r + kayma
        val cy = h / 2
        canvas.drawCircle(cx, cy, r, topuzBoya)
        val s = r * 0.42f
        ok.reset()
        ok.moveTo(cx - s, cy)
        ok.lineTo(cx + s, cy)
        ok.moveTo(cx + s * 0.1f, cy - s * 0.85f)
        ok.lineTo(cx + s, cy)
        ok.lineTo(cx + s * 0.1f, cy + s * 0.85f)
        canvas.drawPath(ok, okBoya)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = topuzCapi / 2
                val cx = bosluk + r + kayma
                // Topuzun biraz dışından da tutulabilsin.
                if (e.x > cx + r * 1.6f) return false
                geriDonus?.cancel()
                tutuyor = true
                dokunmaX = e.x - kayma
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (tutuyor) {
                kayma = (e.x - dokunmaX).coerceIn(0f, enFazla)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (tutuyor) {
                tutuyor = false
                if (e.actionMasked == MotionEvent.ACTION_UP && kayma >= enFazla * ESIK) {
                    kayma = enFazla
                    invalidate()
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    kaydirildi?.invoke()
                } else {
                    geriDon()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun geriDon() {
        geriDonus = ValueAnimator.ofFloat(kayma, 0f).apply {
            duration = 220
            addUpdateListener {
                kayma = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        // Ekran okuyucu kullanan sürükleyemez; onun çift dokunuşu kapatır.
        if (isAccessibilityFocused) kaydirildi?.invoke()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
    }

    companion object {
        private const val ESIK = 0.82f
    }
}
