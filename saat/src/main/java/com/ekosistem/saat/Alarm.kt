package com.ekosistem.saat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bir alarm: duvar saati (saat:dakika) ve tekrar kuralı. Kayıtta hiçbir zaman
 * "yarın" ya da hesaplanmış bir gün durmaz; bir sonraki çalma anı her seferinde
 * o anki saate göre [Zamanlama] ile hesaplanır. (Fossify Clock'ta "yarın"
 * saklandığı için gece kilitlenen telefonda alarm bir gün kayıyordu.)
 */
data class Alarm(
    val id: Int,
    val saat: Int,
    val dakika: Int,
    /** Tekrar günleri: bit 0 pazartesi … bit 6 pazar. 0 = tekrar yok. */
    val gunler: Int = 0,
    /** Tekrar yoksa belirli gün (yyyyAAgg); 0 = ilk uygun an (bugün ya da yarın). */
    val tarih: Int = 0,
    /** Tarih aralığının son günü (yyyyAAgg, dahil); 0 = aralık yok, yalnız [tarih] günü. */
    val tarihBitis: Int = 0,
    val etiket: String = "",
    val acik: Boolean = true,
    /** Atlanacak tek çalmanın günü (yyyyAAgg); 0 = yok. */
    val atla: Int = 0,
    /** "" = sistemin alarm sesi, [SES_YERLESIK] = uygulamanın kendi sesi, diğeri adres. */
    val ses: String = "",
    /** Sesin en yükseğe çıkma süresi (sn); 0 = hemen tam ses. */
    val kademeliSn: Int = 30,
    val titresim: Boolean = true,
    val ertelemeDk: Int = 10,
    /** En fazla erteleme sayısı; 0 = sınırsız. */
    val ertelemeSiniri: Int = 3,
    /** Bu çalmada kaç kez ertelendi. */
    val ertelemeSayisi: Int = 0,
    /** Ertelenmişse yeniden çalacağı an (ms); 0 = ertelenmedi. */
    val ertelemeZamani: Long = 0,
    /** Son kurulan çalma anı (ms); kaçırılan alarmı fark etmek için. */
    val kurulanZaman: Long = 0,
    /** Kapatma görevi: [Gorev.YOK] … [Gorev.ZOR]. */
    val gorev: Int = Gorev.YOK
) {
    val tekrarli: Boolean get() = gunler != 0

    /** Başlangıç ve bitiş günü olan alarm: aralıktaki her gün (ya da seçili günler) çalar. */
    val aralikli: Boolean get() = tarih != 0 && tarihBitis != 0

    fun gunAcik(gun: Int): Boolean = gunler and (1 shl gun) != 0

    fun json(): JSONObject = JSONObject()
        .put("id", id)
        .put("saat", saat)
        .put("dakika", dakika)
        .put("gunler", gunler)
        .put("tarih", tarih)
        .put("tarihBitis", tarihBitis)
        .put("etiket", etiket)
        .put("acik", acik)
        .put("atla", atla)
        .put("ses", ses)
        .put("kademeliSn", kademeliSn)
        .put("titresim", titresim)
        .put("ertelemeDk", ertelemeDk)
        .put("ertelemeSiniri", ertelemeSiniri)
        .put("ertelemeSayisi", ertelemeSayisi)
        .put("ertelemeZamani", ertelemeZamani)
        .put("kurulanZaman", kurulanZaman)
        .put("gorev", gorev)

    companion object {
        const val SES_YERLESIK = "yerlesik"

        /** Hafta içi: pazartesi–cuma. */
        const val HAFTA_ICI = 0b0011111
        const val HAFTA_SONU = 0b1100000
        const val HER_GUN = 0b1111111

        fun jsondan(o: JSONObject): Alarm = Alarm(
            id = o.getInt("id"),
            saat = o.getInt("saat").coerceIn(0, 23),
            dakika = o.getInt("dakika").coerceIn(0, 59),
            gunler = o.optInt("gunler", 0) and HER_GUN,
            tarih = o.optInt("tarih", 0),
            tarihBitis = o.optInt("tarihBitis", 0),
            etiket = o.optString("etiket", ""),
            acik = o.optBoolean("acik", true),
            atla = o.optInt("atla", 0),
            ses = o.optString("ses", ""),
            kademeliSn = o.optInt("kademeliSn", 30),
            titresim = o.optBoolean("titresim", true),
            ertelemeDk = o.optInt("ertelemeDk", 10).coerceIn(1, 60),
            ertelemeSiniri = o.optInt("ertelemeSiniri", 3),
            ertelemeSayisi = o.optInt("ertelemeSayisi", 0),
            ertelemeZamani = o.optLong("ertelemeZamani", 0),
            kurulanZaman = o.optLong("kurulanZaman", 0),
            gorev = o.optInt("gorev", Gorev.YOK).coerceIn(Gorev.YOK, Gorev.ZOR)
        )

        fun listedenJson(alarmlar: List<Alarm>): String {
            val dizi = JSONArray()
            for (a in alarmlar) dizi.put(a.json())
            return JSONObject().put("surum", 1).put("alarmlar", dizi).toString()
        }

        /** Bozuk bir kayıt bütün listeyi götürmesin: okunabilenler döner. */
        fun jsondanListe(metin: String): List<Alarm> {
            val dizi = JSONObject(metin).optJSONArray("alarmlar") ?: return emptyList()
            val sonuc = ArrayList<Alarm>(dizi.length())
            for (i in 0 until dizi.length()) {
                val o = dizi.optJSONObject(i) ?: continue
                runCatching { jsondan(o) }.getOrNull()?.let { sonuc.add(it) }
            }
            return sonuc
        }
    }
}
