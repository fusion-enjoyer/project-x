package com.ekosistem.saat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Kaydırılabilir liste satırı: satırı sola çekince [sola] (silme gibi), sağa
 * çekince [saga] (klasöre taşıma gibi) eylemi arkadan renkli bir şeritle belirir;
 * yeterince çekilirse ya da hızlı savrulursa eylem çalışır, yoksa satır yerine döner.
 * Uzun basma seçenekleri satırın kendi dinleyicisindedir; ikisi birlikte çalışır.
 */
class KaydirmaSatiri(context: Context) : FrameLayout(context) {

    class Eylem(val renk: Int, val ikon: Int, val ad: String, val calistir: () -> Unit)

    var sola: Eylem? = null
    var saga: Eylem? = null

    private val d = resources.displayMetrics.density
    private val arka = FrameLayout(context)
    private val arkaIkon = ImageView(context)
    private val zemin = GradientDrawable().apply { cornerRadius = 20 * d }
    private lateinit var on: View

    private val esik = ViewConfiguration.get(context).scaledTouchSlop
    private var ilkX = 0f
    private var ilkY = 0f
    private var surukluyor = false
    private var hiz: VelocityTracker? = null
    private var animasyon: ValueAnimator? = null

    init {
        arka.background = zemin
        arka.visibility = View.GONE
        arkaIkon.setColorFilter(0xFFFFFFFF.toInt())
        arkaIkon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        arka.addView(arkaIkon, LayoutParams((24 * d).toInt(), (24 * d).toInt(), Gravity.CENTER_VERTICAL))
        addView(arka, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Satırın asıl içeriğini koyar; arka şerit onun altında kalır. */
    fun icerik(gorunum: View) {
        on = gorunum
        addView(gorunum, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // Şerit içeriğin yüksekliğini alsın.
        arka.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        // Arka şerit yalnız içeriğin yüksekliğinde (FrameLayout wrap_content içeriği izler).
        arka.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(on.measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    private fun yonEylemi(dx: Float): Eylem? = if (dx < 0) sola else saga

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ilkX = e.x; ilkY = e.y; surukluyor = false
                animasyon?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - ilkX
                val dy = e.y - ilkY
                if (!surukluyor && abs(dx) > esik && abs(dx) > 1.5f * abs(dy) && yonEylemi(dx) != null) {
                    surukluyor = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    hiz = VelocityTracker.obtain()
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!surukluyor) return super.onTouchEvent(e)
        hiz?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                var dx = e.x - ilkX
                if (yonEylemi(dx) == null) dx = 0f
                // Çok çekilince direnç: satır ekranın dışına kadar gitmez.
                val sinir = width * 0.55f
                if (abs(dx) > sinir) dx = (if (dx < 0) -1 else 1) * (sinir + (abs(dx) - sinir) * 0.25f)
                konumla(dx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hiz?.computeCurrentVelocity(1000)
                val vx = hiz?.xVelocity ?: 0f
                hiz?.recycle(); hiz = null
                bitir(on.translationX, vx, e.actionMasked == MotionEvent.ACTION_CANCEL)
            }
        }
        return true
    }

    private fun konumla(dx: Float) {
        on.translationX = dx
        val eylem = yonEylemi(dx)
        if (dx == 0f || eylem == null) {
            arka.visibility = View.GONE
            return
        }
        arka.visibility = View.VISIBLE
        zemin.setColor(eylem.renk)
        arkaIkon.setImageResource(eylem.ikon)
        arkaIkon.contentDescription = eylem.ad
        val lp = arkaIkon.layoutParams as LayoutParams
        lp.gravity = Gravity.CENTER_VERTICAL or (if (dx < 0) Gravity.END else Gravity.START)
        lp.marginStart = (22 * d).toInt()
        lp.marginEnd = (22 * d).toInt()
        arkaIkon.layoutParams = lp
        // Eşiğe yaklaştıkça şerit belirginleşir.
        arka.alpha = (abs(dx) / (width * 0.33f)).coerceIn(0.35f, 1f)
        arkaIkon.scaleX = if (abs(dx) > width * 0.33f) 1.2f else 1f
        arkaIkon.scaleY = arkaIkon.scaleX
    }

    private fun bitir(dx: Float, vx: Float, iptal: Boolean) {
        surukluyor = false
        val yon = if (dx < 0) -1 else 1
        val eylem = yonEylemi(dx)
        val hizliSavruldu = abs(vx) > 1200 * d && (vx < 0) == (dx < 0)
        val yeterli = abs(dx) > width * 0.33f
        if (!iptal && eylem != null && (yeterli || hizliSavruldu)) {
            // Silme gibi eylemlerde satır ekran dışına kayar; taşıma gibilerde geri döner.
            val bitis = if (eylem === sola) yon * width * 1.05f else 0f
            git(bitis) { eylem.calistir(); if (bitis != 0f) { on.translationX = 0f; konumla(0f) } }
        } else {
            git(0f) { }
        }
    }

    private fun git(hedef: Float, sonunda: () -> Unit) {
        animasyon?.cancel()
        animasyon = ValueAnimator.ofFloat(on.translationX, hedef).apply {
            duration = 180
            addUpdateListener { konumla(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (hedef == 0f) konumla(0f)
                    sonunda()
                }
            })
            start()
        }
    }

    companion object {
        fun silme(context: Context, ad: String, calistir: () -> Unit) = Eylem(
            ContextCompat.getColor(context, com.ekosistem.tasarim.R.color.tehlike), R.drawable.ic_sil, ad, calistir
        )

        fun klasor(context: Context, ad: String, calistir: () -> Unit) = Eylem(
            com.ekosistem.tasarim.Tasarim.vurgu(context), R.drawable.ic_klasor, ad, calistir
        )
    }
}
