package com.ekosistem.notlar

/** "Not hakkında" sayfasındaki sayımlar; Android'e bağlı değil, testli. */
object NotIstatistigi {

    /** Ortalama sessiz okuma hızı (kelime/dakika). */
    private const val OKUMA_HIZI = 200

    /**
     * İçinde harf ya da rakam olan her boşlukla ayrılmış parça bir kelimedir.
     * Böylece "- [ ]", "##", "---" gibi Markdown işaretleri sayılmaz.
     */
    fun kelime(metin: String): Int =
        metin.split(BOSLUK).count { parca -> parca.any { it.isLetterOrDigit() } }

    /** Yukarı yuvarlanmış okuma süresi; 0 ise "bir dakikadan az". */
    fun okumaDakikasi(kelime: Int): Int =
        if (kelime < OKUMA_HIZI / 2) 0 else (kelime + OKUMA_HIZI - 1) / OKUMA_HIZI

    private val BOSLUK = Regex("\\s+")
}
