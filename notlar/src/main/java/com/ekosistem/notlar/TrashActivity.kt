package com.ekosistem.notlar

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TrashActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        depo = NotDeposu(this)

        bosDurum = findViewById(R.id.bosDurum)
        adapter = NotAdapter(
            onTikla = { not -> secenekler(not) },
            onUzunBas = { not -> secenekler(not) }
        )

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
                bosDurum.visibility = if (notlar.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun secenekler(not: Not) {
        val etiketler = arrayOf(getString(R.string.geri_yukle), getString(R.string.kalici_sil))
        AlertDialog.Builder(this)
            .setTitle(not.baslik)
            .setItems(etiketler) { _, hangi ->
                Thread {
                    when (hangi) {
                        0 -> depo.geriYukle(not.uri)
                        1 -> depo.kaliciSil(not.uri)
                    }
                    runOnUiThread { yenile() }
                }.start()
            }
            .show()
    }
}
