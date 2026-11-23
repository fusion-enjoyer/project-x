package com.ekosistem.galeri

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Android'e bağlı olmayan dikdörtgen (testlerde RectF boş taslak). */
data class Kutu(val sol: Float, val ust: Float, val sag: Float, val alt: Float) {
    val genislik get() = sag - sol
    val yukseklik get() = alt - ust
}

/** Tam sayı piksel dikdörtgeni: [sol, sag) × [ust, alt). */
data class PikselKutu(val sol: Int, val ust: Int, val sag: Int, val alt: Int) {
    val genislik get() = sag - sol
    val yukseklik get() = alt - ust
}

/**
 * Kırpma ve döndürmenin hesabı. Ekranda görünen resim, ham pikselin saat yönünde
 * [aci] derece dönmüş hâlidir (EXIF yönü + kullanıcının döndürmesi). Kullanıcının
 * çizdiği kutu ekrandaki resme göre 0..1 aralığındadır; kaydederken ham piksellere çevrilir.
 */
object Kirpma {

    fun aciDuzelt(aci: Int): Int = ((aci % 360) + 360) % 360

    /** Ekrandaki (dönmüş) resmin genişliği/yüksekliği. */
    fun donmusBoyut(gen: Int, yuk: Int, aci: Int): Pair<Int, Int> =
        if (aciDuzelt(aci) % 180 == 0) gen to yuk else yuk to gen

    /** Ekrandaki 0..1 kutuyu ham resmin 0..1 kutusuna çevirir. */
    fun hamaCevir(k: Kutu, aci: Int): Kutu = when (aciDuzelt(aci)) {
        90 -> Kutu(k.ust, 1 - k.sag, k.alt, 1 - k.sol)
        180 -> Kutu(1 - k.sag, 1 - k.alt, 1 - k.sol, 1 - k.ust)
        270 -> Kutu(1 - k.alt, k.sol, 1 - k.ust, k.sag)
        else -> k
    }

    /** Ham resimde kesilecek piksel bölgesi (en az 1×1, resim içinde). */
    fun hamPiksel(k: Kutu, aci: Int, gen: Int, yuk: Int): PikselKutu {
        val h = hamaCevir(k, aci)
        val sol = (h.sol * gen).roundToInt().coerceIn(0, gen - 1)
        val ust = (h.ust * yuk).roundToInt().coerceIn(0, yuk - 1)
        val sag = (h.sag * gen).roundToInt().coerceIn(sol + 1, gen)
        val alt = (h.alt * yuk).roundToInt().coerceIn(ust + 1, yuk)
        return PikselKutu(sol, ust, sag, alt)
    }

    /**
     * Bölgeyi belleğe sığacak kadar küçültme katı (2'nin kuvveti): sonuç en çok
     * [enCokPiksel] piksel. Eski telefonlarda 8 MP ≈ 32 MB; döndürürken iki kopya olur.
     */
    fun ornekleme(gen: Int, yuk: Int, enCokPiksel: Long = 8_000_000L): Int {
        var kat = 1
        while ((gen.toLong() / kat) * (yuk.toLong() / kat) > enCokPiksel) kat *= 2
        return kat
    }

    /** Görüntülemek için: en uzun kenar [hedef] pikselden büyükse 2'nin kuvvetiyle küçült. */
    fun ekranOrneklemesi(gen: Int, yuk: Int, hedef: Int): Int {
        var kat = 1
        while (max(gen, yuk) / (kat * 2) >= hedef) kat *= 2
        return kat
    }

    /**
     * [oran] (genişlik/yükseklik) verilmişse [sinir] içine sığan en büyük ortalı kutu;
     * null ise sınırın tamamı.
     */
    fun ortalaKutu(sinir: Kutu, oran: Float?): Kutu {
        if (oran == null || oran <= 0f) return sinir
        val g = sinir.genislik
        val y = sinir.yukseklik
        val (yg, yy) = if (g / y > oran) (y * oran) to y else g to (g / oran)
        val cx = (sinir.sol + sinir.sag) / 2
        val cy = (sinir.ust + sinir.alt) / 2
        return Kutu(cx - yg / 2, cy - yy / 2, cx + yg / 2, cy + yy / 2)
    }

    /** Kutuyu sınırın içinde tutarak kaydırır. */
    fun kaydir(k: Kutu, dx: Float, dy: Float, sinir: Kutu): Kutu {
        val x = dx.coerceIn(sinir.sol - k.sol, sinir.sag - k.sag)
        val y = dy.coerceIn(sinir.ust - k.ust, sinir.alt - k.alt)
        return Kutu(k.sol + x, k.ust + y, k.sag + x, k.alt + y)
    }

    /** Sürüklenen kenarlar. Köşe = iki kenar. */
    data class Tutamak(val sol: Boolean, val ust: Boolean, val sag: Boolean, val alt: Boolean) {
        val koseMi get() = (sol || sag) && (ust || alt)
    }

    /**
     * Kenar/köşe sürükleme. Oran kilitliyse yalnız köşeler çalışır ve karşı köşe
     * sabit kalır. Kutu [enAz]'dan küçülmez, sınırdan taşmaz.
     */
    fun boyutla(k: Kutu, t: Tutamak, x: Float, y: Float, sinir: Kutu, oran: Float?, enAz: Float): Kutu {
        if (oran != null) {
            if (!t.koseMi) return k
            val sabitX = if (t.sol) k.sag else k.sol
            val sabitY = if (t.ust) k.alt else k.ust
            val enCokG = if (t.sol) sabitX - sinir.sol else sinir.sag - sabitX
            val enCokY = if (t.ust) sabitY - sinir.ust else sinir.alt - sabitY
            var g = max(abs(x - sabitX), abs(y - sabitY) * oran)
            g = min(g, min(enCokG, enCokY * oran))
            g = max(g, max(enAz, enAz * oran))
            g = min(g, min(enCokG, enCokY * oran))
            val yy = g / oran
            val sol = if (t.sol) sabitX - g else sabitX
            val ust = if (t.ust) sabitY - yy else sabitY
            return Kutu(sol, ust, sol + g, ust + yy)
        }
        var (sol, ust, sag, alt) = k
        if (t.sol) sol = x.coerceIn(sinir.sol, sag - enAz)
        if (t.sag) sag = x.coerceIn(sol + enAz, sinir.sag)
        if (t.ust) ust = y.coerceIn(sinir.ust, alt - enAz)
        if (t.alt) alt = y.coerceIn(ust + enAz, sinir.alt)
        return Kutu(sol, ust, sag, alt)
    }

    private fun abs(f: Float) = kotlin.math.abs(f)

    /** Dokunulan noktaya göre tutamak; [pay] parmak payı. Hiçbiri değilse null. */
    fun tutamakBul(k: Kutu, x: Float, y: Float, pay: Float): Tutamak? {
        val icindeX = x > k.sol - pay && x < k.sag + pay
        val icindeY = y > k.ust - pay && y < k.alt + pay
        if (!icindeX || !icindeY) return null
        val sol = abs(x - k.sol) < pay
        val sag = !sol && abs(x - k.sag) < pay
        val ust = abs(y - k.ust) < pay
        val alt = !ust && abs(y - k.alt) < pay
        if (!sol && !sag && !ust && !alt) return null
        return Tutamak(sol, ust, sag, alt)
    }
}
