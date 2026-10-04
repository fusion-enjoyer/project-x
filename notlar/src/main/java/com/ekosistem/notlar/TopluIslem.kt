package com.ekosistem.notlar

/**
 * Birden çok notu etkileyen metin işlemleri (birleştirme, toplu etiket).
 * Disk işi çağıranda; burada yalnızca metin hesaplanır ki test edilebilsin.
 */
object TopluIslem {

    private val GECERSIZ = Regex("[^\\p{L}\\p{N}_-]")
    private val BOSLUK = Regex("\\s+")

    /** "#iş planı" gibi girdiyi etikete çevirir: işaret ve boşluk atılır. */
    fun etiketTemizle(ham: String): String = ham.trim().trimStart('#').replace(GECERSIZ, "").take(40)

    /** Notlar arada boş satırla, verilen sırayla tek metinde toplanır. */
    fun birlestir(metinler: List<String>): String =
        metinler.map { it.trimEnd() }.filter { it.isNotEmpty() }.joinToString("\n\n") + "\n"

    /**
     * Etiketi notun sonuna ekler. Not etiketi zaten taşıyorsa null döner.
     * Son satır yalnızca etiketlerden oluşuyorsa yanına, değilse ayrı satıra yazılır.
     */
    fun etiketEkle(metin: String, etiket: String): String? {
        val varMi = MarkdownBicimci.ETIKET.findAll(metin)
            .any { it.groupValues[1].equals(etiket, ignoreCase = true) }
        if (varMi) return null
        val govde = metin.trimEnd()
        val sonSatir = govde.substringAfterLast('\n').trim()
        val etiketSatiri = sonSatir.isNotEmpty() &&
            sonSatir.split(BOSLUK).all { MarkdownBicimci.ETIKET.matches(it) }
        return when {
            govde.isEmpty() -> "#$etiket\n"
            etiketSatiri -> "$govde #$etiket\n"
            else -> "$govde\n\n#$etiket\n"
        }
    }
}
