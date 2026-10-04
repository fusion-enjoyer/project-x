package com.ekosistem.takvim

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.SayiTekerlegi
import com.ekosistem.tasarim.Tasarim
import java.text.DateFormatSymbols
import java.util.Calendar
import com.ekosistem.tasarim.R as TR

/** Tarih ve saat seçen alt sayfalar (düzenleme ekranı ve tekrar sayfası ortak kullanır). */
object Secenekler {

    /** [gun] ile açılan tarih seçici; [enAz] verilirse ondan önceki günler seçilemez. */
    fun tarihSec(activity: Activity, baslik: String, gun: Int, enAz: Int? = null, sonuc: (Int) -> Unit) {
        val secici = DatePicker(activity)
        secici.init(Gun.yil(gun), Gun.ay(gun) - 1, Gun.ayinGunu(gun), null)
        if (enAz != null) {
            val c = Calendar.getInstance().apply {
                clear()
                set(Gun.yil(enAz), Gun.ay(enAz) - 1, Gun.ayinGunu(enAz))
            }
            secici.minDate = c.timeInMillis
        }
        AltSayfa(activity).baslik(baslik).icerik(secici)
            .madde(TR.drawable.ic_onay_isaret, activity.getString(R.string.tamam)) {
                sonuc(Gun.gun(secici.year, secici.month + 1, secici.dayOfMonth))
            }
            .goster()
    }

    /** Saat ve dakika tekerlekleri; 12 saatlik telefonda ÖÖ/ÖS tekerleği de gelir. */
    fun saatSec(activity: Activity, baslik: String, dakika: Int, sonuc: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        val kutu = LinearLayout(activity)
        kutu.orientation = LinearLayout.HORIZONTAL
        kutu.gravity = Gravity.CENTER

        val saat = SayiTekerlegi(activity)
        val dk = SayiTekerlegi(activity)
        val ogle = SayiTekerlegi(activity)
        saat.olcek = 0.75f
        dk.olcek = 0.75f
        ogle.olcek = 0.5f
        saat.ekranOkuyucuAdi = baslik
        dk.ekranOkuyucuAdi = baslik
        ogle.ekranOkuyucuAdi = baslik
        dk.adet = 60
        dk.ayarla(dakika % 60)
        val yirmiDort = DateFormat.is24HourFormat(activity)
        if (yirmiDort) {
            saat.adet = 24
            saat.ayarla(dakika / 60)
        } else {
            val ekler = DateFormatSymbols.getInstance().amPmStrings
            saat.adet = 12
            saat.metin = { if (it == 0) "12" else it.toString() }
            saat.ayarla((dakika / 60) % 12)
            ogle.adet = 2
            ogle.metin = { ekler[it] }
            ogle.ayarla(dakika / 720)
        }
        kutu.addView(saat)
        val iki = TextView(activity)
        iki.text = ":"
        iki.textSize = 40f
        iki.setTypeface(null, Typeface.BOLD)
        iki.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        iki.setTextColor(ContextCompat.getColor(activity, TR.color.metin))
        kutu.addView(iki, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = (4 * d).toInt(); rightMargin = (4 * d).toInt()
        })
        kutu.addView(dk)
        if (!yirmiDort) kutu.addView(ogle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = (10 * d).toInt() })

        AltSayfa(activity).baslik(baslik).icerik(kutu)
            .madde(TR.drawable.ic_onay_isaret, activity.getString(R.string.tamam)) {
                val s = if (yirmiDort) saat.deger else saat.deger + 12 * ogle.deger
                sonuc(s * 60 + dk.deger)
            }
            .goster()
    }

    /** Seçilebilir çip (tekrar sayfası, hatırlatıcı…): seçiliyken vurgunun pastel zemini. */
    fun cip(activity: Activity, metin: String, secili: Boolean, beyazSayfada: Boolean = false, tikla: () -> Unit): TextView {
        val d = activity.resources.displayMetrics.density
        val t = TextView(activity)
        t.text = metin
        t.textSize = 15f
        t.gravity = Gravity.CENTER
        t.minHeight = (48 * d).toInt()
        // Arka plan önce: setBackgroundResource çizimin kendi dolgusunu uygular, dolgu sonra verilmeli.
        t.setBackgroundResource(R.drawable.bg_chip_hedef)
        t.setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
        val vurgu = Tasarim.vurgu(activity)
        // Seçili değilken zemin rengi: kart renkli alt sayfada çip belirgin dursun.
        t.backgroundTintList = ColorStateList.valueOf(
            if (secili) Tasarim.pastel(vurgu) else ContextCompat.getColor(activity, if (beyazSayfada) TR.color.kart else TR.color.zemin)
        )
        t.setTextColor(if (secili) vurgu else ContextCompat.getColor(activity, TR.color.metin))
        t.setTypeface(null, if (secili) Typeface.BOLD else Typeface.NORMAL)
        t.isSelected = secili
        t.setOnClickListener { tikla() }
        return t
    }
}
