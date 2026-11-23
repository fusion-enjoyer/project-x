package com.ekosistem.galeri

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Yakınlaştırılabilir resim: iki parmakla büyüt, çift dokunuşla 1×/2,5×,
 * büyükken sürükle. Büyütülmemişken yatay fırlatma bir sonraki/önceki
 * resme geçer ([gecis]); tek dokunuş çubukları açıp kapatır ([dokunus]).
 */
class ZoomGorunumu @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : AppCompatImageView(c, a) {

    var dokunus: (() -> Unit)? = null
    /** +1 sonraki, -1 önceki. */
    var gecis: ((Int) -> Unit)? = null

    private val matris = Matrix()
    private var temelOlcek = 1f
    private var olcek = 1f
    private var bmpG = 0
    private var bmpY = 0
    private val d = resources.displayMetrics.density
    private var animasyon: ValueAnimator? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    val buyutulmus get() = olcek > temelOlcek * 1.02f

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        bmpG = bm?.width ?: 0
        bmpY = bm?.height ?: 0
        sigdir()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sigdir()
    }

    /** Ekrana ortalı sığdır. */
    fun sigdir() {
        animasyon?.cancel()
        if (bmpG == 0 || width == 0) return
        temelOlcek = min(width.toFloat() / bmpG, height.toFloat() / bmpY)
        olcek = temelOlcek
        matris.reset()
        matris.postScale(olcek, olcek)
        matris.postTranslate((width - bmpG * olcek) / 2f, (height - bmpY * olcek) / 2f)
        imageMatrix = matris
    }

    private fun sinirla() {
        val r = RectF(0f, 0f, bmpG.toFloat(), bmpY.toFloat())
        matris.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        matris.postTranslate(dx, dy)
        imageMatrix = matris
    }

    private fun olcekle(kat: Float, px: Float, py: Float) {
        val hedef = (olcek * kat).coerceIn(temelOlcek, temelOlcek * 8f)
        val k = hedef / olcek
        olcek = hedef
        matris.postScale(k, k, px, py)
        sinirla()
    }

    private fun animasyonlaOlcekle(hedef: Float, px: Float, py: Float) {
        animasyon?.cancel()
        animasyon = ValueAnimator.ofFloat(olcek, hedef).apply {
            duration = 200
            addUpdateListener { olcekle((it.animatedValue as Float) / olcek, px, py) }
            start()
        }
    }

    private val olcekAlgilayici = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(g: ScaleGestureDetector): Boolean {
            olcekle(g.scaleFactor, g.focusX, g.focusY)
            return true
        }
    })

    private val hareketAlgilayici = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            dokunus?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (buyutulmus) animasyonlaOlcekle(temelOlcek, e.x, e.y)
            else animasyonlaOlcekle(temelOlcek * 2.5f, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!buyutulmus) return false
            matris.postTranslate(-dx, -dy)
            sinirla()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (buyutulmus || e1 == null) return false
            val ddx = e2.x - e1.x
            val ddy = e2.y - e1.y
            if (abs(ddx) > 48 * d && abs(ddx) > abs(ddy) * 1.5f && abs(vx) > 400 * d) {
                gecis?.invoke(if (ddx < 0) +1 else -1)
                return true
            }
            return false
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        olcekAlgilayici.onTouchEvent(e)
        if (!olcekAlgilayici.isInProgress) hareketAlgilayici.onTouchEvent(e)
        // Büyütülmüşken üstteki görünümler dokunuşu kapmasın.
        parent?.requestDisallowInterceptTouchEvent(buyutulmus || e.pointerCount > 1)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Kaydırma sırasında az kalan kaydırma payı (test ve erişilebilirlik için). */
    fun olcekOrani(): Float = if (temelOlcek == 0f) 1f else max(1f, olcek / temelOlcek)
}
