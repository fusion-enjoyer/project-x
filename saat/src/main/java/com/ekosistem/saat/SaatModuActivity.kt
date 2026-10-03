package com.ekosistem.saat

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Saat modu: yatay, tam ekran, ekran açık kalır. Dokununca parlaklık düzeyi
 * değişir (çok loş / orta / parlak), geri tuşu çıkar. Şarjdaki telefonu başucu
 * saatine çevirmek için.
 */
class SaatModuActivity : AppCompatActivity() {

    private lateinit var gorunum: SaatModuGorunumu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        gorunum = SaatModuGorunumu(this)
        gorunum.seviye = Depo.ayarlar(this).getInt("saat_modu_seviye", 1)
        val ipucu = TextView(this).apply {
            text = getString(R.string.saat_modu_ipucu)
            setTextColor(0xFF8B867D.toInt())
            textSize = 13f
            setBackgroundColor(Color.TRANSPARENT)
        }
        val kok = FrameLayout(this)
        kok.addView(gorunum, FrameLayout.LayoutParams(-1, -1))
        kok.addView(ipucu, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (24 * resources.displayMetrics.density).toInt()
        })
        setContentView(kok)
        parlaklikUygula()
        ustunuGizle()
        // İpucu üç saniye sonra kaybolur.
        ipucu.animate().alpha(0f).setStartDelay(3000).setDuration(800).start()
        kok.setOnClickListener {
            gorunum.seviye = (gorunum.seviye + 1) % 3
            Depo.ayarlar(this).edit().putInt("saat_modu_seviye", gorunum.seviye).apply()
            parlaklikUygula()
        }
    }

    private fun parlaklikUygula() {
        window.attributes = window.attributes.apply { screenBrightness = PARLAKLIK[gorunum.seviye] }
    }

    private fun ustunuGizle() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ustunuGizle()
    }

    companion object {
        /** Seviyeye göre pencere parlaklığı (0–1). */
        private val PARLAKLIK = floatArrayOf(0.02f, 0.25f, 0.7f)
    }
}
