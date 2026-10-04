package com.ekosistem.notlar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.AttributeSet
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatEditText

/**
 * Notun metin alanı. İmleç satır değiştirdiğinde haber verir ve imleci
 * kendisi çizer.
 *
 * Neden kendi imleci: Android imleci satırın tepesinden dibine kadar çizer.
 * Başlık satırında yazı tipinin üst boşluğu ve satır aralığı da buna girdiği
 * için imleç harflerden uzun ve yukarı kaymış görünüyordu; boş başlıkta ise
 * gövde boyunda kalıp "Başlık" ipucunun yarısına ancak geliyordu. Bu imleç,
 * bulunduğu yerdeki yazının kendi ölçüsüyle (harfin üstünden kuyruğuna) çizilir.
 * Sistemin imleci saydam bir çizimle görünmez kılınır; tutamaklar yerinde kalır.
 */
class NotEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var secimDegisti: ((Int, Int) -> Unit)? = null

    /** Metin tamamen boşken imlecin boyu: başlık ipucuyla aynı (piksel). */
    var baslikPx = 0f

    var imlecRengi = 0
        set(deger) {
            field = deger
            imlecBoya?.color = deger
        }

    // Üst sınıfın kurucusu onSelectionChanged'i bu alanlar hazırlanmadan
    // çağırabiliyor; o yüzden boş olabilirler.
    private var imlecBoya: Paint? = null
    private var olcuBoya: TextPaint? = null
    private var yanip: Runnable? = null
    private val kutu = RectF()
    private var sonHareket = 0L

    init {
        imlecBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = currentTextColor }
        olcuBoya = TextPaint(Paint.ANTI_ALIAS_FLAG)
        yanip = Runnable {
            invalidate()
            yanipZamanla()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        hareketEtti()
        secimDegisti?.invoke(selStart, selEnd)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, after: Int) {
        super.onTextChanged(text, start, before, after)
        hareketEtti()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        hareketEtti()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        hareketEtti()
    }

    override fun onDetachedFromWindow() {
        yanip?.let { removeCallbacks(it) }
        super.onDetachedFromWindow()
    }

    /** Yazarken ya da imleç taşınırken imleç sönmez; yanıp sönme baştan başlar. */
    private fun hareketEtti() {
        sonHareket = SystemClock.uptimeMillis()
        if (yanip == null) return
        invalidate()
        yanipZamanla()
    }

    private fun yanipZamanla() {
        val gorev = yanip ?: return
        removeCallbacks(gorev)
        if (!imlecGorunmeli()) return
        val gecen = SystemClock.uptimeMillis() - sonHareket
        postDelayed(gorev, YARIM_DONEM - gecen % YARIM_DONEM)
    }

    private fun imlecGorunmeli(): Boolean =
        isFocused && hasWindowFocus() && isCursorVisible && selectionStart >= 0 &&
            selectionStart == selectionEnd

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!imlecGorunmeli()) return
        if ((SystemClock.uptimeMillis() - sonHareket) / YARIM_DONEM % 2 != 0L) return
        val duzen = layout ?: return
        val boya = imlecBoya ?: return
        val olcu = olcuBoya ?: return
        val s = text ?: return
        val konum = selectionStart.coerceIn(0, s.length)
        val satir = duzen.getLineForOffset(konum)

        val ust: Float
        val alt: Float
        if (s.isEmpty()) {
            // Boş notta ipucu ("Başlık") görünür; imleç onun boyunda olsun.
            olcu.set(paint)
            if (baslikPx > 0f) olcu.textSize = baslikPx
            ust = duzen.getLineTop(0).toFloat()
            alt = ust + olcu.descent() - olcu.ascent()
        } else {
            olcu.set(paint)
            val ornek = olcuKarakteri(s, konum, duzen.getLineStart(satir), duzen.getLineEnd(satir))
            if (ornek >= 0) {
                for (span in s.getSpans(ornek, ornek + 1, MetricAffectingSpan::class.java)) {
                    span.updateMeasureState(olcu)
                }
            }
            val taban = duzen.getLineBaseline(satir).toFloat()
            ust = taban + olcu.ascent()
            alt = taban + olcu.descent()
        }

        val yog = resources.displayMetrics.density
        val genislik = 2f * yog
        val x = (duzen.getPrimaryHorizontal(konum) - genislik / 2).coerceAtLeast(0f)
        val solPay = totalPaddingLeft.toFloat()
        val ustPay = totalPaddingTop.toFloat()
        kutu.set(solPay + x, ustPay + ust, solPay + x + genislik, ustPay + alt)
        canvas.drawRoundRect(kutu, genislik / 2, genislik / 2, boya)
    }

    /**
     * İmlecin boyunu belirleyen harf: önündeki harf (yazılan yazının devamı),
     * satır başındaysa arkasındaki, satır boşsa satır sonu karakterinin kendisi
     * (biçimlendirici boş başlık satırına orada büyük boyut verir).
     */
    private fun olcuKarakteri(s: CharSequence, konum: Int, satirBasi: Int, satirSonu: Int): Int {
        if (konum > satirBasi && s[konum - 1] != '\n') return konum - 1
        if (konum < satirSonu && konum < s.length && s[konum] != '\n') return konum
        if (konum < s.length) return konum
        return -1
    }

    private companion object {
        /** Sistem imleciyle aynı ritim: yarım saniye yanık, yarım saniye sönük. */
        const val YARIM_DONEM = 500L
    }
}

/**
 * Notu taşıyan kaydırma alanı. Metin alanının kendi kaydırmasında parmak
 * kalkınca sayfa olduğu yerde duruyordu (savurma yok); not ağır ve takılır
 * hissettiriyordu. Metin bu alanın içinde tam boyuyla durur, kaydırmayı ve
 * savurmayı sistemin kaydırma görünümü yapar.
 */
class NotKaydirici @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    /** (yeniKonum, oncekiKonum) — dikey kaydırma konumu piksel cinsinden. */
    var kaydirildi: ((Int, Int) -> Unit)? = null

    /**
     * Üstte ve altta yüzen çubukların kapladığı yer. İmleç görünür kılınırken
     * bu paylar hesaba katılır; yoksa yazılan satır biçim çubuğunun ya da bul
     * çubuğunun arkasında kalırdı.
     */
    var ustPay = 0
    var altPay = 0

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        kaydirildi?.invoke(t, oldt)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        // Metnin tamamı (odak değişiminde istenen) olduğu gibi kalır; yalnız
        // imleç gibi küçük alanlar paylarla genişletilir.
        if (rect.height() >= height) return super.computeScrollDeltaToGetChildRectOnScreen(rect)
        val genis = Rect(rect)
        genis.top -= ustPay
        genis.bottom += altPay
        return super.computeScrollDeltaToGetChildRectOnScreen(genis)
    }
}
