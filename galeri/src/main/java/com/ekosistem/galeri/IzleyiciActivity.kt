package com.ekosistem.galeri

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ekosistem.tasarim.AltSayfa
import java.util.concurrent.Executors

/**
 * Tam ekran görüntüleyici. Sayfalar parmakla sürüklenir (ViewPager2); resim
 * yakınlaştırılmışsa sürükleme resmi gezdirir, kenara gelince sayfa geçer.
 * Liste [Kaynak]'tan kurulur (hepsi, albüm, arama sonucu); başka uygulamadan
 * "Galeri ile aç" ile gelince tek sayfa.
 *
 * Döndür: önizleme döner, "Kopya kaydet" ile yeni dosya yazılır; orijinal aynen kalır.
 */
class IzleyiciActivity : AppCompatActivity() {

    /** Bir sayfa: medya deposundaki öge ya da dışarıdan gelen adres. */
    private data class Sayfa(val m: Medya?, val uri: Uri, val yon: Int) {
        val video get() = m?.video == true
    }

    private val yurutucu = Executors.newFixedThreadPool(2)
    private val isleyici = Handler(Looper.getMainLooper())
    private lateinit var sayfalar: ViewPager2
    private lateinit var ustCubuk: View
    private lateinit var altCubuk: View
    private lateinit var kaydet: View
    private val adapter = SayfaAdapter()

