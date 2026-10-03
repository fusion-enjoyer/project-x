package com.ekosistem.takvim

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/**
 * "Özel tekrar" sayfası: sıklık, aralık ("her 2 haftada bir"), haftalık günler,
 * aylık seçenek (ayın günü / ayın n. haftası) ve bitiş (hiç / tarih / kez).
 * Alt sayfanın içeriği her değişiklikte yeniden çizilir; "Tamam" kuralı döndürür.
 */
class TekrarSayfasi(
    private val activity: Activity,
    private val baslangicGun: Int,
    mevcut: Kural?,
    private val tamam: (Kural?) -> Unit
) {
    private val d = activity.resources.displayMetrics.density
    private val kutu = LinearLayout(activity)

    private var sik: Sik? = mevcut?.sik ?: Sik.HAFTALIK
    private var aralik = mevcut?.aralik ?: 1
    private var gunler: Set<Int> = mevcut?.gunler?.takeIf { it.isNotEmpty() } ?: setOf(Gun.haftaGunu(baslangicGun))
    private var aySirasi = mevcut?.aySirasi ?: 0
    private var bitis = when {
        mevcut == null -> BITIS_HIC
        mevcut.sayi > 0 -> BITIS_SAYI
        mevcut.bitisGun != null -> BITIS_TARIH
        else -> BITIS_HIC
    }
    private var sayi = mevcut?.sayi?.takeIf { it > 0 } ?: 10
    private var bitisGun = mevcut?.bitisGun ?: (baslangicGun + 90)
    private var sayfa: AltSayfa? = null

    init {
        // Aylık varsayılan: ayın günü; ya da mevcut sıra kuralı korunur.
        kutu.orientation = LinearLayout.VERTICAL
    }

    fun goster() {
        ciz()
        // Önceki alt sayfa kapandıysa içerik hâlâ onun kökünde asılı olabilir.
        (kutu.parent as? ViewGroup)?.removeView(kutu)
        val s = AltSayfa(activity)
            .baslik(activity.getString(R.string.ozel_tekrar))
            .icerik(kutu)
            .madde(TR.drawable.ic_onay_isaret, activity.getString(R.string.tamam)) { tamam(kural()) }
        sayfa = s
        s.goster()
    }

    private fun kural(): Kural? {
        val s = sik ?: return null
        return Kural(
            sik = s,
            aralik = aralik,
            gunler = when {
                s == Sik.HAFTALIK -> gunler
                s == Sik.AYLIK && aySirasi != 0 -> setOf(Gun.haftaGunu(baslangicGun))
                else -> emptySet()
            },
            aySirasi = if (s == Sik.AYLIK) aySirasi else 0,
            sayi = if (bitis == BITIS_SAYI) sayi else 0,
            bitisGun = if (bitis == BITIS_TARIH) bitisGun else null
        )
    }

    private fun baslikEkle(metin: String) {
        val t = TextView(activity)
        t.text = metin
        t.textSize = 13f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(ContextCompat.getColor(activity, TR.color.metin_ikincil))
        t.setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
        kutu.addView(t)
    }

    private fun cipSerisi(vararg ogeler: View) {
        val sira = LinearLayout(activity)
        sira.orientation = LinearLayout.HORIZONTAL
        for (o in ogeler) {
            sira.addView(o, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = (8 * d).toInt()
            })
        }
        val kaydirici = HorizontalScrollView(activity)
        kaydirici.isHorizontalScrollBarEnabled = false
        kaydirici.addView(sira)
        kutu.addView(kaydirici)
    }

    private fun ciz() {
        kutu.removeAllViews()
        val aktif = sik

        baslikEkle(activity.getString(R.string.siklik))
        cipSerisi(
            *listOf(
                Sik.GUNLUK to R.string.sik_gunluk, Sik.HAFTALIK to R.string.sik_haftalik,
                Sik.AYLIK to R.string.sik_aylik, Sik.YILLIK to R.string.sik_yillik
            ).map { (s, ad) ->
                Secenekler.cip(activity, activity.getString(ad), aktif == s) { sik = s; aralik = aralik.coerceAtLeast(1); ciz() }
            }.toTypedArray()
        )

        // Aralık: "Her [−] 2 [+] hafta"
        baslikEkle(activity.getString(R.string.aralik))
        val birim = when (aktif) {
            Sik.GUNLUK -> R.string.birim_gun
            Sik.HAFTALIK -> R.string.birim_hafta
            Sik.AYLIK -> R.string.birim_ay
            else -> R.string.birim_yil
        }
        kutu.addView(
            sayici(aralik, 1, 99, { aralik = it; ciz() }, activity.getString(birim))
        )

        if (aktif == Sik.HAFTALIK) {
            baslikEkle(activity.getString(R.string.gunler))
            val sira = LinearLayout(activity)
            sira.orientation = LinearLayout.HORIZONTAL
            val haftaBasi = Depo.haftaBasi(activity)
            for (i in 0 until 7) {
                val gun = (haftaBasi + i) % 7
                val secili = gun in gunler
                val t = Secenekler.cip(activity, Metinler.haftaGunuKisa(gun), secili) {
                    // En az bir gün seçili kalır.
                    gunler = if (secili) (if (gunler.size > 1) gunler - gun else gunler) else gunler + gun
                    ciz()
                }
                t.setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
                t.textSize = 13f
                t.contentDescription = Metinler.haftaGunuUzun(gun)
                sira.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (3 * d).toInt() })
            }
            kutu.addView(sira)
        }

        if (aktif == Sik.AYLIK) {
            baslikEkle(activity.getString(R.string.ayin_gunu_baslik))
            val gun = Gun.haftaGunu(baslangicGun)
            val n = (Gun.ayinGunu(baslangicGun) - 1) / 7 + 1
            val son = Gun.ayinGunu(baslangicGun) + 7 > Gun.ayinGunSayisi(Gun.yil(baslangicGun), Gun.ay(baslangicGun))
            val secenekler = ArrayList<View>()
            secenekler.add(Secenekler.cip(activity, activity.getString(R.string.ayin_n_gunu, Gun.ayinGunu(baslangicGun)), aySirasi == 0) { aySirasi = 0; ciz() })
            if (n <= 4) {
                val ad = activity.resources.getStringArray(R.array.sira_adlari)[n - 1] + " " + Metinler.haftaGunuUzun(gun)
                secenekler.add(Secenekler.cip(activity, ad, aySirasi == n) { aySirasi = n; ciz() })
            }
            if (son) {
                secenekler.add(Secenekler.cip(activity, activity.getString(R.string.son_sira) + " " + Metinler.haftaGunuUzun(gun), aySirasi == -1) { aySirasi = -1; ciz() })
            }
            cipSerisi(*secenekler.toTypedArray())
        }

        baslikEkle(activity.getString(R.string.bitis))
        cipSerisi(
            Secenekler.cip(activity, activity.getString(R.string.bitis_hic), bitis == BITIS_HIC) { bitis = BITIS_HIC; ciz() },
            Secenekler.cip(activity, activity.getString(R.string.bitis_tarih), bitis == BITIS_TARIH) { bitis = BITIS_TARIH; ciz() },
            Secenekler.cip(activity, activity.getString(R.string.bitis_sayi), bitis == BITIS_SAYI) { bitis = BITIS_SAYI; ciz() }
        )
        if (bitis == BITIS_TARIH) {
            val t = Secenekler.cip(activity, Metinler.tamTarihKisa(bitisGun), true) {
                Secenekler.tarihSec(activity, activity.getString(R.string.bitis_tarih), bitisGun, baslangicGun) {
                    bitisGun = it
                    // Alt sayfa kapandı; tekrar sayfasını yeni değerle yeniden aç.
                    goster()
                }
                sayfa?.kapat()
            }
            kutu.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * d).toInt() })
        } else if (bitis == BITIS_SAYI) {
            kutu.addView(sayici(sayi, 1, 999, { sayi = it; ciz() }, activity.getString(R.string.kez_sonra)))
        }
    }

    /** "Her − n + birim": 48 dp'lik düğmelerle artırıp azaltan satır. */
    private fun sayici(deger: Int, enAz: Int, enCok: Int, degisti: (Int) -> Unit, sonEk: String): View {
        val sira = LinearLayout(activity)
        sira.orientation = LinearLayout.HORIZONTAL
        sira.gravity = Gravity.CENTER_VERTICAL
        fun dugme(etiket: String, ad: String, etkin: Boolean, yeni: Int): TextView {
            val t = Secenekler.cip(activity, etiket, false) { if (etkin) degisti(yeni) }
            t.contentDescription = ad
            t.alpha = if (etkin) 1f else 0.4f
            t.textSize = 20f
            t.minWidth = (48 * d).toInt()
            t.setPadding(0, 0, 0, 0)
            return t
        }
        sira.addView(dugme("−", activity.getString(R.string.azalt), deger > enAz, deger - 1))
        val n = TextView(activity)
        n.text = deger.toString()
        n.textSize = 22f
        n.setTypeface(null, Typeface.BOLD)
        n.gravity = Gravity.CENTER
        n.minWidth = (48 * d).toInt()
        n.setTextColor(ContextCompat.getColor(activity, TR.color.metin))
        sira.addView(n)
        sira.addView(dugme("+", activity.getString(R.string.artir), deger < enCok, deger + 1))
        val e = TextView(activity)
        e.text = sonEk
        e.textSize = 16f
        e.setTextColor(ContextCompat.getColor(activity, TR.color.metin))
        e.setPadding((12 * d).toInt(), 0, 0, 0)
        sira.addView(e)
        return sira
    }

    private companion object {
        const val BITIS_HIC = 0
        const val BITIS_TARIH = 1
        const val BITIS_SAYI = 2
    }
}
