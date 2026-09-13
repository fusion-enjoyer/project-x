package com.ekosistem.notlar

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Tüm notlardaki onay kutularını tek listede toplar. */
class GorevlerActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: GorevAdapter
    private lateinit var bosDurum: TextView
    private var tamamlananlar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gorevler)
        depo = NotDeposu(this)
        bosDurum = findViewById(R.id.bosDurum)

        adapter = GorevAdapter(
            vurgu = Renkler.vurgu(this),
            soluk = ContextCompat.getColor(this, R.color.metin_ikincil),
            onKutu = { gorev -> gorevDegistir(gorev) },
            onSatir = { gorev ->
                startActivity(
                    Intent(this, EditorActivity::class.java)
                        .putExtra("uri", gorev.notUri.toString())
                )
            }
        )

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { v -> menuGoster(v) }
    }

    override fun onResume() {
        super.onResume()
        yenile()
    }

    private fun yenile() {
        Thread {
            val gorevler = try {
                depo.gorevleriListele(tamamlananlar)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                adapter.guncelle(gorevler)
                bosDurum.visibility = if (gorevler.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun gorevDegistir(gorev: Gorev) {
        Thread {
            depo.gorevDegistir(gorev)
            NotWidget.hepsiniGuncelle(applicationContext)
            runOnUiThread { yenile() }
        }.start()
    }

    private fun menuGoster(v: View) {
        val menu = PopupMenu(this, v)
        val madde = menu.menu.add(0, 1, 0, R.string.tamamlananlari_goster)
        madde.isCheckable = true
        madde.isChecked = tamamlananlar
        menu.setOnMenuItemClickListener {
            tamamlananlar = !tamamlananlar
            yenile()
            true
        }
        menu.show()
    }
}

class GorevAdapter(
    private val vurgu: Int,
    private val soluk: Int,
    private val onKutu: (Gorev) -> Unit,
    private val onSatir: (Gorev) -> Unit
) : RecyclerView.Adapter<GorevAdapter.Tutucu>() {

    private var gorevler: List<Gorev> = emptyList()

    fun guncelle(yeni: List<Gorev>) {
        gorevler = yeni
        notifyDataSetChanged()
    }

    class Tutucu(v: View) : RecyclerView.ViewHolder(v) {
        val kutu: ImageView = v.findViewById(R.id.gorevKutu)
        val metin: TextView = v.findViewById(R.id.gorevMetin)
        val not: TextView = v.findViewById(R.id.gorevNot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Tutucu {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_gorev, parent, false)
        return Tutucu(v)
    }

    override fun getItemCount(): Int = gorevler.size

    override fun onBindViewHolder(t: Tutucu, pozisyon: Int) {
        val gorev = gorevler[pozisyon]
        t.metin.text = gorev.metin
        t.not.text = gorev.notBasligi

        if (gorev.isaretli) {
            t.kutu.setImageResource(R.drawable.ic_kutu_dolu)
            t.kutu.imageTintList = ColorStateList.valueOf(vurgu)
            t.metin.paintFlags = t.metin.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            t.metin.setTextColor(soluk)
        } else {
            t.kutu.setImageResource(R.drawable.ic_kutu_bos)
            t.kutu.imageTintList = ColorStateList.valueOf(soluk)
            t.metin.paintFlags = t.metin.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            t.metin.setTextColor(
                ContextCompat.getColor(t.itemView.context, R.color.metin)
            )
        }

        t.kutu.setOnClickListener { onKutu(gorev) }
        t.itemView.setOnClickListener { onSatir(gorev) }
    }
}
