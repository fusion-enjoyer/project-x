package com.ekosistem.saat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.ekosistem.tasarim.Tasarim
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
    private var gorev = Gorev.YOK
    private var sorular = emptyList<Gorev.Soru>()
    private var soruNo = 0
    private var girdi = ""
    private lateinit var gorevSoru: TextView
    private lateinit var gorevCevap: TextView
    private lateinit var gorevIlerleme: TextView
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
        gorev = alarm.gorev

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
            ipucu = getString(if (gorev > Gorev.YOK) R.string.gorev_icin_kaydir else R.string.kapatmak_icin_kaydir)
            kaydirildi = { kapat() }
        }
        altYaz(alarm, ertelenebilir)
        if (gorev > Gorev.YOK && intent.getBooleanExtra(EK_GOREV, false)) gorevAc()
        // Uyurken yanlışlıkla geri tuşu ya da geri hareketi alarmı susturmasın.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (gorev > Gorev.YOK && intent.getBooleanExtra(EK_GOREV, false)) gorevAc()
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

    /** Görevli alarmda kapatmak önce görev panelini açar; görev çözülünce [bitir]. */
    private fun kapat() {
        if (gorev > Gorev.YOK) gorevAc() else bitir()
    }

    private fun bitir() {
        CalmaHizmeti.eylem(this, CalmaHizmeti.EYLEM_KAPAT)
        finish()
    }

    // --- Kapatma görevi ---

    private fun gorevAc() {
        val panel = findViewById<LinearLayout>(R.id.calmaGorev)
        if (panel.visibility == View.VISIBLE) return
        sorular = Gorev.sorular(gorev)
        soruNo = 0
        girdi = ""
        // Tuş takımına yer: büyük saat küçülür, kaydırma düğmesi gider.
        findViewById<View>(R.id.calmaTarih).visibility = View.GONE
        findViewById<TextView>(R.id.calmaSaat).textSize = 44f
        findViewById<View>(R.id.calmaKapat).visibility = View.GONE
        panel.visibility = View.VISIBLE
        panel.removeAllViews()
        val d = resources.displayMetrics.density

        gorevIlerleme = TextView(this).apply {
            setTextColor(0xFF8B867D.toInt()); textSize = 13f
        }
        gorevSoru = TextView(this).apply {
            setTextColor(0xFFF2EFE9.toInt()); textSize = 34f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        gorevCevap = TextView(this).apply {
            setTextColor(0xFFF2EFE9.toInt()); textSize = 28f
            gravity = Gravity.CENTER
            setHintTextColor(0xFFE5736B.toInt())
            setBackgroundResource(R.drawable.bg_calma_hap)
        }
        panel.addView(gorevIlerleme)
        panel.addView(gorevSoru, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (4 * d).toInt() })
        panel.addView(gorevCevap, LinearLayout.LayoutParams(-1, (52 * d).toInt()).apply {
            topMargin = (10 * d).toInt(); bottomMargin = (8 * d).toInt()
        })
        val tuslar = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )
        for (sira in tuslar) {
            val satir = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (t in sira) {
                val tus = TextView(this).apply {
                    text = t
                    gravity = Gravity.CENTER
                    textSize = 24f
                    setTextColor(if (t == "✓") 0xFFFFFFFF.toInt() else 0xFFF2EFE9.toInt())
                    setBackgroundResource(R.drawable.bg_calma_hap)
                    if (t == "✓") backgroundTintList = android.content.res.ColorStateList.valueOf(Tasarim.vurgu(this@CalmaActivity))
                    contentDescription = when (t) {
                        "⌫" -> getString(R.string.gorev_sil)
                        "✓" -> getString(R.string.gorev_tamam)
                        else -> t
                    }
                    setOnClickListener { gorevTus(t) }
                }
                satir.addView(tus, LinearLayout.LayoutParams(0, (50 * d).toInt(), 1f).apply {
                    setMargins((4 * d).toInt(), (3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt())
                })
            }
            panel.addView(satir, LinearLayout.LayoutParams(-1, -2))
        }
        gorevYaz()
    }

    private fun gorevTus(t: String) {
        when (t) {
            "⌫" -> girdi = girdi.dropLast(1)
            "✓" -> {
                if (Gorev.dogruMu(sorular[soruNo], girdi)) {
                    soruNo++
                    girdi = ""
                    if (soruNo >= sorular.size) {
                        bitir()
                        return
                    }
                } else {
                    girdi = ""
                    gorevCevap.hint = getString(R.string.gorev_yanlis)
                    gorevCevap.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            }
            else -> if (girdi.length < 6) girdi += t
        }
        gorevYaz()
    }

    private fun gorevYaz() {
        gorevIlerleme.text = getString(R.string.gorev_ilerleme, soruNo + 1, sorular.size)
        gorevSoru.text = "${sorular[soruNo].metin} = ?"
        gorevCevap.text = girdi
        if (girdi.isNotEmpty()) gorevCevap.hint = ""
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

        const val EK_GOREV = "gorev"

        /** [gorev] doğruysa ekran doğrudan görev paneliyle açılır (bildirimdeki "Kapat"). */
        fun niyet(context: Context, id: Int, gorev: Boolean = false): Intent =
            Intent(context, CalmaActivity::class.java)
                .putExtra(AlarmKurucu.EK_ID, id)
                .putExtra(EK_GOREV, gorev)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
