package com.ekosistem.notlar

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
import androidx.core.content.ContextCompat

/**
 * PIN ekranı. Dört işi görür: uygulamanın kilidini açma, kilitli bir notu açma,
 * yeni PIN kurma, PIN'i kaldırma. Hangi işi yapacağı "kip" ek bilgisiyle belirlenir.
 */
class KilitActivity : TemelActivity() {

    private lateinit var noktalar: LinearLayout
    private lateinit var aciklama: TextView
    private lateinit var baslik: TextView
    private var girilen = StringBuilder()
    private var ilkPin: String? = null
    private var kip = KIP_AC
    private var iptalSinyali: CancellationSignal? = null

    private val acmaKipi: Boolean get() = kip == KIP_AC || kip == KIP_NOT

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kilit)
        kip = intent.getIntExtra("kip", KIP_AC)

        baslik = findViewById(R.id.kilitBaslik)
        aciklama = findViewById(R.id.kilitAciklama)
        noktalar = findViewById(R.id.noktalar)
        findViewById<android.widget.ImageView>(R.id.kilitIkon).imageTintList =
            android.content.res.ColorStateList.valueOf(Renkler.vurgu(this))

        baslik.setText(
            when (kip) {
                KIP_KUR -> R.string.pin_olustur
                KIP_KALDIR -> R.string.pin_kaldir
                KIP_NOT -> R.string.kilitli_not
                else -> R.string.kilit_ac
            }
        )
        aciklama.setText(if (kip == KIP_NOT) R.string.kilitli_not_ozet else R.string.pin_4_hane)

        onBackPressedDispatcher.addCallback(this, geriTusu)
        tuslariKur()
        noktalariGuncelle()
        if (acmaKipi && Kilit.parmakIziAcik(this)) parmakIziniKur()
        // Önceki denemelerden kalan bekleme varsa ekran açılır açılmaz görünsün.
        if (kip != KIP_KUR && Kilit.beklemeKalan(this) > 0) beklemeyiGoster()
    }

    override fun onDestroy() {
        super.onDestroy()
        iptalSinyali?.cancel()
    }

    /**
     * Uygulama kilidi açılmadan geri dönülemez, uygulama kapanır. Not kilidinde
     * ise geri tuşu sadece notu kapatır.
     */
    private val geriTusu = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when (kip) {
                KIP_AC -> finishAffinity()
                KIP_NOT -> {
                    setResult(RESULT_CANCELED)
                    finish()
                }
                else -> finish()
            }
        }
    }

    // --- Tuş takımı ---

    /**
     * Tuşlar ekrana göre boyutlanır: sabit 72 dp geniş telefonda ekranın ortasında
     * küçük bir ada gibi kalıyordu. Genişliğin ve yüksekliğin izin verdiği kadar
     * büyür (en çok 88 dp, telefon kilit ekranı ölçüsü), küçük ekranda 64'e iner.
     */
    private fun tuslariKur() {
        val y = resources.displayMetrics.density
        val genislikDp = resources.displayMetrics.widthPixels / y
        val yukseklikDp = resources.displayMetrics.heightPixels / y
        val boslukDp = 14f
        val tusDp = minOf((genislikDp - 64f) / 3f - boslukDp * 2, yukseklikDp * 0.1f)
            .coerceIn(64f, 88f)
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
                dugme.textSize = if (tus == "⌫") 24f else 32f
                dugme.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                dugme.gravity = Gravity.CENTER
                if (tus.isNotEmpty()) dugme.contentDescription = if (tus == "⌫") getString(R.string.sil) else tus
                dugme.setTextColor(ContextCompat.getColor(this, R.color.metin))
                if (tus.isNotEmpty()) {
                    val sekil = GradientDrawable()
                    sekil.shape = GradientDrawable.OVAL
                    sekil.setColor(ContextCompat.getColor(this, R.color.kart))
                    dugme.background = sekil
                    dugme.setOnClickListener { tusaBasildi(tus) }
                }
                val lp = LinearLayout.LayoutParams((tusDp * y).toInt(), (tusDp * y).toInt())
                lp.setMargins((boslukDp * y).toInt(), (8 * y).toInt(), (boslukDp * y).toInt(), (8 * y).toInt())
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
        // Yanlış denemeler sınırı aştıysa bekleme bitene kadar tuşlar çalışmaz.
        if (kip != KIP_KUR && Kilit.beklemeKalan(this) > 0) {
            beklemeyiGoster()
            return
        }
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
            val lp = LinearLayout.LayoutParams((16 * y).toInt(), (16 * y).toInt())
            lp.setMargins((11 * y).toInt(), 0, (11 * y).toInt(), 0)
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
                    parmakIziniSor()
                } else {
                    ilkPin = null
                    baslik.setText(R.string.pin_olustur)
                    hata(R.string.pin_eslesmedi)
                }
            }
            KIP_KALDIR -> {
                if (Kilit.dogrula(this, pin).also { if (it) Kilit.dogruPin(this) else Kilit.yanlisPin(this) }) {
                    Kilit.pinKaldir(this)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    hata(R.string.pin_yanlis)
                }
            }
            else -> {
                if (Kilit.dogrula(this, pin).also { if (it) Kilit.dogruPin(this) else Kilit.yanlisPin(this) }) {
                    acildi()
                } else {
                    hata(R.string.pin_yanlis)
                }
            }
        }
    }

    private fun acildi() {
        Kilit.dogruPin(this)
        if (kip == KIP_AC) Kilit.oturumAcik = true
        setResult(RESULT_OK)
        finish()
    }

    /** PIN kurulduktan sonra parmak izi ayrıca sorulur; kendiliğinden açılmaz. */
    private fun parmakIziniSor() {
        if (!Kilit.parmakIziDonanimi(this)) {
            setResult(RESULT_OK)
            finish()
            return
        }
        AltSayfa(this)
            .mesaj(getString(R.string.parmak_izi_soru))
            .madde(R.drawable.ic_kilit, getString(R.string.parmak_izi_kullan)) {
                Prefs.parmakIziKaydet(this, true)
            }
            .madde(R.drawable.ic_kilit_kucuk, getString(R.string.sadece_pin)) {
                Prefs.parmakIziKaydet(this, false)
            }
            .kapaninca {
                setResult(RESULT_OK)
                finish()
            }
            .goster()
    }

    /** Kalan bekleme süresini saniye saniye gösterir; bitince normale döner. */
    private fun beklemeyiGoster() {
        val kalan = Kilit.beklemeKalan(this)
        aciklama.removeCallbacks(beklemeSayaci)
        if (kalan <= 0) {
            aciklama.setText(if (kip == KIP_NOT) R.string.kilitli_not_ozet else R.string.pin_4_hane)
            aciklama.setTextColor(ContextCompat.getColor(this, R.color.metin_ikincil))
            return
        }
        aciklama.text = getString(R.string.pin_bekle, ((kalan + 999) / 1000).toInt())
        aciklama.setTextColor(0xFFE24B4A.toInt())
        aciklama.postDelayed(beklemeSayaci, 1000)
    }

    private val beklemeSayaci = Runnable { beklemeyiGoster() }

    private fun hata(mesaj: Int) {
        if (kip != KIP_KUR && Kilit.beklemeKalan(this) > 0) {
            noktalariGuncelle()
            beklemeyiGoster()
            return
        }
        aciklama.setText(mesaj)
        aciklama.setTextColor(0xFFE24B4A.toInt())
        noktalariGuncelle()
        aciklama.postDelayed({
            aciklama.setText(if (kip == KIP_NOT) R.string.kilitli_not_ozet else R.string.pin_4_hane)
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
                .setTitle(getString(if (kip == KIP_NOT) R.string.kilitli_not else R.string.kilit_ac))
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
                            acildi()
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
        const val KIP_NOT = 3
        private const val PIN_UZUNLUK = 4
    }
}
