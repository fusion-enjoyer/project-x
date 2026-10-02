package com.ekosistem.saat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class SaatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        temaUygula(Depo.tema(this))
        Bildirimler.kanallariKur(this)
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
