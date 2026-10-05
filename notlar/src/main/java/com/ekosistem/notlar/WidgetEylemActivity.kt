package com.ekosistem.notlar

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Widget listelerindeki dokunuşların aracısı; ekranda hiçbir şey göstermez
 * (Theme.NoDisplay). Liste satırları tek bir şablon niyet paylaşır, satır
 * farkı doldurma niyetiyle gelir: ya görev işaretlenir ya da not açılır.
 *
 * Neden etkinlik: arka planda alınan bir yayından ekran açmak yeni
 * Android'lerde kısıtlı; etkinlik ise Android 5'ten beri aynı çalışır.
 * NoDisplay olduğu için AppCompat değil, düz [Activity]; kilit ekranı da
 * bunun için açılmaz (bkz. NotlarApp), not açılırsa editör açarken sorar.
 */
class WidgetEylemActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adres = intent.getStringExtra(EK_ADRES)
        when (intent.getStringExtra(EK_EYLEM)) {
            EYLEM_GOREV -> if (adres != null) gorevDegistir(adres)
            EYLEM_AC -> if (adres != null) {
                startActivity(NotWidget.notNiyeti(this, adres).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    private fun gorevDegistir(adres: String) {
        val gorev = Gorev(
            notUri = Uri.parse(adres),
            notBasligi = "",
            satirNo = intent.getIntExtra(EK_SATIR, -1),
            metin = intent.getStringExtra(EK_METIN) ?: return,
            isaretli = false
        )
        val uygulama = applicationContext
        NotDeposu.yazici.execute {
            NotDeposu(uygulama).gorevDegistir(gorev, metniDogrula = true)
            // Değişmese bile tazelenir: widget eskiyse doğru hali görünsün.
            NotWidget.hepsiniGuncelle(uygulama)
        }
    }

    companion object {
        const val EK_EYLEM = "widget_eylem"
        const val EK_ADRES = "widget_adres"
        const val EK_SATIR = "widget_satir"
        const val EK_METIN = "widget_metin"
        const val EYLEM_GOREV = "gorev"
        const val EYLEM_AC = "ac"

        /** Liste widget'larının ortak şablon niyeti; satırlar doldurma niyetiyle ayrışır. */
        fun sablon(context: Context, istek: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                istek,
                Intent(context, WidgetEylemActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                NotWidget.bayrak(true)
            )

        fun acDoldurma(adres: String): Intent =
            Intent().putExtra(EK_EYLEM, EYLEM_AC).putExtra(EK_ADRES, adres)
                // Her satırın niyeti ayrı kalsın (aynı görünürlerse sistem birleştirir).
                .setData(Uri.parse("notlar-widget://ac/" + Uri.encode(adres)))

        fun gorevDoldurma(gorev: Gorev): Intent =
            Intent().putExtra(EK_EYLEM, EYLEM_GOREV)
                .putExtra(EK_ADRES, gorev.notUri.toString())
                .putExtra(EK_SATIR, gorev.satirNo)
                .putExtra(EK_METIN, gorev.metin)
                .setData(Uri.parse("notlar-widget://gorev/${gorev.satirNo}/" + Uri.encode(gorev.notUri.toString())))
    }
}
