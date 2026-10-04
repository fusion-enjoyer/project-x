package com.ekosistem.takvim

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR
import java.util.TimeZone

/**
 * Widget seti: [GundemWidget] (yaklaşan etkinlikler, boyuta göre 2–6 satır),
 * [AyWidget] (ay ızgarası, ileri/geri) ve [SiradakiWidget] (2x1 sıradaki etkinlik).
 * Düzenli güncelleme yok: takvim deposu değişince (`PROVIDER_CHANGED`), saat/dilim
 * değişince ve her gece yarısı [WidgetYenileAlici] hepsini yeniden çizer.
 */
object Widgetlar {
    const val ACTION_GUNCELLE = "com.ekosistem.takvim.WIDGET_GUNCELLE"

    private val SINIFLAR = listOf(GundemWidget::class.java, AyWidget::class.java, SiradakiWidget::class.java)

    fun kimlikler(context: Context, sinif: Class<*>): IntArray {
        val yonetici = AppWidgetManager.getInstance(context) ?: return IntArray(0)
        return runCatching { yonetici.getAppWidgetIds(ComponentName(context, sinif)) }.getOrDefault(IntArray(0))
    }

    /** Hiç widget yerleştirilmemişse hiçbir şey çizilmez, alarm da kurulmaz. */
    fun hepsiniGuncelle(context: Context) {
        if (SINIFLAR.all { kimlikler(context, it).isEmpty() }) return
        GundemWidget.guncelle(context)
        AyWidget.guncelle(context)
        SiradakiWidget.guncelle(context)
        geceYarisiniKur(context)
    }

    /** Ertesi gün 00:00:01'de bir güncelleme (bugün halkası ve "bugün" etiketleri kayar). */
    private fun geceYarisiniKur(context: Context) {
        val tz = TimeZone.getDefault()
        val simdi = System.currentTimeMillis()
        val an = Gun.yerelAn(Gun.yerelGun(simdi, tz) + 1, 0, tz) + 1000L
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val islem = PendingIntent.getBroadcast(
            context, 71_000, Intent(context, WidgetYenileAlici::class.java).setAction(ACTION_GUNCELLE),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        // Tam zamanlı olması gerekmez: birkaç dakika sapma bu widget'lar için sorun değil.
        am.set(AlarmManager.RTC, an, islem)
    }

    private fun bayrak(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    fun uygulamayiAc(context: Context, istek: Int, gun: Int? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (gun != null) {
            // MainActivity bu adresi "şu güne git" olarak tanır (content://com.android.calendar/time/<ms>).
            i.data = Uri.parse("content://com.android.calendar/time/" + Gun.yerelAn(gun, 12 * 60, TimeZone.getDefault()))
        }
        return PendingIntent.getActivity(context, istek, i, bayrak())
    }

    fun yeniEtkinlik(context: Context, istek: Int): PendingIntent = PendingIntent.getActivity(
        context, istek, Intent(context, DuzenleActivity::class.java).setAction("com.ekosistem.takvim.YENI"), bayrak()
    )

    fun ayrinti(context: Context, istek: Int, o: Ornek): PendingIntent = PendingIntent.getActivity(
        context, istek,
        if (OrnekAc.dogumGunuMu(o)) OrnekAc.niyet(context, o).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(context, DetayActivity::class.java).setAction("com.ekosistem.takvim.AYRINTI")
            .setData(Uri.parse("takvim://widget/$istek"))
            .putExtra(DetayActivity.EK_ID, o.etkinlikId).putExtra(DetayActivity.EK_BAS, o.baslangic).putExtra(DetayActivity.EK_BIT, o.bitis),
        bayrak()
    )

    fun yayin(context: Context, istek: Int, sinif: Class<*>, eylem: String): PendingIntent = PendingIntent.getBroadcast(
        context, istek, Intent(context, sinif).setAction(eylem), bayrak()
    )

    /** Widget görünümlerinde ikon/metin rengi koddan verilir (koyu/açık tema kaynaklardan gelir). */
    fun renk(context: Context, kaynak: Int): Int = ContextCompat.getColor(context, kaynak)

    /** [ilk]..[son] arası görünür takvimlerin etkinlikleri; izin yoksa null. */
    fun ornekler(context: Context, ilk: Int, son: Int): List<Ornek>? =
        if (TakvimDeposu.izinVar(context)) TakvimDeposu.ornekler(context, ilk, son) else null

    fun izinSatiri(context: Context, g: RemoteViews, id: Int) {
        g.setTextViewText(id, context.getString(R.string.widget_izin))
        g.setViewVisibility(id, View.VISIBLE)
    }
}

/** Widget sağlayıcıları veriyi depodan okur; ana iş parçacığını bloklamasın diye `goAsync` ile arka planda çalışır. */
abstract class TakvimWidgetSaglayici : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val bekleyen = goAsync()
        Thread {
            try {
                super.onReceive(context.applicationContext, intent)
            } finally {
                bekleyen.finish()
            }
        }.start()
    }
}

