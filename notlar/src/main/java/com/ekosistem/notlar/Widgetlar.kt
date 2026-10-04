package com.ekosistem.notlar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Ana ekran widget'ları için ortak yardımcılar. */
object NotWidget {

    fun bayrak(degistirilebilir: Boolean): Int {
        val temel = PendingIntent.FLAG_UPDATE_CURRENT
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && degistirilebilir ->
                temel or PendingIntent.FLAG_MUTABLE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                temel or PendingIntent.FLAG_IMMUTABLE
            else -> temel
        }
    }

    /**
     * Widget'taki ikonu vurgu rengine boyar. "Sistemle aynı" seçiliyse renk
     * kaynak olarak verilir ve başlatıcıda çözülür: duvar kağıdı değişince
     * widget da uygulama açılmadan yeni tonu alır.
     */
    fun vurguyaBoya(context: Context, gorunum: RemoteViews, gorunumId: Int, uzeri: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Renkler.sistemSecili(context)) {
            val kaynak = if (uzeri) R.color.vurgu_sistem_uzeri else R.color.vurgu_sistem
            gorunum.setColor(gorunumId, "setColorFilter", kaynak)
        } else {
            val renk = if (uzeri) Renkler.vurguUzeri(context) else Renkler.vurgu(context)
            gorunum.setInt(gorunumId, "setColorFilter", renk)
        }
    }

    /**
     * Vurgu rengi değişince widget'lar yeniden çizilir. [hepsiniGuncelle] yalnızca
     * içeriği tazeler; hızlı not widget'ı ve listenin "+" düğmesi orada boyanmaz.
     */
    fun renkleriGuncelle(context: Context) {
        val yonetici = AppWidgetManager.getInstance(context) ?: return
        try {
            // Tek not widget'ında vurgu yok; onu yeniden çizmek boşuna disk okur.
            val saglayicilar = listOf(HizliNotWidget(), ListeWidget())
            for (saglayici in saglayicilar) {
                val ids = yonetici.getAppWidgetIds(ComponentName(context, saglayici::class.java))
                if (ids != null && ids.isNotEmpty()) saglayici.onUpdate(context, yonetici, ids)
            }
        } catch (_: Exception) {
        }
    }

    /** Not kaydedildikten/silindikten sonra tüm widget'ları tazeler. */
    fun hepsiniGuncelle(context: Context) {
        val yonetici = AppWidgetManager.getInstance(context) ?: return
        try {
            val tekIds = yonetici.getAppWidgetIds(
                ComponentName(context, TekNotWidget::class.java)
            )
            if (tekIds != null && tekIds.isNotEmpty()) {
                TekNotWidget().onUpdate(context, yonetici, tekIds)
            }
            val listeIds = yonetici.getAppWidgetIds(
                ComponentName(context, ListeWidget::class.java)
            )
            if (listeIds != null && listeIds.isNotEmpty()) {
                yonetici.notifyAppWidgetViewDataChanged(listeIds, R.id.widgetListe)
            }
        } catch (_: Exception) {
        }
    }
}

/** Dokununca boş bir not açan küçük widget. */
class HizliNotWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        yonetici: AppWidgetManager,
        widgetIds: IntArray
    ) {
        for (id in widgetIds) {
            val gorunum = RemoteViews(context.packageName, R.layout.widget_hizli_not)
            NotWidget.vurguyaBoya(context, gorunum, R.id.widgetDaire)
            NotWidget.vurguyaBoya(context, gorunum, R.id.widgetArti, uzeri = true)
            val niyet = Intent(context, EditorActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            gorunum.setOnClickPendingIntent(
                R.id.widgetKok,
                PendingIntent.getActivity(context, id, niyet, NotWidget.bayrak(false))
            )
            yonetici.updateAppWidget(id, gorunum)
        }
    }
}

