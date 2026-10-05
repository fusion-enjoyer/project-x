package com.ekosistem.notlar

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Sabitlenmiş notlar: iki sütunlu kart ızgarası. Kilitli notun kartında
 * özet yerine "Kilitli" yazar (listedeki gibi). Kartlara dokununca not açılır.
 */
class SabitWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetIzgara

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_sabit)
        WidgetTema.zemin(context, g, R.id.widgetBos)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        g.setTextViewText(R.id.widgetBos, context.getString(R.string.sabit_yok))
        g.setOnClickPendingIntent(
            R.id.widgetBos,
            NotWidget.ekranNiyeti(context, id, Intent(context, MainActivity::class.java))
        )
        val servis = Intent(context, SabitWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetIzgara, servis)
        g.setEmptyView(R.id.widgetIzgara, R.id.widgetBos)
        g.setPendingIntentTemplate(R.id.widgetIzgara, WidgetEylemActivity.sablon(context, id + 2000))
        return g
    }
}

class SabitWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NotKartFabrikasi(NotWidget.dilBaglami(applicationContext), R.layout.widget_sabit_item) {
            NotDeposu(it).notlariListele(null, null).filter { not -> not.sabit }
        }
}

/**
 * Tek bir klasörün ya da etiketin notları. Eklerken süzgeç seçilir
 * ([FiltreAyarActivity]); "+" yeni notu o klasörde ya da o etiketle başlatır.
 */
class FiltreWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_liste)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        NotWidget.vurguyaBoya(context, g, R.id.widgetEkle)
        g.setTextViewText(R.id.widgetBos, context.getString(R.string.widget_filtre_bos))

        val filtre = Filtre.coz(Prefs.widgetFiltre(context, id))
        if (filtre == null) {
            // Süzgeç seçilmemiş: dokununca seçtirilir. "+" ve "not yok" yanıltmasın diye gizli.
            WidgetTema.metin(context, g, R.id.widgetBaslik)
            g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.widget_filtre_sec))
            g.setTextViewText(R.id.widgetBos, "")
            g.setViewVisibility(R.id.widgetEkle, View.GONE)
            val sec = NotWidget.ekranNiyeti(
                context,
                id,
                Intent(context, FiltreAyarActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("notlar-widget://filtre/$id"))
            )
            g.setOnClickPendingIntent(R.id.widgetBaslik, sec)
            g.setOnClickPendingIntent(R.id.widgetBos, sec)
            return g
        }
        g.setViewVisibility(R.id.widgetEkle, View.VISIBLE)
        if (filtre.etiket) {
            NotWidget.vurguMetni(context, g, R.id.widgetBaslik)
            g.setTextViewText(R.id.widgetBaslik, "#" + filtre.ad)
        } else {
            WidgetTema.metin(context, g, R.id.widgetBaslik)
            g.setTextViewText(R.id.widgetBaslik, filtre.ad)
        }
        val yeni = NotWidget.yeniNotNiyeti(context).apply {
            if (filtre.etiket) putExtra(EditorActivity.EK_ETIKET, filtre.ad) else putExtra("klasor", filtre.ad)
        }
        g.setOnClickPendingIntent(R.id.widgetEkle, NotWidget.ekranNiyeti(context, id, yeni))
        // Başlığa dokununca uygulama o klasör/etiketle açılır.
        val liste = Intent(context, MainActivity::class.java).apply {
            if (filtre.etiket) putExtra("etiket", filtre.ad) else putExtra("klasor", filtre.ad)
        }.setData(Uri.parse("notlar-widget://liste/$id")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        g.setOnClickPendingIntent(R.id.widgetBaslik, NotWidget.ekranNiyeti(context, id + 1000, liste))

        val servis = Intent(context, FiltreWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetListe, servis)
        g.setEmptyView(R.id.widgetListe, R.id.widgetBos)
        g.setPendingIntentTemplate(R.id.widgetListe, WidgetEylemActivity.sablon(context, id + 2000))
        return g
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        for (id in widgetIds) Prefs.widgetFiltreSil(context, id)
    }

    /** Kayıtlı süzgeç: "k:Klasör" ya da "e:etiket". */
    class Filtre(val etiket: Boolean, val ad: String) {
        fun kaydi(): String = (if (etiket) "e:" else "k:") + ad

        companion object {
            fun coz(kayit: String?): Filtre? = when {
                kayit == null || kayit.length < 3 -> null
                kayit.startsWith("e:") -> Filtre(true, kayit.substring(2))
                kayit.startsWith("k:") -> Filtre(false, kayit.substring(2))
                else -> null
            }
        }
    }
}

class FiltreWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return NotKartFabrikasi(NotWidget.dilBaglami(applicationContext), R.layout.widget_liste_item) { c ->
            val filtre = FiltreWidget.Filtre.coz(Prefs.widgetFiltre(c, id)) ?: return@NotKartFabrikasi emptyList()
            val depo = NotDeposu(c)
            if (filtre.etiket) depo.etiketliNotlar(filtre.ad) else depo.notlariListele(null, filtre.ad)
        }
    }
}

/**
 * Not başlığı ve özeti çizen ortak fabrika (sabit kartlar, süzgeçli liste).
 * Yerleşimde satirKok, satirBaslik ve satirOzet bulunmalı.
 */
class NotKartFabrikasi(
    private val context: Context,
    private val yerlesim: Int,
    private val yukle: (Context) -> List<Not>
) : RemoteViewsService.RemoteViewsFactory {

    private var notlar: List<Not> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        notlar = try {
            yukle(context).take(50)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        notlar = emptyList()
    }

    override fun getCount(): Int = notlar.size

    override fun getViewAt(pozisyon: Int): RemoteViews {
        val g = RemoteViews(context.packageName, yerlesim)
        val not = notlar.getOrNull(pozisyon) ?: return g
        if (yerlesim == R.layout.widget_sabit_item) WidgetTema.zemin(context, g, R.id.satirKok)
        WidgetTema.metin(context, g, R.id.satirBaslik)
        WidgetTema.ikincil(context, g, R.id.satirOzet)
        g.setTextViewText(R.id.satirBaslik, not.baslik)
        g.setTextViewText(R.id.satirOzet, if (not.kilitli) context.getString(R.string.kilitli) else not.ozet)
        g.setOnClickFillInIntent(R.id.satirKok, WidgetEylemActivity.acDoldurma(not.uri.toString()))
        return g
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false
}

/**
 * Klasör/etiket widget'ı eklenirken (ya da süzgeçsiz widget'a dokununca)
 * süzgeç seçtirir. Şeffaf ekranda alt sayfa: önce klasörler, sonra etiketler.
 */
class FiltreAyarActivity : TemelActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var secildi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || savedInstanceState != null) {
            finish()
            return
        }
        val uygulama = applicationContext
        Thread {
            val depo = NotDeposu(uygulama)
            val klasorler = try {
                depo.klasorAdlari().filter { it != Sablonlar.KLASOR }
            } catch (_: Exception) {
                emptyList()
            }
            val etiketler = try {
                depo.etiketleriListele()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread { if (!isFinishing) goster(klasorler, etiketler) }
        }.start()
    }

    private fun goster(klasorler: List<String>, etiketler: List<String>) {
        val sayfa = AltSayfa(this).baslik(getString(R.string.widget_filtre_sec))
        if (klasorler.isEmpty() && etiketler.isEmpty()) {
            sayfa.mesaj(getString(R.string.widget_filtre_yok))
        }
        for (k in klasorler) {
            sayfa.madde(R.drawable.ic_klasor, k) { sec(FiltreWidget.Filtre(false, k)) }
        }
        for (e in etiketler) {
            sayfa.madde(R.drawable.ic_etiket, "#$e") { sec(FiltreWidget.Filtre(true, e)) }
        }
        if (klasorler.size + etiketler.size > 8) sayfa.aranabilir()
        sayfa.kapaninca { if (!secildi) finish() }.goster()
    }

    private fun sec(filtre: FiltreWidget.Filtre) {
        secildi = true
        Prefs.widgetFiltreKaydet(this, widgetId, filtre.kaydi())
        FiltreWidget().onUpdate(this, AppWidgetManager.getInstance(this), intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
