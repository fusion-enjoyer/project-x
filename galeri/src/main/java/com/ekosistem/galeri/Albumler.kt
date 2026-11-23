package com.ekosistem.galeri

/** Medya listesinden albüm listesi: kapak en yeni öge, sıra en yeni albüm önce. */
object Albumler {

    /** [medyalar] yeniden eskiye sıralı gelir. */
    fun kur(medyalar: List<Medya>): List<Album> {
        val gruplar = LinkedHashMap<String, MutableList<Medya>>()
        for (m in medyalar) gruplar.getOrPut(m.albumId) { ArrayList() }.add(m)
        return gruplar.map { (id, liste) ->
            Album(id, liste.first().albumAdi, liste.first(), liste.size)
        }.sortedWith(
            // Kamera her zaman en başta: en çok bakılan klasör.
            compareByDescending<Album> { kameraMi(it.ad) }.thenByDescending { it.kapak.tarih }
        )
    }

    fun kameraMi(ad: String): Boolean = ad.equals("Camera", true) || ad.equals("Kamera", true) || ad == "100ANDRO"
}
