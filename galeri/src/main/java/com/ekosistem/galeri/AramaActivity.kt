package com.ekosistem.galeri

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.ekosistem.tasarim.BosDurum

/**
 * Arama: dosya adı, albüm, ay/yıl ("ekim 2024"), tür ("video", "ekran görüntüsü").
 * Her şey telefonda; hiçbir şey dışarı gitmez.
 */
class AramaActivity : AppCompatActivity() {

    private val isleyici = Handler(Looper.getMainLooper())
    private lateinit var vm: MedyaViewModel
    private lateinit var alan: EditText
    private lateinit var liste: RecyclerView
    private lateinit var bosDurum: View
    private lateinit var sayi: TextView
    private lateinit var izgara: IzgaraAdapter
    private val ara = Runnable { sorgula() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arama)
        vm = ViewModelProvider(this)[MedyaViewModel::class.java]
        alan = findViewById(R.id.etAra)
        liste = findViewById(R.id.liste)
        bosDurum = findViewById(R.id.bosDurum)
        sayi = findViewById(R.id.sonucSayisi)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        izgara = IzgaraAdapter(this) { Kaynak.Sorgu(alan.text.toString().trim()) }
        izgara.yerlestir(liste, sutunSayisi(this, 100))

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
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                isleyici.removeCallbacks(ara)
                sorgula()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(alan.windowToken, 0)
                true
            } else false
        }
        if (savedInstanceState == null) {
            alan.requestFocus()
            alan.post {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(alan, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        vm.durum.observe(this) { goster(it) }
        sorgula()
    }

    private fun bosSorgu() = Arama.kelimeler(alan.text.toString().trim()).isEmpty()

    private fun sorgula() {
        if (bosSorgu()) {
            goster(null)
            return
        }
        vm.yukle(Kaynak.Sorgu(alan.text.toString().trim()))
    }

    private fun goster(d: MedyaDurumu?) {
        if (d == null || bosSorgu()) {
            liste.visibility = View.GONE
            sayi.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_baslik), getString(R.string.ara_aciklama))
            return
        }
        val bulunan = (d as? MedyaDurumu.Hazir)?.medyalar ?: return
        if (bulunan.isEmpty()) {
            liste.visibility = View.GONE
            sayi.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_sonuc_yok), getString(R.string.ara_sonuc_yok_aciklama))
            izgara.ayarla(emptyList())
            return
        }
        bosDurum.visibility = View.GONE
        liste.visibility = View.VISIBLE
        sayi.visibility = View.VISIBLE
        sayi.text = resources.getQuantityString(R.plurals.sonuc_sayisi, bulunan.size, bulunan.size)
        izgara.ayarla(bulunan) { liste.scrollToPosition(0) }
    }

    override fun onDestroy() {
        isleyici.removeCallbacks(ara)
        super.onDestroy()
    }
}
