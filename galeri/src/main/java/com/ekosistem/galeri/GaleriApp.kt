package com.ekosistem.galeri

import android.app.Application
import android.content.ComponentCallbacks2

class GaleriApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Arka plana geçince küçük resim önbelleğini bırak; eski telefonlarda bellek değerli.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) KucukResimler.temizle()
    }
}