    private var liste: List<Sayfa> = emptyList()
    private var sira = 0
    /** Yalnız [sira]daki sayfaya uygulanan kullanıcı döndürmesi. */
    private var ekAci = 0
    private var cubuklarAcik = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_izleyici)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sayfalar = findViewById(R.id.sayfalar)
        ustCubuk = findViewById(R.id.ustCubuk)
        altCubuk = findViewById(R.id.altCubuk)
        kaydet = findViewById(R.id.btnKaydet)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok)) { _, kenar ->
            val c = kenar.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ustCubuk.setPadding(c.left + dp(8), c.top, c.right + dp(16), 0)
            altCubuk.setPadding(c.left, 0, c.right, c.bottom)
            kenar
        }

        sayfalar.adapter = adapter
        sayfalar.offscreenPageLimit = 1
        sayfalar.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == sira) return
                val eski = sira
                sira = position
                // Döndürülmüş ama kaydedilmemiş sayfadan çıkınca önizleme geri döner.
                if (ekAci != 0) { ekAci = 0; adapter.notifyItemChanged(eski) }
                cubuklariYaz()
            }
        })

        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPaylas).setOnClickListener { paylas() }
        findViewById<View>(R.id.btnDondur).setOnClickListener { dondur() }
        findViewById<View>(R.id.btnKirp).setOnClickListener { kirp() }
        findViewById<View>(R.id.btnBilgi).setOnClickListener { bilgi() }
        kaydet.setOnClickListener { kopyaKaydet() }

        ekAci = savedInstanceState?.getInt("aci") ?: 0
        val gelen = intent.data
        if (intent.action == Intent.ACTION_VIEW && gelen != null) {
            yurutucu.execute {
                val yon = Cozucu.exifYonu(this, gelen)
                runOnUiThread { listeyiKur(listOf(Sayfa(null, gelen, yon)), 0) }
            }
            return
        }
        val ilkSira = savedInstanceState?.getInt("sira") ?: intent.getIntExtra(EK_SIRA, 0)
        val id = savedInstanceState?.getLong("id") ?: intent.getLongExtra(EK_ID, -1)
        val kaynak = Kaynak.coz(intent.getStringExtra(EK_KAYNAK))
        yurutucu.execute {
            val l = MedyaDeposu.kaynaktan(this, kaynak).map { Sayfa(it, it.uri, it.yon) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (l.isEmpty()) { finish(); return@runOnUiThread }
                // Liste değiştiyse (yeni fotoğraf geldi) sıra kayar; kimlikten bul.
                var s = ilkSira
                if (l.getOrNull(s)?.m?.id != id) l.indexOfFirst { it.m?.id == id }.let { if (it >= 0) s = it }
                listeyiKur(l, s.coerceIn(0, l.size - 1))
            }
        }
    }

    private fun listeyiKur(l: List<Sayfa>, s: Int) {
        liste = l
        sira = s
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
        sayfalar.setCurrentItem(s, false)
        cubuklariYaz()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("sira", sira)
        outState.putInt("aci", ekAci)
        simdiki()?.m?.let { outState.putLong("id", it.id) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun simdiki(): Sayfa? = liste.getOrNull(sira)

    private fun cubuklariYaz() {
        val s = simdiki() ?: return
        kaydet.visibility = if (ekAci != 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnDondur).visibility = if (s.video) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnKirp).visibility = if (s.video) View.GONE else View.VISIBLE
        val baslik = findViewById<TextView>(R.id.izleyiciBaslik)
        val alt = findViewById<TextView>(R.id.izleyiciAlt)
        val m = s.m
        if (m != null) {
            baslik.text = Metinler.tamTarih(m.tarih)
            alt.text = getString(R.string.sira_n, sira + 1, liste.size) + " · " + m.albumAdi
        } else {
            baslik.text = s.uri.lastPathSegment.orEmpty()
            alt.text = ""
        }
    }

    // --- Sayfalar -------------------------------------------------------------

    private inner class SayfaAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = liste.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sayfa, parent, false)
            v.findViewById<ZoomGorunumu>(R.id.resim).dokunus = { cubuklariCevir() }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val s = liste[position]
            val v = h.itemView
            val resim = v.findViewById<ZoomGorunumu>(R.id.resim)
            val yukleniyor = v.findViewById<View>(R.id.yukleniyor)
            val oynat = v.findViewById<View>(R.id.btnOynat)
            oynat.visibility = if (s.video) View.VISIBLE else View.GONE
            oynat.setOnClickListener { videoOynat(s) }
            val aci = if (position == sira) ekAci else 0
            val anahtar = "${s.uri}|$aci"
            resim.tag = anahtar
            resim.setImageBitmap(null)
            resim.contentDescription = getString(if (s.video) R.string.video else R.string.fotograf) +
                (s.m?.let { ", " + Metinler.tamTarih(it.tarih) } ?: "")
            yukleniyor.visibility = View.VISIBLE
            val hedef = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            yurutucu.execute {
                if (resim.tag != anahtar) return@execute
                val bmp: Bitmap? = runCatching {
                    val m = s.m
                    if (s.video && m != null) Cozucu.kucukResim(this@IzleyiciActivity, m, hedef / 2)
                    else Cozucu.tamResim(this@IzleyiciActivity, s.uri, s.yon, hedef, aci)
                }.getOrNull()
                isleyici.post {
                    if (resim.tag != anahtar || isDestroyed) return@post
                    yukleniyor.visibility = View.GONE
                    resim.setImageBitmap(bmp)
                    if (bmp == null) Toast.makeText(this@IzleyiciActivity, R.string.acilamadi, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Eylemler -------------------------------------------------------------

    private fun cubuklariCevir() {
        cubuklarAcik = !cubuklarAcik
        val k = WindowInsetsControllerCompat(window, window.decorView)
        if (cubuklarAcik) {
            k.show(WindowInsetsCompat.Type.systemBars())
        } else {
            k.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            k.hide(WindowInsetsCompat.Type.systemBars())
        }
        for (v in listOf(ustCubuk, altCubuk)) {
            v.animate().cancel()
            if (cubuklarAcik) v.visibility = View.VISIBLE
            v.animate().alpha(if (cubuklarAcik) 1f else 0f).setDuration(150)
                .withEndAction { if (!cubuklarAcik) v.visibility = View.INVISIBLE }.start()
        }
    }

    private fun paylas() {
        val s = simdiki() ?: return
        val mime = s.m?.mime?.ifBlank { null } ?: contentResolver.getType(s.uri) ?: "image/*"
        val niyet = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, s.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(niyet, getString(R.string.paylas))) }
    }

    private fun videoOynat(s: Sayfa) {
        val m = s.m ?: return
        // Galeri video oynatıcı taşımıyor (MVP); telefondaki oynatıcıya verilir.
        val niyet = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime.ifBlank { "video/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(niyet) }.onFailure {
            Toast.makeText(this, R.string.oynatici_yok, Toast.LENGTH_LONG).show()
        }
    }

    private fun dondur() {
        ekAci = Kirpma.aciDuzelt(ekAci + 90)
        // Önizleme döner; kaydedilene kadar dosyaya bir şey olmaz.
        adapter.notifyItemChanged(sira)
        kaydet.visibility = if (ekAci != 0) View.VISIBLE else View.GONE
    }

    private fun kirp() {
        val s = simdiki() ?: return
        startActivity(
            Intent(this, KirpActivity::class.java)
                .setData(s.uri)
                .putExtra(KirpActivity.EK_YON, s.yon)
                .putExtra(KirpActivity.EK_ACI, ekAci)
                .putExtra(KirpActivity.EK_AD, s.m?.ad ?: s.uri.lastPathSegment.orEmpty())
                .putExtra(KirpActivity.EK_MIME, s.m?.mime ?: contentResolver.getType(s.uri).orEmpty())
                .putExtra(KirpActivity.EK_TARIH, s.m?.tarih ?: System.currentTimeMillis())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun kopyaKaydet() {
        val s = simdiki() ?: return
        if (!Izin.yazabilir(this)) {
            Izin.iste(this)
            return
        }
        val aci = Kirpma.aciDuzelt(s.yon + ekAci)
        kaydet.isEnabled = false
        yurutucu.execute {
            val sonuc = runCatching {
                Kaydedici.kaydet(
                    this, s.uri, s.m?.ad ?: s.uri.lastPathSegment.orEmpty(),
                    s.m?.mime ?: contentResolver.getType(s.uri).orEmpty(),
                    s.m?.tarih ?: System.currentTimeMillis(), Kutu(0f, 0f, 1f, 1f), aci
                )
            }.getOrNull()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                kaydet.isEnabled = true
                if (sonuc != null) {
                    MedyaDeposu.gecersizKil()
                    ekAci = 0
                    adapter.notifyItemChanged(sira)
                    kaydet.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.kopya_kaydedildi, "Pictures/" + Kaydedici.KLASOR), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.kaydedilemedi, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bilgi() {
        val s = simdiki() ?: return
        val m = s.m
        val satirlar = mutableListOf<String>()
        if (m != null) {
            satirlar += Metinler.tarihSaat(this, m.tarih)
            satirlar += m.ad
            satirlar += getString(R.string.bilgi_album, m.albumAdi)
            if (m.genislik > 0 && m.yukseklik > 0) {
                val (g, y) = Kirpma.donmusBoyut(m.genislik, m.yukseklik, m.yon)
                val mp = g.toLong() * y / 1_000_000.0
                satirlar += getString(R.string.bilgi_boyut, g, y, "%.1f".format(mp))
            }
            if (m.boyut > 0) satirlar += getString(R.string.bilgi_dosya, Metinler.boyut(this, m.boyut))
            if (m.video) satirlar += getString(R.string.bilgi_sure, Metinler.sure(m.sureMs))
        } else {
            satirlar += s.uri.toString()
        }
        AltSayfa(this).baslik(getString(R.string.bilgi)).mesaj(satirlar.joinToString("\n")).goster()
    }

    override fun onDestroy() {
        yurutucu.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EK_KAYNAK = "kaynak"
        const val EK_SIRA = "sira"
        const val EK_ID = "id"
    }
}
