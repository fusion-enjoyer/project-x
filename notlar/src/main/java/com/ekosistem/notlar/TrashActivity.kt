package com.ekosistem.notlar

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TrashActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: View

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        depo = NotDeposu(this)

        bosDurum = findViewById(R.id.bosDurum)
        adapter = NotAdapter(
            onTikla = { not -> secenekler(not) },
            onUzunBas = { not -> secenekler(not) }
        )
        adapter.vurgu = Renkler.vurgu(this)
        adapter.kartRengi = androidx.core.content.ContextCompat.getColor(this, R.color.kart)
        adapter.secimRengi = adapter.kartRengi

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        yenile()
    }

    private fun yenile() {
        Thread {
            val notlar = try {
                depo.copListele()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                adapter.guncelle(notlar)
                if (notlar.isEmpty()) {
                    BosDurum.goster(
                        bosDurum,
                        R.drawable.ic_cop,
                        getString(R.string.cop_bos),
                        getString(R.string.cop_bos_aciklama)
                    )
                } else {
                    bosDurum.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun secenekler(not: Not) {
        AltSayfa(this)
            .baslik(not.baslik)
            .madde(R.drawable.ic_geri_al, getString(R.string.geri_yukle)) {
                Thread {
                    depo.geriYukle(not.uri)
                    runOnUiThread { yenile() }
                }.start()
            }
            .madde(R.drawable.ic_sil, getString(R.string.kalici_sil), tehlikeli = true) {
                Thread {
                    depo.kaliciSil(not.uri)
                    runOnUiThread { yenile() }
                }.start()
            }
            .goster()
    }
}
