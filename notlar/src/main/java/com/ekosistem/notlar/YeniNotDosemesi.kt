package com.ekosistem.notlar

import android.annotation.TargetApi
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Bildirim panelindeki "Yeni not" döşemesi (Android 7+). Telefon kilitliyse
 * önce kilidin açılması istenir; not ekranı kilit ekranının üstünde açılmaz.
 * Eski sürümlerde sistem bu servisi hiç bağlamaz.
 */
@TargetApi(Build.VERSION_CODES.N)
class YeniNotDosemesi : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { ac() } else ac()
    }

    private fun ac() {
        val niyet = Intent(this, EditorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14: Intent ile açmak kaldırıldı, PendingIntent zorunlu.
            val bekleyen = PendingIntent.getActivity(
                this, 0, niyet, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(bekleyen)
        } else {
            // Lint sürüm kontrolünü görmüyor; bu dal yalnız Android 13 ve öncesi.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(niyet)
        }
    }
}
