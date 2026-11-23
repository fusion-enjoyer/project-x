package com.ekosistem.galeri

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Fotoğraf ve videolar telefonun kendi medya deposundan (MediaStore) okunur;
 * Galeri kendi veritabanını tutmaz, dosyalara dokunmaz.
 */
object MedyaDeposu {

    /** Son okunan liste: ekranlar arası geçişte yeniden sorgulamamak için. */
    @Volatile var onbellek: List<Medya>? = null
        private set

    private val resimAdresi: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val videoAdresi: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun adres(video: Boolean, id: Long): Uri =
        ContentUris.withAppendedId(if (video) videoAdresi else resimAdresi, id)

    /** Bütün fotoğraf ve videolar, yeniden eskiye. Ana iş parçacığında çağırma. */
    fun hepsi(c: Context): List<Medya> {
        val liste = ArrayList<Medya>()
        runCatching { oku(c, resimAdresi, video = false, liste) }
        if (Izin.videoVar(c)) runCatching { oku(c, videoAdresi, video = true, liste) }
        liste.sortByDescending { it.tarih }
        onbellek = liste
        return liste
    }

    fun kaynaktan(c: Context, k: Kaynak, taze: Boolean = false): List<Medya> {
        val hepsi = (if (taze) null else onbellek) ?: hepsi(c)
        return when (k) {
            Kaynak.Hepsi -> hepsi
            is Kaynak.Klasor -> hepsi.filter { it.albumId == k.albumId }
            is Kaynak.Sorgu -> Arama.ara(k.metin, hepsi)
        }
    }

    fun gecersizKil() {
        onbellek = null
    }

    @Suppress("DEPRECATION")
    private fun oku(c: Context, adres: Uri, video: Boolean, hedef: MutableList<Medya>) {
        val sutunlar = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_TAKEN
        )
        if (video) sutunlar += MediaStore.Video.VideoColumns.DURATION
        else sutunlar += MediaStore.Images.ImageColumns.ORIENTATION
        // Android 10+: çöp kutusundaki ve yarım kalmış dosyalar sorguya zaten girmez.
        val imlec: Cursor = c.contentResolver.query(adres, sutunlar.toTypedArray(), null, null, null) ?: return
        imlec.use { k ->
            val iId = k.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iAd = k.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val iDeg = k.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val iCek = k.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val iGen = k.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val iYuk = k.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val iBoy = k.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val iMime = k.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val iKova = k.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
            val iKovaAd = k.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val iSure = if (video) k.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            val iYon = if (!video) k.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION) else -1
            while (k.moveToNext()) {
                val id = k.getLong(iId)
                val cekim = if (iCek >= 0) k.getLong(iCek) else 0L
                val degisim = if (iDeg >= 0) k.getLong(iDeg) * 1000L else 0L
                val yon = if (iYon >= 0) Kirpma.aciDuzelt(k.getInt(iYon)) else 0
                var gen = if (iGen >= 0) k.getInt(iGen) else 0
                var yuk = if (iYuk >= 0) k.getInt(iYuk) else 0
                // Android 10+ WIDTH/HEIGHT'ı yön uygulanmış verir; eskiler ham verir. Ham tutalım.
                if (Build.VERSION.SDK_INT >= 29 && yon % 180 != 0) { val t = gen; gen = yuk; yuk = t }
                hedef += Medya(
                    id = id,
                    ad = (if (iAd >= 0) k.getString(iAd) else null).orEmpty(),
                    albumId = (if (iKova >= 0) k.getString(iKova) else null).orEmpty(),
                    albumAdi = (if (iKovaAd >= 0) k.getString(iKovaAd) else null) ?: "—",
                    tarih = if (cekim > 0) cekim else degisim,
                    genislik = gen,
                    yukseklik = yuk,
                    yon = yon,
                    video = video,
                    sureMs = if (iSure >= 0) k.getLong(iSure) else 0L,
                    boyut = if (iBoy >= 0) k.getLong(iBoy) else 0L,
                    mime = (if (iMime >= 0) k.getString(iMime) else null).orEmpty()
                )
            }
        }
    }
}
