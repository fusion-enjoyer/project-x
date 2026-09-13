package com.ekosistem.notlar

import android.content.Intent
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EditorActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var metinAlani: NotEditText
    private lateinit var bicimci: MarkdownBicimci

    private var uri: Uri? = null
    private var hedefKlasor: String? = null
    private var acilisMetni = ""
    private var silindi = false

    private var bicimleniyor = false
    private var satirEklendi = false
    private var aktifSatirBasi = -1

    // Geri al / yinele
    private val gecmis = ArrayDeque<Durum>()
    private val gelecek = ArrayDeque<Durum>()
    private var bekleyen: Durum? = null
    private var sonKayit = 0L
    private var geriAliniyor = false
    private lateinit var btnGeriAl: ImageButton
    private lateinit var btnYinele: ImageButton

    // Bul ve değiştir
    private lateinit var bulCubugu: View
    private lateinit var bulAlani: EditText
    private lateinit var degistirAlani: EditText
    private lateinit var bulSayac: TextView
    private var eslesmeler: List<Int> = emptyList()
    private var eslesmeSirasi = -1

    private data class Durum(val metin: String, val imlec: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        depo = NotDeposu(this)
        bicimci = MarkdownBicimci(this)
        metinAlani = findViewById(R.id.metinAlani)
        btnGeriAl = findViewById(R.id.btnGeriAl)
        btnYinele = findViewById(R.id.btnYinele)

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
                    metinAlani.post { bicimlendir() }
                }
            }.start()
        } else {
            metinAlani.requestFocus()
        }

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditorMenu).setOnClickListener { v -> menuGoster(v) }
        btnGeriAl.setOnClickListener { geriAl() }
        btnYinele.setOnClickListener { yinele() }
        dugmeleriGuncelle()
    }

    private fun vurguRengiUygula() {
        val vurgu = Renkler.vurgu(this)
        findViewById<TextView>(R.id.btnDegistir).setTextColor(vurgu)
        findViewById<TextView>(R.id.btnTumunuDegistir).setTextColor(vurgu)
    }

    // --- Metin değişikliği ---

    private inner class MetinIzleyici : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, bas: Int, onceki: Int, sonraki: Int) {
            if (bicimleniyor || geriAliniyor || s == null) return
            val simdi = System.currentTimeMillis()
            if (simdi - sonKayit > BIRLESTIRME_MS || gecmis.isEmpty()) {
                bekleyen = Durum(s.toString(), metinAlani.selectionStart)
            }
        }

        override fun onTextChanged(s: CharSequence?, bas: Int, onceki: Int, sayi: Int) {
            if (bicimleniyor || geriAliniyor) return
            satirEklendi = s != null && onceki == 0 && sayi == 1 &&
                bas < s.length && s[bas] == '\n'
        }

        override fun afterTextChanged(s: Editable?) {
            if (s == null || bicimleniyor || geriAliniyor) return
            bekleyen?.let {
                gecmis.addLast(it)
                if (gecmis.size > YIGIN_SINIRI) gecmis.removeFirst()
                gelecek.clear()
                sonKayit = System.currentTimeMillis()
                bekleyen = null
                dugmeleriGuncelle()
            }
            bicimleniyor = true
            if (satirEklendi) {
                satirEklendi = false
                listeyiSurdur(s)
            }
            aktifSatirBasi = satirBasiBul(metinAlani.selectionStart)
            bicimci.uygula(s, metinAlani.selectionStart, metinGenisligi())
            bicimleniyor = false
            if (bulCubugu.visibility == View.VISIBLE) eslesmeleriBul(false)
        }
    }

    private fun bicimlendir() {
        val s = metinAlani.text ?: return
        bicimleniyor = true
        bicimci.uygula(s, metinAlani.selectionStart, metinGenisligi())
        bicimleniyor = false
    }

    private fun metinGenisligi(): Int =
        metinAlani.width - metinAlani.totalPaddingLeft - metinAlani.totalPaddingRight

    private fun satirBasiBul(imlec: Int): Int {
        val s = metinAlani.text ?: return 0
        if (imlec <= 0) return 0
        val sinir = minOf(imlec, s.length)
        return s.toString().lastIndexOf('\n', sinir - 1).let { if (it < 0) 0 else it + 1 }
    }

    /**
     * Alanın kendi boyutu başlık boyutudur (24sp); gövde satırları span ile
     * küçültülür. Böylece imleç her satırda o satırın boyutuyla çizilir.
     */
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

    /** Enter'a basınca liste/onay kutusu satırını kendiliğinden sürdürür. */
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
        val yeniOnek = if (onek.contains('[')) eslesme.groupValues[1] + "- [ ] " else onek
        s.insert(imlec, yeniOnek)
    }

    // --- Geri al / yinele ---

    private fun geriAl() {
        if (gecmis.isEmpty()) return
        val s = metinAlani.text ?: return
        val simdiki = Durum(s.toString(), metinAlani.selectionStart)
        val hedef = gecmis.removeLast()
        gelecek.addLast(simdiki)
        durumUygula(hedef)
    }

    private fun yinele() {
        if (gelecek.isEmpty()) return
        val s = metinAlani.text ?: return
        val simdiki = Durum(s.toString(), metinAlani.selectionStart)
        val hedef = gelecek.removeLast()
        gecmis.addLast(simdiki)
        durumUygula(hedef)
    }

    private fun durumUygula(durum: Durum) {
        geriAliniyor = true
        metinAlani.setText(durum.metin)
        metinAlani.setSelection(durum.imlec.coerceIn(0, durum.metin.length))
        geriAliniyor = false
        bicimlendir()
        dugmeleriGuncelle()
    }

    private fun dugmeleriGuncelle() {
        btnGeriAl.isEnabled = gecmis.isNotEmpty()
        btnGeriAl.alpha = if (gecmis.isEmpty()) 0.3f else 1f
        btnYinele.isEnabled = gelecek.isNotEmpty()
        btnYinele.alpha = if (gelecek.isEmpty()) 0.3f else 1f
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

    private fun bicimCubuguKur() {
        findViewById<ImageButton>(R.id.bicimBaslik).setOnClickListener { onekDegistir("## ") }
        findViewById<ImageButton>(R.id.bicimListe).setOnClickListener { onekDegistir("- ") }
        findViewById<ImageButton>(R.id.bicimOnay).setOnClickListener { onekDegistir("- [ ] ") }
        findViewById<ImageButton>(R.id.bicimKalin).setOnClickListener { sarmala("**") }
        findViewById<ImageButton>(R.id.bicimItalik).setOnClickListener { sarmala("*") }
    }

    private fun onekDegistir(onek: String) {
        val s = metinAlani.text ?: return
        val imlec = metinAlani.selectionStart.coerceAtLeast(0)
        val duzMetin = s.toString()
        val satirBasi = if (imlec == 0) 0 else {
            duzMetin.lastIndexOf('\n', imlec - 1).let { if (it < 0) 0 else it + 1 }
        }
        var satirSonu = duzMetin.indexOf('\n', satirBasi)
        if (satirSonu < 0) satirSonu = s.length
        val satir = duzMetin.substring(satirBasi, satirSonu)

        val mevcut = ONEKLER.firstOrNull { satir.startsWith(it) }
        if (mevcut != null) {
            s.delete(satirBasi, satirBasi + mevcut.length)
            if (mevcut != onek) s.insert(satirBasi, onek)
        } else {
            s.insert(satirBasi, onek)
        }
    }

    private fun sarmala(isaret: String) {
        val s = metinAlani.text ?: return
        val bas = metinAlani.selectionStart.coerceAtLeast(0)
        val son = metinAlani.selectionEnd.coerceAtLeast(0)
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
        }.start()
    }

    private fun menuGoster(v: View) {
        val menu = PopupMenu(this, v)
        val mevcutUri = uri
        if (mevcutUri != null) {
            val sabit = Prefs.sabitler(this).contains(mevcutUri.toString())
            menu.menu.add(0, 1, 0, if (sabit) R.string.sabit_kaldir else R.string.sabitle)
        }
        menu.menu.add(0, 4, 1, R.string.bul_degistir)
        menu.menu.add(0, 2, 2, R.string.paylas)
        if (mevcutUri != null) menu.menu.add(0, 3, 3, R.string.sil)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> mevcutUri?.let { Prefs.sabitDegistir(this, it.toString()) }
                2 -> paylas()
                3 -> sil()
                4 -> bulCubuguAc()
            }
            true
        }
        menu.show()
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
        val MADDE = Regex("^([ \\t]*)(?:- \\[[ xX]\\] |- )")
        val ONEKLER = listOf("- [ ] ", "- [x] ", "- [X] ", "- ", "### ", "## ", "# ", "> ")
    }
}
