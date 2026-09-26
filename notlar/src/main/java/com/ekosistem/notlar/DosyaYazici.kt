package com.ekosistem.notlar

import java.io.File
import java.io.FileOutputStream

/**
 * Dosyayı yarım bırakmadan yazar. Önce yanındaki geçici dosyaya yazılır,
 * diske işlenir, sonra tek hamlede asıl dosyanın yerine konur. Yazma sırasında
 * telefon kapanır ya da uygulama öldürülürse eski dosya olduğu gibi kalır;
 * "wt" ile doğrudan yazmada ise dosya önce sıfırlandığı için not boş kalabiliyordu.
 */
object DosyaYazici {

    const val GECICI_UZANTI = ".yaziliyor"

    fun atomikYaz(hedef: File, bayt: ByteArray): Boolean {
        val dizin = hedef.parentFile ?: return false
        val gecici = File(dizin, "." + hedef.name + GECICI_UZANTI)
        return try {
            FileOutputStream(gecici).use { akis ->
                akis.write(bayt)
                akis.flush()
                akis.fd.sync()
            }
            // Aynı dosya sisteminde yeniden adlandırma atomiktir; varsa eskisinin üstüne geçer.
            if (gecici.renameTo(hedef)) {
                true
            } else {
                gecici.delete()
                false
            }
        } catch (_: Exception) {
            gecici.delete()
            false
        }
    }
}
