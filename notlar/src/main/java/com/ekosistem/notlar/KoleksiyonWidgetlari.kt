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
 * Sabitlenmiş notlar: en fazla dört kart, sabitlenme sırasıyla. Kaç kartın
 * sığacağı widget'ın o anki boyutundan hesaplanır ([SabitDuzeni]); kartlar
 * boş yer bırakmadan bütün alanı paylaşır. Önceden iki sütunlu bir liste
 * vardı: tek sabit not sol üstte kalıyor, widget'ın geri kalanı boş
 * duruyordu. Kilitli notun kartında özet yerine "Kilitli" yazar.
 */
class SabitWidget : NotWidgetSaglayici() {

    override val boyutaGore: Boolean = true

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_sabit)
        val sira = Prefs.sabitSirasi(context)
        val notlar = if (sira.isEmpty()) emptyList() else
            NotDeposu(context).notlariListele(null, null)
                .filter { it.sabit }
                .sortedBy { sira.indexOf(it.uri.toString()) }

        if (notlar.isEmpty()) {
            g.setViewVisibility(R.id.widgetIzgara, View.GONE)
            g.setViewVisibility(R.id.widgetBos, View.VISIBLE)
            WidgetTema.zemin(context, g, R.id.widgetBos)
            WidgetTema.ikincil(context, g, R.id.widgetBos)
            g.setTextViewText(R.id.widgetBos, context.getString(R.string.sabit_yok))
            g.setOnClickPendingIntent(
                R.id.widgetBos,
                NotWidget.ekranNiyeti(context, id, Intent(context, MainActivity::class.java))
            )
            return g
        }

        val (genislik, yukseklik) = boyut(context, id)
        val satirlar = SabitDuzeni.sec(notlar.size, genislik, yukseklik)
        val kartYuksekligi =
            (yukseklik - SabitDuzeni.BOSLUK * (satirlar.size - 1)) / satirlar.size

        g.removeAllViews(R.id.widgetIzgara)
        var sonraki = 0
        for (kartSayisi in satirlar) {
            val kartEni = (genislik - SabitDuzeni.BOSLUK * (kartSayisi - 1)) / kartSayisi
            val (baslikSatiri, ozetSatiri) = satirSayilari(context, kartEni, kartYuksekligi)
            val satir = RemoteViews(context.packageName, R.layout.widget_sabit_satir)
            repeat(kartSayisi) {
                satir.addView(R.id.widgetSatir, kart(context, id, notlar[sonraki++], baslikSatiri, ozetSatiri))
            }
            g.addView(R.id.widgetIzgara, satir)
        }
        return g
    }

    private fun kart(context: Context, id: Int, not: Not, baslikSatiri: Int, ozetSatiri: Int): RemoteViews {
        val k = RemoteViews(context.packageName, R.layout.widget_sabit_item)
        WidgetTema.zemin(context, k, R.id.satirKok)
        WidgetTema.metin(context, k, R.id.satirBaslik)
        WidgetTema.ikincil(context, k, R.id.satirOzet)
        k.setTextViewText(R.id.satirBaslik, not.baslik)
        k.setInt(R.id.satirBaslik, "setMaxLines", baslikSatiri)
        if (ozetSatiri > 0) {
            k.setTextViewText(R.id.satirOzet, if (not.kilitli) context.getString(R.string.kilitli) else not.ozet)
            k.setInt(R.id.satirOzet, "setMaxLines", ozetSatiri)
        } else {
            k.setViewVisibility(R.id.satirOzet, View.GONE)
        }
        val adres = not.uri.toString()
        k.setOnClickPendingIntent(R.id.satirKok, NotWidget.ekranNiyeti(context, id, NotWidget.notNiyeti(context, adres)))
        return k
    }

    /**
     * Kartın boyuna sığan başlık ve özet satırı; yarım satır görünmesin.
     * Özete yer yoksa yalnız başlık (en küçük, tek kartlık boyut). Dar kartta
     * başlık tek satır: iki satıra bölününce kelimenin ortasından kırılıyordu.
     */
    private fun satirSayilari(context: Context, kartEni: Int, kartYuksekligi: Int): Pair<Int, Int> {
        val olcek = context.resources.configuration.fontScale
        val baslik = 19f * olcek
        val ozet = 16f * olcek
        val ic = kartYuksekligi - 24f - 2f
        val enCokBaslik = if (kartEni < DAR_KART) 1 else 2
        if (ic < baslik + ozet) return (ic / baslik).toInt().coerceIn(1, enCokBaslik) to 0
        val baslikSatiri = if (enCokBaslik == 2 && ic >= 2 * baslik + 2 * ozet) 2 else 1
        return baslikSatiri to ((ic - baslikSatiri * baslik) / ozet).toInt()
    }

    /**
     * Widget'ın dp cinsinden boyutu. Dikey ekranda genişlik en küçük, yükseklik
     * en büyük değerdir (başlatıcıların ortak kuralı). Bildirmeyen başlatıcıda
     * yerleşimdeki ilk boyut.
     */
    private fun boyut(context: Context, id: Int): Pair<Int, Int> {
        val s = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val g = s.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val y = s.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (g > 0 && y > 0) g to y else 180 to 110
    }

    private companion object {
        /** Bundan dar kartta başlık tek satır (dp). */
        const val DAR_KART = 140
    }
}

