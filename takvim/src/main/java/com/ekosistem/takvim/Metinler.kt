package com.ekosistem.takvim

import android.content.Context
import android.text.format.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Ekranda görünen tarih, saat ve süre metinleri. */
object Metinler {

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Gün numarasını (saf tarih) dilin en uygun yazımıyla biçimler; [iskelet] "dMMMM" gibi. */
    // getBestDateTimePattern ve SimpleDateFormat kurmak pahalı (ICU); iş parçacığı başına bir kez kurulur.
    private val bicimler = ThreadLocal<HashMap<String, SimpleDateFormat>>()

    private fun bicim(iskelet: String, gun: Int): String {
        val yerel = Locale.getDefault()
        val harita = bicimler.get() ?: HashMap<String, SimpleDateFormat>().also { bicimler.set(it) }
        val f = harita.getOrPut("$yerel|$iskelet") {
            SimpleDateFormat(DateFormat.getBestDateTimePattern(yerel, iskelet), yerel).apply { timeZone = UTC }
        }
        return f.format(Date(gun * Gun.GUN_MS))
    }

    /** "Ekim 2026" */
    fun ayYil(gun: Int) = bicim("LLLLy", gun).basHarfBuyuk()

    /** "Ekim" (yıl görünümündeki küçük ayların başlığı) */
    fun ayAdi(gun: Int) = bicim("LLLL", gun).basHarfBuyuk()

    /** "4 Ekim" */
    fun gunAy(gun: Int) = bicim("dMMMM", gun)

    /** "4 Eki" */
    fun gunAyKisa(gun: Int) = bicim("dMMM", gun)

    /** "4 Ekim 2026" */
    fun tamTarih(gun: Int) = bicim("dMMMMy", gun)

    /** "4 Eki 2026" */
    fun tamTarihKisa(gun: Int) = bicim("dMMMy", gun)

    /** "Pazar, 4 Ekim" */
    fun gunBaslik(gun: Int) = haftaGunuUzun(Gun.haftaGunu(gun)) + ", " + gunAy(gun)

    private var gunAdlari: Triple<Locale, Array<String>, Array<String>>? = null

    private fun adlar(): Triple<Locale, Array<String>, Array<String>> {
        val yerel = Locale.getDefault()
        gunAdlari?.let { if (it.first == yerel) return it }
        val s = DateFormatSymbols.getInstance(yerel)
        return Triple(yerel, s.weekdays, s.shortWeekdays).also { gunAdlari = it }
    }

    fun haftaGunuUzun(i: Int): String = adlar().second[sembolNo(i)]

    fun haftaGunuKisa(i: Int): String = adlar().third[sembolNo(i)]

    /** Java'da 1 = Pazar … 7 = Cumartesi; bizde 0 = Pazartesi. */
    private fun sembolNo(i: Int) = (i + 1) % 7 + 1

    // 24 saat ayarını sormak ve ÖÖ/ÖS adlarını almak her çağrıda pahalı (Settings + ICU); kısa süre bellekte tutulur.
    @Volatile private var saatOnbellegiAn = 0L
    @Volatile private var yirmiDortOnbellek = true
    @Volatile private var amPmOnbellek: Array<String> = arrayOf("AM", "PM")

    private fun yirmiDort(c: Context): Boolean {
        val simdi = android.os.SystemClock.elapsedRealtime()
        if (simdi - saatOnbellegiAn > 5_000L || saatOnbellegiAn == 0L) {
            yirmiDortOnbellek = DateFormat.is24HourFormat(c)
            amPmOnbellek = DateFormatSymbols.getInstance().amPmStrings
            saatOnbellegiAn = simdi
        }
        return yirmiDortOnbellek
    }

    fun saat(c: Context, dakika: Int): String {
        val d = Math.floorMod(dakika, 1440)
        val s = d / 60
        val m = d % 60
        if (yirmiDort(c)) return "%02d:%02d".format(s, m)
        val ek = amPmOnbellek[if (s < 12) 0 else 1]
        val s12 = if (s % 12 == 0) 12 else s % 12
        return "%d:%02d %s".format(s12, m, ek)
    }

