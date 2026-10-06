package com.ekosistem.notlar

import android.app.AlarmManager
import android.app.LocaleManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Ana ekran widget'ları için ortak yardımcılar. */
object NotWidget {

    /**
     * Widget çizimleri tek arka plan iş parçacığında sırayla yapılır: Görevler
     * gibi bütün notları tarayan widget'lar ana iş parçacığını tutmasın.
     */
    val isci: ExecutorService = Executors.newSingleThreadExecutor()

    /** Art arda değişiklikler bu süre içinde tek widget güncellemesinde birleşir. */
    private const val GUNCELLEME_GECIKMESI = 500L

    /**
     * Liste widget'larına "veri değişti" bir kez daha, biraz sonra söylenir:
     * bazı başlatıcılar yeniden çizimle aynı anda gelen bildirimi kaçırıyor.
     */
    const val IKINCI_BILDIRIM = 1200L

    /** Bütün widget sağlayıcıları; yeni widget eklenince buraya da yazılır. */
    private val SAGLAYICILAR: List<() -> NotWidgetSaglayici> = listOf(
        ::HizliNotWidget,
        ::TekNotWidget,
        ::ListeWidget,
        ::GorevlerWidget,
        ::BugunWidget,
        ::EylemlerWidget,
        ::SabitWidget,
        ::FiltreWidget,
        ::YaklasanlarWidget,
        ::GecmisWidget
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

    /**
     * Uygulamaya özel dil (Android 13+, Ayarlar → Uygulama dili) widget
     * metinlerine de uygulanır. Süreçte henüz bir ekran açılmadıysa servisin
     * bağlamı sistem dilinde kalıyor, "2 açık" ile "1 day overdue" yan yana
     * çıkıyordu.
     */
    fun dilBaglami(context: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return context
        val diller = context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales ?: return context
        if (diller.isEmpty || context.resources.configuration.locales == diller) return context
        val ayar = Configuration(context.resources.configuration)
        ayar.setLocales(diller)
        return context.createConfigurationContext(ayar)
    }

    /** Tema ya da vurgu rengi değişince bütün widget'lar yeniden çizilir. */
    fun renkleriGuncelle(context: Context) = hepsiniGuncelle(context)

    private val ana = android.os.Handler(android.os.Looper.getMainLooper())
    private var bekleyenGuncelleme: Runnable? = null

    /**
     * Notlar değişti (yazma, oluşturma, taşıma, silme, klasör adı): widget'lar
     * yarım saniye sonra bir kez tazelenir; art arda gelen değişiklikler tek
     * güncellemede birleşir. [NotDeposu] her yazmada çağırır: önceden yalnız
     * bazı ekranlar tazeliyordu, listeden taşınan not klasör widget'ında
     * görünmüyordu.
     */
    fun birazdanGuncelle(context: Context) {
        val uygulama = context.applicationContext
        ana.post {
            bekleyenGuncelleme?.let { ana.removeCallbacks(it) }
            val guncelleme = Runnable {
                bekleyenGuncelleme = null
                hepsiniGuncelle(uygulama)
            }
            bekleyenGuncelleme = guncelleme
            ana.postDelayed(guncelleme, GUNCELLEME_GECIKMESI)
        }
    }

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

    /**
     * İçeriği güne bağlı widget (Bugünün notu, Geçmişte bugün): her çizimden
     * sonra gece yarısına bir yenileme kurulur. Sistemin 30 dakikalık
     * güncellemesi tarihi yarım saate kadar geç çevirebiliyordu.
     */
    protected open val gunlukYenile: Boolean = false

    /** Yerleşimi widget'ın boyutuna göre kurulan widget (Sabitlenmiş notlar): yeniden boyutlanınca çizilir. */
    protected open val boyutaGore: Boolean = false

    abstract fun ciz(context: Context, id: Int): RemoteViews

    override fun onAppWidgetOptionsChanged(
        context: Context,
        yonetici: AppWidgetManager,
        id: Int,
        secenekler: android.os.Bundle?
    ) {
        if (boyutaGore) onUpdate(context, yonetici, intArrayOf(id))
    }

    final override fun onUpdate(context: Context, yonetici: AppWidgetManager, widgetIds: IntArray) {
        val bekleyen = goAsync()
        val uygulama = NotWidget.dilBaglami(context.applicationContext)
        NotWidget.isci.execute {
            try {
                for (id in widgetIds) {
                    try {
                        yonetici.updateAppWidget(id, ciz(uygulama, id))
                        listeId?.let { yonetici.notifyAppWidgetViewDataChanged(id, it) }
                    } catch (_: Exception) {
                    }
                }
                if (gunlukYenile) geceYarisinaKur(uygulama, yonetici)
                listeId?.let { liste ->
                    // Yeni not listede görünmüyordu: bildirim çizimle yarışıp kayboluyordu.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        for (id in widgetIds) runCatching { yonetici.notifyAppWidgetViewDataChanged(id, liste) }
                    }, NotWidget.IKINCI_BILDIRIM)
                }
            } finally {
                bekleyen?.finish()
            }
        }
    }

    /** Bu sağlayıcının bütün widget'ları için ertesi gece yarısı (+1 dk) güncelleme. */
    private fun geceYarisinaKur(context: Context, yonetici: AppWidgetManager) {
        val ids = yonetici.getAppWidgetIds(ComponentName(context, this::class.java)) ?: return
        if (ids.isEmpty()) return
        val niyet = Intent(context, this::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        val bekleyen = PendingIntent.getBroadcast(context, 0, niyet, NotWidget.bayrak(false))
        val takvim = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
        }
        // Kesin alarm izni gerekmez: birkaç dakika gecikmesi sorun değil.
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.set(AlarmManager.RTC, takvim.timeInMillis, bekleyen)
    }
}

