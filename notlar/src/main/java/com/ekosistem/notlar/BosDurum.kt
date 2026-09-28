package com.ekosistem.notlar

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * Liste boşken gösterilen ortak alan (res/layout/bos_durum.xml): vurgunun
 * pastel tonunda ikon kartı, kısa başlık, açıklama ve gerekirse tek eylem.
 * Ana ekran, görevler ve çöp kutusu aynı görünümü kullanır.
 */
object BosDurum {

    fun goster(
        kok: View,
        ikon: Int,
        baslik: String,
        aciklama: String,
        eylem: Pair<String, () -> Unit>? = null
    ) {
        val c = kok.context
        val vurgu = Renkler.vurgu(c)
        val ikonGorunum = kok.findViewById<ImageView>(R.id.bosIkon)
        ikonGorunum.setImageResource(ikon)
        ikonGorunum.imageTintList = ColorStateList.valueOf(vurgu)
        ikonGorunum.backgroundTintList = ColorStateList.valueOf((vurgu and 0x00FFFFFF) or 0x1F000000)
        kok.findViewById<TextView>(R.id.bosBaslik).text = baslik
        kok.findViewById<TextView>(R.id.bosAciklama).text = aciklama
        val dugme = kok.findViewById<TextView>(R.id.bosEylem)
        if (eylem == null) {
            dugme.visibility = View.GONE
        } else {
            dugme.visibility = View.VISIBLE
            dugme.text = eylem.first
            dugme.backgroundTintList = ColorStateList.valueOf(vurgu)
            dugme.setTextColor(Renkler.vurguUzeri(c))
            dugme.setOnClickListener { eylem.second() }
        }
        kok.visibility = View.VISIBLE
    }
}
