package com.ekosistem.notlar

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
        val yaziTipi: android.graphics.Typeface?,
        val altBaslik: String?,
        val kaldir: (() -> Unit)?,
        val tikla: () -> Unit
    )

    private val maddeler = mutableListOf<Madde>()
    private var basligi: String? = null
    private var mesaji: String? = null
    private var girdiIpucu: String? = null
    private var girdiBaslangic: String = ""
    private var girdiEylem: ((String) -> Unit)? = null
    private var girdiDugmesi: String? = null
    private var girdiParola = false
    private var ozelIcerik: View? = null
    private var aramaAcik = false
    private var kapanisEylemi: (() -> Unit)? = null

    /** Sayfanın üstüne metin alanı ve onay düğmesi ekler (klasör adı gibi). */
    fun girdi(
        ipucu: String,
        baslangic: String = "",
        dugmeMetni: String,
        /** Parola alanı: yazılan gizlenir, baştaki/sondaki boşluk korunur. */
        parola: Boolean = false,
        tamamlandi: (String) -> Unit
    ): AltSayfa {
        girdiParola = parola
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

    /**
     * Hazır satırlar yerine kendi görünümünü koymak için (renk seçici gibi).
     * [odak] verilirse sayfa açılınca ona odaklanılır, klavye açılır ve
     * sayfa klavyenin üstünde durur (hızlı geçişin arama kutusu).
     */
    fun icerik(gorunum: View, odak: EditText? = null): AltSayfa {
        ozelIcerik = gorunum
        icerikOdagi = odak
        return this
    }

    private var icerikOdagi: EditText? = null

    /**
     * Uzun menünün üstüne arama kutusu koyar: yazdıkça maddeler süzülür
     * (Türkçe karakterden bağımsız), klavyedeki "Git" ilk eşleşeni açar.
     * Kutu kendiliğinden odaklanmaz; menü her açıldığında klavye çıkmasın.
     */
    fun aranabilir(): AltSayfa {
        aramaAcik = true
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
        yaziTipi: android.graphics.Typeface? = null,
        /** Başlığın altında küçük gri satır (sekmede notun özeti gibi). */
        altBaslik: String? = null,
        /** Doluysa satırın sonunda "×": satır sayfa kapanmadan kalkar (sekme kapatma). */
        kaldir: (() -> Unit)? = null,
        tikla: () -> Unit
    ): AltSayfa {
        maddeler.add(Madde(ikon, baslik, secili, tehlikeli, yaziTipi, altBaslik, kaldir, tikla))
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

        val vurgu = Renkler.vurgu(activity)
        val metinRengi = ContextCompat.getColor(activity, R.color.metin)
        val tehlikeRengi = 0xFFE24B4A.toInt()

        val ipucu = girdiIpucu
        var girdiAlani: EditText? = null
        if (ipucu != null) {
            val alan = EditText(activity)
            alan.hint = ipucu
            alan.setText(girdiBaslangic)
            alan.setSelection(girdiBaslangic.length)
            alan.setSingleLine()
            if (girdiParola) {
                alan.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            alan.textSize = 16f
            alan.setTextColor(metinRengi)
            alan.setHintTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
            alan.setBackgroundResource(R.drawable.bg_girdi)
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
            dugme.setTextColor(Renkler.vurguUzeri(activity))
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
                val ham = girdiAlani?.text?.toString().orEmpty()
                val deger = if (girdiParola) ham else ham.trim()
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

        var aramaAlani: EditText? = null
        if (aramaAcik && girdiIpucu == null && maddeler.size >= ARAMA_ESIGI) {
            val alan = EditText(activity)
            alan.hint = activity.getString(R.string.menude_ara)
            alan.setSingleLine()
            alan.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            alan.textSize = 16f
            alan.setTextColor(metinRengi)
            alan.setHintTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
            alan.setBackgroundResource(R.drawable.bg_girdi)
            alan.setPadding((16 * y).toInt(), 0, (16 * y).toInt(), 0)
            val alanLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * y).toInt())
            alanLp.leftMargin = (24 * y).toInt()
            alanLp.rightMargin = (24 * y).toInt()
            alanLp.bottomMargin = (8 * y).toInt()
            kok.addView(alan, alanLp)
            aramaAlani = alan
            // Pencere açılınca odak kutuya gitmesin (klavye kapalıyken liste kısalırdı).
            kok.isFocusableInTouchMode = true
        }
        // Arama için her satır, süzülecek adının sadeleşmiş hâliyle tutulur.
        val satirlar = mutableListOf<Pair<View, String>>()

        // Uzun listeler ([[ önerisi gibi) ekranı taşırmasın: maddeler kaydırılır.
        val maddeKutusu = LinearLayout(activity)
        maddeKutusu.orientation = LinearLayout.VERTICAL
        for (madde in maddeler) {
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
            // Yazı tipi seçicide her ad kendi yazı tipiyle görünür.
            madde.yaziTipi?.let { tv.typeface = it }
            tv.setTextColor(renk)
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.leftMargin = (18 * y).toInt()
            val alt = madde.altBaslik
            if (alt.isNullOrBlank()) {
                satir.addView(tv, tvLp)
            } else {
                tv.setSingleLine()
                tv.ellipsize = android.text.TextUtils.TruncateAt.END
                val altTv = TextView(activity)
                altTv.text = alt
                altTv.textSize = 13f
                altTv.setSingleLine()
                altTv.ellipsize = android.text.TextUtils.TruncateAt.END
                altTv.setTextColor(ContextCompat.getColor(activity, R.color.metin_ikincil))
                val metinler = LinearLayout(activity)
                metinler.orientation = LinearLayout.VERTICAL
                metinler.addView(tv)
                metinler.addView(altTv)
                satir.addView(metinler, tvLp)
            }

            if (madde.secili) {
                val onay = ImageView(activity)
                onay.setImageResource(R.drawable.ic_onay_isaret)
                onay.imageTintList = ColorStateList.valueOf(vurgu)
                satir.addView(
                    onay,
                    LinearLayout.LayoutParams((20 * y).toInt(), (20 * y).toInt())
                )
            }

            madde.kaldir?.let { kaldir ->
                val kapat = android.widget.ImageButton(activity)
                kapat.setImageResource(R.drawable.ic_kapat)
                kapat.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.metin_ikincil))
                kapat.setBackgroundResource(secilebilirZemin())
                kapat.scaleType = ImageView.ScaleType.CENTER_INSIDE
                kapat.setPadding((10 * y).toInt(), (10 * y).toInt(), (10 * y).toInt(), (10 * y).toInt())
                kapat.contentDescription = activity.getString(R.string.kapat)
                kapat.setOnClickListener {
                    satir.visibility = View.GONE
                    kaldir()
                }
                val kapatLp = LinearLayout.LayoutParams((40 * y).toInt(), (40 * y).toInt())
                kapatLp.leftMargin = (8 * y).toInt()
                kapatLp.rightMargin = (-10 * y).toInt()
                satir.addView(kapat, kapatLp)
            }

            satir.setOnClickListener {
                dialog.dismiss()
                madde.tikla()
            }
            maddeKutusu.addView(
                satir,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ((if (madde.altBaslik.isNullOrBlank()) 56 else 64) * y).toInt()
                )
            )
            satirlar.add(satir to Arama.sadelestir(madde.baslik.trim()))
        }
        val toplam = (maddeler.sumOf { if (it.altBaslik.isNullOrBlank()) 56 else 64 } * y).toInt()
        val sinir = (activity.resources.displayMetrics.heightPixels * 0.6f).toInt()
        val alan = aramaAlani
        if (alan != null) {
            // Aramada liste hep kaydırılır; klavye açılınca kısalır ki kutu ekranda kalsın.
            val kaydirici = ScrollView(activity)
            kaydirici.addView(maddeKutusu)
            val tamBoy = minOf(toplam, sinir)
            val klavyeliBoy = minOf(tamBoy, (activity.resources.displayMetrics.heightPixels * 0.28f).toInt())
            kok.addView(kaydirici, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tamBoy))
            alan.setOnFocusChangeListener { _, odakta ->
                kaydirici.layoutParams = kaydirici.layoutParams.apply {
                    height = if (odakta) klavyeliBoy else tamBoy
                }
            }
            alan.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val ifade = Arama.ifade(s?.toString())
                    for ((gorunum, ad) in satirlar) {
                        gorunum.visibility =
                            if (ifade == null || ad.contains(ifade)) View.VISIBLE else View.GONE
                    }
                    kaydirici.scrollTo(0, 0)
                }
            })
            alan.setOnEditorActionListener { _, _, _ ->
                satirlar.firstOrNull { it.first.visibility == View.VISIBLE }?.first?.performClick()
                true
            }
        } else if (toplam > sinir) {
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
        aramaAlani?.let { _ ->
            if (girdiAlani != null) return@let
            // Klavye ancak kutuya dokununca açılır; açılınca sayfa üstünde kalsın.
            val pencere = dialog.window ?: return@let
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(pencere, false)
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sarmal) { v, kenarlar ->
                    val klavye = kenarlar.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                    val cubuk = kenarlar.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
                    v.setPadding(0, 0, 0, maxOf(klavye, cubuk))
                    kenarlar
                }
                sarmal.requestApplyInsets()
                pencere.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                )
            } else {
                pencere.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
            kok.requestFocus()
        }
        (girdiAlani ?: icerikOdagi)?.let { alan ->
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

    private companion object {
        /** Bundan kısa menüde arama kutusu yer kaplamaya değmez. */
        const val ARAMA_ESIGI = 8
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
