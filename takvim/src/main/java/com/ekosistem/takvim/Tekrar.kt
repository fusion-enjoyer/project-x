package com.ekosistem.takvim

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class Sik { GUNLUK, HAFTALIK, AYLIK, YILLIK }

/**
 * Arayüzün anlayabildiği tekrar kuralı. Depodaki RRULE bundan karmaşıksa
 * ([Tekrar.coz] null döner) kural "özel" sayılır ve olduğu gibi korunur:
 * kullanıcı tekrarı değiştirmedikçe kuralımız onu bozmaz.
 */
data class Kural(
    val sik: Sik,
    val aralik: Int = 1,
    /** Haftalıkta seçili günler; boşsa başlangıç gününün haftadaki günü. */
    val gunler: Set<Int> = emptySet(),
    /** Aylıkta 0 = ayın günü; 1..4 / -1 = ayın n. (son) haftası günü ([gunler] tek eleman). */
    val aySirasi: Int = 0,
    /** Kaç kez (COUNT); 0 = sınırsız. */
    val sayi: Int = 0,
    /** Bitiş günü, dahil (UNTIL); [sayi] varsa boş. */
    val bitisGun: Int? = null
)

object Tekrar {
    private val GUN_KODLARI = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val BYDAY = Regex("^(-?[1-5])?(MO|TU|WE|TH|FR|SA|SU)$")

