package com.ekosistem.notlar

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Bütün notlardaki açık görevler. Kutuya dokununca görev işaretlenir ve
 * listeden düşer; satıra dokununca görevin notu açılır; "+" bugünün notuna
 * görev ekler. Kilitli notların görevleri hiç gösterilmez (Görevler ekranı gibi).
 */
class GorevlerWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_gorevler)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.gorevler))
        g.setTextViewText(R.id.widgetBos, context.getString(R.string.gorev_yok))
        NotWidget.vurguMetni(context, g, R.id.widgetSayac)
        NotWidget.vurguyaBoya(context, g, R.id.widgetEkle)

        val acik = try {
            NotDeposu(context).gorevleriListele(false).size
        } catch (_: Exception) {
            0
        }
        g.setTextViewText(
            R.id.widgetSayac,
            if (acik == 0) "" else context.resources.getQuantityString(R.plurals.acik_gorev, acik, acik)
        )

        g.setOnClickPendingIntent(
            R.id.widgetBaslik,
            NotWidget.ekranNiyeti(context, id, Intent(context, GorevlerActivity::class.java))
        )
        g.setOnClickPendingIntent(
            R.id.widgetEkle,
            NotWidget.ekranNiyeti(context, id + 1000, Intent(context, GorevEkleActivity::class.java))
        )

        val servis = Intent(context, GorevlerWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetListe, servis)
        g.setEmptyView(R.id.widgetListe, R.id.widgetBos)
        g.setPendingIntentTemplate(R.id.widgetListe, WidgetEylemActivity.sablon(context, id + 2000))
        return g
    }
}

class GorevlerWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        GorevlerFabrikasi(NotWidget.dilBaglami(applicationContext))
}

class GorevlerFabrikasi(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var gorevler: List<Gorev> = emptyList()
    private val etiketci = TarihEtiketi(context)

    /** Görev metni uygulamadaki gibi biçimli (kalın, bağlantı, etiket); tema değişince tazelenir. */
    private var stil = WidgetTema.stil(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        etiketci.tazele()
        stil = WidgetTema.stil(context)
        gorevler = try {
            NotDeposu(context).gorevleriListele(false).take(EN_FAZLA)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        gorevler = emptyList()
    }

    override fun getCount(): Int = gorevler.size

    override fun getViewAt(pozisyon: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_gorev_item)
        val gorev = gorevler.getOrNull(pozisyon) ?: return g
        WidgetTema.metin(context, g, R.id.satirMetin)
        WidgetTema.ikincil(context, g, R.id.satirAlt)
        WidgetTema.ikincilIkon(context, g, R.id.satirKutu)
        g.setTextViewText(R.id.satirMetin, NotOnizleme.bicimli(gorev.metin, stil))
        g.setTextViewText(R.id.satirAlt, altSatir(gorev))
        g.setViewVisibility(R.id.satirAlt, View.VISIBLE)
        g.setOnClickFillInIntent(R.id.satirKutu, WidgetEylemActivity.gorevDoldurma(gorev))
        g.setOnClickFillInIntent(R.id.satirKok, WidgetEylemActivity.acDoldurma(gorev.notUri.toString()))
        return g
    }

    /** "2 gün gecikti · Not adı": tarih renkli (Görevler ekranındaki gibi). */
    private fun altSatir(gorev: Gorev): CharSequence {
        val gun = gorev.sonGun ?: return gorev.notBasligi
        val fark = etiketci.fark(gun)
        val etiket = etiketci.etiket(gun)
        val s = SpannableStringBuilder(etiket)
        val renk = when {
            fark < 0 -> WidgetTema.gecikmis(context)
            fark == 0 -> WidgetTema.vurgu(context)
            else -> null
        }
        if (renk != null) {
            s.setSpan(ForegroundColorSpan(renk), 0, etiket.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(StyleSpan(Typeface.BOLD), 0, etiket.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s.append(" · ").append(gorev.notBasligi)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false

    private companion object {
        const val EN_FAZLA = 100
    }
}
