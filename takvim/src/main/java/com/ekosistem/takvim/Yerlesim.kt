package com.ekosistem.takvim

/**
 * Gün/hafta görünümünde aynı saatte çakışan etkinlikler yan yana dizilir
 * (Google Takvim gibi). Her çakışma öbeği kendi sütun sayısını alır; yalnız
 * kendisiyle çakışmayan etkinlik tam genişliği kullanır.
 */
object Yerlesim {

    class Parca(val no: Int, val baslangic: Int, val bitis: Int)

    class Sonuc(val no: Int, val sutun: Int, val sutunSayisi: Int)

    /**
     * [parcalar] dakika cinsindendir. Görsel olarak çok kısa etkinlikler
     * üst üste binmesin diye çağıran bitişi en az 30 dk alır.
     */
    fun yerlestir(parcalar: List<Parca>): List<Sonuc> {
        val sirali = parcalar.sortedWith(compareBy<Parca>({ it.baslangic }, { -(it.bitis - it.baslangic) }, { it.no }))
        val sonuc = ArrayList<Sonuc>(parcalar.size)
        var obek = ArrayList<Parca>()
        var obekSutunu = ArrayList<Int>()
        var obekBitisi = Int.MIN_VALUE
        val sutunBitisleri = ArrayList<Int>()

        fun obegiKapat() {
            val adet = sutunBitisleri.size
            for (i in obek.indices) sonuc.add(Sonuc(obek[i].no, obekSutunu[i], adet))
            obek = ArrayList()
            obekSutunu = ArrayList()
            sutunBitisleri.clear()
            obekBitisi = Int.MIN_VALUE
        }

        for (p in sirali) {
            if (obek.isNotEmpty() && p.baslangic >= obekBitisi) obegiKapat()
            var sutun = sutunBitisleri.indexOfFirst { it <= p.baslangic }
            if (sutun < 0) {
                sutunBitisleri.add(p.bitis)
                sutun = sutunBitisleri.size - 1
            } else {
                sutunBitisleri[sutun] = p.bitis
            }
            obek.add(p)
            obekSutunu.add(sutun)
            obekBitisi = maxOf(obekBitisi, p.bitis)
        }
        if (obek.isNotEmpty()) obegiKapat()
        return sonuc
    }
}
