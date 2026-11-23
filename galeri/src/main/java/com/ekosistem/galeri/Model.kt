package com.ekosistem.galeri

import android.net.Uri

/** Telefonun medya deposundaki bir fotoğraf ya da video. */
data class Medya(
    val id: Long,
    val ad: String,
    val albumId: String,
    val albumAdi: String,
    /** Çekilme anı (yoksa son değişiklik), ms. */
    val tarih: Long,
    val genislik: Int,
    val yukseklik: Int,
    /** EXIF yönü: 0, 90, 180, 270 (yalnız fotoğraf). */
    val yon: Int,
    val video: Boolean,
    val sureMs: Long,
    val boyut: Long,
    val mime: String
) {
    /** content://media/… adresi; ilk kullanımda kurulur (testler Uri'siz çalışsın). */
    val uri: Uri by lazy { MedyaDeposu.adres(video, id) }
}

/** Telefondaki bir klasör (MediaStore "bucket"). */
data class Album(
    val id: String,
    val ad: String,
    val kapak: Medya,
    val adet: Int
)

/** Görüntüleyiciye hangi listenin açıldığı; süreç ölse de liste yeniden kurulabilsin. */
sealed class Kaynak {
    object Hepsi : Kaynak()
    data class Klasor(val albumId: String) : Kaynak()
    data class Sorgu(val metin: String) : Kaynak()

    fun kodla(): String = when (this) {
        Hepsi -> "hepsi"
        is Klasor -> "album:$albumId"
        is Sorgu -> "ara:$metin"
    }

    companion object {
        fun coz(s: String?): Kaynak = when {
            s == null -> Hepsi
            s.startsWith("album:") -> Klasor(s.removePrefix("album:"))
            s.startsWith("ara:") -> Sorgu(s.removePrefix("ara:"))
            else -> Hepsi
        }
    }
}
