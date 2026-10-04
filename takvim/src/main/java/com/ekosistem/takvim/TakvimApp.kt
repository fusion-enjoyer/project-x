package com.ekosistem.takvim

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class TakvimApp : Application() {

    override fun onCreate() {
        super.onCreate()
        temaUygula(Depo.tema(this))
        Bildirimler.kanalKur(this)
        // Günlük özet açıksa alarmı güvene al (zorla durdurma alarmları siler).
        OzetAlici.kur(this)
    }

    companion object {
        fun temaUygula(tema: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (tema) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
