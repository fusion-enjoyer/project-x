package com.ekosistem.saat

import java.util.Calendar
import java.util.TimeZone

/**
 * Dünya saati için saf mantık (testli). Şehir listesi telefonun kendi saat
 * dilimi veritabanından gelir; internet gerekmez.
 */
object DunyaSaati {

    private val BOLGELER = setOf(
        "Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific"
    )

    /** Şehir adı gibi görünen saat dilimleri: "Europe/Istanbul" evet, "Etc/GMT+3" ve "US/Pacific" hayır. */
    fun sehirMi(kimlik: String): Boolean {
        val parcalar = kimlik.split('/')
        val son = parcalar.last()
        // "Australia/ACT", "Australia/NSW" gibi eski kısaltma takma adlar şehir değil.
        return parcalar.size >= 2 && parcalar[0] in BOLGELER && son.none { it.isDigit() } &&
            !(son.length <= 4 && son.all { it.isUpperCase() })
    }

    /** "America/Argentina/Buenos_Aires" → "Buenos Aires". */
    fun kimliktenAd(kimlik: String): String = kimlik.substringAfterLast('/').replace('_', ' ')

    /** Arama için: küçük harf, Türkçe karakterler sadeleşir ("İstanbul" ~ "istanbul" ~ "Istanbul"). */
    fun sadelestir(metin: String): String = buildString {
        for (c in metin) {
            append(
                when (c) {
                    'İ', 'I', 'ı' -> 'i'
                    'Ş', 'ş' -> 's'
                    'Ğ', 'ğ' -> 'g'
                    'Ü', 'ü' -> 'u'
                    'Ö', 'ö' -> 'o'
                    'Ç', 'ç' -> 'c'
                    else -> c.lowercaseChar()
                }
            )
        }
    }

    /** İki saat dilimi arasındaki fark (dakika): hedef − yerel. */
    fun farkDk(hedef: TimeZone, yerel: TimeZone, simdi: Long): Int =
        (hedef.getOffset(simdi) - yerel.getOffset(simdi)) / 60_000

    /** Hedefteki gün yereldekine göre: −1 dün, 0 bugün, +1 yarın. */
    fun gunFarki(hedef: TimeZone, yerel: TimeZone, simdi: Long): Int {
        fun gun(tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = simdi }
            .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        val fark = gun(hedef) - gun(yerel)
        // Yıl dönümünde fark büyük çıkar (2027001 − 2026365): hedef yeni yıldaysa yarın.
        return when {
            fark > 1 -> 1
            fark < -1 -> -1
            else -> fark
        }
    }

    /** Fark parçaları: işaret, saat, dakika (+3 sa, −5 sa 30 dk). */
    fun farkParcalari(dk: Int): Triple<Int, Int, Int> {
        val isaret = Integer.signum(dk)
        val mutlak = kotlin.math.abs(dk)
        return Triple(isaret, mutlak / 60, mutlak % 60)
    }
}
