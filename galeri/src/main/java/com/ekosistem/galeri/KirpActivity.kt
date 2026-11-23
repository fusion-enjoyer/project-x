package com.ekosistem.galeri

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ekosistem.tasarim.Tasarim
import java.util.concurrent.Executors

/**
 * Kırp ve döndür. Sonuç her zaman yeni bir dosya; orijinal olduğu gibi kalır.
 * Önizleme ekran boyunda, kaydederken asıl çözünürlükten kesilir ([Kaydedici]).
 */
class KirpActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private lateinit var kirpma: KirpmaGorunumu
    private lateinit var yukleniyor: ProgressBar
    private lateinit var uri: Uri
    private var yon = 0
    private var aci = 0
    /** Yönü uygulanmış, kullanıcı döndürmesiz önizleme. */
    private var temel: Bitmap? = null
    private var gosterilen: Bitmap? = null
    private var secilenOran = 0
    private var kaydediliyor = false

    private data class Oran(val ad: Int, val deger: Float?)

    private val oranlar by lazy {
        listOf(
            Oran(R.string.oran_serbest, null),
            Oran(R.string.oran_kare, 1f),
            Oran(R.string.oran_4_3, 4f / 3f),
            Oran(R.string.oran_3_4, 3f / 4f),
            Oran(R.string.oran_16_9, 16f / 9f),
            Oran(R.string.oran_9_16, 9f / 16f)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kirp)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        uri = intent.data ?: run { finish(); return }
        yon = intent.getIntExtra(EK_YON, 0)
        aci = savedInstanceState?.getInt("aci") ?: intent.getIntExtra(EK_ACI, 0)
        secilenOran = savedInstanceState?.getInt("oran") ?: 0
        kirpma = findViewById(R.id.kirpma)
        yukleniyor = findViewById(R.id.yukleniyor)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok)) { v, kenar ->
            val c = kenar.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(c.left, c.top, c.right, c.bottom)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<View>(R.id.btnIptal).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSifirla).setOnClickListener {
            aci = 0
            secilenOran = 0
            oranlariBoya()
            onizle()
        }
        findViewById<View>(R.id.btnKaydet).setOnClickListener { kaydet() }
        findViewById<View>(R.id.btnSola).setOnClickListener { aci = Kirpma.aciDuzelt(aci - 90); onizle() }
        findViewById<View>(R.id.btnSaga).setOnClickListener { aci = Kirpma.aciDuzelt(aci + 90); onizle() }
        oranlariKur()

        val hedef = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        yurutucu.execute {
            val b = runCatching { Cozucu.tamResim(this, uri, yon, hedef) }.getOrNull()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                yukleniyor.visibility = View.GONE
                if (b == null) {
                    Toast.makeText(this, R.string.acilamadi, Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                temel = b
                onizle()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("aci", aci)
        outState.putInt("oran", secilenOran)
    }

    private fun onizle() {
        val t = temel ?: return
        gosterilen = if (aci == 0) t else {
            val m = android.graphics.Matrix().apply { postRotate(aci.toFloat()) }
            Bitmap.createBitmap(t, 0, 0, t.width, t.height, m, true)
        }
        kirpma.oran = oranlar[secilenOran].deger
        kirpma.resim(gosterilen)
    }

    private fun oranlariKur() {
        val kap = findViewById<LinearLayout>(R.id.oranlar)
        val d = resources.displayMetrics.density
        oranlar.forEachIndexed { i, o ->
            val t = TextView(this)
            t.text = getString(o.ad)
            t.textSize = 14f
            t.gravity = android.view.Gravity.CENTER
            t.setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            t.setBackgroundResource(R.drawable.bg_izleyici_cip)
            t.setOnClickListener {
                secilenOran = i
                oranlariBoya()
                kirpma.oran = o.deger
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (36 * d).toInt())
            if (i > 0) lp.leftMargin = (8 * d).toInt()
            kap.addView(t, lp)
        }
        oranlariBoya()
    }

    private fun oranlariBoya() {
        val kap = findViewById<LinearLayout>(R.id.oranlar)
        val vurgu = Tasarim.vurgu(this)
        for (i in 0 until kap.childCount) {
            val t = kap.getChildAt(i) as TextView
            val secili = i == secilenOran
            t.isSelected = secili
            t.backgroundTintList = if (secili) ColorStateList.valueOf(vurgu) else null
            t.setTextColor(if (secili) Tasarim.uzerindekiRenk(vurgu) else 0xFFFFFFFF.toInt())
        }
    }

    private fun kaydet() {
        if (kaydediliyor) return
        if (aci == 0 && kirpma.tamami()) {
            Toast.makeText(this, R.string.degisiklik_yok, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Izin.yazabilir(this)) {
            Izin.iste(this)
            return
        }
        kaydediliyor = true
        yukleniyor.visibility = View.VISIBLE
        val kutu = kirpma.secim()
        val toplam = Kirpma.aciDuzelt(yon + aci)
        val ad = intent.getStringExtra(EK_AD).orEmpty()
        val mime = intent.getStringExtra(EK_MIME).orEmpty()
        val tarih = intent.getLongExtra(EK_TARIH, System.currentTimeMillis())
        yurutucu.execute {
            val sonuc = runCatching { Kaydedici.kaydet(this, uri, ad, mime, tarih, kutu, toplam) }.getOrNull()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                kaydediliyor = false
                yukleniyor.visibility = View.GONE
                if (sonuc != null) {
                    MedyaDeposu.gecersizKil()
                    Toast.makeText(this, getString(R.string.kopya_kaydedildi, "Pictures/" + Kaydedici.KLASOR), Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.kaydedilemedi, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        yurutucu.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EK_YON = "yon"
        const val EK_ACI = "aci"
        const val EK_AD = "ad"
        const val EK_MIME = "mime"
        const val EK_TARIH = "tarih"
    }
}
