package com.ekosistem.takvim

/**
 * Başlığa yazılan cümleden tarih, saat, süre ve tekrarı ayıklar:
 * "Yarın 14:30 diş randevusu", "her pazartesi 09:00 toplantı", "15 Ekim tüm gün doğum günü",
 * "friday at 3pm lunch". Türkçe ve İngilizce birlikte çalışır, internet ya da model gerekmez.
 * Android'e bağlı değil, birim testle sınanır.
 *
 * Eşleşmeler yalnız bir *öneridir*: arayüz "uygula" derse alanları doldurur, başlıktan
 * eşleşen kısımları çıkarır; kullanıcı uygulamazsa metin olduğu gibi kalır.
 */
object DogalDil {

    class Sonuc(
        /** Eşleşen kısımlar çıkarılmış başlık. */
        val baslik: String,
        val gun: Int?,
        val baslangicDk: Int?,
        val bitisDk: Int?,
        /** Açıkça söylenen süre ("2 saat", "45 dk"); aralık verilmişse null. */
        val sureDk: Int?,
        val tumGun: Boolean,
        val kural: Kural?
    ) {
        val bulundu get() = gun != null || baslangicDk != null || tumGun || kural != null || sureDk != null
    }

    private val AYLAR = mapOf(
        "ocak" to 1, "subat" to 2, "mart" to 3, "nisan" to 4, "mayis" to 5, "haziran" to 6,
        "temmuz" to 7, "agustos" to 8, "eylul" to 9, "ekim" to 10, "kasim" to 11, "aralik" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    /** 0 = Pazartesi … 6 = Pazar. */
    private val GUNLER = mapOf(
        "pazartesi" to 0, "pzt" to 0, "monday" to 0, "mon" to 0,
        "sali" to 1, "tuesday" to 1, "tue" to 1, "tues" to 1,
        "carsamba" to 2, "wednesday" to 2, "wed" to 2,
        "persembe" to 3, "thursday" to 3, "thu" to 3, "thur" to 3, "thurs" to 3,
        "cuma" to 4, "friday" to 4, "fri" to 4,
        "cumartesi" to 5, "cmt" to 5, "saturday" to 5,
        "pazar" to 6, "sunday" to 6
    )

    private val SUFFIX = "(?:['’]\\w+)?"
    private val AY_KALIBI = AYLAR.keys.sortedByDescending { it.length }.joinToString("|")
    private val GUN_KALIBI = GUNLER.keys.sortedByDescending { it.length }.joinToString("|")

    fun coz(metin: String, bugun: Int, simdiDk: Int): Sonuc {
        // n: eşleşince boşlukla doldurulan katlanmış kopya; silinecek aralıklar ayrıca tutulur.
        var n = Arama.katla(metin)
        val silinecek = ArrayList<IntRange>()

        /** Geçersiz çıkan eşleşmeyi (31 Nisan) geri alır: metin olduğu gibi kalır. */
        fun iptal(m: MatchResult) {
            n = n.replaceRange(m.range, m.value)
            silinecek.remove(m.range)
        }

        fun al(re: Regex): MatchResult? {
            val m = re.find(n) ?: return null
            // Sonraki eşleşmeler aynı yeri bir daha bulmasın.
            n = n.replaceRange(m.range, " ".repeat(m.value.length))
            silinecek.add(m.range)
            return m
        }

        var kural: Kural? = null
        var gun: Int? = null
        var tumGun = false
        var bas: Int? = null
        var bit: Int? = null
        var sure: Int? = null
        var haftayaMi = false
        var tekrarGunu: Int? = null

        // --- tekrar
        al(Regex("\\bher\\s+($GUN_KALIBI)\\b$SUFFIX"))?.let { m ->
            tekrarGunu = GUNLER[m.groupValues[1]]
            kural = Kural(Sik.HAFTALIK)
        } ?: al(Regex("\\bevery\\s+($GUN_KALIBI)\\b"))?.let { m ->
            tekrarGunu = GUNLER[m.groupValues[1]]
            kural = Kural(Sik.HAFTALIK)
        } ?: al(Regex("\\b(?:her|every)\\s+(gun|hafta|ay|yil|day|week|month|year)\\b"))?.let { m ->
            kural = Kural(
                when (m.groupValues[1]) {
                    "gun", "day" -> Sik.GUNLUK
                    "hafta", "week" -> Sik.HAFTALIK
                    "ay", "month" -> Sik.AYLIK
                    else -> Sik.YILLIK
                }
            )
        } ?: al(Regex("\\b(gunluk|haftalik|aylik|yillik|daily|weekly|monthly|yearly)\\b"))?.let { m ->
            kural = Kural(
                when (m.groupValues[1]) {
                    "gunluk", "daily" -> Sik.GUNLUK
                    "haftalik", "weekly" -> Sik.HAFTALIK
                    "aylik", "monthly" -> Sik.AYLIK
                    else -> Sik.YILLIK
                }
            )
        }

        // --- sayısal tarihler (saatten önce: "15.10.2026" saat sanılmasın)
        var tarihBulundu = false
        al(Regex("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})\\b"))?.let { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            gun = gunKur(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
            tarihBulundu = gun != null
            if (!tarihBulundu) iptal(m)
        }
        if (!tarihBulundu) {
            al(Regex("\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\b"))?.let { m ->
                gun = yilsizTarih(bugun, m.groupValues[2].toInt(), m.groupValues[1].toInt())
                tarihBulundu = gun != null
                if (!tarihBulundu) iptal(m)
            }
        }

        // --- tüm gün
        if (al(Regex("\\b(?:tum|butun)\\s+gun\\b|\\ball[\\s-]?day\\b")) != null) tumGun = true

        // --- süre ("2 saat", "45 dk", "1 saat 30 dk")
        var toplamSure = 0
        var sureVar = false
        while (true) {
            val m = al(Regex("\\b(\\d{1,3})\\s*(saatlik|saat|hours?|hrs?|hr|h)\\b")) ?: break
            // "saat 14" (saat + sayı) buraya girmez: sayı önce gelir.
            toplamSure += m.groupValues[1].toInt() * 60
            sureVar = true
        }
        while (true) {
            val m = al(Regex("\\b(\\d{1,3})\\s*(dakikalik|dakika|dk|minutes?|mins?|min)\\b")) ?: break
            toplamSure += m.groupValues[1].toInt()
            sureVar = true
        }
        if (sureVar && toplamSure > 0) sure = toplamSure

        // --- saat aralığı "14:00-15:30"
        al(Regex("\\b(\\d{1,2})[:.](\\d{2})\\s*(?:-|–|—|to|ile)\\s*(\\d{1,2})[:.](\\d{2})\\b"))?.let { m ->
            val a = saat(m.groupValues[1], m.groupValues[2])
            val b = saat(m.groupValues[3], m.groupValues[4])
            if (a != null && b != null) {
                bas = a
                bit = b
            }
        }

        // --- tek saat
        if (bas == null) {
            val oncekiDilim = Regex("\\b(sabah|ogleden\\s+sonra|ogle|aksam|gece|morning|afternoon|evening|night)\\b")
            // 14:30, 14.30 (+ am/pm), saat 14:30
            al(Regex("\\b(?:saat\\s+)?(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?\\b$SUFFIX"))?.let { m ->
                var dk = saat(m.groupValues[1], m.groupValues[2])
                if (dk != null) dk = ampm(dk, m.groupValues[3])
                bas = dk
            }
            if (bas == null) {
                // 3pm, 3 pm
                al(Regex("\\b(\\d{1,2})\\s*(am|pm)\\b"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    if (h in 1..12) bas = ampm(h * 60, m.groupValues[2])
                }
            }
            if (bas == null) {
                // 3 buçuk
                al(Regex("\\b(?:saat\\s+)?(\\d{1,2})\\s+bucuk\\b"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    if (h in 0..23) bas = (if (h in 1..6) h + 12 else h) * 60 + 30
                }
            }
            if (bas == null) {
                // 14'te, 14'de ("saat" olmadan yalnız ek şart)
                al(Regex("\\b(?:saat\\s+)?(\\d{1,2})['’](?:te|de|ta|da)\\b"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    if (h in 0..23) bas = h * 60
                    bas = bas?.let { varsayilanOgle(it, m.groupValues[1]) }
                }
            }
            if (bas == null) {
                // saat 14, saat 3
                al(Regex("\\bsaat\\s+(\\d{1,2})\\b$SUFFIX"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    if (h in 0..23) bas = varsayilanOgle(h * 60, m.groupValues[1])
                }
            }
            if (bas == null) {
                // at 3 (İngilizce, am/pm yok)
                al(Regex("\\bat\\s+(\\d{1,2})\\b"))?.let { m ->
                    val h = m.groupValues[1].toInt()
                    if (h in 0..23) bas = varsayilanOgle(h * 60, m.groupValues[1])
                }
            }
            // günün bölümü: "akşam 8", "sabah", "öğle"
            val dilim = oncekiDilim.find(n)
            if (dilim != null) {
                val ad = dilim.groupValues[1].replace(Regex("\\s+"), " ")
                val b0 = bas
                if (b0 != null) {
                    if (ad in listOf("ogleden sonra", "aksam", "afternoon", "evening") && b0 < 12 * 60) bas = b0 + 12 * 60
                    else if ((ad == "gece" || ad == "night") && b0 in (6 * 60 until 12 * 60)) bas = b0 + 12 * 60
                    n = n.replaceRange(dilim.range, " ".repeat(dilim.value.length)); silinecek.add(dilim.range)
                } else if (ad == "ogle") {
                    bas = 12 * 60
                    n = n.replaceRange(dilim.range, " ".repeat(dilim.value.length)); silinecek.add(dilim.range)
                }
            }
        }

        // --- yazıyla tarih
        if (!tarihBulundu) {
            // 15 Ekim [2027]
            al(Regex("\\b(\\d{1,2})\\s*(?:['’]\\w+)?\\s+($AY_KALIBI)\\b$SUFFIX(?:\\s+(\\d{4})\\b)?"))?.let { m ->
                val ay = AYLAR[m.groupValues[2]]
                val d = m.groupValues[1].toInt()
                val yil = m.groupValues[3].toIntOrNull()
                if (ay != null) {
                    gun = if (yil != null) gunKur(yil, ay, d) else yilsizTarih(bugun, ay, d)
                    tarihBulundu = gun != null
                }
                if (!tarihBulundu) iptal(m)
            }
        }
        if (!tarihBulundu) {
            // Oct 15 [2027]
            al(Regex("\\b($AY_KALIBI)\\s+(\\d{1,2})\\b(?:\\s+(\\d{4})\\b)?"))?.let { m ->
                val ay = AYLAR[m.groupValues[1]]
                val d = m.groupValues[2].toInt()
                val yil = m.groupValues[3].toIntOrNull()
                if (ay != null) {
                    gun = if (yil != null) gunKur(yil, ay, d) else yilsizTarih(bugun, ay, d)
                    tarihBulundu = gun != null
                }
                if (!tarihBulundu) iptal(m)
            }
        }

        // --- göreli gün ve haftanın günü
        if (gun == null) {
            if (al(Regex("\\b(?:obur\\s+gun|ertesi\\s+gun|day\\s+after\\s+tomorrow)\\b")) != null) gun = bugun + 2
            else if (al(Regex("\\b(?:yarin|tomorrow|tmrw)\\b$SUFFIX")) != null) gun = bugun + 1
            else if (al(Regex("\\b(?:bugun|today)\\b$SUFFIX")) != null) gun = bugun
        }
        if (gun == null) {
            // "haftaya salı", "next friday", "bu cuma", "this friday", "cuma"
            val m = al(Regex("\\b(?:(haftaya|gelecek|next|bu|this)\\s+)?($GUN_KALIBI)\\b$SUFFIX"))
            if (m != null) {
                val hedef = GUNLER[m.groupValues[2]]!!
                haftayaMi = m.groupValues[1] in listOf("haftaya", "gelecek", "next")
                gun = sonrakiGun(bugun, hedef, haftayaMi, bas, simdiDk)
            }
        }
        if (tekrarGunu != null && gun == null) gun = sonrakiGun(bugun, tekrarGunu!!, false, bas, simdiDk)
        // Aylık/yıllık tekrar tarihsiz ise bugüne bağlanmaz; gün alanı boş kalır.

        // --- başlığı derle
        val b = StringBuilder(metin)
        for (r in silinecek.sortedByDescending { it.first }) {
            b.delete(r.first, minOf(r.last + 1, b.length))
        }
        val baslik = temizle(b.toString())

        val sureSonuc = if (bit != null) null else sure
        return Sonuc(baslik, gun, bas, bit, sureSonuc, tumGun, kural)
    }

    private fun saat(h: String, m: String): Int? {
        val sa = h.toInt()
        val dk = m.toInt()
        return if (sa in 0..23 && dk in 0..59) sa * 60 + dk else null
    }

    private fun ampm(dk: Int, ek: String): Int {
        val h = dk / 60
        return when {
            ek == "pm" && h < 12 -> dk + 12 * 60
            ek == "am" && h == 12 -> dk - 12 * 60
            else -> dk
        }
    }

    /** "saat 3" gibi yalnız saat söylenince 1–6 arası öğleden sonra sayılır (07 ve üzeri olduğu gibi). */
    private fun varsayilanOgle(dk: Int, yazilan: String): Int {
        val h = dk / 60
        return if (yazilan.length == 1 || yazilan.toInt() in 1..6) (if (h in 1..6) dk + 12 * 60 else dk) else dk
    }

    private fun gunKur(yil: Int, ay: Int, gun: Int): Int? {
        if (ay !in 1..12 || gun !in 1..Gun.ayinGunSayisi(yil, ay)) return null
        return Gun.gun(yil, ay, gun)
    }

    /** Yılsız tarih: bu yıl, geçmişse gelecek yıl. */
    private fun yilsizTarih(bugun: Int, ay: Int, gun: Int): Int? {
        val yil = Gun.yil(bugun)
        val bu = gunKur(yil, ay, gun) ?: return null
        return if (bu >= bugun) bu else gunKur(yil + 1, ay, gun)
    }

    private fun sonrakiGun(bugun: Int, hedef: Int, haftaya: Boolean, bas: Int?, simdiDk: Int): Int {
        if (haftaya) return Gun.haftaBasi(bugun, 0) + 7 + hedef
        val fark = Math.floorMod(hedef - Gun.haftaGunu(bugun), 7)
        if (fark == 0 && bas != null && bas <= simdiDk) return bugun + 7
        return bugun + fark
    }

    private fun temizle(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        // Eşleşmelerden artan bağlaçlar ve işaretler uçlardan atılır.
        val baglac = Regex("^(?:at|on|in|for|by|saat|ve|and)\\b\\s*|\\s*\\b(?:at|on|in|for|by|saat|ve|and)$", RegexOption.IGNORE_CASE)
        var onceki: String
        do {
            onceki = t
            t = t.replace(baglac, "").trim().trim(',', ';', ':', '-', '–', '—', '.').trim()
        } while (t != onceki)
        return t
    }
}
