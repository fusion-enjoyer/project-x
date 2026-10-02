package com.ekosistem.saat

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/**
 * Kronometre sekmesi. Ekran yalnız sekme görünürken ve çalışırken, saniyenin
 * onda biri kadar sıklıkta güncellenir (sürekli animasyon pil ve hız yer).
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
    private val tik = object : Runnable {
        override fun run() {
            sureYaz()
            if (gorunur && Depo.kronometre(activity).calisiyor) isleyici.postDelayed(this, 100)
        }
    }

    init {
        sag.setOnClickListener {
            val k = Depo.kronometre(activity)
            val simdi = SystemClock.elapsedRealtime()
            Depo.kronometreKaydet(activity, if (k.calisiyor) k.durdur(simdi) else k.basla(simdi))
            yenile()
        }
        sol.setOnClickListener {
            val k = Depo.kronometre(activity)
            Depo.kronometreKaydet(activity, if (k.calisiyor) k.tur(SystemClock.elapsedRealtime()) else k.sifirla())
            yenile()
        }
    }

    fun goster(evet: Boolean) {
        gorunur = evet
        kok.visibility = if (evet) View.VISIBLE else View.GONE
        if (evet) yenile() else isleyici.removeCallbacks(tik)
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
        isleyici.removeCallbacks(tik)
        isleyici.post(tik)
    }

    private fun sureYaz() {
        sure.text = Kronometre.bicim(Depo.kronometre(activity).gecen(SystemClock.elapsedRealtime()))
    }

    private fun turlariYaz(k: Kronometre) {
        turlar.removeAllViews()
        val sureler = k.turSureleri()
        if (sureler.isEmpty()) return
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
            satir.addView(yazi(Kronometre.bicim(sureler[i]), 1.2f, Gravity.CENTER, renk))
            satir.addView(yazi(Kronometre.bicim(k.turlar[i]), 1.2f, Gravity.END,
                ContextCompat.getColor(activity, TR.color.metin_ikincil)))
            turlar.addView(satir, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * d).toInt()
            })
        }
    }

    fun durdur() = isleyici.removeCallbacks(tik)
}
