package com.ekosistem.takvim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
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

/** Ayrıntılı ve yığılı ay görünümünde bir günün tek etkinliği. */
class GunEtkinligi(val renk: Int, val baslik: String)

/**
 * Ay ızgarasındaki bir günün özeti: nokta renkleri (en çok üç, farklı), etkinlik
 * sayısı ve sıralı etkinlikler (yığılı/ayrıntılı yoğunluk için).
 */
class GunOzeti(val renkler: IntArray, val sayi: Int, val etkinlikler: List<GunEtkinligi> = emptyList())

/**
 * Ay görünümünün ızgarası. Her gün bir sayı; bugün halka, seçili gün dolu daire.
 * Üç yoğunluk (Apple Takvim'deki gibi): Kompakt'ta altında en çok üç nokta,
 * Yığılı'da her etkinliğe ince renkli çubuk, Ayrıntılı'da hücrenin içinde
 * etkinlik başlıkları (ızgara ekranı doldurur). Tek View olarak çizilir (42 ayrı
 * görünüm eski telefonu yorardı); ekran okuyucu için sanal görünümler sunar.
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
    var yogunluk = Depo.AY_KOMPAKT
        set(v) { field = v; requestLayout(); invalidate(); erisim.invalidateRoot() }
    /** Ayrıntılı yoğunlukta ızgaranın dolduracağı yükseklik (px); 0 = en küçük satır. */
    var hedefYukseklik = 0
        set(v) { if (field != v) { field = v; if (yogunluk == Depo.AY_AYRINTILI) requestLayout() } }

    private val d = resources.displayMetrics.density
    // Yazı ölçeği büyüdükçe daire büyür ama sınırda durur: 7 sütun 360 dp'ye sığmalı.
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val daireYaricap get() = 17f * d * olcek
    private val ayrintili get() = yogunluk == Depo.AY_AYRINTILI
    private val kucukDaire get() = 11f * d * olcek
    private val hapYuksekligi get() = 15f * d * olcek
    private val satirYuksekligi: Float get() = when (yogunluk) {
        Depo.AY_YIGILI -> daireYaricap * 2 + 6f * d + YIGIN * 5f * d + 6f * d
        Depo.AY_AYRINTILI -> maxOf(if (satirSayisi > 0) hedefYukseklik / satirSayisi.toFloat() else 0f, 76f * d * olcek)
        else -> daireYaricap * 2 + 20f * d
    }
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
    private val hapYazi = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f * d * olcek; color = metin }
    private val cizgi = Paint().apply { color = ContextCompat.getColor(context, TR.color.ayrac); strokeWidth = 1f }
    private val alan = RectF()
    private val halka = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
        color = vurgu
    }

    private val tikSiniri = ViewConfiguration.get(context).scaledTouchSlop
    private var basX = 0f
    private var basY = 0f
    private var cokParmak = false

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

    private fun daire() = if (ayrintili) kucukDaire else daireYaricap

    private fun merkezY(satir: Int) = satir * satirYuksekligi + (if (ayrintili) 4f else 6f) * d + daire()

    override fun onDraw(canvas: Canvas) {
        val toplam = satirSayisi * 7
        val r = daire()
        yazi.textSize = (if (ayrintili) 13f else 15f) * d * olcek
        if (ayrintili) {
            // Satırları ince çizgi ayırır: hücreler doluyken göz hangi güne baktığını kaybetmesin.
            for (s in 0 until satirSayisi) canvas.drawLine(kenar, s * satirYuksekligi, width.toFloat(), s * satirYuksekligi, cizgi)
        }
        for (i in 0 until toplam) {
            val gun = ilkGun + i
            val cx = merkezX(i % 7)
            val cy = merkezY(i / 7)
            val buAy = Gun.ay(gun) == ay
            val sec = gun == secili
            val bug = gun == bugun

            if (sec) {
                dolgu.color = vurgu
                canvas.drawCircle(cx, cy, r, dolgu)
            } else if (bug) {
                canvas.drawCircle(cx, cy, r - halka.strokeWidth / 2, halka)
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

            val ozet = gunler[gun] ?: continue
            when (yogunluk) {
                Depo.AY_YIGILI -> cubuklariCiz(canvas, i % 7, cy + r + 6f * d, ozet, buAy)
                Depo.AY_AYRINTILI -> haplariCiz(canvas, i % 7, i / 7, cy + r + 4f * d, ozet, buAy)
                else -> {
                    val adet = min(3, ozet.renkler.size)
                    val aralik = 7f * d
                    var x = cx - (adet - 1) * aralik / 2f
                    val y = cy + r + 6f * d
                    for (k in 0 until adet) {
                        dolgu.color = ozet.renkler[k]
                        canvas.drawCircle(x, y, 2.6f * d, dolgu)
                        x += aralik
                    }
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

    /** Yığılı: her etkinliğe bir ince çubuk, en çok [YIGIN] tane. */
    private fun cubuklariCiz(canvas: Canvas, sutun: Int, ust: Float, ozet: GunOzeti, buAy: Boolean) {
        val sol = kenar + sutun * sutunGenisligi() + 6f * d
        val sag = kenar + (sutun + 1) * sutunGenisligi() - 6f * d
        var y = ust
        for (e in ozet.etkinlikler.take(YIGIN)) {
            dolgu.color = e.renk
            dolgu.alpha = if (buAy) 255 else 110
            alan.set(sol, y, sag, y + 3f * d)
            canvas.drawRoundRect(alan, 1.5f * d, 1.5f * d, dolgu)
            y += 5f * d
        }
        dolgu.alpha = 255
    }

    /** Ayrıntılı: hücreye sığdığı kadar etkinlik başlığı, gerisi "+N". */
    private fun haplariCiz(canvas: Canvas, sutun: Int, satir: Int, ust: Float, ozet: GunOzeti, buAy: Boolean) {
        val sol = kenar + sutun * sutunGenisligi() + 2f * d
        val sag = kenar + (sutun + 1) * sutunGenisligi() - 2f * d
        val alt = (satir + 1) * satirYuksekligi - 2f * d
        val adim = hapYuksekligi + 2f * d
        val sigan = ((alt - ust + 2f * d) / adim).toInt().coerceAtLeast(0)
        if (sigan == 0) return
        val liste = ozet.etkinlikler
        val gosterilen = if (liste.size > sigan) sigan - 1 else liste.size
        val gece = ZamanOlcusu.gece(context)
        val saydam = if (buAy) 255 else 120
        var y = ust
        for (e in liste.take(gosterilen)) {
            alan.set(sol, y, sag, y + hapYuksekligi)
            dolgu.color = ZamanOlcusu.blokZemini(e.renk, gece)
            dolgu.alpha = dolgu.alpha * saydam / 255
            canvas.drawRoundRect(alan, 3f * d, 3f * d, dolgu)
            dolgu.color = e.renk
            dolgu.alpha = saydam
            canvas.drawRect(sol, y + 1f * d, sol + 2.5f * d, y + hapYuksekligi - 1f * d, dolgu)
            dolgu.alpha = 255
            hapYazi.color = metin
            hapYazi.alpha = saydam
            val genislik = sag - sol - 6f * d
            if (genislik > 4f * d) {
                val metinSatiri = TextUtils.ellipsize(e.baslik, hapYazi, genislik, TextUtils.TruncateAt.END).toString()
                val taban = y + hapYuksekligi / 2f - (hapYazi.ascent() + hapYazi.descent()) / 2f
                canvas.drawText(metinSatiri, sol + 4.5f * d, taban, hapYazi)
            }
            y += adim
        }
        val kalan = liste.size - gosterilen
        if (kalan > 0) {
            hapYazi.color = soluk
            hapYazi.alpha = saydam
            val taban = y + hapYuksekligi / 2f - (hapYazi.ascent() + hapYazi.descent()) / 2f
            canvas.drawText("+$kalan", sol + 4.5f * d, taban, hapYazi)
        }
        hapYazi.alpha = 255
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
                cokParmak = false
                return true
            }
            // İki parmakla sıkıştırma (yoğunluk değiştirme) bir güne dokunma sayılmasın.
            MotionEvent.ACTION_POINTER_DOWN -> cokParmak = true
            MotionEvent.ACTION_UP -> {
                if (!cokParmak && abs(e.x - basX) < tikSiniri && abs(e.y - basY) < tikSiniri) {
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
            if (yogunluk != Depo.AY_KOMPAKT) gunler[gun]?.etkinlikler?.take(5)?.forEach { s.append(", ").append(it.baslik) }
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

private const val YIGIN = 4
