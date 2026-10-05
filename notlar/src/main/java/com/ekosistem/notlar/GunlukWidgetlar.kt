package com.ekosistem.notlar

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bugünün notu: gün adı, tarih ve günlük notun ilk satırları. Dokununca
 * bugünün notu açılır; yoksa şablonuyla oluşturulur (uygulamadaki kısayolla
 * aynı yol). Gece yarısı kendini yeniler.
 */
class BugunWidget : NotWidgetSaglayici() {

    override val gunlukYenile: Boolean = true

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_bugun)
        WidgetTema.zemin(context, g, R.id.widgetKok)
        WidgetTema.metin(context, g, R.id.widgetTarih)
        WidgetTema.ikincil(context, g, R.id.widgetGun, R.id.widgetIcerik)
        NotWidget.vurguMetni(context, g, R.id.widgetYaz)
        g.setTextViewText(R.id.widgetYaz, context.getString(R.string.bugun_yaz))

        val dil = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val simdi = Date()
        g.setTextViewText(R.id.widgetGun, SimpleDateFormat("EEEE", dil).format(simdi))
        g.setTextViewText(
            R.id.widgetTarih,
            SimpleDateFormat(DateFormat.getBestDateTimePattern(dil, "MMMMd"), dil).format(simdi)
        )
        g.setTextViewText(R.id.widgetIcerik, icerik(context))
        g.setOnClickPendingIntent(
            R.id.widgetKok,
            NotWidget.ekranNiyeti(context, id, gunlukNiyeti(context))
        )
        return g
    }

    /** Başlıktan sonraki dolu satırlar; şablonun başlıkları ve boş maddeleri atlanır. */
    private fun icerik(context: Context): String {
        val depo = NotDeposu(context)
        val not = depo.baslikIleBul(Sablonlar.bugununBasligi())
            ?: return context.getString(R.string.bugun_bos)
        if (Kilit.notKilitli(context, not.uri.toString())) return context.getString(R.string.kilitli)
        val satirlar = depo.oku(not.uri, 4096).lines()
        val ilk = satirlar.indexOfFirst { it.isNotBlank() }
        val dolu = satirlar.drop(ilk + 1).asSequence()
            .filterNot { Sifreleme.veriSatiriMi(it) }
            .filterNot { it.trimStart().startsWith("#") }
            .mapNotNull { satir ->
                val onay = MarkdownBicimci.ONAY.find(satir)
                if (onay != null) {
                    val metin = SonTarih.temizle(satir.substring(onay.value.length))
                    if (metin.isEmpty()) null
                    else (if (onay.groupValues[2].equals(" ", true)) "☐ " else "☑ ") + metin
                } else {
                    TekNotFabrikasi.temizle(satir).takeIf { it.isNotBlank() }
                }
            }
            .take(6)
            .toList()
        return if (dolu.isEmpty()) context.getString(R.string.bugun_bos) else dolu.joinToString("\n")
    }

    companion object {
        fun gunlukNiyeti(context: Context): Intent =
            // CLEAR_TOP: uygulama açıkken de ana ekran bu istekle yeniden kurulsun.
            Intent(context, MainActivity::class.java).setAction(MainActivity.KISAYOL_GUNLUK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** Hızlı eylemler çubuğu (4x1): yeni not, bugünün notu, görev ekle, fotoğraflı not, ara. */
class EylemlerWidget : NotWidgetSaglayici() {

    override fun ciz(context: Context, id: Int): RemoteViews {
        val g = RemoteViews(context.packageName, R.layout.widget_eylemler)
        WidgetTema.hap(context, g, R.id.widgetKok)
        WidgetTema.ikon(context, g, R.id.eylemGunluk, R.id.eylemGorev, R.id.eylemKamera, R.id.eylemAra)
        NotWidget.vurguyaBoya(context, g, R.id.eylemYeniDaire)
        NotWidget.vurguyaBoya(context, g, R.id.eylemYeniArti, uzeri = true)

        // İstek kodları widget başına ayrı; aynı niyet tipleri birbirini ezmesin.
        val taban = id * 10
        g.setOnClickPendingIntent(
            R.id.eylemYeni,
            NotWidget.ekranNiyeti(context, taban, NotWidget.yeniNotNiyeti(context))
        )
        g.setOnClickPendingIntent(
            R.id.eylemGunluk,
            NotWidget.ekranNiyeti(context, taban + 1, BugunWidget.gunlukNiyeti(context))
        )
        g.setOnClickPendingIntent(
            R.id.eylemGorev,
            NotWidget.ekranNiyeti(context, taban + 2, Intent(context, GorevEkleActivity::class.java))
        )
        g.setOnClickPendingIntent(
            R.id.eylemKamera,
            NotWidget.ekranNiyeti(
                context,
                taban + 3,
                NotWidget.yeniNotNiyeti(context).putExtra(EditorActivity.EK_KAMERA, true)
            )
        )
        g.setOnClickPendingIntent(
            R.id.eylemAra,
            NotWidget.ekranNiyeti(
                context,
                taban + 4,
                Intent(context, MainActivity::class.java).setAction(MainActivity.KISAYOL_ARA)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        )
        return g
    }
}