/** Gündem widget'ı: bugünden başlayan yaklaşan etkinlikler; boyutu büyüdükçe satır sayısı artar. */
class GundemWidget : TakvimWidgetSaglayici() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    override fun onAppWidgetOptionsChanged(context: Context, yonetici: AppWidgetManager, id: Int, secenekler: android.os.Bundle) =
        guncelle(context)

    companion object {
        private const val SATIR = 6


        fun guncelle(context: Context) {
            val kimlikler = Widgetlar.kimlikler(context, GundemWidget::class.java)
            if (kimlikler.isEmpty()) return
            val yonetici = AppWidgetManager.getInstance(context)
            val tz = TimeZone.getDefault()
            val simdi = System.currentTimeMillis()
            val bugun = Gun.bugun(simdi, tz)
            val ornekler = Widgetlar.ornekler(context, bugun, bugun + 13)
            for (id in kimlikler) {
                val yukseklik = yonetici.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val adet = ((yukseklik - 44) / 46).coerceIn(2, SATIR)
                yonetici.updateAppWidget(id, ciz(context, id, ornekler, bugun, simdi, adet))
            }
        }

        /** Yerleşim dışarıdan da çağrılabilsin diye ayrı (hata ayıklama önizlemesi kullanır). */
        fun ciz(context: Context, widgetId: Int, ornekler: List<Ornek>?, bugun: Int, simdi: Long, adet: Int): RemoteViews {
            val g = RemoteViews(context.packageName, R.layout.widget_gundem)
            g.setOnClickPendingIntent(R.id.widgetBaslik, Widgetlar.uygulamayiAc(context, 72_000 + widgetId))
            g.setOnClickPendingIntent(R.id.widgetYeni, Widgetlar.yeniEtkinlik(context, 72_100 + widgetId))
            g.setViewVisibility(R.id.widgetBos, View.GONE)
            for (i in 0 until SATIR) g.setViewVisibility(WidgetKimlikleri.gr[i], View.GONE)
            if (ornekler == null) {
                g.setTextViewText(R.id.widgetBos, context.getString(R.string.widget_izin))
                g.setViewVisibility(R.id.widgetBos, View.VISIBLE)
                g.setOnClickPendingIntent(R.id.widgetBos, Widgetlar.uygulamayiAc(context, 72_200 + widgetId))
                return g
            }
            val satirlar = WidgetVerisi.gundemSatirlari(ornekler, bugun, simdi, adet)
            if (satirlar.isEmpty()) {
                g.setTextViewText(R.id.widgetBos, context.getString(R.string.widget_bos))
                g.setViewVisibility(R.id.widgetBos, View.VISIBLE)
                g.setOnClickPendingIntent(R.id.widgetBos, Widgetlar.uygulamayiAc(context, 72_200 + widgetId))
                return g
            }
            val metin = Widgetlar.renk(context, TR.color.metin)
            val vurgu = Tasarim.vurgu(context)
            for ((i, s) in satirlar.withIndex()) {
                val satir = WidgetKimlikleri.gr[i]
                g.setViewVisibility(satir, View.VISIBLE)
                val gunEt = WidgetKimlikleri.gs[i]
                if (s.gunBasi) {
                    g.setTextViewText(gunEt, Metinler.widgetGunu(context, s.gun, bugun))
                    g.setTextColor(gunEt, if (s.gun == bugun) vurgu else metin)
                    g.setViewVisibility(gunEt, View.VISIBLE)
                } else {
                    g.setTextViewText(gunEt, "")
                }
                g.setInt(WidgetKimlikleri.gb[i], "setBackgroundColor", s.ornek.renk)
                g.setTextViewText(WidgetKimlikleri.gad[i], s.ornek.baslik.ifBlank { context.getString(R.string.basliksiz) })
                g.setTextViewText(WidgetKimlikleri.gsaat[i], Metinler.ornekAltYazisi(context, s.ornek, s.gun))
                g.setOnClickPendingIntent(satir, Widgetlar.ayrinti(context, 73_000 + widgetId * 10 + i, s.ornek))
            }
            return g
        }
    }
}

