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
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class EditorActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var metinAlani: EditText
    private var uri: Uri? = null
    private var hedefKlasor: String? = null
    private var acilisMetni = ""
    private var silindi = false

    private var bicimleniyor = false
    private var satirEklendi = false
    private var vurguRengi = 0
    private var solukRenk = 0
    private var isaretliRenk = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        depo = NotDeposu(this)
        metinAlani = findViewById(R.id.metinAlani)

        vurguRengi = ContextCompat.getColor(this, R.color.vurgu)
        solukRenk = ContextCompat.getColor(this, R.color.metin_ikincil)
        isaretliRenk = ContextCompat.getColor(this, R.color.pill_metin)

        ipucuKur()
        bicimCubuguKur()
        onayKutusuDokunmaKur()

        metinAlani.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, baslangic: Int, onceki: Int, sayi: Int) {
                satirEklendi = s != null && onceki == 0 && sayi == 1 &&
                    baslangic < s.length && s[baslangic] == '\n'
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null || bicimleniyor) return
                bicimleniyor = true
                if (satirEklendi) {
                    satirEklendi = false
                    listeyiSurdur(s)
                }
                bicimlendir(s)
                bicimleniyor = false
            }
        })

        uri = intent.getStringExtra("uri")?.let(Uri::parse)
        hedefKlasor = intent.getStringExtra("klasor")
        val acilacak = uri
        if (acilacak != null) {
            Thread {
                val metin = depo.oku(acilacak)
                runOnUiThread {
                    acilisMetni = metin
                    metinAlani.setText(metin)
                }
            }.start()
        } else {
            metinAlani.requestFocus()
        }

        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnEditorMenu).setOnClickListener { v -> menuGoster(v) }
    }

    // --- Biçimlendirme ---

    /**
     * Alanın kendi boyutu başlık boyutudur (24sp); gövde satırları span ile
     * küçültülür. Böylece imleç her satırda o satırın boyutuyla çizilir ve
     * boş notta ipucuyla aynı hizada durur.
     */
    private fun ipucuKur() {
        val baslik = getString(R.string.baslik_ipucu)
        val govde = getString(R.string.notunu_yaz)
        val ipucu = SpannableString("$baslik\n$govde")
        ipucu.setSpan(StyleSpan(Typeface.BOLD), 0, baslik.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ipucu.setSpan(
            AbsoluteSizeSpan(GOVDE_SP, true),
            baslik.length + 1,
            ipucu.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        metinAlani.hint = ipucu
    }

    private fun bicimlendir(s: Editable) {
        for (span in s.getSpans(0, s.length, AbsoluteSizeSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, StyleSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, OnayKutusuSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, StrikethroughSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, ForegroundColorSpan::class.java)) s.removeSpan(span)
        if (s.isEmpty()) return

        val ilkSonu = s.indexOf('\n')
        val baslikSonu = if (ilkSonu < 0) s.length else ilkSonu
        if (baslikSonu > 0) {
            s.setSpan(StyleSpan(Typeface.BOLD), 0, baslikSonu, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (ilkSonu >= 0 && ilkSonu + 1 < s.length) {
            s.setSpan(
                AbsoluteSizeSpan(GOVDE_SP, true),
                ilkSonu + 1,
                s.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        onayKutulariniBicimle(s)
    }

    private fun onayKutulariniBicimle(s: Editable) {
        var i = 0
        while (i <= s.length) {
            var sonu = s.indexOf('\n', i)
            if (sonu < 0) sonu = s.length
            if (sonu > i) {
                val satir = s.subSequence(i, sonu).toString()
                val eslesme = ONAY_DESENI.find(satir)
                if (eslesme != null) {
                    val girinti = eslesme.groupValues[1].length
                    val isaretli = !eslesme.groupValues[2].equals(" ", true)
                    val kutuBas = i + girinti
                    val kutuSon = kutuBas + ISARET_UZUNLUGU
                    s.setSpan(
                        OnayKutusuSpan(isaretli, vurguRengi, if (isaretli) isaretliRenk else solukRenk),
                        kutuBas,
                        kutuSon,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (isaretli && kutuSon < sonu) {
                        s.setSpan(StrikethroughSpan(), kutuSon, sonu, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(
                            ForegroundColorSpan(solukRenk),
                            kutuSon,
                            sonu,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            if (sonu >= s.length) break
            i = sonu + 1
        }
    }

    /** Enter'a basınca liste/onay kutusu satırını kendiliğinden sürdürür. */
    private fun listeyiSurdur(s: Editable) {
        val imlec = metinAlani.selectionStart
        if (imlec <= 0 || imlec > s.length) return
        val satirSonu = imlec - 1
        if (satirSonu <= 0 || s[satirSonu] != '\n') return
        val satirBasi = s.lastIndexOf("\n", satirSonu - 1) + 1
        if (satirBasi == 0) return // ilk satır başlıktır, liste sürdürülmez
        val onceki = s.subSequence(satirBasi, satirSonu).toString()
        val eslesme = MADDE_DESENI.find(onceki) ?: return
        val onek = eslesme.value
        if (onceki.length == onek.length) {
            // Boş madde: listeyi bitir
            s.delete(satirBasi, satirSonu)
            return
        }
        val yeniOnek = if (onek.contains('[')) {
            eslesme.groupValues[1] + "- [ ] "
        } else {
            onek
        }
        s.insert(imlec, yeniOnek)
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
            val metin = s.subSequence(satirBasi, satirSonu).toString()
            val eslesme = ONAY_DESENI.find(metin) ?: return@setOnTouchListener false
            val girinti = eslesme.groupValues[1].length
            val kutuSonu = satirBasi + girinti + ISARET_UZUNLUGU
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

    /** İmlecin bulunduğu satırın önekini açar/kapatır. */
    private fun onekDegistir(onek: String) {
        val s = metinAlani.text ?: return
        val imlec = metinAlani.selectionStart.coerceAtLeast(0)
        val duzMetin = s.toString()
        val satirBasi = if (imlec == 0) 0 else {
            duzMetin.lastIndexOf('\n', imlec - 1).let { if (it < 0) 0 else it + 1 }
        }
        var satirSonu = duzMetin.indexOf('\n', satirBasi)
        if (satirSonu < 0) satirSonu = s.length
        val satir = s.subSequence(satirBasi, satirSonu).toString()

        val mevcut = ONEKLER.firstOrNull { satir.startsWith(it) }
        if (mevcut != null) {
            s.delete(satirBasi, satirBasi + mevcut.length)
            if (mevcut != onek) s.insert(satirBasi, onek)
        } else {
            s.insert(satirBasi, onek)
        }
    }

    /** Seçimi işaretle sarar; seçim yoksa işaretleri ekleyip arasına geçer. */
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
        menu.menu.add(0, 2, 1, R.string.paylas)
        if (mevcutUri != null) menu.menu.add(0, 3, 2, R.string.sil)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> mevcutUri?.let { Prefs.sabitDegistir(this, it.toString()) }
                2 -> paylas()
                3 -> sil()
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
            val oldu = depo.copeTasi(hedef)
            runOnUiThread {
                if (oldu) Toast.makeText(this, R.string.cope_tasindi, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private companion object {
        const val GOVDE_SP = 16
        const val ISARET_UZUNLUGU = 5 // "- [ ]"
        val ONAY_DESENI = Regex("^([ \\t]*)- \\[([ xX])\\]")
        val MADDE_DESENI = Regex("^([ \\t]*)(?:- \\[[ xX]\\] |- )")
        val ONEKLER = listOf("- [ ] ", "- [x] ", "- [X] ", "- ", "## ", "# ", "### ")
    }
}
