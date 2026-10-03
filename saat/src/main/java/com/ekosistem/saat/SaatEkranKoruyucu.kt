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
        val seviye = Depo.ayarlar(this).getInt(SaatModuGorunumu.TERCIH, 2)
        // Tam parlak seviyede ekran kısılmaz; loş seçildiyse sistem de kısar.
        isScreenBright = seviye == 2
        setContentView(SaatModuGorunumu(this).apply { this.seviye = seviye })
    }
}
