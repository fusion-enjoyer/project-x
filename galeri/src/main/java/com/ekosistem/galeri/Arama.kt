package com.ekosistem.galeri

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Çevrimdışı arama: dosya adı, albüm adı, tarih ("ekim", "ekim 2024", "2024",
 * "october") ve tür ("video", "fotoğraf", "ekran görüntüsü"). Türkçe ve İngilizce
 * ay adları her zaman tanınır; aksan ve büyük/küçük harf fark etmez.
 */
object Arama {

    private val TR = Locale("tr")

    /** Sorguyu küçük harfli, aksansız kelimelere böler. */
    fun kelimeler(sorgu: String): List<String> =
        sade(sorgu).split(' ', '\t', ',', '.', '/', '-', '_').filter { it.isNotBlank() }

    /** "İstanbul" → "istanbul", "Ağustos" → "agustos", "Screenshot_1" → "screenshot_1". */
    fun sade(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    'İ', 'I', 'ı' -> 'i'
                    'Ş', 'ş' -> 's'
                    'Ğ', 'ğ' -> 'g'
                    'Ü', 'ü' -> 'u'
                    'Ö', 'ö' -> 'o'
                    'Ç', 'ç' -> 'c'
                    'Â', 'â' -> 'a'
                    'Î', 'î' -> 'i'
                    'Û', 'û' -> 'u'
                    else -> c.lowercaseChar()
                }
            )
        }
        return sb.toString()
    }

    private val ayAdlari: Array<List<String>> by lazy {
        val tr = DateFormatSymbols.getInstance(TR).months
        val en = DateFormatSymbols.getInstance(Locale.ENGLISH).months
        val enKisa = DateFormatSymbols.getInstance(Locale.ENGLISH).shortMonths
        Array(12) { i -> listOf(sade(tr[i]), sade(en[i]), sade(enKisa[i])).distinct() }
    }

    /** Bir medyanın aranacağı metin: bir kez kurulur, her harfte yeniden değil. */
    fun aramaMetni(m: Medya, dilim: TimeZone = TimeZone.getDefault()): String {
        val t = Calendar.getInstance(dilim).apply { timeInMillis = m.tarih }
        val ay = t.get(Calendar.MONTH)
        val sb = StringBuilder()
        sb.append(sade(m.ad)).append(' ')
        sb.append(sade(m.albumAdi)).append(' ')
        sb.append(t.get(Calendar.YEAR)).append(' ')
        sb.append(t.get(Calendar.DAY_OF_MONTH)).append(' ')
        for (a in ayAdlari[ay]) sb.append(a).append(' ')
        sb.append(if (m.video) "video " else "fotograf foto resim photo image ")
        if (ekranGoruntusuMu(m)) sb.append("ekran goruntusu screenshot ")
        if (m.mime == "image/gif") sb.append("gif ")
        return sb.toString()
    }

    fun ekranGoruntusuMu(m: Medya): Boolean {
        val a = sade(m.albumAdi)
        val d = sade(m.ad)
        return a.contains("screenshot") || a.contains("ekran") || d.startsWith("screenshot")
    }

    /** Her kelime metinde bir kelimenin başında geçmeli ("eki" → "ekim"; "kim" → değil). */
    fun eslesir(kelimeler: List<String>, metin: String): Boolean {
        if (kelimeler.isEmpty()) return false
        for (k in kelimeler) {
            var bulundu = false
            var i = metin.indexOf(k)
            while (i >= 0) {
                if (i == 0 || !metin[i - 1].isLetterOrDigit()) { bulundu = true; break }
                i = metin.indexOf(k, i + 1)
            }
            if (!bulundu) return false
        }
        return true
    }

    fun ara(sorgu: String, medyalar: List<Medya>, dilim: TimeZone = TimeZone.getDefault()): List<Medya> {
        val k = kelimeler(sorgu)
        if (k.isEmpty()) return emptyList()
        val varsayilan = dilim == TimeZone.getDefault()
        return medyalar.filter { eslesir(k, if (varsayilan) it.aramaMetni else aramaMetni(it, dilim)) }
    }
}
