package com.ekosistem.notlar

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: TextView
    private lateinit var klasorSatiri: LinearLayout
    private var sorgu: String? = null
    private var seciliKlasor: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        depo = NotDeposu(this)

        bosDurum = findViewById(R.id.bosDurum)
        klasorSatiri = findViewById(R.id.klasorSatiri)
        adapter = NotAdapter(
            onTikla = { not -> editorAc(not.uri) },
            onUzunBas = { not -> notSecenekleri(not) }
        )

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter
        swipeKur(liste)

        findViewById<TextView>(R.id.btnYeni).setOnClickListener {
            val i = Intent(this, EditorActivity::class.java)
            seciliKlasor?.let { k -> i.putExtra("klasor", k) }
            startActivity(i)
        }

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { v -> menuGoster(v) }

        findViewById<EditText>(R.id.arama).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sorgu = s?.toString()
                yenile()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        yenile()
    }

    private fun yenile() {
        val aktifSorgu = sorgu
        val aktifKlasor = seciliKlasor
        Thread {
            val notlar = try {
                depo.notlariListele(aktifSorgu, aktifKlasor)
            } catch (_: Exception) {
                emptyList()
            }
            val klasorler = try {
                depo.klasorAdlari()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (aktifSorgu != sorgu || aktifKlasor != seciliKlasor) return@runOnUiThread
                adapter.guncelle(notlar)
                klasorCubuguGuncelle(klasorler)
                if (notlar.isEmpty()) {
                    bosDurum.setText(
                        if (sorgu.isNullOrBlank()) R.string.bos_durum else R.string.bos_arama
                    )
                    bosDurum.visibility = View.VISIBLE
                } else {
                    bosDurum.visibility = View.GONE
                }
            }
        }.start()
    }

    // --- Klasör çubuğu ---

    private fun klasorCubuguGuncelle(adlar: List<String>) {
        klasorSatiri.removeAllViews()
        chipEkle(getString(R.string.tumu), seciliKlasor == null) {
            seciliKlasor = null
            yenile()
        }
        for (ad in adlar) {
            chipEkle(ad, seciliKlasor == ad) {
                seciliKlasor = if (seciliKlasor == ad) null else ad
                yenile()
            }
        }
        chipEkle("+", false) { yeniKlasorDialog(null) }
    }

    private fun chipEkle(etiket: String, secili: Boolean, tikla: () -> Unit) {
        val tv = TextView(this)
        tv.text = etiket
        tv.textSize = 13f
        tv.setTypeface(null, if (secili) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(
            ContextCompat.getColor(this, if (secili) R.color.vurgu else R.color.metin_ikincil)
        )
        tv.setBackgroundResource(if (secili) R.drawable.bg_chip_secili else R.drawable.bg_chip)
        val y = resources.displayMetrics.density
        tv.setPadding((14 * y).toInt(), (7 * y).toInt(), (14 * y).toInt(), (7 * y).toInt())
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = (8 * y).toInt()
        tv.layoutParams = lp
        tv.setOnClickListener { tikla() }
        klasorSatiri.addView(tv)
    }

    private fun yeniKlasorDialog(tasinacak: Not?) {
        val giris = EditText(this)
        giris.hint = getString(R.string.klasor_adi)
        val kutu = FrameLayout(this)
        val y = resources.displayMetrics.density
        kutu.setPadding((20 * y).toInt(), (8 * y).toInt(), (20 * y).toInt(), 0)
        kutu.addView(
            giris,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.yeni_klasor)
            .setView(kutu)
            .setPositiveButton(R.string.olustur) { _, _ ->
                val ad = giris.text.toString().trim()
                if (ad.isEmpty()) {
                    yenile()
                    return@setPositiveButton
                }
                Thread {
                    depo.klasorOlustur(ad)
                    if (tasinacak != null) depo.klasoreTasi(tasinacak.uri, ad)
                    runOnUiThread { yenile() }
                }.start()
            }
            .setNegativeButton(R.string.iptal) { _, _ -> yenile() }
            .setOnCancelListener { yenile() }
            .show()
    }

    // --- Kaydırma hareketleri ---

    private fun swipeKur(liste: RecyclerView) {
        val y = resources.displayMetrics.density
        val kose = 20f * y
        val silBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE24B4A.toInt() }
        val tasiBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.vurgu)
        }
        val silIkon = ContextCompat.getDrawable(this, R.drawable.ic_cop)
        val tasiIkon = ContextCompat.getDrawable(this, R.drawable.ic_klasor)

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
                if (yon == ItemTouchHelper.LEFT) swipeSil(not) else tasiDialog(not)
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
                    c.drawRoundRect(r, kose, kose, tasiBoya)
                    tasiIkon?.let { ikonCiz(c, it, v, solda = true) }
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

    private fun swipeSil(not: Not) {
        Thread {
            val oldu = depo.copeTasi(not.uri)
            runOnUiThread {
                if (oldu) Toast.makeText(this, R.string.cope_tasindi, Toast.LENGTH_SHORT).show()
                yenile()
            }
        }.start()
    }

    private fun tasiDialog(not: Not) {
        Thread {
            val klasorler = depo.klasorAdlari()
            runOnUiThread {
                val etiketler = mutableListOf(getString(R.string.ana_klasor))
                etiketler.addAll(klasorler)
                etiketler.add(getString(R.string.yeni_klasor) + "…")
                AlertDialog.Builder(this)
                    .setTitle(R.string.klasore_tasi)
                    .setItems(etiketler.toTypedArray()) { _, hangi ->
                        when (hangi) {
                            0 -> tasi(not, null)
                            etiketler.size - 1 -> yeniKlasorDialog(not)
                            else -> tasi(not, klasorler[hangi - 1])
                        }
                    }
                    .setOnCancelListener { yenile() }
                    .show()
            }
        }.start()
    }

    private fun tasi(not: Not, klasor: String?) {
        Thread {
            val oldu = depo.klasoreTasi(not.uri, klasor)
            runOnUiThread {
                if (oldu) Toast.makeText(this, R.string.tasindi, Toast.LENGTH_SHORT).show()
                yenile()
            }
        }.start()
    }

    // --- Diğer ---

    private fun editorAc(uri: Uri) {
        startActivity(
            Intent(this, EditorActivity::class.java).putExtra("uri", uri.toString())
        )
    }

    private fun notSecenekleri(not: Not) {
        val sabitEtiket = getString(if (not.sabit) R.string.sabit_kaldir else R.string.sabitle)
        val secenekler = arrayOf(
            sabitEtiket,
            getString(R.string.klasore_tasi),
            getString(R.string.sil)
        )
        AlertDialog.Builder(this)
            .setTitle(not.baslik)
            .setItems(secenekler) { _, hangi ->
                when (hangi) {
                    0 -> {
                        Prefs.sabitDegistir(this, not.uri.toString())
                        yenile()
                    }
                    1 -> tasiDialog(not)
                    2 -> swipeSil(not)
                }
            }
            .show()
    }

    private fun menuGoster(v: View) {
        val menu = PopupMenu(this, v)
        menu.menu.add(0, 1, 0, R.string.klasor_sec)
        menu.menu.add(0, 2, 1, R.string.cop_kutusu)
        menu.menu.add(0, 3, 2, R.string.tema)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> klasorSec()
                2 -> startActivity(Intent(this, TrashActivity::class.java))
                3 -> temaSec()
            }
            true
        }
        menu.show()
    }

    private fun klasorSec() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_KLASOR)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (istek == ISTEK_KLASOR && sonuc == RESULT_OK) {
            val uri = veri?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Prefs.klasorUriKaydet(this, uri.toString())
                seciliKlasor = null
                yenile()
            } catch (_: Exception) {
            }
        }
    }

    private fun temaSec() {
        val etiketler = arrayOf(
            getString(R.string.tema_sistem),
            getString(R.string.tema_acik),
            getString(R.string.tema_siyah)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.tema)
            .setSingleChoiceItems(etiketler, Prefs.tema(this)) { dialog, hangi ->
                Prefs.temaKaydet(this, hangi)
                Tema.uygula(hangi)
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        private const val ISTEK_KLASOR = 42
    }
}
