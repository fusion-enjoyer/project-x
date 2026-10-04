package com.ekosistem.notlar

/**
 * Notun başlıklarından içindekiler listesi. Kod bloğu içindeki `#` satırları
 * (kabuk yorumu gibi) başlık sayılmaz. Konum, başlık satırının metindeki
 * başlangıcıdır; editör oraya kaydırır.
 */
object Icindekiler {

    data class Baslik(val seviye: Int, val metin: String, val konum: Int)

    private val BASLIK = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")

    fun basliklar(metin: String): List<Baslik> {
        val sonuc = mutableListOf<Baslik>()
        var konum = 0
        var kodIcinde = false
        for (satir in metin.split('\n')) {
            val girintisiz = satir.trimStart()
            if (girintisiz.startsWith("```") || girintisiz.startsWith("~~~")) {
                kodIcinde = !kodIcinde
            } else if (!kodIcinde) {
                BASLIK.find(satir)?.let { m ->
                    val yazi = m.groupValues[2].trim()
                    if (yazi.isNotEmpty()) sonuc.add(Baslik(m.groupValues[1].length, yazi, konum))
                }
            }
            konum += satir.length + 1
        }
        return sonuc
    }
}
