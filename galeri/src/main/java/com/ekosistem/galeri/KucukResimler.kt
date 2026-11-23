package com.ekosistem.galeri

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Izgaranın küçük resimleri: bellekte LRU önbellek, arka planda birkaç iş parçacığı.
 * Kaydırırken ekrandan çıkan karenin isteği sonuç gelince yok sayılır (etiket kontrolü).
 */
object KucukResimler {

    private val isleyici = Handler(Looper.getMainLooper())
    private val yurutucu = Executors.newFixedThreadPool(3)

    // Belleğin sekizde biri; eski telefonda ~16 MB, birkaç yüz kare.
    private val onbellek = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun anahtar(m: Medya, px: Int) = "${m.uri}|$px|${m.tarih}"

    fun yukle(c: Context, m: Medya, hedef: ImageView, px: Int) {
        val a = anahtar(m, px)
        hedef.tag = a
        onbellek.get(a)?.let { hedef.setImageBitmap(it); return }
        hedef.setImageDrawable(null)
        val uygulama = c.applicationContext
        yurutucu.execute {
            if (hedef.tag != a) return@execute
            val bmp = runCatching { Cozucu.kucukResim(uygulama, m, px) }.getOrNull() ?: return@execute
            onbellek.put(a, bmp)
            isleyici.post { if (hedef.tag == a) hedef.setImageBitmap(bmp) }
        }
    }

    fun temizle() = onbellek.evictAll()
}
