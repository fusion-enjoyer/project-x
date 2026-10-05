package com.ekosistem.notlar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Çöp kutusu. Sola kaydırma kalıcı siler, sağa kaydırma geri yükler (ana
 * ekrandaki iki yönlü kaydırmanın eşi). Kalıcı silme geri alınamadığı için
 * hemen yapılmaz: not listeden çekilir, şeritte birkaç saniye "Geri al"
 * görünür; şerit kapanınca ya da ekrandan çıkılınca dosya silinir.
 */
class TrashActivity : TemelActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: View
    private lateinit var btnHepsiniSil: ImageButton
    private lateinit var serit: View
    private lateinit var seritMetin: TextView
    private lateinit var seritEylem: TextView
    private var seritKapatici: Runnable? = null

    /** Ekranda görünen notlar (silinmeyi bekleyenler hariç). */
    private var notlar: List<Not> = emptyList()

    /** Kaydırılıp silinen, şerit kapanınca kalıcı silinecek notlar. */
    private val bekleyenler = mutableListOf<Not>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)
        depo = NotDeposu(this)

        bosDurum = findViewById(R.id.bosDurum)
        btnHepsiniSil = findViewById(R.id.btnHepsiniSil)
        serit = findViewById(R.id.bildirimSeridi)
        seritMetin = findViewById(R.id.bildirimMetin)
        seritEylem = findViewById(R.id.bildirimEylem)
        seritEylem.setTextColor(Renkler.vurgu(this))

        adapter = NotAdapter(
            onTikla = { not -> secenekler(not) },
            onUzunBas = { not -> secenekler(not) }
        )
        adapter.vurgu = Renkler.vurgu(this)
        adapter.kartRengi = ContextCompat.getColor(this, R.color.kart)
        adapter.secimRengi = adapter.kartRengi

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter
        kaydirmaKur(liste)

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        btnHepsiniSil.setOnClickListener { hepsiniSilOnayi() }
        ipucuVer(findViewById(R.id.btnGeri), btnHepsiniSil)
    }

    override fun onResume() {
        super.onResume()
        yenile()
    }

    override fun onStop() {
        super.onStop()
        // Ekrandan çıkılırken bekleyen silmeler yapılır; geri alma şansı bitti.
        seritGizle()
        bekleyenleriSil()
    }

    private fun yenile() {
        Thread {
            val hepsi = try {
                depo.copListele()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                val bekleyen = bekleyenler.mapTo(HashSet()) { it.uri }
                goster(hepsi.filter { it.uri !in bekleyen })
            }
        }.start()
    }

    private fun goster(liste: List<Not>) {
        notlar = liste
        adapter.guncelle(liste)
        btnHepsiniSil.visibility = if (liste.isEmpty()) View.GONE else View.VISIBLE
        if (liste.isEmpty()) {
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

    private fun secenekler(not: Not) {
        AltSayfa(this)
            .baslik(not.baslik)
            .madde(R.drawable.ic_geri_al, getString(R.string.geri_yukle)) { geriYukle(not) }
            .madde(R.drawable.ic_sil, getString(R.string.kalici_sil), tehlikeli = true) {
                NotDeposu.yazici.execute {
                    depo.kaliciSil(not.uri)
                    runOnUiThread { yenile() }
                }
            }
            .goster()
    }

    private fun geriYukle(not: Not) {
        goster(notlar.filter { it.uri != not.uri })
        NotDeposu.yazici.execute {
            depo.geriYukle(not.uri)
            runOnUiThread { yenile() }
        }
    }

    // --- Kaydırarak silme / geri yükleme ---

    private fun kaydirmaKur(liste: RecyclerView) {
        val y = resources.displayMetrics.density
        val kose = 20f * y
        val silBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE24B4A.toInt() }
        val geriBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Renkler.vurgu(this@TrashActivity) }
        val silIkon = ContextCompat.getDrawable(this, R.drawable.ic_cop)
        val geriIkon = ContextCompat.getDrawable(this, R.drawable.ic_geri_al)?.mutate()
        if (geriIkon != null) DrawableCompat.setTint(geriIkon, Renkler.vurguUzeri(this))

        val geri = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                hedef: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, yon: Int) {
                val not = adapter.notAl(vh.bindingAdapterPosition) ?: return
                liste.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                if (yon == ItemTouchHelper.LEFT) kaydirarakSil(not) else geriYukle(not)
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                durum: Int,
                aktif: Boolean
            ) {
                val v = vh.itemView
                if (dX < 0) {
                    val r = RectF(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
                    c.drawRoundRect(r, kose, kose, silBoya)
                    silIkon?.let { ikonCiz(c, it, v, solda = false) }
                } else if (dX > 0) {
                    val r = RectF(v.left.toFloat(), v.top.toFloat(), v.left + dX, v.bottom.toFloat())
                    c.drawRoundRect(r, kose, kose, geriBoya)
                    geriIkon?.let { ikonCiz(c, it, v, solda = true) }
                }
                super.onChildDraw(c, rv, vh, dX, dY, durum, aktif)
            }
        }
        ItemTouchHelper(geri).attachToRecyclerView(liste)
    }

    private fun ikonCiz(c: Canvas, ikon: Drawable, v: View, solda: Boolean) {
        val y = resources.displayMetrics.density
        val boyut = (24 * y).toInt()
        val kenar = (20 * y).toInt()
        val ust = v.top + (v.height - boyut) / 2
        val solX = if (solda) v.left + kenar else v.right - kenar - boyut
        ikon.setBounds(solX, ust, solX + boyut, ust + boyut)
        ikon.draw(c)
    }

    private fun kaydirarakSil(not: Not) {
        bekleyenler.add(not)
        goster(notlar.filter { it.uri != not.uri })
        // Art arda kaydırılanlar tek şeritte toplanır; "Geri al" hepsini geri getirir.
        seritGoster(resources.getQuantityString(R.plurals.kalici_silindi, bekleyenler.size, bekleyenler.size)) {
            bekleyenler.clear()
            yenile()
        }
    }

    private fun bekleyenleriSil() {
        if (bekleyenler.isEmpty()) return
        val silinecek = bekleyenler.toList()
        bekleyenler.clear()
        NotDeposu.yazici.execute {
            for (not in silinecek) depo.kaliciSil(not.uri)
        }
    }

    // --- Hepsini sil ---

    private fun hepsiniSilOnayi() {
        val sayi = notlar.size + bekleyenler.size
        if (sayi == 0) return
        AltSayfa(this)
            .baslik(getString(R.string.cop_bosalt))
            .mesaj(resources.getQuantityString(R.plurals.cop_bosalt_ozet, sayi, sayi))
            .madde(R.drawable.ic_cop_bosalt, getString(R.string.hepsini_kalici_sil), tehlikeli = true) {
                val silinecek = notlar + bekleyenler
                bekleyenler.clear()
                seritGizle()
                goster(emptyList())
                NotDeposu.yazici.execute {
                    for (not in silinecek) depo.kaliciSil(not.uri)
                    runOnUiThread { yenile() }
                }
            }
            .goster()
    }

    // --- Bildirim şeridi ---

    private fun seritGoster(mesaj: String, geriAl: () -> Unit) {
        seritKapatici?.let { serit.removeCallbacks(it) }
        seritMetin.text = mesaj
        serit.visibility = View.VISIBLE
        seritEylem.setOnClickListener {
            seritGizle()
            geriAl()
        }
        val kapatici = Runnable {
            seritGizle()
            bekleyenleriSil()
        }
        seritKapatici = kapatici
        serit.postDelayed(kapatici, SERIT_SURESI)
    }

    private fun seritGizle() {
        seritKapatici?.let { serit.removeCallbacks(it) }
        seritKapatici = null
        serit.visibility = View.GONE
    }

    companion object {
        private const val SERIT_SURESI = 5000L
    }
}
