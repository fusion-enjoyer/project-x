package com.ekosistem.takvim

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Bildirim panelindeki "Yeni etkinlik" döşemesi (Android 7+): dokununca etkinlik
 * ekleme ekranı açılır. Eski sürümlerde sistem bu servisi hiç bağlamaz.
 */
@TargetApi(Build.VERSION_CODES.N)
class YeniEtkinlikDosemesi : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.dosemesi_yeni)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { ac() } else ac()
    }

    private fun ac() {
        val niyet = Intent(this, DuzenleActivity::class.java).setAction("com.ekosistem.takvim.YENI")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14: Intent ile açmak kaldırıldı, PendingIntent zorunlu.
            val bekleyen = PendingIntent.getActivity(this, 0, niyet, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(bekleyen)
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(niyet)
        }
    }
}
