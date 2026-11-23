package com.ekosistem.galeri

import java.util.Calendar
import java.util.TimeZone

val UTC: TimeZone = TimeZone.getTimeZone("UTC")

fun an(yil: Int, ay: Int, gun: Int): Long = Calendar.getInstance(UTC).apply {
    clear(); set(yil, ay - 1, gun, 12, 0)
}.timeInMillis

fun medya(
    id: Long = 1,
    ad: String = "IMG_0001.jpg",
    album: String = "Camera",
    albumId: String = album,
    tarih: Long = an(2024, 10, 5),
    video: Boolean = false,
    mime: String = if (video) "video/mp4" else "image/jpeg"
) = Medya(id, ad, albumId, album, tarih, 4000, 3000, 0, video, 0, 0, mime)