    /** Izgara kenarındaki saat etiketi: "09:00" ya da "9 ÖÖ". */
    fun saatEtiketi(c: Context, saat: Int): String {
        if (yirmiDort(c)) return "%02d:00".format(saat)
        val ek = amPmOnbellek[if (saat < 12) 0 else 1]
        return "%d %s".format(if (saat % 12 == 0) 12 else saat % 12, ek)
    }

    /** Listede [gun] günü için etkinliğin saat bilgisi. */
    fun ornekSaati(c: Context, o: Ornek, gun: Int): String {
        if (o.tumGun) return c.getString(R.string.tum_gun)
        val ilk = gun == o.ilkGun
        val son = gun == o.sonGun
        return when {
            ilk && son ->
                if (o.bitis <= o.baslangic) saat(c, o.baslangicDk)
                else "${saat(c, o.baslangicDk)} – ${saat(c, o.bitisDk)}"
            ilk -> "${saat(c, o.baslangicDk)} –"
            son -> "– ${saat(c, o.bitisDk)}"
            else -> c.getString(R.string.tum_gun)
        }
    }

    /** "09:00 – 10:30 · Konum" ya da yalnız saat. */
    fun ornekAltYazisi(c: Context, o: Ornek, gun: Int): String =
        (if (o.konum.isBlank()) ornekSaati(c, o, gun) else ornekSaati(c, o, gun) + " · " + o.konum.lines().first()) +
            if (o.reddedildi) " · " + c.getString(R.string.reddedildi) else ""

    /** Süre: "1 sa 30 dk", "45 dk", "2 gün". */
    fun sure(c: Context, dakika: Int): String = when {
        dakika % 1440 == 0 && dakika >= 1440 -> c.getString(R.string.gun_n, dakika / 1440)
        dakika >= 60 && dakika % 60 == 0 -> c.getString(R.string.saat_n, dakika / 60)
        dakika >= 60 -> c.getString(R.string.saat_dk_n, dakika / 60, dakika % 60)
        else -> c.getString(R.string.dakika_n, dakika)
    }

    /** Göreli gün: Bugün / Yarın / "Pazar, 4 Ekim". */
    fun gunGoreli(c: Context, gun: Int, bugun: Int): String = when (gun - bugun) {
        0 -> c.getString(R.string.bugun)
        1 -> c.getString(R.string.yarin)
        -1 -> c.getString(R.string.dun)
        else -> gunBaslik(gun)
    }

    /** Dosya adı için "20261004". */
    fun dosyaTarihi(ms: Long): String {
        val g = Gun.yerelGun(ms, java.util.TimeZone.getDefault())
        return "%04d%02d%02d".format(Gun.yil(g), Gun.ay(g), Gun.ayinGunu(g))
    }

    /** Widget'ta gün etiketi: Bugün / Yarın / "Pzt 5". */
    fun widgetGunu(c: Context, gun: Int, bugun: Int): String = when (gun - bugun) {
        0 -> c.getString(R.string.bugun)
        1 -> c.getString(R.string.yarin)
        else -> haftaGunuKisa(Gun.haftaGunu(gun)) + " " + Gun.ayinGunu(gun)
    }

    /** Arama sonucunda tarih: "Bugün", "Yarın", yoksa "Pzt, 5 Eki" (başka yıldaysa yılıyla). */
    fun widgetGunuUzun(c: Context, gun: Int, bugun: Int): String = when (gun - bugun) {
        0 -> c.getString(R.string.bugun)
        1 -> c.getString(R.string.yarin)
        -1 -> c.getString(R.string.dun)
        else -> haftaGunuKisa(Gun.haftaGunu(gun)) + ", " + (if (Gun.yil(gun) == Gun.yil(bugun)) gunAyKisa(gun) else tamTarihKisa(gun))
    }

    /** Başlığa yazılan cümleden anlaşılanın kısa özeti ("Yarın · 14:00 – 15:00 · Her hafta"). */
    fun oneriOzeti(c: Context, s: DogalDil.Sonuc, bugun: Int): String {
        val p = ArrayList<String>()
        s.gun?.let { p.add(widgetGunuUzun(c, it, bugun)) }
        if (s.tumGun) p.add(c.getString(R.string.tum_gun))
        else if (s.baslangicDk != null) p.add(saat(c, s.baslangicDk) + (s.bitisDk?.let { " – " + saat(c, it) } ?: ""))
        s.sureDk?.let { p.add(sure(c, it)) }
        s.kural?.let { p.add(tekrar(c, it, "x", s.gun ?: bugun)) }
        return p.joinToString(" · ")
    }

