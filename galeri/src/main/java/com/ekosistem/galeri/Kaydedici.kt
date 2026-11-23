package com.ekosistem.galeri

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Döndürülmüş ya da kırpılmış resmi YENİ bir dosya olarak kaydeder; orijinale
 * hiç dokunulmaz. Kopya Resimler/Galeri klasörüne, orijinalin çekim tarihiyle
 * düşer (listede orijinalin hemen yanında görünür).
 */
object Kaydedici {

    const val KLASOR = "Galeri"

    /**
     * [kutu] ekrandaki (yönü ve [aci] uygulanmış) resme göre 0..1. [aci] = EXIF yönü + kullanıcı.
     * Başarılıysa yeni dosyanın adresi.
     */
    fun kaydet(c: Context, kaynak: Uri, ad: String, mime: String, tarih: Long, kutu: Kutu, aci: Int): Uri? {
        val (g, y) = Cozucu.boyut(c, kaynak) ?: return null
        val bolge = Kirpma.hamPiksel(kutu, aci, g, y)
        var kat = Kirpma.ornekleme(bolge.genislik, bolge.yukseklik)
        var bmp: Bitmap? = null
        repeat(3) {
            if (bmp != null) return@repeat
            try {
                bmp = kes(c, kaynak, bolge, kat)
            } catch (_: OutOfMemoryError) {
                kat *= 2
            }
        }
        val kesik = bmp ?: return null
        val son = try { Cozucu.dondur(kesik, aci) } catch (_: OutOfMemoryError) { kesik.recycle(); return null }

        val png = mime == "image/png"
        val bicim = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val yeniMime = if (png) "image/png" else "image/jpeg"
        val yeniAd = yeniAd(ad, if (png) "png" else "jpg", System.currentTimeMillis())
        return try {
            if (Build.VERSION.SDK_INT >= 29) yazQ(c, son, bicim, yeniAd, yeniMime, tarih)
            else yazEski(c, son, bicim, yeniAd, yeniMime, tarih)
        } finally {
            son.recycle()
        }
    }

    /** "IMG_2024.jpg" → "IMG_2024_duzenleme_1700000000.jpg" */
    fun yeniAd(ad: String, uzanti: String, an: Long): String {
        val kok = ad.substringBeforeLast('.', ad).ifBlank { "IMG" }
        return "${kok}_duzenleme_${an / 1000}.$uzanti"
    }

    private fun kes(c: Context, uri: Uri, b: PikselKutu, kat: Int): Bitmap? {
        val s = BitmapFactory.Options().apply { inSampleSize = kat }
        // Önce yalnız bölgeyi oku: 48 MP fotoğrafı belleğe almadan kesebilmek için.
        val bolgeli = runCatching {
            c.contentResolver.openInputStream(uri)?.use { akis ->
                @Suppress("DEPRECATION")
                val d = BitmapRegionDecoder.newInstance(akis, false)
                try { d?.decodeRegion(Rect(b.sol, b.ust, b.sag, b.alt), s) } finally { d?.recycle() }
            }
        }.getOrNull()
        if (bolgeli != null) return bolgeli
        // Bölge okunamayan biçimler (GIF, bazı eski WebP): tamamını okuyup kes.
        val tam = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, s) } ?: return null
        val sol = (b.sol / kat).coerceIn(0, tam.width - 1)
        val ust = (b.ust / kat).coerceIn(0, tam.height - 1)
        val gen = (b.genislik / kat).coerceIn(1, tam.width - sol)
        val yuk = (b.yukseklik / kat).coerceIn(1, tam.height - ust)
        val kesik = Bitmap.createBitmap(tam, sol, ust, gen, yuk)
        if (kesik !== tam) tam.recycle()
        return kesik
    }

    private fun yazQ(c: Context, bmp: Bitmap, bicim: Bitmap.CompressFormat, ad: String, mime: String, tarih: Long): Uri? {
        val cr = c.contentResolver
        val degerler = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ad)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + KLASOR)
            put(MediaStore.MediaColumns.DATE_TAKEN, tarih)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val adres = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = cr.insert(adres, degerler) ?: return null
        return try {
            val tamam = cr.openOutputStream(uri)?.use { bmp.compress(bicim, 95, it) } ?: false
            if (!tamam) error("yazılamadı")
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            runCatching { cr.delete(uri, null, null) }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun yazEski(c: Context, bmp: Bitmap, bicim: Bitmap.CompressFormat, ad: String, mime: String, tarih: Long): Uri? {
        val klasor = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), KLASOR)
        if (!klasor.exists() && !klasor.mkdirs()) return null
        val dosya = File(klasor, ad)
        val tamam = runCatching { FileOutputStream(dosya).use { bmp.compress(bicim, 95, it) } }.getOrDefault(false)
        if (!tamam) { dosya.delete(); return null }
        dosya.setLastModified(tarih)
        // Medya deposu dosyayı tarayıp eklesin; adresini dönmesini beklemek gerekmiyor.
        MediaScannerConnection.scanFile(c, arrayOf(dosya.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(dosya)
    }
}
