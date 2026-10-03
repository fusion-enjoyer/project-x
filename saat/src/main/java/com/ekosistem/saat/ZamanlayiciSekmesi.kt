package com.ekosistem.saat

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer

/**
 * Zamanlayıcı sekmesi: rakamla süre girişi (sağdan dolar, "130" = 1 dk 30 sn)
 * ya da çalışan zamanlayıcıların kartları (+1 dk, duraklat, sıfırla, sil).
 * Aynı anda birden çok zamanlayıcı çalışabilir.
 */
class ZamanlayiciSekmesi(private val activity: Activity) {

    private val kok: View = activity.findViewById(R.id.sekmeZamanlayici)
    private val giris: View = activity.findViewById(R.id.zamanGiris)
    private val liste: LinearLayout = activity.findViewById(R.id.zamanListesi)
    private val kaydirici: View = activity.findViewById(R.id.zamanKaydirici)
    private val gosterge: TextView = activity.findViewById(R.id.zamanGosterge)
    private val vurgu = Tasarim.vurgu(activity)
    private val isleyici = Handler(Looper.getMainLooper())
    private var rakamlar = ""
    private var girisAcik = false
    private var gorunur = false
    private val tik = object : Runnable {
        override fun run() {
            kalanlariYaz()
            val simdi = System.currentTimeMillis()
            val calisanlar = Depo.zamanlayicilar(activity).filter { it.calisiyor }
            if (gorunur && calisanlar.isNotEmpty()) {
                // Gösterilen saniye, kalan sürenin yukarı yuvarlanmışı: tam değiştiği
                // anda çiz. (Duvar saati saniyesine hizalayınca ekran bir saniyeye
                // kadar geç kalıyor, sayaç "yavaş" akıyormuş gibi görünüyordu.)
                val sonraki = calisanlar.minOf { (it.kalan(simdi) - 1).coerceAtLeast(0) % 1000 + 1 }
                isleyici.postDelayed(this, sonraki + 4)
            }
        }
    }

    init {
        val tuslar = mapOf(
            R.id.tus0 to "0", R.id.tus1 to "1", R.id.tus2 to "2", R.id.tus3 to "3", R.id.tus4 to "4",
            R.id.tus5 to "5", R.id.tus6 to "6", R.id.tus7 to "7", R.id.tus8 to "8", R.id.tus9 to "9",
            R.id.tus00 to "00"
        )
        for ((id, r) in tuslar) activity.findViewById<View>(id).setOnClickListener {
            if (rakamlar.isEmpty() && r.all { it == '0' }) return@setOnClickListener
            rakamlar = (rakamlar + r).take(6)
            gostergeYaz()
        }
        activity.findViewById<View>(R.id.tusSil).apply {
            contentDescription = activity.getString(R.string.sil)
            setOnClickListener { rakamlar = rakamlar.dropLast(1); gostergeYaz() }
            setOnLongClickListener { rakamlar = ""; gostergeYaz(); true }
        }
        activity.findViewById<TextView>(R.id.zamanBaslat).apply {
            setText(R.string.basla)
            backgroundTintList = ColorStateList.valueOf(vurgu)
            setTextColor(Tasarim.vurguUzeri(activity))
            setOnClickListener { baslat() }
        }
    }

    fun goster(evet: Boolean) {
        gorunur = evet
        kok.visibility = if (evet) View.VISIBLE else View.GONE
        if (evet) {
            if (Depo.zamanlayicilar(activity).isEmpty()) girisiAc() else yenile()
        } else {
            isleyici.removeCallbacks(tik)
        }
    }

    /** Alt çubuktaki "+": yeni zamanlayıcı girişi; girişteyken listeye dönüş. */
    fun yeniDugmesi() {
        if (girisAcik && Depo.zamanlayicilar(activity).isNotEmpty()) {
            girisAcik = false
            yenile()
        } else {
            girisiAc()
        }
    }

    private fun girisiAc() {
        girisAcik = true
        rakamlar = Depo.sonSure(activity)
        gostergeYaz()
        yenile()
    }

    /** "05m 00s" biçiminde; birimler küçük. */
    private fun gostergeYaz() {
        val r = rakamlar.padStart(6, '0')
        val b = SpannableStringBuilder()
        fun parca(sayi: String, birim: String) {
            b.append(sayi)
            val bas = b.length
            b.append(birim)
            b.setSpan(RelativeSizeSpan(0.45f), bas, b.length, 0)
            b.append("  ")
        }
        parca(r.substring(0, 2), activity.getString(R.string.birim_sa))
        parca(r.substring(2, 4), activity.getString(R.string.birim_dk))
        parca(r.substring(4, 6), activity.getString(R.string.birim_sn))
        gosterge.text = b.trimEnd()
        gosterge.contentDescription = Zamanlayici.bicim(Zamanlayici.rakamlardanMs(rakamlar))
        activity.findViewById<View>(R.id.zamanBaslat).apply {
            isEnabled = Zamanlayici.rakamlardanMs(rakamlar) > 0
            alpha = if (isEnabled) 1f else 0.4f
        }
    }

