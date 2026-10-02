package com.ekosistem.saat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.R as TR
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Saat ve dakika seçen döner tekerlek: ortada büyük seçili sayı, üstünde ve
 * altında küçülerek solan komşular. Sürükle, fırlat ya da komşuya dokun.
 * Sistem NumberPicker'ı her Android sürümünde başka görünüyor; bu her yerde aynı.
 */
class SayiTekerlegi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var adet = 60
        set(deger) {
            field = deger
            invalidate()
        }
    var deger = 0
        private set
    var degisti: ((Int) -> Unit)? = null
    var ekranOkuyucuAdi: String = ""

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val satir = 64f * d
    private val buyuk = 64f * sp
    private val kucuk = 28f * sp
    private val anaRenk = ContextCompat.getColor(context, TR.color.metin)
    private val solukRenk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val boya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = false
    }

    /** Ortadaki sayının dikey kayması (px); 0 = tam ortada. */
    private var kayma = 0f
    private var sonY = 0f
    private var ilkY = 0f
    private var surukluyor = false
    private var hiz: VelocityTracker? = null
    private val kaydirici = OverScroller(context)
    private var kaydiriciSonY = 0
    private var yerlesme: ValueAnimator? = null

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun ayarla(yeni: Int) {
        deger = ((yeni % adet) + adet) % adet
        kayma = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        boya.textSize = buyuk
        val genislik = (boya.measureText("00") + 24 * d).toInt()
        setMeasuredDimension(
            resolveSize(genislik, widthMeasureSpec),
            resolveSize((satir * 3).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val orta = height / 2f
        for (i in -2..2) {
            val y = orta + i * satir + kayma
            val uzaklik = (abs(y - orta) / satir).coerceIn(0f, 1f)
            boya.textSize = buyuk + (kucuk - buyuk) * uzaklik
            boya.color = karistir(anaRenk, solukRenk, uzaklik)
            boya.alpha = (255 * (1f - (abs(y - orta) / (satir * 1.8f)).coerceIn(0f, 1f))).toInt()
            boya.isFakeBoldText = uzaklik < 0.3f
            val sayi = ((deger + i) % adet + adet) % adet
            val tabanY = y - (boya.descent() + boya.ascent()) / 2
            canvas.drawText("%02d".format(sayi), width / 2f, tabanY, boya)
        }
    }

    private fun karistir(a: Int, b: Int, t: Float): Int {
        fun k(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).roundToInt()
        return (0xFF shl 24) or (k(16) shl 16) or (k(8) shl 8) or k(0)
    }

    /** Kaymayı uygular; yarım satırı geçince sayı değişir. */
    private fun kaydir(dy: Float) {
        kayma += dy
        var degisti = false
        while (kayma > satir / 2) {
            kayma -= satir
            deger = (deger - 1 + adet) % adet
            degisti = true
        }
        while (kayma < -satir / 2) {
            kayma += satir
            deger = (deger + 1) % adet
            degisti = true
        }
        if (degisti) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            this.degisti?.invoke(deger)
        }
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (hiz == null) hiz = VelocityTracker.obtain()
        hiz?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                kaydirici.forceFinished(true)
                yerlesme?.cancel()
                sonY = e.y
                ilkY = e.y
                surukluyor = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!surukluyor && abs(e.y - ilkY) > 6 * d) surukluyor = true
                if (surukluyor) kaydir(e.y - sonY)
                sonY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (!surukluyor) {
                    // Üstteki ya da alttaki komşuya dokunmak bir adım çevirir.
                    val orta = height / 2f
                    when {
                        e.y < orta - satir / 2 -> adimAt(-1)
                        e.y > orta + satir / 2 -> adimAt(+1)
                        else -> performClick()
                    }
                } else {
                    hiz?.computeCurrentVelocity(1000)
                    val vy = hiz?.yVelocity ?: 0f
                    if (abs(vy) > 300 * d) {
                        kaydiriciSonY = 0
                        kaydirici.fling(0, 0, 0, vy.toInt(), 0, 0, -100_000, 100_000)
                        postInvalidateOnAnimation()
                    } else {
                        yerles()
                    }
                }
                hiz?.recycle()
                hiz = null
            }
            MotionEvent.ACTION_CANCEL -> {
                yerles()
                hiz?.recycle()
                hiz = null
            }
        }
        return true
    }

    override fun computeScroll() {
        if (kaydirici.computeScrollOffset()) {
            val y = kaydirici.currY
            kaydir((y - kaydiriciSonY).toFloat())
            kaydiriciSonY = y
            if (kaydirici.isFinished) yerles() else postInvalidateOnAnimation()
        }
    }

    /** Kesirli kaymayı en yakın sayıya oturtur. */
    private fun yerles() {
        yerlesme?.cancel()
        val bas = kayma
        if (bas == 0f) return
        var onceki = bas
        yerlesme = ValueAnimator.ofFloat(bas, 0f).apply {
            duration = 160
            addUpdateListener {
                val v = it.animatedValue as Float
                kaydir(v - onceki)
                onceki = v
            }
            start()
        }
    }

    private fun adimAt(yon: Int) {
        deger = ((deger + yon) % adet + adet) % adet
        kayma = 0f
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        degisti?.invoke(deger)
        invalidate()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

    override fun performClick(): Boolean = super.performClick()

    // Ekran okuyucu: "Saat, 06", sesle yukarı/aşağı kaydırarak değiştirilir.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.NumberPicker::class.java.name
        info.contentDescription = "$ekranOkuyucuAdi, ${"%02d".format(deger)}"
        info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> adimAt(+1)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> adimAt(-1)
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return true
    }
}
