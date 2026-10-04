package com.ekosistem.takvim

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.TimeZone

/** Debug: üç widget'ın RemoteViews çıktısını ekranda gösterir (emülatörde launcher'a gerek kalmaz). */
class WidgetOnizlemeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val kok = LinearLayout(this)
        kok.orientation = LinearLayout.VERTICAL
        kok.setPadding((16 * d).toInt(), (40 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        val simdi = System.currentTimeMillis()
        val bugun = Gun.bugun(simdi, TimeZone.getDefault())
        val ornekler = Widgetlar.ornekler(this, bugun, bugun + 13)
        fun ekle(ad: String, g: RemoteViews, w: Int, h: Int) {
            kok.addView(TextView(this).apply { text = ad })
            val kutu = FrameLayout(this)
            kutu.addView(g.apply(this, kutu), FrameLayout.LayoutParams((w * d).toInt(), (h * d).toInt()))
            kok.addView(kutu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val adet = intent.getIntExtra("adet", 4)
        ekle("gundem", GundemWidget.ciz(this, 1, ornekler, bugun, simdi, adet), 320, (adet * 46 + 56))
        ekle("ay", AyWidget.ciz(this, Gun.ayEkle(bugun, intent.getIntExtra("ay", 0)), bugun), 320, 340)
        ekle("siradaki", SiradakiWidget.ciz(this), 170, 70)
        val kaydirici = ScrollView(this)
        kaydirici.addView(kok)
        setContentView(kaydirici)
    }
}
