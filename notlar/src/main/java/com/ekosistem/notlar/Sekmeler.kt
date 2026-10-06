package com.ekosistem.notlar

import android.content.Context

/**
 * Editör sekmeleri (Obsidian'daki gibi). Her sekme bir not geçmişidir:
 * bağlantıyla gidilen not geçmişe eklenir, alt çubuktaki ← → onda gezer.
 * Sekmeler uygulama kapansa da hatırlanır.
 *
 * Kayıtlardaki adres notun adresidir; henüz kaydedilmemiş yeni not için
 * [YENI] ile başlayan geçici bir ad tutulur, not kaydedilince adresiyle
 * değiştirilir ([adresDegistir]), boş bırakılırsa silinir ([kaldir]).
 */
class Sekme(val id: Long, val gecmis: MutableList<String>, var konum: Int) {
    val adres: String get() = gecmis[konum]
    val geriVar: Boolean get() = konum > 0
    val ileriVar: Boolean get() = konum < gecmis.lastIndex
}

/** Sekmelerin saf mantığı; Android'e dokunmaz, testlerde doğrudan denenir. */
class SekmeDurumu {
    val sekmeler = mutableListOf<Sekme>()
    var etkinId = 0L
        private set

    val etkin: Sekme? get() = sekmeler.firstOrNull { it.id == etkinId } ?: sekmeler.lastOrNull()

    /**
     * Uygulamanın başka yerinden (liste, widget, bildirim) açılan not. Başka
     * bir sekmede zaten açıksa o sekmeye geçilir; yoksa etkin sekmede açılır.
     */
    fun ac(adres: String): Sekme {
        sekmeler.firstOrNull { it.adres == adres }?.let {
            etkinId = it.id
            return it
        }
        return git(adres)
    }

    /** Etkin sekmede başka nota gidilir (bağlantı, hızlı geçiş); ilerideki geçmiş silinir. */
    fun git(adres: String): Sekme {
        val s = etkin ?: return yeniSekme(adres)
        etkinId = s.id
        if (s.adres == adres) return s
        while (s.gecmis.lastIndex > s.konum) s.gecmis.removeAt(s.gecmis.lastIndex)
        s.gecmis.add(adres)
        s.konum = s.gecmis.lastIndex
        while (s.gecmis.size > EN_UZUN_GECMIS) {
            s.gecmis.removeAt(0)
            s.konum--
        }
        return s
    }

    fun yeniSekme(adres: String): Sekme {
        val s = Sekme((sekmeler.maxOfOrNull { it.id } ?: 0L) + 1, mutableListOf(adres), 0)
        sekmeler.add(s)
        etkinId = s.id
        return s
    }

    fun sec(id: Long): Sekme? = sekmeler.firstOrNull { it.id == id }?.also { etkinId = it.id }

    /** Etkin sekmede bir önceki (yon = -1) ya da sonraki (yon = 1) not; yoksa null. */
    fun gez(yon: Int): Sekme? {
        val s = etkin ?: return null
        val yeni = s.konum + yon
        if (yeni !in s.gecmis.indices) return null
        s.konum = yeni
        etkinId = s.id
        return s
    }

    /** Sekmeyi kapatır; etkin sekme kapandıysa yanındaki etkin olur. */
    fun kapat(id: Long) {
        val sira = sekmeler.indexOfFirst { it.id == id }
        if (sira < 0) return
        sekmeler.removeAt(sira)
        if (etkinId == id) {
            etkinId = sekmeler.getOrNull(minOf(sira, sekmeler.lastIndex))?.id ?: 0L
        }
    }

    fun digerleriniKapat(id: Long) {
        sekmeler.removeAll { it.id != id }
        if (sekmeler.isNotEmpty()) etkinId = id
    }

