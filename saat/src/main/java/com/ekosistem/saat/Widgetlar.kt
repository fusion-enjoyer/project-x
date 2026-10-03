package com.ekosistem.saat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.TimeZone

/**
 * Widget seti: [SaatWidget] (saat + alarm + şehirler, 4x2), [SaatKucukWidget]
 * (2x1 dijital saat), [AlarmWidget] (2x1 sonraki alarm), [DunyaWidget] (4x2
 * şehirler) ve [ZamanlayiciWidget] (4x1 hızlı zamanlayıcı). Saatleri TextClock
 * işletir; veri değişince ([hepsiniGuncelle]) yeniden çizilir, düzenli güncelleme yok.
 */
object Widgetlar {

    fun hepsiniGuncelle(context: Context) {
        SaatWidget.guncelle(context)
        AlarmWidget.guncelle(context)
        DunyaWidget.guncelle(context)
        ZamanlayiciWidget.guncelle(context)
    }

    /** Bu türden yerleştirilmiş widget kimlikleri; yoksa null (boşuna çizilmesin). */
    fun kimlikler(context: Context, sinif: Class<*>): IntArray? {
        val yonetici = AppWidgetManager.getInstance(context) ?: return null
        val kimlikler = runCatching { yonetici.getAppWidgetIds(ComponentName(context, sinif)) }.getOrNull()
        return kimlikler?.takeIf { it.isNotEmpty() }
    }

    fun uygulamayiAc(context: Context, sekme: Int, istek: Int): PendingIntent = PendingIntent.getActivity(
        context, istek,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EK_SEKME, sekme)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun guncelle(context: Context, sinif: Class<*>, g: RemoteViews) {
        val kimlikler = kimlikler(context, sinif) ?: return
        AppWidgetManager.getInstance(context).updateAppWidget(kimlikler, g)
    }
}

/** 2x1 dijital saat: yalnız saat ve gün; dokununca uygulama açılır. */
class SaatKucukWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) {
        val g = RemoteViews(context.packageName, R.layout.widget_saat_kucuk)
        g.setOnClickPendingIntent(R.id.widgetKok, Widgetlar.uygulamayiAc(context, MainActivity.SEKME_ALARM, 50_001))
        yonetici.updateAppWidget(kimlikler, g)
    }
}

/** 2x1 sonraki alarm. */
class AlarmWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    companion object {
        fun guncelle(context: Context) {
            if (Widgetlar.kimlikler(context, AlarmWidget::class.java) == null) return
            val simdi = System.currentTimeMillis()
            val tatil = Depo.tatilBitis(context)
            val sonraki = Depo.alarmlar(context).filter { it.id != MainActivity.DENEME_ID }
                .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, TimeZone.getDefault(), tatil) }.minOrNull()
            val g = RemoteViews(context.packageName, R.layout.widget_alarm)
            g.setTextViewText(
                R.id.widgetAlarmMetin,
                sonraki?.let { "${Metinler.gun(context, it, simdi)} ${Metinler.saat(context, it)}" }
                    ?: context.getString(R.string.alarm_yok_kisa)
            )
            g.setOnClickPendingIntent(R.id.widgetKok, Widgetlar.uygulamayiAc(context, MainActivity.SEKME_ALARM, 50_002))
            Widgetlar.guncelle(context, AlarmWidget::class.java, g)
        }
    }
}

/** 4x2 dünya saatleri: ilk dört şehir. */
class DunyaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    companion object {
        private val HUCRELER = listOf(
            intArrayOf(R.id.widgetDunya1, R.id.widgetDunyaAd1, R.id.widgetDunyaSaat1),
            intArrayOf(R.id.widgetDunya2, R.id.widgetDunyaAd2, R.id.widgetDunyaSaat2),
            intArrayOf(R.id.widgetDunya3, R.id.widgetDunyaAd3, R.id.widgetDunyaSaat3),
            intArrayOf(R.id.widgetDunya4, R.id.widgetDunyaAd4, R.id.widgetDunyaSaat4)
        )

        fun guncelle(context: Context) {
            if (Widgetlar.kimlikler(context, DunyaWidget::class.java) == null) return
            val sehirler = Depo.sehirler(context).take(HUCRELER.size)
            val g = RemoteViews(context.packageName, R.layout.widget_dunya)
            g.setViewVisibility(R.id.widgetDunyaBos, if (sehirler.isEmpty()) View.VISIBLE else View.GONE)
            g.setViewVisibility(R.id.widgetDunyaSatir1, if (sehirler.isEmpty()) View.GONE else View.VISIBLE)
            g.setViewVisibility(R.id.widgetDunyaSatir2, if (sehirler.size > 2) View.VISIBLE else View.GONE)
            for ((i, h) in HUCRELER.withIndex()) {
                val id = sehirler.getOrNull(i)
                if (id == null) {
                    g.setViewVisibility(h[0], View.INVISIBLE)
                } else {
                    g.setViewVisibility(h[0], View.VISIBLE)
                    g.setTextViewText(h[1], Sehirler.ad(id))
                    g.setString(h[2], "setTimeZone", id)
                }
            }
            g.setOnClickPendingIntent(R.id.widgetKok, Widgetlar.uygulamayiAc(context, MainActivity.SEKME_DUNYA, 50_003))
            Widgetlar.guncelle(context, DunyaWidget::class.java, g)
        }
    }
}

/** 4x1 hızlı zamanlayıcı: ilk dört hazır süre; dokununca hemen başlar. */
class ZamanlayiciWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) = guncelle(context)

    companion object {
        private val CIPLER = intArrayOf(R.id.widgetZ1, R.id.widgetZ2, R.id.widgetZ3, R.id.widgetZ4)

        fun guncelle(context: Context) {
            if (Widgetlar.kimlikler(context, ZamanlayiciWidget::class.java) == null) return
            val sureler = Depo.hazirSureler(context).take(CIPLER.size)
            val g = RemoteViews(context.packageName, R.layout.widget_zamanlayici)
            for ((i, id) in CIPLER.withIndex()) {
                val ms = sureler.getOrNull(i)
                if (ms == null) {
                    g.setViewVisibility(id, View.INVISIBLE)
                    continue
                }
                g.setViewVisibility(id, View.VISIBLE)
                g.setTextViewText(
                    id, Zamanlayici.kisa(
                        ms, context.getString(R.string.birim_sa), context.getString(R.string.birim_dk),
                        context.getString(R.string.birim_sn)
                    )
                )
                g.setOnClickPendingIntent(
                    id, PendingIntent.getBroadcast(
                        context, 60_000 + i,
                        Intent(context, HizliZamanlayiciAlici::class.java).putExtra(HizliZamanlayiciAlici.EK_SURE, ms),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            Widgetlar.guncelle(context, ZamanlayiciWidget::class.java, g)
        }
    }
}

/** Widget'taki hazır süre çipine dokununca zamanlayıcıyı başlatır. */
class HizliZamanlayiciAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ms = intent.getLongExtra(EK_SURE, 0)
        if (ms <= 0) return
        val id = (Depo.zamanlayicilar(context).maxOfOrNull { it.id } ?: 0) + 1
        Depo.zamanlayiciYaz(context, Zamanlayici(id, ms).baslat(System.currentTimeMillis()))
        ZamanlayiciKurucu.hepsiniKur(context)
        android.widget.Toast.makeText(
            context, context.getString(R.string.zamanlayici_basladi), android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val EK_SURE = "sure"
    }
}
