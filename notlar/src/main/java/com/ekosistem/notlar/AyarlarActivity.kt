package com.ekosistem.notlar

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class AyarlarActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var satirTema: View
    private lateinit var satirRenk: View
    private lateinit var satirKlasor: View
    private lateinit var satirSiralama: View
    private lateinit var satirKilit: View
    private var renkSayfasi: AltSayfa? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ayarlar)
        depo = NotDeposu(this)

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }

        val vurgu = Renkler.vurgu(this)
        satirTema = findViewById(R.id.satirTema)
        satirRenk = findViewById(R.id.satirRenk)
        satirKlasor = findViewById(R.id.satirKlasor)
        satirSiralama = findViewById(R.id.satirSiralama)

        satirKur(satirTema, R.drawable.ic_ayar_gorunum, vurgu, getString(R.string.tema), temaAdi())
        satirKur(satirRenk, R.drawable.ic_ayar_gorunum, vurgu, getString(R.string.vurgu_rengi), renkAdi())
        satirKur(
            satirKlasor,
            R.drawable.ic_ayar_klasor,
            NOTR,
            getString(R.string.klasor_sec),
            klasorOzeti()
        )
        satirKur(
            satirSiralama,
            R.drawable.ic_ayar_sirala,
            NOTR,
            getString(R.string.siralama),
            siralamaAdi()
        )
        satirKur(
            findViewById(R.id.satirDisaAktar),
            R.drawable.ic_ayar_yedek,
            YESIL,
            getString(R.string.disa_aktar),
            null
        )
        satirKur(
            findViewById(R.id.satirIceAktar),
            R.drawable.ic_ayar_yedek,
            YESIL,
            getString(R.string.ice_aktar),
            null
        )
        satirKilit = findViewById(R.id.satirKilit)
        satirKur(
            satirKilit,
            R.drawable.ic_ayar_kilit,
            KIRMIZI,
            getString(R.string.uygulama_kilidi),
            kilitOzeti()
        )
        satirKur(
            findViewById(R.id.satirEkranGizle),
            R.drawable.ic_ayar_kilit,
            KIRMIZI,
            getString(R.string.ekran_gizle),
            if (Prefs.ekranGizle(this)) getString(R.string.acik) else getString(R.string.kapali)
        )

        val surum = findViewById<View>(R.id.satirSurum)
        satirKur(
            surum,
            R.drawable.ic_ayar_bilgi,
            NOTR,
            getString(R.string.surum),
            "${BuildConfig.VERSION_NAME} · ${getString(R.string.izin_yok_rozet)}"
        )
        surum.findViewById<ImageView>(R.id.ayarChevron).visibility = View.INVISIBLE
        surum.isClickable = false

        satirTema.setOnClickListener { temaSec() }
        satirRenk.setOnClickListener { renkSec() }
        satirKlasor.setOnClickListener { klasorSec() }
        satirSiralama.setOnClickListener { siralamaSec() }
        satirKilit.setOnClickListener { kilitAyari() }
        findViewById<View>(R.id.satirEkranGizle).setOnClickListener {
            Prefs.ekranGizleKaydet(this, !Prefs.ekranGizle(this))
            recreate()
        }
        findViewById<View>(R.id.satirDisaAktar).setOnClickListener { disaAktarmayiBaslat() }
        findViewById<View>(R.id.satirIceAktar).setOnClickListener { iceAktarmayiBaslat() }
    }

    private fun satirKur(satir: View, ikon: Int, rozetRengi: Int, baslik: String, ozet: String?) {
        val ikonGorunum = satir.findViewById<ImageView>(R.id.ayarIkon)
        ikonGorunum.setImageResource(ikon)
        ikonGorunum.backgroundTintList = ColorStateList.valueOf(rozetRengi)
        satir.findViewById<TextView>(R.id.ayarBaslik).text = baslik
        val ozetGorunum = satir.findViewById<TextView>(R.id.ayarOzet)
        if (ozet.isNullOrBlank()) {
            ozetGorunum.visibility = View.GONE
        } else {
            ozetGorunum.visibility = View.VISIBLE
            ozetGorunum.text = ozet
        }
    }

    private fun ozetGuncelle(satir: View, ozet: String) {
        val ozetGorunum = satir.findViewById<TextView>(R.id.ayarOzet)
        ozetGorunum.visibility = View.VISIBLE
        ozetGorunum.text = ozet
    }

    // --- Görünüm ---

    private fun temaAdi(): String = when (Prefs.tema(this)) {
        1 -> getString(R.string.tema_acik)
        2 -> getString(R.string.tema_siyah)
        else -> getString(R.string.tema_sistem)
    }

    private fun renkAdi(): String {
        val indeks = Prefs.vurguIndeksi(this).coerceIn(0, Renkler.SECENEKLER.size - 1)
        return getString(Renkler.SECENEKLER[indeks].adKaynagi)
    }

    private fun siralamaAdi(): String = when (Prefs.siralama(this)) {
        1 -> getString(R.string.siralama_eski)
        2 -> getString(R.string.siralama_ad_az)
        3 -> getString(R.string.siralama_ad_za)
        else -> getString(R.string.siralama_yeni)
    }

    private fun klasorOzeti(): String {
        val uriStr = Prefs.klasorUri(this) ?: return getString(R.string.klasor_uygulama)
        return try {
            val doc = DocumentFile.fromTreeUri(this, Uri.parse(uriStr))
            doc?.name ?: Uri.parse(uriStr).lastPathSegment ?: getString(R.string.klasor_uygulama)
        } catch (_: Exception) {
            getString(R.string.klasor_uygulama)
        }
    }

    private fun kilitOzeti(): String {
        if (!Kilit.kurulu(this)) return getString(R.string.kilit_kapali)
        val parcalar = mutableListOf(getString(R.string.kilit_acik), gecikmeAdi())
        if (Kilit.parmakIziAcik(this)) parcalar.add(getString(R.string.parmak_izi))
        return parcalar.joinToString(" · ")
    }

    private fun gecikmeAdi(): String = getString(
        when (Prefs.kilitGecikmesi(this)) {
            1 -> R.string.kilit_30sn
            2 -> R.string.kilit_1dk
            3 -> R.string.kilit_5dk
            else -> R.string.kilit_hemen
        }
    )

    /** Kilit kuruluysa seçenekler açılır; değilse doğrudan PIN kurulumuna gider. */
    private fun kilitAyari() {
        if (!Kilit.kurulu(this)) {
            kilitEkraniAc(KilitActivity.KIP_KUR)
            return
        }
        val sayfa = AltSayfa(this).baslik(getString(R.string.uygulama_kilidi))
        if (Kilit.parmakIziDonanimi(this)) {
            val acik = Prefs.parmakIzi(this)
            sayfa.madde(R.drawable.ic_kilit, getString(R.string.parmak_izi), secili = acik) {
                Prefs.parmakIziKaydet(this, !acik)
                ozetGuncelle(satirKilit, kilitOzeti())
            }
        }
        sayfa.madde(
            R.drawable.ic_gecmis,
            "${getString(R.string.otomatik_kilit)}: ${gecikmeAdi()}"
        ) { gecikmeSec() }
        sayfa.madde(R.drawable.ic_sil, getString(R.string.pin_kaldir), tehlikeli = true) {
            kilitEkraniAc(KilitActivity.KIP_KALDIR)
        }
        sayfa.goster()
    }

    private fun gecikmeSec() {
        val etiketler = listOf(
            R.string.kilit_hemen,
            R.string.kilit_30sn,
            R.string.kilit_1dk,
            R.string.kilit_5dk
        )
        val secili = Prefs.kilitGecikmesi(this)
        val sayfa = AltSayfa(this).baslik(getString(R.string.otomatik_kilit))
        etiketler.forEachIndexed { indeks, etiket ->
            sayfa.madde(R.drawable.ic_gecmis, getString(etiket), secili = indeks == secili) {
                Prefs.kilitGecikmesiKaydet(this, indeks)
                ozetGuncelle(satirKilit, kilitOzeti())
            }
        }
        sayfa.goster()
    }

    private fun kilitEkraniAc(kip: Int) {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, KilitActivity::class.java).putExtra("kip", kip),
            ISTEK_KILIT
        )
    }

    private fun temaSec() {
        val etiketler = listOf(R.string.tema_sistem, R.string.tema_acik, R.string.tema_siyah)
        val secili = Prefs.tema(this)
        val sayfa = AltSayfa(this).baslik(getString(R.string.tema))
        etiketler.forEachIndexed { indeks, etiket ->
            sayfa.madde(
                R.drawable.ic_ayar_gorunum_koyu,
                getString(etiket),
                secili = indeks == secili
            ) {
                Prefs.temaKaydet(this, indeks)
                Tema.uygula(indeks)
                recreate()
            }
        }
        sayfa.goster()
    }

    /** Renk seçenekleri yuvarlak örneklerle gösterilir. */
    private fun renkSec() {
        val yogunluk = resources.displayMetrics.density
        val kutu = LinearLayout(this)
        kutu.orientation = LinearLayout.VERTICAL
        kutu.setPadding(0, (8 * yogunluk).toInt(), 0, 0)

        val sayfa = AltSayfa(this).baslik(getString(R.string.vurgu_rengi)).icerik(kutu)
        val seciliIndeks = Prefs.vurguIndeksi(this)
        val gece = Renkler.geceMi(this)

        var satir: LinearLayout? = null
        Renkler.SECENEKLER.forEachIndexed { indeks, secenek ->
            if (indeks % 5 == 0) {
                satir = LinearLayout(this)
                satir?.orientation = LinearLayout.HORIZONTAL
                kutu.addView(
                    satir,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (12 * yogunluk).toInt() }
                )
            }
            val renk = if (gece) secenek.koyu else secenek.acik
            val ornek = View(this)
            val sekil = GradientDrawable()
            sekil.shape = GradientDrawable.OVAL
            sekil.setColor(renk)
            if (indeks == seciliIndeks) {
                sekil.setStroke((2.5f * yogunluk).toInt(), Color.parseColor(if (gece) "#F2EFE9" else "#171614"))
            }
            ornek.background = sekil
            ornek.contentDescription = getString(secenek.adKaynagi)
            val boyut = (44 * yogunluk).toInt()
            val lp = LinearLayout.LayoutParams(boyut, boyut)
            lp.rightMargin = (10 * yogunluk).toInt()
            ornek.setOnClickListener {
                Prefs.vurguKaydet(this, indeks)
                renkSayfasi?.kapat()
                recreate()
            }
            satir?.addView(ornek, lp)
        }
        renkSayfasi = sayfa
        sayfa.goster()
    }

    private fun siralamaSec() {
        val etiketler = listOf(
            R.string.siralama_yeni,
            R.string.siralama_eski,
            R.string.siralama_ad_az,
            R.string.siralama_ad_za
        )
        val secili = Prefs.siralama(this)
        val sayfa = AltSayfa(this).baslik(getString(R.string.siralama))
        etiketler.forEachIndexed { indeks, etiket ->
            sayfa.madde(R.drawable.ic_sirala, getString(etiket), secili = indeks == secili) {
                Prefs.siralamaKaydet(this, indeks)
                ozetGuncelle(satirSiralama, siralamaAdi())
            }
        }
        sayfa.goster()
    }

    // --- Klasör ---

    private fun klasorSec() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        Kilit.sistemAraciBekleniyor = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_KLASOR)
    }

    // --- Yedekleme ---

    private fun disaAktarmayiBaslat() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, Yedekleme.dosyaAdi())
        Kilit.sistemAraciBekleniyor = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_DISA)
    }

    private fun iceAktarmayiBaslat() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("application/zip")
            .addCategory(Intent.CATEGORY_OPENABLE)
        Kilit.sistemAraciBekleniyor = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_ICE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (sonuc != RESULT_OK) return
        if (istek == ISTEK_KILIT) {
            recreate()
            return
        }
        val uri = veri?.data ?: return
        when (istek) {
            ISTEK_KLASOR -> {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    Prefs.klasorUriKaydet(this, uri.toString())
                    ozetGuncelle(satirKlasor, klasorOzeti())
                } catch (_: Exception) {
                }
            }
            ISTEK_DISA -> Thread {
                val sayi = Yedekleme.disaAktar(this, depo, uri)
                runOnUiThread { bilgi(sayi >= 0, getString(R.string.yedek_alindi)) }
            }.start()
            ISTEK_ICE -> Thread {
                val sayi = Yedekleme.iceAktar(this, depo, uri)
                runOnUiThread {
                    bilgi(sayi >= 0, getString(R.string.yedek_yuklendi, maxOf(sayi, 0)))
                }
            }.start()
        }
    }

    private fun bilgi(basarili: Boolean, mesaj: String) {
        val metin = if (basarili) mesaj else getString(R.string.yedek_hata)
        Toast.makeText(this, metin, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val ISTEK_KLASOR = 42
        const val ISTEK_DISA = 43
        const val ISTEK_ICE = 44
        const val ISTEK_KILIT = 45
        val KIRMIZI = 0xFFA32D2D.toInt()
        val NOTR = 0xFF5F5E5A.toInt()
        val YESIL = 0xFF0F6E56.toInt()
    }
}
