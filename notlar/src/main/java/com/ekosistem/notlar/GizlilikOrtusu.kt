package com.ekosistem.notlar

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Kilit kuruluyken uygulama arka plana geçerken ekranın üstüne örtü çekilir:
 * son uygulamalar ekranında not yerine bankacılık uygulamalarındaki gibi
 * üstü çizili göz görünür. Önceden `setRecentsScreenshotEnabled(false)`
 * kullanılıyordu; sistem orada düz gri bir kutu gösteriyordu.
 *
 * İki sinyal dinlenir. Ana ekrana dönüşte ekran duraklatılır (onPause).
 * Uygulama açıkken doğrudan son uygulamalara geçilince ise Android 10+
 * ekranı duraklatmaz, canlı görüntüsünü gösterir; o zaman yalnızca "en öndeki
 * ekran" olmaktan çıkılır (onTopResumedActivityChanged). Uygulamanın kendi alt
 * sayfaları bu sinyali tetiklemez, yani menü açınca örtü çıkmaz.
 *
 * Uygulama içi geçişlerde (not açma, ayarlara gitme, geri dönme) örtü
 * çekilmez; yoksa geçiş animasyonunda bir anlığına görünürdü.
 * "Ekran görüntüsünü engelle" açıksa sistem zaten boş kutu gösterir.
 */
object GizlilikOrtusu {

    /** Uygulamanın kendi ekranlarından biri açılıyor; sıradaki duraklama arka plana geçiş değil. */
    @Volatile
    var icGecis = false

    private const val ETIKET = "gizlilik_ortusu"

    /** Ekran en önde olmaktan çıktı (son uygulamalar, başka uygulama, ana ekran). */
    fun onundenCekildi(a: Activity) {
        // İç geçişte bayrağı duraklama tüketir; burada yalnızca bakılır.
        if (icGecis) return
        ortuGerekirseGoster(a)
    }

    fun duraklatildi(a: Activity) {
        if (icGecis) {
            icGecis = false
            return
        }
        ortuGerekirseGoster(a)
    }

    private fun ortuGerekirseGoster(a: Activity) {
        if (a.isFinishing || a is KilitActivity || !Kilit.kurulu(a)) return
        goster(a)
    }

    fun devamEdildi(a: Activity) {
        val kok = a.window.decorView as? ViewGroup ?: return
        kok.findViewWithTag<View>(ETIKET)?.let { kok.removeView(it) }
    }

    private fun goster(a: Activity) {
        val kok = a.window.decorView as? ViewGroup ?: return
        if (kok.findViewWithTag<View>(ETIKET) != null) return
        val y = a.resources.displayMetrics.density
        val ortu = FrameLayout(a).apply {
            tag = ETIKET
            setBackgroundColor(ContextCompat.getColor(a, R.color.zemin))
            // Örtü açıkken altındaki ekrana dokunulamasın.
            isClickable = true
            elevation = 100 * y
        }
        val icerik = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        icerik.addView(
            ImageView(a).apply {
                setImageResource(R.drawable.ic_goz_kapali)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(a, R.color.metin_ikincil))
            },
            LinearLayout.LayoutParams((72 * y).toInt(), (72 * y).toInt())
        )
        icerik.addView(
            TextView(a).apply {
                text = a.getString(R.string.app_name)
                setTextColor(ContextCompat.getColor(a, R.color.metin_ikincil))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * y).toInt() }
        )
        ortu.addView(
            icerik,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        kok.addView(
            ortu,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }
}

/**
 * Bütün ekranların ortak atası. Tek işi uygulama içi geçişi gizlilik örtüsüne
 * bildirmek: `startActivity` ve Activity Result API'si de sonunda buradan geçer.
 */
abstract class TemelActivity : AppCompatActivity() {
    @Deprecated("Activity Result API'si de bu yoldan geçer; yalnızca gözlemlenir.")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (intent.component?.packageName == packageName) GizlilikOrtusu.icGecis = true
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    /** Android 10+: son uygulamalara geçişte ekran duraklatılmadan önünden çekilir. */
    override fun onTopResumedActivityChanged(enOnde: Boolean) {
        super.onTopResumedActivityChanged(enOnde)
        if (enOnde) GizlilikOrtusu.devamEdildi(this) else GizlilikOrtusu.onundenCekildi(this)
    }
}