    /**
     * RRULE'u çözer. Arayüzün gösteremediği her şey (BYSETPOS, birden çok ay
     * günü, başlangıçla uyuşmayan BYMONTHDAY…) için null: o durumda kural dokunulmadan saklanır.
     */
    fun coz(rrule: String?, baslangicGun: Int, tz: TimeZone): Kural? {
        if (rrule.isNullOrBlank()) return null
        val alanlar = parcala(rrule) ?: return null
        val izinli = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL", "WKST")
        if (!izinli.containsAll(alanlar.keys)) return null

        val sik = when (alanlar["FREQ"]) {
            "DAILY" -> Sik.GUNLUK
            "WEEKLY" -> Sik.HAFTALIK
            "MONTHLY" -> Sik.AYLIK
            "YEARLY" -> Sik.YILLIK
            else -> return null
        }
        val aralik = alanlar["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
        if (aralik < 1) return null
        if (alanlar.containsKey("COUNT") && alanlar.containsKey("UNTIL")) return null
        val sayi = alanlar["COUNT"]?.let { it.toIntOrNull() ?: return null } ?: 0
        if (alanlar.containsKey("COUNT") && sayi < 1) return null
        val bitis = alanlar["UNTIL"]?.let { untilGunu(it, tz) ?: return null }

        val gunMetni = alanlar["BYDAY"]
        val ayGunu = alanlar["BYMONTHDAY"]
        val ay = alanlar["BYMONTH"]
        val gunler = gunMetni?.split(',')?.map { BYDAY.matchEntire(it.trim()) ?: return null }

        return when (sik) {
            Sik.GUNLUK -> {
                if (gunler != null || ayGunu != null || ay != null) return null
                Kural(sik, aralik, sayi = sayi, bitisGun = bitis)
            }
            Sik.HAFTALIK -> {
                if (ayGunu != null || ay != null) return null
                if (gunler != null && gunler.any { it.groupValues[1].isNotEmpty() }) return null
                val secili = gunler?.map { GUN_KODLARI.indexOf(it.groupValues[2]) }?.toSet() ?: emptySet()
                // Yalnız başlangıç gününü söyleyen BYDAY "belirtilmemiş" sayılır.
                val gunKumesi = if (secili == setOf(Gun.haftaGunu(baslangicGun))) emptySet() else secili
                Kural(sik, aralik, gunler = gunKumesi, sayi = sayi, bitisGun = bitis)
            }
            Sik.AYLIK -> {
                if (ay != null) return null
                if (gunler != null) {
                    if (ayGunu != null || gunler.size != 1) return null
                    val sira = gunler[0].groupValues[1].toIntOrNull() ?: return null
                    if (sira !in listOf(1, 2, 3, 4, -1)) return null
                    val gun = GUN_KODLARI.indexOf(gunler[0].groupValues[2])
                    Kural(sik, aralik, setOf(gun), sira, sayi, bitis)
                } else {
                    if (ayGunu != null && ayGunu.toIntOrNull() != Gun.ayinGunu(baslangicGun)) return null
                    Kural(sik, aralik, sayi = sayi, bitisGun = bitis)
                }
            }
            Sik.YILLIK -> {
                if (gunler != null) return null
                if (ayGunu != null && ayGunu.toIntOrNull() != Gun.ayinGunu(baslangicGun)) return null
                if (ay != null && ay.toIntOrNull() != Gun.ay(baslangicGun)) return null
                Kural(sik, aralik, sayi = sayi, bitisGun = bitis)
            }
        }
    }

    fun yaz(kural: Kural, tumGun: Boolean, tz: TimeZone): String {
        val s = StringBuilder("FREQ=")
        s.append(
            when (kural.sik) {
                Sik.GUNLUK -> "DAILY"
                Sik.HAFTALIK -> "WEEKLY"
                Sik.AYLIK -> "MONTHLY"
                Sik.YILLIK -> "YEARLY"
            }
        )
        if (kural.aralik > 1) s.append(";INTERVAL=").append(kural.aralik)
        when {
            kural.sik == Sik.HAFTALIK && kural.gunler.isNotEmpty() ->
                s.append(";BYDAY=").append(kural.gunler.sorted().joinToString(",") { GUN_KODLARI[it] })
            kural.sik == Sik.AYLIK && kural.aySirasi != 0 && kural.gunler.isNotEmpty() ->
                s.append(";BYDAY=").append(kural.aySirasi).append(GUN_KODLARI[kural.gunler.first()])
        }
        if (kural.sayi > 0) {
            s.append(";COUNT=").append(kural.sayi)
        } else if (kural.bitisGun != null) {
            s.append(";UNTIL=").append(untilYaz(kural.bitisGun, tumGun, tz))
        }
        return s.toString()
    }

    /**
     * Serinin "bu ve sonrakiler" bölünmesinde eski seriyi keser: COUNT/UNTIL
     * atılır, [oncekiAn] (bölünen örneğin başlangıcından 1 sn önce) ya da tüm
     * günde [oncekiGun] UNTIL olur. Kuralın geri kalanı olduğu gibi kalır.
     */
    fun kes(rrule: String, oncekiAn: Long, oncekiGun: Int, tumGun: Boolean): String {
        val parcalar = rrule.trim().removePrefix("RRULE:").split(';')
            .filter { it.isNotBlank() && !it.uppercase().startsWith("COUNT=") && !it.uppercase().startsWith("UNTIL=") }
        val until = if (tumGun) tarihYaz(oncekiGun) else utcYaz(oncekiAn)
        return (parcalar + "UNTIL=$until").joinToString(";")
    }

    /** Kuralın kesilmeden önceki COUNT'u; yoksa 0. */
    fun sayi(rrule: String?): Int =
        rrule?.let { parcala(it)?.get("COUNT")?.toIntOrNull() } ?: 0

    /** COUNT'u [yeni]ye çevirir (bölünen yeni serinin kalan tekrarı için). */
    fun sayiyiDegistir(rrule: String, yeni: Int): String {
        val parcalar = rrule.trim().removePrefix("RRULE:").split(';')
            .filter { it.isNotBlank() && !it.uppercase().startsWith("COUNT=") }
        return (parcalar + "COUNT=$yeni").joinToString(";")
    }

    /**
     * Depodaki DURATION: "P3600S" (AOSP'nin yazdığı), "PT1H30M", "P1D", "P2W".
     * Tanınmazsa 0.
     */
    fun sureMs(duration: String?): Long {
        val m = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
            .matchEntire(duration?.trim().orEmpty()) ?: return 0
        fun n(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return ((n(1) * 7 + n(2)) * 86_400L + n(3) * 3600L + n(4) * 60L + n(5)) * 1000L
    }

    /** Tekrarlayan etkinlik DTEND yerine süre ister: tüm günde "P2D", değilse "P3600S". */
    fun sureYaz(baslangic: Long, bitis: Long, tumGun: Boolean): String {
        val fark = maxOf(0L, bitis - baslangic)
        return if (tumGun) "P${maxOf(1L, fark / Gun.GUN_MS)}D" else "P${fark / 1000L}S"
    }

    private fun parcala(rrule: String): Map<String, String>? {
        val alanlar = LinkedHashMap<String, String>()
        for (parca in rrule.trim().removePrefix("RRULE:").split(';')) {
            if (parca.isBlank()) continue
            val i = parca.indexOf('=')
            if (i < 0) return null
            alanlar[parca.substring(0, i).uppercase()] = parca.substring(i + 1)
        }
        return alanlar
    }

    /** "20261231" ya da "20261231T215959Z" (UTC) / "20261231T235959" (yerel) → yerel gün. */
    private fun untilGunu(metin: String, tz: TimeZone): Int? {
        val m = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})(Z)?)?$").matchEntire(metin.trim()) ?: return null
        val (y, a, g) = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3]).map { it.toInt() }
        if (m.groupValues[4].isEmpty()) return Gun.gun(y, a, g)
        if (m.groupValues[7] != "Z") return Gun.gun(y, a, g)
        val ms = Gun.gun(y, a, g) * Gun.GUN_MS +
            (m.groupValues[4].toInt() * 3600L + m.groupValues[5].toInt() * 60L + m.groupValues[6].toInt()) * 1000L
        return Gun.yerelGun(ms, tz)
    }

    private fun untilYaz(bitisGun: Int, tumGun: Boolean, tz: TimeZone): String =
        if (tumGun) tarihYaz(bitisGun)
        else utcYaz(Gun.yerelAn(bitisGun + 1, 0, tz) - 1000L)

    private fun tarihYaz(gun: Int) = "%04d%02d%02d".format(Gun.yil(gun), Gun.ay(gun), Gun.ayinGunu(gun))

    private fun utcYaz(ms: Long): String {
        val bicim = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        bicim.timeZone = TimeZone.getTimeZone("UTC")
        return bicim.format(java.util.Date(ms))
    }
}
