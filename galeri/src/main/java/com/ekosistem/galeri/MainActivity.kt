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
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.ekosistem.tasarim.BosDurum
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer
import com.ekosistem.tasarim.R as TR

/**
 * Ana ekran: bütün fotoğraflar (aylara göre) ya da albümler. Altta ortada küçük,
 * yüzen kapsül gezinme (ailenin sekme hapı dili).
 */
class MainActivity : AppCompatActivity() {

    private val isleyici = Handler(Looper.getMainLooper())
    private lateinit var vm: MedyaViewModel
    private lateinit var liste: RecyclerView
    private lateinit var bosDurum: View
    private lateinit var yukleniyor: View
    private lateinit var izgara: IzgaraAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private var sekme = SEKME_FOTOGRAFLAR
    private var soruldu = false
    private val vurgu by lazy { Tasarim.vurgu(this) }

    /** Albümler sekmesinde geri, uygulamadan çıkmaz; Fotoğraflar'a döner. */
    private val geriSekme = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = sekmeyeGec(SEKME_FOTOGRAFLAR)
    }

    // Kamera yeni fotoğraf çekince ya da kopya kaydedilince liste kendiliğinden yenilensin.
    private val gozlemci = object : ContentObserver(isleyici) {
        override fun onChange(selfChange: Boolean) {
            MedyaDeposu.gecersizKil()
            isleyici.removeCallbacks(yenile)
            isleyici.postDelayed(yenile, 600)
        }
    }
    private val yenile = Runnable { if (!isFinishing) vm.yenile() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vm = ViewModelProvider(this)[MedyaViewModel::class.java]
        liste = findViewById(R.id.liste)
        bosDurum = findViewById(R.id.bosDurum)
        yukleniyor = findViewById(R.id.yukleniyor)
        izgara = IzgaraAdapter(this) { Kaynak.Hepsi }
        albumAdapter = AlbumAdapter(this)
        sekme = savedInstanceState?.getInt(EK_SEKME) ?: SEKME_FOTOGRAFLAR
        onBackPressedDispatcher.addCallback(this, geriSekme)

        val ara = findViewById<View>(R.id.btnAra)
        ara.setOnClickListener { startActivity(Intent(this, AramaActivity::class.java)) }
        ipucuVer(ara)
        findViewById<View>(R.id.kismiUyari).setOnClickListener { Izin.iste(this) }

        sekmeleriKur()
        yerlestir()
        vm.durum.observe(this) { ciz(it) }
        if (!Izin.okunabilir(this)) Izin.iste(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EK_SEKME, sekme)
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, gozlemci)
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, gozlemci)
        }
        findViewById<View>(R.id.kismiUyari).visibility = if (Izin.kismi(this)) View.VISIBLE else View.GONE
        // İzin ayarlardan değişmiş olabilir; önbellek varsa ucuz.
        vm.yukle(Kaynak.Hepsi, albumler = true)
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(gozlemci)
        super.onStop()
    }

    private fun ciz(d: MedyaDurumu) {
        yukleniyor.visibility = if (d is MedyaDurumu.Yukleniyor) View.VISIBLE else View.GONE
        when (d) {
            MedyaDurumu.Yukleniyor -> {
                liste.visibility = View.INVISIBLE
                bosDurum.visibility = View.GONE
            }
            MedyaDurumu.IzinYok -> izinYok()
            is MedyaDurumu.Hazir -> {
                if (d.medyalar.isEmpty()) {
                    liste.visibility = View.GONE
                    BosDurum.goster(bosDurum, R.drawable.ic_foto, getString(R.string.bos_baslik), getString(R.string.bos_aciklama))
                } else {
                    bosDurum.visibility = View.GONE
                    liste.visibility = View.VISIBLE
                }
                izgara.ayarla(d.medyalar)
                albumAdapter.ayarla(d.albumler)
            }
        }
    }

    // --- Kapsül gezinme -------------------------------------------------------

    private class Hap(val no: Int, val kutu: LinearLayout, val ikon: ImageView, val yazi: TextView)

    private val haplar = ArrayList<Hap>()

    private fun sekmeleriKur() {
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val d = resources.displayMetrics.density
        val sekmeler = listOf(
            Triple(SEKME_FOTOGRAFLAR, R.drawable.ic_foto, R.string.fotograflar),
            Triple(SEKME_ALBUMLER, R.drawable.ic_klasor, R.string.albumler)
        )
        for ((no, ikon, ad) in sekmeler) {
            val kapsayici = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = getString(ad)
                setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
                minimumWidth = (48 * d).toInt()
                setBackgroundResource(R.drawable.bg_sekme)
                setOnClickListener { sekmeyeGec(no) }
            }
            val simge = ImageView(this).apply {
                setImageResource(ikon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            kapsayici.addView(simge, LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            val yazi = TextView(this).apply {
                text = getString(ad)
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(vurgu)
                maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            kapsayici.addView(yazi, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = (6 * d).toInt() })
            kutu.addView(kapsayici, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (44 * d).toInt()))
            haplar.add(Hap(no, kapsayici, simge, yazi))
            ipucuVer(kapsayici)
        }
        sekmeDurumunuGuncelle(animasyonlu = false)
    }

    private fun sekmeyeGec(yeni: Int) {
        if (yeni == sekme) {
            // Seçili sekmeye yeniden dokunmak başa kaydırır.
            liste.smoothScrollToPosition(0)
            return
        }
        sekme = yeni
        sekmeDurumunuGuncelle(animasyonlu = true)
        yerlestir()
    }

    /**
     * Seçili sekme hap olur (simge + ad), diğerleri yalnız simge. Yer değişimi,
     * hap rengi ve simge renkleri [animasyonlu] ise yumuşak geçişle olur.
     */
    private fun sekmeDurumunuGuncelle(animasyonlu: Boolean) {
        geriSekme.isEnabled = sekme != SEKME_FOTOGRAFLAR
        findViewById<TextView>(R.id.baslik).setText(if (sekme == SEKME_ALBUMLER) R.string.albumler else R.string.fotograflar)
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val pasif = ContextCompat.getColor(this, TR.color.metin_ikincil)
        val pastel = Tasarim.pastel(vurgu)
        if (animasyonlu) {
            val gecis = TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(Fade())
                duration = 260
                interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            }
            // Kapsül içeriği kadar geniş: genişlik değişimi de yumuşak olsun.
            TransitionManager.beginDelayedTransition(kutu.parent as ViewGroup, gecis)
        }
        for (h in haplar) {
            val secili = h.no == sekme
            h.kutu.isSelected = secili
            h.yazi.visibility = if (secili) View.VISIBLE else View.GONE
            val hedefHap = if (secili) pastel else (pastel and 0x00FFFFFF)
            val hedefSimge = if (secili) vurgu else pasif
            val eskiHap = h.kutu.backgroundTintList?.defaultColor ?: (pastel and 0x00FFFFFF)
            val eskiSimge = h.ikon.imageTintList?.defaultColor ?: hedefSimge
            if (animasyonlu) {
                android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), eskiHap, hedefHap).apply {
                    duration = 260
                    addUpdateListener { h.kutu.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                    start()
                }
                android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), eskiSimge, hedefSimge).apply {
                    duration = 260
                    addUpdateListener { h.ikon.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                    start()
                }
            } else {
                h.kutu.backgroundTintList = ColorStateList.valueOf(hedefHap)
                h.ikon.imageTintList = ColorStateList.valueOf(hedefSimge)
            }
        }
    }

    private fun yerlestir() {
        if (sekme == SEKME_ALBUMLER) albumAdapter.yerlestir(liste, maxOf(2, resources.configuration.screenWidthDp / 180))
        else izgara.yerlestir(liste, sutunSayisi(this, 100))
    }

    // --- Eylemler -------------------------------------------------------------

    private fun izinYok() {
        liste.visibility = View.GONE
        // Kullanıcı "bir daha sorma" dediyse sistem penceresi açılmaz; ayarlara yönlendir.
        val sorulabilir = !soruldu ||
            Izin.istenecekler().any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != Izin.ISTEK) return
        soruldu = true
        findViewById<View>(R.id.kismiUyari).visibility = if (Izin.kismi(this)) View.VISIBLE else View.GONE
        vm.yukle(Kaynak.Hepsi, albumler = true, taze = true)
    }

    override fun onDestroy() {
        isleyici.removeCallbacks(yenile)
        super.onDestroy()
    }

    companion object {
        const val SEKME_FOTOGRAFLAR = 0
        const val SEKME_ALBUMLER = 1
        private const val EK_SEKME = "sekme"
    }
}
