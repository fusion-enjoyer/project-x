package com.ekosistem.saat

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Şehir seç: harf bölümlü liste, sağda harf dizini, altta arama çubuğu.
 * Arama Türkçe karakterden bağımsız ("istanbul" = "İstanbul"), ülkeye göre de bulur.
 */
class SehirSecActivity : AppCompatActivity() {

    /** Listedeki bir satır: harf başlığı ya da şehir. */
    private sealed class Satir {
        data class Baslik(val harf: String) : Satir()
        data class Kayit(val sehir: Sehirler.Sehir) : Satir()
    }

    private var satirlar: List<Satir> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sehir_sec)
        klavyeyeUyum()
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        val listeGorunumu = findViewById<ListView>(R.id.sehirListesi)
        val dizin = findViewById<HarfDizini>(R.id.harfDizini)
        val balon = findViewById<TextView>(R.id.harfBalonu)
        val ekli = Depo.sehirler(this).toSet()
        val hepsi = Sehirler.hepsi().filter { it.id !in ekli }

        fun harf(s: Sehirler.Sehir): String {
            val c = DunyaSaati.sadelestir(s.ad).firstOrNull()?.uppercaseChar()
            return if (c != null && c in 'A'..'Z') c.toString() else "#"
        }

        /** Arama yokken harf başlıklı, varken düz (ilgiye göre sıralı) liste. */
        fun doldur(sorgu: String) {
            if (sorgu.isEmpty()) {
                val sonuc = ArrayList<Satir>(hepsi.size + 30)
                var onceki = ""
                for (s in hepsi) {
                    val h = harf(s)
                    if (h != onceki) { sonuc.add(Satir.Baslik(h)); onceki = h }
                    sonuc.add(Satir.Kayit(s))
                }
                satirlar = sonuc
                dizin.visibility = View.VISIBLE
                dizin.harfler = sonuc.filterIsInstance<Satir.Baslik>().map { it.harf }
            } else {
                satirlar = hepsi.filter { it.arama.contains(sorgu) }
                    .sortedByDescending { DunyaSaati.sadelestir(it.ad).startsWith(sorgu) }
                    .map { Satir.Kayit(it) }
                dizin.visibility = View.GONE
            }
        }
        doldur("")

        val uyarlayici = object : BaseAdapter() {
            override fun getCount() = satirlar.size
            override fun getItem(i: Int) = satirlar[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(i: Int) = if (satirlar[i] is Satir.Baslik) 0 else 1
            override fun isEnabled(i: Int) = satirlar[i] is Satir.Kayit
            override fun getView(i: Int, eski: View?, ust: ViewGroup): View {
                val inf = LayoutInflater.from(this@SehirSecActivity)
                return when (val s = satirlar[i]) {
                    is Satir.Baslik -> (eski ?: inf.inflate(R.layout.item_sehir_baslik, ust, false)).also {
                        (it as TextView).text = s.harf
                    }
                    is Satir.Kayit -> (eski ?: inf.inflate(R.layout.item_sehir_sec, ust, false)).also {
                        it.findViewById<TextView>(R.id.secAd).text = s.sehir.ad
                        it.findViewById<TextView>(R.id.secAlt).text = "${s.sehir.ulke} · ${Sehirler.gmt(s.sehir.id)}"
                    }
                }
            }
        }
        listeGorunumu.adapter = uyarlayici
        listeGorunumu.setOnItemClickListener { _, _, i, _ ->
            val s = satirlar[i] as? Satir.Kayit ?: return@setOnItemClickListener
            Depo.sehirleriKaydet(this, Depo.sehirler(this) + s.sehir.id)
            Widgetlar.hepsiniGuncelle(this)
            finish()
        }

        dizin.harfSecildi = { h ->
            val sira = satirlar.indexOfFirst { it is Satir.Baslik && it.harf == h }
            if (sira >= 0) listeGorunumu.setSelection(sira)
            balon.text = h
            balon.visibility = View.VISIBLE
        }
        dizin.birakildi = { balon.visibility = View.GONE }

        findViewById<EditText>(R.id.sehirAra).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                doldur(DunyaSaati.sadelestir(s?.toString().orEmpty().trim()))
                uyarlayici.notifyDataSetChanged()
                listeGorunumu.setSelection(0)
            }
        })
    }

    /**
     * Arama çubuğu altta olduğu için klavyenin üstüne çıkmalı. Android 15 ve
     * sonrasında ADJUST_RESIZE çalışmıyor; klavye yüksekliğini alt boşluk yapıyoruz.
     */
    private fun klavyeyeUyum() {
        val kok = findViewById<View>(R.id.sehirKok)
        if (Build.VERSION.SDK_INT >= 30) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(kok) { v, kenarlar ->
                val cubuk = kenarlar.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val klavye = kenarlar.getInsets(WindowInsetsCompat.Type.ime()).bottom
                v.setPadding(cubuk.left, cubuk.top, cubuk.right, maxOf(cubuk.bottom, klavye))
                kenarlar
            }
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        } else {
            kok.fitsSystemWindows = true
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
    }
}
