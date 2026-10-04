package com.ekosistem.notlar

/**
 * Yardım metni (`res/raw/yardim.md`) parçalara ayrılır: düz anlatım ve örnekler.
 * Örnek `~~~ornek` ile açılır, `~~~` ile kapanır; ekranda yazılan hâli ve
 * uygulamadaki görünüşü alt alta gösterilir. `~~~yalin` yalnızca yazılan hâli
 * gösterir (görsel bağlantısı gibi biçimlenince anlamını yitirenler için).
 * Çit için tilde kullanılır ki örneğin içinde ``` kod bloğu yazılabilsin.
 */
object Yardim {

    const val METIN = 0
    const val ORNEK = 1
    const val YALIN = 2

    data class Parca(val tur: Int, val icerik: String)

    fun bol(kaynak: String): List<Parca> {
        val sonuc = mutableListOf<Parca>()
        val tampon = StringBuilder()
        var tur = METIN
        fun bosalt() {
            val icerik = tampon.toString().trim('\n')
            if (icerik.isNotBlank()) sonuc.add(Parca(tur, icerik))
            tampon.setLength(0)
        }
        for (satir in kaynak.lines()) {
            val temiz = satir.trim()
            when {
                tur == METIN && (temiz == "~~~ornek" || temiz == "~~~yalin") -> {
                    bosalt()
                    tur = if (temiz == "~~~ornek") ORNEK else YALIN
                }
                tur != METIN && temiz == "~~~" -> {
                    bosalt()
                    tur = METIN
                }
                else -> tampon.append(satir).append('\n')
            }
        }
        bosalt()
        return sonuc
    }
}
