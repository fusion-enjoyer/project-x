package com.ekosistem.saat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Yatay "saat modu": siyah zeminde dev saat, tarih ve sonraki alarm. Hem
 * uygulamanın kendi ekranında ([SaatModuActivity]) hem de telefonun ekran
 * koruyucusu olarak ([SaatEkranKoruyucu]) aynı görünüm kullanılır. Ekran yanma
 * izi bırakmasın diye içerik dakikada bir birkaç piksel kayar; üç parlaklık
 * düzeyi vardır (gece için çok loş).
 */
class SaatModuGorunumu(context: Context) : FrameLayout(context) {

    private val kutu = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val saat = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "HH:mm"
        includeFontPadding = false
        fontFeatureSettings = "tnum"
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setTextColor(ANA)
    }
    private val tarih = TextClock(context).apply {
        val bicim = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        format12Hour = bicim
        format24Hour = bicim
        setTextColor(IKINCIL)
    }
    private val alarm = TextView(context).apply {
        setTextColor(VURGU)
        setTypeface(null, Typeface.BOLD)
    }
    private val isleyici = Handler(Looper.getMainLooper())
    private val dakikalik = object : Runnable {
        override fun run() {
            alarmiYaz()
            kaydir()
            isleyici.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }

    /** 0 çok loş, 1 orta, 2 parlak. */
    var seviye: Int = 1
        set(deger) {
            field = deger.coerceIn(0, 2)
            kutu.alpha = ALFA[field]
        }

    init {
        setBackgroundColor(Color.BLACK)
        // Üç satır da aynı eksende ortalanır.
        for (v in listOf(saat, tarih, alarm)) {
            (v as TextView).gravity = Gravity.CENTER
            kutu.addView(v, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        addView(kutu, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        seviye = 1
    }

    override fun onSizeChanged(w: Int, h: Int, eskiW: Int, eskiH: Int) {
        super.onSizeChanged(w, h, eskiW, eskiH)
        fun px(oran: Float) = (minOf(h * 1.8f, w * 0.55f) * oran)
        // Yatayda yükseklik kısa: saat yüksekliğin yarısına yakın olsun.
        saat.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(h * 0.52f, w * 0.30f))
        tarih.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.07f).coerceAtLeast(28f))
        alarm.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.06f).coerceAtLeast(24f))
        (alarm.layoutParams as LinearLayout.LayoutParams).topMargin = (h * 0.04f).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isleyici.post(dakikalik)
    }

    override fun onDetachedFromWindow() {
        isleyici.removeCallbacks(dakikalik)
        super.onDetachedFromWindow()
    }

    private fun alarmiYaz() {
        val simdi = System.currentTimeMillis()
        val tatil = Depo.tatilBitis(context)
        val sonraki = Depo.alarmlar(context).filter { it.id != MainActivity.DENEME_ID }
            .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, TimeZone.getDefault(), tatil) }.minOrNull()
        if (sonraki == null) {
            alarm.visibility = GONE
        } else {
            alarm.visibility = VISIBLE
            alarm.text = "${Metinler.gun(context, sonraki, simdi)} ${Metinler.saat(context, sonraki)}"
        }
    }

    /** Yanma izini önlemek için içerik küçük adımlarla yer değiştirir. */
    private fun kaydir() {
        val yatay = width * 0.04f
        val dikey = height * 0.06f
        kutu.animate().translationX(Random.nextFloat() * 2 * yatay - yatay)
            .translationY(Random.nextFloat() * 2 * dikey - dikey).setDuration(2000).start()
    }

    companion object {
        private const val ANA = 0xFFF2EFE9.toInt()
        private const val IKINCIL = 0xFF8B867D.toInt()
        private const val VURGU = 0xFFF97316.toInt()
        private val ALFA = floatArrayOf(0.30f, 0.62f, 1f)
    }
}
