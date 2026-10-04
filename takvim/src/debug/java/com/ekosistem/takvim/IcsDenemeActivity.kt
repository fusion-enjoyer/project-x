package com.ekosistem.takvim

import android.app.Activity
import android.os.Bundle
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** Debug: tüm takvimleri uygulamanın dış dosya klasörüne `.ics` yazar (SAF seçici olmadan sınamak için). */
class IcsDenemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Executors.newSingleThreadExecutor().execute {
            val idler = TakvimDeposu.takvimler(this).map { it.id }
            val liste = IcsDeposu.disaAktar(this, idler)
            val dosya = File(getExternalFilesDir(null), "disa.ics")
            dosya.writeText(Ics.yaz(liste, System.currentTimeMillis()), Charsets.UTF_8)
            Log.d("IcsDeneme", "yazildi ${liste.size} -> ${dosya.absolutePath}")
            runOnUiThread { finish() }
        }
    }
}
