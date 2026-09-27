package com.ekosistem.notlar

/**
 * Arama karşılaştırması. Telefonda çoğu kişi Türkçe karakter kullanmadan
 * yazar: "toplanti" yazınca "Toplantı" da bulunmalı. Hem aranan hem metin
 * aynı biçime getirilir: küçük harf, ı/i, ş/s, ğ/g, ü/u, ö/o, ç/c birleşir.
 *
 * Dönüşüm harf harf yapılır ve uzunluk hiç değişmez; böylece sadeleşmiş
 * metinde bulunan konum, kartta vurgulanacak asıl metindeki konumla aynıdır.
 * (String.lowercase bazı dillerde harf sayısını değiştirebiliyor.)
 */
object Arama {

    fun sadelestir(metin: String): String {
        val harfler = CharArray(metin.length)
        for (i in metin.indices) harfler[i] = harf(metin[i])
        return String(harfler)
    }

    /** Aranan ifade; boşsa null (arama yok). */
    fun ifade(ham: String?): String? = ham?.trim()?.takeIf { it.isNotEmpty() }?.let { sadelestir(it) }

    private fun harf(c: Char): Char = when (c) {
        'I', 'ı', 'İ', 'î', 'Î' -> 'i'
        'ş', 'Ş' -> 's'
        'ğ', 'Ğ' -> 'g'
        'ü', 'Ü', 'û', 'Û' -> 'u'
        'ö', 'Ö' -> 'o'
        'ç', 'Ç' -> 'c'
        'â', 'Â' -> 'a'
        else -> c.lowercaseChar()
    }
}
