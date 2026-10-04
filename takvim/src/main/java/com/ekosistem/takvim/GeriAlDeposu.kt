package com.ekosistem.takvim

import android.os.SystemClock

/**
 * Silinen etkinliğin geri alma kaydı. Ayrıntı ekranı siler ve kapanır; ana ekran
 * öne gelince "Silindi · Geri al" şeridini gösterir. Kayıt bellekte tutulur ve
 * kısa süre sonra geçersizdir (uygulama kapanınca zaten geri alınamaz).
 */
object GeriAlDeposu {
    private const val GECERLILIK_MS = 9_000L
    private var kayit: TakvimDeposu.SilmeKaydi? = null
    private var an = 0L

    fun birak(k: TakvimDeposu.SilmeKaydi) {
        kayit = k
        an = SystemClock.elapsedRealtime()
    }

    /** Hâlâ geçerli kayıt; alındığı anda tüketilir. */
    fun al(): TakvimDeposu.SilmeKaydi? {
        val k = kayit ?: return null
        kayit = null
        return if (SystemClock.elapsedRealtime() - an <= GECERLILIK_MS) k else null
    }
}
