package com.ekosistem.galeri

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ekosistem.tasarim.AltSayfa
import java.util.concurrent.Executors

/**
 * Tam ekran görüntüleyici. Liste [Kaynak]'tan kurulur (hepsi, albüm, arama sonucu);
 * başka uygulamadan "Galeri ile aç" ile gelince tek resim gösterilir.
 *
 * Döndür: önizleme hemen döner, "Kopya kaydet" ile yeni dosya yazılır; orijinal aynen kalır.
 */
class IzleyiciActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private lateinit var resim: ZoomGorunumu
    private lateinit var ustCubuk: View
    private lateinit var altCubuk: View
    private lateinit var kaydet: View
    private lateinit var yukleniyor: ProgressBar
    private lateinit var oynat: View

    private var liste: List<Medya> = emptyList()
    private var sira = 0
    /** Dışarıdan açılan tek resim (MediaStore'da olmayabilir). */
    private var disUri: Uri? = null
    private var disYon = 0
    private var ekAci = 0
    private var cubuklarAcik = true
    private var nesil = 0
    private var gosterilen: Bitmap? = null
    /** Gösterilen resmi biz mi çözdük (bırakabiliriz) yoksa küçük resim önbelleğinden mi geldi. */
    private var gosterilenBizim = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_izleyici)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        resim = findViewById(R.id.resim)
        ustCubuk = findViewById(R.id.ustCubuk)
        altCubuk = findViewById(R.id.altCubuk)
        kaydet = findViewById(R.id.btnKaydet)
        yukleniyor = findViewById(R.id.yukleniyor)
        oynat = findViewById(R.id.btnOynat)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kok)) { _, kenar ->
            val c = kenar.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ustCubuk.setPadding(c.left + dp(8), c.top, c.right + dp(16), 0)
            altCubuk.setPadding(c.left, 0, c.right, c.bottom)
            kenar
        }

        resim.dokunus = { cubuklariCevir() }
        resim.gecis = { yon -> git(sira + yon, yon) }
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPaylas).setOnClickListener { paylas() }
        findViewById<View>(R.id.btnDondur).setOnClickListener { dondur() }
        findViewById<View>(R.id.btnKirp).setOnClickListener { kirp() }
        findViewById<View>(R.id.btnBilgi).setOnClickListener { bilgi() }
        kaydet.setOnClickListener { kopyaKaydet() }
        oynat.setOnClickListener { videoOynat() }

        ekAci = savedInstanceState?.getInt("aci") ?: 0
        val gelen = intent.data
        if (intent.action == Intent.ACTION_VIEW && gelen != null) {
            disUri = gelen
            yurutucu.execute {
                disYon = Cozucu.exifYonu(this, gelen)
                runOnUiThread { goster() }
            }
            return
        }
        sira = savedInstanceState?.getInt("sira") ?: intent.getIntExtra(EK_SIRA, 0)
        val id = savedInstanceState?.getLong("id") ?: intent.getLongExtra(EK_ID, -1)
        val kaynak = Kaynak.coz(intent.getStringExtra(EK_KAYNAK))
        yurutucu.execute {
            val l = MedyaDeposu.kaynaktan(this, kaynak)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                liste = l
                // Liste değiştiyse (yeni fotoğraf geldi) sıra kayar; kimlikten bul.
                if (liste.getOrNull(sira)?.id != id) liste.indexOfFirst { it.id == id }.let { if (it >= 0) sira = it }
                if (liste.isEmpty()) finish() else goster()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("sira", sira)
        outState.putInt("aci", ekAci)
        simdiki()?.let { outState.putLong("id", it.id) }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun simdiki(): Medya? = liste.getOrNull(sira)

    private fun git(yeni: Int, yon: Int) {
        if (disUri != null || yeni !in liste.indices) return
        sira = yeni
        ekAci = 0
        // Kısa kayma: hangi yöne geçildiği hissedilsin.
        resim.translationX = yon * resim.width * 0.15f
        resim.alpha = 0.4f
        resim.animate().translationX(0f).alpha(1f).setDuration(160).start()
        goster()
    }

    private fun goster() {
        val m = simdiki()
        val uri = m?.uri ?: disUri ?: return
        val video = m?.video == true
        val yon = m?.yon ?: disYon
        kaydet.visibility = if (ekAci != 0) View.VISIBLE else View.GONE
        oynat.visibility = if (video) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnDondur).visibility = if (video) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnKirp).visibility = if (video) View.GONE else View.VISIBLE
        val baslik = findViewById<TextView>(R.id.izleyiciBaslik)
        val alt = findViewById<TextView>(R.id.izleyiciAlt)
        if (m != null) {
            baslik.text = Metinler.tamTarih(m.tarih)
            alt.text = getString(R.string.sira_n, sira + 1, liste.size) + " · " + m.albumAdi
        } else {
            baslik.text = uri.lastPathSegment.orEmpty()
            alt.text = ""
        }
        val n = ++nesil
        yukleniyor.visibility = View.VISIBLE
        val hedef = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val aci = ekAci
        yurutucu.execute {
            val bmp = runCatching {
                if (video && m != null) Cozucu.kucukResim(this, m, hedef / 2)
                else Cozucu.tamResim(this, uri, yon, hedef, aci)
            }.getOrNull()
            runOnUiThread {
                if (n != nesil || isDestroyed) { if (!video) bmp?.recycle(); return@runOnUiThread }
                yukleniyor.visibility = View.GONE
                val eski = gosterilen
                val eskiBizim = gosterilenBizim
                gosterilen = bmp
                gosterilenBizim = !video
                resim.setImageBitmap(bmp)
                resim.contentDescription = getString(if (video) R.string.video else R.string.fotograf) + ", " + baslik.text
                // Videonun küçük resmi ızgaranın önbelleğinde de duruyor; yalnız kendi çözdüğümüzü bırak.
                if (eski != null && eski !== bmp && eskiBizim) eski.recycle()
                if (bmp == null) Toast.makeText(this, R.string.acilamadi, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            v.animate().alpha(if (cubuklarAcik) 1f else 0f).setDuration(150)
                .withEndAction { v.visibility = if (cubuklarAcik) View.VISIBLE else View.INVISIBLE }.start()
            if (cubuklarAcik) v.visibility = View.VISIBLE
        }
    }

    private fun paylas() {
        val uri = simdiki()?.uri ?: disUri ?: return
        val mime = simdiki()?.mime?.ifBlank { null } ?: "image/*"
        val niyet = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(niyet, getString(R.string.paylas))) }
    }

    private fun videoOynat() {
        val m = simdiki() ?: return
        // Galeri video oynatıcı taşımıyor (MVP); telefondaki oynatıcıya verilir.
        val niyet = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime.ifBlank { "video/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(niyet) }.onFailure {
            Toast.makeText(this, R.string.oynatici_yok, Toast.LENGTH_LONG).show()
        }
    }

    private fun dondur() {
        ekAci = Kirpma.aciDuzelt(ekAci + 90)
        // Önizleme anında döner; kaydedilene kadar dosyaya bir şey olmaz.
        gosterilen?.let {
            val yeni = Cozucu.dondur(it, 90)
            gosterilen = yeni
            resim.setImageBitmap(yeni)
        } ?: goster()
        kaydet.visibility = if (ekAci != 0) View.VISIBLE else View.GONE
    }

    private fun kirp() {
        val m = simdiki()
        val uri = m?.uri ?: disUri ?: return
        startActivity(
            Intent(this, KirpActivity::class.java)
                .setData(uri)
                .putExtra(KirpActivity.EK_YON, m?.yon ?: disYon)
                .putExtra(KirpActivity.EK_ACI, ekAci)
                .putExtra(KirpActivity.EK_AD, m?.ad ?: uri.lastPathSegment.orEmpty())
                .putExtra(KirpActivity.EK_MIME, m?.mime ?: contentResolver.getType(uri).orEmpty())
                .putExtra(KirpActivity.EK_TARIH, m?.tarih ?: System.currentTimeMillis())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun kopyaKaydet() {
        val uri = simdiki()?.uri ?: disUri ?: return
        if (!Izin.yazabilir(this)) {
            Izin.iste(this)
            return
        }
        val m = simdiki()
        val aci = Kirpma.aciDuzelt((m?.yon ?: disYon) + ekAci)
        kaydet.isEnabled = false
        yurutucu.execute {
            val sonuc = runCatching {
                Kaydedici.kaydet(
                    this, uri, m?.ad ?: uri.lastPathSegment.orEmpty(), m?.mime ?: contentResolver.getType(uri).orEmpty(),
                    m?.tarih ?: System.currentTimeMillis(), Kutu(0f, 0f, 1f, 1f), aci
                )
            }.getOrNull()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                kaydet.isEnabled = true
                if (sonuc != null) {
                    MedyaDeposu.gecersizKil()
                    ekAci = 0
                    kaydet.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.kopya_kaydedildi, "Pictures/" + Kaydedici.KLASOR), Toast.LENGTH_LONG).show()
                    goster()
                } else {
                    Toast.makeText(this, R.string.kaydedilemedi, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bilgi() {
        val m = simdiki()
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
            satirlar += disUri.toString()
        }
        AltSayfa(this).baslik(getString(R.string.bilgi)).mesaj(satirlar.joinToString("\n")).goster()
    }

    override fun onDestroy() {
        yurutucu.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EK_KAYNAK = "kaynak"
        const val EK_SIRA = "sira"
        const val EK_ID = "id"
    }
}