/** Ay widget'ı: ay ızgarası, etkinlik noktaları, ileri/geri ve bugün; güne dokununca uygulama o günde açılır. */
class AyWidget : TakvimWidgetSaglayici() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ONCEKI -> Depo.widgetAyOfsetiKaydet(context, Depo.widgetAyOfseti(context) - 1)
            ACTION_SONRAKI -> Depo.widgetAyOfsetiKaydet(context, Depo.widgetAyOfseti(context) + 1)
            ACTION_BUGUN -> Depo.widgetAyOfsetiKaydet(context, 0)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_ONCEKI = "com.ekosistem.takvim.WIDGET_AY_ONCEKI"
        const val ACTION_SONRAKI = "com.ekosistem.takvim.WIDGET_AY_SONRAKI"
        const val ACTION_BUGUN = "com.ekosistem.takvim.WIDGET_AY_BUGUN"


        fun guncelle(context: Context) {
            val kimlikler = Widgetlar.kimlikler(context, AyWidget::class.java)
            if (kimlikler.isEmpty()) return
            val simdi = System.currentTimeMillis()
            val bugun = Gun.bugun(simdi, TimeZone.getDefault())
            val ayGunu = Gun.ayEkle(bugun, Depo.widgetAyOfseti(context))
            val g = ciz(context, ayGunu, bugun)
            AppWidgetManager.getInstance(context).updateAppWidget(kimlikler, g)
        }

        fun ciz(context: Context, ayGunu: Int, bugun: Int): RemoteViews {
            val g = RemoteViews(context.packageName, R.layout.widget_ay)
            val haftaBasi = Depo.haftaBasi(context)
            val yil = Gun.yil(ayGunu)
            val ay = Gun.ay(ayGunu)
            val ayBasi = Gun.gun(yil, ay, 1)
            val ilk = Gun.haftaBasi(ayBasi, haftaBasi)
            val satir = (ayBasi - ilk + Gun.ayinGunSayisi(yil, ay) + 6) / 7
            val ornekler = Widgetlar.ornekler(context, ilk, ilk + satir * 7 - 1)

            val metin = Widgetlar.renk(context, TR.color.metin)
            val soluk = Widgetlar.renk(context, TR.color.metin_ikincil)
            val vurgu = Tasarim.vurgu(context)
            val vurguUzeri = Tasarim.vurguUzeri(context)

            g.setTextViewText(R.id.widgetAyBaslik, Metinler.ayYil(ayGunu))
            g.setOnClickPendingIntent(R.id.widgetAyBaslik, Widgetlar.yayin(context, 74_001, AyWidget::class.java, ACTION_BUGUN))
            g.setOnClickPendingIntent(R.id.widgetAyOnceki, Widgetlar.yayin(context, 74_002, AyWidget::class.java, ACTION_ONCEKI))
            g.setOnClickPendingIntent(R.id.widgetAySonraki, Widgetlar.yayin(context, 74_003, AyWidget::class.java, ACTION_SONRAKI))
            g.setOnClickPendingIntent(R.id.widgetAyYeni, Widgetlar.yeniEtkinlik(context, 74_004))
            g.setInt(R.id.widgetAyOnceki, "setColorFilter", metin)
            g.setInt(R.id.widgetAySonraki, "setColorFilter", metin)
            g.setInt(R.id.widgetAyYeni, "setColorFilter", vurgu)

            for (i in 0 until 7) {
                val t = WidgetKimlikleri.wh[i]
                g.setTextViewText(t, Metinler.haftaGunuKisa((haftaBasi + i) % 7))
            }

            val noktalar = HashMap<Int, Int>()
            ornekler?.forEach { o ->
                for (d in maxOf(o.ilkGun, ilk)..minOf(o.sonGun, ilk + satir * 7 - 1)) if (d !in noktalar) noktalar[d] = o.renk
            }
            for (r in 0 until 6) g.setViewVisibility(WidgetKimlikleri.wr[r], if (r < satir) View.VISIBLE else View.GONE)
            for (i in 0 until satir * 7) {
                val gun = ilk + i
                val hucre = WidgetKimlikleri.wc[i]
                val sayi = WidgetKimlikleri.wn[i]
                val nokta = WidgetKimlikleri.wd[i]
                val bu = gun == bugun
                g.setTextViewText(sayi, Gun.ayinGunu(gun).toString())
                g.setTextColor(sayi, when {
                    bu -> vurguUzeri
                    Gun.ay(gun) == ay -> metin
                    else -> soluk
                })
                g.setInt(hucre, "setBackgroundResource", if (bu) R.drawable.bg_widget_bugun else 0)
                val renk = noktalar[gun]
                if (renk != null) {
                    g.setViewVisibility(nokta, View.VISIBLE)
                    g.setInt(nokta, "setColorFilter", if (bu) vurguUzeri else renk)
                } else {
                    g.setViewVisibility(nokta, View.INVISIBLE)
                }
                g.setOnClickPendingIntent(hucre, Widgetlar.uygulamayiAc(context, 75_000 + i, gun))
            }
            return g
        }
    }
}

