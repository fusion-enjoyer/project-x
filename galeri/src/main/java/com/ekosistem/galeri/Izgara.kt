package com.ekosistem.galeri

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Genişliği kadar yüksek kare (ızgara hücresi). */
class KareCerceve @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : FrameLayout(c, a) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

/** Ekran genişliğine göre sütun sayısı: telefonda 4, yatayda/tablette daha çok. */
fun sutunSayisi(c: Context, hucreDp: Int): Int {
    val dp = c.resources.configuration.screenWidthDp
    return maxOf(3, dp / hucreDp)
}

/**
 * Fotoğraf ızgarası; aylara göre başlıklı. Dokununca görüntüleyici [kaynak]
 * listesinin o ögesiyle açılır.
 */
class IzgaraAdapter(
    private val activity: Activity,
    private val kaynak: () -> Kaynak
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Satir {
        data class Baslik(val metin: String) : Satir()
        data class Oge(val m: Medya, val sira: Int) : Satir()
    }

    private var satirlar: List<Satir> = emptyList()
    private var pikselBoyu = 256

    fun ayarla(medyalar: List<Medya>, basliklar: Boolean = true) {
        val yeni = ArrayList<Satir>(medyalar.size + 24)
        var sonAy = Int.MIN_VALUE
        medyalar.forEachIndexed { i, m ->
            if (basliklar) {
                val ay = Metinler.ayAnahtari(m.tarih)
                if (ay != sonAy) { yeni += Satir.Baslik(Metinler.ayYil(m.tarih)); sonAy = ay }
            }
            yeni += Satir.Oge(m, i)
        }
        satirlar = yeni
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun yerlestir(liste: RecyclerView, sutun: Int) {
        pikselBoyu = (activity.resources.displayMetrics.widthPixels / sutun).coerceIn(96, 512)
        val yonetici = GridLayoutManager(activity, sutun)
        yonetici.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (satirlar.getOrNull(position) is Satir.Baslik) sutun else 1
        }
        liste.layoutManager = yonetici
        liste.adapter = this
    }

    override fun getItemCount() = satirlar.size

    override fun getItemViewType(position: Int) = if (satirlar[position] is Satir.Baslik) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_baslik, parent, false)) {}
        else object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_kare, parent, false)) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val s = satirlar[position]) {
            is Satir.Baslik -> (h.itemView as TextView).text = s.metin
            is Satir.Oge -> kareBagla(h.itemView, s.m) { izleyiciAc(activity, kaynak(), s.sira, s.m.id) }
        }
    }

    private fun kareBagla(v: View, m: Medya, tikla: () -> Unit) {
        val resim = v.findViewById<ImageView>(R.id.kareResim)
        KucukResimler.yukle(activity, m, resim, pikselBoyu)
        val sure = v.findViewById<TextView>(R.id.kareSure)
        if (m.video) {
            sure.visibility = View.VISIBLE
            sure.text = Metinler.sure(m.sureMs)
        } else {
            sure.visibility = if (m.mime == "image/gif") View.VISIBLE else View.GONE
            sure.text = "GIF"
        }
        v.contentDescription = (if (m.video) activity.getString(R.string.video) else activity.getString(R.string.fotograf)) +
            ", " + Metinler.tamTarih(m.tarih)
        v.setOnClickListener { tikla() }
    }
}

/** Albüm listesi: kapak, ad, adet. */
class AlbumAdapter(private val activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var albumler: List<Album> = emptyList()
    private var pikselBoyu = 384

    fun ayarla(liste: List<Album>) {
        albumler = liste
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun yerlestir(liste: RecyclerView, sutun: Int) {
        pikselBoyu = (activity.resources.displayMetrics.widthPixels / sutun).coerceIn(128, 640)
        liste.layoutManager = GridLayoutManager(activity, sutun)
        liste.adapter = this
    }

    override fun getItemCount() = albumler.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        // Kapak köşeleri yuvarlak: çerçevenin zemini kadar kırp.
        (v.findViewById<View>(R.id.albumKapak).parent as View).clipToOutline = true
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val a = albumler[position]
        val v = h.itemView
        KucukResimler.yukle(activity, a.kapak, v.findViewById(R.id.albumKapak), pikselBoyu)
        v.findViewById<TextView>(R.id.albumAd).text = a.ad
        v.findViewById<TextView>(R.id.albumAdet).text = activity.resources.getQuantityString(R.plurals.oge_sayisi, a.adet, a.adet)
        v.setOnClickListener {
            activity.startActivity(
                Intent(activity, AlbumActivity::class.java)
                    .putExtra(AlbumActivity.EK_ID, a.id)
                    .putExtra(AlbumActivity.EK_AD, a.ad)
            )
        }
    }
}

fun izleyiciAc(a: Activity, k: Kaynak, sira: Int, id: Long) {
    a.startActivity(
        Intent(a, IzleyiciActivity::class.java)
            .putExtra(IzleyiciActivity.EK_KAYNAK, k.kodla())
            .putExtra(IzleyiciActivity.EK_SIRA, sira)
            .putExtra(IzleyiciActivity.EK_ID, id)
    )
}
