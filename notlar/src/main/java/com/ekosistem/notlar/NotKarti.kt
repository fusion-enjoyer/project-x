package com.ekosistem.notlar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Notu, editörde göründüğü gibi bir görsele çizer (sosyal medyada, mesajda
 * paylaşmak için). Biçimlenmiş metin dışarıda hazırlanır; burada yalnızca
 * zemin, kenar boşluğu ve çok uzun notun solarak kesilmesi var.
 */
object NotKarti {

    /** Telefon ekranı genişliği: yazı boyları editördekiyle aynı oranda kalır. */
    const val GENISLIK = 1080

    private const val EN_FAZLA_BOY = GENISLIK * 3

    fun ciz(
        context: Context,
        metin: CharSequence,
        boya: TextPaint,
        satirCarpani: Float,
        satirEki: Float,
        pay: Int
    ): Bitmap {
        val duzen = duzen(metin, boya, GENISLIK - 2 * pay, satirCarpani, satirEki)
        val kesildi = duzen.height + 2 * pay > EN_FAZLA_BOY
        val boy = if (kesildi) EN_FAZLA_BOY else duzen.height + 2 * pay
        val zemin = ContextCompat.getColor(context, R.color.zemin)

        val resim = Bitmap.createBitmap(GENISLIK, boy, Bitmap.Config.ARGB_8888)
        val tuval = Canvas(resim)
        tuval.drawColor(zemin)
        tuval.save()
        tuval.translate(pay.toFloat(), pay.toFloat())
        tuval.clipRect(0, 0, GENISLIK - 2 * pay, boy - pay)
        duzen.draw(tuval)
        tuval.restore()

        if (kesildi) {
            // Kesilen yer sert bir çizgi olmasın: son kısım zemine doğru solar.
            val solma = pay * 4f
            val golge = Paint()
            golge.shader = LinearGradient(
                0f, boy - solma, 0f, boy.toFloat(),
                zemin and 0x00FFFFFF, zemin, Shader.TileMode.CLAMP
            )
            tuval.drawRect(0f, boy - solma, GENISLIK.toFloat(), boy.toFloat(), golge)
        }
        return resim
    }

    @Suppress("DEPRECATION")
    private fun duzen(
        metin: CharSequence,
        boya: TextPaint,
        genislik: Int,
        carpan: Float,
        ek: Float
    ): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(metin, 0, metin.length, boya, genislik)
                .setLineSpacing(ek, carpan)
                .setIncludePad(true)
                .build()
        } else {
            StaticLayout(metin, boya, genislik, Layout.Alignment.ALIGN_NORMAL, carpan, ek, true)
        }

    /** Görseli önbelleğe yazar ve paylaşım için adresini verir; her seferinde üzerine yazılır. */
    fun kaydet(context: Context, resim: Bitmap): Uri? = try {
        val klasor = File(context.cacheDir, "paylas")
        klasor.mkdirs()
        val dosya = File(klasor, "not.png")
        FileOutputStream(dosya).use { resim.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.dosyalar", dosya)
    } catch (_: Exception) {
        null
    } finally {
        resim.recycle()
    }
}
