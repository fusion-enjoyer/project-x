package com.ekosistem.notlar

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

class NotlarApp : Application() {

    /** Ekranda görünen ekran sayısı; 0'a düşünce uygulama arka plandadır. */
    private var gorunenSayisi = 0
    private var kilitIstendi = false

    override fun onCreate() {
        super.onCreate()
        Tema.uygula(Prefs.tema(this))
        registerActivityLifecycleCallbacks(YasamDongusu())
    }

    /**
     * Kilit ekranı tek yerden yönetilir: uygulama öne her döndüğünde seçilen
     * gecikme dolduysa oturum kapanır ve PIN yeniden sorulur.
     */
    private inner class YasamDongusu : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (gorunenSayisi == 0) Kilit.onPlanaGelindi(this@NotlarApp)
            gorunenSayisi++
            if (activity is KilitActivity || kilitIstendi) return
            if (!Kilit.gerekli(this@NotlarApp)) return
            kilitIstendi = true
            activity.startActivity(
                Intent(activity, KilitActivity::class.java)
                    .putExtra("kip", KilitActivity.KIP_AC)
            )
        }

        override fun onActivityStopped(activity: Activity) {
            gorunenSayisi--
            if (gorunenSayisi <= 0) {
                gorunenSayisi = 0
                Kilit.arkaPlanaGecildi()
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is KilitActivity) kilitIstendi = false
        }

        override fun onActivityCreated(activity: Activity, durum: Bundle?) {
            // Kilit kuruluysa son uygulamalar ekranında notların önizlemesi
            // görünmesin (Android 13+). Eski sürümlerde "ekran görüntüsünü
            // engelle" ayarı bunu da kapsar.
            if (Build.VERSION.SDK_INT >= 33 && Kilit.kurulu(this@NotlarApp)) {
                activity.setRecentsScreenshotEnabled(false)
            }
        }
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, durum: Bundle) {}
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
