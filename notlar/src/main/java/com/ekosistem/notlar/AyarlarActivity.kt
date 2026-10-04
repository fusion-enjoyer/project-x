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
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.documentfile.provider.DocumentFile

class AyarlarActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var satirTema: View
    private lateinit var satirRenk: View
    private lateinit var satirKlasor: View
    private lateinit var satirSiralama: View
    private lateinit var satirKilit: View
    private lateinit var satirYaziTipi: View
    private lateinit var satirYaziBoyu: View
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
        satirYaziTipi = findViewById(R.id.satirYaziTipi)
        satirYaziBoyu = findViewById(R.id.satirYaziBoyu)
        satirKur(satirYaziTipi, R.drawable.ic_ayar_gorunum, vurgu, getString(R.string.yazi_tipi), yaziTipiAdi())
        satirKur(satirYaziBoyu, R.drawable.ic_ayar_gorunum, vurgu, getString(R.string.yazi_boyu), yaziBoyuAdi())
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
            R.drawable.ic_ayar_disa,
            YESIL,
            getString(R.string.disa_aktar),
            null
        )
        satirKur(
            findViewById(R.id.satirIceAktar),
            R.drawable.ic_ayar_ice,
            YESIL,
            getString(R.string.ice_aktar),
            null
        )
        satirKur(
            findViewById(R.id.satirKeep),
            R.drawable.ic_ayar_ice,
            YESIL,
            getString(R.string.keep_aktar),
            getString(R.string.keep_aktar_ozet)
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
            getString(R.string.ekran_gizle_ozet)
        )
        anahtarKur(findViewById(R.id.satirEkranGizle), Prefs.ekranGizle(this)) { acik ->
            Prefs.ekranGizleKaydet(this, acik)
            ekranGizlemeyiUygula(this)
        }

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
        satirYaziTipi.setOnClickListener { yaziTipiSec() }
        satirYaziBoyu.setOnClickListener { yaziBoyuSec() }
        satirRenk.setOnClickListener { renkSec() }
        satirKlasor.setOnClickListener { klasorSec() }
        satirSiralama.setOnClickListener { siralamaSec() }
        satirKilit.setOnClickListener { kilitAyari() }
        findViewById<View>(R.id.satirDisaAktar).setOnClickListener { disaAktarmayiBaslat() }
        findViewById<View>(R.id.satirIceAktar).setOnClickListener { iceAktarmayiBaslat() }
        findViewById<View>(R.id.satirKeep).setOnClickListener { keepAciklamasi() }
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

    /**
     * Aç/kapa ayarı: ok yerine anahtar, satırın tamamına dokunmak onu çevirir.
     * Önceden "Kapalı" yazan oklu satırdı; dokununca ekran yeniden kuruluyordu.
     */
    private fun anahtarKur(satir: View, acik: Boolean, degisti: (Boolean) -> Unit) {
        val anahtar = satir.findViewById<SwitchCompat>(R.id.ayarAnahtar)
        satir.findViewById<View>(R.id.ayarChevron).visibility = View.GONE
        anahtar.visibility = View.VISIBLE
        anahtar.isChecked = acik
        val durumlar = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        anahtar.trackTintList = ColorStateList(
            durumlar,
            intArrayOf(Renkler.vurgu(this), ContextCompat.getColor(this, R.color.anahtar_kapali))
        )
        anahtar.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        satir.setOnClickListener {
            anahtar.toggle()
            degisti(anahtar.isChecked)
        }
        // Ekran okuyucu satırı bir anahtar olarak okusun ("açık/kapalı").
        ViewCompat.setAccessibilityDelegate(satir, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Switch::class.java.name
                info.isCheckable = true
                info.isChecked = anahtar.isChecked
            }
        })
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
        if (Renkler.sistemSecili(this)) return getString(R.string.renk_sistem)
        val indeks = Prefs.vurguIndeksi(this).coerceIn(0, Renkler.SECENEKLER.size - 1)
        return getString(Renkler.SECENEKLER[indeks].adKaynagi)
    }

    private fun yaziTipiAdi(): String =
        getString(YaziTipleri.secenek(Prefs.yaziTipi(this)).adKaynagi)

    private fun yaziBoyuAdi(): String = getString(
        when (Prefs.yaziBoyu(this)) {
            14 -> R.string.boy_kucuk
            18 -> R.string.boy_buyuk
            20 -> R.string.boy_cok_buyuk
            else -> R.string.boy_normal
        }
    )

    /** Her seçenek kendi yazı tipiyle yazılır: kullanıcı seçmeden önce görür. */
    private fun yaziTipiSec() {
        val secili = YaziTipleri.secenek(Prefs.yaziTipi(this)).kimlik
        val sayfa = AltSayfa(this).baslik(getString(R.string.yazi_tipi))
        for (secenek in YaziTipleri.SECENEKLER) {
            sayfa.madde(
                R.drawable.ic_ayar_gorunum_koyu,
                getString(secenek.adKaynagi),
                secili = secenek.kimlik == secili,
                yaziTipi = YaziTipleri.tip(secenek.kimlik)
            ) {
                Prefs.yaziTipiKaydet(this, secenek.kimlik)
                ozetGuncelle(satirYaziTipi, yaziTipiAdi())
            }
        }
        sayfa.goster()
    }

    private fun yaziBoyuSec() {
        val boylar = listOf(
            14 to R.string.boy_kucuk,
            16 to R.string.boy_normal,
            18 to R.string.boy_buyuk,
            20 to R.string.boy_cok_buyuk
        )
        val secili = Prefs.yaziBoyu(this)
        val sayfa = AltSayfa(this).baslik(getString(R.string.yazi_boyu))
        for ((sp, ad) in boylar) {
            sayfa.madde(R.drawable.ic_ayar_gorunum_koyu, getString(ad), secili = sp == secili) {
                Prefs.yaziBoyuKaydet(this, sp)
                ozetGuncelle(satirYaziBoyu, yaziBoyuAdi())
            }
        }
        sayfa.goster()
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

        // Renkler beşerli satırlarda, her örnek eşit genişlikte bir hücrenin
        // ortasında durur; eksik kalan son satır da ortalanır (sola yapışmaz).
        val satirBasina = 5
        val hucre = (56 * yogunluk).toInt()
        var satir: LinearLayout? = null
        Renkler.SECENEKLER.forEachIndexed { indeks, secenek ->
            if (indeks % satirBasina == 0) {
                satir = LinearLayout(this)
                satir?.orientation = LinearLayout.HORIZONTAL
                satir?.gravity = Gravity.CENTER_HORIZONTAL
                kutu.addView(
                    satir,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (8 * yogunluk).toInt() }
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
            ipucuVer(ornek)
            ornek.setOnClickListener {
                Prefs.vurguKaydet(this, indeks)
                renkSayfasi?.kapat()
                recreate()
            }
            // 44 dp daire, 56 dp dokunma hücresinin ortasında.
            val hucreKap = FrameLayout(this)
            val boyut = (44 * yogunluk).toInt()
            hucreKap.addView(ornek, FrameLayout.LayoutParams(boyut, boyut, Gravity.CENTER))
            satir?.addView(hucreKap, LinearLayout.LayoutParams(hucre, hucre))
        }
        // Android 12+: renk duvar kağıdından (Material You) gelsin, cihazla bütün dursun.
        if (Renkler.sistemRengiVar()) {
            sayfa.madde(
                R.drawable.ic_ayar_gorunum,
                getString(R.string.renk_sistem),
                secili = seciliIndeks == Renkler.SISTEM
            ) {
                Prefs.vurguKaydet(this, Renkler.SISTEM)
                recreate()
            }
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

    // --- Google Keep ---

    /** Kullanıcı Takeout'u bilmeyebilir: önce nereden ne indireceğini anlat. */
    private fun keepAciklamasi() {
        AltSayfa(this)
            .mesaj(getString(R.string.keep_aciklama))
            .madde(R.drawable.ic_ayar_ice, getString(R.string.keep_zip_sec)) { keepZipSec() }
            .goster()
    }

    private fun keepZipSec() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("application/zip")
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip")
            )
            // Takeout büyük dışa aktarımı birden çok zip'e böler.
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addCategory(Intent.CATEGORY_OPENABLE)
        Kilit.sistemAraciBekleniyor = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_KEEP)
    }

    private fun keepAktar(kaynaklar: List<Uri>) {
        Toast.makeText(this, R.string.keep_aktariliyor, Toast.LENGTH_LONG).show()
        val arsiv = getString(R.string.keep_arsiv_klasoru)
        // Uzun sürebilir; kullanıcı ayarlardan çıksa da iş bitsin, sonuç yine gösterilsin.
        val uygulama = applicationContext
        NotDeposu.yazici.execute {
            val s = KeepAktarma.aktar(uygulama, NotDeposu(uygulama), kaynaklar, arsiv)
            val mesaj = keepSonucMetni(s)
            runOnUiThread { sonucGoster(mesaj) }
        }
    }

    private fun keepSonucMetni(s: KeepAktarma.Sonuc?): String = when {
        s == null -> getString(R.string.yedek_hata)
        !s.keepBulundu -> getString(R.string.keep_bulunamadi)
        else -> buildList {
            add(resources.getQuantityString(R.plurals.keep_not_sayisi, s.not, s.not))
            if (s.gorsel > 0) {
                add(resources.getQuantityString(R.plurals.keep_gorsel_sayisi, s.gorsel, s.gorsel))
            }
            if (s.zatenVardi > 0) {
                add(resources.getQuantityString(R.plurals.keep_zaten_vardi, s.zatenVardi, s.zatenVardi))
            }
            if (s.atlananEk > 0) {
                add(resources.getQuantityString(R.plurals.keep_ek_atlandi, s.atlananEk, s.atlananEk))
            }
        }.joinToString("\n")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (sonuc != RESULT_OK) return
        if (istek == ISTEK_KILIT) {
            recreate()
            return
        }
        if (istek == ISTEK_KEEP) {
            val secilenler = mutableListOf<Uri>()
            val coklu = veri?.clipData
            if (coklu != null) {
                for (i in 0 until coklu.itemCount) coklu.getItemAt(i).uri?.let { secilenler.add(it) }
            } else {
                veri?.data?.let { secilenler.add(it) }
            }
            if (secilenler.isNotEmpty()) keepAktar(secilenler)
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
            ISTEK_ICE -> {
                // Kullanıcı ayarlardan çıksa da iş bitsin, sonuç yine gösterilsin.
                val uygulama = applicationContext
                NotDeposu.yazici.execute {
                    val s = Yedekleme.iceAktar(uygulama, NotDeposu(uygulama), uri)
                    val mesaj = if (s == null) {
                        getString(R.string.yedek_hata)
                    } else {
                        listOfNotNull(
                            resources.getQuantityString(R.plurals.yedek_yuklendi, s.yeni, s.yeni),
                            s.zatenVardi.takeIf { it > 0 }?.let {
                                resources.getQuantityString(R.plurals.keep_zaten_vardi, it, it)
                            },
                            getString(R.string.yedek_yarim).takeIf { s.yarim }
                        ).joinToString("\n")
                    }
                    runOnUiThread { sonucGoster(mesaj) }
                }
            }
        }
    }

    /** Birkaç satırlık sonuç alt sayfada; bildirim (toast) iki satırda kesiyor. */
    private fun sonucGoster(mesaj: String) {
        if (isFinishing || isDestroyed) {
            Toast.makeText(applicationContext, mesaj, Toast.LENGTH_LONG).show()
        } else {
            AltSayfa(this)
                .mesaj(mesaj)
                .madde(R.drawable.ic_onay_isaret, getString(R.string.kapat)) {}
                .goster()
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
        const val ISTEK_KEEP = 46
        val KIRMIZI = 0xFFA32D2D.toInt()
        val NOTR = 0xFF5F5E5A.toInt()
        val YESIL = 0xFF0F6E56.toInt()
    }
}