    private fun baslat() {
        val ms = Zamanlayici.rakamlardanMs(rakamlar)
        if (ms <= 0) return
        Depo.sonSureKaydet(activity, rakamlar)
        val id = (Depo.zamanlayicilar(activity).maxOfOrNull { it.id } ?: 0) + 1
        Depo.zamanlayiciYaz(activity, Zamanlayici(id, ms).baslat(System.currentTimeMillis()))
        ZamanlayiciKurucu.hepsiniKur(activity)
        girisAcik = false
        yenile()
    }

    fun yenile() {
        val zamanlayicilar = Depo.zamanlayicilar(activity)
        val girisGoster = girisAcik || zamanlayicilar.isEmpty()
        giris.visibility = if (girisGoster) View.VISIBLE else View.GONE
        kaydirici.visibility = if (girisGoster) View.GONE else View.VISIBLE
        liste.removeAllViews()
        if (!girisGoster) {
            for (z in zamanlayicilar) liste.addView(kart(z))
            liste.addView(yeniSatiri())
        }
        isleyici.removeCallbacks(tik)
        isleyici.post(tik)
    }

    /** Listenin altında görünür "yeni zamanlayıcı": var olanları silmeden bir tane daha kurulur. */
    private fun yeniSatiri(): View {
        val d = activity.resources.displayMetrics.density
        return TextView(activity).apply {
            text = "＋  " + activity.getString(R.string.yeni_zamanlayici)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(vurgu)
            setBackgroundResource(com.ekosistem.tasarim.R.drawable.bg_kart)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (56 * d).toInt()
            )
            setOnClickListener { girisiAc() }
        }
    }

    /** Geri tuşu: tuş takımı açıksa ve zamanlayıcılar varsa listeye döner. */
    fun girisiKapat(): Boolean {
        if (!girisAcik || Depo.zamanlayicilar(activity).isEmpty()) return false
        girisAcik = false
        yenile()
        return true
    }

    private fun kart(z: Zamanlayici): View {
        val v = LayoutInflater.from(activity).inflate(R.layout.item_zamanlayici, liste, false)
        v.tag = z.id
        v.findViewById<TextView>(R.id.zToplam).text =
            listOf(z.etiket, Zamanlayici.bicim(z.sure)).filter { it.isNotBlank() }.joinToString(" · ")
        v.findViewById<TextView>(R.id.zBaslat).apply {
            setText(if (z.calisiyor) R.string.duraklat else R.string.devam)
            backgroundTintList = ColorStateList.valueOf(vurgu)
            setTextColor(Tasarim.vurguUzeri(activity))
            setOnClickListener { degistir(z.id) { if (it.calisiyor) it.duraklat(simdi()) else it.baslat(simdi()) } }
        }
        v.findViewById<View>(R.id.zEkle).setOnClickListener { degistir(z.id) { it.ekle(60_000, simdi()) } }
        v.findViewById<View>(R.id.zSifirla).setOnClickListener { degistir(z.id) { it.sifirla() } }
        v.findViewById<View>(R.id.zSil).setOnClickListener {
            Depo.zamanlayiciSil(activity, z.id)
            ZamanlayiciKurucu.iptal(activity, z.id)
            yenile()
        }
        ipucuVer(v.findViewById(R.id.zSil))
        return v
    }

    private fun simdi() = System.currentTimeMillis()

    private fun degistir(id: Int, islem: (Zamanlayici) -> Zamanlayici) {
        val z = Depo.zamanlayici(activity, id) ?: return
        Depo.zamanlayiciYaz(activity, islem(z))
        ZamanlayiciKurucu.hepsiniKur(activity)
        yenile()
    }

    private fun kalanlariYaz() {
        val simdi = simdi()
        val harita = Depo.zamanlayicilar(activity).associateBy { it.id }
        for (i in 0 until liste.childCount) {
            val v = liste.getChildAt(i)
            val z = harita[v.tag as? Int] ?: continue
            v.findViewById<TextView>(R.id.zKalan).apply {
                text = Zamanlayici.bicim(z.kalan(simdi))
                alpha = if (z.calisiyor) 1f else 0.55f
            }
        }
    }

    fun durdur() = isleyici.removeCallbacks(tik)
}
