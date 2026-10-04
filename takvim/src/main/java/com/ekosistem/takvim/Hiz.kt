package com.ekosistem.takvim

import android.util.Log

/** Yalnız debug sürümde süre ölçümü (`adb logcat -s TakvimHiz`); yayın sürümünde hiçbir şey yazmaz. */
object Hiz {
    inline fun <T> olc(etiket: String, islem: () -> T): T {
        if (!BuildConfig.DEBUG) return islem()
        val bas = System.nanoTime()
        val sonuc = islem()
        Log.d("TakvimHiz", "$etiket ${(System.nanoTime() - bas) / 1_000_000} ms")
        return sonuc
    }
}
