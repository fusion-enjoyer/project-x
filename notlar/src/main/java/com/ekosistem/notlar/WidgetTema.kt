package com.ekosistem.notlar

import android.content.Context
import android.widget.RemoteViews

/**
 * Widget renkleri uygulamanın tema ayarını izler. Widget'ı başlatıcı çizer ve
 * XML'deki renkleri telefonun sistem temasına göre çözer: uygulamada "Açık"
 * seçiliyken telefon koyu moddaysa widget koyu çıkıyordu.
 *
 * Tema "Sistemle aynı" ise hiçbir şeye dokunulmaz; başlatıcı sistem teması
 * değişince widget'ı kendisi çevirir. "Açık" ya da "Saf siyah" seçiliyse
 * renkler koddan sabit verilir. Değerler `colors.xml` ile aynıdır.
 */
object WidgetTema {

    class Palet(
        val gece: Boolean,
        val zemin: Int,
        val hap: Int,
        val metin: Int,
        val ikincil: Int
    )

    private val ACIK = Palet(
        false, R.drawable.bg_widget_acik, R.drawable.bg_widget_hap_acik,
        0xFF171614.toInt(), 0xFF8A867E.toInt()
    )
    private val KOYU = Palet(
        true, R.drawable.bg_widget_koyu, R.drawable.bg_widget_hap_koyu,
        0xFFF2EFE9.toInt(), 0xFF8B867D.toInt()
    )

    /** null: tema sistemle aynı, renkler kaynaklardan çözülür. */
    fun palet(c: Context): Palet? = when (Prefs.tema(c)) {
        1 -> ACIK
        2 -> KOYU
        else -> null
    }

    /** Zemini (yuvarlak köşeli kart) temaya göre değiştirir. */
    fun zemin(c: Context, g: RemoteViews, vararg idler: Int) {
        val p = palet(c) ?: return
        for (id in idler) g.setInt(id, "setBackgroundResource", p.zemin)
    }

    /** Hap biçimli zemin (Hızlı eylemler çubuğu). */
    fun hap(c: Context, g: RemoteViews, id: Int) {
        val p = palet(c) ?: return
        g.setInt(id, "setBackgroundResource", p.hap)
    }

    fun metin(c: Context, g: RemoteViews, vararg idler: Int) {
        val p = palet(c) ?: return
        for (id in idler) g.setTextColor(id, p.metin)
    }

    fun ikincil(c: Context, g: RemoteViews, vararg idler: Int) {
        val p = palet(c) ?: return
        for (id in idler) g.setTextColor(id, p.ikincil)
    }

    /** Metin renginde çizilen ikonlar (görev kutusu, eylem ikonları). */
    fun ikon(c: Context, g: RemoteViews, vararg idler: Int) {
        val p = palet(c) ?: return
        for (id in idler) g.setInt(id, "setColorFilter", p.metin)
    }

    fun ikincilIkon(c: Context, g: RemoteViews, vararg idler: Int) {
        val p = palet(c) ?: return
        for (id in idler) g.setInt(id, "setColorFilter", p.ikincil)
    }

    /**
     * Seçili vurgu renginin widget temasına uyan tonu. Uygulamanın kendi
     * [Renkler.vurgu]'su sistem temasına baktığı için burada palet esas alınır.
     */
    fun vurgu(c: Context): Int {
        val p = palet(c) ?: return Renkler.vurgu(c)
        val indeks = Prefs.vurguIndeksi(c)
        val secenek = Renkler.SECENEKLER.getOrNull(indeks) ?: Renkler.SECENEKLER[0]
        return if (p.gece) secenek.koyu else secenek.acik
    }

    /** Not satırlarını uygulamadaki gibi biçimlemek için renkler ([NotOnizleme]). */
    fun stil(c: Context): NotOnizleme.Stil {
        val p = palet(c)
        val soluk = p?.ikincil ?: androidx.core.content.ContextCompat.getColor(c, R.color.metin_ikincil)
        return NotOnizleme.Stil(vurgu(c), soluk, p?.gece ?: Renkler.geceMi(c))
    }

    /** Gecikmiş görev ve tarih rengi; `fark_silindi` ile aynı tonlar. */
    fun gecikmis(c: Context): Int {
        val gece = palet(c)?.gece ?: Renkler.geceMi(c)
        return if (gece) 0xFFE24B4A.toInt() else 0xFFA32D2D.toInt()
    }
}
