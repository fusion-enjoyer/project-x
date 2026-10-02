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
 * Ana ekran widget'ı: saat, tarih, bir sonraki alarm. Saat ve tarihi TextClock
 * kendisi işletir; widget yalnız alarmlar değişince (AlarmKurucu) güncellenir,
 * düzenli güncelleme yok.
 */
class SaatWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, yonetici: AppWidgetManager, kimlikler: IntArray) {
        guncelle(context)
    }

    companion object {
        fun guncelle(context: Context) {
            val yonetici = AppWidgetManager.getInstance(context) ?: return
            val kimlikler = runCatching {
                yonetici.getAppWidgetIds(ComponentName(context, SaatWidget::class.java))
            }.getOrNull() ?: return
            if (kimlikler.isEmpty()) return
            val g = RemoteViews(context.packageName, R.layout.widget_saat)
            val simdi = System.currentTimeMillis()
            val sonraki = Depo.alarmlar(context).filter { it.id != MainActivity.DENEME_ID }
                .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, TimeZone.getDefault()) }.minOrNull()
            if (sonraki == null) {
                g.setViewVisibility(R.id.widgetAlarm, View.GONE)
            } else {
                g.setViewVisibility(R.id.widgetAlarm, View.VISIBLE)
                g.setTextViewText(R.id.widgetAlarm, "${Metinler.gun(context, sonraki, simdi)} ${Metinler.saat(context, sonraki)}")
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
