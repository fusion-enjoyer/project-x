package com.ekosistem.galeri

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Resmi ortalı gösterir, üstünde sürüklenebilir kırpma çerçevesi çizer:
 * köşe ve kenarlardan boyutla, içinden kaydır. Dışarısı karartılır,
 * içinde üçte bir çizgileri görünür. Hesap [Kirpma]'da.
 */
class KirpmaGorunumu @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {

    private val d = resources.displayMetrics.density
    private var bmp: Bitmap? = null
    private val resimKutusu = RectF()
    private var sinir = Kutu(0f, 0f, 0f, 0f)
    private var kutu = Kutu(0f, 0f, 0f, 0f)
    private var tutamak: Kirpma.Tutamak? = null
    private var tasiyor = false
    private var sonX = 0f
    private var sonY = 0f

    /** Genişlik/yükseklik; null serbest. */
    var oran: Float? = null
        set(v) {
            field = v
            kutu = Kirpma.ortalaKutu(sinir, v)
            invalidate()
        }

    var degisti: (() -> Unit)? = null

    private val karartma = Paint().apply { color = 0xA6000000.toInt() }
    private val cerceve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * d
    }
    private val izgara = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f * d
    }
    private val kose = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND
    }
    private val resimBoya = Paint(Paint.FILTER_BITMAP_FLAG)

    fun resim(b: Bitmap?) {
        bmp = b
        yerlestir()
    }

    /** Seçili bölge, resme göre 0..1. */
    fun secim(): Kutu {
        val g = resimKutusu.width().coerceAtLeast(1f)
        val y = resimKutusu.height().coerceAtLeast(1f)
        return Kutu(
            ((kutu.sol - resimKutusu.left) / g).coerceIn(0f, 1f),
            ((kutu.ust - resimKutusu.top) / y).coerceIn(0f, 1f),
            ((kutu.sag - resimKutusu.left) / g).coerceIn(0f, 1f),
            ((kutu.alt - resimKutusu.top) / y).coerceIn(0f, 1f)
        )
    }

    /** Resmin tamamı seçili mi (kırpılmamış). */
    fun tamami(): Boolean {
        val s = secim()
        return s.sol < 0.002f && s.ust < 0.002f && s.sag > 0.998f && s.alt > 0.998f
    }

    fun sifirla() {
        kutu = Kirpma.ortalaKutu(sinir, oran)
        invalidate()
        degisti?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        yerlestir()
    }

    private fun yerlestir() {
        val b = bmp
        if (b == null || width == 0) return
        val pay = 24 * d
        val ol = min((width - 2 * pay) / b.width, (height - 2 * pay) / b.height)
        val g = b.width * ol
        val y = b.height * ol
        resimKutusu.set((width - g) / 2, (height - y) / 2, (width + g) / 2, (height + y) / 2)
        sinir = Kutu(resimKutusu.left, resimKutusu.top, resimKutusu.right, resimKutusu.bottom)
        kutu = Kirpma.ortalaKutu(sinir, oran)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        canvas.drawBitmap(b, null, resimKutusu, resimBoya)
        val k = kutu
        // Dışarıyı kararttır (dört dikdörtgen; Path.op eski sürümde yok).
        canvas.drawRect(resimKutusu.left, resimKutusu.top, resimKutusu.right, k.ust, karartma)
        canvas.drawRect(resimKutusu.left, k.alt, resimKutusu.right, resimKutusu.bottom, karartma)
        canvas.drawRect(resimKutusu.left, k.ust, k.sol, k.alt, karartma)
        canvas.drawRect(k.sag, k.ust, resimKutusu.right, k.alt, karartma)
        if (tasiyor || tutamak != null) {
            for (i in 1..2) {
                val x = k.sol + k.genislik * i / 3
                val y = k.ust + k.yukseklik * i / 3
                canvas.drawLine(x, k.ust, x, k.alt, izgara)
                canvas.drawLine(k.sol, y, k.sag, y, izgara)
            }
        }
        canvas.drawRect(k.sol, k.ust, k.sag, k.alt, cerceve)
        val u = min(20 * d, min(k.genislik, k.yukseklik) / 3)
        val p = Path()
        p.moveTo(k.sol, k.ust + u); p.lineTo(k.sol, k.ust); p.lineTo(k.sol + u, k.ust)
        p.moveTo(k.sag - u, k.ust); p.lineTo(k.sag, k.ust); p.lineTo(k.sag, k.ust + u)
        p.moveTo(k.sag, k.alt - u); p.lineTo(k.sag, k.alt); p.lineTo(k.sag - u, k.alt)
        p.moveTo(k.sol + u, k.alt); p.lineTo(k.sol, k.alt); p.lineTo(k.sol, k.alt - u)
        canvas.drawPath(p, kose)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tutamak = Kirpma.tutamakBul(kutu, e.x, e.y, 24 * d)
                tasiyor = tutamak == null && e.x > kutu.sol && e.x < kutu.sag && e.y > kutu.ust && e.y < kutu.alt
                sonX = e.x
                sonY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return tutamak != null || tasiyor
            }
            MotionEvent.ACTION_MOVE -> {
                val t = tutamak
                if (t != null) kutu = Kirpma.boyutla(kutu, t, e.x, e.y, sinir, oran, 48 * d)
                else if (tasiyor) kutu = Kirpma.kaydir(kutu, e.x - sonX, e.y - sonY, sinir)
                sonX = e.x
                sonY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tutamak != null || tasiyor) degisti?.invoke()
                tutamak = null
                tasiyor = false
                invalidate()
            }
        }
        return true
    }
}
