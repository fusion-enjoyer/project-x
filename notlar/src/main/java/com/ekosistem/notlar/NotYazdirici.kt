package com.ekosistem.notlar

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import java.io.FileOutputStream

/**
 * Notu sistemin yazdırma ekranına verir; oradan yazıcıya ya da "PDF olarak
 * kaydet"e gider. Kâğıt beyaz olduğu için not her zaman açık temanın
 * renkleriyle biçimlenir, gece modunda da okunur kalır.
 *
 * Editördeki yazı boyları ekran pikseli; kâğıtta gövde [GOVDE_PT] puntoya
 * gelecek şekilde ölçeklenir. Sayfa sonları satır ortasından geçmez.
 */
class NotYazdirici(
    context: Context,
    private val belgeAdi: String,
    private val metin: String,
    /** Editörün boyası: yazı tipi ve gövde boyu buradan gelir. */
    taban: TextPaint,
    private val satirCarpani: Float,
    private val satirEki: Float,
    depo: NotDeposu
) : PrintDocumentAdapter() {

    private val acik: Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        }
    )
    private val bicimci = MarkdownBicimci(acik).also { it.depo = depo }
    private val boya = TextPaint(taban).apply {
        color = ContextCompat.getColor(acik, R.color.metin)
    }

    private var nitelik: PrintAttributes? = null
    private var duzen: StaticLayout? = null
    private var olcek = 1f
    private var payPt = 0f

    /** Her sayfanın ilk ve son satırı. */
    private var sayfalar: List<IntRange> = emptyList()

    override fun onLayout(
        eski: PrintAttributes?,
        yeni: PrintAttributes,
        iptal: CancellationSignal?,
        geri: LayoutResultCallback,
        ekler: Bundle?
    ) {
        if (iptal?.isCanceled == true) {
            geri.onLayoutCancelled()
            return
        }
        val kagit = yeni.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        val genislikPt = kagit.widthMils * 72f / 1000f
        val boyPt = kagit.heightMils * 72f / 1000f
        payPt = PAY_PT
        olcek = GOVDE_PT / boya.textSize.coerceAtLeast(1f)
        // StaticLayout sıfır ya da eksi genişlikte çöker; uç kâğıt boyunda bile en az bu kadar.
        val icGenislik = ((genislikPt - 2 * payPt) / olcek).toInt().coerceAtLeast(EN_AZ_GENISLIK)
        val icBoy = (boyPt - 2 * payPt) / olcek

        val bicimli = SpannableStringBuilder(metin)
        bicimci.uygula(bicimli, -1, icGenislik)
        val yeniDuzen = duzenKur(bicimli, icGenislik)
        duzen = yeniDuzen
        sayfalar = sayfala(yeniDuzen, icBoy)
        // PrintedPdfDocument çözünürlük ya da kenar boşluğu eksikse çöker; boş gelen doldurulur.
        nitelik = PrintAttributes.Builder()
            .setMediaSize(kagit)
            .setResolution(yeni.resolution ?: PrintAttributes.Resolution("pdf", "PDF", 300, 300))
            .setMinMargins(yeni.minMargins ?: PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(
                if (yeni.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME) {
                    PrintAttributes.COLOR_MODE_MONOCHROME
                } else {
                    PrintAttributes.COLOR_MODE_COLOR
                }
            )
            .build()

        val bilgi = PrintDocumentInfo.Builder("$belgeAdi.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(sayfalar.size)
            .build()
        geri.onLayoutFinished(bilgi, true)
    }

    override fun onWrite(
        istenen: Array<out PageRange>,
        hedef: ParcelFileDescriptor,
        iptal: CancellationSignal?,
        geri: WriteResultCallback
    ) {
        val duzen = duzen
        val nitelik = nitelik
        if (duzen == null || nitelik == null) {
            geri.onWriteFailed(null)
            return
        }
        // İstenen aralıktan bağımsız bütün sayfalar yazılır; seçimi yazdırma ekranı yapar.
        val belge = PrintedPdfDocument(acik, nitelik)
        try {
            sayfalar.forEachIndexed { sira, satirlar ->
                if (iptal?.isCanceled == true) {
                    geri.onWriteCancelled()
                    return
                }
                val sayfa = belge.startPage(sira)
                val tuval = sayfa.canvas
                val ust = duzen.getLineTop(satirlar.first)
                val alt = duzen.getLineBottom(satirlar.last)
                tuval.save()
                tuval.translate(payPt, payPt)
                tuval.scale(olcek, olcek)
                tuval.translate(0f, -ust.toFloat())
                tuval.clipRect(0, ust, duzen.width, alt)
                duzen.draw(tuval)
                tuval.restore()
                belge.finishPage(sayfa)
            }
            FileOutputStream(hedef.fileDescriptor).use { belge.writeTo(it) }
            geri.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            geri.onWriteFailed(e.message)
        } finally {
            belge.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun duzenKur(bicimli: CharSequence, genislik: Int): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(bicimli, 0, bicimli.length, boya, genislik)
                .setLineSpacing(satirEki, satirCarpani)
                .setIncludePad(true)
                .build()
        } else {
            StaticLayout(bicimli, boya, genislik, Layout.Alignment.ALIGN_NORMAL, satirCarpani, satirEki, true)
        }

    private companion object {
        const val EN_AZ_GENISLIK = 200

        /** Kâğıttaki gövde yazı boyu (punto). */
        const val GOVDE_PT = 11f

        /** Dört kenardan boşluk: 2 cm kadar. */
        const val PAY_PT = 56f

        /** Satırları sayfalara böler; satır sayfa sınırını aşarsa sonraki sayfaya geçer. */
        fun sayfala(duzen: Layout, sayfaBoyu: Float): List<IntRange> {
            if (duzen.lineCount == 0) return listOf(0..0)
            val sonuc = mutableListOf<IntRange>()
            var ilk = 0
            while (ilk < duzen.lineCount) {
                val sinir = duzen.getLineTop(ilk) + sayfaBoyu
                var son = ilk
                while (son + 1 < duzen.lineCount && duzen.getLineBottom(son + 1) <= sinir) son++
                sonuc.add(ilk..son)
                ilk = son + 1
            }
            return sonuc
        }
    }
}
