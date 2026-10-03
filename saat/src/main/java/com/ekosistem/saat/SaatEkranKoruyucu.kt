package com.ekosistem.saat

import android.service.dreams.DreamService

/**
 * Telefonun "Ekran koruyucu" listesinde görünen Saat: şarjdayken ya da yuvada
 * ekran kapanmak yerine yatay saat modunu gösterir. Etkileşimsizdir; dokununca
 * telefon uyanır.
 */
class SaatEkranKoruyucu : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(SaatModuGorunumu(this).apply { seviye = Depo.ayarlar(this@SaatEkranKoruyucu).getInt("saat_modu_seviye", 1) })
    }
}
