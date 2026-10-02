package com.ekosistem.saat

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.TimeZone

/**
 * Bildirim panelindeki "Alarm" döşemesi (Android 7+): bir sonraki alarmı
 * gösterir (Android 10+ alt yazıda), dokununca uygulamayı açar. Alarm yoksa
 * pasif görünür. Eski sürümlerde sistem bu servisi hiç bağlamaz.
 */
@TargetApi(Build.VERSION_CODES.N)
class AlarmDosemesi : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val simdi = System.currentTimeMillis()
        val sonraki = Depo.alarmlar(this).filter { it.id != MainActivity.DENEME_ID }
            .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, TimeZone.getDefault()) }.minOrNull()
        tile.label = getString(R.string.dosemesi_adi)
        tile.state = if (sonraki != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (sonraki != null) {
                "${Metinler.gun(this, sonraki, simdi)} ${Metinler.saat(this, sonraki)}"
            } else {
                getString(R.string.dosemesi_alarm_yok)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { ac() } else ac()
    }

    private fun ac() {
        val niyet = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EK_SEKME, MainActivity.SEKME_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14: Intent ile açmak kaldırıldı, PendingIntent zorunlu.
            val bekleyen = PendingIntent.getActivity(
                this, 0, niyet, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(bekleyen)
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(niyet)
        }
    }
}
