package com.ekosistem.galeri

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.ekosistem.tasarim.BosDurum
import java.util.concurrent.Executors

/** Bir klasörün fotoğraf ve videoları. */
class AlbumActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private lateinit var izgara: IzgaraAdapter
    private val albumId by lazy { intent.getStringExtra(EK_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<TextView>(R.id.baslik).text = intent.getStringExtra(EK_AD).orEmpty()
        izgara = IzgaraAdapter(this) { Kaynak.Klasor(albumId) }
        izgara.yerlestir(findViewById(R.id.liste), maxOf(4, sutunSayisi(this, 100)))
    }

    override fun onStart() {
        super.onStart()
        yurutucu.execute {
            val liste = if (Izin.okunabilir(this)) MedyaDeposu.kaynaktan(this, Kaynak.Klasor(albumId)) else emptyList()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val r = findViewById<RecyclerView>(R.id.liste)
                if (liste.isEmpty()) {
                    r.visibility = View.GONE
                    BosDurum.goster(findViewById(R.id.bosDurum), R.drawable.ic_klasor, getString(R.string.album_bos), getString(R.string.album_bos_aciklama))
                } else {
                    r.visibility = View.VISIBLE
                    findViewById<View>(R.id.bosDurum).visibility = View.GONE
                }
                findViewById<TextView>(R.id.altBaslik).text = resources.getQuantityString(R.plurals.oge_sayisi, liste.size, liste.size)
                izgara.ayarla(liste)
            }
        }
    }

    override fun onDestroy() {
        yurutucu.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EK_ID = "album_id"
        const val EK_AD = "album_ad"
    }
}
