package com.ekosistem.notlar

/**
 * Satır bazlı fark hesabı (en uzun ortak alt dizi). Sürüm geçmişinde "bu sürüme
 * dönersem ne değişir" sorusunu yanıtlar: kullanıcı önce farkı görür, sonra onaylar.
 */
object Fark {

    const val AYNI = 0

    /** Sürüme dönülürse geri gelecek satır. */
    const val EKLENEN = 1

    /** Sürüme dönülürse kaybolacak satır. */
    const val SILINEN = 2

    data class Satir(val tur: Int, val metin: String)

    /** [oncesi] güncel not, [sonrasi] dönülmek istenen sürüm. */
    fun hesapla(oncesi: List<String>, sonrasi: List<String>): List<Satir> {
        // Ortak baş ve son kırpılır; büyük notlarda tabloyu ciddi biçimde küçültür.
        var bas = 0
        while (bas < oncesi.size && bas < sonrasi.size && oncesi[bas] == sonrasi[bas]) bas++
        var son = 0
        while (son < oncesi.size - bas && son < sonrasi.size - bas &&
            oncesi[oncesi.size - 1 - son] == sonrasi[sonrasi.size - 1 - son]
        ) son++

        val sonuc = mutableListOf<Satir>()
        for (i in 0 until bas) sonuc.add(Satir(AYNI, oncesi[i]))
        sonuc.addAll(
            ortaFark(
                oncesi.subList(bas, oncesi.size - son),
                sonrasi.subList(bas, sonrasi.size - son)
            )
        )
        for (i in oncesi.size - son until oncesi.size) sonuc.add(Satir(AYNI, oncesi[i]))
        return sonuc
    }

    fun sayac(fark: List<Satir>): Pair<Int, Int> =
        fark.count { it.tur == EKLENEN } to fark.count { it.tur == SILINEN }

    private fun ortaFark(a: List<String>, b: List<String>): List<Satir> {
        if (a.isEmpty() && b.isEmpty()) return emptyList()
        if (a.isEmpty()) return b.map { Satir(EKLENEN, it) }
        if (b.isEmpty()) return a.map { Satir(SILINEN, it) }
        // Devasa notlarda tablo belleğe sığmaz; o zaman blok değişimi gösterilir.
        if (a.size.toLong() * b.size > SINIR) {
            return a.map { Satir(SILINEN, it) } + b.map { Satir(EKLENEN, it) }
        }

        val n = a.size
        val m = b.size
        val genislik = m + 1
        val tablo = IntArray((n + 1) * genislik)
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                tablo[i * genislik + j] = if (a[i] == b[j]) {
                    tablo[(i + 1) * genislik + (j + 1)] + 1
                } else {
                    maxOf(tablo[(i + 1) * genislik + j], tablo[i * genislik + (j + 1)])
                }
            }
        }

        val sonuc = mutableListOf<Satir>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    sonuc.add(Satir(AYNI, a[i])); i++; j++
                }
                tablo[(i + 1) * genislik + j] >= tablo[i * genislik + (j + 1)] -> {
                    sonuc.add(Satir(SILINEN, a[i])); i++
                }
                else -> {
                    sonuc.add(Satir(EKLENEN, b[j])); j++
                }
            }
        }
        while (i < n) {
            sonuc.add(Satir(SILINEN, a[i])); i++
        }
        while (j < m) {
            sonuc.add(Satir(EKLENEN, b[j])); j++
        }
        return sonuc
    }

    private const val SINIR = 400_000L
}