/** Dokununca boş bir not açan küçük widget. */
class HizliNotWidget : NotWidgetSaglayici() {

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_hizli_not)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.ikincil(context, g, R.id.widgetEtiket)
        // Sabit yazılar da koddan: yerleşimdekini başlatıcı sistem diliyle çözüyor.
        g.setTextViewText(R.id.widgetEtiket, context.getString(R.string.widget_hizli_not))
        NotWidget.vurguyaBoya(context, g, R.id.widgetDaire)
        NotWidget.vurguyaBoya(context, g, R.id.widgetArti, uzeri = true)
        g.setOnClickPendingIntent(
            R.id.widgetKok,
            NotWidget.ekranNiyeti(context, id, NotWidget.yeniNotNiyeti(context))
        )
        return g
    }
}

/**
 * Seçilen tek bir notu ana ekranda gösterir. Satırlar liste olarak çizilir:
 * görevlerin kutusu widget'tan işaretlenebilir (Görevler widget'ıyla aynı yol).
 */
class TekNotWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_tek_not)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        val adres = Prefs.widgetNotu(context, id)
        if (adres == null) {
            // Not seçilmemiş (ayar ekranı yarıda kaldı ya da not silindi): dokununca seçtirilir.
            g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.widget_not_sec))
            g.setTextViewText(R.id.widgetBos, "")
            val sec = NotWidget.ekranNiyeti(
                context,
                id,
                Intent(context, WidgetAyarActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("notlar-widget://sec/$id"))
            )
            g.setOnClickPendingIntent(R.id.widgetBaslik, sec)
            g.setOnClickPendingIntent(R.id.widgetBos, sec)
            g.setOnClickPendingIntent(R.id.widgetKok, sec)
            return g
        }
        val satirlar = NotDeposu(context).oku(Uri.parse(adres), TekNotFabrikasi.OKUMA_SINIRI).lines()
        val ilk = satirlar.indexOfFirst { it.isNotBlank() }
        g.setTextViewText(
            R.id.widgetBaslik,
            if (ilk >= 0) NotOnizleme.sade(satirlar[ilk]) else context.getString(R.string.widget_bos)
        )
        // Kilitli notun gövdesi ana ekranda gösterilmez; liste boş kalır, yerine "Kilitli" yazar.
        g.setTextViewText(
            R.id.widgetBos,
            if (Kilit.notKilitli(context, adres)) context.getString(R.string.kilitli) else ""
        )
        val acici = NotWidget.ekranNiyeti(context, id, NotWidget.notNiyeti(context, adres))
        g.setOnClickPendingIntent(R.id.widgetBaslik, acici)
        g.setOnClickPendingIntent(R.id.widgetBos, acici)

        val servis = Intent(context, TekNotWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetListe, servis)
        g.setEmptyView(R.id.widgetListe, R.id.widgetBos)
        g.setPendingIntentTemplate(R.id.widgetListe, WidgetEylemActivity.sablon(context, id + 2000))
        return g
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        for (id in widgetIds) Prefs.widgetNotuSil(context, id)
    }
}

class TekNotWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TekNotFabrikasi(
            NotWidget.dilBaglami(applicationContext),
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
}

