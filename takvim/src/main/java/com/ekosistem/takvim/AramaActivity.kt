package com.ekosistem.takvim

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.BosDurum
import java.util.TimeZone
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/**
 * Etkinlik arama: başlık, konum ve açıklamada, geçmiş bir yıl ile gelecek iki yıl içinde.
 * Yaklaşanlar önce (yakından uzağa), sonra geçmiş (yeniden eskiye).
 */
class AramaActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private val isleyici = Handler(Looper.getMainLooper())
    private var nesil = 0
    private lateinit var liste: LinearLayout
    private lateinit var kaydirici: ScrollView
    private lateinit var bosDurum: View
    private lateinit var alan: EditText

    private val ara = Runnable { sorgula() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arama)
        liste = findViewById(R.id.aramaListe)
        kaydirici = findViewById(R.id.aramaKaydirici)
        bosDurum = findViewById(R.id.bosDurum)
        alan = findViewById(R.id.etAra)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        alan.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Her harfte değil, yazmayı bıraktıktan kısa süre sonra aranır.
                isleyici.removeCallbacks(ara)
                isleyici.postDelayed(ara, 250)
            }
        })
        alan.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { isleyici.removeCallbacks(ara); sorgula(); true } else false
        }
        // Arama ekranı açılınca yazmaya hazır: imleç alanda, klavye açık.
        alan.requestFocus()
        alan.post {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(alan, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sorgula()
    }

    private fun sorgula() {
        val sorgu = alan.text.toString().trim()
        val kelimeler = Arama.kelimeler(sorgu)
        if (kelimeler.isEmpty()) {
            kaydirici.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_baslik), getString(R.string.ara_aciklama))
            return
        }
        if (!TakvimDeposu.izinVar(this)) return
        val n = ++nesil
        yurutucu.execute {
            val simdi = System.currentTimeMillis()
            val bugun = Gun.bugun(simdi, TimeZone.getDefault())
            val hepsi = TakvimDeposu.ornekler(this, bugun - 365, bugun + 730, aciklamaDahil = true)
            val bulunan = hepsi.filter { Arama.eslesir(kelimeler, it) }
            val (yaklasan, gecmis) = Arama.sirala(bulunan, simdi)
            runOnUiThread { if (n == nesil && !isDestroyed) goster(yaklasan, gecmis, bugun) }
        }
    }

    private fun goster(yaklasan: List<Ornek>, gecmis: List<Ornek>, bugun: Int) {
        liste.removeAllViews()
        if (yaklasan.isEmpty() && gecmis.isEmpty()) {
            kaydirici.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_sonuc_yok), getString(R.string.ara_sonuc_yok_aciklama))
            return
        }
        bosDurum.visibility = View.GONE
        kaydirici.visibility = View.VISIBLE
        bolum(getString(R.string.yaklasan_bolum, yaklasan.size), yaklasan, bugun)
        bolum(getString(R.string.gecmis_bolum, gecmis.size), gecmis, bugun)
        kaydirici.scrollTo(0, 0)
    }

    private fun bolum(baslik: String, ornekler: List<Ornek>, bugun: Int) {
        if (ornekler.isEmpty()) return
        val t = TextView(this)
        t.text = baslik
        t.textSize = 13f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
        t.setPadding(4, (16 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        liste.addView(t)
        val inflater = LayoutInflater.from(this)
        for (o in ornekler) {
            val v = inflater.inflate(R.layout.item_etkinlik, liste, false)
            v.findViewById<View>(R.id.etkRenk).backgroundTintList = android.content.res.ColorStateList.valueOf(o.renk)
            v.findViewById<TextView>(R.id.etkBaslik).text = o.baslik.ifBlank { getString(R.string.basliksiz) }
            val gun = Metinler.widgetGunuUzun(this, o.ilkGun, bugun)
            v.findViewById<TextView>(R.id.etkAlt).text = gun + " · " + Metinler.ornekAltYazisi(this, o, o.ilkGun) +
                (if (o.tekrarli) " · " + getString(R.string.tekrarli_kisa) else "")
            v.setOnClickListener {
                startActivity(
                    Intent(this, DetayActivity::class.java)
                        .putExtra(DetayActivity.EK_ID, o.etkinlikId).putExtra(DetayActivity.EK_BAS, o.baslangic).putExtra(DetayActivity.EK_BIT, o.bitis)
                )
            }
            liste.addView(v)
        }
    }

    override fun onDestroy() {
        isleyici.removeCallbacks(ara)
        super.onDestroy()
    }
}
