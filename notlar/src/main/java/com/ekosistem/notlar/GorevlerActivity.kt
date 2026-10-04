package com.ekosistem.notlar

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Tüm notlardaki onay kutularını tek listede toplar. */
class GorevlerActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: GorevAdapter
    private lateinit var bosDurum: View
    private var tamamlananlar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gorevler)
        depo = NotDeposu(this)
        bosDurum = findViewById(R.id.bosDurum)

        adapter = GorevAdapter(
            vurgu = Renkler.vurgu(this),
            soluk = ContextCompat.getColor(this, R.color.metin_ikincil),
            gecikmis = ContextCompat.getColor(this, R.color.fark_silindi),
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
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { menuGoster() }
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
                if (gorevler.isEmpty()) {
                    BosDurum.goster(
                        bosDurum,
                        R.drawable.ic_bicim_onay,
                        getString(R.string.gorev_yok),
                        getString(R.string.gorev_yok_aciklama)
                    )
                } else {
                    bosDurum.visibility = View.GONE
                }
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

    private fun menuGoster() {
        AltSayfa(this)
            .madde(
                R.drawable.ic_kutu_dolu,
                getString(R.string.tamamlananlari_goster),
                secili = tamamlananlar
            ) {
                tamamlananlar = !tamamlananlar
                yenile()
            }
            .goster()
    }
}

class GorevAdapter(
    private val vurgu: Int,
    private val soluk: Int,
    /** Tarihi geçmiş açık görevin etiket rengi. */
    private val gecikmis: Int,
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
        t.metin.typeface = YaziTipleri.yazi(t.itemView.context)
        t.metin.text = gorev.metin
        t.not.text = altSatir(t.itemView.context, gorev)

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

    /** "3 gün gecikti · Not başlığı": tarih etiketi renkli, not adı soluk. */
    private fun altSatir(context: Context, gorev: Gorev): CharSequence {
        val gun = gorev.sonGun ?: return gorev.notBasligi
        val fark = (gun - SonTarih.bugun()).toInt()
        val etiket = tarihEtiketi(context, gun, fark)
        val renk = when {
            gorev.isaretli -> soluk
            fark < 0 -> gecikmis
            fark == 0 -> vurgu
            else -> soluk
        }
        val s = SpannableStringBuilder(etiket)
        s.setSpan(ForegroundColorSpan(renk), 0, etiket.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (!gorev.isaretli && fark <= 0) {
            s.setSpan(StyleSpan(Typeface.BOLD), 0, etiket.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s.append(" · ").append(gorev.notBasligi)
    }

    private fun tarihEtiketi(context: Context, gun: Long, fark: Int): String {
        val r = context.resources
        return when {
            fark == 0 -> r.getString(R.string.bugun)
            fark == 1 -> r.getString(R.string.yarin)
            fark < 0 -> r.getQuantityString(R.plurals.gun_gecikti, -fark, -fark)
            fark < 7 -> r.getQuantityString(R.plurals.gun_kaldi, fark, fark)
            else -> {
                val (yil, ay, g) = SonTarih.tarih(gun)
                val takvim = Calendar.getInstance().apply { clear(); set(yil, ay - 1, g) }
                // Bu yılın tarihinde yıl yazılmaz.
                val buYil = yil == Calendar.getInstance().get(Calendar.YEAR)
                val bicim = DateFormat.getBestDateTimePattern(
                    Locale.getDefault(),
                    if (buYil) "MMMd" else "yMMMd"
                )
                SimpleDateFormat(bicim, Locale.getDefault()).format(takvim.time)
            }
        }
    }
}
