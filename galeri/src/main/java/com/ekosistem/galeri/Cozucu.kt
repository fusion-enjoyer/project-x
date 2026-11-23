package com.ekosistem.galeri

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface

/** Resim okuma: boyut, EXIF yönü, ekrana göre küçültülmüş tam resim, küçük resim. */
object Cozucu {

    /** Ham (yönsüz) piksel boyutu; okunamazsa null. */
    fun boyut(c: Context, uri: Uri): Pair<Int, Int>? {
        val s = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, s) } }
        return if (s.outWidth > 0 && s.outHeight > 0) s.outWidth to s.outHeight else null
    }

    /** Dosyanın kendi EXIF yönü (başka uygulamadan gelen resimler için). */
    fun exifYonu(c: Context, uri: Uri): Int = runCatching {
        c.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

    /**
     * Ekrana sığacak kadar küçültülmüş, yönü uygulanmış ve [ekAci] kadar
     * döndürülmüş resim. Bellek yetmezse bir kat daha küçültüp yeniden dener.
     */
    fun tamResim(c: Context, uri: Uri, yon: Int, hedef: Int, ekAci: Int = 0): Bitmap? {
        val (g, y) = boyut(c, uri) ?: return null
        var kat = Kirpma.ekranOrneklemesi(g, y, hedef)
        repeat(3) {
            try {
                val s = BitmapFactory.Options().apply { inSampleSize = kat }
                val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, s) }
                    ?: return null
                return dondur(bmp, yon + ekAci)
            } catch (_: OutOfMemoryError) {
                kat *= 2
            }
        }
        return null
    }

    fun dondur(bmp: Bitmap, aci: Int): Bitmap {
        val a = Kirpma.aciDuzelt(aci)
        if (a == 0) return bmp
        val m = Matrix().apply { postRotate(a.toFloat()) }
        val yeni = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (yeni !== bmp) bmp.recycle()
        return yeni
    }

    /** Izgara için küçük resim (kenar ≈ [px]); yönü uygulanmış. */
    @Suppress("DEPRECATION")
    fun kucukResim(c: Context, m: Medya, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            return runCatching { c.contentResolver.loadThumbnail(m.uri, Size(px, px), null) }.getOrNull()
        }
        val bmp = runCatching {
            if (m.video) MediaStore.Video.Thumbnails.getThumbnail(c.contentResolver, m.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            else MediaStore.Images.Thumbnails.getThumbnail(c.contentResolver, m.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }.getOrNull()
        if (bmp != null) return if (m.video) bmp else dondur(bmp, m.yon)
        // Depo küçük resim üretemediyse (bazı eski telefonlar) kendimiz küçültürüz.
        return if (m.video) null else tamResim(c, m.uri, m.yon, px)
    }
}