    /** Silinen not bütün geçmişlerden çıkar; boşalan sekme kapanır. */
    fun kaldir(adres: String) {
        for (s in sekmeler.toList()) {
            if (adres !in s.gecmis) continue
            var konum = s.konum
            val kalan = mutableListOf<String>()
            for ((i, a) in s.gecmis.withIndex()) {
                if (a == adres) {
                    if (i <= s.konum) konum--
                    continue
                }
                // Arka arkaya aynı not kalmasın (A, B, A'dan B silinince).
                if (kalan.lastOrNull() == a) {
                    if (i <= s.konum) konum--
                    continue
                }
                kalan.add(a)
            }
            if (kalan.isEmpty()) {
                kapat(s.id)
                continue
            }
            s.gecmis.clear()
            s.gecmis.addAll(kalan)
            s.konum = konum.coerceIn(0, kalan.lastIndex)
        }
    }

    /** Not taşındı ya da yeni not kaydedildi: eski adres her yerde yenisiyle değişir. */
    fun adresDegistir(degisim: Map<String, String>): Boolean {
        var degisti = false
        for (s in sekmeler) {
            for (i in s.gecmis.indices) {
                val yeni = degisim[s.gecmis[i]] ?: continue
                s.gecmis[i] = yeni
                degisti = true
            }
        }
        return degisti
    }

    /** Satır başına bir sekme: kimlik, konum, adresler (sekmeyle ayrılmış). İlk satır etkin sekme. */
    fun yaz(): String = buildString {
        append(etkinId)
        for (s in sekmeler) {
            append('\n').append(s.id).append('\t').append(s.konum)
            for (a in s.gecmis) append('\t').append(a)
        }
    }

    companion object {
        /** Sekme başına en fazla bu kadar geri gidilebilir. */
        const val EN_UZUN_GECMIS = 50

        fun oku(metin: String?): SekmeDurumu {
            val d = SekmeDurumu()
            val satirlar = metin?.split('\n') ?: return d
            for (satir in satirlar.drop(1)) {
                val parca = satir.split('\t')
                val id = parca.getOrNull(0)?.toLongOrNull() ?: continue
                val konum = parca.getOrNull(1)?.toIntOrNull() ?: continue
                val gecmis = parca.drop(2).filter { it.isNotEmpty() }.toMutableList()
                if (gecmis.isEmpty()) continue
                d.sekmeler.add(Sekme(id, gecmis, konum.coerceIn(0, gecmis.lastIndex)))
            }
            d.etkinId = satirlar.first().toLongOrNull() ?: 0L
            return d
        }
    }
}

/**
 * Uygulamanın tek sekme durumu. Her değişiklik hemen kaydedilir; yeni not
 * kaydı arka plandan da çağırdığı için erişim kilitli.
 */
object Sekmeler {
    /** Kaydedilmemiş yeni notun geçici adı bununla başlar. */
    const val YENI = "yeni:"

    private var durum: SekmeDurumu? = null

    private fun sp(c: Context) = c.getSharedPreferences("notlar", Context.MODE_PRIVATE)

    @Synchronized
    fun <T> degistir(c: Context, islem: (SekmeDurumu) -> T): T {
        val d = durum ?: SekmeDurumu.oku(sp(c).getString("sekmeler", null)).also { durum = it }
        val sonuc = islem(d)
        sp(c).edit().putString("sekmeler", d.yaz()).apply()
        return sonuc
    }

    @Synchronized
    fun <T> oku(c: Context, islem: (SekmeDurumu) -> T): T {
        val d = durum ?: SekmeDurumu.oku(sp(c).getString("sekmeler", null)).also { durum = it }
        return islem(d)
    }

    fun yeniAd(): String = YENI + System.nanoTime()

    fun kaldir(c: Context, adres: String) = degistir(c) { it.kaldir(adres) }

    fun adresleriTasi(c: Context, degisim: Map<String, String>) {
        if (oku(c) { d -> d.sekmeler.none { s -> s.gecmis.any { it in degisim } } }) return
        degistir(c) { it.adresDegistir(degisim) }
    }
}
