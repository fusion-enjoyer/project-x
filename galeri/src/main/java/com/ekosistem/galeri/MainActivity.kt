package com.ekosistem.galeri

import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ekosistem.tasarim.BosDurum
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/** Ana ekran: bütün fotoğraflar (aylara göre) ya da albümler. */
class MainActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private val isleyici = Handler(Looper.getMainLooper())
    private lateinit var liste: RecyclerView
    private lateinit var bosDurum: View
    private lateinit var izgara: IzgaraAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private var albumSekmesi = false
    private var nesil = 0
    private var degisti = false

    // Kamera yeni fotoğraf çekince ya da kopya kaydedilince liste kendiliğinden yenilensin.
    private val gozlemci = object : ContentObserver(isleyici) {
        override fun onChange(selfChange: Boolean) {
            MedyaDeposu.gecersizKil()
            degisti = true
            isleyici.removeCallbacks(yenile)
            isleyici.postDelayed(yenile, 600)
        }
    }
    private val yenile = Runnable { if (!isFinishing) yukle() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        liste = findViewById(R.id.liste)
        bosDurum = findViewById(R.id.bosDurum)
        izgara = IzgaraAdapter(this) { Kaynak.Hepsi }
        albumAdapter = AlbumAdapter(this)
        albumSekmesi = savedInstanceState?.getBoolean("album") ?: false

        val ara = findViewById<View>(R.id.btnAra)
        ara.setOnClickListener { startActivity(Intent(this, AramaActivity::class.java)) }
        ipucuVer(ara)
        findViewById<View>(R.id.sekmeFotograflar).setOnClickListener { sekmeSec(false) }
        findViewById<View>(R.id.sekmeAlbumler).setOnClickListener { sekmeSec(true) }
        findViewById<View>(R.id.kismiUyari).setOnClickListener { Izin.iste(this) }
        sekmeBoya()
        yerlestir()

        if (!Izin.okunabilir(this)) Izin.iste(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("album", albumSekmesi)
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, gozlemci)
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, gozlemci)
        }
        yukle()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(gozlemci)
        super.onStop()
    }

    private fun sekmeSec(album: Boolean) {
        if (album == albumSekmesi) {
            liste.smoothScrollToPosition(0)
            return
        }
        albumSekmesi = album
        sekmeBoya()
        yerlestir()
        yukle()
    }

    private fun sekmeBoya() {
        val vurgu = Tasarim.vurgu(this)
        for ((id, secili) in listOf(R.id.sekmeFotograflar to !albumSekmesi, R.id.sekmeAlbumler to albumSekmesi)) {
            val t = findViewById<TextView>(id)
            t.isSelected = secili
            t.backgroundTintList = ColorStateList.valueOf(if (secili) vurgu else ContextCompat.getColor(this, TR.color.kart))
            t.setTextColor(if (secili) Tasarim.vurguUzeri(this) else ContextCompat.getColor(this, TR.color.metin))
        }
    }

    private fun yerlestir() {
        if (albumSekmesi) albumAdapter.yerlestir(liste, maxOf(2, resources.configuration.screenWidthDp / 180))
        else izgara.yerlestir(liste, maxOf(4, sutunSayisi(this, 100)))
    }

    private fun yukle() {
        findViewById<View>(R.id.kismiUyari).visibility = if (Izin.kismi(this)) View.VISIBLE else View.GONE
        if (!Izin.okunabilir(this)) {
            izinYok()
            return
        }
        val n = ++nesil
        val taze = degisti
        degisti = false
        yurutucu.execute {
            val hepsi = if (taze) MedyaDeposu.hepsi(this) else MedyaDeposu.onbellek ?: MedyaDeposu.hepsi(this)
            val albumler = if (albumSekmesi) Albumler.kur(hepsi) else emptyList()
            runOnUiThread {
                if (n != nesil || isDestroyed) return@runOnUiThread
                if (hepsi.isEmpty()) {
                    liste.visibility = View.GONE
                    BosDurum.goster(bosDurum, R.drawable.ic_foto, getString(R.string.bos_baslik), getString(R.string.bos_aciklama))
                    return@runOnUiThread
                }
                bosDurum.visibility = View.GONE
                liste.visibility = View.VISIBLE
                if (albumSekmesi) albumAdapter.ayarla(albumler) else izgara.ayarla(hepsi)
            }
        }
    }

    private fun izinYok() {
        liste.visibility = View.GONE
        // Kullanıcı "bir daha sorma" dediyse sistem penceresi açılmaz; ayarlara yönlendir.
        val sorulabilir = Izin.istenecekler().any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) } || !soruldu
        BosDurum.goster(
            bosDurum, R.drawable.ic_kilit, getString(R.string.izin_baslik), getString(R.string.izin_aciklama),
            if (sorulabilir) getString(R.string.izin_ver) to { Izin.iste(this) }
            else getString(R.string.ayarlari_ac) to {
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
                Unit
            }
        )
    }

    private var soruldu = false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != Izin.ISTEK) return
        soruldu = true
        degisti = true
        yukle()
    }

    override fun onDestroy() {
        isleyici.removeCallbacks(yenile)
        yurutucu.shutdown()
        super.onDestroy()
    }
}