/** Seçilen tek bir notu ana ekranda gösterir. */
class TekNotWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        yonetici: AppWidgetManager,
        widgetIds: IntArray
    ) {
        val depo = NotDeposu(context)
        for (id in widgetIds) {
            val gorunum = RemoteViews(context.packageName, R.layout.widget_tek_not)
            val adres = Prefs.widgetNotu(context, id)
            if (adres == null) {
                gorunum.setTextViewText(R.id.widgetBaslik, context.getString(R.string.widget_bos))
                gorunum.setTextViewText(R.id.widgetIcerik, "")
                yonetici.updateAppWidget(id, gorunum)
                continue
            }
            val uri = Uri.parse(adres)
            val icerik = depo.oku(uri, 2048)
            val satirlar = icerik.lines()
            val ilk = satirlar.indexOfFirst { it.isNotBlank() }
            val baslik = if (ilk >= 0) temizle(satirlar[ilk]) else context.getString(R.string.widget_bos)
            // Kilitli notun gövdesi ana ekranda gösterilmez.
            val govde = when {
                Kilit.notKilitli(context, adres) -> context.getString(R.string.kilitli)
                ilk >= 0 -> satirlar.drop(ilk + 1)
                    .filterNot { Sifreleme.veriSatiriMi(it) }
                    .joinToString("\n") { temizle(it) }.trim()
                else -> ""
            }
            gorunum.setTextViewText(R.id.widgetBaslik, baslik)
            gorunum.setTextViewText(R.id.widgetIcerik, govde)
            val niyet = Intent(context, EditorActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra("uri", adres)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            gorunum.setOnClickPendingIntent(
                R.id.widgetKok,
                PendingIntent.getActivity(context, id, niyet, NotWidget.bayrak(false))
            )
            yonetici.updateAppWidget(id, gorunum)
        }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        for (id in widgetIds) Prefs.widgetNotuSil(context, id)
    }

    private fun temizle(satir: String): String =
        satir.trim()
            .trimStart('#', '>', ' ')
            .replace("- [ ]", "☐")
            .replace("- [x]", "☑")
            .replace("- [X]", "☑")
            .replace("**", "")
            .replace("`", "")
}

/** Kaydırılabilir not listesi widget'ı. */
class ListeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        yonetici: AppWidgetManager,
        widgetIds: IntArray
    ) {
        for (id in widgetIds) {
            val gorunum = RemoteViews(context.packageName, R.layout.widget_liste)
            NotWidget.vurguyaBoya(context, gorunum, R.id.widgetEkle)

            val servis = Intent(context, ListeWidgetServisi::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            gorunum.setRemoteAdapter(R.id.widgetListe, servis)
            gorunum.setEmptyView(R.id.widgetListe, R.id.widgetBos)

            val yeniNiyet = Intent(context, EditorActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            gorunum.setOnClickPendingIntent(
                R.id.widgetEkle,
                PendingIntent.getActivity(context, id, yeniNiyet, NotWidget.bayrak(false))
            )

            val kalip = Intent(context, EditorActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            gorunum.setPendingIntentTemplate(
                R.id.widgetListe,
                PendingIntent.getActivity(context, id + 1000, kalip, NotWidget.bayrak(true))
            )

            yonetici.updateAppWidget(id, gorunum)
            yonetici.notifyAppWidgetViewDataChanged(id, R.id.widgetListe)
        }
    }
}

class ListeWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListeFabrikasi(applicationContext)
}

class ListeFabrikasi(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notlar: List<Not> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        notlar = try {
            NotDeposu(context).notlariListele(null, null).take(50)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        notlar = emptyList()
    }

    override fun getCount(): Int = notlar.size

    override fun getViewAt(pozisyon: Int): RemoteViews {
        val gorunum = RemoteViews(context.packageName, R.layout.widget_liste_item)
        val not = notlar.getOrNull(pozisyon) ?: return gorunum
        gorunum.setTextViewText(R.id.satirBaslik, not.baslik)
        gorunum.setTextViewText(R.id.satirOzet, not.ozet)
        gorunum.setOnClickFillInIntent(
            R.id.satirKok,
            Intent().putExtra("uri", not.uri.toString()).setData(not.uri)
        )
        return gorunum
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false
}
