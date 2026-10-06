package com.ekosistem.notlar

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Açık sekmeler, mobil tarayıcıdaki gibi (Obsidian'ın sekme ekranı): iki
 * sütunlu kart ızgarası, her kartta notun başlığı ve küçültülmüş önizlemesi.
 * Dokununca o sekmeye geçilir; "×" ya da sağa/sola kaydırma sekmeyi kapatır.
 * Altta yeni sekme düğmesi, üstte "Tümünü kapat".
 *
 * Önizleme metni arka planda okunur ([icerik]); o gelene kadar kartta liste
 * önbelleğindeki özet durur.
 */
class SekmeEkrani(
    private val activity: Activity,
    kartlar: List<Kart>,
    /** Notun önizlenecek metni (arka planda çağrılır); null ise önizleme yok. */
    private val icerik: (Kart) -> String?,
    private val secildi: (Kart) -> Unit,
    private val kapatildi: (Kart) -> Unit,
    private val yeniIstendi: () -> Unit,
    private val hepsiKapatildi: () -> Unit
) {
    class Kart(
        val id: Long,
        val baslik: String,
        /** İçerik okunamazsa ya da okunmamalıysa (kilitli not) gösterilen kısa metin. */
        val ozet: String,
        val etkin: Boolean,
        /** Kilitli/şifreli not: içeriği okunmaz. */
        val gizli: Boolean
    ) {
        var onizleme: CharSequence? = null
    }

    private val liste = kartlar.toMutableList()
    private var dialog: Dialog? = null
    private lateinit var sayac: TextView
    private val y = activity.resources.displayMetrics.density
    private val metinRengi = ContextCompat.getColor(activity, R.color.metin)
    private val ikincil = ContextCompat.getColor(activity, R.color.metin_ikincil)
    private val vurgu = Renkler.vurgu(activity)
    private val stil = NotOnizleme.uygulamaStili(activity)

    fun kapat() {
        dialog?.dismiss()
        dialog = null
    }

    fun goster() {
        val d = Dialog(activity, R.style.SekmeEkraniTemasi)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val kok = FrameLayout(activity)
        kok.fitsSystemWindows = true
        kok.setBackgroundColor(ContextCompat.getColor(activity, R.color.zemin))

        val dikey = LinearLayout(activity)
        dikey.orientation = LinearLayout.VERTICAL
        kok.addView(dikey, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Üst satır: geri, "3 sekme", Tümünü kapat
        val ust = LinearLayout(activity)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL
        ust.setPadding((8 * y).toInt(), (8 * y).toInt(), (12 * y).toInt(), (4 * y).toInt())
        val geri = ImageButton(activity)
        geri.setImageResource(R.drawable.ic_geri)
        geri.imageTintList = ColorStateList.valueOf(metinRengi)
        geri.setBackgroundResource(zemin(android.R.attr.selectableItemBackgroundBorderless))
        geri.contentDescription = activity.getString(R.string.kapat)
        geri.setOnClickListener { kapat() }
        ust.addView(geri, LinearLayout.LayoutParams((48 * y).toInt(), (48 * y).toInt()))
        sayac = TextView(activity)
        sayac.textSize = 20f
        sayac.setTypeface(null, Typeface.BOLD)
        sayac.setTextColor(metinRengi)
        val sayacLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        sayacLp.leftMargin = (4 * y).toInt()
        ust.addView(sayac, sayacLp)
        val hepsi = TextView(activity)
        hepsi.text = activity.getString(R.string.tumunu_kapat)
        hepsi.textSize = 14f
        hepsi.setTypeface(null, Typeface.BOLD)
        hepsi.setTextColor(vurgu)
        hepsi.gravity = Gravity.CENTER
        hepsi.setPadding((12 * y).toInt(), 0, (12 * y).toInt(), 0)
        hepsi.setBackgroundResource(zemin(android.R.attr.selectableItemBackground))
        hepsi.setOnClickListener {
            kapat()
            hepsiKapatildi()
        }
        ust.addView(hepsi, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (44 * y).toInt()))
        dikey.addView(ust)

        // Kart ızgarası
        val izgara = RecyclerView(activity)
        izgara.layoutManager = GridLayoutManager(activity, SUTUN)
        izgara.clipToPadding = false
        izgara.setPadding((12 * y).toInt(), (4 * y).toInt(), (12 * y).toInt(), (104 * y).toInt())
        val ekranEni = activity.resources.displayMetrics.widthPixels
        val kartEni = (ekranEni - 24 * y - SUTUN * 2 * KART_PAYI * y) / SUTUN
        val adaptor = Adaptor((kartEni * KART_ORANI).toInt())
        izgara.adapter = adaptor
        dikey.addView(izgara, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Sağa ya da sola kaydırınca sekme kapanır (tarayıcılardaki gibi).
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder) = false
            // Kart dar (ekranın yarısı); yarısı kadar sürüklemek zorlaşıyordu.
            override fun getSwipeThreshold(t: RecyclerView.ViewHolder) = 0.35f
            override fun onSwiped(tutucu: RecyclerView.ViewHolder, yon: Int) {
                val sira = tutucu.bindingAdapterPosition
                if (sira != RecyclerView.NO_POSITION) kartiKapat(sira, adaptor)
            }
            override fun onChildDraw(
                c: android.graphics.Canvas, r: RecyclerView, t: RecyclerView.ViewHolder,
                dx: Float, dy: Float, durum: Int, aktif: Boolean
            ) {
                // Kart uzaklaştıkça solar.
                t.itemView.alpha = 1f - minOf(1f, kotlin.math.abs(dx) / t.itemView.width)
                super.onChildDraw(c, r, t, dx, dy, durum, aktif)
            }
            override fun clearView(r: RecyclerView, t: RecyclerView.ViewHolder) {
                super.clearView(r, t)
                t.itemView.alpha = 1f
            }
        }).attachToRecyclerView(izgara)

        // Altta yüzen yeni sekme düğmesi (ana ekrandaki yeni not düğmesi gibi).
        val arti = ImageButton(activity)
        arti.setImageResource(R.drawable.ic_arti)
        arti.setBackgroundResource(R.drawable.bg_nav_orta)
        arti.backgroundTintList = ColorStateList.valueOf(vurgu)
        arti.imageTintList = ColorStateList.valueOf(Renkler.vurguUzeri(activity))
        arti.contentDescription = activity.getString(R.string.yeni_sekme)
        arti.elevation = 6 * y
        arti.setOnClickListener {
            kapat()
            yeniIstendi()
        }
        ipucuVer(arti)
        val artiLp = FrameLayout.LayoutParams((60 * y).toInt(), (60 * y).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        artiLp.bottomMargin = (24 * y).toInt()
        kok.addView(arti, artiLp)

        sayaciGuncelle()
        d.setContentView(kok)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Açık temada saat ve pil simgeleri koyu olsun.
            val acik = ColorUtils.calculateLuminance(ContextCompat.getColor(activity, R.color.zemin)) > 0.5
            androidx.core.view.WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = acik
                isAppearanceLightNavigationBars = acik
            }
        }
        dialog = d
        d.setOnDismissListener { dialog = null }
        d.show()
        // Etkin sekme görünür olsun.
        liste.indexOfFirst { it.etkin }.takeIf { it > 0 }?.let { izgara.scrollToPosition(it) }

        // Önizlemeler arka planda; geldikçe kart tazelenir.
        val kopya = liste.toList()
        Thread {
            for (kart in kopya) {
                if (kart.gizli) continue
                val ham = runCatching { icerik(kart) }.getOrNull() ?: continue
                val hazir = onizleme(ham, kart.baslik, stil)
                activity.runOnUiThread {
                    kart.onizleme = hazir
                    val sira = liste.indexOf(kart)
                    if (sira >= 0 && dialog != null) adaptor.notifyItemChanged(sira)
                }
            }
        }.start()
    }

    private fun kartiKapat(sira: Int, adaptor: Adaptor) {
        val kart = liste.getOrNull(sira) ?: return
        liste.removeAt(sira)
        adaptor.notifyItemRemoved(sira)
        sayaciGuncelle()
        // Bu editörün sekmesi kapanırsa çağıran ekranı kapatıp başka nota geçer.
        kapatildi(kart)
    }

    private fun sayaciGuncelle() {
        sayac.text = activity.resources.getQuantityString(R.plurals.sekme_sayisi, liste.size, liste.size)
    }

    private fun zemin(nitelik: Int): Int {
        val deger = android.util.TypedValue()
        activity.theme.resolveAttribute(nitelik, deger, true)
        return deger.resourceId
    }

    private inner class Tutucu(
        val kok: LinearLayout,
        val baslik: TextView,
        val kapatDugmesi: ImageButton,
        val govde: TextView
    ) : RecyclerView.ViewHolder(kok)

    private inner class Adaptor(private val kartBoyu: Int) : RecyclerView.Adapter<Tutucu>() {
        override fun getItemCount(): Int = liste.size

        override fun onCreateViewHolder(ust: ViewGroup, tur: Int): Tutucu {
            val kok = LinearLayout(activity)
            kok.orientation = LinearLayout.VERTICAL
            kok.isClickable = true
            // Dokunma dalgası kartın üstünde (foreground Android 6+).
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                kok.foreground = ContextCompat.getDrawable(activity, zemin(android.R.attr.selectableItemBackground))
            }
            kok.clipToOutline = true
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kartBoyu)
            val pay = (KART_PAYI * y).toInt()
            lp.setMargins(pay, pay, pay, pay)
            kok.layoutParams = lp

            val ustSatir = LinearLayout(activity)
            ustSatir.orientation = LinearLayout.HORIZONTAL
            ustSatir.gravity = Gravity.CENTER_VERTICAL
            ustSatir.setPadding((12 * y).toInt(), (4 * y).toInt(), 0, 0)
            val baslik = TextView(activity)
            baslik.textSize = 14f
            baslik.setTypeface(null, Typeface.BOLD)
            baslik.setTextColor(metinRengi)
            baslik.setSingleLine()
            baslik.ellipsize = TextUtils.TruncateAt.END
            ustSatir.addView(baslik, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val kapatma = ImageButton(activity)
            kapatma.setImageResource(R.drawable.ic_kapat)
            kapatma.imageTintList = ColorStateList.valueOf(ikincil)
            kapatma.setBackgroundResource(zemin(android.R.attr.selectableItemBackgroundBorderless))
            kapatma.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            kapatma.setPadding((10 * y).toInt(), (10 * y).toInt(), (10 * y).toInt(), (10 * y).toInt())
            kapatma.contentDescription = activity.getString(R.string.kapat)
            ustSatir.addView(kapatma, LinearLayout.LayoutParams((40 * y).toInt(), (40 * y).toInt()))
            kok.addView(ustSatir)

            val govde = TextView(activity)
            govde.textSize = 10.5f
            govde.setLineSpacing(2 * y, 1f)
            govde.setTextColor(metinRengi)
            govde.setPadding((12 * y).toInt(), 0, (12 * y).toInt(), (10 * y).toInt())
            kok.addView(govde, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            return Tutucu(kok, baslik, kapatma, govde)
        }

        override fun onBindViewHolder(t: Tutucu, sira: Int) {
            val kart = liste[sira]
            t.baslik.text = kart.baslik
            t.baslik.setTextColor(if (kart.etkin) vurgu else metinRengi)
            t.govde.text = kart.onizleme ?: kart.ozet
            // Satır sınırı kartın boyuna göre: yarım kalan satır görünmesin.
            t.govde.maxLines = Int.MAX_VALUE
            t.govde.post {
                val satirBoyu = t.govde.lineHeight.takeIf { it > 0 } ?: return@post
                val sigan = (t.govde.height - t.govde.paddingTop - t.govde.paddingBottom) / satirBoyu
                if (sigan > 0) {
                    t.govde.maxLines = sigan
                    t.govde.ellipsize = TextUtils.TruncateAt.END
                }
            }
            val sekil = GradientDrawable()
            sekil.cornerRadius = 18 * y
            sekil.setColor(ContextCompat.getColor(activity, R.color.kart))
            // Açık olan sekme vurgu renginde çerçeveli.
            if (kart.etkin) sekil.setStroke((2 * y).toInt(), vurgu)
            t.kok.background = sekil
            t.kok.setOnClickListener {
                kapat()
                secildi(kart)
            }
            t.kapatDugmesi.setOnClickListener {
                val simdiki = t.bindingAdapterPosition
                if (simdiki != RecyclerView.NO_POSITION) kartiKapat(simdiki, this)
            }
        }
    }

    companion object {
        private const val SUTUN = 2
        private const val KART_PAYI = 6
        /** Kartın boyu eninin bu katı: telefon ekranı gibi dikey. */
        private const val KART_ORANI = 1.35f
        private const val EN_COK_SATIR = 40

        /**
         * Kartın önizlemesi: satırlar uygulamadaki gibi biçimli ([NotOnizleme]);
         * ilk satır kartın başlığıyla aynıysa atlanır, kod bloğu eş aralıklı.
         */
        fun onizleme(ham: String, baslik: String, stil: NotOnizleme.Stil): CharSequence {
            val sb = SpannableStringBuilder()
            var ilk = true
            var kodda = false
            var kutuRengi: Int? = null
            var sayi = 0
            for (satir0 in ham.lineSequence()) {
                if (sayi >= EN_COK_SATIR) break
                val satir = satir0.trimEnd()
                if (MarkdownBicimci.KOD_CITI.containsMatchIn(satir)) {
                    kodda = !kodda
                    continue
                }
                if (ilk) {
                    if (satir.isBlank()) continue
                    ilk = false
                    if (NotOnizleme.sade(satir) == baslik.trim()) continue
                }
                val bas = sb.length
                if (kodda) {
                    sb.append(satir)
                    sb.setSpan(android.text.style.TypefaceSpan("monospace"), bas, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    val cozulen = NotOnizleme.coz(satir)
                    cozulen.kutuTuru?.let { kutuRengi = stil.kutuRengi(it) }
                    if (cozulen.kutuTuru == null && !cozulen.alinti) kutuRengi = null
                    val metin = NotOnizleme.bicimli(cozulen, stil, if (cozulen.alinti) kutuRengi else null)
                    // Art arda boş satırlar teke iner.
                    if (metin.isBlank() && (sb.isEmpty() || sb.endsWith("\n\n"))) continue
                    sb.append(metin)
                }
                sb.append('\n')
                sayi++
            }
            return sb.trimEnd()
        }
    }
}
