package com.ekosistem.notlar

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uygulama ve not kilidi. PIN düz saklanmaz; rastgele tuz ile SHA-256 özeti
 * tutulur. Not içerikleri şifrelenmez — dosyaların taşınabilirliği korunur,
 * kilit yalnızca uygulama içi erişimi kapatır.
 */
object Kilit {

    /** Uygulama kilidi bu oturumda açıldı mı? Arka plana geçince sıfırlanır. */
    @Volatile
    var oturumAcik = false

    @Volatile
    private var arkaPlanZamani = 0L

    /**
     * Klasör seçici, paylaşma penceresi gibi bilinçli çıkışlarda dönüşte kilit
     * sorulmaz; yoksa her yedek alma denemesi PIN ekranıyla kesilirdi.
     */
    @Volatile
    var sistemAraciBekleniyor = false

    /** Otomatik kilitlenme seçenekleri (ms). Ayarlardaki sırayla aynı. */
    val GECIKMELER = longArrayOf(0L, 30_000L, 60_000L, 300_000L)

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
        Prefs.parmakIziKaydet(c, false)
        oturumAcik = true
    }

    // --- Deneme sınırı ---

    /**
     * 4 haneli PIN'in 10.000 olasılığı var; sınırsız denemede telefonu eline
     * alan biri birkaç dakikada açardı. Her 5 yanlışta bekleme başlar: 30 sn,
     * sonra 1, 2, 4... dakika (en fazla 15). Uygulamayı kapatıp açmak sayacı
     * sıfırlamaz. Doğru PIN sayacı temizler.
     */
    fun beklemeKalan(c: Context): Long =
        (Prefs.pinBeklemeBitis(c) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun yanlisPin(c: Context) {
        val sayi = Prefs.pinHataSayisi(c) + 1
        Prefs.pinHataSayisiKaydet(c, sayi)
        if (sayi % DENEME_SINIRI == 0) {
            val seri = sayi / DENEME_SINIRI
            val sure = (ILK_BEKLEME shl (seri - 1).coerceAtMost(10)).coerceAtMost(EN_UZUN_BEKLEME)
            Prefs.pinBeklemeBitisKaydet(c, System.currentTimeMillis() + sure)
        }
    }

    fun dogruPin(c: Context) {
        if (Prefs.pinHataSayisi(c) != 0) Prefs.pinHataSayisiKaydet(c, 0)
        if (Prefs.pinBeklemeBitis(c) != 0L) Prefs.pinBeklemeBitisKaydet(c, 0L)
    }

    private const val DENEME_SINIRI = 5
    private const val ILK_BEKLEME = 30_000L
    private const val EN_UZUN_BEKLEME = 15 * 60_000L

    fun dogrula(c: Context, pin: String): Boolean {
        val kayitli = Prefs.pinOzeti(c) ?: return true
        val tuz = Prefs.pinTuzu(c) ?: return false
        return ozet(pin, tuz) == kayitli
    }

    /** Uygulama açılışında kilit ekranı gerekiyor mu? */
    fun gerekli(c: Context): Boolean = kurulu(c) && !oturumAcik

    // --- Ön plan / arka plan ---

    fun arkaPlanaGecildi() {
        arkaPlanZamani = SystemClock.elapsedRealtime()
    }

    /** Uygulama öne döndüğünde seçilen gecikme dolduysa oturumu kapatır. */
    fun onPlanaGelindi(c: Context) {
        if (!kurulu(c) || !oturumAcik) return
        if (sistemAraciBekleniyor) {
            sistemAraciBekleniyor = false
            return
        }
        val gecikme = GECIKMELER.getOrElse(Prefs.kilitGecikmesi(c)) { 0L }
        if (SystemClock.elapsedRealtime() - arkaPlanZamani >= gecikme) oturumAcik = false
    }

    // --- Parmak izi ---

    /** BiometricPrompt Android 9'dan itibaren var; donanım yoksa seçenek gizlenir. */
    fun parmakIziDonanimi(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = c.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
            pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
    }

    fun parmakIziAcik(c: Context): Boolean = parmakIziDonanimi(c) && Prefs.parmakIzi(c)

    // --- Not kilidi ---

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
