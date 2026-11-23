package com.ekosistem.galeri

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Görüntüleyicinin aşağı çekip kapatma hareketi: resim büyütülmemişken aşağı
 * sürüklenince içerik parmağı izler, arka plan solar; yeterince çekilince ya da
 * hızlı fırlatılınca kapanır, yoksa yerine yaylanır. Yatay sürükleme ViewPager2'de kalır.
 */
class KapatmaCercevesi @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : FrameLayout(c, a) {

    /** Şu an aşağı çekilebilir mi (resim büyütülmemiş, video oynamıyor). */
    var izinVar: () -> Boolean = { true }
    /** Sürüklenen görünüm (sayfalar). */
    var hedef: View? = null
    /** 0..1: ne kadar çekildi; arka plan ve çubuklar buna göre solar. */
    var ilerleme: ((Float) -> Unit)? = null
    var kapat: (() -> Unit)? = null

    private val esik = ViewConfiguration.get(c).scaledTouchSlop
    private val d = resources.displayMetrics.density
    private var ilkX = 0f
    private var ilkY = 0f
    private var cekiyor = false
    private var hiz: VelocityTracker? = null

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ilkX = e.rawX
                ilkY = e.rawY
                cekiyor = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount > 1 || !izinVar()) return false
                val dx = e.rawX - ilkX
                val dy = e.rawY - ilkY
                if (dy > esik && dy > abs(dx) * 1.5f) {
                    cekiyor = true
                    ilkY = e.rawY
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!cekiyor) return super.onTouchEvent(e)
        if (hiz == null) hiz = VelocityTracker.obtain()
        hiz?.addMovement(e)
        val h = hedef ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = (e.rawY - ilkY).coerceAtLeast(0f)
                h.translationY = dy
                val oran = (dy / (height * 0.5f)).coerceIn(0f, 1f)
                h.scaleX = 1f - 0.15f * oran
                h.scaleY = 1f - 0.15f * oran
                ilerleme?.invoke(oran)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hiz?.computeCurrentVelocity(1000)
                val vy = hiz?.yVelocity ?: 0f
                hiz?.recycle()
                hiz = null
                cekiyor = false
                val yeterli = h.translationY > height * 0.18f || vy > 1200 * d
                if (e.actionMasked == MotionEvent.ACTION_UP && yeterli) {
                    h.animate().translationY(height.toFloat()).alpha(0f).setDuration(160)
                        .withEndAction { kapat?.invoke() }.start()
                    ilerleme?.invoke(1f)
                } else {
                    h.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(200)
                        .setUpdateListener { ilerleme?.invoke((h.translationY / (height * 0.5f)).coerceIn(0f, 1f)) }
                        .withEndAction { h.animate().setUpdateListener(null) }
                        .start()
                }
            }
        }
        return true
    }
}
