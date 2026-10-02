package com.ekosistem.saat

import android.content.Context
import android.content.Intent
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
import com.ekosistem.tasarim.ipucuVer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alarm çalıyor ekranı: kilit ekranının üstünde açılır, ekranı uyandırır.
 * Büyük saat, etiket, süresi değiştirilebilen erteleme (Samsung'daki gibi
 * +/−) ve kaydırarak kapatma. Geri tuşu alarmı kapatmaz.
 */
class CalmaActivity : ComponentActivity() {

    private var id = -1
    private var ertelemeDk = 10
    private val isleyici = Handler(Looper.getMainLooper())
    private val saatiGuncelle = object : Runnable {
        override fun run() {
            saatYaz()
            isleyici.postDelayed(this, 1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kilitUstundeAc()
        setContentView(R.layout.activity_calma)
        id = intent.getIntExtra(AlarmKurucu.EK_ID, CalmaHizmeti.calanId)
        if (id < 0) id = CalmaHizmeti.calanId
        val alarm = Depo.alarm(this, id)
        if (alarm == null || CalmaHizmeti.calanId != id) {
            finish()
            return
        }
        ertelemeDk = alarm.ertelemeDk

        findViewById<TextView>(R.id.calmaEtiket).apply {
            text = alarm.etiket
            visibility = if (alarm.etiket.isBlank()) View.GONE else View.VISIBLE
        }
        val ertelenebilir = alarm.ertelemeSiniri == 0 || alarm.ertelemeSayisi < alarm.ertelemeSiniri
        findViewById<View>(R.id.calmaErteleSatiri).visibility = if (ertelenebilir) View.VISIBLE else View.GONE
        findViewById<View>(R.id.calmaErtele).setOnClickListener { ertele() }
        findViewById<View>(R.id.calmaAzalt).setOnClickListener { sureDegistir(-1) }
        findViewById<View>(R.id.calmaArtir).setOnClickListener { sureDegistir(+1) }
        ipucuVer(findViewById(R.id.calmaAzalt), findViewById(R.id.calmaArtir))
        ertelemeYaz()

        findViewById<KaydirmaDugmesi>(R.id.calmaKapat).apply {
            ipucu = getString(R.string.kapatmak_icin_kaydir)
            kaydirildi = { kapat() }
        }
        altYaz(alarm, ertelenebilir)
        // Uyurken yanlışlıkla geri tuşu ya da geri hareketi alarmı susturmasın.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    override fun onResume() {
        super.onResume()
        CalmaHizmeti.dinleyici = {
            runOnUiThread { if (CalmaHizmeti.calanId != id) finish() }
        }
        if (CalmaHizmeti.calanId != id) finish()
        isleyici.post(saatiGuncelle)
    }

    override fun onPause() {
        super.onPause()
        isleyici.removeCallbacks(saatiGuncelle)
    }

    override fun onDestroy() {
        CalmaHizmeti.dinleyici = null
        super.onDestroy()
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (Depo.sesTusu(this)) {
                0 -> ertele()
                1 -> kapat()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun ertele() {
        val gorunur = findViewById<View>(R.id.calmaErteleSatiri).visibility == View.VISIBLE
        if (!gorunur) return
        CalmaHizmeti.eylem(this, CalmaHizmeti.EYLEM_ERTELE, ertelemeDk)
        finish()
    }

    private fun kapat() {
        CalmaHizmeti.eylem(this, CalmaHizmeti.EYLEM_KAPAT)
        finish()
    }

    private fun sureDegistir(yon: Int) {
        val sira = SURELER.indexOfFirst { it >= ertelemeDk }.let { if (it < 0) SURELER.lastIndex else it }
        // Listede olmayan bir süreden (alarmda 7 dk seçiliyse) en yakın komşuya geçilir.
        val yeni = when {
            SURELER[sira] == ertelemeDk -> sira + yon
            yon < 0 -> sira - 1
            else -> sira
        }
        ertelemeDk = SURELER[yeni.coerceIn(0, SURELER.lastIndex)]
        ertelemeYaz()
    }

    private fun ertelemeYaz() {
        findViewById<TextView>(R.id.calmaErtele).text = getString(R.string.ertele_dk, ertelemeDk)
        val azalt = findViewById<View>(R.id.calmaAzalt)
        val artir = findViewById<View>(R.id.calmaArtir)
        azalt.isEnabled = ertelemeDk > SURELER.first()
        artir.isEnabled = ertelemeDk < SURELER.last()
        azalt.alpha = if (azalt.isEnabled) 1f else 0.35f
        artir.alpha = if (artir.isEnabled) 1f else 0.35f
    }

    private fun altYaz(alarm: Alarm, ertelenebilir: Boolean) {
        val parcalar = mutableListOf<String>()
        when (Depo.sesTusu(this)) {
            0 -> if (ertelenebilir) parcalar.add(getString(R.string.ses_tusu_erteler))
            1 -> parcalar.add(getString(R.string.ses_tusu_kapatir))
        }
        if (!ertelenebilir) {
            parcalar.add(getString(R.string.erteleme_hakki_yok))
        } else if (alarm.ertelemeSiniri > 0) {
            val kalan = alarm.ertelemeSiniri - alarm.ertelemeSayisi
            parcalar.add(resources.getQuantityString(R.plurals.erteleme_hakki, kalan, kalan))
        }
        findViewById<TextView>(R.id.calmaAlt).text = parcalar.joinToString(" · ")
    }

    private fun saatYaz() {
        val simdi = Date()
        findViewById<TextView>(R.id.calmaSaat).text =
            Metinler.kucukOgleEki(android.text.format.DateFormat.getTimeFormat(this).format(simdi))
        val bicim = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        findViewById<TextView>(R.id.calmaTarih).text =
            SimpleDateFormat(bicim, Locale.getDefault()).format(simdi)
    }

    private fun kilitUstundeAc() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        /** Erteleme süresi seçenekleri (dk); 60'a kadar (Samsung'da da öyle). */
        val SURELER = intArrayOf(1, 5, 10, 15, 20, 30, 45, 60)

        fun niyet(context: Context, id: Int): Intent =
            Intent(context, CalmaActivity::class.java)
                .putExtra(AlarmKurucu.EK_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
