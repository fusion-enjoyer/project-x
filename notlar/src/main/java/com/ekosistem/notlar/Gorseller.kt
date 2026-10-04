package com.ekosistem.notlar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import java.util.Collections

/**
 * Not içindeki görseller. Dosyalar not klasörünün yanındaki `ekler/` klasörüne
 * kopyalanır, Markdown'a `![](ekler/ad.jpg)` olarak yazılır — yani not başka bir
 * uygulamada (Obsidian gibi) açıldığında da görsel yerinde durur.
 *
 * Çözümleme ve bit eşlem çözme pahalı olduğu için ikisi de önbelleklenir:
 * biçimlendirici her tuş vuruşunda çalışıyor, oraya disk erişimi giremez.
 */
object Gorseller {

    const val EKLER = "ekler"

    /** Görselin kapladığı en fazla yükseklik, ekranın yarısı kadar. */
    private const val YUKSEKLIK_ORANI = 0.5f

    private val anaIs = Handler(Looper.getMainLooper())

    private val onbellek = object : LruCache<String, Bitmap>(bellekSiniri()) {
        override fun sizeOf(anahtar: String, deger: Bitmap): Int = deger.byteCount
    }

    /** Aynı görseli iki kez çözmeye çalışma; çözülemeyeni de tekrar deneme. */
    private val yukleniyor = Collections.synchronizedSet(mutableSetOf<String>())
    private val basarisiz = Collections.synchronizedSet(mutableSetOf<String>())
    private val adresler = Collections.synchronizedMap(mutableMapOf<String, Uri?>())

    private fun bellekSiniri(): Int =
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(2L * 1024 * 1024, 24L * 1024 * 1024).toInt()

    /** Yeni görsel eklendiğinde yol → adres eşlemesi tazelensin. */
    fun adresleriUnut() {
        adresler.clear()
        basarisiz.clear()
    }

    // --- Seçici ---

    /**
     * Sistemin fotoğraf seçicisi (Photo Picker): Android 13'te geldi, Android
     * 11-12'ye sistem güncellemesiyle taşındı (SDK uzantısı R ≥ 2). İzin
     * istemez, galeriyle aynı arayüzü sunar ve cihazla bütün durur.
     */
    fun fotoSeciciVar(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Uzanti.surum() >= 2
        else -> false
    }

