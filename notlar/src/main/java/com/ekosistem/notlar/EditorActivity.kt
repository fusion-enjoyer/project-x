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
    private lateinit var ustCubuk: View

    /** Üstteki ikonlar şu an görünür mü? (kaydırmayla gizlenip geri gelirler) */
    private var ustCubukAcik = true

    /** Kayıt iş parçacığı yeni notu oluşturunca yazar; sıradaki iş onu görmeli. */
    @Volatile
    private var uri: Uri? = null
    private var hedefKlasor: String? = null
    private var acilisMetni = ""
    private var oncekiIcerik = ""
    private var silindi = false
    private var kilitBekliyor = false

    /** Not içeriği alana yüklendi mi? Yüklenmeden kaydetmek boş metni yazardı. */
    private var yuklendi = false

    /** Kurtarma sorusu yanıtsız kapatıldıysa taslak bir sonraki açılışa kalır. */
    private var taslakYanitBekliyor = false
    private lateinit var taslaklar: Taslaklar
    private val taslakYazici = Runnable { taslagiYaz() }

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
        if (Prefs.ekranGizle(this)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        setContentView(R.layout.activity_editor)
        depo = NotDeposu(this)
        taslaklar = Taslaklar(this)
        bicimci = MarkdownBicimci(this)
        metinAlani = findViewById(R.id.metinAlani)
        bicimCubugu = findViewById(R.id.bicimCubugu)
        bicimKaydirici = findViewById(R.id.bicimKaydirici)
        btnOkuma = findViewById(R.id.btnOkuma)
        ustCubuk = findViewById(R.id.ustCubuk)
        kaydirmaKur()

        bicimci.depo = depo
        // Görsel arka planda çözülünce satır yüksekliği yeniden hesaplanmalı.
        bicimci.gorselHazir = { if (!isFinishing) bicimlendir() }
        tipografiUygula()

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
        when {
            acilacak == null -> {
                yuklendi = true
                metinAlani.requestFocus()
            }
            // Kilitli notun içeriği kilit açılana kadar hiç yüklenmez.
            Kilit.notKilitli(this, acilacak.toString()) -> {
                kilitBekliyor = true
                metinAlani.visibility = View.INVISIBLE
                bicimKaydirici.visibility = View.GONE
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this, KilitActivity::class.java)
                        .putExtra("kip", KilitActivity.KIP_NOT),
                    ISTEK_KILIT
                )
            }
            else -> notuYukle(acilacak)
        }

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditorMenu).setOnClickListener { menuGoster() }
        btnOkuma.setOnClickListener { okumaModunuDegistir() }
        dugmeleriGuncelle()
    }

    /**
     * Aşağı kaydırınca üstteki ikonlar kaçar, yukarı kaydırınca döner —
     * yazarken ekranın tamamı metne kalsın diye (Obsidian'daki davranış).
     */
    private fun kaydirmaKur() {
        metinAlani.kaydirildi = { yeni, onceki ->
            val fark = yeni - onceki
            when {
                // Notun en başındayken çubuk her zaman açık kalır.
                yeni <= 0 -> ustCubuguGoster(true)
                fark > ESIK -> ustCubuguGoster(false)
                fark < -ESIK -> ustCubuguGoster(true)
            }
        }
    }

    private fun ustCubuguGoster(acik: Boolean) {
        if (acik == ustCubukAcik) return
        // Bul çubuğu açıkken üst ikonlar zaten gizli; karışmasınlar.
        if (bulCubugu.visibility == View.VISIBLE) return
        ustCubukAcik = acik
        ustCubuk.animate()
            .translationY(if (acik) 0f else -ustCubuk.height.toFloat() * 1.4f)
            .alpha(if (acik) 1f else 0f)
            .setDuration(160)
            .start()
    }

    /**
     * Taban yazı boyutu GÖVDE boyutudur; başlık span ile büyür. Tersi yapılırsa
     * (eski hâli) imleç taban boyutuna göre çizildiği için gövdede harflerden
     * kocaman bir imleç görünüyordu.
     */
    private fun tipografiUygula() {
        bicimci.boyutlariYenile()
        metinAlani.textSize = bicimci.govdeSp.toFloat()
        metinAlani.typeface = when (Prefs.yaziTipi(this)) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        ipucuKur()
    }

    private fun notuYukle(adres: Uri) {
        Thread {
            val metin = depo.okuKesin(adres)
            val taslak = if (metin != null) taslaklar.oku(adres.toString()) else null
            runOnUiThread {
                if (metin == null) {
                    // Boş not gibi açılsaydı ilk yazılan harf asıl notun üzerine yazılırdı.
                    Toast.makeText(this, R.string.not_acilamadi, Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }
                acilisMetni = metin
                oncekiIcerik = metin
                metniYerlestir(metin)
                yuklendi = true
                when {
                    taslak == null -> {}
                    taslak.metin == metin -> NotDeposu.yazici.execute { taslaklar.sil(adres.toString()) }
                    else -> taslakOner(adres, taslak.metin)
                }
            }
        }.start()
    }

    /** Önceki oturumdan kaydedilememiş değişiklik kaldıysa geri yüklemeyi önerir. */
    private fun taslakOner(adres: Uri, metin: String) {
        taslakYanitBekliyor = true
        AltSayfa(this)
            .baslik(getString(R.string.taslak_bulundu))
            .madde(R.drawable.ic_geri_al, getString(R.string.taslak_geri_yukle)) {
                taslakYanitBekliyor = false
                val s = metinAlani.text ?: return@madde
                gecmis.addLast(Durum(s.toString(), metinAlani.selectionStart))
                gelecek.clear()
                // acilisMetni değişmez: alan artık farklı, ilk çıkışta nota yazılır.
                metniYerlestir(metin)
                dugmeleriGuncelle()
            }
            .madde(R.drawable.ic_sil, getString(R.string.taslak_at), tehlikeli = true) {
                taslakYanitBekliyor = false
                NotDeposu.yazici.execute { taslaklar.sil(adres.toString()) }
            }
            .goster()
    }

    /** Yazma durunca metnin güvenlik kopyasını alır (bkz. [Taslaklar]). */
    private fun taslagiYaz() {
        if (!yuklendi || silindi || kilitBekliyor || isFinishing) return
        val metin = metinAlani.text.toString()
        if (metin == acilisMetni) return
        // Adres yürütme anında okunur: yeni not bu arada oluşturulduysa ona yazılır.
        NotDeposu.yazici.execute { taslaklar.yaz(uri?.toString(), metin) }
    }

    /** Metni geri al yığınını bozmadan alana koyar. */
    private fun metniYerlestir(metin: String) {
        geriAliniyor = true
        metinAlani.setText(metin)
        geriAliniyor = false
        sonDurum = Durum(metin, 0)
        metinAlani.post { bicimlendir() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        when (istek) {
            ISTEK_KILIT -> {
                if (sonuc != RESULT_OK) {
                    finish()
                    return
                }
                kilitBekliyor = false
                metinAlani.visibility = View.VISIBLE
                bicimKaydirici.visibility = if (okumaModu) View.GONE else View.VISIBLE
                uri?.let { notuYukle(it) }
            }
            ISTEK_GECMIS -> {
                if (sonuc != RESULT_OK) return
                val surum = veri?.getStringExtra("surum")?.let(Uri::parse) ?: return
                surumuGeriYukle(surum)
            }
            ISTEK_GORSEL -> {
                if (sonuc != RESULT_OK || veri == null) return
                val secilenler = mutableListOf<Uri>()
                val coklu = veri.clipData
                if (coklu != null) {
                    for (i in 0 until coklu.itemCount) {
                        coklu.getItemAt(i).uri?.let { secilenler.add(it) }
                    }
                } else {
                    veri.data?.let { secilenler.add(it) }
                }
                gorselleriEkle(secilenler)
            }
        }
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

            metinAlani.removeCallbacks(taslakYazici)
            metinAlani.postDelayed(taslakYazici, TASLAK_MS)
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
            AbsoluteSizeSpan(bicimci.govdeSp + 8, true),
            0,
            baslik.length,
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
            if (gorselDokunusunuIsle(olay)) return@setOnTouchListener true
            if (olay.action != MotionEvent.ACTION_UP) return@setOnTouchListener false
            val duzen = metinAlani.layout ?: return@setOnTouchListener false
            val s = metinAlani.text ?: return@setOnTouchListener false
            val x = olay.x - metinAlani.totalPaddingLeft + metinAlani.scrollX
            val y = olay.y - metinAlani.totalPaddingTop + metinAlani.scrollY
            val satir = duzen.getLineForVertical(y.toInt())
            val satirBasi = duzen.getLineStart(satir)
            val satirSonu = duzen.getLineEnd(satir)
            if (satirBasi >= satirSonu) return@setOnTouchListener false
            val dokunulan = duzen.getOffsetForHorizontal(satir, x)
            if (baglantiyaDokunuldu(dokunulan)) return@setOnTouchListener true

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

    /** [[bağlantı]] veya #etikete dokunulduysa işler; değilse false döner. */
    private fun baglantiyaDokunuldu(konum: Int): Boolean {
        val s = metinAlani.text ?: return false
        val metin = s.toString()
        for (m in MarkdownBicimci.BAGLANTI.findAll(metin)) {
            if (konum in m.range) {
                baglantiyiAc(m.groupValues[1].trim())
                return true
            }
        }
        for (m in MarkdownBicimci.ETIKET.findAll(metin)) {
            if (konum in m.range) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra("etiket", m.groupValues[1])
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                return true
            }
        }
        return false
    }

    /** Bağlantı hedefi varsa açar, yoksa o başlıkla yeni not oluşturur. */
    private fun baglantiyiAc(baslik: String) {
        if (baslik.isEmpty()) return
        kaydet()
        Thread {
            val hedef = depo.baslikIleBul(baslik)
            val adres = hedef?.uri ?: depo.notOlustur("$baslik\n", hedefKlasor)
            runOnUiThread {
                if (adres != null) {
                    startActivity(
                        Intent(this, EditorActivity::class.java).putExtra("uri", adres.toString())
                    )
                }
            }
        }.start()
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
            Arac(R.drawable.ic_gorsel, R.string.gorsel_ekle) { gorselSec() },
            Arac(R.drawable.ic_etiket, R.string.etiket_ekle) { etiketEkle() },
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

    // --- Görsel ekleme ---

    private fun gorselSec() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        Kilit.sistemAraciBekleniyor = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_GORSEL)
    }

    /**
     * Seçilen görseller `ekler/` klasörüne kopyalanır ve nota Markdown bağlantısı
     * yazılır. Kopyalama sırasında editör beklemez; bittiğinde satır eklenir.
     */
    private fun gorselleriEkle(secilenler: List<Uri>) {
        if (secilenler.isEmpty()) return
        val mevcutUri = uri
        Thread {
            val klasor = hedefKlasor ?: mevcutUri?.let { depo.notunKlasoru(it) }
            val yollar = secilenler.mapNotNull { Gorseller.iceAl(this, depo, it, klasor) }
            runOnUiThread {
                if (yollar.isEmpty()) {
                    Toast.makeText(this, R.string.gorsel_hata, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                yollar.forEach { gorselSatiriEkle(it) }
            }
        }.start()
    }

    private fun gorselSatiriEkle(yol: String) {
        val s = metinAlani.text ?: return
        var konum = metinAlani.selectionEnd.coerceIn(0, s.length)
        // Görsel kendi satırında dursun; satır ortasındaysa önce alt satıra geç.
        val onek = if (konum == 0 || s[konum - 1] == '\n') "" else "\n"
        val metin = "$onek![]($yol)\n"
        s.insert(konum, metin)
        konum += metin.length
        metinAlani.setSelection(konum.coerceIn(0, s.length))
    }

    // --- Etiket ---

    /**
     * Etiket `#ad` olarak notun içine yazılır — ayrı bir alan yok, dosya düz
     * Markdown kalsın diye. Var olan etiketler listelenir ki yazım tutarlı olsun.
     */
    private fun etiketEkle() {
        Thread {
            val etiketler = depo.etiketleriListele()
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.etiket_ekle))
                sayfa.girdi(
                    ipucu = getString(R.string.yeni_etiket),
                    dugmeMetni = getString(R.string.ekle)
                ) { ad -> etiketiYaz(ad) }
                for (etiket in etiketler) {
                    sayfa.madde(R.drawable.ic_etiket, "#$etiket") { etiketiYaz(etiket) }
                }
                sayfa.goster()
            }
        }.start()
    }

    private fun etiketiYaz(ham: String) {
        val temiz = ham.trim().trimStart('#').replace(Regex("[^\\p{L}\\p{N}_-]"), "").take(40)
        if (temiz.isEmpty()) return
        val s = metinAlani.text ?: return
        val konum = metinAlani.selectionEnd.coerceIn(0, s.length)
        val onek = if (konum == 0 || s[konum - 1].isWhitespace()) "" else " "
        val metin = "$onek#$temiz "
        s.insert(konum, metin)
        metinAlani.setSelection((konum + metin.length).coerceIn(0, s.length))
    }

    // --- Görsele dokunma ---

    /*
     * Görsel etkileşimi (metin seçimindeki gibi):
     *  - tek dokunuş  → görselin üstünde küçük "Düzenle" balonu
     *  - basılı tut   → titreşim; parmak kalkarsa menü açılır
     *  - basılı tutup sürükle → görsel satırı parmakla taşınır, imleç hedefi gösterir
     */
    private var gorselBasi = -1
    private var gorselBasilmaZamani = 0L
    private var gorselIlkY = 0f
    private var gorselSonY = 0f
    private var gorselSurukluyor = false
    private var gorselKaydiriyor = false
    private var gorselUzunBasti = false
    private val gorselUzunBasma = Runnable {
        gorselUzunBasti = true
        metinAlani.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }
    private var duzenleBalonu: android.widget.PopupWindow? = null

    private fun gorselKonumda(olay: MotionEvent): Int {
        val duzen = metinAlani.layout ?: return -1
        val s = metinAlani.text ?: return -1
        val x = olay.x - metinAlani.totalPaddingLeft + metinAlani.scrollX
        val y = olay.y - metinAlani.totalPaddingTop + metinAlani.scrollY
        val satir = duzen.getLineForVertical(y.toInt())
        val konum = duzen.getOffsetForHorizontal(satir, x)
        val span = s.getSpans(konum, konum, GorselSpan::class.java).firstOrNull() ?: return -1
        return s.getSpanStart(span)
    }

    private fun gorselDokunusunuIsle(olay: MotionEvent): Boolean {
        when (olay.action) {
            MotionEvent.ACTION_DOWN -> {
                val bas = gorselKonumda(olay)
                if (bas < 0) return false
                gorselBasi = bas
                gorselIlkY = olay.y
                gorselSonY = olay.y
                gorselSurukluyor = false
                gorselKaydiriyor = false
                gorselUzunBasti = false
                gorselBasilmaZamani = System.currentTimeMillis()
                metinAlani.postDelayed(gorselUzunBasma, UZUN_BASMA_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gorselBasi < 0) return false
                val kayma = Math.abs(olay.y - gorselIlkY)
                if (gorselUzunBasti && kayma > SURUKLEME_ESIGI) gorselSurukluyor = true
                if (gorselSurukluyor) {
                    surukleImleciGoster(olay)
                } else if (!gorselUzunBasti) {
                    /*
                     * Uzun basmadan parmak kaydıysa bu bir sayfa kaydırmasıdır.
                     * Dokunuşu DOWN'da biz yuttuğumuz için EditText kaydıramaz;
                     * kaydırmayı kendimiz yapıyoruz, yoksa geniş bir görselin
                     * üstünden not kaydırılamazdı.
                     */
                    if (!gorselKaydiriyor && kayma > SURUKLEME_ESIGI) {
                        gorselKaydiriyor = true
                        metinAlani.removeCallbacks(gorselUzunBasma)
                    }
                    if (gorselKaydiriyor) {
                        val fark = (gorselSonY - olay.y).toInt()
                        val enFazla = (metinAlani.layout?.height ?: 0) -
                            (metinAlani.height - metinAlani.totalPaddingTop -
                                metinAlani.totalPaddingBottom)
                        val hedefScroll = (metinAlani.scrollY + fark)
                            .coerceIn(0, maxOf(0, enFazla))
                        metinAlani.scrollTo(0, hedefScroll)
                    }
                }
                gorselSonY = olay.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (gorselBasi < 0) return false
                metinAlani.removeCallbacks(gorselUzunBasma)
                val bas = gorselBasi
                gorselBasi = -1
                when {
                    gorselKaydiriyor -> Unit // sayfa kaydırıldı; eylem yok
                    gorselSurukluyor -> gorseliHedefeTasi(bas, olay)
                    gorselUzunBasti -> gorselMenusu(bas)
                    else -> duzenleBalonuGoster(bas)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (gorselBasi < 0) return false
                metinAlani.removeCallbacks(gorselUzunBasma)
                gorselBasi = -1
                return true
            }
        }
        return false
    }

    /** Sürükleme sırasında hedef satırı imleçle işaretler. */
    private fun surukleImleciGoster(olay: MotionEvent) {
        val hedef = hedefKonum(olay) ?: return
        metinAlani.setSelection(hedef.coerceIn(0, metinAlani.text?.length ?: 0))
    }

    private fun hedefKonum(olay: MotionEvent): Int? {
        val duzen = metinAlani.layout ?: return null
        val y = olay.y - metinAlani.totalPaddingTop + metinAlani.scrollY
        val satir = duzen.getLineForVertical(y.toInt())
        return duzen.getLineStart(satir)
    }

    /** Sürükleme bitti: görselin satırını parmağın bıraktığı satıra taşır. */
    private fun gorseliHedefeTasi(gorselKonumu: Int, olay: MotionEvent) {
        val s = metinAlani.text ?: return
        val metin = s.toString()
        val kaynakBas = satirBasi(metin, gorselKonumu)
        val kaynakSon = satirSonu(metin, kaynakBas)
        val satir = metin.substring(kaynakBas, kaynakSon)

        var hedef = hedefKonum(olay) ?: return
        hedef = satirBasi(metin, hedef.coerceIn(0, metin.length))
        if (hedef in kaynakBas..kaynakSon) return

        if (hedef < kaynakBas) {
            // Önce kaynağı sil (sondaki \n ile), sonra hedefe ekle.
            val silSonu = if (kaynakSon < metin.length) kaynakSon + 1 else kaynakSon
            s.delete(kaynakBas, silSonu)
            s.insert(hedef, satir + "\n")
            metinAlani.setSelection(hedef.coerceIn(0, s.length))
        } else {
            s.insert(hedef, satir + "\n")
            val silSonu = if (kaynakSon < metin.length) kaynakSon + 1 else kaynakSon
            s.delete(kaynakBas, silSonu)
            metinAlani.setSelection((hedef - (silSonu - kaynakBas)).coerceIn(0, s.length))
        }
    }

    /** Tek dokunuşta çıkan küçük balon: metin seçim araç çubuğu görünümünde. */
    private fun duzenleBalonuGoster(bas: Int) {
        duzenleBalonu?.dismiss()
        val duzen = metinAlani.layout ?: return
        val s = metinAlani.text ?: return

        val dugme = TextView(this)
        dugme.text = getString(R.string.duzenle)
        dugme.textSize = 14f
        dugme.setTypeface(null, Typeface.BOLD)
        dugme.setTextColor(ContextCompat.getColor(this, R.color.metin))
        dugme.setBackgroundResource(R.drawable.bg_ucan_hap)
        val yog = resources.displayMetrics.density
        dugme.setPadding((16 * yog).toInt(), (10 * yog).toInt(), (16 * yog).toInt(), (10 * yog).toInt())

        val balon = android.widget.PopupWindow(
            dugme,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        balon.isOutsideTouchable = true
        balon.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dugme.setOnClickListener {
            balon.dismiss()
            gorselMenusu(bas)
        }

        // Balonu görsel satırının üstüne yerleştir.
        val satirNo = duzen.getLineForOffset(bas)
        val satirUstu = duzen.getLineTop(satirNo)
        val ekranY = satirUstu + metinAlani.totalPaddingTop - metinAlani.scrollY
        val yer = IntArray(2)
        metinAlani.getLocationInWindow(yer)
        balon.showAtLocation(
            metinAlani,
            android.view.Gravity.NO_GRAVITY,
            yer[0] + (20 * yog).toInt(),
            yer[1] + ekranY - (52 * yog).toInt()
        )
        duzenleBalonu = balon
    }

    private fun gorselMenusu(bas: Int) {
        AltSayfa(this)
            .baslik(getString(R.string.gorsel))
            .madde(R.drawable.ic_geri_al, getString(R.string.yukari_tasi)) {
                satiriTasi(bas, true)
            }
            .madde(R.drawable.ic_yinele, getString(R.string.asagi_tasi)) {
                satiriTasi(bas, false)
            }
            .madde(R.drawable.ic_sil, getString(R.string.gorseli_kaldir), tehlikeli = true) {
                satiriSil(bas)
            }
            .goster()
    }

    /** [konum]'un bulunduğu satırı bir üstteki/alttaki satırla yer değiştirir. */
    private fun satiriTasi(konum: Int, yukari: Boolean) {
        val s = metinAlani.text ?: return
        val metin = s.toString()
        val bas = satirBasi(metin, konum)
        val son = satirSonu(metin, bas)
        val satir = metin.substring(bas, son)

        if (yukari) {
            if (bas == 0) return
            val oncekiBas = satirBasi(metin, bas - 1)
            val onceki = metin.substring(oncekiBas, bas - 1)
            s.replace(oncekiBas, son, "$satir\n$onceki")
            metinAlani.setSelection(oncekiBas.coerceIn(0, s.length))
        } else {
            if (son >= metin.length) return
            val sonrakiSon = satirSonu(metin, son + 1)
            val sonraki = metin.substring(son + 1, sonrakiSon)
            s.replace(bas, sonrakiSon, "$sonraki\n$satir")
            metinAlani.setSelection((bas + sonraki.length + 1).coerceIn(0, s.length))
        }
    }

    private fun satiriSil(konum: Int) {
        val s = metinAlani.text ?: return
        val metin = s.toString()
        val bas = satirBasi(metin, konum)
        val son = satirSonu(metin, bas)
        // Satır sonundaki yeni satır karakteri de gitsin, boş satır kalmasın.
        val bitis = if (son < metin.length) son + 1 else son
        s.delete(bas, bitis)
        metinAlani.setSelection(bas.coerceIn(0, s.length))
    }

    private fun satirBasi(metin: String, konum: Int): Int {
        if (konum <= 0) return 0
        val i = metin.lastIndexOf('\n', (konum - 1).coerceAtMost(metin.length - 1))
        return if (i < 0) 0 else i + 1
    }

    private fun satirSonu(metin: String, bas: Int): Int {
        val i = metin.indexOf('\n', bas)
        return if (i < 0) metin.length else i
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
        // Bul çubuğu üst ikonların yerini alır; ikisi üst üste binmesin.
        ustCubuk.visibility = View.GONE
        bulAlani.requestFocus()
    }

    private fun bulCubuguKapat() {
        bulCubugu.visibility = View.GONE
        ustCubuk.visibility = View.VISIBLE
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
        var degisti = false
        if (bicimci.kaynakModu != Prefs.kaynakModu(this)) {
            bicimci.kaynakModu = Prefs.kaynakModu(this)
            degisti = true
        }
        if (bicimci.govdeSp != Prefs.yaziBoyu(this)) degisti = true
        tipografiUygula()
        if (degisti) bicimlendir()
    }

    override fun onPause() {
        super.onPause()
        kaydet()
    }

    /**
     * Notu arka planda kaydeder. İş [NotDeposu.yazici] sırasına girer, böylece
     * arkasından gelen "taşı" ya da ikinci bir kayıt bunun bitmesini bekler.
     * Metin önce taslağa yazılır; asıl dosya yazılamazsa taslak kalır ve not
     * bir sonraki açılışta kurtarılabilir.
     */
    private fun kaydet() {
        metinAlani.removeCallbacks(taslakYazici)
        if (silindi || kilitBekliyor || !yuklendi) return
        val metin = metinAlani.text.toString()
        if (metin == acilisMetni) {
            // Yazılıp ilk hale geri dönüldüyse eski taslak boşuna "geri yükle" sordurmasın.
            // Yeni notun taslağına dokunulmaz: kurtarılmayı bekleyen bir not olabilir.
            if (!taslakYanitBekliyor) {
                uri?.let { adres -> NotDeposu.yazici.execute { taslaklar.sil(adres.toString()) } }
            }
            return
        }
        val oncekiAcilis = acilisMetni
        acilisMetni = metin
        val onceki = oncekiIcerik
        oncekiIcerik = metin
        val klasor = hedefKlasor
        val uygulama = applicationContext
        val kayit = NotDeposu.yazici.submit {
            // Adres yürütme anında okunur: önceki kayıt yeni notu oluşturduysa
            // ikinci bir kopya açılmaz, aynı nota yazılır.
            val hedef = uri
            val adres = hedef?.toString()
            if (metin.isNotBlank()) taslaklar.yaz(adres, metin)
            val tamam = if (hedef == null) {
                if (metin.isBlank()) {
                    true
                } else {
                    val yeni = depo.notOlustur(metin, klasor)
                    if (yeni != null) uri = yeni
                    yeni != null
                }
            } else {
                if (onceki.isNotBlank()) depo.gecmiseYaz(hedef, onceki)
                depo.yaz(hedef, metin)
            }
            if (tamam) {
                taslaklar.sil(adres)
            } else {
                runOnUiThread {
                    // Bir sonraki çıkışta yeniden denensin.
                    if (acilisMetni == metin) acilisMetni = oncekiAcilis
                    Toast.makeText(uygulama, R.string.kayit_hatasi, Toast.LENGTH_LONG).show()
                }
            }
            NotWidget.hepsiniGuncelle(uygulama)
        }
        NotDeposu.bekleyenKayit = kayit
    }

    private fun menuGoster() {
        val mevcutUri = uri
        val metin = metinAlani.text?.toString().orEmpty()
        val kelime = metin.split(Regex("\\s+")).count { it.isNotBlank() }
        val sayfa = AltSayfa(this).baslik(
            getString(R.string.kelime_karakter, kelime, metin.length)
        )

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

        if (mevcutUri != null) {
            val zaman = Prefs.hatirlatici(this, mevcutUri.toString())
            sayfa.madde(
                R.drawable.ic_hatirlatici,
                if (zaman > 0) getString(R.string.hatirlatici_kaldir) else getString(R.string.hatirlatici_kur),
                secili = zaman > 0
            ) {
                if (zaman > 0) {
                    Hatirlatici.kaldir(this, mevcutUri.toString())
                    Toast.makeText(this, R.string.hatirlatici_kaldirildi, Toast.LENGTH_SHORT).show()
                } else {
                    hatirlaticiSec(mevcutUri.toString())
                }
            }

            sayfa.madde(
                R.drawable.ic_kilit,
                getString(if (Kilit.notKilitli(this, mevcutUri.toString())) R.string.kilidi_kaldir else R.string.nota_kilit),
                secili = Kilit.notKilitli(this, mevcutUri.toString())
            ) { notKilidiDegistir(mevcutUri.toString()) }

            sayfa.madde(R.drawable.ic_baglanti, getString(R.string.geri_baglantilar)) {
                geriBaglantilariGoster()
            }
            sayfa.madde(R.drawable.ic_gecmis, getString(R.string.gecmis)) { gecmisiAc() }
        }
        if (mevcutUri != null) {
            sayfa.madde(R.drawable.ic_tasi, getString(R.string.klasore_tasi)) {
                klasoreTasiSec(mevcutUri)
            }
            sayfa.madde(R.drawable.ic_arti_koyu, getString(R.string.notu_cogalt)) {
                notuCogalt()
            }
        }
        sayfa.madde(R.drawable.ic_sablon, getString(R.string.sablon_olarak_kaydet)) {
            sablonOlarakKaydet()
        }
        sayfa.madde(R.drawable.ic_ara, getString(R.string.bul_degistir)) { bulCubuguAc() }
        sayfa.madde(R.drawable.ic_paylas, getString(R.string.paylas)) { paylas() }

        if (mevcutUri != null) {
            sayfa.madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) { sil() }
        }
        sayfa.goster()
    }

    // --- Hatırlatıcı, kilit, bağlantı, geçmiş ---

    private fun hatirlaticiSec(adres: String) {
        val takvim = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            this,
            { _, yil, ay, gun ->
                android.app.TimePickerDialog(
                    this,
                    { _, saat, dakika ->
                        takvim.set(yil, ay, gun, saat, dakika, 0)
                        if (takvim.timeInMillis <= System.currentTimeMillis()) {
                            Toast.makeText(this, R.string.gecmis_zaman, Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                        bildirimIzniIste()
                        Hatirlatici.kur(this, adres, takvim.timeInMillis)
                        Toast.makeText(this, R.string.hatirlatici_kuruldu, Toast.LENGTH_SHORT).show()
                    },
                    takvim.get(java.util.Calendar.HOUR_OF_DAY),
                    takvim.get(java.util.Calendar.MINUTE),
                    true
                ).show()
            },
            takvim.get(java.util.Calendar.YEAR),
            takvim.get(java.util.Calendar.MONTH),
            takvim.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun bildirimIzniIste() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    private fun notKilidiDegistir(adres: String) {
        if (!Kilit.kurulu(this)) {
            Toast.makeText(this, R.string.once_pin_kur, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, AyarlarActivity::class.java))
            return
        }
        if (Kilit.notKilitli(this, adres)) {
            Kilit.notKilidiDegistir(this, adres)
            Toast.makeText(this, R.string.not_kilidi_acildi, Toast.LENGTH_SHORT).show()
            return
        }
        // Kilidin ne yaptığı önce anlatılır: not şifrelenmez, sadece gizlenir.
        AltSayfa(this)
            .baslik(getString(R.string.not_kilit_ozet))
            .madde(R.drawable.ic_kilit, getString(R.string.nota_kilit)) {
                Kilit.notKilidiDegistir(this, adres)
                Toast.makeText(this, R.string.not_kilitlendi, Toast.LENGTH_SHORT).show()
            }
            .goster()
    }

    private fun geriBaglantilariGoster() {
        val mevcut = uri ?: return
        Thread {
            val hepsi = depo.notlariListele(null, null)
            val not = hepsi.firstOrNull { it.uri == mevcut }
            val baglar = if (not != null) depo.geriBaglantilar(not) else emptyList()
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.geri_baglantilar))
                if (baglar.isEmpty()) {
                    sayfa.madde(R.drawable.ic_baglanti, getString(R.string.baglanti_yok)) {}
                } else {
                    for (b in baglar) {
                        sayfa.madde(R.drawable.ic_baglanti, b.baslik) {
                            startActivity(
                                Intent(this, EditorActivity::class.java)
                                    .putExtra("uri", b.uri.toString())
                            )
                        }
                    }
                }
                sayfa.goster()
            }
        }.start()
    }

    /**
     * Sürüm geçmişi ayrı bir ekranda açılır: önce fark gösterilir, geri dönmeyi
     * kullanıcı onaylar. Ekrandaki güncel metin karşılaştırma için devredilir.
     */
    private fun gecmisiAc() {
        val mevcut = uri ?: return
        GecmisActivity.gecerliMetin = metinAlani.text?.toString() ?: ""
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, GecmisActivity::class.java).putExtra("uri", mevcut.toString()),
            ISTEK_GECMIS
        )
    }

    /** Geri yükleme geri al yığınına düşer; yanlışlıkla dönülürse kurtarılabilir. */
    private fun surumuGeriYukle(surumUri: Uri) {
        Thread {
            val eski = depo.oku(surumUri)
            runOnUiThread {
                if (eski.isBlank()) return@runOnUiThread
                val s = metinAlani.text ?: return@runOnUiThread
                gecmis.addLast(Durum(s.toString(), metinAlani.selectionStart))
                gelecek.clear()
                metniYerlestir(eski)
                dugmeleriGuncelle()
                Toast.makeText(this, R.string.gecmise_donuldu, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    /** Notu editörden ayrılmadan başka klasöre taşır; adres değişir. */
    private fun klasoreTasiSec(mevcut: Uri) {
        Thread {
            val klasorler = depo.klasorAdlari().filter { it != Sablonlar.KLASOR }
            val simdiki = depo.notunKlasoru(mevcut)
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.klasore_tasi))
                sayfa.madde(
                    R.drawable.ic_tasi,
                    getString(R.string.ana_klasor),
                    secili = simdiki == null
                ) { klasoreTasi(mevcut, null) }
                for (klasor in klasorler) {
                    sayfa.madde(R.drawable.ic_tasi, klasor, secili = simdiki == klasor) {
                        klasoreTasi(mevcut, klasor)
                    }
                }
                sayfa.goster()
            }
        }.start()
    }

    private fun klasoreTasi(mevcut: Uri, klasor: String?) {
        kaydet()
        // Kayıtla aynı sırada: taşıma, son yazılanlar dosyaya geçtikten sonra yapılır.
        NotDeposu.yazici.execute {
            val yeni = depo.klasoreTasi(uri ?: mevcut, klasor)
            // Sıradaki kayıt yeni adrese yazsın diye burada, iş parçacığında atanır.
            if (yeni != null) uri = yeni
            runOnUiThread {
                val mesaj = if (yeni != null) R.string.tasindi else R.string.yedek_hata
                Toast.makeText(this, mesaj, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun notuCogalt() {
        val metin = metinAlani.text?.toString().orEmpty()
        if (metin.isBlank()) return
        val mevcut = uri
        Thread {
            val klasor = mevcut?.let { depo.notunKlasoru(it) }
            val yeni = depo.notOlustur(metin, klasor)
            runOnUiThread {
                if (yeni != null) {
                    startActivity(
                        Intent(this, EditorActivity::class.java).putExtra("uri", yeni.toString())
                    )
                } else {
                    Toast.makeText(this, R.string.yedek_hata, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Açık notu olduğu gibi şablon klasörüne kopyalar. */
    private fun sablonOlarakKaydet() {
        val metin = metinAlani.text.toString()
        if (metin.isBlank()) return
        Thread {
            val adres = Sablonlar.kaydet(depo, metin)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (adres != null) R.string.sablon_kaydedildi else R.string.sablon_hata,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun paylas() {
        val metin = metinAlani.text.toString()
        if (metin.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, metin)
        Kilit.sistemAraciBekleniyor = true
        startActivity(Intent.createChooser(intent, getString(R.string.paylas)))
    }

    private fun sil() {
        if (uri == null) return
        silindi = true
        metinAlani.removeCallbacks(taslakYazici)
        NotDeposu.yazici.execute {
            val hedef = uri ?: return@execute
            taslaklar.sil(hedef.toString())
            val oldu = depo.copeTasi(hedef) != null
            NotWidget.hepsiniGuncelle(applicationContext)
            runOnUiThread {
                if (oldu) Toast.makeText(this, R.string.cope_tasindi, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private companion object {
        const val ISTEK_KILIT = 9
        const val ISTEK_GECMIS = 10
        const val ISTEK_GORSEL = 11

        /** Çubuğu gizleyip göstermek için gereken en küçük kaydırma (piksel). */
        const val ESIK = 12

        /** Görselde uzun basma süresi ve sürükleme eşiği. */
        const val UZUN_BASMA_MS = 420L
        const val SURUKLEME_ESIGI = 24f
        const val BIRLESTIRME_MS = 700L

        /** Yazma bu kadar durunca taslak alınır. */
        const val TASLAK_MS = 1500L
        const val YIGIN_SINIRI = 60
        val TR: Locale = Locale.forLanguageTag("tr-TR")
        val MADDE = Regex("^([ \\t]*)(?:- \\[[ xX]\\] |- |(\\d+)\\. )")
        val NUMARA = Regex("^\\d+\\. ")
        val ONEKLER = listOf("- [ ] ", "- [x] ", "- [X] ", "- ", "### ", "## ", "# ", "> ")
    }
}
