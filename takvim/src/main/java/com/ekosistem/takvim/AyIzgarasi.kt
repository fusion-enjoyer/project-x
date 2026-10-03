package com.ekosistem.takvim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR
import kotlin.math.abs
import kotlin.math.min

/** Ay ızgarasındaki bir günün özeti: etkinlik noktalarının renkleri ve sayısı. */
class GunOzeti(val renkler: IntArray, val sayi: Int)

/**
 * Ay görünümünün ızgarası. Her gün bir sayı; bugün halka, seçili gün dolu daire,
 * altında en çok üç etkinlik noktası. Tek View olarak çizilir (42 ayrı görünüm
 * eski telefonu yorardı); ekran okuyucu için sanal görünümler sunar.
 */
class AyIzgarasi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ilkGun = 0
        set(v) { field = v; requestLayout(); invalidate(); erisim.invalidateRoot() }
    var satirSayisi = 6
        set(v) { field = v; requestLayout(); invalidate(); erisim.invalidateRoot() }
    /** Gösterilen ay (1..12); dışındaki günler soluk çizilir. */
    var ay = 1
        set(v) { field = v; invalidate() }
    var secili = 0
        set(v) { field = v; invalidate(); erisim.invalidateRoot() }
    var bugun = 0
        set(v) { field = v; invalidate() }
    var haftaNumaralari = false
        set(v) { field = v; requestLayout(); invalidate() }
    var haftaBasi = 0
    var gunler: Map<Int, GunOzeti> = emptyMap()
        set(v) { field = v; invalidate(); erisim.invalidateRoot() }
    var gunSecildi: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    // Yazı ölçeği büyüdükçe daire büyür ama sınırda durur: 7 sütun 360 dp'ye sığmalı.
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val daireYaricap get() = 17f * d * olcek
    private val satirYuksekligi get() = daireYaricap * 2 + 20f * d
    private val kenar get() = if (haftaNumaralari) 30f * d else 0f

    private val metin = ContextCompat.getColor(context, TR.color.metin)
    private val soluk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val vurgu = Tasarim.vurgu(context)
    private val vurguUzeri = Tasarim.vurguUzeri(context)

    private val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * d * olcek
    }
    private val kucukYazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * d * olcek
        color = soluk
    }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halka = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
        color = vurgu
    }

    private val tikSiniri = ViewConfiguration.get(context).scaledTouchSlop
    private var basX = 0f
    private var basY = 0f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val g = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(g, (satirYuksekligi * satirSayisi).toInt())
    }

    private fun sutunGenisligi() = (width - kenar) / 7f

    private fun merkezX(sutun: Int) = kenar + sutun * sutunGenisligi() + sutunGenisligi() / 2f

    private fun merkezY(satir: Int) = satir * satirYuksekligi + 6f * d + daireYaricap

    override fun onDraw(canvas: Canvas) {
        val toplam = satirSayisi * 7
        for (i in 0 until toplam) {
            val gun = ilkGun + i
            val cx = merkezX(i % 7)
            val cy = merkezY(i / 7)
            val buAy = Gun.ay(gun) == ay
            val sec = gun == secili
            val bug = gun == bugun

            if (sec) {
                dolgu.color = vurgu
                canvas.drawCircle(cx, cy, daireYaricap, dolgu)
            } else if (bug) {
                canvas.drawCircle(cx, cy, daireYaricap - halka.strokeWidth / 2, halka)
            }
            yazi.color = when {
                sec -> vurguUzeri
                bug -> vurgu
                buAy -> metin
                else -> soluk
            }
            yazi.typeface = if (bug || sec) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val taban = cy - (yazi.ascent() + yazi.descent()) / 2f
            canvas.drawText(Gun.ayinGunu(gun).toString(), cx, taban, yazi)

            gunler[gun]?.let { ozet ->
                val adet = min(3, ozet.renkler.size)
                val aralik = 7f * d
                var x = cx - (adet - 1) * aralik / 2f
                val y = cy + daireYaricap + 6f * d
                for (k in 0 until adet) {
                    dolgu.color = ozet.renkler[k]
                    canvas.drawCircle(x, y, 2.6f * d, dolgu)
                    x += aralik
                }
            }
        }
        if (haftaNumaralari) {
            for (r in 0 until satirSayisi) {
                val pazartesi = ilkGun + r * 7 + Math.floorMod(-haftaBasi, 7)
                val cy = merkezY(r)
                canvas.drawText(Gun.isoHafta(pazartesi).toString(), kenar / 2f, cy - (kucukYazi.ascent() + kucukYazi.descent()) / 2f, kucukYazi)
            }
        }
    }

    /** (x, y) noktasındaki gün sırası ya da -1. */
    private fun hucre(x: Float, y: Float): Int {
        if (x < kenar || x >= width || y < 0 || y >= satirYuksekligi * satirSayisi) return -1
        val sutun = ((x - kenar) / sutunGenisligi()).toInt().coerceIn(0, 6)
        val satir = (y / satirYuksekligi).toInt().coerceIn(0, satirSayisi - 1)
        return satir * 7 + sutun
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                basX = e.x; basY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - basX) < tikSiniri && abs(e.y - basY) < tikSiniri) {
                    val h = hucre(e.x, e.y)
                    if (h >= 0) {
                        performClick()
                        gunSecildi?.invoke(ilkGun + h)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    // ---- Ekran okuyucu: her gün ayrı bir düğüm ----

    private val erisim = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val h = hucre(x, y)
            return if (h < 0 || h >= satirSayisi * 7) INVALID_ID else h
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            for (i in 0 until satirSayisi * 7) ids.add(i)
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val gun = ilkGun + id
            val sayi = gunler[gun]?.sayi ?: 0
            val s = StringBuilder(Metinler.gunBaslik(gun))
            if (sayi > 0) s.append(", ").append(context.getString(R.string.etkinlik_n, sayi))
            if (gun == bugun) s.append(", ").append(context.getString(R.string.bugun))
            node.contentDescription = s
            val r = Rect()
            val sutun = id % 7
            val satir = id / 7
            r.left = (kenar + sutun * sutunGenisligi()).toInt()
            r.right = (kenar + (sutun + 1) * sutunGenisligi()).toInt()
            r.top = (satir * satirYuksekligi).toInt()
            r.bottom = ((satir + 1) * satirYuksekligi).toInt()
            node.setBoundsInParent(r)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isSelected = gun == secili
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: android.os.Bundle?): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                gunSecildi?.invoke(ilkGun + id)
                return true
            }
            return false
        }
    }

    init {
        ViewCompat.setAccessibilityDelegate(this, erisim)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        erisim.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
}
