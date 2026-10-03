package com.ekosistem.saat

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.BosDurum
import com.ekosistem.tasarim.Tasarim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dünya saati sekmesi: yerel saat ve eklenen şehirler. */
class DunyaSekmesi(private val activity: Activity, private val bosDurum: View) {

    private val kok: View = activity.findViewById(R.id.sekmeDunya)
    private val liste: LinearLayout = activity.findViewById(R.id.dunyaListesi)

    fun goster(evet: Boolean) {
        kok.visibility = if (evet) View.VISIBLE else View.GONE
        if (evet) yenile()
    }

    /** Telefonun kendi saati: şehir kartlarıyla aynı satır, vurgu tonlu zeminde. */
    private fun yerelSatir(): View {
        val v = LayoutInflater.from(activity).inflate(R.layout.item_sehir, liste, false)
        val tz = TimeZone.getDefault()
        val ad = runCatching { Sehirler.ad(tz.id) }.getOrNull()?.takeIf { it.isNotBlank() && '/' !in it }
            ?: activity.getString(R.string.yerel_saat)
        val bicim = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        val tarih = SimpleDateFormat(bicim, Locale.getDefault()).format(Date())
        v.findViewById<TextView>(R.id.sehirAd).text = ad
        v.findViewById<TextView>(R.id.sehirFark).text = "${activity.getString(R.string.yerel_saat)} · $tarih"
        v.backgroundTintList = ColorStateList.valueOf(Tasarim.pastel(Tasarim.vurgu(activity)))
        return v
    }

    private fun sehriSil(id: String, ad: String) {
        val onceki = Depo.sehirler(activity)
        Depo.sehirleriKaydet(activity, onceki - id)
        SaatWidget.guncelle(activity)
        yenile()
        GeriAl.goster(activity, activity.getString(R.string.sehir_silindi, ad)) {
            Depo.sehirleriKaydet(activity, onceki)
            SaatWidget.guncelle(activity)
            yenile()
        }
    }

    fun sehirEkle() = activity.startActivity(Intent(activity, SehirSecActivity::class.java))

    fun yenile() {
        val sehirler = Depo.sehirler(activity)
        liste.removeAllViews()
        if (sehirler.isEmpty()) {
            BosDurum.goster(
                bosDurum, R.drawable.ic_dunya,
                activity.getString(R.string.bos_sehir_baslik), activity.getString(R.string.bos_sehir_aciklama),
                activity.getString(R.string.sehir_ekle) to { sehirEkle() }
            )
            // Boş durum ortada dursun; yerel saat üstte kalsın.
            bosDurum.translationY = activity.resources.displayMetrics.density * 70
        } else {
            bosDurum.visibility = View.GONE
            bosDurum.translationY = 0f
        }
        val simdi = System.currentTimeMillis()
        liste.addView(yerelSatir())
        for (id in sehirler) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_sehir, liste, false)
            val ad = Sehirler.ad(id)
            v.findViewById<TextView>(R.id.sehirAd).text = ad
            v.findViewById<TextView>(R.id.sehirFark).text = Sehirler.farkMetni(activity, id, simdi)
            v.findViewById<TextView>(R.id.sehirSaat).let { (it as TextClock).timeZone = id }
            v.setOnLongClickListener {
                AltSayfa(activity).baslik(ad)
                    .madde(R.drawable.ic_sil, activity.getString(R.string.sil), tehlikeli = true) { sehriSil(id, ad) }
                    .goster()
                true
            }
            // Sola kaydırarak da silinir; yanlışlıkla olursa "Geri al".
            val sarmal = KaydirmaSatiri(activity)
            sarmal.icerik(v)
            sarmal.sola = KaydirmaSatiri.silme(activity, activity.getString(R.string.sil)) { sehriSil(id, ad) }
            sarmal.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * activity.resources.displayMetrics.density).toInt() }
            liste.addView(sarmal)
        }
    }
}
