package com.ekosistem.notlar

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Yaklaşanlar: hatırlatıcılar ve son tarihli görevler (gecikmişler ve önümüzdeki
 * iki hafta) zaman sırasıyla. Kilitli notun hatırlatıcısında başlık yerine
 * "Kilitli not" yazar (bildirimdeki gibi). Gece yarısı "Bugün/Yarın" yenilenir.
 */
class YaklasanlarWidget : NotWidgetSaglayici() {

    override val listeId: Int = R.id.widgetListe
    override val gunlukYenile: Boolean = true

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_yaklasanlar)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetBos)
        g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.yaklasanlar))
        g.setTextViewText(R.id.widgetBos, context.getString(R.string.yaklasan_yok))
        g.setOnClickPendingIntent(
            R.id.widgetBaslik,
            NotWidget.ekranNiyeti(context, id, Intent(context, GorevlerActivity::class.java))
        )
        val servis = Intent(context, YaklasanlarWidgetServisi::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        servis.data = Uri.parse(servis.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        g.setRemoteAdapter(R.id.widgetListe, servis)
        g.setEmptyView(R.id.widgetListe, R.id.widgetBos)
        g.setPendingIntentTemplate(R.id.widgetListe, WidgetEylemActivity.sablon(context, id + 2000))
        return g
    }
}

class YaklasanlarWidgetServisi : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        YaklasanlarFabrikasi(NotWidget.dilBaglami(applicationContext))
}

