package com.ekosistem.saat

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/** Süre doldu ekranı: çalma ekranının düzeni, "+1 dk" ve kaydırarak durdur. */
class ZamanDolduActivity : ComponentActivity() {

    private val isleyici = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_calma)
        val calan = ZamanlayiciHizmeti.calanlar.firstOrNull()?.let { Depo.zamanlayici(this, it) }
        if (calan == null) {
            finish()
            return
        }
        findViewById<TextView>(R.id.calmaTarih).setText(R.string.sure_doldu)
        findViewById<TextView>(R.id.calmaSaat).text = Zamanlayici.bicim(calan.sure)
        findViewById<TextView>(R.id.calmaEtiket).apply {
            text = calan.etiket
            visibility = if (calan.etiket.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.calmaAzalt).visibility = View.GONE
        findViewById<View>(R.id.calmaArtir).visibility = View.GONE
        findViewById<TextView>(R.id.calmaErtele).apply {
            setText(R.string.bir_dk_ekle)
            setOnClickListener { eylem(ZamanlayiciHizmeti.EYLEM_EKLE) }
        }
        findViewById<KaydirmaDugmesi>(R.id.calmaKapat).apply {
            ipucu = getString(R.string.durdurmak_icin_kaydir)
            kaydirildi = { eylem(ZamanlayiciHizmeti.EYLEM_DURDUR) }
        }
        findViewById<TextView>(R.id.calmaAlt).text = ""
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    override fun onResume() {
        super.onResume()
        ZamanlayiciHizmeti.dinleyici = { runOnUiThread { if (ZamanlayiciHizmeti.calanlar.isEmpty()) finish() } }
        if (ZamanlayiciHizmeti.calanlar.isEmpty()) finish()
    }

    override fun onDestroy() {
        ZamanlayiciHizmeti.dinleyici = null
        isleyici.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            eylem(ZamanlayiciHizmeti.EYLEM_DURDUR)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun eylem(e: String) {
        ZamanlayiciHizmeti.eylem(this, e)
        finish()
    }
}
