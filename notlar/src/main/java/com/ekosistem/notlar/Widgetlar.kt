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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Ana ekran widget'ları için ortak yardımcılar. */
object NotWidget {

    /**
     * Widget çizimleri tek arka plan iş parçacığında sırayla yapılır: Görevler
     * gibi bütün notları tarayan widget'lar ana iş parçacığını tutmasın.
     */
    val isci: ExecutorService = Executors.newSingleThreadExecutor()

    /** Bütün widget sağlayıcıları; yeni widget eklenince buraya da yazılır. */
    private val SAGLAYICILAR: List<() -> NotWidgetSaglayici> = listOf(
        ::HizliNotWidget,
        ::TekNotWidget,
        ::ListeWidget
    )

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
            val renk = WidgetTema.vurgu(context)
            gorunum.setInt(gorunumId, "setColorFilter", if (uzeri) Renkler.uzeriRengi(renk) else renk)
        }
    }

    /** Vurgu rengi metinde (sayaç, "+ Yaz" gibi). */
    fun vurguMetni(context: Context, gorunum: RemoteViews, gorunumId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Renkler.sistemSecili(context)) {
            gorunum.setColorStateList(gorunumId, "setTextColor", R.color.vurgu_sistem)
        } else {
            gorunum.setTextColor(gorunumId, WidgetTema.vurgu(context))
        }
    }

    /** Tema ya da vurgu rengi değişince bütün widget'lar yeniden çizilir. */
    fun renkleriGuncelle(context: Context) = hepsiniGuncelle(context)

    /** Not kaydedildikten/silindikten, tema ya da sabitleme değiştikten sonra. */
    fun hepsiniGuncelle(context: Context) {
        val uygulama = context.applicationContext
        val yonetici = AppWidgetManager.getInstance(uygulama) ?: return
        for (yeni in SAGLAYICILAR) {
            try {
                val saglayici = yeni()
                val ids = yonetici.getAppWidgetIds(ComponentName(uygulama, saglayici::class.java))
                if (ids != null && ids.isNotEmpty()) saglayici.onUpdate(uygulama, yonetici, ids)
            } catch (_: Exception) {
            }
        }
    }

    /** Uygulama içinde bir ekranı açan, widget'a bağlanacak niyet. */
    fun ekranNiyeti(context: Context, istek: Int, niyet: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            istek,
            niyet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            bayrak(false)
        )

    /** Var olan bir notu editörde açan niyet. */
    fun notNiyeti(context: Context, adres: String): Intent =
        Intent(context, EditorActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra("uri", adres)
            .setData(Uri.parse(adres))

    /** Boş bir not açan niyet. */
    fun yeniNotNiyeti(context: Context): Intent =
        Intent(context, EditorActivity::class.java).setAction(Intent.ACTION_MAIN)
}

/**
 * Bütün widget'ların atası. Çizim arka planda yapılır; sistem güncelleme
 * isteğiyle çağrıldıysa `goAsync` ile yayın açık tutulur. Uygulama içinden
 * doğrudan çağrıldığında `goAsync` null döner, iş yine arka planda yürür.
 */
abstract class NotWidgetSaglayici : AppWidgetProvider() {

    /** Koleksiyon widget'ının liste görünümü; varsa çizimden sonra içerik tazelenir. */
    protected open val listeId: Int? = null

    abstract fun ciz(context: Context, id: Int): RemoteViews

    final override fun onUpdate(context: Context, yonetici: AppWidgetManager, widgetIds: IntArray) {
        val bekleyen = goAsync()
        val uygulama = context.applicationContext
        NotWidget.isci.execute {
            try {
                for (id in widgetIds) {
                    try {
                        yonetici.updateAppWidget(id, ciz(uygulama, id))
                        listeId?.let { yonetici.notifyAppWidgetViewDataChanged(id, it) }
                    } catch (_: Exception) {
                    }
                }
            } finally {
                bekleyen?.finish()
            }
        }
    }
}

/** Dokununca boş bir not açan küçük widget. */
class HizliNotWidget : NotWidgetSaglayici() {

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_hizli_not)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.ikincil(context, g, R.id.widgetEtiket)
        NotWidget.vurguyaBoya(context, g, R.id.widgetDaire)
        NotWidget.vurguyaBoya(context, g, R.id.widgetArti, uzeri = true)
        g.setOnClickPendingIntent(
            R.id.widgetKok,
            NotWidget.ekranNiyeti(context, id, NotWidget.yeniNotNiyeti(context))
        )
        return g
    }
}

/** Seçilen tek bir notu ana ekranda gösterir. */
class TekNotWidget : NotWidgetSaglayici() {

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_tek_not)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetIcerik)
        val adres = Prefs.widgetNotu(context, id)
        if (adres == null) {
            g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.widget_bos))
            g.setTextViewText(R.id.widgetIcerik, "")
            return g
        }
        val icerik = NotDeposu(context).oku(Uri.parse(adres), 2048)
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
        g.setTextViewText(R.id.widgetBaslik, baslik)
        g.setTextViewText(R.id.widgetIcerik, govde)
        g.setOnClickPendingIntent(
            R.id.widgetKok,
            NotWidget.ekranNiyeti(context, id, NotWidget.notNiyeti(context, adres))
        )
        return g
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
class ListeWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_liste)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        NotWidget.vurguyaBoya(context, g, R.id.widgetEkle)

        val servis = Intent(context, ListeWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetListe, servis)
        g.setEmptyView(R.id.widgetListe, R.id.widgetBos)

        g.setOnClickPendingIntent(
            R.id.widgetEkle,
            NotWidget.ekranNiyeti(context, id, NotWidget.yeniNotNiyeti(context))
        )
        val kalip = Intent(context, EditorActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        g.setPendingIntentTemplate(
            R.id.widgetListe,
            PendingIntent.getActivity(context, id + 1000, kalip, NotWidget.bayrak(true))
        )
        return g
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
        val g = RemoteViews(context.packageName, R.layout.widget_liste_item)
        val not = notlar.getOrNull(pozisyon) ?: return g
        WidgetTema.metin(context, g, R.id.satirBaslik)
        WidgetTema.ikincil(context, g, R.id.satirOzet)
        g.setTextViewText(R.id.satirBaslik, not.baslik)
        g.setTextViewText(R.id.satirOzet, not.ozet)
        g.setOnClickFillInIntent(
            R.id.satirKok,
            Intent().putExtra("uri", not.uri.toString()).setData(not.uri)
        )
        return g
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false
}
