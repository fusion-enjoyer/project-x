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
import kotlin.math.min

/**
 * Yakınlaştırılabilir resim: iki parmakla büyüt, çift dokunuşla 1×/2,5×,
 * büyükken sürükle. Büyütülmemişken yatay sürükleme ViewPager2'ye kalır
 * (sayfa geçişi); tek dokunuş çubukları açıp kapatır ([dokunus]).
 */
class ZoomGorunumu @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : AppCompatImageView(c, a) {

    var dokunus: (() -> Unit)? = null

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
            // Büyütülmüş resim kenarına dayandıysa yatay sürükleme sonraki sayfaya geçsin.
            val r = RectF(0f, 0f, bmpG.toFloat(), bmpY.toFloat())
            matris.mapRect(r)
            val sagda = r.right <= width + 1f
            val solda = r.left >= -1f
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && ((dx > 0 && sagda) || (dx < 0 && solda))) {
                birak = true
                return false
            }
            matris.postTranslate(-dx, -dy)
            sinirla()
            return true
        }
    })

    /** Bu dokunuşta sayfa kaydırması ViewPager2'ye bırakıldı mı. */
    private var birak = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) birak = false
        olcekAlgilayici.onTouchEvent(e)
        if (!olcekAlgilayici.isInProgress) hareketAlgilayici.onTouchEvent(e)
        // Büyütülmüşken üstteki görünümler dokunuşu kapmasın.
        parent?.requestDisallowInterceptTouchEvent((buyutulmus && !birak) || e.pointerCount > 1)
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
