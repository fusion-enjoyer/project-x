package com.ekosistem.saat

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/**
 * Kronometre sekmesi. Süre yalnız sekme görünürken ve çalışırken, ekranın
 * kendi kare hızında (Choreographer) güncellenir; yüzde bir saniye basamağı
 * akıcı akar. Sekme gizlenince ya da durunca döngü biter, arka planda iş yok.
 */
class KronometreSekmesi(private val activity: Activity) {

    private val kok: View = activity.findViewById(R.id.sekmeKronometre)
    private val sure: TextView = activity.findViewById(R.id.kronoSure)
    private val sol: TextView = activity.findViewById(R.id.kronoSol)
    private val sag: TextView = activity.findViewById(R.id.kronoSag)
    private val turlar: LinearLayout = activity.findViewById(R.id.kronoTurlar)
    private val vurgu = Tasarim.vurgu(activity)
    private val isleyici = Handler(Looper.getMainLooper())
    private var gorunur = false
    private var kareBekliyor = false
    private val kare = Choreographer.FrameCallback {
        kareBekliyor = false
        sureYaz()
        if (gorunur && kronometreCalisiyor) kareIste()
    }
    private var kronometreCalisiyor = false

    private fun kareIste() {
        if (kareBekliyor) return
        kareBekliyor = true
        Choreographer.getInstance().postFrameCallback(kare)
    }

    private fun kareyiDurdur() {
        kareBekliyor = false
        Choreographer.getInstance().removeFrameCallback(kare)
    }

    init {
        sag.setOnClickListener {
            val k = Depo.kronometre(activity)
            val simdi = SystemClock.elapsedRealtime()
            Depo.kronometreKaydet(
                activity, if (k.calisiyor) k.durdur(simdi) else k.basla(simdi, System.currentTimeMillis())
            )
            CalisanBildirim.guncelle(activity)
            yenile()
        }
        sol.setOnClickListener {
            val k = Depo.kronometre(activity)
            Depo.kronometreKaydet(activity, if (k.calisiyor) k.tur(SystemClock.elapsedRealtime()) else k.sifirla())
            CalisanBildirim.guncelle(activity)
            yenile()
        }
    }

    fun goster(evet: Boolean) {
        gorunur = evet
        kok.visibility = if (evet) View.VISIBLE else View.GONE
        if (evet) yenile() else kareyiDurdur()
    }

    fun yenile() {
        val k = Depo.kronometre(activity)
        sag.setText(if (k.calisiyor) R.string.durdur else if (k.sifirda) R.string.basla else R.string.devam)
        sag.backgroundTintList = ColorStateList.valueOf(
            if (k.calisiyor) ContextCompat.getColor(activity, TR.color.tehlike) else vurgu
        )
        sag.setTextColor(Tasarim.uzerindekiRenk(if (k.calisiyor) ContextCompat.getColor(activity, TR.color.tehlike) else vurgu))
        sol.setText(if (k.calisiyor) R.string.tur else R.string.sifirla)
        sol.isEnabled = !k.sifirda
        sol.alpha = if (k.sifirda) 0.4f else 1f
        turlariYaz(k)
        kronometreCalisiyor = k.calisiyor
        sureYaz()
        if (k.calisiyor) kareIste() else kareyiDurdur()
    }

    private fun sureYaz() {
        sure.text = Kronometre.bicim(Depo.kronometre(activity).gecen(SystemClock.elapsedRealtime()), basamak = 2)
    }

    private fun turlariYaz(k: Kronometre) {
        turlar.removeAllViews()
        val sureler = k.turSureleri()
        // Tur yokken süre ve düğmeler ekranın ortasında durur; tur gelince yukarı çıkar.
        val turVar = sureler.isNotEmpty()
        activity.findViewById<View>(R.id.kronoUst).visibility = if (turVar) View.GONE else View.VISIBLE
        activity.findViewById<View>(R.id.kronoAlt).visibility = if (turVar) View.GONE else View.VISIBLE
        activity.findViewById<View>(R.id.kronoTurKaydirici).visibility = if (turVar) View.VISIBLE else View.GONE
        if (!turVar) return
        val enHizli = if (sureler.size > 1) sureler.indexOf(sureler.minOrNull()) else -1
        val enYavas = if (sureler.size > 1) sureler.indexOf(sureler.maxOrNull()) else -1
        val d = activity.resources.displayMetrics.density
        // En yeni tur üstte.
        for (i in sureler.indices.reversed()) {
            val satir = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((18 * d).toInt(), (12 * d).toInt(), (18 * d).toInt(), (12 * d).toInt())
                setBackgroundResource(TR.drawable.bg_kart)
            }
            val renk = when (i) {
                enHizli -> ContextCompat.getColor(activity, TR.color.tamam)
                enYavas -> ContextCompat.getColor(activity, TR.color.tehlike)
                else -> ContextCompat.getColor(activity, TR.color.metin)
            }
            fun yazi(metin: String, agirlik: Float, hiza: Int, r: Int) = TextView(activity).apply {
                text = metin
                textSize = 16f
                gravity = hiza
                setTextColor(r)
                fontFeatureSettings = "tnum"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, agirlik)
            }
            satir.addView(yazi(activity.getString(R.string.tur_n, i + 1), 1f, Gravity.START, renk))
            satir.addView(yazi(Kronometre.bicim(sureler[i], basamak = 2), 1.2f, Gravity.CENTER, renk))
            satir.addView(yazi(Kronometre.bicim(k.turlar[i], basamak = 2), 1.2f, Gravity.END,
                ContextCompat.getColor(activity, TR.color.metin_ikincil)))
            turlar.addView(satir, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * d).toInt()
            })
        }
    }

    fun durdur() = kareyiDurdur()
}
