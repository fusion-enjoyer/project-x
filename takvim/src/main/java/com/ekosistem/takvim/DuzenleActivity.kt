package com.ekosistem.takvim

import android.content.ContentUris
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.AyarSatiri
import com.ekosistem.tasarim.Tasarim
import java.util.TimeZone
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/**
 * Etkinlik ekleme ve düzenleme. Başlık, tüm gün, başlangıç/bitiş, tekrar,
 * takvim, hatırlatıcılar, konum, açıklama. Tekrarlayan etkinlik kaydedilirken
 * "yalnız bu / bu ve sonrakiler / hepsi" sorulur.
 */
class DuzenleActivity : AppCompatActivity() {

    private val tz = TimeZone.getDefault()
    private val yurutucu = Executors.newSingleThreadExecutor()
    private var vurgu = 0

    private var orijinal: Etkinlik? = null
    private var ornekBas = 0L
    private var takvimler: List<Takvim> = emptyList()

    private var takvimId = 0L
    private var tumGun = false
    private var basGun = 0
    private var basDk = 0
    private var bitGun = 0
    private var bitDk = 0
    private var sonSaatBas = 9 * 60
    private var sonSaatBit = 10 * 60
    private var kural: String? = null
    private var ozelRenk = 0
    private var hatirlaticilar = mutableListOf<Int>()
    private var zamanDilimi = ""
    private var degisti = false
    private var kaydediliyor = false
    private var hazir = false

    private lateinit var etBaslik: EditText
    private lateinit var etKonum: EditText
    private lateinit var etAciklama: EditText
    private lateinit var btnKaydet: TextView

