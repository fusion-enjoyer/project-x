package com.ekosistem.notlar

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class NotAdapter(
    private val onTikla: (Not) -> Unit,
    private val onUzunBas: (Not) -> Unit
) : RecyclerView.Adapter<NotAdapter.Tutucu>() {

    private var notlar: List<Not> = emptyList()

    var secililer: Set<String> = emptySet()
    var sorgu: String? = null
    var vurgu: Int = 0
    var kartRengi: Int = 0
    var secimRengi: Int = 0

    fun guncelle(yeni: List<Not>) {
        notlar = yeni
        notifyDataSetChanged()
    }

    fun notAl(pozisyon: Int): Not? = notlar.getOrNull(pozisyon)

    fun tumNotlar(): List<Not> = notlar

    class Tutucu(v: View) : RecyclerView.ViewHolder(v) {
        val baslik: TextView = v.findViewById(R.id.notBaslik)
        val ozet: TextView = v.findViewById(R.id.notOzet)
        val tarih: TextView = v.findViewById(R.id.notTarih)
        val sabit: ImageView = v.findViewById(R.id.sabitIkon)
        val kilit: ImageView = v.findViewById(R.id.kilitIkon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Tutucu {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_not, parent, false)
        return Tutucu(v)
    }

    override fun getItemCount(): Int = notlar.size

    override fun onBindViewHolder(t: Tutucu, pozisyon: Int) {
        val not = notlar[pozisyon]
        // Notun yazı tipi kartta da aynı: editörde seçilen yazı listede de görünür.
        val yazi = YaziTipleri.yazi(t.itemView.context)
        t.baslik.setTypeface(yazi, Typeface.BOLD)
        t.ozet.typeface = yazi
        t.baslik.text = vurgula(not.baslik)

        // Kilitli notta içerik yerine "Kilitli" yazar; önizleme sızdırmaz.
        // Eşitleme çakışması kopyası, içeriği yerine ne olduğunu söyler.
        val cakisma = Cakisma.coz(not.ad)
        val ikincil = when {
            not.kilitli -> t.ozet.context.getString(R.string.kilitli)
            cakisma != null -> t.ozet.context.getString(
                R.string.cakisma_ozet,
                cakisma.asilAd.substringBeforeLast('.')
            )
            else -> not.eslesme ?: not.ozet
        }
        t.ozet.text = when {
            not.kilitli -> ikincil
            cakisma != null -> SpannableString(ikincil).apply {
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(t.ozet.context, R.color.fark_silindi)),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            else -> vurgula(ikincil)
        }
        t.ozet.visibility = if (ikincil.isBlank()) View.GONE else View.VISIBLE
        t.kilit.visibility = if (not.kilitli) View.VISIBLE else View.GONE

        // Bir dakikadan yeni düzenlemede Android "0 dakika önce" yazıyordu.
        val gecen = System.currentTimeMillis() - not.degistirilme
        val zaman = when {
            not.degistirilme <= 0 -> ""
            gecen in 0 until DateUtils.MINUTE_IN_MILLIS -> t.ozet.context.getString(R.string.az_once)
            else -> DateUtils.getRelativeTimeSpanString(not.degistirilme).toString()
        }
        val parcalar = listOfNotNull(zaman.takeIf { it.isNotBlank() }, not.klasor)
        val tarihMetni = SpannableStringBuilder(parcalar.joinToString(" · "))
        if (not.gorev > 0) {
            // Görev rozeti: "✓ 3/7"; hepsi bittiyse vurgu renginde.
            if (tarihMetni.isNotEmpty()) tarihMetni.append(" · ")
            val bas = tarihMetni.length
            tarihMetni.append("✓ ${not.biten}/${not.gorev}")
            if (not.biten == not.gorev && vurgu != 0) {
                tarihMetni.setSpan(ForegroundColorSpan(vurgu), bas, tarihMetni.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        t.tarih.text = tarihMetni

        t.sabit.visibility = if (not.sabit) View.VISIBLE else View.GONE
        if (vurgu != 0) t.sabit.imageTintList = ColorStateList.valueOf(vurgu)

        // Renkler atanmadıysa karta dokunulmaz; yoksa zemin saydam kalır.
        if (kartRengi != 0) {
            val secili = secililer.contains(not.uri.toString())
            t.itemView.backgroundTintList =
                ColorStateList.valueOf(if (secili) secimRengi else kartRengi)
        }

        t.itemView.setOnClickListener { onTikla(not) }
        t.itemView.setOnLongClickListener { onUzunBas(not); true }
    }

    /** Arama yapılıyorsa eşleşen kısmı vurgu renginde ve kalın gösterir. */
    private fun vurgula(metin: String): CharSequence {
        val aranan = Arama.ifade(sorgu)
        if (aranan.isNullOrEmpty() || metin.isEmpty()) return metin
        // Aynı uzunlukta sadeleştirilir; bulunan konum asıl metinde de doğru.
        val kucuk = Arama.sadelestir(metin)
        var i = kucuk.indexOf(aranan)
        if (i < 0) return metin
        val s = SpannableString(metin)
        while (i >= 0) {
            val son = minOf(i + aranan.length, metin.length)
            s.setSpan(ForegroundColorSpan(vurgu), i, son, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(
                StyleSpan(Typeface.BOLD),
                i,
                son,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i = kucuk.indexOf(aranan, son)
        }
        return s
    }
}
