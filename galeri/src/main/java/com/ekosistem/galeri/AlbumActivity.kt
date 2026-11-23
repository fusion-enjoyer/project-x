package com.ekosistem.galeri

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.ekosistem.tasarim.BosDurum

/** Bir klasörün fotoğraf ve videoları. */
class AlbumActivity : AppCompatActivity() {

    private lateinit var vm: MedyaViewModel
    private lateinit var izgara: IzgaraAdapter
    private val albumId by lazy { intent.getStringExtra(EK_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        vm = ViewModelProvider(this)[MedyaViewModel::class.java]
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<TextView>(R.id.baslik).text = intent.getStringExtra(EK_AD).orEmpty()
        val liste = findViewById<RecyclerView>(R.id.liste)
        val bos = findViewById<View>(R.id.bosDurum)
        val yukleniyor = findViewById<View>(R.id.yukleniyor)
        izgara = IzgaraAdapter(this) { Kaynak.Klasor(albumId) }
        izgara.yerlestir(liste, sutunSayisi(this))
        vm.durum.observe(this) { d ->
            yukleniyor.visibility = if (d is MedyaDurumu.Yukleniyor) View.VISIBLE else View.GONE
            val medyalar = (d as? MedyaDurumu.Hazir)?.medyalar ?: emptyList()
            if (d is MedyaDurumu.Hazir && medyalar.isEmpty() || d is MedyaDurumu.IzinYok) {
                liste.visibility = View.GONE
                BosDurum.goster(bos, R.drawable.ic_klasor, getString(R.string.album_bos), getString(R.string.album_bos_aciklama))
            } else {
                liste.visibility = View.VISIBLE
                bos.visibility = View.GONE
            }
            findViewById<TextView>(R.id.altBaslik).text =
                if (d is MedyaDurumu.Hazir) resources.getQuantityString(R.plurals.oge_sayisi, medyalar.size, medyalar.size) else ""
            izgara.ayarla(medyalar)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.yukle(Kaynak.Klasor(albumId))
    }

    companion object {
        const val EK_ID = "album_id"
        const val EK_AD = "album_ad"
    }
}
