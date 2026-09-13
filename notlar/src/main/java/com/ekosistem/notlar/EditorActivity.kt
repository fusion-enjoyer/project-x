package com.ekosistem.notlar

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class EditorActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var metinAlani: NotEditText
    private lateinit var bicimci: MarkdownBicimci
    private lateinit var bicimCubugu: LinearLayout
    private lateinit var bicimKaydirici: View
    private lateinit var btnOkuma: ImageButton

    private var uri: Uri? = null
    private var hedefKlasor: String? = null
    private var acilisMetni = ""
    private var silindi = false

    private var bicimleniyor = false
    private var satirEklendi = false
    private var aktifSatirBasi = -1
    private var okumaModu = false

    // Geri al / yinele
    private val gecmis = ArrayDeque<Durum>()
    private val gelecek = ArrayDeque<Durum>()
    private var sonDurum = Durum("", 0)
    private var sonKayit = 0L
    private var geriAliniyor = false
    private var btnGeriAl: ImageButton? = null
    private var btnYinele: ImageButton? = null

    // Bul ve değiştir
    private lateinit var bulCubugu: View
    private lateinit var bulAlani: EditText
    private lateinit var degistirAlani: EditText
    private lateinit var bulSayac: TextView
    private var eslesmeler: List<Int> = emptyList()
    private var eslesmeSirasi = -1

    private data class Durum(val metin: String, val imlec: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        depo = NotDeposu(this)
        bicimci = MarkdownBicimci(this)
        metinAlani = findViewById(R.id.metinAlani)
        bicimCubugu = findViewById(R.id.bicimCubugu)
        bicimKaydirici = findViewById(R.id.bicimKaydirici)
        btnOkuma = findViewById(R.id.btnOkuma)

        ipucuKur()
        bicimCubuguKur()
        onayKutusuDokunmaKur()
        bulCubuguKur()
        vurguRengiUygula()

        metinAlani.addTextChangedListener(MetinIzleyici())
        metinAlani.secimDegisti = { bas, _ ->
            val yeniSatir = satirBasiBul(bas)
            if (yeniSatir != aktifSatirBasi && !bicimleniyor) {
                aktifSatirBasi = yeniSatir
                bicimlendir()
            }
        }

        uri = intent.getStringExtra("uri")?.let(Uri::parse)
        hedefKlasor = intent.getStringExtra("klasor")
        val acilacak = uri
        if (acilacak != null) {
            Thread {
                val metin = depo.oku(acilacak)
                runOnUiThread {
                    acilisMetni = metin
                    geriAliniyor = true
                    metinAlani.setText(metin)
                    geriAliniyor = false
                    sonDurum = Durum(metin, 0)
                    metinAlani.post { bicimlendir() }
                }
            }.start()
        } else {
            metinAlani.requestFocus()
        }

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditorMenu).setOnClickListener { menuGoster() }
        btnOkuma.setOnClickListener { okumaModunuDegistir() }
        dugmeleriGuncelle()
    }

    private fun vurguRengiUygula() {
        val vurgu = Renkler.vurgu(this)
        findViewById<TextView>(R.id.btnDegistir).setTextColor(vurgu)
        findViewById<TextView>(R.id.btnTumunuDegistir).setTextColor(vurgu)
    }

    // --- Metin değişikliği ---

    private inner class MetinIzleyici : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, bas: Int, onceki: Int, sonraki: Int) {}

        override fun onTextChanged(s: CharSequence?, bas: Int, onceki: Int, sayi: Int) {
            if (bicimleniyor || geriAliniyor) return
            satirEklendi = s != null && onceki == 0 && sayi == 1 &&
                bas < s.length && s[bas] == '\n'
        }

        override fun afterTextChanged(s: Editable?) {
            if (s == null || bicimleniyor || geriAliniyor) return

            bicimleniyor = true
            if (satirEklendi) {
                satirEklendi = false
                listeyiSurdur(s)
            }
            aktifSatirBasi = satirBasiBul(metinAlani.selectionStart)
            bicimci.uygula(s, imlecKonumu(), metinGenisligi())
            bicimleniyor = false

            /*
             * Geri al yığını: her yazma öbeğinden (700 ms) önceki durum saklanır.
             * Anlık görüntü değişiklikten SONRA alınır, önce değil; böylece iptal
             * edilen bir değişiklik yığında asla eski bir durumu bırakmaz.
             */
            val simdi = System.currentTimeMillis()
            if (simdi - sonKayit > BIRLESTIRME_MS) {
                gecmis.addLast(sonDurum)
                if (gecmis.size > YIGIN_SINIRI) gecmis.removeFirst()
                gelecek.clear()
                sonKayit = simdi
                dugmeleriGuncelle()
            }
            sonDurum = Durum(s.toString(), metinAlani.selectionStart)

            if (bulCubugu.visibility == View.VISIBLE) eslesmeleriBul(false)
        }
    }

    private fun bicimlendir() {
        val s = metinAlani.text ?: return
        bicimleniyor = true
        bicimci.uygula(s, imlecKonumu(), metinGenisligi())
        bicimleniyor = false
    }

    /** Okuma modunda hiçbir satır "aktif" değildir; tüm işaretler gizlenir. */
    private fun imlecKonumu(): Int = if (okumaModu) -1 else metinAlani.selectionStart

    private fun metinGenisligi(): Int =
        metinAlani.width - metinAlani.totalPaddingLeft - metinAlani.totalPaddingRight

    private fun satirBasiBul(imlec: Int): Int {
        val s = metinAlani.text ?: return 0
        if (imlec <= 0) return 0
        val sinir = minOf(imlec, s.length)
        return s.toString().lastIndexOf('\n', sinir - 1).let { if (it < 0) 0 else it + 1 }
    }

    private fun ipucuKur() {
        val baslik = getString(R.string.baslik_ipucu)
        val govde = getString(R.string.notunu_yaz)
        val ipucu = SpannableString("$baslik\n$govde")
        ipucu.setSpan(StyleSpan(Typeface.BOLD), 0, baslik.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ipucu.setSpan(
            AbsoluteSizeSpan(MarkdownBicimci.GOVDE_SP, true),
            baslik.length + 1,
            ipucu.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        metinAlani.hint = ipucu
    }

    /** Enter'a basınca liste/onay/numaralı madde satırını kendiliğinden sürdürür. */
    private fun listeyiSurdur(s: Editable) {
        val imlec = metinAlani.selectionStart
        if (imlec <= 0 || imlec > s.length) return
        val satirSonu = imlec - 1
        if (satirSonu <= 0 || s[satirSonu] != '\n') return
        val satirBasi = s.toString().lastIndexOf('\n', satirSonu - 1) + 1
        if (satirBasi == 0) return // ilk satır başlıktır
        val onceki = s.subSequence(satirBasi, satirSonu).toString()
        val eslesme = MADDE.find(onceki) ?: return
        val onek = eslesme.value
        if (onceki.length == onek.length) {
            s.delete(satirBasi, satirSonu)
            return
        }
        val girinti = eslesme.groupValues[1]
        val numara = eslesme.groupValues[2]
        val yeniOnek = when {
            numara.isNotEmpty() -> girinti + ((numara.toIntOrNull() ?: 0) + 1) + ". "
            onek.contains('[') -> girinti + "- [ ] "
            else -> onek
        }
        s.insert(imlec, yeniOnek)
    }

    // --- Geri al / yinele ---

    private fun geriAl() {
        if (gecmis.isEmpty()) return
        val s = metinAlani.text ?: return
        gelecek.addLast(Durum(s.toString(), metinAlani.selectionStart))
        durumUygula(gecmis.removeLast())
    }

    private fun yinele() {
        if (gelecek.isEmpty()) return
        val s = metinAlani.text ?: return
        gecmis.addLast(Durum(s.toString(), metinAlani.selectionStart))
        durumUygula(gelecek.removeLast())
    }

    private fun durumUygula(durum: Durum) {
        geriAliniyor = true
        metinAlani.setText(durum.metin)
        metinAlani.setSelection(durum.imlec.coerceIn(0, durum.metin.length))
        geriAliniyor = false
        sonDurum = durum
        sonKayit = 0L // sonraki yazım kesinlikle yeni bir adım açsın
        bicimlendir()
        dugmeleriGuncelle()
    }

    private fun dugmeleriGuncelle() {
        btnGeriAl?.let {
            it.isEnabled = gecmis.isNotEmpty()
            it.alpha = if (gecmis.isEmpty()) 0.3f else 1f
        }
        btnYinele?.let {
            it.isEnabled = gelecek.isNotEmpty()
            it.alpha = if (gelecek.isEmpty()) 0.3f else 1f
        }
    }

    // --- Onay kutusuna dokunma ---

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun onayKutusuDokunmaKur() {
        metinAlani.setOnTouchListener { _, olay ->
            if (olay.action != MotionEvent.ACTION_UP) return@setOnTouchListener false
            val duzen = metinAlani.layout ?: return@setOnTouchListener false
            val s = metinAlani.text ?: return@setOnTouchListener false
            val x = olay.x - metinAlani.totalPaddingLeft + metinAlani.scrollX
            val y = olay.y - metinAlani.totalPaddingTop + metinAlani.scrollY
            val satir = duzen.getLineForVertical(y.toInt())
            val satirBasi = duzen.getLineStart(satir)
            val satirSonu = duzen.getLineEnd(satir)
            if (satirBasi >= satirSonu) return@setOnTouchListener false
            val metin = s.subSequence(satirBasi, satirSonu).toString()
            val eslesme = MarkdownBicimci.ONAY.find(metin) ?: return@setOnTouchListener false
            val girinti = eslesme.groupValues[1].length
            val kutuSonu = satirBasi + girinti + MarkdownBicimci.ONAY_UZUNLUGU
            if (kutuSonu > s.length) return@setOnTouchListener false
            if (x > duzen.getPrimaryHorizontal(kutuSonu)) return@setOnTouchListener false

            val isaretIndeksi = satirBasi + girinti + 3
            val isaretli = !eslesme.groupValues[2].equals(" ", true)
            s.replace(isaretIndeksi, isaretIndeksi + 1, if (isaretli) " " else "x")
            true
        }
    }

    // --- Biçim çubuğu ---

    private data class Arac(val ikon: Int, val etiket: Int, val eylem: () -> Unit)

    private fun bicimCubuguKur() {
        val araclar = listOf(
            Arac(R.drawable.ic_geri_al, R.string.geri_al) { geriAl() },
            Arac(R.drawable.ic_yinele, R.string.yinele) { yinele() },
            Arac(R.drawable.ic_bicim_baslik, R.string.bicim_baslik) { onekDegistir("## ") },
            Arac(R.drawable.ic_bicim_kalin, R.string.bicim_kalin) { sarmala("**") },
            Arac(R.drawable.ic_bicim_italik, R.string.bicim_italik) { sarmala("*") },
            Arac(R.drawable.ic_bicim_cizili, R.string.bicim_cizili) { sarmala("~~") },
            Arac(R.drawable.ic_bicim_kod, R.string.bicim_kod) { sarmala("`") },
            Arac(R.drawable.ic_bicim_alinti, R.string.bicim_alinti) { onekDegistir("> ") },
            Arac(R.drawable.ic_bicim_liste, R.string.bicim_liste) { onekDegistir("- ") },
            Arac(R.drawable.ic_bicim_numarali, R.string.bicim_numarali) { onekDegistir("1. ") },
            Arac(R.drawable.ic_bicim_onay, R.string.bicim_onay) { onekDegistir("- [ ] ") },
            Arac(R.drawable.ic_girinti_arti, R.string.girinti_arti) { girintiDegistir(true) },
            Arac(R.drawable.ic_girinti_eksi, R.string.girinti_eksi) { girintiDegistir(false) }
        )

        val y = resources.displayMetrics.density
        val renk = ContextCompat.getColor(this, R.color.metin_ikincil)
        bicimCubugu.removeAllViews()
        for (arac in araclar) {
            val dugme = ImageButton(this)
            dugme.setImageResource(arac.ikon)
            dugme.imageTintList = ColorStateList.valueOf(renk)
            dugme.contentDescription = getString(arac.etiket)
            dugme.setBackgroundResource(android.R.color.transparent)
            dugme.setOnClickListener { arac.eylem() }
            bicimCubugu.addView(
                dugme,
                LinearLayout.LayoutParams((46 * y).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            )
            when (arac.ikon) {
                R.drawable.ic_geri_al -> btnGeriAl = dugme
                R.drawable.ic_yinele -> btnYinele = dugme
            }
        }
    }

    private fun satirSinirlari(): Pair<Int, Int> {
        val s = metinAlani.text ?: return 0 to 0
        val imlec = metinAlani.selectionStart.coerceAtLeast(0)
        val duzMetin = s.toString()
        val bas = if (imlec == 0) 0 else {
            duzMetin.lastIndexOf('\n', imlec - 1).let { if (it < 0) 0 else it + 1 }
        }
        var son = duzMetin.indexOf('\n', bas)
        if (son < 0) son = s.length
        return bas to son
    }

    private fun onekDegistir(onek: String) {
        val s = metinAlani.text ?: return
        val (satirBasi, satirSonu) = satirSinirlari()
        val satir = s.subSequence(satirBasi, satirSonu).toString()
        val girintisiz = satir.trimStart(' ', '\t')
        val girinti = satir.length - girintisiz.length

        val mevcut = ONEKLER.firstOrNull { girintisiz.startsWith(it) }
            ?: NUMARA.find(girintisiz)?.value
        if (mevcut != null) {
            s.delete(satirBasi + girinti, satirBasi + girinti + mevcut.length)
            if (mevcut != onek) s.insert(satirBasi + girinti, onek)
        } else {
            s.insert(satirBasi + girinti, onek)
        }
    }

    private fun girintiDegistir(artir: Boolean) {
        val s = metinAlani.text ?: return
        val (satirBasi, satirSonu) = satirSinirlari()
        if (artir) {
            s.insert(satirBasi, "  ")
            return
        }
        val satir = s.subSequence(satirBasi, satirSonu).toString()
        val silinecek = when {
            satir.startsWith("  ") -> 2
            satir.startsWith(" ") -> 1
            satir.startsWith("\t") -> 1
            else -> 0
        }
        if (silinecek > 0) s.delete(satirBasi, satirBasi + silinecek)
    }

    /**
     * Seçim varsa onu sarar; yoksa imlecin üstündeki kelimeyi sarar. Kelime de
     * yoksa işaretleri koyup imleci aralarına bırakır.
     */
    private fun sarmala(isaret: String) {
        val s = metinAlani.text ?: return
        var bas = metinAlani.selectionStart.coerceAtLeast(0)
        var son = metinAlani.selectionEnd.coerceAtLeast(0)
        if (bas == son) {
            val kelime = imlectekiKelime(s, bas)
            if (kelime != null) {
                bas = kelime.first
                son = kelime.second
            }
        }
        if (bas == son) {
            s.insert(bas, isaret + isaret)
            metinAlani.setSelection(bas + isaret.length)
        } else {
            val ilk = minOf(bas, son)
            val ikinci = maxOf(bas, son)
            s.insert(ikinci, isaret)
            s.insert(ilk, isaret)
            metinAlani.setSelection(ilk + isaret.length, ikinci + isaret.length)
        }
    }

    private fun imlectekiKelime(s: Editable, imlec: Int): Pair<Int, Int>? {
        if (s.isEmpty()) return null
        var bas = imlec.coerceIn(0, s.length)
        var son = bas
        while (bas > 0 && !s[bas - 1].isWhitespace() && s[bas - 1] != '*') bas--
        while (son < s.length && !s[son].isWhitespace() && s[son] != '*') son++
        return if (son > bas) bas to son else null
    }

    // --- Görüntüleme modları ---

    private fun okumaModunuDegistir() {
        okumaModu = !okumaModu
        if (okumaModu) {
            klavyeyiGizle()
            metinAlani.clearFocus()
        }
        metinAlani.isFocusable = !okumaModu
        metinAlani.isFocusableInTouchMode = !okumaModu
        metinAlani.isCursorVisible = !okumaModu
        bicimKaydirici.visibility = if (okumaModu) View.GONE else View.VISIBLE
        btnOkuma.setImageResource(
            if (okumaModu) R.drawable.ic_duzenle else R.drawable.ic_okuma
        )
        btnOkuma.imageTintList = ColorStateList.valueOf(
            if (okumaModu) Renkler.vurgu(this) else ContextCompat.getColor(this, R.color.metin)
        )
        btnOkuma.contentDescription = getString(
            if (okumaModu) R.string.duzenleme_gorunumu else R.string.okuma_gorunumu
        )
        bicimlendir()
    }

    private fun kaynakModunuDegistir() {
        Prefs.kaynakModuKaydet(this, !Prefs.kaynakModu(this))
        bicimci.kaynakModu = Prefs.kaynakModu(this)
        bicimlendir()
    }

    private fun klavyeyiGizle() {
        val yonetici = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        yonetici?.hideSoftInputFromWindow(metinAlani.windowToken, 0)
    }

    // --- Bul ve değiştir ---

    private fun bulCubuguKur() {
        bulCubugu = findViewById(R.id.bulCubugu)
        bulAlani = findViewById(R.id.bulAlani)
        degistirAlani = findViewById(R.id.degistirAlani)
        bulSayac = findViewById(R.id.bulSayac)

        bulAlani.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = eslesmeleriBul(true)
        })

        findViewById<ImageButton>(R.id.bulSonraki).setOnClickListener { eslesmeyeGit(1) }
        findViewById<ImageButton>(R.id.bulOnceki).setOnClickListener { eslesmeyeGit(-1) }
        findViewById<ImageButton>(R.id.bulKapat).setOnClickListener { bulCubuguKapat() }
        findViewById<TextView>(R.id.btnDegistir).setOnClickListener { degistir() }
        findViewById<TextView>(R.id.btnTumunuDegistir).setOnClickListener { tumunuDegistir() }
    }

    private fun bulCubuguAc() {
        bulCubugu.visibility = View.VISIBLE
        bulAlani.requestFocus()
    }

    private fun bulCubuguKapat() {
        bulCubugu.visibility = View.GONE
        eslesmeler = emptyList()
        eslesmeSirasi = -1
        metinAlani.requestFocus()
    }

    private fun eslesmeleriBul(ilkineGit: Boolean) {
        val aranan = bulAlani.text.toString()
        val metin = metinAlani.text?.toString() ?: ""
        if (aranan.isEmpty()) {
            eslesmeler = emptyList()
            eslesmeSirasi = -1
            bulSayac.text = ""
            return
        }
        val kucukMetin = metin.lowercase(TR)
        val kucukAranan = aranan.lowercase(TR)
        val bulunan = mutableListOf<Int>()
        var i = kucukMetin.indexOf(kucukAranan)
        while (i >= 0) {
            bulunan.add(i)
            i = kucukMetin.indexOf(kucukAranan, i + kucukAranan.length)
        }
        eslesmeler = bulunan
        if (bulunan.isEmpty()) {
            eslesmeSirasi = -1
            bulSayac.text = getString(R.string.bulunamadi)
            return
        }
        if (ilkineGit || eslesmeSirasi !in bulunan.indices) eslesmeSirasi = 0
        sayaciGuncelle()
        eslesmeyiSec()
    }

    private fun sayaciGuncelle() {
        bulSayac.text = if (eslesmeler.isEmpty()) {
            getString(R.string.bulunamadi)
        } else {
            "${eslesmeSirasi + 1}/${eslesmeler.size}"
        }
    }

    private fun eslesmeyeGit(yon: Int) {
        if (eslesmeler.isEmpty()) return
        eslesmeSirasi = (eslesmeSirasi + yon + eslesmeler.size) % eslesmeler.size
        sayaciGuncelle()
        eslesmeyiSec()
    }

    private fun eslesmeyiSec() {
        val bas = eslesmeler.getOrNull(eslesmeSirasi) ?: return
        val uzunluk = bulAlani.text.length
        val s = metinAlani.text ?: return
        metinAlani.setSelection(bas.coerceIn(0, s.length), (bas + uzunluk).coerceIn(0, s.length))
    }

    private fun degistir() {
        val bas = eslesmeler.getOrNull(eslesmeSirasi) ?: return
        val aranan = bulAlani.text.toString()
        if (aranan.isEmpty()) return
        val s = metinAlani.text ?: return
        val son = (bas + aranan.length).coerceAtMost(s.length)
        s.replace(bas, son, degistirAlani.text.toString())
        eslesmeleriBul(false)
    }

    private fun tumunuDegistir() {
        val aranan = bulAlani.text.toString()
        if (aranan.isEmpty() || eslesmeler.isEmpty()) return
        val yeni = degistirAlani.text.toString()
        val sayi = eslesmeler.size
        val s = metinAlani.text ?: return
        for (bas in eslesmeler.asReversed()) {
            val son = (bas + aranan.length).coerceAtMost(s.length)
            s.replace(bas, son, yeni)
        }
        Toast.makeText(this, getString(R.string.degistirildi, sayi), Toast.LENGTH_SHORT).show()
        eslesmeleriBul(true)
    }

    // --- Kayıt ve menü ---

    override fun onResume() {
        super.onResume()
        if (bicimci.kaynakModu != Prefs.kaynakModu(this)) {
            bicimci.kaynakModu = Prefs.kaynakModu(this)
            bicimlendir()
        }
    }

    override fun onPause() {
        super.onPause()
        kaydet()
    }

    private fun kaydet() {
        if (silindi) return
        val metin = metinAlani.text.toString()
        if (metin == acilisMetni) return
        val hedef = uri
        acilisMetni = metin
        Thread {
            if (hedef == null) {
                if (metin.isNotBlank()) uri = depo.notOlustur(metin, hedefKlasor)
            } else {
                depo.yaz(hedef, metin)
            }
            NotWidget.hepsiniGuncelle(applicationContext)
        }.start()
    }

    private fun menuGoster() {
        val mevcutUri = uri
        val sayfa = AltSayfa(this).baslik(getString(R.string.not_islemleri))

        if (mevcutUri != null) {
            val sabit = Prefs.sabitler(this).contains(mevcutUri.toString())
            sayfa.madde(
                R.drawable.ic_sabit_24,
                getString(if (sabit) R.string.sabit_kaldir else R.string.sabitle),
                secili = sabit
            ) {
                Prefs.sabitDegistir(this, mevcutUri.toString())
            }
        }

        sayfa.madde(
            R.drawable.ic_kaynak,
            getString(R.string.kaynak_modu),
            secili = Prefs.kaynakModu(this)
        ) { kaynakModunuDegistir() }

        sayfa.madde(R.drawable.ic_ara, getString(R.string.bul_degistir)) { bulCubuguAc() }
        sayfa.madde(R.drawable.ic_paylas, getString(R.string.paylas)) { paylas() }

        if (mevcutUri != null) {
            sayfa.madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) { sil() }
        }
        sayfa.goster()
    }

    private fun paylas() {
        val metin = metinAlani.text.toString()
        if (metin.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, metin)
        startActivity(Intent.createChooser(intent, getString(R.string.paylas)))
    }

    private fun sil() {
        val hedef = uri ?: return
        silindi = true
        Thread {
            val oldu = depo.copeTasi(hedef) != null
            NotWidget.hepsiniGuncelle(applicationContext)
            runOnUiThread {
                if (oldu) Toast.makeText(this, R.string.cope_tasindi, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private companion object {
        const val BIRLESTIRME_MS = 700L
        const val YIGIN_SINIRI = 60
        val TR: Locale = Locale.forLanguageTag("tr-TR")
        val MADDE = Regex("^([ \\t]*)(?:- \\[[ xX]\\] |- |(\\d+)\\. )")
        val NUMARA = Regex("^\\d+\\. ")
        val ONEKLER = listOf("- [ ] ", "- [x] ", "- [X] ", "- ", "### ", "## ", "# ", "> ")
    }
}