    /** Sıradaki etkinlik widget'ında zaman satırı: "Şimdi · 15:00'e kadar", "Bugün 14:00", "Yarın 09:30". */
    fun siradakiZamani(c: Context, o: Ornek, simdi: Long, bugun: Int): String {
        if (o.tumGun) return c.getString(R.string.tum_gun)
        if (o.baslangic <= simdi) return c.getString(R.string.simdi_kadar, saat(c, o.bitisDk))
        return widgetGunu(c, o.ilkGun, bugun) + " " + saat(c, o.baslangicDk)
    }

    /** Hatırlatıcı: "10 dakika önce"; tüm günde "1 gün önce 09:00". */
    fun hatirlatici(c: Context, dakika: Int, tumGun: Boolean): String {
        if (tumGun) {
            if (dakika <= 0 && dakika > -1440) return c.getString(R.string.hat_gunu, saat(c, -dakika))
            val gun = (dakika + 1439) / 1440
            val gunIci = gun * 1440 - dakika
            return c.getString(R.string.hat_gun_once, gun, saat(c, gunIci))
        }
        return when {
            dakika == 0 -> c.getString(R.string.hat_anda)
            dakika % 10080 == 0 -> c.getString(R.string.hat_hafta, dakika / 10080)
            dakika % 1440 == 0 -> c.getString(R.string.hat_gun, dakika / 1440)
            dakika % 60 == 0 -> c.getString(R.string.hat_saat, dakika / 60)
            else -> c.getString(R.string.hat_dakika, dakika)
        }
    }

    /** Tekrar özeti; [kural] çözülemeyen ham kural için null verilir ve "Özel" yazılır. */
    fun tekrar(c: Context, kural: Kural?, ham: String?, baslangicGun: Int): String {
        if (ham.isNullOrBlank()) return c.getString(R.string.tekrar_yok)
        if (kural == null) return c.getString(R.string.tekrar_ozel)
        val s = StringBuilder()
        s.append(
            when (kural.sik) {
                Sik.GUNLUK -> if (kural.aralik == 1) c.getString(R.string.her_gun) else c.getString(R.string.her_n_gun, kural.aralik)
                Sik.HAFTALIK -> if (kural.aralik == 1) c.getString(R.string.her_hafta) else c.getString(R.string.her_n_hafta, kural.aralik)
                Sik.AYLIK -> if (kural.aralik == 1) c.getString(R.string.her_ay) else c.getString(R.string.her_n_ay, kural.aralik)
                Sik.YILLIK -> if (kural.aralik == 1) c.getString(R.string.her_yil) else c.getString(R.string.her_n_yil, kural.aralik)
            }
        )
        when (kural.sik) {
            Sik.HAFTALIK -> {
                val gunler = kural.gunler.ifEmpty { setOf(Gun.haftaGunu(baslangicGun)) }
                s.append(" · ").append(gunler.sorted().joinToString(", ") { haftaGunuKisa(it) })
            }
            Sik.AYLIK ->
                if (kural.aySirasi == 0) {
                    s.append(" · ").append(c.getString(R.string.ayin_n_gunu, Gun.ayinGunu(baslangicGun)))
                } else {
                    val sira = if (kural.aySirasi == -1) c.getString(R.string.son_sira)
                    else c.resources.getStringArray(R.array.sira_adlari)[kural.aySirasi - 1]
                    s.append(" · ").append(sira).append(' ').append(haftaGunuUzun(kural.gunler.first()))
                }
            else -> {}
        }
        if (kural.sayi > 0) s.append(" · ").append(c.getString(R.string.n_kez, kural.sayi))
        else if (kural.bitisGun != null) s.append(" · ").append(c.getString(R.string.tarihe_kadar, tamTarihKisa(kural.bitisGun)))
        return s.toString()
    }

    private fun String.basHarfBuyuk() = if (isEmpty()) this else substring(0, 1).uppercase(Locale.getDefault()) + substring(1)
}
