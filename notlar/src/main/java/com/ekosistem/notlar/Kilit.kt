package com.ekosistem.notlar

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uygulama ve not kilidi. PIN düz saklanmaz; rastgele tuz ile SHA-256 özeti
 * tutulur. Not içerikleri şifrelenmez — dosyaların taşınabilirliği korunur,
 * kilit yalnızca uygulama içi erişimi kapatır.
 */
object Kilit {

    /** Bu oturumda kilit bir kez açıldıysa tekrar sorulmaz. */
    @Volatile
    var oturumAcik = false

    fun kurulu(c: Context): Boolean = Prefs.pinOzeti(c) != null

    fun pinKur(c: Context, pin: String) {
        val tuz = ByteArray(16)
        SecureRandom().nextBytes(tuz)
        val tuzMetni = tuz.joinToString("") { "%02x".format(it) }
        Prefs.pinKaydet(c, ozet(pin, tuzMetni), tuzMetni)
        oturumAcik = true
    }

    fun pinKaldir(c: Context) {
        Prefs.pinKaydet(c, null, null)
        oturumAcik = true
    }

    fun dogrula(c: Context, pin: String): Boolean {
        val kayitli = Prefs.pinOzeti(c) ?: return true
        val tuz = Prefs.pinTuzu(c) ?: return false
        return ozet(pin, tuz) == kayitli
    }

    /** Uygulama açılışında kilit ekranı gerekiyor mu? */
    fun gerekli(c: Context): Boolean = kurulu(c) && !oturumAcik

    fun notKilitli(c: Context, uri: String): Boolean =
        kurulu(c) && Prefs.kilitliNotlar(c).contains(uri)

    fun notKilidiDegistir(c: Context, uri: String): Boolean =
        Prefs.kilitliNotDegistir(c, uri)

    private fun ozet(pin: String, tuz: String): String {
        val sindirici = MessageDigest.getInstance("SHA-256")
        val bayt = sindirici.digest((tuz + pin).toByteArray(Charsets.UTF_8))
        return bayt.joinToString("") { "%02x".format(it) }
    }
}
