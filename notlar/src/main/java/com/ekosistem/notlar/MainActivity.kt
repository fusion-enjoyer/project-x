package com.ekosistem.notlar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: TextView
    private var sorgu: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        depo = NotDeposu(this)

        bosDurum = findViewById(R.id.bosDurum)
        adapter = NotAdapter(
            onTikla = { not -> editorAc(not.uri) },
            onUzunBas = { not -> notSecenekleri(not) }
        )

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter

        findViewById<TextView>(R.id.btnYeni).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { v -> menuGoster(v) }

        findViewById<EditText>(R.id.arama).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sorgu = s?.toString()
                yenile()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        yenile()
    }

    private fun yenile() {
        val aktifSorgu = sorgu
        Thread {
            val notlar = try {
                depo.notlariListele(aktifSorgu)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (aktifSorgu != sorgu) return@runOnUiThread
                adapter.guncelle(notlar)
                if (notlar.isEmpty()) {
                    bosDurum.setText(
                        if (sorgu.isNullOrBlank()) R.string.bos_durum else R.string.bos_arama
                    )
                    bosDurum.visibility = View.VISIBLE
                } else {
                    bosDurum.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun editorAc(uri: Uri) {
        startActivity(
            Intent(this, EditorActivity::class.java).putExtra("uri", uri.toString())
        )
    }

    private fun notSecenekleri(not: Not) {
        val sabitEtiket = getString(if (not.sabit) R.string.sabit_kaldir else R.string.sabitle)
        val secenekler = arrayOf(sabitEtiket, getString(R.string.sil))
        AlertDialog.Builder(this)
            .setTitle(not.baslik)
            .setItems(secenekler) { _, hangi ->
                when (hangi) {
                    0 -> {
                        Prefs.sabitDegistir(this, not.uri.toString())
                        yenile()
                    }
                    1 -> Thread {
                        val oldu = depo.copeTasi(not.uri)
                        runOnUiThread {
                            if (oldu) Toast.makeText(this, R.string.cope_tasindi, Toast.LENGTH_SHORT).show()
                            yenile()
                        }
                    }.start()
                }
            }
            .show()
    }

    private fun menuGoster(v: View) {
        val menu = PopupMenu(this, v)
        menu.menu.add(0, 1, 0, R.string.klasor_sec)
        menu.menu.add(0, 2, 1, R.string.cop_kutusu)
        menu.menu.add(0, 3, 2, R.string.tema)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> klasorSec()
                2 -> startActivity(Intent(this, TrashActivity::class.java))
                3 -> temaSec()
            }
            true
        }
        menu.show()
    }

    private fun klasorSec() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ISTEK_KLASOR)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (istek == ISTEK_KLASOR && sonuc == RESULT_OK) {
            val uri = veri?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Prefs.klasorUriKaydet(this, uri.toString())
                yenile()
            } catch (_: Exception) {
            }
        }
    }

    private fun temaSec() {
        val etiketler = arrayOf(
            getString(R.string.tema_sistem),
            getString(R.string.tema_acik),
            getString(R.string.tema_siyah)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.tema)
            .setSingleChoiceItems(etiketler, Prefs.tema(this)) { dialog, hangi ->
                Prefs.temaKaydet(this, hangi)
                Tema.uygula(hangi)
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        private const val ISTEK_KLASOR = 42
    }
}
