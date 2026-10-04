package com.ekosistem.takvim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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

/**
 * Yıl görünümü: on iki küçük ay. Bugün vurgu dairesinde; yoğunluk haritası
 * açıksa her gün o gündeki etkinlik sayısına göre koyulaşır (Apple Takvim'in
 * Mac'teki yıl görünümü gibi). Bir aya dokununca o ay açılır.
 */
class YilIzgarasi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var yil = 2026
        set(v) { field = v; invalidate(); erisim.invalidateRoot() }
    var bugun = 0
        set(v) { field = v; invalidate() }
    var haftaBasi = 0
        set(v) { field = v; invalidate() }
    var isiHaritasi = true
        set(v) { field = v; invalidate() }
    /** Gün → o gündeki etkinlik sayısı. */
    var sayilar: Map<Int, Int> = emptyMap()
        set(v) { field = v; enCok = maxOf(4, v.values.maxOrNull() ?: 0); invalidate(); erisim.invalidateRoot() }
    var aySecildi: ((ay: Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)

    private val metin = ContextCompat.getColor(context, TR.color.metin)
    private val soluk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val vurgu = Tasarim.vurgu(context)
    private val vurguUzeri = Tasarim.vurguUzeri(context)

    private val ayYazi = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val gunYazi = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)
    private val alan = RectF()

    private val tikSiniri = ViewConfiguration.get(context).scaledTouchSlop
    private var basX = 0f
    private var basY = 0f
    private var cokParmak = false

    init {
        isClickable = true
        isFocusable = true
    }

    /** Geniş ekranda (yatay, tablet) dört sütun, telefonda üç. */
    private fun sutunSayisi() = if (width / d >= 560f) 4 else 3

    private val araBosluk get() = 14f * d
    private fun ayGenisligi() = (width - araBosluk * (sutunSayisi() - 1)) / sutunSayisi()
    private fun hucre() = ayGenisligi() / 7f
    private val baslikYuksekligi get() = 24f * d * olcek
    private fun ayYuksekligi() = baslikYuksekligi + hucre() * 6 + 8f * d

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val g = MeasureSpec.getSize(widthMeasureSpec)
        val sutun = if (g / d >= 560f) 4 else 3
        val ayG = (g - 14f * d * (sutun - 1)) / sutun
        val ayY = 24f * d * olcek + ayG / 7f * 6 + 8f * d
        val satir = (12 + sutun - 1) / sutun
        setMeasuredDimension(g, (ayY * satir + 12f * d * (satir - 1)).toInt())
    }

    private fun ayAlani(ay: Int, r: RectF) {
        val s = sutunSayisi()
        val sutun = (ay - 1) % s
        val satir = (ay - 1) / s
        val x = sutun * (ayGenisligi() + araBosluk)
        val y = satir * (ayYuksekligi() + 12f * d)
        r.set(x, y, x + ayGenisligi(), y + ayYuksekligi())
    }

    /**
     * Sayıya göre zemin saydamlığı, yılın en yoğun gününe göre dört basamak. Sabit
     * eşik (ör. 5+ = en koyu) her gün dolu bir takvimde bütün yılı aynı renge
     * boyuyordu; en az 4 sayılır ki seyrek takvimde tek etkinlik en koyu görünmesin.
     * Gecede açık vurgu üstünde açık yazı okunur kalsın diye en koyu basamak düşük
     * (en koyu hücrede bile yazı ≥ 4.5:1).
     */
    private fun isiSaydamligi(sayi: Int): Int {
        if (sayi <= 0) return 0
        val basamak = ((sayi * 4 + enCok - 1) / enCok).coerceIn(1, 4)
        val a = intArrayOf(0x2E, 0x55, 0x80, 0xAA)[basamak - 1]
        return if (ZamanOlcusu.gece(context)) a * 3 / 4 else a
    }

    private var enCok = 4

    override fun onDraw(canvas: Canvas) {
        val buAy = if (Gun.yil(bugun) == yil) Gun.ay(bugun) else 0
        val h = hucre()
        ayYazi.textSize = 15f * d * olcek
        gunYazi.textSize = minOf(10f * d * olcek, h * 0.62f)
        val r = RectF()
        for (ay in 1..12) {
            ayAlani(ay, r)
            ayYazi.color = if (ay == buAy) vurgu else metin
            canvas.drawText(Metinler.ayAdi(Gun.gun(yil, ay, 1)), r.left + 2f * d, r.top + baslikYuksekligi - 8f * d, ayYazi)

            val ilk = Gun.gun(yil, ay, 1)
            val ofset = Math.floorMod(Gun.haftaGunu(ilk) - haftaBasi, 7)
            val gunSayisi = Gun.ayinGunSayisi(yil, ay)
            for (n in 0 until gunSayisi) {
                val gun = ilk + n
                val k = ofset + n
                val cx = r.left + (k % 7) * h + h / 2f
                val cy = r.top + baslikYuksekligi + (k / 7) * h + h / 2f
                val bug = gun == bugun
                if (bug) {
                    dolgu.color = vurgu
                    canvas.drawCircle(cx, cy, h * 0.46f, dolgu)
                } else if (isiHaritasi) {
                    val a = isiSaydamligi(sayilar[gun] ?: 0)
                    if (a > 0) {
                        dolgu.color = (vurgu and 0x00FFFFFF) or (a shl 24)
                        alan.set(cx - h * 0.46f, cy - h * 0.46f, cx + h * 0.46f, cy + h * 0.46f)
                        canvas.drawRoundRect(alan, h * 0.2f, h * 0.2f, dolgu)
                    }
                }
                gunYazi.color = if (bug) vurguUzeri else metin
                gunYazi.typeface = if (bug) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(Gun.ayinGunu(gun).toString(), cx, cy - (gunYazi.ascent() + gunYazi.descent()) / 2f, gunYazi)
            }
        }
    }

    private fun ayAt(x: Float, y: Float): Int {
        val r = RectF()
        for (ay in 1..12) {
            ayAlani(ay, r)
            if (r.contains(x, y)) return ay
        }
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                basX = e.x; basY = e.y
                cokParmak = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> cokParmak = true
            MotionEvent.ACTION_UP -> {
                if (!cokParmak && abs(e.x - basX) < tikSiniri && abs(e.y - basY) < tikSiniri) {
                    val ay = ayAt(e.x, e.y)
                    if (ay > 0) {
                        performClick()
                        aySecildi?.invoke(ay)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    // ---- Ekran okuyucu: her ay bir düğüm ----

    private val erisim = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = ayAt(x, y).let { if (it < 0) INVALID_ID else it }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            for (ay in 1..12) ids.add(ay)
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val ilk = Gun.gun(yil, id, 1)
            val son = ilk + Gun.ayinGunSayisi(yil, id) - 1
            val toplam = (ilk..son).sumOf { sayilar[it] ?: 0 }
            val s = StringBuilder(Metinler.ayYil(ilk))
            s.append(", ").append(if (toplam == 0) context.getString(R.string.etkinlik_yok) else context.getString(R.string.etkinlik_n, toplam))
            node.contentDescription = s
            val r = RectF()
            ayAlani(id, r)
            val b = Rect()
            r.roundOut(b)
            node.setBoundsInParent(b)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: android.os.Bundle?): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                aySecildi?.invoke(id)
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
