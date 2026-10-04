package com.ekosistem.takvim

/**
 * Etkinlik araması. Sorgu kelimelere bölünür, her kelime başlık/konum/açıklamada
 * (Türkçe harften bağımsız: "toplanti" → "Toplantı", "ISIK" → "ışık") geçmelidir.
 * Takvim deposunun kendi araması SQLite'ın yalnız ASCII büyük-küçük eşlemesine
 * güvendiği için Türkçe'de eksik buluyordu; arama burada yapılır.
 */
object Arama {

    /** Harfleri ASCII'ye ve küçüğe çevirir; uzunluk korunur. */
    fun katla(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) {
            b.append(
                when (ch) {
                    'ı', 'İ', 'I', 'î' -> 'i'
                    'ş', 'Ş' -> 's'
                    'ç', 'Ç' -> 'c'
                    'ğ', 'Ğ' -> 'g'
                    'ö', 'Ö' -> 'o'
                    'ü', 'Ü', 'û' -> 'u'
                    'â' -> 'a'
                    else -> ch.lowercaseChar()
                }
            )
        }
        return b.toString()
    }

    fun kelimeler(sorgu: String): List<String> =
        katla(sorgu).split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun eslesir(kelimeler: List<String>, o: Ornek): Boolean {
        if (kelimeler.isEmpty()) return false
        val saman = katla(o.baslik + "\n" + o.konum + "\n" + o.aciklama)
        return kelimeler.all { it in saman }
    }

    /**
     * Sonuçları sıralar: bugünden itibaren yaklaşanlar (yakından uzağa), sonra geçmiş
     * (yeniden eskiye). Tekrarlayan etkinlik her tekrarıyla değil bir kez, en yakın örneğiyle
     * görünür. [enCok] sonuçtan fazlası kesilir.
     */
    fun sirala(sonuc: List<Ornek>, simdi: Long, enCok: Int = 300): Pair<List<Ornek>, List<Ornek>> {
        val (gelecek, gecmis) = sonuc.partition { it.bitis.coerceAtLeast(it.baslangic) >= simdi }
        val g = gelecek.sortedBy { it.baslangic }.tekilleştir()
        val e = gecmis.sortedByDescending { it.baslangic }.tekilleştir()
        val yaklasan = g.take(enCok)
        return yaklasan to e.filter { eski -> g.none { it.etkinlikId == eski.etkinlikId && it.tekrarli } }
            .take(maxOf(0, enCok - yaklasan.size))
    }

    /** Aynı etkinliğin tekrarları sıradaki ilk örnekte toplanır; tek seferlikler olduğu gibi kalır. */
    private fun List<Ornek>.tekilleştir(): List<Ornek> {
        val gorulen = HashSet<Long>()
        return filter { !it.tekrarli || gorulen.add(it.etkinlikId) }
    }
}