/** Notun başlıktan sonraki satırları; görev satırı kutulu, diğerleri düz metin. */
class TekNotFabrikasi(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    /** [kutu]: satır bir Obsidian kutusunun parçası; zemini o kutunun renginde (editördeki gibi). */
    private class Satir(val metin: CharSequence, val gorev: Gorev?, val kutu: Int? = null)

    private var adres: String? = null
    private var satirlar: List<Satir> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val a = Prefs.widgetNotu(context, widgetId)
        adres = a
        satirlar = if (a == null || Kilit.notKilitli(context, a)) emptyList() else try {
            oku(a)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun oku(a: String): List<Satir> {
        val uri = Uri.parse(a)
        val hepsi = NotDeposu(context).oku(uri, OKUMA_SINIRI).lines()
        val ilk = hepsi.indexOfFirst { it.isNotBlank() }
        if (ilk < 0) return emptyList()
        val sonuc = mutableListOf<Satir>()
        // Satırlar uygulamadaki gibi: başlık, kalın, bağlantı, renkli kutu (NotOnizleme).
        val stil = WidgetTema.stil(context)
        var kutuRengi: Int? = null
        var kodda = false
        var oncekiBos = true
        for (no in ilk + 1 until hepsi.size) {
            val ham = hepsi[no]
            if (Sifreleme.veriSatiriMi(ham)) continue
            // Art arda boş satırlar teke iner; widget'ta yer boşa gitmesin.
            if (ham.isBlank()) {
                if (!oncekiBos) sonuc.add(Satir("", null))
                oncekiBos = true
                kutuRengi = null
                continue
            }
            oncekiBos = false
            // Kod bloğu olduğu gibi, eş aralıklı; çit satırı (```) görünmez.
            if (MarkdownBicimci.KOD_CITI.containsMatchIn(ham)) {
                kodda = !kodda
                continue
            }
            if (kodda) {
                val kod = SpannableString(ham)
                kod.setSpan(android.text.style.TypefaceSpan("monospace"), 0, ham.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sonuc.add(Satir(kod, null))
                continue
            }
            val onay = MarkdownBicimci.ONAY.find(ham)
            if (onay != null) {
                kutuRengi = null
                val isaretli = !onay.groupValues[2].equals(" ", true)
                val metin = SonTarih.temizle(ham.substring(onay.value.length))
                val gorev = Gorev(uri, "", no, metin, isaretli)
                val gosterilen = SpannableStringBuilder(NotOnizleme.bicimli(metin, stil))
                if (isaretli) gosterilen.setSpan(StrikethroughSpan(), 0, gosterilen.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sonuc.add(Satir(gosterilen, gorev))
            } else {
                val cozulen = NotOnizleme.coz(ham)
                // Kutu başlığından sonraki "> " satırları kutunun gövdesi: çizgi kutunun renginde.
                cozulen.kutuTuru?.let { kutuRengi = stil.kutuRengi(it) }
                if (cozulen.kutuTuru == null && !cozulen.alinti) kutuRengi = null
                val kutu = if (cozulen.kutuTuru != null || cozulen.alinti) kutuRengi else null
                var gosterilen = NotOnizleme.bicimli(cozulen, stil, if (cozulen.alinti) kutuRengi else null)
                // Kutu başlığının solunda da gövdedeki renkli çizgi olsun.
                if (cozulen.kutuTuru != null && kutu != null) {
                    gosterilen = SpannableStringBuilder("▍ ").apply {
                        setSpan(android.text.style.ForegroundColorSpan(kutu), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }.append(gosterilen)
                }
                if (gosterilen.isEmpty()) continue
                sonuc.add(Satir(gosterilen, null, kutu))
            }
            if (sonuc.size >= EN_FAZLA_SATIR) break
        }
        return sonuc
    }

    override fun onDestroy() {
        satirlar = emptyList()
    }

    override fun getCount(): Int = satirlar.size

    /**
     * Her satır türünün kendi yerleşimi var: başlatıcı satırları yeniden
     * kullanırken bir türün ayarı (gizli kutu, soluk renk) ötekine taşınmasın.
     */
    override fun getViewAt(pozisyon: Int): RemoteViews {
        val satir = satirlar.getOrNull(pozisyon)
        val gorev = satir?.gorev
        val yerlesim = when {
            gorev == null -> R.layout.widget_not_satir
            gorev.isaretli -> R.layout.widget_gorev_item_isaretli
            else -> R.layout.widget_gorev_item
        }
        val g = RemoteViews(context.packageName, yerlesim)
        val a = adres
        if (satir == null || a == null) return g
        g.setTextViewText(R.id.satirMetin, satir.metin)
        if (gorev == null) {
            // Kutu satırları kutunun renginin açık tonunda (editördeki zemin); başlatıcı
            // satırı yeniden kullandığında öteki satırlara taşınmasın diye her seferinde.
            val zemin = satir.kutu?.let { (it and 0x00FFFFFF) or 0x24000000 } ?: 0
            g.setInt(R.id.satirKok, "setBackgroundColor", zemin)
        }
        when {
            // Not gövdesi uygulamadaki gibi ana yazı renginde (önceden griydi).
            gorev == null -> WidgetTema.metin(context, g, R.id.satirMetin)
            gorev.isaretli -> {
                WidgetTema.ikincil(context, g, R.id.satirMetin)
                NotWidget.vurguyaBoya(context, g, R.id.satirKutu)
            }
            else -> {
                WidgetTema.metin(context, g, R.id.satirMetin)
                WidgetTema.ikincilIkon(context, g, R.id.satirKutu)
            }
        }
        if (gorev != null) g.setOnClickFillInIntent(R.id.satirKutu, WidgetEylemActivity.gorevDoldurma(gorev))
        g.setOnClickFillInIntent(R.id.satirKok, WidgetEylemActivity.acDoldurma(a))
        return g
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false

    companion object {
        const val OKUMA_SINIRI = 8192
        private const val EN_FAZLA_SATIR = 80
    }
}

/** Kaydırılabilir not listesi widget'ı. */
class ListeWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_liste)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.app_name))
        g.setTextViewText(R.id.widgetBos, context.getString(R.string.widget_bos))
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
        ListeFabrikasi(NotWidget.dilBaglami(applicationContext))
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