/** Sabitlenmiş notlar widget'ında kartların dizilişi (saf mantık, test edilir). */
object SabitDuzeni {
    const val EN_COK = 4
    const val BOSLUK = 8
    /** Bundan küçük kartta başlık okunmuyor. */
    private const val EN_DAR = 100
    private const val EN_KISA = 56
    /** Kartın en iyi göründüğü en/boy oranı. */
    private const val IDEAL_ORAN = 1.6

    /** Bu boyuttaki widget'a sığan kart sayısı (en az 1, en çok [EN_COK]). */
    fun kapasite(genislik: Int, yukseklik: Int): Int =
        minOf(EN_COK, sutunSiniri(genislik) * satirSiniri(yukseklik))

    /**
     * Satır başına kart sayıları. Gösterilecek not sayısı kapasiteyle sınırlı;
     * satır sayısı, kartlar ideal orana en yakın olacak şekilde seçilir.
     * Kartlar satırlara dengeli dağılır; azı alttaki satırlarda kalır ve
     * genişleyerek satırı doldurur (3 not: üstte 2, altta 1 geniş kart).
     */
    fun sec(notSayisi: Int, genislik: Int, yukseklik: Int): List<Int> {
        val n = minOf(notSayisi, kapasite(genislik, yukseklik))
        if (n <= 0) return emptyList()
        var enIyi = listOf(n)
        var enIyiPuan = Double.MAX_VALUE
        for (r in 1..minOf(n, satirSiniri(yukseklik))) {
            val satirlar = List(r) { i -> n / r + if (i < n % r) 1 else 0 }
            if (satirlar.first() > sutunSiniri(genislik)) continue
            val kartBoyu = (yukseklik - BOSLUK * (r - 1)).toDouble() / r
            val puan = satirlar.sumOf { c ->
                val kartEni = (genislik - BOSLUK * (c - 1)).toDouble() / c
                c * kotuluk(kartEni / kartBoyu)
            } / n
            if (puan < enIyiPuan) {
                enIyiPuan = puan
                enIyi = satirlar
            }
        }
        return enIyi
    }

    private fun kotuluk(oran: Double): Double = kotlin.math.abs(kotlin.math.ln(oran / IDEAL_ORAN))

    private fun sutunSiniri(genislik: Int): Int = ((genislik + BOSLUK) / (EN_DAR + BOSLUK)).coerceAtLeast(1)

    private fun satirSiniri(yukseklik: Int): Int = ((yukseklik + BOSLUK) / (EN_KISA + BOSLUK)).coerceAtLeast(1)
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
 * Not başlığı ve özeti çizen liste fabrikası (süzgeçli liste).
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
