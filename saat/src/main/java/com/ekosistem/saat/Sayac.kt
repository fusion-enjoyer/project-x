package com.ekosistem.saat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kronometre durumu. Saat telefonun açılıştan beri geçen süresinden
 * (elapsedRealtime) ölçülür: kullanıcı sistem saatini değiştirse de kaymaz.
 */
data class Kronometre(
    /** Çalışıyorsa son başlatma anı (elapsedRealtime); duruyorsa 0. */
    val baslangic: Long = 0,
    /** Önceki çalışmalardan biriken süre (ms). */
    val birikmis: Long = 0,
    /** Her turun bittiği andaki toplam süre (ms), sırayla. */
    val turlar: List<Long> = emptyList()
) {
    val calisiyor: Boolean get() = baslangic > 0
    val sifirda: Boolean get() = !calisiyor && birikmis == 0L && turlar.isEmpty()

    fun gecen(simdi: Long): Long = birikmis + if (calisiyor) (simdi - baslangic).coerceAtLeast(0) else 0

    fun basla(simdi: Long): Kronometre = if (calisiyor) this else copy(baslangic = simdi)

    fun durdur(simdi: Long): Kronometre =
        if (!calisiyor) this else copy(baslangic = 0, birikmis = gecen(simdi))

    fun tur(simdi: Long): Kronometre = if (!calisiyor) this else copy(turlar = turlar + gecen(simdi))

    fun sifirla(): Kronometre = Kronometre()

    /** Her turun kendi süresi (toplamlar arası fark). */
    fun turSureleri(): List<Long> = turlar.mapIndexed { i, t -> t - (if (i == 0) 0 else turlar[i - 1]) }

    fun json(): String = JSONObject()
        .put("baslangic", baslangic)
        .put("birikmis", birikmis)
        .put("turlar", JSONArray(turlar))
        .toString()

    companion object {
        fun jsondan(metin: String?): Kronometre {
            if (metin.isNullOrEmpty()) return Kronometre()
            return runCatching {
                val o = JSONObject(metin)
                val dizi = o.optJSONArray("turlar") ?: JSONArray()
                Kronometre(
                    o.optLong("baslangic"),
                    o.optLong("birikmis"),
                    List(dizi.length()) { dizi.getLong(it) }
                )
            }.getOrDefault(Kronometre())
        }

        /** "1:02:03.4" ya da "02:03.4" (onda bir saniye). */
        fun bicim(ms: Long, ondalik: Boolean = true): String {
            val toplam = ms.coerceAtLeast(0)
            val sa = toplam / 3_600_000
            val dk = (toplam / 60_000) % 60
            val sn = (toplam / 1000) % 60
            val onda = (toplam / 100) % 10
            val ana = if (sa > 0) "%d:%02d:%02d".format(sa, dk, sn) else "%02d:%02d".format(dk, sn)
            return if (ondalik) "$ana.$onda" else ana
        }
    }
}

/**
 * Bir zamanlayıcı. Çalışırken bitiş anı (duvar saati, setAlarmClock ile
 * kurulur) tutulur; duraklatılınca kalan süre.
 */
data class Zamanlayici(
    val id: Int,
    /** Kurulan toplam süre (ms); "yeniden başlat" buna döner. */
    val sure: Long,
    /** Çalışıyorsa bitiş anı (ms, duvar saati); duruyorsa 0. */
    val bitis: Long = 0,
    /** Duruyorsa kalan süre (ms). */
    val kalanDurgun: Long = sure,
    val etiket: String = ""
) {
    val calisiyor: Boolean get() = bitis > 0

    fun kalan(simdi: Long): Long = if (calisiyor) bitis - simdi else kalanDurgun

    fun bitti(simdi: Long): Boolean = kalan(simdi) <= 0

    fun baslat(simdi: Long): Zamanlayici =
        if (calisiyor) this else copy(bitis = simdi + kalanDurgun.coerceAtLeast(1000))

    fun duraklat(simdi: Long): Zamanlayici =
        if (!calisiyor) this else copy(bitis = 0, kalanDurgun = kalan(simdi).coerceAtLeast(0))

    /** "+1 dk": çalışırken bitiş ileri alınır, biterken de yeniden kurulur. */
    fun ekle(ms: Long, simdi: Long): Zamanlayici = if (calisiyor) {
        copy(bitis = maxOf(bitis, simdi) + ms)
    } else {
        copy(kalanDurgun = kalanDurgun.coerceAtLeast(0) + ms)
    }

    fun sifirla(): Zamanlayici = copy(bitis = 0, kalanDurgun = sure)

    fun json(): JSONObject = JSONObject()
        .put("id", id).put("sure", sure).put("bitis", bitis)
        .put("kalanDurgun", kalanDurgun).put("etiket", etiket)

    companion object {
        fun jsondan(o: JSONObject) = Zamanlayici(
            o.getInt("id"), o.getLong("sure"), o.optLong("bitis"),
            o.optLong("kalanDurgun", o.getLong("sure")), o.optString("etiket")
        )

        fun listedenJson(liste: List<Zamanlayici>): String =
            JSONArray().apply { liste.forEach { put(it.json()) } }.toString()

        fun jsondanListe(metin: String?): List<Zamanlayici> {
            if (metin.isNullOrEmpty()) return emptyList()
            return runCatching {
                val d = JSONArray(metin)
                (0 until d.length()).mapNotNull { runCatching { jsondan(d.getJSONObject(it)) }.getOrNull() }
            }.getOrDefault(emptyList())
        }

        /**
         * Rakamla süre girişi (Google Saat'teki gibi): yazılan rakamlar sağdan
         * dolar. "130" → 1 dk 30 sn, "10000" → 1 sa. En fazla 6 rakam (99:59:59).
         */
        fun rakamlardanMs(rakamlar: String): Long {
            val r = rakamlar.filter { it.isDigit() }.takeLast(6).padStart(6, '0')
            val sa = r.substring(0, 2).toLong()
            val dk = r.substring(2, 4).toLong()
            val sn = r.substring(4, 6).toLong()
            return ((sa * 3600) + (dk * 60) + sn) * 1000
        }

        /** Kalan süre: "4:59" ya da "1:04:59"; yukarı yuvarlanır (0'a gelince biter). */
        fun bicim(ms: Long): String {
            val sn = ((ms.coerceAtLeast(0) + 999) / 1000)
            val sa = sn / 3600
            val dk = (sn / 60) % 60
            val s = sn % 60
            return if (sa > 0) "%d:%02d:%02d".format(sa, dk, s) else "%d:%02d".format(dk, s)
        }
    }
}
