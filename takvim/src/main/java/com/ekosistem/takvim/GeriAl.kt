package com.ekosistem.takvim

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView

/**
 * Alt kısımda kısa süre görünen "Silindi · Geri al" şeridi. Kaydırarak silme
 * yanlışlıkla olabilir; beş saniye içinde dokunulursa işlem geri alınır.
 */
object GeriAl {

    private val isleyici = Handler(Looper.getMainLooper())
    private var gizleme: Runnable? = null

    fun goster(activity: Activity, mesaj: String, geriAl: () -> Unit) {
        val v = activity.findViewById<TextView>(R.id.geriAlSeridi) ?: return
        val eylem = activity.getString(R.string.geri_al)
        val metin = SpannableString("$mesaj    $eylem")
        val bas = metin.length - eylem.length
        metin.setSpan(StyleSpan(android.graphics.Typeface.BOLD), bas, metin.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        metin.setSpan(UnderlineSpan(), bas, metin.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        v.text = metin
        v.contentDescription = "$mesaj. $eylem"
        v.setOnClickListener {
            gizle(v)
            geriAl()
        }
        v.animate().cancel()
        v.alpha = 0f
        v.visibility = View.VISIBLE
        v.animate().alpha(1f).setDuration(150).start()
        gizleme?.let { isleyici.removeCallbacks(it) }
        gizleme = Runnable { gizle(v) }.also { isleyici.postDelayed(it, 5000) }
    }

    private fun gizle(v: View) {
        gizleme?.let { isleyici.removeCallbacks(it) }
        gizleme = null
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(150).withEndAction { v.visibility = View.GONE }.start()
    }
}
