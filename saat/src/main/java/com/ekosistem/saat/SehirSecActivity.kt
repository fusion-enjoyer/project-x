package com.ekosistem.saat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Şehir seç: arama Türkçe karakterden bağımsız ("istanbul" = "İstanbul"), ülkeye göre de bulur. */
class SehirSecActivity : AppCompatActivity() {

    private var gorunen: List<Sehirler.Sehir> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sehir_sec)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        val listeGorunumu = findViewById<ListView>(R.id.sehirListesi)
        val ekli = Depo.sehirler(this).toSet()
        val hepsi = Sehirler.hepsi().filter { it.id !in ekli }
        gorunen = hepsi
        val uyarlayici = object : BaseAdapter() {
            override fun getCount() = gorunen.size
            override fun getItem(i: Int) = gorunen[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, eski: View?, ust: ViewGroup): View {
                val v = eski ?: LayoutInflater.from(this@SehirSecActivity).inflate(R.layout.item_sehir_sec, ust, false)
                val s = gorunen[i]
                v.findViewById<TextView>(R.id.secAd).text = s.ad
                v.findViewById<TextView>(R.id.secAlt).text = "${s.ulke} · ${Sehirler.gmt(s.id)}"
                return v
            }
        }
        listeGorunumu.adapter = uyarlayici
        listeGorunumu.setOnItemClickListener { _, _, i, _ ->
            Depo.sehirleriKaydet(this, Depo.sehirler(this) + gorunen[i].id)
            SaatWidget.guncelle(this)
            finish()
        }
        findViewById<EditText>(R.id.sehirAra).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val sorgu = DunyaSaati.sadelestir(s?.toString().orEmpty().trim())
                gorunen = if (sorgu.isEmpty()) hepsi else hepsi.filter { it.arama.contains(sorgu) }
                    .sortedByDescending { DunyaSaati.sadelestir(it.ad).startsWith(sorgu) }
                uyarlayici.notifyDataSetChanged()
            }
        })
    }
}
