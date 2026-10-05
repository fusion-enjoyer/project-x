package com.ekosistem.notlar

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
     * Kilit ekranı tek yerden yönetilir: uygulama öne her döndüğünde oturum
     * kapanır ve PIN yeniden sorulur.
     */
    private inner class YasamDongusu : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (gorunenSayisi == 0) Kilit.onPlanaGelindi(this@NotlarApp)
            gorunenSayisi++
            // Widget aracısı ekranda bir şey göstermez; görev işaretlemek PIN istemesin.
            // Not açılacaksa editör başlarken kilit yine sorulur.
            if (activity is KilitActivity || activity is WidgetEylemActivity || kilitIstendi) return
            if (!Kilit.gerekli(this@NotlarApp)) return
            kilitIstendi = true
            activity.startActivity(
                Intent(activity, KilitActivity::class.java)
                    .putExtra("kip", KilitActivity.KIP_AC)
            )
        }

        override fun onActivityStopped(activity: Activity) {
            gorunenSayisi--
            if (gorunenSayisi < 0) gorunenSayisi = 0
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is KilitActivity) kilitIstendi = false
        }

        override fun onActivityCreated(activity: Activity, durum: Bundle?) {
            ekranGizlemeyiUygula(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            // Ayar değişince arkadaki ekranlar da öne döndüklerinde uyar.
            ekranGizlemeyiUygula(activity)
            GizlilikOrtusu.devamEdildi(activity)
        }

        // Kilit kuruluysa son uygulamalar ekranında not yerine örtü görünür.
        override fun onActivityPaused(activity: Activity) = GizlilikOrtusu.duraklatildi(activity)
        override fun onActivitySaveInstanceState(activity: Activity, durum: Bundle) {}
    }
}

/**
 * "Ekran görüntüsünü engelle" her ekrana buradan uygulanır. Önceden ekranlar
 * bunu tek tek yapıyordu; Görevler, Çöp kutusu ve Ayarlar unutulmuştu.
 */
fun ekranGizlemeyiUygula(activity: Activity) {
    if (Prefs.ekranGizle(activity)) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
