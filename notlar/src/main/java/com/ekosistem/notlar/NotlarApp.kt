package com.ekosistem.notlar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class NotlarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Tema.uygula(Prefs.tema(this))
    }
}

object Tema {
    fun uygula(tema: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (tema) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
