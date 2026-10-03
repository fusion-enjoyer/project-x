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
        Widgetlar.hepsiniGuncelle(activity)
        yenile()
        GeriAl.goster(activity, activity.getString(R.string.sehir_silindi, ad)) {
            Depo.sehirleriKaydet(activity, onceki)
            Widgetlar.hepsiniGuncelle(activity)
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
        val d = activity.resources.displayMetrics.density
        for (id in sehirler) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_sehir, liste, false)
            val gercekAd = Sehirler.ad(id)
            val etiket = Depo.sehirEtiketleri(activity)[id]
            val ad = etiket ?: gercekAd
            val fark = Sehirler.farkMetni(activity, id, simdi)
            v.findViewById<TextView>(R.id.sehirAd).text = ad
            // Etiket varsa asıl şehir adı da küçük satırda kalır.
            v.findViewById<TextView>(R.id.sehirFark).text = if (etiket != null) "$gercekAd · $fark" else fark
            v.findViewById<TextView>(R.id.sehirSaat).let { (it as TextClock).timeZone = id }
            v.setOnLongClickListener {
                sehirSecenekleri(id, ad, gercekAd, etiket)
                true
            }
            // Sola kaydırarak da silinir; yanlışlıkla olursa "Geri al".
            val sarmal = KaydirmaSatiri(activity)
            sarmal.tag = id
            sarmal.icerik(v)
            sarmal.sola = KaydirmaSatiri.silme(activity, activity.getString(R.string.sil)) { sehriSil(id, ad) }
            sarmal.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * d).toInt() }
            val tutac = v.findViewById<View>(R.id.sehirTutac)
            tutac.visibility = if (sehirler.size > 1) View.VISIBLE else View.GONE
            tutac.setOnTouchListener(Suruklenebilir(sarmal))
            liste.addView(sarmal)
        }
    }

    private fun sehirSecenekleri(id: String, ad: String, gercekAd: String, etiket: String?) {
        val sayfa = AltSayfa(activity).baslik(ad)
        sayfa.madde(R.drawable.ic_etiket, activity.getString(if (etiket == null) R.string.etiket_ekle else R.string.etiket_degistir)) {
            AltSayfa(activity).baslik(gercekAd)
                .girdi(activity.getString(R.string.sehir_etiket_ipucu), etiket.orEmpty(), activity.getString(R.string.tamam)) { yeni ->
                    Depo.sehirEtiketiKaydet(activity, id, yeni.take(24))
                    Widgetlar.hepsiniGuncelle(activity)
                    yenile()
                }.goster()
        }
        if (etiket != null) {
            sayfa.madde(com.ekosistem.tasarim.R.drawable.ic_kapat, activity.getString(R.string.etiketi_kaldir)) {
                Depo.sehirEtiketiKaydet(activity, id, "")
                Widgetlar.hepsiniGuncelle(activity)
                yenile()
            }
        }
        sayfa.madde(R.drawable.ic_sil, activity.getString(R.string.sil), tehlikeli = true) { sehriSil(id, ad) }
        sayfa.goster()
    }

    /**
     * Tutaçtan sürükleyerek sıralama. Görünümler yer değiştirmez (dokunma akışı
     * kopmasın): sürüklenen kart parmakla gider, komşular boşluğu açmak için
     * kayar; parmak kalkınca yeni sıra kaydedilir ve liste yeniden çizilir.
     * Yerel saat satırı (ilk satır) sabittir.
     */
    private inner class Suruklenebilir(private val kart: View) : View.OnTouchListener {
        private var baslangicY = 0f
        private var hedef = 0
        private var sira = 0
        private var adim = 0f

        override fun onTouch(v: View, e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    baslangicY = e.rawY
                    sira = liste.indexOfChild(kart)
                    hedef = sira
                    adim = kart.height + (12 * activity.resources.displayMetrics.density)
                    kart.translationZ = 16 * activity.resources.displayMetrics.density
                    kart.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start()
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val enAz = -(sira - 1) * adim
                    val enCok = (liste.childCount - 1 - sira) * adim
                    val dy = (e.rawY - baslangicY).coerceIn(enAz, enCok)
                    kart.translationY = dy
                    val yeniHedef = (sira + Math.round(dy / adim)).coerceIn(1, liste.childCount - 1)
                    if (yeniHedef != hedef) {
                        hedef = yeniHedef
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        for (i in 1 until liste.childCount) {
                            if (i == sira) continue
                            val kayma = when {
                                sira < hedef && i in (sira + 1)..hedef -> -adim
                                sira > hedef && i in hedef until sira -> adim
                                else -> 0f
                            }
                            liste.getChildAt(i).animate().translationY(kayma).setDuration(150).start()
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // Yeni sıra: sürüklenen kart hedef konumuna taşınır.
                    val kimlikler = (1 until liste.childCount).map { liste.getChildAt(it).tag as String }.toMutableList()
                    if (hedef != sira && e.actionMasked == android.view.MotionEvent.ACTION_UP) {
                        val tasinan = kimlikler.removeAt(sira - 1)
                        kimlikler.add(hedef - 1, tasinan)
                        Depo.sehirleriKaydet(activity, kimlikler)
                        Widgetlar.hepsiniGuncelle(activity)
                    }
                    kart.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    kart.translationZ = 0f
                    // Dokunma olayı bitmeden liste yeniden kurulursa çöker: ertele.
                    liste.post { yenile() }
                }
            }
            return true
        }
    }
}