class YaklasanlarFabrikasi(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private class Yaklasan(
        /** Sıralama anı: hatırlatıcıda kendi zamanı, görevde gününün sonu. */
        val sira: Long,
        val ikon: Int,
        val etiket: String,
        val renk: Int,
        val metin: String,
        val adres: String
    )

    private var satirlar: List<Yaklasan> = emptyList()

    /** Görev metni uygulamadaki gibi biçimli; tema değişince tazelenir. */
    private var stil = WidgetTema.stil(context)
    private val etiketci = TarihEtiketi(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        stil = WidgetTema.stil(context)
        satirlar = try {
            topla()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun topla(): List<Yaklasan> {
        etiketci.tazele()
        val bugun = etiketci.bugun
        val simdi = System.currentTimeMillis()
        val depo = NotDeposu(context)
        val notlar = depo.notlariListele(null, null).associateBy { it.uri.toString() }
        val ikincil = WidgetTema.palet(context)?.ikincil ?: ContextCompat.getColor(context, R.color.metin_ikincil)
        val vurgu = WidgetTema.vurgu(context)
        val saat = DateFormat.getTimeFormat(context)
        val sonuc = mutableListOf<Yaklasan>()

        for ((adres, zaman) in Prefs.tumHatirlaticilar(context)) {
            // Çalmış tek seferlik hatırlatıcı ya da çöpteki/silinmiş not gösterilmez.
            if (zaman < simdi - 5 * 60_000L) continue
            val not = notlar[adres] ?: continue
            val gun = SonTarih.gunu(zaman)
            sonuc.add(
                Yaklasan(
                    sira = zaman,
                    ikon = R.drawable.ic_hatirlatici,
                    etiket = gunAdi(gun, bugun) + " " + saat.format(Date(zaman)),
                    renk = if (gun == bugun) vurgu else ikincil,
                    metin = if (not.kilitli) context.getString(R.string.kilitli_not) else not.baslik,
                    adres = adres
                )
            )
        }

        val gecemiz = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        for (gorev in depo.gorevleriListele(false)) {
            val gun = gorev.sonGun ?: continue
            val fark = etiketci.fark(gun)
            if (fark > ILERI_GUN) continue
            sonuc.add(
                Yaklasan(
                    // Günün sonuna konur: aynı günün saatli hatırlatıcıları önce gelsin.
                    sira = gecemiz + (fark + 1) * GUN_MS - 1,
                    ikon = R.drawable.ic_gunluk,
                    etiket = etiketci.etiket(gun),
                    renk = when {
                        fark < 0 -> WidgetTema.gecikmis(context)
                        fark == 0 -> vurgu
                        else -> ikincil
                    },
                    metin = gorev.metin,
                    adres = gorev.notUri.toString()
                )
            )
        }
        return sonuc.sortedBy { it.sira }.take(EN_FAZLA)
    }

    /** Hatırlatıcının günü: "Bugün", "Yarın", hafta içinde gün adı, sonra tarih. */
    private fun gunAdi(gun: Long, bugun: Long): String {
        val fark = (gun - bugun).toInt()
        if (fark !in 2..6) return etiketci.etiket(gun)
        val dil = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val (y, a, g) = SonTarih.tarih(gun)
        val t = Calendar.getInstance().apply {
            clear()
            set(y, a - 1, g)
        }
        return SimpleDateFormat("EEEE", dil).format(t.time)
    }

    override fun onDestroy() {
        satirlar = emptyList()
    }

    override fun getCount(): Int = satirlar.size

    override fun getViewAt(pozisyon: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_yaklasan_item)
        val s = satirlar.getOrNull(pozisyon) ?: return g
        WidgetTema.metin(context, g, R.id.satirMetin)
        g.setImageViewResource(R.id.satirIkon, s.ikon)
        g.setInt(R.id.satirIkon, "setColorFilter", s.renk)
        g.setTextColor(R.id.satirAlt, s.renk)
        g.setTextViewText(R.id.satirAlt, s.etiket)
        g.setTextViewText(R.id.satirMetin, NotOnizleme.bicimli(s.metin, stil))
        g.setOnClickFillInIntent(R.id.satirKok, WidgetEylemActivity.acDoldurma(s.adres))
        return g
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(pozisyon: Int): Long = pozisyon.toLong()

    override fun hasStableIds(): Boolean = false

    private companion object {
        const val EN_FAZLA = 30
        const val ILERI_GUN = 14
        const val GUN_MS = 24 * 60 * 60_000L
    }
}

/**
 * Geçmişte bugün: bir yıl ya da bir ay önce bugün yazılan not; yoksa her gün
 * değişen eski bir not (seçim [GecmisSecici]'de). Kilitli notlar seçilmez.
 */
class GecmisWidget : NotWidgetSaglayici() {

    override val gunlukYenile: Boolean = true

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_gecmis)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetBaslik)
        WidgetTema.ikincil(context, g, R.id.widgetEtiket, R.id.widgetIcerik)
        WidgetTema.ikincilIkon(context, g, R.id.widgetIkon)

        val notlar = NotDeposu(context).notlariListele(null, null).filterNot { it.kilitli }
        val adaylar = notlar.map { n ->
            val adres = n.uri.toString()
            val olusturma = Prefs.olusturma(context, adres)
            GecmisSecici.Aday(
                adres,
                n.baslik,
                GecmisSecici.gunlukGunu(n.baslik)
                    ?: (if (olusturma > 0) SonTarih.gunu(olusturma) else SonTarih.gunu(n.degistirilme))
            )
        }
        val secim = GecmisSecici.sec(adaylar, SonTarih.bugun())
        if (secim == null) {
            g.setTextViewText(R.id.widgetEtiket, context.getString(R.string.widget_gecmis))
            g.setTextViewText(R.id.widgetBaslik, context.getString(R.string.gecmiste_bugun_yok))
            g.setTextViewText(R.id.widgetIcerik, "")
            g.setOnClickPendingIntent(
                R.id.widgetKok,
                NotWidget.ekranNiyeti(context, id, Intent(context, MainActivity::class.java))
            )
            return g
        }
        val not = notlar.first { it.uri.toString() == secim.aday.anahtar }
        g.setTextViewText(
            R.id.widgetEtiket,
            when (secim) {
                is GecmisSecici.Secim.YilOnce ->
                    context.resources.getQuantityString(R.plurals.gecmis_yil_once, secim.yil, secim.yil)
                is GecmisSecici.Secim.AyOnce -> context.getString(R.string.gecmis_ay_once)
                is GecmisSecici.Secim.Eski -> context.getString(R.string.gecmis_eski_not)
            }
        )
        g.setTextViewText(R.id.widgetBaslik, not.baslik)
        g.setTextViewText(R.id.widgetIcerik, not.ozet)
        g.setOnClickPendingIntent(
            R.id.widgetKok,
            NotWidget.ekranNiyeti(context, id, NotWidget.notNiyeti(context, not.uri.toString()))
        )
        return g
    }
}
