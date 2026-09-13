package com.ekosistem.notlar

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** "Tek not" widget'ı eklenirken hangi notun gösterileceğini seçtirir. */
class WidgetAyarActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_gorevler)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        findViewById<TextView>(R.id.ekranBasligi).setText(R.string.widget_not_sec)
        findViewById<TextView>(R.id.bosDurum).setText(R.string.bos_durum)
        findViewById<ImageButton>(R.id.btnMenu).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnGeri).setOnClickListener { finish() }

        val adapter = NotAdapter(
            onTikla = { not -> notSecildi(not) },
            onUzunBas = { }
        )
        adapter.vurgu = Renkler.vurgu(this)
        adapter.kartRengi = ContextCompat.getColor(this, R.color.kart)
        adapter.secimRengi = adapter.kartRengi

        val liste = findViewById<RecyclerView>(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter

        Thread {
            val notlar = try {
                NotDeposu(this).notlariListele(null, null)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                adapter.guncelle(notlar)
                findViewById<TextView>(R.id.bosDurum).visibility =
                    if (notlar.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun notSecildi(not: Not) {
        Prefs.widgetNotuKaydet(this, widgetId, not.uri.toString())
        val yonetici = AppWidgetManager.getInstance(this)
        TekNotWidget().onUpdate(this, yonetici, intArrayOf(widgetId))
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}
