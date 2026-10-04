package com.ekosistem.tasarim

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Ekranın altından açılan menü. Açılır menüler ekranın üstünde kalıyordu;
 * alt sayfa başparmakla rahat erişilir (Obsidian'ın da yaptığı gibi).
 */
class AltSayfa(private val activity: Activity) {

    private data class Madde(
        val ikon: Int,
        val baslik: String,
        val secili: Boolean,
        val tehlikeli: Boolean,
        val tikla: () -> Unit,
        val bolum: Boolean = false
    )

    private val maddeler = mutableListOf<Madde>()
    private var basligi: String? = null
    private var mesaji: String? = null
    private var girdiIpucu: String? = null
    private var girdiBaslangic: String = ""
    private var girdiEylem: ((String) -> Unit)? = null
    private var girdiDugmesi: String? = null
    private var ozelIcerik: View? = null
    private var kapanisEylemi: (() -> Unit)? = null

    /** Sayfanın üstüne metin alanı ve onay düğmesi ekler (klasör adı gibi). */
    fun girdi(
        ipucu: String,
        baslangic: String = "",
        dugmeMetni: String,
        tamamlandi: (String) -> Unit
    ): AltSayfa {
        girdiIpucu = ipucu
        girdiBaslangic = baslangic
        girdiDugmesi = dugmeMetni
        girdiEylem = tamamlandi
        return this
    }

    /** Menünün üstündeki küçük gri etiket (not adı, "Sıralama" gibi). */
    fun baslik(metin: String): AltSayfa {
        basligi = metin
        return this
    }

    /**
     * Sayfanın asıl söylediği cümle: sonuç, soru ya da açıklama. Başlık
     * gibi küçük ve gri değil, ana metin boyunda okunur.
     */
    fun mesaj(metin: String): AltSayfa {
        mesaji = metin
        return this
    }

    /** Hazır satırlar yerine kendi görünümünü koymak için (renk seçici gibi). */
    fun icerik(gorunum: View): AltSayfa {
        ozelIcerik = gorunum
        return this
    }

    /** Sayfa kapandığında çağrılır. */
    fun kapaninca(eylem: () -> Unit): AltSayfa {
        kapanisEylemi = eylem
        return this
    }

    fun madde(
        ikon: Int,
        baslik: String,
        secili: Boolean = false,
        tehlikeli: Boolean = false,
        tikla: () -> Unit
    ): AltSayfa {
        maddeler.add(Madde(ikon, baslik, secili, tehlikeli, tikla))
        return this
    }

    /** Maddeleri gruplayan küçük gri ara başlık ("Saat aralığı" gibi). */
    fun bolum(metin: String): AltSayfa {
        maddeler.add(Madde(0, metin, false, false, {}, bolum = true))
        return this
    }

    private var acikDialog: Dialog? = null

    /** Açık sayfayı programdan kapatır (renk seçiminde olduğu gibi). */
    fun kapat() {
        acikDialog?.dismiss()
        acikDialog = null
    }

    fun goster() {
        val y = activity.resources.displayMetrics.density
        val dialog = Dialog(activity, R.style.AltSayfaTemasi)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val kok = LinearLayout(activity)
        kok.orientation = LinearLayout.VERTICAL
        kok.setBackgroundResource(R.drawable.bg_alt_sayfa)
        kok.setPadding(0, (10 * y).toInt(), 0, (16 * y).toInt())

        // Tutamak
        val tutamak = View(activity)
        tutamak.setBackgroundResource(R.drawable.bg_tutamak)
        val tutamakLp = LinearLayout.LayoutParams((36 * y).toInt(), (4 * y).toInt())
        tutamakLp.gravity = Gravity.CENTER_HORIZONTAL
        tutamakLp.bottomMargin = (10 * y).toInt()
        kok.addView(tutamak, tutamakLp)

        basligi?.let { metin ->
            val tv = TextView(activity)
            tv.text = metin
            tv.textSize = 13f
            tv.setTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
            tv.setPadding((24 * y).toInt(), (4 * y).toInt(), (24 * y).toInt(), (8 * y).toInt())
            kok.addView(tv)
        }

        mesaji?.let { metin ->
            val tv = TextView(activity)
            tv.text = metin
            tv.textSize = 16f
            tv.setLineSpacing(4 * y, 1f)
            tv.setTextColor(ContextCompat.getColor(activity, R.color.metin))
            tv.setPadding((24 * y).toInt(), (4 * y).toInt(), (24 * y).toInt(), (14 * y).toInt())
            kok.addView(tv)
        }

        val vurgu = Tasarim.vurgu(activity)
        val metinRengi = ContextCompat.getColor(activity, R.color.metin)
        val tehlikeRengi = ContextCompat.getColor(activity, R.color.tehlike)

        val ipucu = girdiIpucu
        var girdiAlani: EditText? = null
        if (ipucu != null) {
            val alan = EditText(activity)
            alan.hint = ipucu
            alan.setText(girdiBaslangic)
            alan.setSelection(girdiBaslangic.length)
            alan.setSingleLine()
            alan.textSize = 16f
            alan.setTextColor(metinRengi)
            alan.setHintTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
            alan.setBackgroundResource(R.drawable.bg_arama_pill)
            alan.setPadding((16 * y).toInt(), 0, (16 * y).toInt(), 0)
            val alanLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * y).toInt()
            )
            alanLp.leftMargin = (24 * y).toInt()
            alanLp.rightMargin = (24 * y).toInt()
            alanLp.bottomMargin = (12 * y).toInt()
            kok.addView(alan, alanLp)
            girdiAlani = alan

            val dugme = TextView(activity)
            dugme.text = girdiDugmesi
            dugme.textSize = 16f
            dugme.setTypeface(null, android.graphics.Typeface.BOLD)
            dugme.gravity = Gravity.CENTER
            dugme.setTextColor(Tasarim.vurguUzeri(activity))
            dugme.setBackgroundResource(R.drawable.bg_pill_buton)
            dugme.backgroundTintList = ColorStateList.valueOf(vurgu)
            val dugmeLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * y).toInt()
            )
            dugmeLp.leftMargin = (24 * y).toInt()
            dugmeLp.rightMargin = (24 * y).toInt()
            dugmeLp.bottomMargin = (4 * y).toInt()
            dugme.setOnClickListener {
                val deger = girdiAlani?.text?.toString()?.trim().orEmpty()
                dialog.dismiss()
                if (deger.isNotEmpty()) girdiEylem?.invoke(deger)
            }
            kok.addView(dugme, dugmeLp)
        }

        ozelIcerik?.let { gorunum ->
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (24 * y).toInt()
            lp.rightMargin = (24 * y).toInt()
            lp.bottomMargin = (8 * y).toInt()
            kok.addView(gorunum, lp)
        }

        // Uzun listeler ([[ önerisi gibi) ekranı taşırmasın: maddeler kaydırılır.
        val maddeKutusu = LinearLayout(activity)
        maddeKutusu.orientation = LinearLayout.VERTICAL
        for (madde in maddeler) {
            if (madde.bolum) {
                val tv = TextView(activity)
                tv.text = madde.baslik
                tv.textSize = 13f
                tv.setTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
                tv.setPadding((24 * y).toInt(), (14 * y).toInt(), (24 * y).toInt(), (4 * y).toInt())
                maddeKutusu.addView(tv)
                continue
            }
            val satir = LinearLayout(activity)
            satir.orientation = LinearLayout.HORIZONTAL
            satir.gravity = Gravity.CENTER_VERTICAL
            satir.setPadding((24 * y).toInt(), 0, (24 * y).toInt(), 0)
            satir.isClickable = true
            satir.setBackgroundResource(secilebilirZemin())

            val renk = when {
                madde.tehlikeli -> tehlikeRengi
                madde.secili -> vurgu
                else -> metinRengi
            }

            val ikon = ImageView(activity)
            ikon.setImageResource(madde.ikon)
            ikon.imageTintList = ColorStateList.valueOf(renk)
            satir.addView(ikon, LinearLayout.LayoutParams((24 * y).toInt(), (24 * y).toInt()))

            val tv = TextView(activity)
            tv.text = madde.baslik
            tv.textSize = 16f
            tv.setTextColor(renk)
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.leftMargin = (18 * y).toInt()
            satir.addView(tv, tvLp)

            if (madde.secili) {
                val onay = ImageView(activity)
                onay.setImageResource(R.drawable.ic_onay_isaret)
                onay.imageTintList = ColorStateList.valueOf(vurgu)
                satir.addView(
                    onay,
                    LinearLayout.LayoutParams((20 * y).toInt(), (20 * y).toInt())
                )
            }

            satir.setOnClickListener {
                dialog.dismiss()
                madde.tikla()
            }
            maddeKutusu.addView(
                satir,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (56 * y).toInt()
                )
            )
        }
        val toplam = maddeler.sumOf { if (it.bolum) 40 else 56 }.let { (it * y).toInt() }
        val sinir = (activity.resources.displayMetrics.heightPixels * 0.6f).toInt()
        if (toplam > sinir) {
            val kaydirici = ScrollView(activity)
            kaydirici.isVerticalScrollBarEnabled = true
            kaydirici.addView(maddeKutusu)
            kok.addView(kaydirici, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sinir))
        } else if (maddeler.isNotEmpty()) {
            kok.addView(maddeKutusu)
        }

        // Alt sayfayı ekranın altına yasla, üstündeki boşluğa dokununca kapansın.
        val sarmal = LinearLayout(activity)
        sarmal.orientation = LinearLayout.VERTICAL
        sarmal.gravity = Gravity.BOTTOM
        sarmal.setOnClickListener { dialog.dismiss() }
        kok.isClickable = true
        sarmal.addView(
            kok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(sarmal)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.BOTTOM)
        }
        acikDialog = dialog
        dialog.setOnDismissListener {
            acikDialog = null
            kapanisEylemi?.invoke()
        }
        dialog.show()
        girdiAlani?.let { alan ->
            alan.requestFocus()
            val pencere = dialog.window ?: return@let
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // Android 15 ve sonrası kenardan kenara çalışır: ADJUST_RESIZE artık
                // sayfayı klavyenin üstüne almıyor, metin kutusu klavyenin altında
                // kalıyordu. Klavye yüksekliğini kendimiz alt boşluk yaparız.
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(pencere, false)
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sarmal) { v, kenarlar ->
                    val klavye = kenarlar.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                    val cubuk = kenarlar.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
                    v.setPadding(0, 0, 0, maxOf(klavye, cubuk))
                    kenarlar
                }
                sarmal.requestApplyInsets()
                pencere.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                )
            } else {
                // Eski sürümlerde ADJUST_RESIZE olmadan sayfa klavyenin altında kalıyordu.
                pencere.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
        }
    }

    private fun secilebilirZemin(): Int {
        val deger = android.util.TypedValue()
        activity.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            deger,
            true
        )
        return deger.resourceId
    }
}
