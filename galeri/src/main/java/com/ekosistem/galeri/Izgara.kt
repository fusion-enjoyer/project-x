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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
    return maxOf(4, dp / hucreDp)
}

/** Izgaranın satırı: ay başlığı ya da fotoğraf. */
sealed class Satir {
    abstract val anahtar: Long
    data class Baslik(val metin: String, val ay: Int) : Satir() {
        override val anahtar get() = -ay.toLong() - 1
    }
    data class Oge(val m: Medya, val sira: Int) : Satir() {
        override val anahtar get() = if (m.video) -(m.id shl 1) - 1_000_000_000L else m.id shl 1
    }

    object Fark : DiffUtil.ItemCallback<Satir>() {
        override fun areItemsTheSame(a: Satir, b: Satir) = a.anahtar == b.anahtar
        override fun areContentsTheSame(a: Satir, b: Satir) = a == b
    }
}

/**
 * Fotoğraf ızgarası; aylara göre başlıklı. Değişiklikler DiffUtil ile arka planda
 * hesaplanır, yeni fotoğraf gelince liste titremez ve kaydırma yeri korunur.
 */
class IzgaraAdapter(
    private val activity: Activity,
    private val kaynak: () -> Kaynak
) : ListAdapter<Satir, RecyclerView.ViewHolder>(Satir.Fark) {

    private var pikselBoyu = 256

    init {
        setHasStableIds(true)
    }

    fun ayarla(medyalar: List<Medya>, basliklar: Boolean = true, bitti: (() -> Unit)? = null) {
        val yeni = ArrayList<Satir>(medyalar.size + 24)
        var sonAy = Int.MIN_VALUE
        medyalar.forEachIndexed { i, m ->
            if (basliklar) {
                val ay = Metinler.ayAnahtari(m.tarih)
                if (ay != sonAy) { yeni += Satir.Baslik(Metinler.ayYil(m.tarih), ay); sonAy = ay }
            }
            yeni += Satir.Oge(m, i)
        }
        submitList(yeni) { bitti?.invoke() }
    }

    fun yerlestir(liste: RecyclerView, sutun: Int) {
        pikselBoyu = (activity.resources.displayMetrics.widthPixels / sutun).coerceIn(96, 512)
        val yonetici = GridLayoutManager(activity, sutun)
        yonetici.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (currentList.getOrNull(position) is Satir.Baslik) sutun else 1
        }
        liste.layoutManager = yonetici
        liste.adapter = this
    }

    override fun getItemId(position: Int) = getItem(position).anahtar

    override fun getItemViewType(position: Int) = if (getItem(position) is Satir.Baslik) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_baslik, parent, false)) {}
        else object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_kare, parent, false)) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val s = getItem(position)) {
            is Satir.Baslik -> (h.itemView as TextView).text = s.metin
            is Satir.Oge -> kareBagla(h.itemView, s.m) { izleyiciAc(activity, kaynak(), s.sira, s.m.id) }
        }
    }

    private fun kareBagla(v: View, m: Medya, tikla: () -> Unit) {
        val resim = v.findViewById<ImageView>(R.id.kareResim)
        KucukResimler.yukle(activity, m, resim, pikselBoyu)
        val sure = v.findViewById<TextView>(R.id.kareSure)
        when {
            m.video -> { sure.visibility = View.VISIBLE; sure.text = Metinler.sure(m.sureMs) }
            m.mime == "image/gif" -> { sure.visibility = View.VISIBLE; sure.text = "GIF" }
            else -> sure.visibility = View.GONE
        }
        v.contentDescription = activity.getString(if (m.video) R.string.video else R.string.fotograf) +
            ", " + Metinler.tamTarih(m.tarih)
        v.setOnClickListener { tikla() }
    }
}

/** Albüm listesi: kapak, ad, adet. */
class AlbumAdapter(private val activity: Activity) : ListAdapter<Album, RecyclerView.ViewHolder>(Fark) {

    private object Fark : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(a: Album, b: Album) = a.id == b.id
        override fun areContentsTheSame(a: Album, b: Album) = a == b
    }

    private var pikselBoyu = 384

    fun ayarla(liste: List<Album>) = submitList(liste)

    fun yerlestir(liste: RecyclerView, sutun: Int) {
        pikselBoyu = (activity.resources.displayMetrics.widthPixels / sutun).coerceIn(128, 640)
        liste.layoutManager = GridLayoutManager(activity, sutun)
        liste.adapter = this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        // Kapak köşeleri yuvarlak: çerçevenin zemini kadar kırp.
        (v.findViewById<View>(R.id.albumKapak).parent as View).clipToOutline = true
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val a = getItem(position)
        val v = h.itemView
        KucukResimler.yukle(activity, a.kapak, v.findViewById(R.id.albumKapak), pikselBoyu)
        v.findViewById<TextView>(R.id.albumAd).text = a.ad
        val adet = activity.resources.getQuantityString(R.plurals.oge_sayisi, a.adet, a.adet)
        v.findViewById<TextView>(R.id.albumAdet).text = adet
        v.contentDescription = a.ad + ", " + adet
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
