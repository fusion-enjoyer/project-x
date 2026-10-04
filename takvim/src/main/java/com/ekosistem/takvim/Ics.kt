package com.ekosistem.takvim

import java.util.TimeZone

/**
 * `.ics` (iCalendar, RFC 5545) dosyasındaki bir etkinlik. Tekrarlayan etkinliğin tek tek değiştirilmiş
 * örnekleri ([oncekiOrnek] dolu) aynı [uid] ile ayrı kayıt olarak gelir; iptal edilmiş örnekler
 * ana etkinliğin [muaf] listesindedir.
 */
data class IcsEtkinlik(
    val uid: String,
    val baslik: String,
    val aciklama: String,
    val konum: String,
    /** Tüm günde UTC gece yarısı, değilse an (ms). */
    val baslangic: Long,
    /** Tüm günde son günün ertesi UTC gece yarısı. */
    val bitis: Long,
    val tumGun: Boolean,
    val zamanDilimi: String,
    val kural: String? = null,
    /** Başlangıçtan kaç dakika önce uyarı. */
    val hatirlaticilar: List<Int> = emptyList(),
    /** İptal edilen örneklerin (EXDATE) başlangıç anları. */
    val muaf: List<Long> = emptyList(),
    /** RECURRENCE-ID: bu kayıt, serinin şu örneğinin değiştirilmiş hali. */
    val oncekiOrnek: Long? = null
)

/** Saf `.ics` yazıcı ve okuyucu (Android'e bağlı değil, birim testle sınanır). */
object Ics {
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    // ---- Yazma ----

    fun yaz(liste: List<IcsEtkinlik>, simdi: Long, takvimAdi: String? = null): String {
        val b = StringBuilder()
        satir(b, "BEGIN:VCALENDAR")
        satir(b, "VERSION:2.0")
        satir(b, "PRODID:-//Takvim//TR//")
        satir(b, "CALSCALE:GREGORIAN")
        if (takvimAdi != null) satir(b, "X-WR-CALNAME:" + kacis(takvimAdi))
        for (e in liste) etkinlikYaz(b, e, simdi)
        satir(b, "END:VCALENDAR")
        return b.toString()
    }

    private fun etkinlikYaz(b: StringBuilder, e: IcsEtkinlik, simdi: Long) {
        satir(b, "BEGIN:VEVENT")
        satir(b, "UID:" + e.uid)
        satir(b, "DTSTAMP:" + utc(simdi))
        e.oncekiOrnek?.let {
            satir(b, if (e.tumGun) "RECURRENCE-ID;VALUE=DATE:" + tarih(it) else "RECURRENCE-ID:" + utc(it))
        }
        // Tekrarlayan saatli etkinlik dilimiyle yazılır: yaz saati geçişinde saat kaymasın.
        val dilimli = e.kural != null && !e.tumGun
        if (e.tumGun) {
            satir(b, "DTSTART;VALUE=DATE:" + tarih(e.baslangic))
            satir(b, "DTEND;VALUE=DATE:" + tarih(e.bitis))
        } else if (dilimli) {
            val tz = TimeZone.getTimeZone(e.zamanDilimi)
            satir(b, "DTSTART;TZID=${e.zamanDilimi}:" + yerel(e.baslangic, tz))
            satir(b, "DTEND;TZID=${e.zamanDilimi}:" + yerel(e.bitis, tz))
        } else {
            satir(b, "DTSTART:" + utc(e.baslangic))
            satir(b, "DTEND:" + utc(e.bitis))
        }
        if (e.baslik.isNotEmpty()) satir(b, "SUMMARY:" + kacis(e.baslik))
        if (e.konum.isNotEmpty()) satir(b, "LOCATION:" + kacis(e.konum))
        if (e.aciklama.isNotEmpty()) satir(b, "DESCRIPTION:" + kacis(e.aciklama))
        e.kural?.let { satir(b, "RRULE:" + it.removePrefix("RRULE:")) }
        if (e.muaf.isNotEmpty()) {
            satir(b, if (e.tumGun) "EXDATE;VALUE=DATE:" + e.muaf.joinToString(",") { tarih(it) } else "EXDATE:" + e.muaf.joinToString(",") { utc(it) })
        }
        for (dk in e.hatirlaticilar) {
            satir(b, "BEGIN:VALARM")
            satir(b, "ACTION:DISPLAY")
            satir(b, "DESCRIPTION:" + kacis(e.baslik.ifEmpty { "Takvim" }))
            satir(b, "TRIGGER:" + if (dk <= 0) "PT0S" else "-PT${dk}M")
            satir(b, "END:VALARM")
        }
        satir(b, "END:VEVENT")
    }

