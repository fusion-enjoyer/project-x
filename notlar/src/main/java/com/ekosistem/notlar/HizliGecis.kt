package com.ekosistem.notlar

import android.app.Activity
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Nota git (Obsidian'ın hızlı geçişi): alt sayfada arama kutusu ve not
 * listesi. Yazdıkça notlar adına göre süzülür (Türkçe karakterden bağımsız;
 * adın başıyla eşleşenler önce). Dokununca not aynı sekmede, basılı
 * tutunca yeni sekmede açılır.
 *
 * Liste geri dönüştürülen satırlarla (ListView): 1.000 notluk klasörde
 * her not için ayrı satır kurmak sayfanın açılışını geciktirirdi.
 */
class HizliGecis(
    private val activity: Activity,
    private val notlar: List<Not>,
    private val secildi: (adres: String, yeniSekmede: Boolean) -> Unit
) {
    private val adlar = notlar.map { Arama.sadelestir(it.baslik) }
    private var gorunen: List<Not> = notlar

    fun goster() {
        val y = activity.resources.displayMetrics.density
        val metinRengi = ContextCompat.getColor(activity, R.color.metin)
        val ikincil = ContextCompat.getColor(activity, R.color.metin_ikincil)
        val sayfa = AltSayfa(activity).baslik(activity.getString(R.string.hizli_gecis))

        val kok = LinearLayout(activity)
        kok.orientation = LinearLayout.VERTICAL

        val kutu = EditText(activity)
        kutu.hint = activity.getString(R.string.hizli_gecis_ipucu)
        kutu.setSingleLine()
        kutu.imeOptions = EditorInfo.IME_ACTION_GO
        kutu.textSize = 16f
        kutu.setTextColor(metinRengi)
        kutu.setHintTextColor(ikincil)
        kutu.setBackgroundResource(R.drawable.bg_girdi)
        kutu.setPadding((16 * y).toInt(), 0, (16 * y).toInt(), 0)
        kok.addView(kutu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * y).toInt()))

        val ipucu = TextView(activity)
        ipucu.text = activity.getString(R.string.hizli_gecis_alt)
        ipucu.textSize = 12f
        ipucu.setTextColor(ikincil)
        ipucu.setPadding((4 * y).toInt(), (6 * y).toInt(), 0, (6 * y).toInt())
        kok.addView(ipucu)

        val liste = ListView(activity)
        liste.divider = null
        liste.isVerticalScrollBarEnabled = true
        val adaptor = Adaptor(y, metinRengi, ikincil)
        liste.adapter = adaptor
        // Klavye açıkken sayfa klavyenin üstünde; liste ekranın üçte birinden uzun olmasın.
        val boy = (activity.resources.displayMetrics.heightPixels * 0.34f).toInt()
        val listeKabi = android.widget.FrameLayout(activity)
        listeKabi.addView(liste)
        val bos = TextView(activity)
        bos.text = activity.getString(R.string.eslesen_not_yok)
        bos.textSize = 15f
        bos.setTextColor(ikincil)
        bos.gravity = Gravity.CENTER
        bos.visibility = View.GONE
        listeKabi.addView(bos)
        liste.emptyView = bos
        kok.addView(listeKabi, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, boy))

        fun sec(not: Not, yeniSekmede: Boolean) {
            sayfa.kapat()
            secildi(not.uri.toString(), yeniSekmede)
        }
        liste.setOnItemClickListener { _, _, konum, _ -> gorunen.getOrNull(konum)?.let { sec(it, false) } }
        liste.setOnItemLongClickListener { _, _, konum, _ ->
            gorunen.getOrNull(konum)?.let { sec(it, true) }
            true
        }
        kutu.setOnEditorActionListener { _, _, _ ->
            gorunen.firstOrNull()?.let { sec(it, false) }
            true
        }
        kutu.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                gorunen = suz(Arama.ifade(s?.toString()))
                adaptor.notifyDataSetChanged()
                liste.setSelection(0)
            }
        })

        sayfa.icerik(kok, odak = kutu).goster()
    }

    /** Adın başıyla eşleşenler önce, sonra adın içinde geçenler (son düzenlenen sırasıyla). */
    private fun suz(ifade: String?): List<Not> {
        if (ifade == null) return notlar
        val basta = mutableListOf<Not>()
        val icinde = mutableListOf<Not>()
        for (i in notlar.indices) {
            val ad = adlar[i]
            when {
                ad.startsWith(ifade) -> basta.add(notlar[i])
                ad.contains(ifade) -> icinde.add(notlar[i])
            }
        }
        return basta + icinde
    }

    private inner class Adaptor(
        private val y: Float,
        private val metinRengi: Int,
        private val ikincil: Int
    ) : BaseAdapter() {
        override fun getCount(): Int = gorunen.size
        override fun getItem(konum: Int): Any = gorunen[konum]
        override fun getItemId(konum: Int): Long = konum.toLong()

        override fun getView(konum: Int, eski: View?, ust: ViewGroup?): View {
            val satir = (eski as? LinearLayout) ?: yeniSatir()
            val not = gorunen[konum]
            (satir.getChildAt(0) as TextView).text = not.baslik
            val klasor = satir.getChildAt(1) as TextView
            klasor.text = not.klasor.orEmpty()
            klasor.visibility = if (not.klasor.isNullOrEmpty()) View.GONE else View.VISIBLE
            return satir
        }

        private fun yeniSatir(): LinearLayout {
            val satir = LinearLayout(activity)
            satir.orientation = LinearLayout.VERTICAL
            satir.gravity = Gravity.CENTER_VERTICAL
            satir.minimumHeight = (52 * y).toInt()
            satir.setPadding((4 * y).toInt(), (8 * y).toInt(), (4 * y).toInt(), (8 * y).toInt())
            val baslik = TextView(activity)
            baslik.textSize = 16f
            baslik.setTextColor(metinRengi)
            baslik.setSingleLine()
            baslik.ellipsize = TextUtils.TruncateAt.END
            satir.addView(baslik)
            val klasor = TextView(activity)
            klasor.textSize = 12f
            klasor.setTextColor(ikincil)
            klasor.setSingleLine()
            satir.addView(klasor)
            return satir
        }
    }
}
