package com.ekosistem.takvim

/**
 * Konum/açıklama metninde görüntülü toplantı bağlantısı arar (Meet, Zoom, Teams, Webex, Jitsi…).
 * Bulunursa ayrıntıda "Toplantıya katıl" düğmesi ve bildirimde "Katıl" eylemi çıkar. Saf mantık, testli.
 */
object Baglanti {

    private val KALIPLAR = listOf(
        Regex("""https?://meet\.google\.com/[a-z0-9\-?=&_.]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[a-z0-9\-]+\.)?zoom\.us/(?:j|my|s|w)/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[a-z0-9\-]+\.)?zoomgov\.com/j/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://teams\.microsoft\.com/l/meetup-join/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://teams\.live\.com/meet/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[a-z0-9\-]+\.)?webex\.com/[^\s<>"')]*(?:meet|join)[^\s<>"')]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://meet\.jit\.si/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://whereby\.com/[^\s<>"')]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:[a-z0-9\-]+\.)?bigbluebutton[^\s<>"')]*""", RegexOption.IGNORE_CASE)
    )

    /** İlk toplantı bağlantısı ya da null. Sondaki noktalama bağlantıdan sayılmaz. */
    fun bul(vararg metinler: String?): String? {
        for (m in metinler) {
            if (m.isNullOrBlank()) continue
            // En erken geçen bağlantı kazansın (hangi kalıp olduğuna bakmadan).
            val enIyi = KALIPLAR.mapNotNull { k -> k.find(m) }.minByOrNull { it.range.first } ?: continue
            return enIyi.value.trimEnd('.', ',', ';', ':', '!', '?', ')')
        }
        return null
    }

    /** Hangi hizmet olduğunu kısa adla söyler ("Meet", "Zoom"…); düğme etiketinde kullanılır. */
    fun hizmet(adres: String): String = when {
        "meet.google" in adres -> "Meet"
        "zoom" in adres -> "Zoom"
        "teams" in adres -> "Teams"
        "webex" in adres -> "Webex"
        "jit.si" in adres -> "Jitsi"
        "whereby" in adres -> "Whereby"
        else -> ""
    }
}
