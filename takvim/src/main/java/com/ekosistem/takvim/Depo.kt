package com.ekosistem.takvim

import android.content.Context
import java.util.Calendar

/** Uygulamanın kendi ayarları. Etkinlikler burada değil, telefonun takvim deposunda. */
object Depo {
    private const val DOSYA = "takvim"

    const val GORUNUM_AY = 0
    const val GORUNUM_HAFTA = 1
    const val GORUNUM_GUN = 2
    const val GORUNUM_GUNDEM = 3

    private fun p(c: Context) = c.getSharedPreferences(DOSYA, Context.MODE_PRIVATE)

    /** 0 sistem, 1 açık, 2 saf siyah. */
    fun tema(c: Context) = p(c).getInt("tema", 0)
    fun temaKaydet(c: Context, v: Int) = p(c).edit().putInt("tema", v).apply()

    fun baslangicGorunumu(c: Context) = p(c).getInt("baslangic_gorunum", GORUNUM_AY)
    fun baslangicGorunumuKaydet(c: Context, v: Int) = p(c).edit().putInt("baslangic_gorunum", v).apply()

    /** -1 = telefonun ayarı; 0 Pazartesi, 5 Cumartesi, 6 Pazar. */
    fun haftaBasiAyari(c: Context) = p(c).getInt("hafta_basi", -1)
    fun haftaBasiKaydet(c: Context, v: Int) = p(c).edit().putInt("hafta_basi", v).apply()

    /** Ekranda kullanılan haftanın ilk günü (0 = Pazartesi). */
    fun haftaBasi(c: Context): Int = when (val a = haftaBasiAyari(c)) {
        -1 -> when (Calendar.getInstance().firstDayOfWeek) {
            Calendar.SUNDAY -> 6
            Calendar.SATURDAY -> 5
            else -> 0
        }
        else -> a
    }

    /** Yeni etkinlik için takvim; 0 = ilk yazılabilir takvim. */
    fun varsayilanTakvim(c: Context) = p(c).getLong("varsayilan_takvim", 0L)
    fun varsayilanTakvimKaydet(c: Context, v: Long) = p(c).edit().putLong("varsayilan_takvim", v).apply()

    /** Yeni etkinliğin hatırlatıcısı (dakika); -1 = yok. */
    fun varsayilanHatirlatma(c: Context) = p(c).getInt("varsayilan_hatirlatma", 10)
    fun varsayilanHatirlatmaKaydet(c: Context, v: Int) = p(c).edit().putInt("varsayilan_hatirlatma", v).apply()

    fun varsayilanSure(c: Context) = p(c).getInt("varsayilan_sure", 60)
    fun varsayilanSureKaydet(c: Context, v: Int) = p(c).edit().putInt("varsayilan_sure", v).apply()

    fun ertelemeDk(c: Context) = p(c).getInt("erteleme_dk", 10)
    fun ertelemeDkKaydet(c: Context, v: Int) = p(c).edit().putInt("erteleme_dk", v).apply()

    fun haftaNumaralari(c: Context) = p(c).getBoolean("hafta_numaralari", false)
    fun haftaNumaralariKaydet(c: Context, v: Boolean) = p(c).edit().putBoolean("hafta_numaralari", v).apply()

    /** Takvim izni daha önce istendi mi: reddedilip "bir daha sorma" denmişse düğme ayarlara götürür. */
    fun izinIstendi(c: Context) = p(c).getBoolean("izin_istendi", false)
    fun izinIstendiKaydet(c: Context) = p(c).edit().putBoolean("izin_istendi", true).apply()

    /** Ay widget'ında gösterilen ay, bu aydan kaç ay ileride/geride (0 = bu ay). */
    fun widgetAyOfseti(c: Context) = p(c).getInt("widget_ay_ofseti", 0)
    fun widgetAyOfsetiKaydet(c: Context, v: Int) = p(c).edit().putInt("widget_ay_ofseti", v).apply()

    /** Rehberdeki doğum günlerini takvime kat (rehber izni ister; varsayılan kapalı). */
    fun dogumGunleri(c: Context) = p(c).getBoolean("dogum_gunleri", false)
    fun dogumGunleriKaydet(c: Context, v: Boolean) = p(c).edit().putBoolean("dogum_gunleri", v).apply()

    /** Günlük özet bildirimi (varsayılan kapalı) ve saati (gece yarısından beri dakika). */
    fun ozetAcik(c: Context) = p(c).getBoolean("ozet_acik", false)
    fun ozetAcikKaydet(c: Context, v: Boolean) = p(c).edit().putBoolean("ozet_acik", v).apply()
    fun ozetDk(c: Context) = p(c).getInt("ozet_dk", 7 * 60 + 30)
    fun ozetDkKaydet(c: Context, v: Int) = p(c).edit().putInt("ozet_dk", v).apply()

    /** Davete "hayır" denen etkinlikleri de göster (varsayılan kapalı: listelerde görünmezler). */
    fun reddedilenleriGoster(c: Context) = p(c).getBoolean("reddedilenleri_goster", false)
    fun reddedilenleriGosterKaydet(c: Context, v: Boolean) = p(c).edit().putBoolean("reddedilenleri_goster", v).apply()

    fun bildirimIstendi(c: Context) = p(c).getBoolean("bildirim_istendi", false)
    fun bildirimIstendiKaydet(c: Context) = p(c).edit().putBoolean("bildirim_istendi", true).apply()

    /**
     * Ertelenmiş hatırlatıcılar (JSON satırları: e etkinlik, b/n başlangıç/bitiş,
     * a yeniden çalacağı an, t başlık, k konum, g tüm gün). Telefon yeniden
     * başlayınca yeniden kurulabilsin diye saklanır.
     */
    fun ertelemeler(c: Context): List<org.json.JSONObject> =
        (p(c).getStringSet("ertelemeler", emptySet()) ?: emptySet()).mapNotNull {
            runCatching { org.json.JSONObject(it) }.getOrNull()
        }

    fun ertelemeEkle(c: Context, e: Long, b: Long, n: Long, a: Long, t: String, k: String, g: Boolean, l: String = "") {
        ertelemeCikar(c, e, b)
        val yeni = HashSet(p(c).getStringSet("ertelemeler", emptySet()) ?: emptySet())
        yeni.add(
            org.json.JSONObject().put("e", e).put("b", b).put("n", n).put("a", a)
                .put("t", t).put("k", k).put("g", g).put("l", l).toString()
        )
        p(c).edit().putStringSet("ertelemeler", yeni).apply()
    }

    fun ertelemeCikar(c: Context, etkinlikId: Long, baslangic: Long) {
        val eski = p(c).getStringSet("ertelemeler", emptySet()) ?: emptySet()
        val yeni = eski.filterNot {
            runCatching { org.json.JSONObject(it) }.getOrNull()
                ?.let { j -> j.optLong("e") == etkinlikId && j.optLong("b") == baslangic } ?: false
        }.toSet()
        p(c).edit().putStringSet("ertelemeler", yeni).apply()
    }
}
