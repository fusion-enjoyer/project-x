package com.ekosistem.notlar

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sürüm geçmişi. Bir sürüme dokunmak onu doğrudan yüklemez: önce güncel notla
 * farkı gösterilir, geri dönüşü kullanıcı onaylar. (Obsidian'ın dosya kurtarma
 * eklentisiyle aynı akış.)
 */
class GecmisActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var listeKaydirici: ScrollView
    private lateinit var surumListesi: LinearLayout
    private lateinit var farkKap: View
    private lateinit var farkBaslik: TextView
    private lateinit var farkOzet: TextView
    private lateinit var farkMetin: TextView
    private lateinit var bosDurum: TextView

    private var notUri: Uri? = null
    private var guncelMetin = ""
    private var metinDevralindi = false
    private var seciliSurum: NotDeposu.Surum? = null

    /** Açık sürümün iki görünümü: değişiklikler ya da sürümün tamamı. */
    private var farkYazisi: CharSequence = ""
    private var surumMetni = ""

    private var yesil = 0
    private var kirmizi = 0
    private var ikincilRenk = 0

    private val tarihBicimi = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gecmis)
        depo = NotDeposu(this)

        listeKaydirici = findViewById(R.id.listeKaydirici)
        surumListesi = findViewById(R.id.surumListesi)
        farkKap = findViewById(R.id.farkKap)
        farkBaslik = findViewById(R.id.farkBaslik)
        farkOzet = findViewById(R.id.farkOzet)
        farkMetin = findViewById(R.id.farkMetin)
        bosDurum = findViewById(R.id.bosDurum)
        // Not, kendi yazı tipiyle okunur; kod gibi eş aralıklı yazıyla değil.
        farkMetin.typeface = YaziTipleri.yazi(this)
        findViewById<TextView>(R.id.secDegisiklik).setOnClickListener { gorunumuSec(false) }
        findViewById<TextView>(R.id.secTamMetin).setOnClickListener { gorunumuSec(true) }

        yesil = ContextCompat.getColor(this, R.color.fark_eklendi)
        kirmizi = ContextCompat.getColor(this, R.color.fark_silindi)
        ikincilRenk = ContextCompat.getColor(this, R.color.metin_ikincil)

        val vurgu = Renkler.vurgu(this)
        val geriYukle = findViewById<TextView>(R.id.btnGeriYukle)
        geriYukle.backgroundTintList = ColorStateList.valueOf(vurgu)
        geriYukle.setTextColor(Renkler.vurguUzeri(this))
        geriYukle.setOnClickListener { geriYuklemeyiOnayla() }

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { geriGit() }
        onBackPressedDispatcher.addCallback(this, geriTusu)

        notUri = intent.getStringExtra("uri")?.let(Uri::parse)
        gecerliMetin?.let {
            guncelMetin = it
            metinDevralindi = true
        }
        gecerliMetin = null
        surumleriYukle()
    }

    private val geriTusu = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = geriGit()
    }

    /** Fark ekranındayken geri tuşu listeye döner, ekrandan çıkmaz. */
    private fun geriGit() {
        if (farkKap.visibility == View.VISIBLE) {
            farkKap.visibility = View.GONE
            listeKaydirici.visibility = View.VISIBLE
            findViewById<TextView>(R.id.ekranBaslik).setText(R.string.gecmis)
            seciliSurum = null
            surumMetni = ""
            farkYazisi = ""
            return
        }
        finish()
    }

    // --- Sürüm listesi ---

    private fun surumleriYukle() {
        val adres = notUri ?: run { finish(); return }
        Thread {
            if (!metinDevralindi) guncelMetin = depo.oku(adres)
            val surumler = depo.gecmisiListele(adres)
            val guncelSatirlar = guncelMetin.lines()
            val satirlar = surumler.map { surum ->
                val fark = Fark.hesapla(guncelSatirlar, depo.oku(surum.uri).lines())
                surum to Fark.sayac(fark)
            }
            runOnUiThread { listeyiCiz(satirlar) }
        }.start()
    }

    private fun listeyiCiz(satirlar: List<Pair<NotDeposu.Surum, Pair<Int, Int>>>) {
        surumListesi.removeAllViews()
        if (satirlar.isEmpty()) {
            bosDurum.visibility = View.VISIBLE
            listeKaydirici.visibility = View.GONE
            return
        }
        bosDurum.visibility = View.GONE
        val y = resources.displayMetrics.density
        for ((surum, sayac) in satirlar) {
            val kart = LinearLayout(this)
            kart.orientation = LinearLayout.VERTICAL
            kart.setBackgroundResource(R.drawable.bg_kart)
            kart.setPadding((16 * y).toInt(), (14 * y).toInt(), (16 * y).toInt(), (14 * y).toInt())
            kart.isClickable = true
            kart.setOnClickListener { surumuAc(surum) }

            val tarih = TextView(this)
            tarih.text = tarihBicimi.format(Date(surum.zaman))
            tarih.textSize = 16f
            tarih.setTypeface(null, android.graphics.Typeface.BOLD)
            tarih.setTextColor(ContextCompat.getColor(this, R.color.metin))
            kart.addView(tarih)

            val ozet = TextView(this)
            ozet.text = sayacMetni(sayac.first, sayac.second)
            ozet.textSize = 13f
            ozet.setTextColor(ContextCompat.getColor(this, R.color.metin_ikincil))
            val ozetLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            ozetLp.topMargin = (4 * y).toInt()
            kart.addView(ozet, ozetLp)

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * y).toInt()
            surumListesi.addView(kart, lp)
        }
    }

    /** "+4 satır · −1 satır" — renkleri farkla aynı dili konuşur. */
    private fun sayacMetni(eklenen: Int, silinen: Int): CharSequence {
        if (eklenen == 0 && silinen == 0) return getString(R.string.gecmis_fark_yok)
        val yazi = SpannableStringBuilder()
        if (eklenen > 0) {
            val bas = yazi.length
            yazi.append(resources.getQuantityString(R.plurals.fark_eklenen, eklenen, eklenen))
            yazi.setSpan(ForegroundColorSpan(yesil), bas, yazi.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (silinen > 0) {
            if (yazi.isNotEmpty()) yazi.append("  ·  ")
            val bas = yazi.length
            yazi.append(resources.getQuantityString(R.plurals.fark_silinen, silinen, silinen))
            yazi.setSpan(ForegroundColorSpan(kirmizi), bas, yazi.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return yazi
    }

    // --- Fark görünümü ---

    private fun surumuAc(surum: NotDeposu.Surum) {
        Thread {
            val metin = depo.oku(surum.uri)
            val fark = Fark.hesapla(guncelMetin.lines(), metin.lines())
            val (eklenen, silinen) = Fark.sayac(fark)
            val yazi = farkiBicimle(fark)
            runOnUiThread {
                seciliSurum = surum
                farkYazisi = yazi
                surumMetni = metin
                farkBaslik.text = tarihBicimi.format(Date(surum.zaman))
                farkOzet.text = SpannableStringBuilder(sayacMetni(eklenen, silinen))
                    .append("\n")
                    .append(getString(R.string.gecmis_aciklama))
                gorunumuSec(false)
                listeKaydirici.visibility = View.GONE
                farkKap.visibility = View.VISIBLE
                findViewById<TextView>(R.id.ekranBaslik).setText(R.string.gecmis_karsilastir)
            }
        }.start()
    }

    /** Değişiklikler ya da sürümün kendisi; seçili olan vurgunun soluk tonunda. */
    private fun gorunumuSec(tamMetin: Boolean) {
        farkMetin.text = if (tamMetin) surumMetni.ifEmpty { " " } else farkYazisi
        val vurgu = Renkler.vurgu(this)
        val kart = ContextCompat.getColor(this, R.color.kart)
        for ((id, secili) in listOf(R.id.secDegisiklik to !tamMetin, R.id.secTamMetin to tamMetin)) {
            val cip = findViewById<TextView>(id)
            cip.backgroundTintList = ColorStateList.valueOf(
                if (secili) (vurgu and 0x00FFFFFF) or 0x26000000 else kart
            )
            cip.setTypeface(null, if (secili) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            cip.isSelected = secili
        }
    }

    /**
     * Değişmeyen uzun bloklar kısaltılır; ekranda asıl görülmesi gereken,
     * geri dönüşün neyi değiştireceği. Kod farkı gibi "+/−" işaretleri yok:
     * geri gelecek satır yeşil zeminde, silinecek satır kırmızı zeminde ve
     * üstü çizili durur.
     */
    private fun farkiBicimle(fark: List<Fark.Satir>): CharSequence {
        val yazi = SpannableStringBuilder()
        var i = 0
        while (i < fark.size) {
            if (fark[i].tur != Fark.AYNI) {
                satirEkle(yazi, fark[i])
                i++
                continue
            }
            var son = i
            while (son < fark.size && fark[son].tur == Fark.AYNI) son++
            val uzunluk = son - i
            if (uzunluk <= BAGLAM * 2 + 1) {
                for (k in i until son) satirEkle(yazi, fark[k])
            } else {
                for (k in i until i + BAGLAM) satirEkle(yazi, fark[k])
                val bas = yazi.length
                yazi.append("⋯  ")
                yazi.append(
                    resources.getQuantityString(
                        R.plurals.fark_degismeyen,
                        uzunluk - BAGLAM * 2,
                        uzunluk - BAGLAM * 2
                    )
                )
                yazi.append("\n")
                yazi.setSpan(ForegroundColorSpan(ikincilRenk), bas, yazi.length, EE)
                yazi.setSpan(RelativeSizeSpan(0.85f), bas, yazi.length, EE)
                yazi.setSpan(LeadingMarginSpan.Standard(icPay()), bas, yazi.length, EE)
                for (k in son - BAGLAM until son) satirEkle(yazi, fark[k])
            }
            i = son
        }
        if (yazi.isEmpty()) yazi.append(getString(R.string.gecmis_fark_yok))
        return yazi
    }

    private fun satirEkle(yazi: SpannableStringBuilder, satir: Fark.Satir) {
        val bas = yazi.length
        // Boş satırın da zemini görünsün diye en az bir boşluk yazılır.
        yazi.append(satir.metin.ifEmpty { " " }).append("\n")
        yazi.setSpan(LeadingMarginSpan.Standard(icPay()), bas, yazi.length, EE)
        when (satir.tur) {
            Fark.EKLENEN -> {
                yazi.setSpan(SatirZemini(soluklastir(yesil)), bas, yazi.length, EE)
            }
            Fark.SILINEN -> {
                yazi.setSpan(SatirZemini(soluklastir(kirmizi)), bas, yazi.length, EE)
                yazi.setSpan(ForegroundColorSpan(kirmizi), bas, yazi.length, EE)
                yazi.setSpan(StrikethroughSpan(), bas, yazi.length - 1, EE)
            }
            else -> yazi.setSpan(ForegroundColorSpan(ikincilRenk), bas, yazi.length, EE)
        }
    }

    private fun icPay(): Int = (10 * resources.displayMetrics.density).toInt()

    private fun soluklastir(renk: Int): Int = (renk and 0x00FFFFFF) or 0x24000000

    /** Satırın tüm genişliğini boyayan yumuşak zemin (yalnız harflerin arkasını değil). */
    private class SatirZemini(private val renk: Int) : LineBackgroundSpan {
        override fun drawBackground(
            c: Canvas, p: Paint, sol: Int, sag: Int, ust: Int, taban: Int, alt: Int,
            metin: CharSequence, bas: Int, son: Int, satirNo: Int
        ) {
            val onceki = p.color
            val stil = p.style
            p.color = renk
            p.style = Paint.Style.FILL
            c.drawRect(sol.toFloat(), ust.toFloat(), sag.toFloat(), alt.toFloat(), p)
            p.color = onceki
            p.style = stil
        }
    }

    private fun geriYuklemeyiOnayla() {
        val surum = seciliSurum ?: return
        setResult(RESULT_OK, Intent().putExtra("surum", surum.uri.toString()))
        finish()
    }

    companion object {
        /** Editördeki (belki henüz kaydedilmemiş) metin karşılaştırma için devredilir. */
        @Volatile
        var gecerliMetin: String? = null

        private const val BAGLAM = 3
        private const val EE = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    }
}