/** 2x1 sıradaki etkinlik: başlık ve ne zaman; dokununca ayrıntısı açılır. */
class SiradakiWidget : TakvimWidgetSaglayici() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    companion object {
        fun guncelle(context: Context) {
            val kimlikler = Widgetlar.kimlikler(context, SiradakiWidget::class.java)
            if (kimlikler.isEmpty()) return
            AppWidgetManager.getInstance(context).updateAppWidget(kimlikler, ciz(context))
        }

        fun ciz(context: Context): RemoteViews {
            val g = RemoteViews(context.packageName, R.layout.widget_siradaki)
            val tz = TimeZone.getDefault()
            val simdi = System.currentTimeMillis()
            val bugun = Gun.bugun(simdi, tz)
            val ornekler = Widgetlar.ornekler(context, bugun, bugun + 7)
            g.setOnClickPendingIntent(R.id.widgetKok, Widgetlar.uygulamayiAc(context, 76_000))
            if (ornekler == null) {
                g.setTextViewText(R.id.widgetSiradakiBaslik, context.getString(R.string.widget_izin))
                g.setTextViewText(R.id.widgetSiradakiZaman, "")
                g.setInt(R.id.widgetSiradakiRenk, "setBackgroundColor", Tasarim.vurgu(context))
                return g
            }
            val o = WidgetVerisi.siradaki(ornekler, simdi, bugun)
            if (o == null) {
                g.setTextViewText(R.id.widgetSiradakiBaslik, context.getString(R.string.siradaki_yok))
                g.setTextViewText(R.id.widgetSiradakiZaman, "")
                g.setInt(R.id.widgetSiradakiRenk, "setBackgroundColor", Tasarim.vurgu(context))
                return g
            }
            g.setTextViewText(R.id.widgetSiradakiBaslik, o.baslik.ifBlank { context.getString(R.string.basliksiz) })
            g.setTextViewText(R.id.widgetSiradakiZaman, Metinler.siradakiZamani(context, o, simdi, bugun))
            g.setInt(R.id.widgetSiradakiRenk, "setBackgroundColor", o.renk)
            g.setOnClickPendingIntent(R.id.widgetKok, Widgetlar.ayrinti(context, 76_001, o))
            return g
        }
    }
}

/** Depo değişince, saat/dilim değişince ve her gece yarısı widget'ları yeniler. */
class WidgetYenileAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bekleyen = goAsync()
        Thread {
            try {
                Widgetlar.hepsiniGuncelle(context.applicationContext)
            } finally {
                bekleyen.finish()
            }
        }.start()
    }
}