    /** Satırı 75 sekizlik sınırın altında katlar (UTF-8 çok baytlı karakteri ortasından bölmez). */
    private fun satir(b: StringBuilder, metin: String) {
        var sekizli = 0
        var ilk = true
        var i = 0
        while (i < metin.length) {
            val cp = metin.codePointAt(i)
            val boy = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            val n = Character.charCount(cp)
            if (sekizli + boy > 74) {
                b.append("\r\n ")
                sekizli = 1
                ilk = false
            }
            b.append(metin, i, i + n)
            sekizli += boy
            i += n
        }
        b.append("\r\n")
    }

    private fun kacis(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    private fun iki(n: Int) = n.toString().padStart(2, '0')

    private fun tarih(utcGunMs: Long): String {
        val g = Gun.utcGun(utcGunMs)
        return "%04d%02d%02d".format(Gun.yil(g), Gun.ay(g), Gun.ayinGunu(g))
    }

    private fun yerel(ms: Long, tz: TimeZone): String {
        val g = Gun.yerelGun(ms, tz)
        val dk = Gun.yerelDakika(ms, tz)
        val sn = Math.floorMod(ms + tz.getOffset(ms), 60_000L) / 1000
        return "%04d%02d%02dT%s%s%s".format(Gun.yil(g), Gun.ay(g), Gun.ayinGunu(g), iki(dk / 60), iki(dk % 60), iki(sn.toInt()))
    }

    private fun utc(ms: Long) = yerel(ms, UTC) + "Z"

    // ---- Okuma ----

    /** Dosyadaki bütün VEVENT'leri okur; anlaşılamayan olaylar atlanır. */
    fun oku(metin: String, varsayilanDilim: TimeZone): List<IcsEtkinlik> {
        val satirlar = metin.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')
        val sonuc = ArrayList<IcsEtkinlik>()
        var olay: MutableList<Ozellik>? = null
        var alarmlar: MutableList<Int>? = null
        var alarmIci = false
        var alarmDakika: Int? = null
        for (ham in satirlar) {
            val s = ham.trimEnd()
            when {
                s.equals("BEGIN:VEVENT", true) -> { olay = ArrayList(); alarmlar = ArrayList() }
                s.equals("END:VEVENT", true) -> {
                    olay?.let { o -> olustur(o, alarmlar.orEmpty(), varsayilanDilim)?.let { sonuc.add(it) } }
                    olay = null; alarmlar = null
                }
                s.equals("BEGIN:VALARM", true) && olay != null -> { alarmIci = true; alarmDakika = null }
                s.equals("END:VALARM", true) && olay != null -> {
                    alarmDakika?.let { alarmlar?.add(it) }
                    alarmIci = false
                }
                olay != null -> {
                    val p = ozellik(s) ?: continue
                    if (alarmIci) {
                        if (p.ad == "TRIGGER" && !p.parametreler.containsKey("VALUE")) alarmDakika = tetik(p.deger)
                    } else {
                        olay.add(p)
                    }
                }
            }
        }
        return sonuc
    }

    private class Ozellik(val ad: String, val parametreler: Map<String, String>, val deger: String)

    private fun ozellik(satir: String): Ozellik? {
        // ad[;param=deger]*:deger  — değerdeki ':' ve ';' için ilk ':' ayırır (tırnaklı parametreleri hesaba katar)
        var tirnak = false
        var iki = -1
        for ((i, c) in satir.withIndex()) {
            if (c == '"') tirnak = !tirnak
            if (c == ':' && !tirnak) { iki = i; break }
        }
        if (iki <= 0) return null
        val bas = satir.substring(0, iki).split(';')
        val parametreler = HashMap<String, String>()
        for (p in bas.drop(1)) {
            val e = p.indexOf('=')
            if (e > 0) parametreler[p.substring(0, e).uppercase()] = p.substring(e + 1).trim('"')
        }
        return Ozellik(bas[0].uppercase(), parametreler, satir.substring(iki + 1))
    }

    private fun coz(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> b.append('\n')
                    else -> b.append(n)
                }
                i += 2
            } else {
                b.append(c); i++
            }
        }
        return b.toString()
    }

    /** "-PT15M", "-P1D", "PT0S" → önce kaç dakika; sonrasında olan ("+") ya da çözülemeyen null. */
    private fun tetik(deger: String): Int? {
        val d = deger.trim()
        if (d.startsWith("+")) return null
        val ms = Tekrar.sureMs(d.removePrefix("-"))
        if (ms == 0L && !d.removePrefix("-").matches(Regex("P(T0S|0S|0D|T0M)")) ) return null
        return (ms / 60_000L).toInt()
    }

    private class Zaman(val ms: Long, val tumGun: Boolean, val dilim: String?)

    /** "20261004", "20261004T091500Z", "20261004T091500" (+ TZID) → an. */
    private fun zaman(p: Ozellik, varsayilan: TimeZone): Zaman? {
        val d = p.deger.trim()
        val tarihOnly = Regex("^(\\d{4})(\\d{2})(\\d{2})$").matchEntire(d)
        if (tarihOnly != null || p.parametreler["VALUE"].equals("DATE", true)) {
            val m = tarihOnly ?: return null
            val g = Gun.gun(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            return Zaman(Gun.utcGunBasi(g), true, null)
        }
        val m = Regex("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})(Z?)$").matchEntire(d) ?: return null
        val g = Gun.gun(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        val dk = m.groupValues[4].toInt() * 60 + m.groupValues[5].toInt()
        val sn = m.groupValues[6].toInt()
        val tz = when {
            m.groupValues[7] == "Z" -> UTC
            p.parametreler["TZID"] != null -> bilinenDilim(p.parametreler["TZID"]!!) ?: varsayilan
            else -> varsayilan
        }
        return Zaman(Gun.yerelAn(g, dk, tz) + sn * 1000L, false, if (m.groupValues[7] == "Z") null else tz.id)
    }

    private fun bilinenDilim(id: String): TimeZone? =
        if (TimeZone.getAvailableIDs().contains(id)) TimeZone.getTimeZone(id) else null

    private fun olustur(o: List<Ozellik>, alarmlar: List<Int>, varsayilan: TimeZone): IcsEtkinlik? {
        fun al(ad: String) = o.firstOrNull { it.ad == ad }
        if (al("STATUS")?.deger?.trim().equals("CANCELLED", true)) return null
        val bas = al("DTSTART")?.let { zaman(it, varsayilan) } ?: return null
        var bit: Long = when {
            al("DTEND") != null -> zaman(al("DTEND")!!, varsayilan)?.ms
            al("DURATION") != null -> bas.ms + Tekrar.sureMs(al("DURATION")!!.deger)
            else -> null
        } ?: if (bas.tumGun) bas.ms + Gun.GUN_MS else bas.ms
        if (bit < bas.ms) bit = bas.ms
        if (bas.tumGun && bit <= bas.ms) bit = bas.ms + Gun.GUN_MS
        val muaf = ArrayList<Long>()
        for (p in o.filter { it.ad == "EXDATE" }) {
            for (parca in p.deger.split(',')) {
                zaman(Ozellik("EXDATE", p.parametreler, parca), varsayilan)?.let { muaf.add(it.ms) }
            }
        }
        val dilim = bas.dilim ?: varsayilan.id
        return IcsEtkinlik(
            uid = al("UID")?.deger?.trim().orEmpty().ifEmpty { "ics-" + (al("SUMMARY")?.deger.orEmpty().hashCode()) + "-" + bas.ms },
            baslik = coz(al("SUMMARY")?.deger.orEmpty()),
            aciklama = coz(al("DESCRIPTION")?.deger.orEmpty()),
            konum = coz(al("LOCATION")?.deger.orEmpty()),
            baslangic = bas.ms, bitis = bit, tumGun = bas.tumGun, zamanDilimi = dilim,
            kural = al("RRULE")?.deger?.trim()?.takeIf { it.isNotEmpty() },
            hatirlaticilar = alarmlar.distinct().sorted(),
            muaf = muaf,
            oncekiOrnek = al("RECURRENCE-ID")?.let { zaman(it, varsayilan)?.ms }
        )
    }
}
