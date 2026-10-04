package com.ekosistem.notlar

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Parolayla şifrelenmiş not. Not kilidinden farklı olarak dosyanın kendisi
 * şifrelidir: klasörü Syncthing'le eşitleyen ya da yedeği açan biri de içeriği
 * okuyamaz.
 *
 * Dosya yine `.md`'dir; başında okunabilir bir kapak (başlık ve açıklama),
 * sonunda tek satırlık veri durur:
 *
 *     notlar-sifreli:1:<tekrar>:<tuz>:<iv>:<şifreli metin>
 *
 * Anahtar paroladan PBKDF2-HMAC-SHA256 ile türetilir (tuz 16 bayt), metin
 * AES-256-GCM ile şifrelenir (iv 12 bayt, her kayıtta yeni). Sürüm, tekrar
 * sayısı ve tuz ek doğrulanan veri (AAD) olarak bağlanır; biri değiştirilirse
 * çözme başarısız olur. PBKDF2-SHA256 ve java.util.Base64 Android 8'de geldi.
 */
object Sifreleme {

    private const val ONEK = "notlar-sifreli:"
    private const val SURUM = 1
    const val TEKRAR = 310_000
    private const val TUZ_BOYU = 16
    private const val IV_BOYU = 12
    private const val ETIKET_BIT = 128

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun destekleniyor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun veriSatiriMi(satir: CharSequence): Boolean = satir.startsWith(ONEK)

    fun sifreliMi(metin: CharSequence): Boolean = metin.lineSequence().any { veriSatiriMi(it) }

    /** Türetilmiş anahtar; not açıkken bellekte tutulur, her kayıtta yeniden türetilmez. */
    class Anahtar(val anahtar: SecretKey, val tuz: ByteArray, val tekrar: Int)

    sealed class Sonuc {
        class Acildi(val metin: String, val anahtar: Anahtar) : Sonuc()
        object YanlisParola : Sonuc()
        object Bozuk : Sonuc()
    }

    /** Yeni şifreleme için: rastgele tuzla anahtar türetir. Yavaştır, ana iş parçacığında çağrılmaz. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun yeniAnahtar(parola: CharArray, tekrar: Int = TEKRAR): Anahtar {
        val tuz = ByteArray(TUZ_BOYU).also { SecureRandom().nextBytes(it) }
        return Anahtar(turet(parola, tuz, tekrar), tuz, tekrar)
    }

    /** Dosyaya yazılacak metin: [kapak] satırları, boş satır, veri satırı. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun sifrele(duz: String, a: Anahtar, kapak: String): String {
        val iv = ByteArray(IV_BOYU).also { SecureRandom().nextBytes(it) }
        val sifre = Cipher.getInstance("AES/GCM/NoPadding")
        sifre.init(Cipher.ENCRYPT_MODE, a.anahtar, GCMParameterSpec(ETIKET_BIT, iv))
        sifre.updateAAD(ekVeri(a.tekrar, a.tuz))
        val veri = sifre.doFinal(duz.toByteArray(Charsets.UTF_8))
        val k = Base64.getEncoder()
        val satir = listOf(
            ONEK.removeSuffix(":"), SURUM, a.tekrar, k.encodeToString(a.tuz),
            k.encodeToString(iv), k.encodeToString(veri)
        ).joinToString(":")
        return kapak.trimEnd() + "\n\n" + satir + "\n"
    }

    /** Dosya metnini çözer. Yavaştır (anahtar türetilir), ana iş parçacığında çağrılmaz. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun coz(dosya: String, parola: CharArray): Sonuc {
        val satir = dosya.lineSequence().firstOrNull { veriSatiriMi(it) } ?: return Sonuc.Bozuk
        val parcalar = satir.trim().removePrefix(ONEK).split(':')
        if (parcalar.size != 5 || parcalar[0] != SURUM.toString()) return Sonuc.Bozuk
        return try {
            val tekrar = parcalar[1].toInt()
            if (tekrar !in 1..10_000_000) return Sonuc.Bozuk
            val c = Base64.getDecoder()
            val tuz = c.decode(parcalar[2])
            val iv = c.decode(parcalar[3])
            val veri = c.decode(parcalar[4])
            val a = Anahtar(turet(parola, tuz, tekrar), tuz, tekrar)
            val sifre = Cipher.getInstance("AES/GCM/NoPadding")
            sifre.init(Cipher.DECRYPT_MODE, a.anahtar, GCMParameterSpec(ETIKET_BIT, iv))
            sifre.updateAAD(ekVeri(tekrar, tuz))
            Sonuc.Acildi(String(sifre.doFinal(veri), Charsets.UTF_8), a)
        } catch (_: AEADBadTagException) {
            Sonuc.YanlisParola
        } catch (_: Exception) {
            Sonuc.Bozuk
        }
    }

    private fun turet(parola: CharArray, tuz: ByteArray, tekrar: Int): SecretKey {
        val tarif = PBEKeySpec(parola, tuz, tekrar, 256)
        try {
            val ham = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(tarif).encoded
            return SecretKeySpec(ham, "AES")
        } finally {
            tarif.clearPassword()
        }
    }

    private fun ekVeri(tekrar: Int, tuz: ByteArray): ByteArray =
        "$ONEK$SURUM:$tekrar:".toByteArray(Charsets.UTF_8) + tuz
}
