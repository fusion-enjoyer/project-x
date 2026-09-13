package com.ekosistem.notlar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan

/** Markdown işaretini tamamen gizler (imleç başka satırdayken). */
class GizliSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = 0

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
    }
}

/** "> alıntı" satırının solundaki dikey çubuk. */
class AlintiSpan(private val renk: Int, private val yogunluk: Float) : LeadingMarginSpan {

    private val boya = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getLeadingMargin(first: Boolean): Int = (14 * yogunluk).toInt()

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        yon: Int,
        ust: Int,
        taban: Int,
        alt: Int,
        metin: CharSequence?,
        bas: Int,
        son: Int,
        ilkSatir: Boolean,
        duzen: Layout?
    ) {
        boya.color = renk
        boya.style = Paint.Style.FILL
        val genislik = 3f * yogunluk
        val sol = x + yon * 1f
        val kutu = RectF(
            minOf(sol, sol + yon * genislik),
            ust.toFloat(),
            maxOf(sol, sol + yon * genislik),
            alt.toFloat()
        )
        c.drawRoundRect(kutu, genislik / 2f, genislik / 2f, boya)
    }
}

/** "---" satırını yatay ayraç çizgisine çevirir. */
class AyracSpan(private val genislik: Int, private val renk: Int) : ReplacementSpan() {

    private val boya = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = genislik

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        boya.color = renk
        boya.strokeWidth = (paint.textSize * 0.07f).coerceAtLeast(1f)
        val orta = y + (paint.ascent() + paint.descent()) / 2f
        canvas.drawLine(x, orta, x + genislik, orta, boya)
    }
}
