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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
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
import com.ekosistem.tasarim.ipucuVer

/**
 * Ana ekran, üç sekme: Fotoğraflar (güne göre), Albümler, Ara. Üstte büyük ince
 * başlık, altta ortada yüzen yalnız ikonlu gezinme hapı.
 */
class MainActivity : AppCompatActivity() {

    private val isleyici = Handler(Looper.getMainLooper())
    private lateinit var vm: MedyaViewModel
    private lateinit var araVm: MedyaViewModel
    private lateinit var liste: RecyclerView
    private lateinit var bosDurum: View
    private lateinit var yukleniyor: View
    private lateinit var baslik: TextView
    private lateinit var alan: EditText
    private lateinit var izgara: IzgaraAdapter
    private lateinit var araIzgara: IzgaraAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private var sekme = SEKME_FOTOGRAFLAR
    private var soruldu = false

    /** Fotoğraflar dışındaki sekmede geri, uygulamadan çıkmaz; Fotoğraflar'a döner. */
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
    private val ara = Runnable { sorgula() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val saglayici = ViewModelProvider(this)
        vm = saglayici[MedyaViewModel::class.java]
        araVm = saglayici["ara", MedyaViewModel::class.java]
        liste = findViewById(R.id.liste)
        bosDurum = findViewById(R.id.bosDurum)
        yukleniyor = findViewById(R.id.yukleniyor)
        baslik = findViewById(R.id.baslik)
        alan = findViewById(R.id.etAra)
        izgara = IzgaraAdapter(this) { Kaynak.Hepsi }
        araIzgara = IzgaraAdapter(this) { Kaynak.Sorgu(alan.text.toString().trim()) }
        albumAdapter = AlbumAdapter(this)
        sekme = savedInstanceState?.getInt(EK_SEKME) ?: SEKME_FOTOGRAFLAR
        onBackPressedDispatcher.addCallback(this, geriSekme)
        findViewById<View>(R.id.kismiUyari).setOnClickListener { Izin.iste(this) }
        aramaAlaniniKur()

        sekmeleriKur()
        sekmeyiGoster()
        vm.durum.observe(this) { if (sekme != SEKME_ARA) ciz(it) }
        araVm.durum.observe(this) { if (sekme == SEKME_ARA) aramaCiz(it) }
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

    // --- Çizim ----------------------------------------------------------------

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

    private fun aramaCiz(d: MedyaDurumu?) {
        yukleniyor.visibility = View.GONE
        if (d == null || Arama.kelimeler(alan.text.toString()).isEmpty()) {
            liste.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_baslik), getString(R.string.ara_aciklama))
            return
        }
        if (d is MedyaDurumu.IzinYok) { izinYok(); return }
        val bulunan = (d as? MedyaDurumu.Hazir)?.medyalar ?: return
        if (bulunan.isEmpty()) {
            liste.visibility = View.GONE
            BosDurum.goster(bosDurum, R.drawable.ic_ara, getString(R.string.ara_sonuc_yok), getString(R.string.ara_sonuc_yok_aciklama))
        } else {
            bosDurum.visibility = View.GONE
            liste.visibility = View.VISIBLE
        }
        araIzgara.ayarla(bulunan) { liste.scrollToPosition(0) }
    }

    // --- Arama ----------------------------------------------------------------

    private fun aramaAlaniniKur() {
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
                klavye(false)
                true
            } else false
        }
    }

    private fun sorgula() {
        if (sekme != SEKME_ARA) return
        val sorgu = alan.text.toString().trim()
        if (Arama.kelimeler(sorgu).isEmpty()) aramaCiz(null) else araVm.yukle(Kaynak.Sorgu(sorgu))
    }

    private fun klavye(ac: Boolean) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (ac) {
            alan.requestFocus()
            alan.post { imm.showSoftInput(alan, InputMethodManager.SHOW_IMPLICIT) }
        } else {
            imm.hideSoftInputFromWindow(alan.windowToken, 0)
            alan.clearFocus()
        }
    }

    // --- Gezinme hapı ---------------------------------------------------------

    private val dugmeler = ArrayList<Pair<Int, ImageView>>()

    private fun sekmeleriKur() {
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val d = resources.displayMetrics.density
        val sekmeler = listOf(
            Triple(SEKME_FOTOGRAFLAR, R.drawable.ic_foto, R.string.fotograflar),
            Triple(SEKME_ALBUMLER, R.drawable.ic_albumler, R.string.albumler),
            Triple(SEKME_ARA, R.drawable.ic_ara, R.string.ara)
        )
        val renk = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nav_ikon))
        for ((no, ikon, ad) in sekmeler) {
            val dugme = ImageView(this).apply {
                setImageResource(ikon)
                imageTintList = renk
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = getString(ad)
                setOnClickListener { sekmeyeGec(no) }
            }
            val lp = LinearLayout.LayoutParams((68 * d).toInt(), (56 * d).toInt())
            lp.gravity = Gravity.CENTER_VERTICAL
            kutu.addView(dugme, lp)
            dugmeler += no to dugme
            ipucuVer(dugme)
        }
    }

    private fun sekmeyeGec(yeni: Int) {
        if (yeni == sekme) {
            // Seçili sekmeye yeniden dokunmak başa kaydırır.
            liste.smoothScrollToPosition(0)
            if (yeni == SEKME_ARA) klavye(true)
            return
        }
        sekme = yeni
        sekmeyiGoster()
        if (yeni == SEKME_ARA) klavye(true) else klavye(false)
    }

    private fun sekmeyiGoster() {
        geriSekme.isEnabled = sekme != SEKME_FOTOGRAFLAR
        for ((no, dugme) in dugmeler) {
            val secili = no == sekme
            dugme.isSelected = secili
            dugme.background = if (secili) ContextCompat.getDrawable(this, R.drawable.bg_nav_secili) else null
        }
        baslik.setText(
            when (sekme) {
                SEKME_ALBUMLER -> R.string.albumler
                SEKME_ARA -> R.string.ara
                else -> R.string.fotograflar
            }
        )
        alan.visibility = if (sekme == SEKME_ARA) View.VISIBLE else View.GONE
        when (sekme) {
            SEKME_ALBUMLER -> albumAdapter.yerlestir(liste, maxOf(2, resources.configuration.screenWidthDp / 180))
            SEKME_ARA -> araIzgara.yerlestir(liste, sutunSayisi(this))
            else -> izgara.yerlestir(liste, sutunSayisi(this))
        }
        if (sekme == SEKME_ARA) aramaCiz(araVm.durum.value.takeIf { alan.text.isNotBlank() })
        else vm.durum.value?.let { ciz(it) }
    }

    // --- İzin -----------------------------------------------------------------

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
        isleyici.removeCallbacks(ara)
        super.onDestroy()
    }

    companion object {
        const val SEKME_FOTOGRAFLAR = 0
        const val SEKME_ALBUMLER = 1
        const val SEKME_ARA = 2
        private const val EK_SEKME = "sekme"
    }
}
