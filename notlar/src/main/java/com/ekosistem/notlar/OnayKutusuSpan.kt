package com.ekosistem.notlar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/**
 * "- [ ]" / "- [x]" işaretini gerçek bir onay kutusu olarak çizer.
 * Dosyadaki metin değişmez; yalnızca görünüm değişir.
 */
class OnayKutusuSpan(
    private val isaretli: Boolean,
    private val vurgu: Int,
    private val soluk: Int
) : ReplacementSpan() {

    private val boya = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun kutuBoyu(paint: Paint): Float = paint.textSize * 0.92f

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = (kutuBoyu(paint) + paint.textSize * 0.38f).toInt()

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
        val boy = kutuBoyu(paint)
        val orta = y + (paint.ascent() + paint.descent()) / 2f
        val ust = orta - boy / 2f
        val kose = boy * 0.28f
        val kalinlik = boy * 0.11f
        /*
         * Çerçeve yolun üstüne ortalanarak çizilir: kutuyu tam x'ten başlatırsak
         * çizginin sol yarısı metin alanının kırpma sınırının dışında kalır ve
         * sol kenar ince görünür. Yarım çizgi kalınlığı kadar içeri alınıyor.
         */
        val ic = kalinlik / 2f
        val kutu = RectF(x + ic, ust + ic, x + boy - ic, ust + boy - ic)

        if (isaretli) {
            boya.style = Paint.Style.FILL
            boya.color = vurgu
            canvas.drawRoundRect(kutu, kose, kose, boya)

            boya.style = Paint.Style.STROKE
            boya.color = soluk
            boya.strokeWidth = kalinlik * 1.2f
            boya.strokeCap = Paint.Cap.ROUND
            val solX = x + boy * 0.26f
            val ortaX = x + boy * 0.44f
            val sagX = x + boy * 0.75f
            val ortaY = ust + boy * 0.52f
            val altY = ust + boy * 0.70f
            val ustY = ust + boy * 0.32f
            canvas.drawLine(solX, ortaY, ortaX, altY, boya)
            canvas.drawLine(ortaX, altY, sagX, ustY, boya)
        } else {
            boya.style = Paint.Style.STROKE
            boya.color = soluk
            boya.strokeWidth = kalinlik
            canvas.drawRoundRect(kutu, kose, kose, boya)
        }
    }
}
