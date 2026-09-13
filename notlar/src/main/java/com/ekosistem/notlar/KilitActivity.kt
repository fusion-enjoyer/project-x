package com.ekosistem.notlar

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * PIN ekranı. Üç işi görür: kilidi açma, yeni PIN kurma, PIN'i kaldırma.
 * Hangi işi yapacağı "kip" ek bilgisiyle belirlenir.
 */
class KilitActivity : AppCompatActivity() {

    private lateinit var noktalar: LinearLayout
    private lateinit var aciklama: TextView
    private lateinit var baslik: TextView
    private var girilen = StringBuilder()
    private var ilkPin: String? = null
    private var kip = KIP_AC
    private var iptalSinyali: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kilit)
        kip = intent.getIntExtra("kip", KIP_AC)

        baslik = findViewById(R.id.kilitBaslik)
        aciklama = findViewById(R.id.kilitAciklama)
        noktalar = findViewById(R.id.noktalar)

        baslik.setText(
            when (kip) {
                KIP_KUR -> R.string.pin_olustur
                KIP_KALDIR -> R.string.pin_kaldir
                else -> R.string.kilit_ac
            }
        )
        aciklama.setText(R.string.pin_4_hane)

        onBackPressedDispatcher.addCallback(this, geriTusu)
        tuslariKur()
        noktalariGuncelle()
        if (kip == KIP_AC) parmakIziniKur()
    }

    override fun onDestroy() {
        super.onDestroy()
        iptalSinyali?.cancel()
    }

    /** Kilidi açmadan geri dönülemez; uygulama kapanır. */
    private val geriTusu = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (kip == KIP_AC) finishAffinity() else finish()
        }
    }

    // --- Tuş takımı ---

    private fun tuslariKur() {
        val y = resources.displayMetrics.density
        val kap = findViewById<LinearLayout>(R.id.tuslar)
        val satirlar = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        for (satir in satirlar) {
            val yatay = LinearLayout(this)
            yatay.orientation = LinearLayout.HORIZONTAL
            for (tus in satir) {
                val dugme = TextView(this)
                dugme.text = tus
                dugme.textSize = 24f
                dugme.gravity = Gravity.CENTER
                dugme.setTextColor(ContextCompat.getColor(this, R.color.metin))
                if (tus.isNotEmpty()) {
                    val sekil = GradientDrawable()
                    sekil.shape = GradientDrawable.OVAL
                    sekil.setColor(ContextCompat.getColor(this, R.color.kart))
                    dugme.background = sekil
                    dugme.setOnClickListener { tusaBasildi(tus) }
                }
                val lp = LinearLayout.LayoutParams((72 * y).toInt(), (72 * y).toInt())
                lp.setMargins((10 * y).toInt(), (8 * y).toInt(), (10 * y).toInt(), (8 * y).toInt())
                yatay.addView(dugme, lp)
            }
            kap.addView(
                yatay,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun tusaBasildi(tus: String) {
        if (tus == "⌫") {
            if (girilen.isNotEmpty()) girilen.deleteCharAt(girilen.length - 1)
        } else if (girilen.length < PIN_UZUNLUK) {
            girilen.append(tus)
        }
        noktalariGuncelle()
        if (girilen.length == PIN_UZUNLUK) {
            noktalar.postDelayed({ tamamlandi() }, 120)
        }
    }

    private fun noktalariGuncelle() {
        val y = resources.displayMetrics.density
        val vurgu = Renkler.vurgu(this)
        val bos = ContextCompat.getColor(this, R.color.ayrac)
        noktalar.removeAllViews()
        for (i in 0 until PIN_UZUNLUK) {
            val nokta = View(this)
            val sekil = GradientDrawable()
            sekil.shape = GradientDrawable.OVAL
            sekil.setColor(if (i < girilen.length) vurgu else bos)
            nokta.background = sekil
            val lp = LinearLayout.LayoutParams((14 * y).toInt(), (14 * y).toInt())
            lp.setMargins((9 * y).toInt(), 0, (9 * y).toInt(), 0)
            noktalar.addView(nokta, lp)
        }
    }

    private fun tamamlandi() {
        val pin = girilen.toString()
        girilen = StringBuilder()
        when (kip) {
            KIP_KUR -> {
                val ilk = ilkPin
                if (ilk == null) {
                    ilkPin = pin
                    baslik.setText(R.string.pin_tekrar)
                    noktalariGuncelle()
                } else if (ilk == pin) {
                    Kilit.pinKur(this, pin)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    ilkPin = null
                    baslik.setText(R.string.pin_olustur)
                    hata(R.string.pin_eslesmedi)
                }
            }
            KIP_KALDIR -> {
                if (Kilit.dogrula(this, pin)) {
                    Kilit.pinKaldir(this)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    hata(R.string.pin_yanlis)
                }
            }
            else -> {
                if (Kilit.dogrula(this, pin)) {
                    Kilit.oturumAcik = true
                    setResult(RESULT_OK)
                    finish()
                } else {
                    hata(R.string.pin_yanlis)
                }
            }
        }
    }

    private fun hata(mesaj: Int) {
        aciklama.setText(mesaj)
        aciklama.setTextColor(0xFFE24B4A.toInt())
        noktalariGuncelle()
        aciklama.postDelayed({
            aciklama.setText(R.string.pin_4_hane)
            aciklama.setTextColor(ContextCompat.getColor(this, R.color.metin_ikincil))
        }, 1800)
    }

    // --- Parmak izi (Android 9+) ---

    private fun parmakIziniKur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val dugme = findViewById<TextView>(R.id.btnParmakIzi)
        dugme.visibility = View.VISIBLE
        dugme.setTextColor(Renkler.vurgu(this))
        dugme.setOnClickListener { parmakIziSor() }
        parmakIziSor()
    }

    private fun parmakIziSor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            iptalSinyali?.cancel()
            val sinyal = CancellationSignal()
            iptalSinyali = sinyal
            BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.kilit_ac))
                .setNegativeButton(
                    getString(R.string.pin_kullan),
                    mainExecutor
                ) { _, _ -> }
                .build()
                .authenticate(
                    sinyal,
                    mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            sonuc: BiometricPrompt.AuthenticationResult?
                        ) {
                            Kilit.oturumAcik = true
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                )
        } catch (_: Exception) {
            // Donanım yoksa ya da kayıtlı parmak izi yoksa sessizce PIN'e düşülür.
        }
    }

    companion object {
        const val KIP_AC = 0
        const val KIP_KUR = 1
        const val KIP_KALDIR = 2
        private const val PIN_UZUNLUK = 4
    }
}