    private val geri = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            AltSayfa(this@DuzenleActivity)
                .mesaj(getString(R.string.degisiklik_at_soru))
                .madde(TR.drawable.ic_kapat, getString(R.string.degisiklik_at), tehlikeli = true) {
                    isEnabled = false
                    finish()
                }
                .madde(R.drawable.ic_duzenle, getString(R.string.duzenlemeye_don)) {}
                .goster()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duzenle)
        vurgu = Tasarim.vurgu(this)
        etBaslik = findViewById(R.id.etBaslik)
        etKonum = findViewById(R.id.etKonum)
        etAciklama = findViewById(R.id.etAciklama)
        btnKaydet = findViewById(R.id.btnKaydet)
        btnKaydet.backgroundTintList = ColorStateList.valueOf(vurgu)
        btnKaydet.setTextColor(Tasarim.vurguUzeri(this))
        btnKaydet.setOnClickListener { kaydet() }
        findViewById<View>(R.id.btnGeri).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, geri)

        val izleyici = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (hazir) isaretle() }
        }
        etBaslik.addTextChangedListener(izleyici)
        etBaslik.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (hazir) oneriGuncelle() }
        })
        findViewById<TextView>(R.id.dogalDilCip).setOnClickListener { oneriUygula() }
        etKonum.addTextChangedListener(izleyici)
        etAciklama.addTextChangedListener(izleyici)

        if (!TakvimDeposu.izinVar(this)) {
            finish()
            return
        }
        yurutucu.execute {
            val t = TakvimDeposu.takvimler(this)
            val id = istenenId()
            val e = if (id > 0) TakvimDeposu.etkinlik(this, id) else null
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                takvimler = t
                if (id > 0 && e == null) {
                    Toast.makeText(this, R.string.etkinlik_bulunamadi, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    baslat(e)
                }
            }
        }
    }

    private fun istenenId(): Long {
        val i = intent
        val id = i.getLongExtra(EK_ID, 0L)
        if (id > 0) return id
        if (i.action == android.content.Intent.ACTION_EDIT) {
            return runCatching { ContentUris.parseId(i.data!!) }.getOrDefault(0L)
        }
        return 0L
    }

    private fun isaretle() {
        degisti = true
        geri.isEnabled = true
    }

    // ---- Başlangıç durumu ----

    private fun baslat(e: Etkinlik?) {
        // Çoğaltma: bilgiler yüklenir ama kayıt "yeni etkinlik" olarak yazılır.
        val cogalt = intent.getBooleanExtra(EK_COGALT, false)
        orijinal = if (cogalt) null else e
        val simdi = System.currentTimeMillis()
        val bugun = Gun.bugun(simdi, tz)
        if (e != null) {
            val bas = intent.getLongExtra(EK_BAS, 0L).takeIf { it > 0 } ?: e.baslangic
            val bit = intent.getLongExtra(EK_BIT, 0L).takeIf { it > 0 } ?: e.bitis
            ornekBas = bas
            takvimId = e.takvimId
            tumGun = e.tumGun
            zamanDilimi = e.zamanDilimi
            if (e.tumGun) {
                basGun = Gun.utcGun(bas)
                bitGun = maxOf(basGun, Gun.utcGun(bit - 1))
            } else {
                basGun = Gun.yerelGun(bas, tz); basDk = Gun.yerelDakika(bas, tz)
                bitGun = Gun.yerelGun(bit, tz); bitDk = Gun.yerelDakika(bit, tz)
            }
            kural = e.kural
            ozelRenk = e.ozelRenk
            hatirlaticilar = e.hatirlaticilar.toMutableList()
            etBaslik.setText(e.baslik)
            etKonum.setText(e.konum)
            etAciklama.setText(e.aciklama)
            findViewById<TextView>(R.id.duzenleBaslik).setText(if (cogalt) R.string.yeni_etkinlik else R.string.etkinligi_duzenle)
        } else {
            findViewById<TextView>(R.id.duzenleBaslik).setText(R.string.yeni_etkinlik)
            val yazilabilir = takvimler.filter { it.yazilabilir }
            val tercih = Depo.varsayilanTakvim(this)
            takvimId = (yazilabilir.firstOrNull { it.id == tercih }
                ?: yazilabilir.firstOrNull { it.hesapTuru != CalendarContract.ACCOUNT_TYPE_LOCAL && it.gorunur }
                ?: yazilabilir.firstOrNull { it.gorunur }
                ?: yazilabilir.firstOrNull())?.id ?: 0L
            zamanDilimi = tz.id
            disaridanDoldur(bugun, simdi)
            Depo.varsayilanHatirlatma(this).takeIf { it >= 0 }?.let { hatirlaticilar.add(if (tumGun) VARSAYILAN_TUM_GUN else it) }
        }
        hazir = true
        arayuzuYaz()
        if (e == null) {
            etBaslik.requestFocus()
            etBaslik.post {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(etBaslik, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /** Yeni etkinlik: ana ekrandan gün/saat, başka uygulamadan INSERT ekstraları. */
    private fun disaridanDoldur(bugun: Int, simdi: Long) {
        val i = intent
        val sure = Depo.varsayilanSure(this)
        val dis = i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
        if (dis > 0) {
            tumGun = i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
            val bitis = i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
            if (tumGun) {
                basGun = Gun.utcGun(dis)
                bitGun = if (bitis > dis) maxOf(basGun, Gun.utcGun(bitis - 1)) else basGun
            } else {
                basGun = Gun.yerelGun(dis, tz); basDk = Gun.yerelDakika(dis, tz)
                val b = if (bitis > dis) bitis else dis + sure * 60_000L
                bitGun = Gun.yerelGun(b, tz); bitDk = Gun.yerelDakika(b, tz)
            }
            i.getStringExtra(CalendarContract.Events.TITLE)?.let { etBaslik.setText(it) }
            i.getStringExtra(CalendarContract.Events.EVENT_LOCATION)?.let { etKonum.setText(it) }
            i.getStringExtra(CalendarContract.Events.DESCRIPTION)?.let { etAciklama.setText(it) }
            return
        }
        basGun = i.getIntExtra(EK_GUN, bugun)
        val istenen = i.getIntExtra(EK_DAKIKA, -1)
        basDk = when {
            istenen >= 0 -> istenen
            basGun == bugun -> minOf(23 * 60, (Gun.yerelDakika(simdi, tz) / 60 + 1) * 60)
            else -> 9 * 60
        }
        val bitis = basGun * 1440L + basDk + sure
        bitGun = Math.floorDiv(bitis, 1440L).toInt()
        bitDk = Math.floorMod(bitis, 1440L).toInt()
    }

    // ---- Başlıktan anlama ("yarın 14:00 toplantı") ----

    private var oneri: DogalDil.Sonuc? = null

    /** Yalnız yeni etkinlikte: başlık yazılırken tarih/saat cümlesi aranır ve öneri çıkar. */
    private fun oneriGuncelle() {
        val cip = findViewById<TextView>(R.id.dogalDilCip)
        val yeni = orijinal == null
        val metin = etBaslik.text.toString()
        val simdi = System.currentTimeMillis()
        val s = if (yeni && metin.length >= 3) DogalDil.coz(metin, Gun.bugun(simdi, tz), Gun.yerelDakika(simdi, tz)) else null
        oneri = s?.takeIf { it.bulundu }
        val o = oneri
        if (o == null) {
            cip.visibility = View.GONE
            return
        }
        cip.text = getString(R.string.dogal_uygula, Metinler.oneriOzeti(this, o, Gun.bugun(simdi, tz)))
        cip.visibility = View.VISIBLE
    }

    /** Öneriyi uygular: tarih/saat/süre/tekrar alanları dolar, anlaşılan kısım başlıktan çıkar. */
    private fun oneriUygula() {
        val o = oneri ?: return
        if (o.tumGun && !tumGun) tumGunDegistir(true)
        else if (!o.tumGun && o.baslangicDk != null && tumGun) tumGunDegistir(false)
        val eskiSure = bitGun * 1440L + bitDk - (basGun * 1440L + basDk)
        if (o.gun != null) basGun = o.gun
        if (!tumGun) {
            if (o.baslangicDk != null) basDk = o.baslangicDk
            val sure = when {
                o.bitisDk != null -> (if (o.bitisDk > basDk) o.bitisDk - basDk else o.bitisDk + 1440 - basDk).toLong()
                o.sureDk != null -> o.sureDk.toLong()
                else -> maxOf(eskiSure, Depo.varsayilanSure(this).toLong()).coerceAtLeast(15)
            }
            val bit = basGun * 1440L + basDk + sure
            bitGun = Math.floorDiv(bit, 1440L).toInt()
            bitDk = Math.floorMod(bit, 1440L).toInt()
        } else {
            bitGun = basGun
            bitDk = 0
        }
        o.kural?.let { kural = Tekrar.yaz(it, tumGun, tz) }
        etBaslik.setText(o.baslik)
        etBaslik.setSelection(etBaslik.text.length)
        isaretle()
        arayuzuYaz()
    }

    // ---- Arayüz ----

    private fun arayuzuYaz() {
        val tumGunSatir = findViewById<View>(R.id.satirTumGun)
        AyarSatiri.kur(tumGunSatir, R.drawable.ic_saat, vurgu, getString(R.string.tum_gun), null)
        AyarSatiri.anahtar(tumGunSatir, tumGun) { tumGunDegistir(it) }

        zamanSatiriniYaz(findViewById(R.id.satirBaslangic), getString(R.string.baslangic), true)
        zamanSatiriniYaz(findViewById(R.id.satirBitis), getString(R.string.bitis), false)

        val tekrar = findViewById<View>(R.id.satirTekrar)
        AyarSatiri.kur(tekrar, R.drawable.ic_tekrar, vurgu, getString(R.string.tekrar), tekrarOzeti())
        tekrar.setOnClickListener { tekrarSec() }

        val takvim = findViewById<View>(R.id.satirTakvim)
        val t = takvimler.firstOrNull { it.id == takvimId }
        AyarSatiri.kur(takvim, R.drawable.ic_takvim, t?.renk ?: vurgu, getString(R.string.takvim), t?.ad ?: getString(R.string.takvim_sec))
        takvim.findViewById<ImageView>(TR.id.ayarIkon).imageTintList = ColorStateList.valueOf(Tasarim.uzerindekiRenk(t?.renk ?: vurgu))
        takvim.setOnClickListener { takvimSec() }

        // Etkinliğe özel renk: yalnız yerel takvimde (sunucu takvimleri kendi renk paletini kullanır).
        val yerel = t?.hesapTuru == CalendarContract.ACCOUNT_TYPE_LOCAL
        val renkSatiri = findViewById<View>(R.id.satirRenk)
        renkSatiri.visibility = if (yerel) View.VISIBLE else View.GONE
        findViewById<View>(R.id.ayracRenk).visibility = if (yerel) View.VISIBLE else View.GONE
        if (yerel) {
            val gosterilen = if (ozelRenk != 0) ozelRenk else (t?.renk ?: vurgu)
            AyarSatiri.kur(renkSatiri, R.drawable.ic_renk, gosterilen, getString(R.string.renk), if (ozelRenk == 0) getString(R.string.renk_takvim) else null)
            renkSatiri.findViewById<ImageView>(TR.id.ayarIkon).imageTintList = ColorStateList.valueOf(Tasarim.uzerindekiRenk(gosterilen))
            renkSatiri.setOnClickListener { renkSec() }
        }

        hatirlaticilariYaz()
    }

    private fun tarihEtiketi(g: Int): String {
        val kisa = Metinler.haftaGunuKisa(Gun.haftaGunu(g)) + ", " +
            if (Gun.yil(g) == Gun.yil(Gun.bugun(System.currentTimeMillis(), tz))) Metinler.gunAyKisa(g) else Metinler.tamTarihKisa(g)
        return kisa
    }

    private fun zamanSatiriniYaz(satir: View, etiket: String, baslangic: Boolean) {
        satir.findViewById<TextView>(R.id.zamanEtiket).text = etiket
        val tarih = satir.findViewById<TextView>(R.id.zamanTarih)
        val saat = satir.findViewById<TextView>(R.id.zamanSaat)
        val gun = if (baslangic) basGun else bitGun
        val dk = if (baslangic) basDk else bitDk
        tarih.text = tarihEtiketi(gun)
        tarih.contentDescription = etiket + ", " + Metinler.tamTarih(gun)
        saat.text = Metinler.saat(this, dk)
        saat.contentDescription = etiket + ", " + Metinler.saat(this, dk)
        saat.visibility = if (tumGun) View.GONE else View.VISIBLE
        tarih.setOnClickListener {
            Secenekler.tarihSec(this, etiket, gun, if (baslangic) null else basGun) { g ->
                if (baslangic) baslangicDegistir(g, basDk) else bitisDegistir(g, bitDk)
            }
        }
        saat.setOnClickListener {
            Secenekler.saatSec(this, etiket, dk) { m ->
                if (baslangic) baslangicDegistir(basGun, m) else bitisDegistir(bitGun, m)
            }
        }
    }

    private fun baslangicDegistir(g: Int, dk: Int) {
        // Süre korunur: başlangıcı kaydırınca bitiş de aynı kadar kayar.
        val sure = bitGun * 1440L + bitDk - (basGun * 1440L + basDk)
        basGun = g
        basDk = dk
        val bit = basGun * 1440L + basDk + maxOf(0L, sure)
        bitGun = Math.floorDiv(bit, 1440L).toInt()
        bitDk = Math.floorMod(bit, 1440L).toInt()
        isaretle()
        arayuzuYaz()
    }

    private fun bitisDegistir(g: Int, dk: Int) {
        bitGun = g
        bitDk = dk
        // Bitiş başlangıçtan önce olamaz: başlangıca çekilir.
        if (bitGun * 1440L + bitDk < basGun * 1440L + basDk) {
            bitGun = basGun
            bitDk = basDk
        }
        isaretle()
        arayuzuYaz()
    }

    private fun tumGunDegistir(acik: Boolean) {
        if (acik == tumGun) return
        val onceVarsayilan = varsayilanHatirlatmaListesi(tumGun)
        if (acik) {
            sonSaatBas = basDk
            sonSaatBit = bitDk
            basDk = 0
            bitDk = 0
            if (bitGun < basGun) bitGun = basGun
        } else {
            basDk = sonSaatBas
            bitDk = sonSaatBit
            // Aynı güne sığıyorsa saat aralığını geri ver, değilse başlangıç + 1 saat.
            if (bitGun == basGun && bitDk <= basDk) bitDk = minOf(1439, basDk + 60)
        }
        tumGun = acik
        // Hatırlatıcı henüz varsayılan haldeyse yeni türün varsayılanına geçer.
        if (hatirlaticilar == onceVarsayilan) {
            hatirlaticilar = varsayilanHatirlatmaListesi(acik).toMutableList()
        }
        isaretle()
        arayuzuYaz()
    }

    private fun varsayilanHatirlatmaListesi(tumGunMu: Boolean): List<Int> =
        Depo.varsayilanHatirlatma(this).takeIf { it >= 0 }?.let { listOf(if (tumGunMu) VARSAYILAN_TUM_GUN else it) } ?: emptyList()

    // ---- Tekrar ----

    private fun cozulmusKural(): Kural? = Tekrar.coz(kural, basGun, tz)

    private fun tekrarOzeti(): String = Metinler.tekrar(this, cozulmusKural(), kural, basGun)

    private fun tekrarSec() {
        val k = cozulmusKural()
        val sade = k?.copy(sayi = 0, bitisGun = null)
        val gun = Gun.haftaGunu(basGun)
        val n = (Gun.ayinGunu(basGun) - 1) / 7 + 1
        fun yaz(yeni: Kural?) {
            kural = yeni?.let { Tekrar.yaz(it, tumGun, tz) }
            isaretle()
            arayuzuYaz()
        }
        val sayfa = AltSayfa(this).baslik(getString(R.string.tekrar))
        if (kural != null && k == null) {
            sayfa.madde(R.drawable.ic_tekrar, getString(R.string.tekrar_ozel_korunur), secili = true) {}
        }
        sayfa.madde(R.drawable.ic_tekrar, getString(R.string.tekrar_yok), secili = kural == null) { yaz(null) }
        sayfa.madde(R.drawable.ic_tekrar, getString(R.string.her_gun), secili = sade == Kural(Sik.GUNLUK)) { yaz(Kural(Sik.GUNLUK)) }
        sayfa.madde(
            R.drawable.ic_tekrar, getString(R.string.her_hafta) + " · " + Metinler.haftaGunuUzun(gun),
            secili = sade == Kural(Sik.HAFTALIK)
        ) { yaz(Kural(Sik.HAFTALIK)) }
        sayfa.madde(
            R.drawable.ic_tekrar, getString(R.string.her_ay) + " · " + getString(R.string.ayin_n_gunu, Gun.ayinGunu(basGun)),
            secili = sade == Kural(Sik.AYLIK)
        ) { yaz(Kural(Sik.AYLIK)) }
        if (n <= 4) {
            sayfa.madde(
                R.drawable.ic_tekrar,
                getString(R.string.her_ay) + " · " + resources.getStringArray(R.array.sira_adlari)[n - 1] + " " + Metinler.haftaGunuUzun(gun),
                secili = sade == Kural(Sik.AYLIK, gunler = setOf(gun), aySirasi = n)
            ) { yaz(Kural(Sik.AYLIK, gunler = setOf(gun), aySirasi = n)) }
        }
        sayfa.madde(
            R.drawable.ic_tekrar, getString(R.string.her_yil) + " · " + Metinler.gunAy(basGun),
            secili = sade == Kural(Sik.YILLIK)
        ) { yaz(Kural(Sik.YILLIK)) }
        sayfa.madde(R.drawable.ic_duzenle, getString(R.string.ozel_tekrar)) {
            TekrarSayfasi(this, basGun, k) { yeni -> yaz(yeni) }.goster()
        }
        sayfa.goster()
    }

    // ---- Renk ----

    private fun renkSec() {
        val d = resources.displayMetrics.density
        val sira = android.widget.LinearLayout(this)
        sira.orientation = android.widget.LinearLayout.HORIZONTAL
        val sayfa = AltSayfa(this).baslik(getString(R.string.renk))
        for (renk in PALET) {
            val secili = renk == ozelRenk
            val daire = ImageView(this)
            daire.setBackgroundResource(R.drawable.bg_nokta)
            daire.backgroundTintList = ColorStateList.valueOf(renk)
            daire.scaleType = ImageView.ScaleType.CENTER
            if (secili) {
                daire.setImageResource(TR.drawable.ic_onay_isaret)
                daire.imageTintList = ColorStateList.valueOf(Tasarim.uzerindekiRenk(renk))
            }
            daire.contentDescription = getString(R.string.renk) + " " + (PALET.indexOf(renk) + 1)
            daire.setOnClickListener { ozelRenk = renk; isaretle(); sayfa.kapat(); arayuzuYaz() }
            sira.addView(daire, android.widget.LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply {
                rightMargin = (8 * d).toInt()
            })
        }
        val kaydirici = android.widget.HorizontalScrollView(this)
        kaydirici.isHorizontalScrollBarEnabled = false
        kaydirici.addView(sira)
        sayfa.icerik(kaydirici)
        sayfa.madde(R.drawable.ic_takvim, getString(R.string.renk_takvim), secili = ozelRenk == 0) {
            ozelRenk = 0; isaretle(); arayuzuYaz()
        }
        sayfa.goster()
    }

    // ---- Takvim ve hatırlatıcılar ----

    private fun takvimSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.takvim))
        for (t in takvimler.filter { it.yazilabilir }) {
            val ad = if (t.hesap.isNotEmpty() && t.hesap != t.ad && t.hesapTuru != CalendarContract.ACCOUNT_TYPE_LOCAL) "${t.ad} · ${t.hesap}" else t.ad
            sayfa.madde(R.drawable.ic_takvim, ad, secili = t.id == takvimId) {
                takvimId = t.id
                isaretle()
                arayuzuYaz()
            }
        }
        sayfa.goster()
    }

    private fun hatirlaticilariYaz() {
        val kutu = findViewById<android.widget.LinearLayout>(R.id.hatirlaticiListesi)
        kutu.removeAllViews()
        hatirlaticilar.sorted().forEachIndexed { sira, dk ->
            val satir = layoutInflater.inflate(TR.layout.item_ayar, kutu, false)
            AyarSatiri.kur(satir, R.drawable.ic_zil, vurgu, Metinler.hatirlatici(this, dk, tumGun), null)
            satir.setOnClickListener { hatirlaticiSec(dk) }
            kutu.addView(satir)
            kutu.addView(layoutInflater.inflate(R.layout.item_ayrac, kutu, false))
        }
        val ekle = findViewById<View>(R.id.satirHatirlaticiEkle)
        AyarSatiri.kur(ekle, R.drawable.ic_arti, vurgu, getString(R.string.hatirlatici_ekle), null)
        ekle.setOnClickListener { hatirlaticiSec(null) }
    }

    /** [eski] verilmişse o hatırlatıcı değişir ya da kaldırılır, yoksa yenisi eklenir. */
    private fun hatirlaticiSec(eski: Int?) {
        val secenekler = if (tumGun) listOf(900, 0, 2340, 9540) else listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)
        val sayfa = AltSayfa(this).baslik(getString(R.string.hatirlatici))
        for (dk in secenekler) {
            if (dk != eski && dk in hatirlaticilar) continue
            sayfa.madde(R.drawable.ic_zil, Metinler.hatirlatici(this, dk, tumGun), secili = dk == eski) {
                if (eski != null) hatirlaticilar.remove(eski)
                hatirlaticilar.add(dk)
                isaretle()
                arayuzuYaz()
            }
        }
        if (eski != null) {
            sayfa.madde(TR.drawable.ic_kapat, getString(R.string.kaldir), tehlikeli = true) {
                hatirlaticilar.remove(eski)
                isaretle()
                arayuzuYaz()
            }
        }
        sayfa.goster()
    }

    // ---- Kaydet ----

    private fun etkinligiKur(): Etkinlik? {
        val baslangic: Long
        val bitis: Long
        if (tumGun) {
            baslangic = Gun.utcGunBasi(basGun)
            bitis = Gun.utcGunBasi(maxOf(bitGun, basGun) + 1)
        } else {
            baslangic = Gun.yerelAn(basGun, basDk, tz)
            bitis = Gun.yerelAn(bitGun, bitDk, tz)
            if (bitis < baslangic) return null
        }
        var yeniKural = kural
        val o = orijinal
        // Tüm gün ↔ saatli değişince bitiş tarihinin biçimi (20261231 / 20261231T…Z) yenilenir.
        if (yeniKural != null && o != null && o.tumGun != tumGun) {
            Tekrar.coz(yeniKural, basGun, tz)?.let { yeniKural = Tekrar.yaz(it, tumGun, tz) }
        }
        return Etkinlik(
            id = o?.id ?: 0L, takvimId = takvimId, baslik = etBaslik.text.toString().trim(),
            konum = etKonum.text.toString().trim(), aciklama = etAciklama.text.toString().trim(),
            baslangic = baslangic, bitis = bitis, tumGun = tumGun,
            zamanDilimi = if (tumGun) "UTC" else (o?.takeIf { !it.tumGun }?.zamanDilimi ?: tz.id),
            kural = yeniKural, hatirlaticilar = hatirlaticilar.sorted(), renk = o?.renk ?: 0,
            ozelRenk = if (takvimler.firstOrNull { it.id == takvimId }?.hesapTuru == CalendarContract.ACCOUNT_TYPE_LOCAL) ozelRenk else 0
        )
    }

    private fun kaydet() {
        if (kaydediliyor) return
        if (takvimId == 0L) {
            Toast.makeText(this, R.string.takvim_sec_uyari, Toast.LENGTH_LONG).show()
            return
        }
        val e = etkinligiKur()
        if (e == null) {
            Toast.makeText(this, R.string.bitis_once, Toast.LENGTH_LONG).show()
            return
        }
        val o = orijinal
        if (o?.kural != null && o.asilId == 0L) {
            val kuralDegisti = e.kural != o.kural
            val sayfa = AltSayfa(this).baslik(getString(R.string.hangi_etkinlikler))
            if (!kuralDegisti) sayfa.madde(R.drawable.ic_gun, getString(R.string.kapsam_bu)) { kaydetIsle(e, Kapsam.BU) }
            sayfa.madde(R.drawable.ic_gundem, getString(R.string.kapsam_sonrakiler)) { kaydetIsle(e, Kapsam.BUNDAN_SONRA) }
            sayfa.madde(R.drawable.ic_tekrar, getString(R.string.kapsam_hepsi)) { kaydetIsle(e, Kapsam.HEPSI) }
            sayfa.goster()
        } else {
            kaydetIsle(e, Kapsam.HEPSI)
        }
    }

    private fun kaydetIsle(e: Etkinlik, kapsam: Kapsam) {
        kaydediliyor = true
        btnKaydet.alpha = 0.5f
        val o = orijinal
        yurutucu.execute {
            val id = if (o == null) TakvimDeposu.ekle(this, e) else TakvimDeposu.guncelle(this, o, ornekBas, e, kapsam)
            runOnUiThread {
                if (id == null) {
                    kaydediliyor = false
                    btnKaydet.alpha = 1f
                    Toast.makeText(this, R.string.kaydedilemedi, Toast.LENGTH_LONG).show()
                } else {
                    if (o == null) Depo.varsayilanTakvimKaydet(this, e.takvimId)
                    geri.isEnabled = false
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    companion object {
        const val EK_ID = "id"
        const val EK_BAS = "bas"
        const val EK_BIT = "bit"
        const val EK_GUN = "gun"
        const val EK_DAKIKA = "dakika"
        const val EK_COGALT = "cogalt"

        /** Tüm gün etkinliğinin varsayılan hatırlatıcısı: önceki gün 09:00. */
        const val VARSAYILAN_TUM_GUN = 900

        /** Etkinlik rengi paleti (tasarım dilinin vurguları + yaygın tonlar). */
        val PALET = intArrayOf(
            0xFF0F766E.toInt(), 0xFF2563EB.toInt(), 0xFF4F46E5.toInt(), 0xFF9333EA.toInt(), 0xFFDB2777.toInt(),
            0xFFDC2626.toInt(), 0xFFEA580C.toInt(), 0xFFCA8A04.toInt(), 0xFF16A34A.toInt(), 0xFF52525B.toInt()
        )
    }
}
