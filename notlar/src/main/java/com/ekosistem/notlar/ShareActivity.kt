package com.ekosistem.notlar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Başka uygulamalardan "Paylaş" ile gönderilen metni nota çevirir. */
class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val metin = intent.getStringExtra(Intent.EXTRA_TEXT)
        val baslik = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (metin.isNullOrBlank()) {
            finish()
            return
        }
        val icerik = if (baslik.isNullOrBlank()) metin else "$baslik\n\n$metin"
        Thread {
            NotDeposu(this).notOlustur(icerik)
            runOnUiThread {
                Toast.makeText(this, R.string.not_kaydedildi, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}
