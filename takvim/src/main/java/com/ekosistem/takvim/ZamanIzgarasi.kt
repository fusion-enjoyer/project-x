package com.ekosistem.takvim

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/** Üç zaman görünümünün (başlık, tüm gün şeridi, saat ızgarası) ortak ölçüleri. */
object ZamanOlcusu {
    /** Soldaki saat etiketi sütunu. */
    fun kenar(d: Float) = 46f * d

    fun gece(c: Context) =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Etkinlik bloğunun zemini: takvim renginin açık tonu. */
    fun blokZemini(renk: Int, gece: Boolean): Int =
        (renk and 0x00FFFFFF) or ((if (gece) 0x59 else 0x38) shl 24)
}

/** Haftanın gün başlıkları: gün adı ve sayısı; bugün vurgu dairesinde. */
class HaftaBasligi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ilkGun = 0
        set(v) { field = v; invalidate() }
    var gunSayisi = 7
        set(v) { field = v; invalidate() }
    var bugun = 0
        set(v) { field = v; invalidate() }
    /** Sol sütunda küçük hafta numarası ("H40"); boşsa çizilmez. */
    var hafta: String = ""
        set(v) { field = v; invalidate() }
    var gunSecildi: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val metin = ContextCompat.getColor(context, TR.color.metin)
    private val soluk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val vurgu = Tasarim.vurgu(context)
    private val vurguUzeri = Tasarim.vurguUzeri(context)
    private val gunAdi = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 12f * d * olcek }
    private val sayi = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 16f * d * olcek }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)

    init { isClickable = true }

    private val daire get() = 17f * d * olcek

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (daire * 2 + 30f * d).toInt())
    }

    private fun sutun() = (width - ZamanOlcusu.kenar(d)) / gunSayisi

    override fun onDraw(canvas: Canvas) {
        val kenar = ZamanOlcusu.kenar(d)
        if (hafta.isNotEmpty()) {
            gunAdi.color = soluk
            canvas.drawText(hafta, kenar / 2f, height / 2f, gunAdi)
        }
        for (i in 0 until gunSayisi) {
            val gun = ilkGun + i
            val cx = kenar + i * sutun() + sutun() / 2f
            val bug = gun == bugun
            gunAdi.color = if (bug) vurgu else soluk
            gunAdi.typeface = if (bug) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(Metinler.haftaGunuKisa(Gun.haftaGunu(gun)), cx, 8f * d - gunAdi.ascent(), gunAdi)
            val cy = 8f * d - gunAdi.ascent() + gunAdi.descent() + 6f * d + daire
            if (bug) {
                dolgu.color = vurgu
                canvas.drawCircle(cx, cy, daire, dolgu)
            }
            sayi.color = if (bug) vurguUzeri else metin
            sayi.typeface = if (bug) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(Gun.ayinGunu(gun).toString(), cx, cy - (sayi.ascent() + sayi.descent()) / 2f, sayi)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val kenar = ZamanOlcusu.kenar(d)
            if (e.x >= kenar) {
                val i = ((e.x - kenar) / sutun()).toInt().coerceIn(0, gunSayisi - 1)
                performClick()
                gunSecildi?.invoke(ilkGun + i)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

/**
 * Gün başlığının altındaki tüm gün şeridi: tüm gün etkinlikleri sütunlara
 * yayılan hap olarak (birkaç günlük etkinlik tek uzun hap). Üç satırdan fazlası
 * "+n" ile sayılır.
 */
class TumGunSeridi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ilkGun = 0
    var gunSayisi = 7
    var ornekler: List<Ornek> = emptyList()
        set(v) { field = v; hesapla(); requestLayout(); invalidate() }
    var ornekTiklandi: ((Ornek) -> Unit)? = null

    private class Hap(val ornek: Ornek, val ilk: Int, val son: Int, val satir: Int)

    private val d = resources.displayMetrics.density
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    private val haplar = ArrayList<Hap>()
    private val tasan = HashMap<Int, Int>()
    private val satirYuksekligi get() = 22f * d * olcek
    private val yazi = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d * olcek }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)
    private val soluk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val rect = RectF()

    init { isClickable = true }

    fun gunleriAyarla(ilk: Int, adet: Int) {
        ilkGun = ilk
        gunSayisi = adet
        hesapla()
        requestLayout()
        invalidate()
    }

    private fun satirAdedi(): Int {
        if (haplar.isEmpty()) return 0
        val enAlt = min(MAKS, haplar.maxOf { it.satir } + 1)
        return if (tasan.isNotEmpty()) MAKS + 1 else enAlt
    }

    private fun hesapla() {
        haplar.clear()
        tasan.clear()
        val sutunSonlari = ArrayList<IntArray>()   // satır başına her sütunun dolu olduğu son sütun
        val sirali = ornekler.filter { it.tumGun }
            .sortedWith(compareBy<Ornek>({ it.ilkGun }, { -(it.sonGun - it.ilkGun) }, { it.baslik }))
        for (o in sirali) {
            val i0 = max(o.ilkGun, ilkGun) - ilkGun
            val i1 = min(o.sonGun, ilkGun + gunSayisi - 1) - ilkGun
            if (i1 < i0) continue
            var satir = 0
            while (true) {
                if (satir >= sutunSonlari.size) sutunSonlari.add(IntArray(gunSayisi))
                val dolu = (i0..i1).any { sutunSonlari[satir][it] != 0 }
                if (!dolu) break
                satir++
            }
            for (k in i0..i1) sutunSonlari[satir][k] = 1
            if (satir < MAKS) haplar.add(Hap(o, i0, i1, satir))
            else for (k in i0..i1) tasan[k] = (tasan[k] ?: 0) + 1
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val satir = satirAdedi()
        val h = if (satir == 0) 0 else (satir * satirYuksekligi + 6f * d).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun sutun() = (width - ZamanOlcusu.kenar(d)) / gunSayisi

    override fun onDraw(canvas: Canvas) {
        val kenar = ZamanOlcusu.kenar(d)
        val gece = ZamanOlcusu.gece(context)
        for (h in haplar) {
            rect.set(
                kenar + h.ilk * sutun() + 2f * d, h.satir * satirYuksekligi + 2f * d,
                kenar + (h.son + 1) * sutun() - 2f * d, (h.satir + 1) * satirYuksekligi
            )
            dolgu.color = h.ornek.renk
            canvas.drawRoundRect(rect, 6f * d, 6f * d, dolgu)
            yazi.color = Tasarim.uzerindekiRenk(h.ornek.renk)
            yazi.typeface = Typeface.DEFAULT_BOLD
            val yer = rect.width() - 10f * d
            if (yer > 0) {
                val kesik = TextUtils.ellipsize(h.ornek.baslik.ifBlank { context.getString(R.string.basliksiz) }, yazi, yer, TextUtils.TruncateAt.END)
                canvas.drawText(kesik, 0, kesik.length, rect.left + 5f * d, rect.centerY() - (yazi.ascent() + yazi.descent()) / 2f, yazi)
            }
        }
        if (tasan.isNotEmpty()) {
            yazi.color = soluk
            yazi.typeface = Typeface.DEFAULT_BOLD
            for ((k, n) in tasan) {
                val x = kenar + k * sutun() + sutun() / 2f
                val metin = "+$n"
                canvas.drawText(metin, x - yazi.measureText(metin) / 2f, MAKS * satirYuksekligi + satirYuksekligi / 2f - (yazi.ascent() + yazi.descent()) / 2f, yazi)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val kenar = ZamanOlcusu.kenar(d)
            val hap = haplar.firstOrNull {
                e.x >= kenar + it.ilk * sutun() && e.x <= kenar + (it.son + 1) * sutun() &&
                    e.y >= it.satir * satirYuksekligi && e.y <= (it.satir + 1) * satirYuksekligi + 4f * d
            }
            if (hap != null) {
                performClick()
                ornekTiklandi?.invoke(hap.ornek)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        const val MAKS = 3
    }
}

/**
 * Gün ve hafta görünümünün saat ızgarası. Etkinlikler gün sütunlarında blok
 * olarak çizilir, çakışanlar yan yana dizilir ([Yerlesim]); bugünün sütununda
 * şimdiki an çizgisi vardır. Dokunmak etkinliği açar, boş yere uzun basmak
 * o saate yeni etkinlik başlatır.
 */
class ZamanIzgarasi @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ilkGun = 0
        set(v) { field = v; bloklariKur() }
    var gunSayisi = 7
        set(v) { field = v; bloklariKur() }
    var bugun = 0
        set(v) { field = v; invalidate() }
    var ornekler: List<Ornek> = emptyList()
        set(v) { field = v; bloklariKur() }
    var ornekTiklandi: ((Ornek) -> Unit)? = null
    var bosUzunBasildi: ((gun: Int, dakika: Int) -> Unit)? = null

    private class Blok(val ornek: Ornek, val gun: Int, val alan: RectF, val basDk: Int, val bitDk: Int)

    private val d = resources.displayMetrics.density
    private val olcek get() = resources.configuration.fontScale.coerceIn(1f, 1.3f)
    /** Bir saatin yüksekliği (px). */
    val saatYuksekligi get() = 56f * d * olcek
    private val altBosluk get() = 104f * d

    private val metin = ContextCompat.getColor(context, TR.color.metin)
    private val soluk = ContextCompat.getColor(context, TR.color.metin_ikincil)
    private val ayrac = ContextCompat.getColor(context, TR.color.ayrac)
    private val vurgu = Tasarim.vurgu(context)
    private val cizgi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ayrac; strokeWidth = 1f }
    private val etiket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = soluk; textSize = 11f * d * olcek; textAlign = Paint.Align.RIGHT
    }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG)
    private val yazi = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bloklar = ArrayList<Blok>()
    private val yol = Path()
    private val sim: Runnable = Runnable { invalidate(); zamanlayiciKur() }
    private val kenar get() = ZamanOlcusu.kenar(d)

    private fun sutun() = (width - kenar) / gunSayisi

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (24 * saatYuksekligi + altBosluk).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bloklariKur()
    }

    fun dakikaY(dakika: Int) = dakika / 60f * saatYuksekligi

    private fun bloklariKur() {
        bloklar.clear()
        if (width > 0) {
            val sut = sutun()
            for (i in 0 until gunSayisi) {
                val gun = ilkGun + i
                val parcalar = ornekler.withIndex()
                    .filter { !it.value.tumGun && it.value.gunuIcerir(gun) }
                    .map {
                        val b = it.value.gunBaslangicDk(gun)
                        Yerlesim.Parca(it.index, b, max(it.value.gunBitisDk(gun), b + 30))
                    }
                for (s in Yerlesim.yerlestir(parcalar)) {
                    val o = ornekler[s.no]
                    val p = parcalar.first { it.no == s.no }
                    val genislik = sut / s.sutunSayisi
                    val sol = kenar + i * sut + s.sutun * genislik
                    bloklar.add(
                        Blok(
                            o, gun,
                            RectF(sol + 1.5f * d, dakikaY(p.baslangic) + 1f * d, sol + genislik - 1.5f * d, dakikaY(p.bitis) - 1f * d),
                            o.gunBaslangicDk(gun), o.gunBitisDk(gun)
                        )
                    )
                }
            }
        }
        invalidate()
        erisim.invalidateRoot()
    }

    override fun onDraw(canvas: Canvas) {
        val gece = ZamanOlcusu.gece(context)
        val sut = sutun()
        // Bugünün sütunu çok hafif vurgulanır.
        if (bugun in ilkGun until ilkGun + gunSayisi && gunSayisi > 1) {
            dolgu.color = Tasarim.pastel(vurgu)
            val x = kenar + (bugun - ilkGun) * sut
            canvas.drawRect(x, 0f, x + sut, height.toFloat(), dolgu)
        }
        for (s in 0..24) {
            val y = dakikaY(s * 60)
            if (s in 1..23) canvas.drawText(Metinler.saatEtiketi(context, s), kenar - 8f * d, y - (etiket.ascent() + etiket.descent()) / 2f, etiket)
            canvas.drawLine(kenar, y, width.toFloat(), y, cizgi)
        }
        for (i in 0..gunSayisi) {
            val x = kenar + i * sut
            canvas.drawLine(x, 0f, x, 24 * saatYuksekligi, cizgi)
        }
        for (b in bloklar) blokCiz(canvas, b, gece)
        simdiCizgisi(canvas)
    }

    private fun blokCiz(canvas: Canvas, b: Blok, gece: Boolean) {
        val r = b.alan
        if (r.width() <= 0 || r.height() <= 0) return
        val yaricap = 6f * d
        dolgu.color = ZamanOlcusu.blokZemini(b.ornek.renk, gece)
        canvas.drawRoundRect(r, yaricap, yaricap, dolgu)
        canvas.save()
        yol.reset()
        yol.addRoundRect(r, yaricap, yaricap, Path.Direction.CW)
        canvas.clipPath(yol)
        dolgu.color = b.ornek.renk
        canvas.drawRect(r.left, r.top, r.left + 3.5f * d, r.bottom, dolgu)
        canvas.restore()

        val ic = RectF(r.left + 8f * d, r.top + 3f * d, r.right - 4f * d, r.bottom - 2f * d)
        if (ic.width() < 12f * d || ic.height() < 10f * d) return
        canvas.save()
        canvas.clipRect(ic)
        yazi.color = metin
        yazi.textSize = 12f * d * olcek
        yazi.typeface = Typeface.DEFAULT_BOLD
        val baslik = b.ornek.baslik.ifBlank { context.getString(R.string.basliksiz) }
        @Suppress("DEPRECATION")
        val bas = StaticLayout(baslik, yazi, ic.width().toInt().coerceAtLeast(1), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        canvas.translate(ic.left, ic.top)
        bas.draw(canvas)
        if (ic.height() > bas.height + 14f * d * olcek) {
            yazi.typeface = Typeface.DEFAULT
            yazi.textSize = 11f * d * olcek
            yazi.color = soluk
            val satir = Metinler.saat(context, b.basDk) + " – " + Metinler.saat(context, b.bitDk)
            canvas.drawText(
                TextUtils.ellipsize(satir, yazi, ic.width(), TextUtils.TruncateAt.END).toString(),
                0f, bas.height - yazi.ascent() + 1f * d, yazi
            )
        }
        canvas.restore()
    }

    private fun simdiCizgisi(canvas: Canvas) {
        if (bugun !in ilkGun until ilkGun + gunSayisi) return
        val dk = Gun.yerelDakika(System.currentTimeMillis(), TimeZone.getDefault())
        val y = dakikaY(dk)
        val sut = sutun()
        dolgu.color = vurgu
        val x = kenar + (bugun - ilkGun) * sut
        canvas.drawRect(x, y - 1f * d, x + sut, y + 1f * d, dolgu)
        canvas.drawCircle(x, y, 4.5f * d, dolgu)
    }

    // ---- Zaman çizgisini canlı tut ----

    private fun zamanlayiciKur() {
        val simdi = System.currentTimeMillis()
        postDelayed(sim, 60_000L - simdi % 60_000L + 50L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        zamanlayiciKur()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(sim)
        super.onDetachedFromWindow()
    }

    // ---- Dokunma ----

    private val hareket = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            bloklar.lastOrNull { it.alan.contains(e.x, e.y) }?.let {
                performClick()
                ornekTiklandi?.invoke(it.ornek)
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            if (e.x < kenar || bloklar.any { it.alan.contains(e.x, e.y) }) return
            val gun = ilkGun + ((e.x - kenar) / sutun()).toInt().coerceIn(0, gunSayisi - 1)
            val dk = (e.y / saatYuksekligi * 60).toInt().coerceIn(0, 1410) / 30 * 30
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            bosUzunBasildi?.invoke(gun, dk)
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean = hareket.onTouchEvent(e) || super.onTouchEvent(e)

    override fun performClick(): Boolean = super.performClick()

    // ---- Ekran okuyucu: her etkinlik bir düğüm ----

    private val erisim = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val i = bloklar.indexOfLast { it.alan.contains(x, y) }
            return if (i < 0) INVALID_ID else i
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            for (i in bloklar.indices) ids.add(i)
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val b = bloklar[id]
            node.contentDescription = b.ornek.baslik.ifBlank { context.getString(R.string.basliksiz) } + ", " +
                Metinler.gunBaslik(b.gun) + ", " + Metinler.ornekAltYazisi(context, b.ornek, b.gun)
            val r = Rect()
            b.alan.roundOut(r)
            node.setBoundsInParent(r)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                ornekTiklandi?.invoke(bloklar[id].ornek)
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
