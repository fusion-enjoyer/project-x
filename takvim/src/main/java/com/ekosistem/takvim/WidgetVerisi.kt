package com.ekosistem.takvim

/**
 * Widget'ların hangi etkinlikleri göstereceğine karar veren saf mantık
 * (Android'e bağlı değil, birim testle sınanır).
 */
object WidgetVerisi {

    /** Gündem widget'ında bir satır: [gunBasi] o günün ilk etkinliği demektir (gün etiketi orada yazılır). */
    class Satir(val gun: Int, val gunBasi: Boolean, val ornek: Ornek)

    /**
     * [bugun]den başlayarak en çok [adet] satır. Bugünün bitmiş saatli etkinlikleri
     * ([simdi]den önce biten) atlanır; tüm gün etkinlikleri günün başında, saatliler
     * başlangıca göre sıralanır.
     */
    fun gundemSatirlari(ornekler: List<Ornek>, bugun: Int, simdi: Long, adet: Int, gunSayisi: Int = 14): List<Satir> {
        val sonuc = ArrayList<Satir>()
        for (g in bugun until bugun + gunSayisi) {
            val liste = ornekler.filter { it.gunuIcerir(g) && !(g == bugun && !it.tumGun && it.bitis < simdi && it.bitis > it.baslangic) }
                .sortedWith(compareBy<Ornek>({ !it.tumGun }, { it.gunBaslangicDk(g) }, { it.baslik }))
            for ((i, o) in liste.withIndex()) {
                if (sonuc.size >= adet) return sonuc
                sonuc.add(Satir(g, i == 0, o))
            }
        }
        return sonuc
    }

    /**
     * Sıradaki etkinlik: şu an süren ya da en yakın başlayan. Bugünün tüm gün
     * etkinliği, saatli etkinlik yoksa "sıradaki" sayılır.
     */
    fun siradaki(ornekler: List<Ornek>, simdi: Long, bugun: Int): Ornek? {
        val adaylar = ornekler.filter { o ->
            if (o.tumGun) o.gunuIcerir(bugun) else o.bitis.coerceAtLeast(o.baslangic) >= simdi
        }
        // Saatliler önce (başlangıca göre), tüm gün yalnız saatli yoksa.
        return adaylar.filter { !it.tumGun }.minByOrNull { it.baslangic } ?: adaylar.firstOrNull { it.tumGun }
    }
}
