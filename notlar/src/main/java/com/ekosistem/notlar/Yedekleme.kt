package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Notların tamamını tek zip dosyasına yazar ve geri okur. İnternet gerekmez. */
object Yedekleme {

    fun dosyaAdi(): String {
        val bicim = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return "notlar-yedek-${bicim.format(Date())}.zip"
    }

    /** Başarılıysa yazılan not sayısı, hata olduysa -1. */
    fun disaAktar(context: Context, depo: NotDeposu, hedef: Uri): Int {
        return try {
            var sayi = 0
            context.contentResolver.openOutputStream(hedef)?.use { akis ->
                ZipOutputStream(akis.buffered()).use { zip ->
                    sayi = klasoruYaz(context, depo, depo.kok(), "", zip)
                }
            } ?: return -1
            sayi
        } catch (_: Exception) {
            -1
        }
    }

    private fun klasoruYaz(
        context: Context,
        depo: NotDeposu,
        dizin: DocumentFile,
        onek: String,
        zip: ZipOutputStream
    ): Int {
        var sayi = 0
        for (f in dizin.listFiles()) {
            val ad = f.name ?: continue
            if (ad.startsWith(".")) continue
            if (f.isDirectory) {
                sayi += klasoruYaz(context, depo, f, "$onek$ad/", zip)
                continue
            }
            // Görseller metin değil: ham bayt kopyalanır ve not sayılmaz.
            if (onek == Gorseller.EKLER + "/") {
                baytYaz(context, f.uri, "$onek$ad", zip)
                continue
            }
            if (!ad.endsWith(".md", true) && !ad.endsWith(".txt", true)) continue
            zip.putNextEntry(ZipEntry("$onek$ad"))
            zip.write(depo.oku(f.uri).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            sayi++
        }
        return sayi
    }

    private fun baytYaz(context: Context, kaynak: Uri, yol: String, zip: ZipOutputStream) {
        try {
            context.contentResolver.openInputStream(kaynak)?.use { giris ->
                zip.putNextEntry(ZipEntry(yol))
                giris.copyTo(zip, 64 * 1024)
                zip.closeEntry()
            }
        } catch (_: Exception) {
            // Tek bir görsel okunamazsa yedeğin tamamı yanmasın.
        }
    }

    /** Başarılıysa içe aktarılan not sayısı, hata olduysa -1. */
    fun iceAktar(context: Context, depo: NotDeposu, kaynak: Uri): Int {
        return try {
            var sayi = 0
            context.contentResolver.openInputStream(kaynak)?.use { akis ->
                ZipInputStream(akis.buffered()).use { zip ->
                    var girdi = zip.nextEntry
                    while (girdi != null) {
                        val yol = girdi.name
                        if (!girdi.isDirectory && yol.startsWith(Gorseller.EKLER + "/")) {
                            // Yoldaki dizin kısmı atılır; zip'ten çıkış yapılamasın.
                            Gorseller.geriYukle(
                                context,
                                depo,
                                yol.substringAfterLast('/'),
                                zip.readBytes()
                            )
                        } else if (!girdi.isDirectory && (yol.endsWith(".md", true) ||
                                yol.endsWith(".txt", true))
                        ) {
                            val icerik = zip.readBytes().toString(Charsets.UTF_8)
                            val klasor = yol.substringBeforeLast('/', "")
                                .takeIf { it.isNotEmpty() && !it.startsWith(".") }
                            if (klasor != null) depo.klasorOlustur(klasor)
                            if (depo.notOlustur(icerik, klasor) != null) sayi++
                        }
                        zip.closeEntry()
                        girdi = zip.nextEntry
                    }
                }
            } ?: return -1
            sayi
        } catch (_: Exception) {
            -1
        }
    }
}
