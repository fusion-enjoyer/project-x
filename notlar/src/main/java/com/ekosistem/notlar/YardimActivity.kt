package com.ekosistem.notlar

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * "Nasıl yazılır": uygulamadaki biçimlerin rehberi. Metin `res/raw/yardim.md`
 * (dile göre `raw-tr`); anlatım ve örnekler uygulamanın kendi biçimlendiricisiyle
 * çizilir, yani rehberde görünen, notta görünecek olanın aynısıdır.
 */
class YardimActivity : AppCompatActivity() {

    private lateinit var bicimci: MarkdownBicimci
    private var yogunluk = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yardim)
        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        yogunluk = resources.displayMetrics.density
        bicimci = MarkdownBicimci(this)
        // Rehber her zaman biçimli görünür; kullanıcı kaynak modundaysa da.
        bicimci.kaynakModu = false

        val kaynak = resources.openRawResource(R.raw.yardim).bufferedReader().use { it.readText() }
        val kutu = findViewById<LinearLayout>(R.id.yardimKutu)
        // Metnin sığacağı genişlik: ekran eksi kenar boşlukları (örnek kartı ayrıca daralır).
        val genislik = resources.displayMetrics.widthPixels - (40 * yogunluk).toInt()
        // Başlık ekranın üstünde; metinde not başlığı yok, hiçbir parça başlıklı biçimlenmez.
        for (parca in Yardim.bol(kaynak)) {
            when (parca.tur) {
                Yardim.METIN -> kutu.addView(
                    bicimli(parca.icerik, genislik),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = (12 * yogunluk).toInt() }
                )
                else -> kutu.addView(
                    ornekKarti(parca, genislik - (28 * yogunluk).toInt()),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = (16 * yogunluk).toInt() }
                )
            }
        }
    }

    /** Notun editördeki görünümü: aynı yazı tipi, boy ve satır aralığı. */
    private fun bicimli(metin: String, genislik: Int): TextView {
        val tv = TextView(this)
        tv.typeface = YaziTipleri.yazi(this)
        tv.textSize = bicimci.govdeSp.toFloat()
        tv.setLineSpacing(4 * yogunluk, 1f)
        tv.setTextColor(ContextCompat.getColor(this, R.color.metin))
        val s = SpannableStringBuilder(metin)
        bicimci.uygula(s, -1, genislik, basliksiz = true)
        tv.text = s
        return tv
    }

    /** Üstte yazılan (eş aralıklı, işaretler görünür), altta görünüşü. */
    private fun ornekKarti(parca: Yardim.Parca, genislik: Int): LinearLayout {
        val kart = LinearLayout(this)
        kart.orientation = LinearLayout.VERTICAL
        kart.setBackgroundResource(R.drawable.bg_kart)
        val ic = (14 * yogunluk).toInt()
        kart.setPadding(ic, ic, ic, ic)

        kart.addView(etiket(R.string.yardim_yazdigin))
        val ham = TextView(this)
        ham.typeface = Typeface.MONOSPACE
        ham.textSize = (bicimci.govdeSp - 2).toFloat()
        ham.setLineSpacing(3 * yogunluk, 1f)
        ham.setTextColor(ContextCompat.getColor(this, R.color.metin))
        ham.text = parca.icerik
        kart.addView(ham)

        if (parca.tur == Yardim.ORNEK) {
            kart.addView(
                etiket(R.string.yardim_gorunusu),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (12 * yogunluk).toInt() }
            )
            kart.addView(bicimli(parca.icerik, genislik))
        }
        return kart
    }

    private fun etiket(metin: Int): TextView {
        val tv = TextView(this)
        tv.setText(metin)
        tv.textSize = 12f
        tv.setTextColor(ContextCompat.getColor(this, R.color.metin_ikincil))
        tv.setPadding(0, 0, 0, (4 * yogunluk).toInt())
        return tv
    }
}
