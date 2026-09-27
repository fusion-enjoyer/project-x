package com.ekosistem.notlar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Dışarıdan gelen metin ve dosyalar. Uygulama açılmadan, şeffaf bir ekranda
 * alt sayfa gösterir:
 *
 *   Paylaş (SEND)            ┐
 *   Seçili metin (PROCESS)   ┴─▶ "Yeni not" | "Bugünün notuna ekle"
 *   .md/.txt "Birlikte aç"   ───▶ "Notlarıma kopyala ve aç" | "Vazgeç"
 *
 * Seçili metin menüsü Android 6'dan itibaren var; eski sürümlerde yalnızca
 * Paylaş görünür.
 */
class ShareActivity : AppCompatActivity() {

    /** Bir işlem başladıysa sayfa kapanınca ekran hemen kapanmasın. */
    private var islemde = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema manifestte şeffaf; setTheme ile değiştirilirse arkadaki uygulama görünmez olur.
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> dosyaSor(intent.data)
            Intent.ACTION_PROCESS_TEXT -> metinSor(disaridanMetin())
            else -> metinSor(paylasilanMetin())
        }
    }

    private fun disaridanMetin(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        } else {
            null
        }

    private fun paylasilanMetin(): String? {
        val metin = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val baslik = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return if (baslik.isNullOrBlank()) metin else "$baslik\n\n$metin"
    }

    private fun metinSor(metin: String?) {
        if (metin.isNullOrBlank()) {
            finish()
            return
        }
        AltSayfa(this)
            .baslik(ozetle(metin))
            .madde(R.drawable.ic_arti_koyu, getString(R.string.yeni_not_olarak)) {
                calistir(R.string.not_kaydedildi) { depo -> depo.notOlustur(metin) }
            }
            .madde(R.drawable.ic_gunluk, getString(R.string.bugunun_notuna_ekle)) {
                calistir(R.string.bugunun_notuna_eklendi) { depo ->
                    Sablonlar.bugununNotunaEkle(this, depo, metin)
                }
            }
            .kapaninca { if (!islemde) finish() }
            .goster()
    }

    private fun dosyaSor(adres: Uri?) {
        if (adres == null) {
            finish()
            return
        }
        val ad = dosyaAdi(adres)
        AltSayfa(this)
            .baslik(getString(R.string.dosya_notlara_kopyala_soru, ad))
            .madde(R.drawable.ic_arti_koyu, getString(R.string.dosya_kopyala_ac)) {
                islemde = true
                val uygulama = applicationContext
                NotDeposu.yazici.execute {
                    val depo = NotDeposu(uygulama)
                    // Dev dosyayı belleğe almayalım; not için 1 MB fazlasıyla yeter.
                    val icerik = depo.okuKesin(adres, DOSYA_SINIRI)
                    val yeni = icerik?.let { depo.notOlustur(it, null, ad.substringBeforeLast('.')) }
                    runOnUiThread {
                        if (yeni == null) {
                            Toast.makeText(uygulama, R.string.not_acilamadi, Toast.LENGTH_LONG).show()
                        } else {
                            startActivity(
                                Intent(this, EditorActivity::class.java)
                                    .putExtra("uri", yeni.toString())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        finish()
                    }
                }
            }
            .madde(R.drawable.ic_kapat, getString(R.string.vazgec)) {}
            .kapaninca { if (!islemde) finish() }
            .goster()
    }

    /** Arka planda çalışır, bitince kısa bir bildirim gösterip ekranı kapatır. */
    private fun calistir(basariMesaji: Int, is_: (NotDeposu) -> Uri?) {
        islemde = true
        val uygulama = applicationContext
        NotDeposu.yazici.execute {
            val sonuc = runCatching { is_(NotDeposu(uygulama)) }.getOrNull()
            NotWidget.hepsiniGuncelle(uygulama)
            runOnUiThread {
                val mesaj = if (sonuc != null) basariMesaji else R.string.yedek_hata
                Toast.makeText(uygulama, mesaj, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun ozetle(metin: String): String {
        val tek = metin.trim().replace(Regex("\\s+"), " ")
        return if (tek.length > OZET) tek.take(OZET) + "…" else tek
    }

    private fun dosyaAdi(adres: Uri): String {
        try {
            contentResolver.query(adres, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
            }
        } catch (_: Exception) {
        }
        return adres.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.app_name)
    }

    private companion object {
        const val OZET = 120
        const val DOSYA_SINIRI = 1024 * 1024
    }
}
