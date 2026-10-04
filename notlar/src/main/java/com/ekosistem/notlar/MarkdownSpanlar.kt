package com.ekosistem.notlar

import android.graphics.Bitmap
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

/** "- " madde işaretini yuvarlak madde imine çevirir. */
class MaddeSpan : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = paint.measureText(IM).toInt()

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
        canvas.drawText(IM, x, y.toFloat(), paint)
    }

    private companion object {
        const val IM = "•  "
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

/**
 * `![](ekler/ad.jpg)` satırını görselin kendisiyle değiştirir. Bit eşlem henüz
 * çözülmediyse (ya da dosya bulunamadıysa) yerine adı yazan bir yer tutucu çizer;
 * böylece satır yüksekliği zıplamadan görsel gelince yerine oturur.
 */
class GorselSpan(
    private val bitmap: Bitmap?,
    private val enFazlaGenislik: Int,
    private val etiket: String,
    private val cerceveRengi: Int,
    private val yaziRengi: Int,
    private val yogunluk: Float
) : ReplacementSpan() {

    private val boya = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun genislik(): Int =
        (bitmap?.width ?: enFazlaGenislik).coerceIn(1, enFazlaGenislik.coerceAtLeast(1))

    private fun yukseklik(): Int = bitmap?.height ?: (YER_TUTUCU_DP * yogunluk).toInt()

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val h = yukseklik()
        val bosluk = (BOSLUK_DP * yogunluk).toInt()
        if (fm != null) {
            // Satır, görselin tamamını içine alacak kadar yükselir.
            fm.ascent = -(h + bosluk)
            fm.top = fm.ascent
            fm.descent = 0
            fm.bottom = 0
        }
        return genislik()
    }

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
        val h = yukseklik()
        val ust = (y - h).toFloat()
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, x, ust, null)
            return
        }
        boya.color = cerceveRengi
        boya.style = Paint.Style.STROKE
        boya.strokeWidth = yogunluk
        val kose = 12f * yogunluk
        val kutu = RectF(x, ust, x + genislik(), y.toFloat())
        canvas.drawRoundRect(kutu, kose, kose, boya)

        boya.style = Paint.Style.FILL
        boya.color = yaziRengi
        boya.textSize = paint.textSize * 0.85f
        val yazi = kisalt(etiket, kutu.width() - 24 * yogunluk)
        canvas.drawText(
            yazi,
            x + 12 * yogunluk,
            ust + h / 2f - (boya.ascent() + boya.descent()) / 2f,
            boya
        )
    }

    private fun kisalt(metin: String, alan: Float): String {
        if (alan <= 0f) return ""
        if (boya.measureText(metin) <= alan) return metin
        var kesilen = metin
        while (kesilen.isNotEmpty() && boya.measureText("$kesilen…") > alan) {
            kesilen = kesilen.dropLast(1)
        }
        return "$kesilen…"
    }

    private companion object {
        const val YER_TUTUCU_DP = 44f
        const val BOSLUK_DP = 6f
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

/**
 * Kod bloğu (```) satırlarının zemini: satırın tamamını kaplar, art arda
 * satırlar tek bir blok gibi görünür. Yazı zaten eş aralıklıdır.
 */
class KodBlokSpan(
    private val renk: Int,
    /** Yazının zemin kenarına yapışmaması için soldan boşluk (piksel). */
    private val bosluk: Int
) : android.text.style.LineBackgroundSpan, LeadingMarginSpan {

    override fun getLeadingMargin(first: Boolean): Int = bosluk

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?
    ) {
    }

    override fun drawBackground(
        c: Canvas,
        p: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        val eski = p.color
        p.color = renk
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), p)
        p.color = eski
    }
}

/**
 * Bul ve değiştirde eşleşmenin zemini. BackgroundColorSpan'dan türemez:
 * biçimlendirici her harfte o türü siler, vurgu yazarken kaybolurdu.
 */
class BulVurguSpan(private val renk: Int) : android.text.style.CharacterStyle(),
    android.text.style.UpdateAppearance {
    override fun updateDrawState(tp: android.text.TextPaint) {
        tp.bgColor = renk
    }
}
