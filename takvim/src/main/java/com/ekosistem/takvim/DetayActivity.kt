package com.ekosistem.takvim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.util.Linkify
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.ipucuVer
import java.util.TimeZone
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/** Etkinlik ayrıntısı: zaman, tekrar, konum, hatırlatıcılar, takvim, açıklama; düzenle, sil, paylaş. */
class DetayActivity : AppCompatActivity() {

    private val tz = TimeZone.getDefault()
    private val yurutucu = Executors.newSingleThreadExecutor()
    private var etkinlik: Etkinlik? = null
    private var ornekBas = 0L
    private var ornekBit = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detay)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<View>(R.id.btnDuzenle).setOnClickListener { duzenle() }
        findViewById<View>(R.id.btnSil).setOnClickListener { silSor() }
        findViewById<View>(R.id.btnDaha).setOnClickListener { dahaMenusu() }
        ipucuVer(findViewById(R.id.btnDuzenle), findViewById(R.id.btnSil), findViewById(R.id.btnDaha))
    }

    override fun onResume() {
        super.onResume()
        yukle()
    }

    private fun kimlik(): Long {
        val id = intent.getLongExtra(EK_ID, 0L)
        if (id > 0) return id
        return runCatching { ContentUris.parseId(intent.data!!) }.getOrDefault(0L)
    }

    private fun yukle() {
        if (!TakvimDeposu.izinVar(this)) {
            finish()
            return
        }
        val id = kimlik()
        yurutucu.execute {
            val e = TakvimDeposu.etkinlik(this, id)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (e == null) {
                    Toast.makeText(this, R.string.etkinlik_bulunamadi, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    etkinlik = e
                    ornekBas = intent.getLongExtra(EK_BAS, 0L).takeIf { it > 0 }
                        ?: intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L).takeIf { it > 0 } ?: e.baslangic
                    ornekBit = intent.getLongExtra(EK_BIT, 0L).takeIf { it > 0 }
                        ?: intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L).takeIf { it > 0 } ?: (ornekBas + (e.bitis - e.baslangic))
                    ciz(e)
                }
            }
        }
    }

    private fun ciz(e: Etkinlik) {
        val ornek = Ornek.olustur(e.id, e.takvimId, e.baslik, e.konum, ornekBas, ornekBit, e.tumGun, e.renk, e.kural != null, tz)
        findViewById<View>(R.id.detayRenk).backgroundTintList = ColorStateList.valueOf(Renk.yuzey(this, e.renk))
        findViewById<TextView>(R.id.detayBaslik).text = e.baslik.ifBlank { getString(R.string.basliksiz) }

        val zaman = findViewById<TextView>(R.id.detayZaman)
        val alt = findViewById<TextView>(R.id.detayAlt)
        if (ornek.tumGun) {
            if (ornek.cokGunlu) {
                zaman.text = Metinler.gunBaslik(ornek.ilkGun) + " –"
                alt.text = Metinler.gunBaslik(ornek.sonGun) + " · " + getString(R.string.tum_gun)
            } else {
                zaman.text = Metinler.gunBaslik(ornek.ilkGun)
                alt.text = getString(R.string.tum_gun)
            }
        } else if (ornek.cokGunlu) {
            zaman.text = Metinler.gunBaslik(ornek.ilkGun) + ", " + Metinler.saat(this, ornek.baslangicDk) + " –"
            alt.text = Metinler.gunBaslik(ornek.sonGun) + ", " + Metinler.saat(this, Gun.yerelDakika(ornekBit, tz))
        } else {
            zaman.text = Metinler.gunBaslik(ornek.ilkGun)
            val sure = ((ornekBit - ornekBas) / 60_000L).toInt()
            alt.text = Metinler.ornekSaati(this, ornek, ornek.ilkGun) + if (sure > 0) " · " + Metinler.sure(this, sure) else ""
        }

        val kutu = findViewById<LinearLayout>(R.id.detayKutu)
        kutu.removeAllViews()
        fun satir(ikon: Int, baslik: CharSequence, altYazi: String? = null, tikla: (() -> Unit)? = null, uzun: (() -> Unit)? = null): View {
            if (kutu.childCount > 0) kutu.addView(layoutInflater.inflate(R.layout.item_ayrac, kutu, false))
            val v = layoutInflater.inflate(R.layout.item_bilgi, kutu, false)
            v.findViewById<ImageView>(R.id.bilgiIkon).setImageResource(ikon)
            v.findViewById<ImageView>(R.id.bilgiIkon).imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, TR.color.metin_ikincil))
            v.findViewById<TextView>(R.id.bilgiBaslik).text = baslik
            altYazi?.let { v.findViewById<TextView>(R.id.bilgiAlt).apply { text = it; visibility = View.VISIBLE } }
            if (tikla != null) v.setOnClickListener { tikla() } else v.isClickable = false
            if (uzun != null) v.setOnLongClickListener { uzun(); true }
            kutu.addView(v)
            return v
        }

        if (e.kural != null) {
            satir(R.drawable.ic_tekrar, Metinler.tekrar(this, Tekrar.coz(e.kural, ornek.ilkGun, tz), e.kural, ornek.ilkGun))
        }
        if (e.konum.isNotBlank()) {
            satir(R.drawable.ic_konum, e.konum, tikla = { haritadaAc(e.konum) }, uzun = { panoyaKopyala(e.konum) })
        }
        if (e.davetliler.size > 1 || (e.davetliler.isNotEmpty() && e.benimDurumum != 0)) {
            val satirlar = e.davetliler.take(8).joinToString("\n") { d ->
                (d.ad.ifBlank { d.eposta }) + " · " + davetDurumu(d.durum) + (if (d.eposta.equals(e.organizator, true)) " · " + getString(R.string.organizator) else "")
            } + if (e.davetliler.size > 8) "\n" + getString(R.string.ozet_daha, e.davetliler.size - 8) else ""
            satir(R.drawable.ic_kisi, getString(R.string.davetliler_n, e.davetliler.size), satirlar)
        }
        if (e.hatirlaticilar.isNotEmpty()) {
            satir(R.drawable.ic_zil, e.hatirlaticilar.joinToString("\n") { Metinler.hatirlatici(this, it, e.tumGun) })
        }
        satir(R.drawable.ic_takvim, e.takvimAdi.ifBlank { getString(R.string.takvim) })
        if (e.aciklama.isNotBlank()) {
            val v = satir(R.drawable.ic_not, e.aciklama)
            v.findViewById<TextView>(R.id.bilgiBaslik).apply {
                Linkify.addLinks(this, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
                setLinkTextColor(com.ekosistem.tasarim.Tasarim.vurgu(this@DetayActivity))
            }
        }

        // Toplantı bağlantısı: konum ya da açıklamada Meet/Zoom/Teams… varsa tek dokunuşla katıl.
        val katil = findViewById<TextView>(R.id.detayKatil)
        val baglanti = Baglanti.bul(e.konum, e.aciklama)
        if (baglanti != null) {
            katil.text = getString(R.string.katil, Baglanti.hizmet(baglanti)).trim()
            katil.backgroundTintList = ColorStateList.valueOf(com.ekosistem.tasarim.Tasarim.vurgu(this))
            katil.setTextColor(com.ekosistem.tasarim.Tasarim.vurguUzeri(this))
            katil.visibility = View.VISIBLE
            katil.setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(baglanti))) } }
        } else {
            katil.visibility = View.GONE
        }
        yanitiYaz(e)

        val yazilabilir = e.yazilabilir
        findViewById<View>(R.id.btnDuzenle).visibility = if (yazilabilir) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnSil).visibility = if (yazilabilir) View.VISIBLE else View.GONE
    }

    private fun davetDurumu(durum: Int) = getString(
        when (durum) {
            Davetli.KABUL -> R.string.durum_kabul
            Davetli.RED -> R.string.durum_red
            Davetli.BELKI -> R.string.durum_belki
            else -> R.string.durum_bekliyor
        }
    )

    /** Davetliyse ve organizatör değilsen "Katılacak mısın?" Evet / Belki / Hayır. */
    private fun yanitiYaz(e: Etkinlik) {
        val kutu = findViewById<View>(R.id.detayYanit)
        val benDavetli = e.sahipHesap.isNotEmpty() && e.davetliler.any { it.eposta.equals(e.sahipHesap, true) }
        val organizatorBen = e.organizator.equals(e.sahipHesap, true)
        if (!benDavetli || organizatorBen || !e.yazilabilir) {
            kutu.visibility = View.GONE
            return
        }
        kutu.visibility = View.VISIBLE
        val sira = findViewById<LinearLayout>(R.id.detayYanitCipleri)
        sira.removeAllViews()
        val d = resources.displayMetrics.density
        val simdiki = e.davetliler.first { it.eposta.equals(e.sahipHesap, true) }.durum
        for ((durum, ad) in listOf(Davetli.KABUL to R.string.yanit_evet, Davetli.BELKI to R.string.yanit_belki, Davetli.RED to R.string.yanit_hayir)) {
            val cip = Secenekler.cip(this, getString(ad), simdiki == durum, beyazSayfada = true) {
                yurutucu.execute {
                    val tamam = TakvimDeposu.yanitla(this, e, durum)
                    runOnUiThread { if (tamam) yukle() else Toast.makeText(this, R.string.kaydedilemedi, Toast.LENGTH_LONG).show() }
                }
            }
            sira.addView(cip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (8 * d).toInt() })
        }
    }

    private fun haritadaAc(konum: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(konum))))
        }.onFailure { panoyaKopyala(konum) }
    }

    private fun panoyaKopyala(metin: String) {
        val pano = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        pano.setPrimaryClip(ClipData.newPlainText(getString(R.string.konum_ipucu), metin))
        Toast.makeText(this, R.string.kopyalandi, Toast.LENGTH_SHORT).show()
    }

    private fun dahaMenusu() {
        val e = etkinlik ?: return
        val sayfa = AltSayfa(this).baslik(e.baslik.ifBlank { getString(R.string.basliksiz) })
        sayfa.madde(R.drawable.ic_paylas, getString(R.string.paylas)) { paylas() }
        sayfa.madde(R.drawable.ic_takvim, getString(R.string.ics_paylas)) { icsPaylas() }
        if (TakvimDeposu.izinVar(this)) sayfa.madde(R.drawable.ic_cogalt, getString(R.string.cogalt)) { cogalt() }
        sayfa.goster()
    }

    /** Etkinliği `.ics` dosyası olarak paylaşır (başka takvim uygulaması açıp ekleyebilir). */
    private fun icsPaylas() {
        val e = etkinlik ?: return
        yurutucu.execute {
            val klasor = java.io.File(cacheDir, "ics").apply { mkdirs() }
            klasor.listFiles()?.forEach { it.delete() }   // eski paylaşım dosyaları birikmesin
            val ad = e.baslik.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(40).ifEmpty { "etkinlik" }
            val dosya = java.io.File(klasor, "$ad.ics")
            val metin = Ics.yaz(listOf(IcsDeposu.tek(this, e)), System.currentTimeMillis())
            dosya.writeText(metin, Charsets.UTF_8)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.dosya", dosya)
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null
                    )
                )
            }
        }
    }

    /** Aynı bilgilerle yeni bir etkinlik açar (kaydedilene kadar hiçbir şey değişmez). */
    private fun cogalt() {
        val e = etkinlik ?: return
        startActivityForResult(
            Intent(this, DuzenleActivity::class.java)
                .putExtra(DuzenleActivity.EK_ID, e.id).putExtra(DuzenleActivity.EK_BAS, ornekBas).putExtra(DuzenleActivity.EK_BIT, ornekBit)
                .putExtra(DuzenleActivity.EK_COGALT, true),
            ISTEK_DUZENLE
        )
    }

    private fun paylas() {
        val e = etkinlik ?: return
        val ornek = Ornek.olustur(e.id, e.takvimId, e.baslik, e.konum, ornekBas, ornekBit, e.tumGun, e.renk, e.kural != null, tz)
        val metin = StringBuilder(e.baslik.ifBlank { getString(R.string.basliksiz) })
        metin.append('\n').append(Metinler.gunBaslik(ornek.ilkGun)).append(", ").append(Metinler.ornekSaati(this, ornek, ornek.ilkGun))
        if (e.konum.isNotBlank()) metin.append('\n').append(e.konum)
        if (e.aciklama.isNotBlank()) metin.append("\n\n").append(e.aciklama)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, metin.toString()), null
            )
        )
    }

    private fun duzenle() {
        val e = etkinlik ?: return
        startActivityForResult(
            Intent(this, DuzenleActivity::class.java)
                .putExtra(DuzenleActivity.EK_ID, e.id).putExtra(DuzenleActivity.EK_BAS, ornekBas).putExtra(DuzenleActivity.EK_BIT, ornekBit),
            ISTEK_DUZENLE
        )
    }

    @Deprecated("startActivityForResult ile birlikte")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Kaydedilince liste yenilenmiş olur; "bu ve sonrakiler" yeni etkinlik doğurabileceği için listeye dön.
        if (requestCode == ISTEK_DUZENLE && resultCode == RESULT_OK) finish()
    }

    private fun silSor() {
        val e = etkinlik ?: return
        val sayfa = AltSayfa(this)
        if (e.kural != null && e.asilId == 0L) {
            sayfa.baslik(getString(R.string.sil_baslik))
                .madde(R.drawable.ic_gun, getString(R.string.kapsam_bu), tehlikeli = true) { sil(Kapsam.BU) }
                .madde(R.drawable.ic_gundem, getString(R.string.kapsam_sonrakiler), tehlikeli = true) { sil(Kapsam.BUNDAN_SONRA) }
                .madde(R.drawable.ic_tekrar, getString(R.string.kapsam_hepsi), tehlikeli = true) { sil(Kapsam.HEPSI) }
        } else {
            sayfa.mesaj(getString(R.string.sil_soru))
                .madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) { sil(Kapsam.HEPSI) }
                .madde(TR.drawable.ic_kapat, getString(R.string.vazgec)) {}
        }
        sayfa.goster()
    }

    private fun sil(kapsam: Kapsam) {
        val e = etkinlik ?: return
        yurutucu.execute {
            val kayit = TakvimDeposu.sil(this, e, ornekBas, kapsam)
            runOnUiThread {
                if (kayit != null) {
                    HatirlaticiAlici.uyariyiKapat(this, e.id, ornekBas)
                    Bildirimler.kaldir(this, e.id, ornekBas)
                    GeriAlDeposu.birak(kayit)
                    finish()
                } else {
                    Toast.makeText(this, R.string.silinemedi, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EK_ID = "id"
        const val EK_BAS = "bas"
        const val EK_BIT = "bit"
        private const val ISTEK_DUZENLE = 1
    }
}