    /** SdkExtensions Android 11'de geldi; eski sürümde sınıf hiç yüklenmesin. */
    @RequiresApi(Build.VERSION_CODES.R)
    private object Uzanti {
        fun surum(): Int = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R)
    }

    /** Fotoğraf seçicinin niyeti; yalnızca [fotoSeciciVar] doğruyken kullanılır. */
    @SuppressLint("NewApi", "InlinedApi")
    fun fotoSeciciNiyeti(): Intent =
        Intent(MediaStore.ACTION_PICK_IMAGES)
            .setType("image/*")
            .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())

    /** Fotoğraf seçicisi olmayan sürümlerde belge seçici (görsellerle süzülü). */
    fun belgeSeciciNiyeti(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

    // --- Yol çözümleme ---

    /**
     * `ekler/ad.jpg` ya da `../ekler/ad.jpg` gibi göreli yolu gerçek dosyaya
     * çevirir. Dosya arama SAF'ta sorgu demek, biçimlendirici ise her tuş
     * vuruşunda çalışıyor: sonuç önbellekte yoksa null döner, çözüm arka planda
     * yapılır ve bitince [hazir] çağrılır.
     *
     * `.` ve `..` atılır; ekler her zaman kök klasörün altındadır, `..` yalnızca
     * alt klasördeki notun yazdığı yolda bulunur.
     */
    fun adres(depo: NotDeposu, yol: String, hazir: () -> Unit): Uri? {
        val temiz = yol.trim()
        if (temiz.isEmpty()) return null
        if (temiz.startsWith("content://") || temiz.startsWith("file://")) {
            return runCatching { Uri.parse(temiz) }.getOrNull()
        }
        if (adresler.containsKey(temiz)) return adresler[temiz]
        if (!yukleniyor.add("yol:$temiz")) return null

        Thread {
            val bulunan = coz(depo, temiz)
            adresler[temiz] = bulunan
            yukleniyor.remove("yol:$temiz")
            if (bulunan != null) anaIs.post { hazir() }
        }.start()
        return null
    }

    private fun coz(depo: NotDeposu, yol: String): Uri? = try {
        val parcalar = yol.split('/')
            .map { Uri.decode(it) }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        var dosya: DocumentFile? = depo.kok()
        for ((i, parca) in parcalar.withIndex()) {
            dosya = dosya?.let { depo.cocukBul(it, parca) }
            if (dosya == null) break
            if (i < parcalar.size - 1 && !dosya.isDirectory) {
                dosya = null
                break
            }
        }
        dosya?.takeIf { it.isFile }?.uri
    } catch (_: Exception) {
        null
    }

    // --- Bit eşlem ---

    fun bitmap(uri: Uri, genislik: Int): Bitmap? = onbellek.get(anahtar(uri, genislik))

    /** Görseli arka planda çözer; bitince [hazir] ana iş parçacığında çağrılır. */
    fun yukle(context: Context, uri: Uri, genislik: Int, hazir: () -> Unit) {
        val anahtar = anahtar(uri, genislik)
        if (basarisiz.contains(anahtar) || !yukleniyor.add(anahtar)) return
        val uygulama = context.applicationContext
        val enFazlaYukseklik =
            (uygulama.resources.displayMetrics.heightPixels * YUKSEKLIK_ORANI).toInt()
        Thread {
            val bitmap = coz(uygulama, uri, genislik, enFazlaYukseklik)
            if (bitmap != null) onbellek.put(anahtar, bitmap) else basarisiz.add(anahtar)
            yukleniyor.remove(anahtar)
            if (bitmap != null) anaIs.post { hazir() }
        }.start()
    }

    private fun anahtar(uri: Uri, genislik: Int): String = "$uri@$genislik"

    /**
     * Tam çözünürlüklü fotoğraf 1 GB'lık telefonda belleği bitirir; önce boyut
     * okunur, sonra hedefe sığacak en küçük 2'nin katıyla örneklenerek çözülür.
     */
    private fun coz(context: Context, uri: Uri, genislik: Int, yukseklik: Int): Bitmap? {
        if (genislik <= 0) return null
        return try {
            val olcu = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, olcu)
            }
            if (olcu.outWidth <= 0 || olcu.outHeight <= 0) return null

            var ornek = 1
            while (olcu.outWidth / (ornek * 2) >= genislik &&
                olcu.outHeight / (ornek * 2) >= yukseklik
            ) ornek *= 2

            val secenekler = BitmapFactory.Options().apply {
                inSampleSize = ornek
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val ham = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, secenekler)
            } ?: return null

            olcekle(ham, genislik, yukseklik)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun olcekle(ham: Bitmap, genislik: Int, yukseklik: Int): Bitmap {
        val oran = minOf(
            genislik.toFloat() / ham.width,
            yukseklik.toFloat() / ham.height,
            1f
        )
        if (oran >= 1f) return ham
        val g = (ham.width * oran).toInt().coerceAtLeast(1)
        val y = (ham.height * oran).toInt().coerceAtLeast(1)
        return try {
            val olcekli = Bitmap.createScaledBitmap(ham, g, y, true)
            if (olcekli != ham) ham.recycle()
            olcekli
        } catch (_: OutOfMemoryError) {
            ham
        }
    }

    // --- İçe alma ---

    /**
     * Seçilen görseli `ekler/` klasörüne kopyalar ve nota yazılacak göreli yolu
     * döndürür. Alt klasördeki not için yol `../ekler/...` olur ki standart
     * Markdown olarak da doğru çözülsün.
     */
    fun iceAl(context: Context, depo: NotDeposu, kaynak: Uri, notKlasoru: String?): String? {
        val klasor = depo.eklerKlasoru(true) ?: return null
        val tur = context.contentResolver.getType(kaynak) ?: "image/jpeg"
        val uzanti = MimeTypeMap.getSingleton().getExtensionFromMimeType(tur) ?: "jpg"
        val govde = tekilAd(depo.cocukAdlari(klasor), gosterilenAd(context, kaynak), uzanti)

        val hedef = klasor.createFile(tur, govde) ?: return null
        if (!kopyala(context, kaynak, hedef.uri)) {
            runCatching { hedef.delete() }
            return null
        }
        val ad = hedef.name ?: "$govde.$uzanti"
        adresleriUnut()
        val onek = if (notKlasoru == null) "" else "../"
        return "$onek$EKLER/${Uri.encode(ad, "")}"
    }

    /**
     * Yedekten çıkan görseli `ekler/` klasörüne yazar. Aynı adlı dosya varsa
     * dokunulmaz: notlardaki bağlantılar ada göre çözüldüğü için ad korunmalı.
     */
    fun geriYukle(context: Context, depo: NotDeposu, ad: String, bayt: ByteArray): Boolean =
        disaridanAl(context, depo, ad, bayt) != null

    /**
     * Dışarıdan gelen (yedek, Google Keep) görseli `ekler/`e yazar ve dosyanın
     * klasördeki adını döndürür. Sağlayıcı uzantıyı türe göre koyduğu için
     * ("a.jpeg" → "a.jpg") nota yazılacak ad buradan alınmalı.
     */
    fun disaridanAl(context: Context, depo: NotDeposu, ad: String, bayt: ByteArray): String? {
        val temizAd = ad.substringAfterLast('/').trim()
        if (temizAd.isEmpty() || temizAd.startsWith(".")) return null
        val klasor = depo.eklerKlasoru(true) ?: return null
        if (temizAd in depo.cocukAdlari(klasor)) return temizAd

        val uzanti = temizAd.substringAfterLast('.', "")
        val tur = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(uzanti.lowercase()) ?: "image/jpeg"
        val hedef = klasor.createFile(tur, temizAd.substringBeforeLast('.')) ?: return null
        return try {
            context.contentResolver.openOutputStream(hedef.uri)?.use { cikis ->
                cikis.write(bayt)
                adresleriUnut()
                hedef.name ?: temizAd
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun kopyala(context: Context, kaynak: Uri, hedef: Uri): Boolean = try {
        context.contentResolver.openInputStream(kaynak)?.use { giris ->
            context.contentResolver.openOutputStream(hedef)?.use { cikis ->
                giris.copyTo(cikis, 64 * 1024)
                true
            } ?: false
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun gosterilenAd(context: Context, uri: Uri): String {
        val ham = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { imlec ->
                    if (imlec.moveToFirst() && !imlec.isNull(0)) imlec.getString(0) else null
                }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment ?: "gorsel"

        return ham.substringAfterLast('/')
            .substringBeforeLast('.')
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .trim()
            .take(40)
            .ifBlank { "gorsel" }
    }

    private fun tekilAd(mevcutAdlar: Set<String>, govde: String, uzanti: String): String {
        var deneme = govde
        var i = 2
        while ("$deneme.$uzanti" in mevcutAdlar || deneme in mevcutAdlar) {
            deneme = "$govde-$i"
            i++
        }
        return deneme
    }
}
