package com.ekosistem.notlar

import android.os.Bundle
import android.widget.Toast

/**
 * Widget'tan görev ekleme: şeffaf ekranda tek satırlık alt sayfa. Görev
 * bugünün notuna eklenir (yoksa oluşturulur); görevler böylece bir güne bağlı
 * kalır ve Görevler ekranında not adıyla görünür.
 */
class GorevEkleActivity : TemelActivity() {

    private var islemde = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema manifestte şeffaf; setTheme ile değiştirilirse arka plan görünmez olur.
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        AltSayfa(this)
            .baslik(getString(R.string.gorev_ekle))
            .girdi(
                ipucu = getString(R.string.gorev_ipucu),
                dugmeMetni = getString(R.string.ekle)
            ) { metin -> if (metin.isNotBlank()) ekle(metin) }
            .kapaninca { if (!islemde) finish() }
            .goster()
    }

    private fun ekle(metin: String) {
        islemde = true
        val uygulama = applicationContext
        NotDeposu.yazici.execute {
            val adres = Sablonlar.bugununNotunaGorevEkle(uygulama, NotDeposu(uygulama), metin)
            NotWidget.hepsiniGuncelle(uygulama)
            runOnUiThread {
                Toast.makeText(
                    uygulama,
                    if (adres != null) R.string.gorev_eklendi else R.string.yedek_hata,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
