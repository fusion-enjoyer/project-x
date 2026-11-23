package com.ekosistem.galeri

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Medya okuma izinleri Android sürümüne göre üç ayrı biçimde. */
object Izin {

    const val ISTEK = 41

    private fun var_(c: Context, izin: String) =
        ContextCompat.checkSelfPermission(c, izin) == PackageManager.PERMISSION_GRANTED

    /** İstenecek izinler. Android 10 öncesinde kopya kaydetmek için yazma da birlikte istenir. */
    fun istenecekler(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /** Tam erişim: bütün fotoğraflar görünür. */
    fun tam(c: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> var_(c, Manifest.permission.READ_MEDIA_IMAGES)
        else -> var_(c, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 14+: kullanıcı yalnız bazı fotoğrafları seçmiş. */
    fun kismi(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 && !tam(c) &&
            var_(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun okunabilir(c: Context): Boolean = tam(c) || kismi(c)

    fun videoVar(c: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            var_(c, Manifest.permission.READ_MEDIA_VIDEO) || var_(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> var_(c, Manifest.permission.READ_MEDIA_VIDEO)
        else -> var_(c, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 9 ve öncesinde kopya kaydetmek için yazma izni gerekir. */
    fun yazabilir(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 || var_(c, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun iste(a: Activity) = ActivityCompat.requestPermissions(a, istenecekler(), ISTEK)
}
