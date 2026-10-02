package com.ekosistem.saat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.TimeZone

/**
 * Ana ekran widget'ı: saat, tarih, bir sonraki alarm ve dünya saatindeki ilk
 * üç şehir. Saatleri TextClock kendisi işletir; widget yalnız alarmlar ve
 * şehirler değişince güncellenir, düzenli güncelleme yok.
 */
class SaatWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) {
        guncelle(context)
    }

    companion object {
        private const val SEHIR_SAYISI = 3

        /** Her şehir için: kutu, ad, saat. */
        private val SEHIR_KUTULARI = listOf(
            intArrayOf(R.id.widgetSehir1, R.id.widgetSehirAd1, R.id.widgetSehirSaat1),
            intArrayOf(R.id.widgetSehir2, R.id.widgetSehirAd2, R.id.widgetSehirSaat2),
            intArrayOf(R.id.widgetSehir3, R.id.widgetSehirAd3, R.id.widgetSehirSaat3)
        )

        fun guncelle(context: Context) {
            val yonetici = AppWidgetManager.getInstance(context) ?: return
            val kimlikler = runCatching {
                yonetici.getAppWidgetIds(ComponentName(context, SaatWidget::class.java))
            }.getOrNull() ?: return
            if (kimlikler.isEmpty()) return
            val g = RemoteViews(context.packageName, R.layout.widget_saat)
            val simdi = System.currentTimeMillis()
            val tatil = Depo.tatilBitis(context)
            val sonraki = Depo.alarmlar(context).filter { it.id != MainActivity.DENEME_ID }
                .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, TimeZone.getDefault(), tatil) }.minOrNull()
            if (sonraki == null) {
                g.setViewVisibility(R.id.widgetAlarm, View.GONE)
            } else {
                g.setViewVisibility(R.id.widgetAlarm, View.VISIBLE)
                g.setTextViewText(R.id.widgetAlarm, "${Metinler.gun(context, sonraki, simdi)} ${Metinler.saat(context, sonraki)}")
            }
            val sehirler = Depo.sehirler(context).take(SEHIR_SAYISI)
            g.setViewVisibility(R.id.widgetSehirler, if (sehirler.isEmpty()) View.GONE else View.VISIBLE)
            for ((i, kutu) in SEHIR_KUTULARI.withIndex()) {
                val id = sehirler.getOrNull(i)
                if (id == null) {
                    g.setViewVisibility(kutu[0], View.GONE)
                } else {
                    g.setViewVisibility(kutu[0], View.VISIBLE)
                    g.setTextViewText(kutu[1], Sehirler.ad(id))
                    g.setString(kutu[2], "setTimeZone", id)
                }
            }
            val ac = PendingIntent.getActivity(
                context, 50_000, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            g.setOnClickPendingIntent(R.id.widgetKok, ac)
            yonetici.updateAppWidget(kimlikler, g)
        }
    }
}
