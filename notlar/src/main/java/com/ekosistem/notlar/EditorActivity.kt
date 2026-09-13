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
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EditorActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var metinAlani: EditText
    private var uri: Uri? = null
    private var hedefKlasor: String? = null
    private var acilisMetni = ""
    private var silindi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        depo = NotDeposu(this)
        metinAlani = findViewById(R.id.metinAlani)

        ipucuKur()
        metinAlani.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.let { basligiBicimle(it) }
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

    /** Boş editörde ipucunu gerçek görünümle eşleştirir: büyük başlık + normal metin. */
    private fun ipucuKur() {
        val baslik = getString(R.string.baslik_ipucu)
        val govde = getString(R.string.notunu_yaz)
        val ipucu = SpannableString("$baslik\n$govde")
        ipucu.setSpan(AbsoluteSizeSpan(24, true), 0, baslik.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ipucu.setSpan(StyleSpan(Typeface.BOLD), 0, baslik.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        metinAlani.hint = ipucu
    }

    /** İlk satırı canlı olarak başlık gibi gösterir (24sp, kalın). */
    private fun basligiBicimle(s: Editable) {
        for (span in s.getSpans(0, s.length, AbsoluteSizeSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, StyleSpan::class.java)) s.removeSpan(span)
        if (s.isEmpty()) return
        var son = s.indexOf('\n')
        if (son < 0) son = s.length
        if (son > 0) {
            s.setSpan(AbsoluteSizeSpan(24, true), 0, son, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(StyleSpan(Typeface.BOLD), 0, son, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
}
