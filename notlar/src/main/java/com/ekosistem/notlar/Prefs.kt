package com.ekosistem.notlar

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("notlar", Context.MODE_PRIVATE)

    fun klasorUri(c: Context): String? = sp(c).getString("klasor_uri", null)

    fun klasorUriKaydet(c: Context, uri: String) {
        sp(c).edit().putString("klasor_uri", uri).apply()
    }

    fun sabitler(c: Context): Set<String> =
        sp(c).getStringSet("sabitler", emptySet()) ?: emptySet()

    fun sabitDegistir(c: Context, id: String): Boolean {
        val s = sabitler(c).toMutableSet()
        val eklendi = if (!s.add(id)) { s.remove(id); false } else true
        sp(c).edit().putStringSet("sabitler", s).apply()
        return eklendi
    }

    /** Google Keep'ten daha önce alınmış notların anahtarları; ikinci aktarma çiftlemesin. */
    fun keepAktarilanlar(c: Context): Set<String> =
        sp(c).getStringSet("keep_aktarilanlar", emptySet()) ?: emptySet()

    fun keepAktarilanlarEkle(c: Context, yeni: Collection<String>) {
        if (yeni.isEmpty()) return
        val s = keepAktarilanlar(c).toMutableSet()
        s.addAll(yeni)
        sp(c).edit().putStringSet("keep_aktarilanlar", s).apply()
    }

    fun tema(c: Context): Int = sp(c).getInt("tema", 0)

    fun temaKaydet(c: Context, t: Int) {
        sp(c).edit().putInt("tema", t).apply()
    }

    fun vurguIndeksi(c: Context): Int = sp(c).getInt("vurgu", 0)

    fun vurguKaydet(c: Context, indeks: Int) {
        sp(c).edit().putInt("vurgu", indeks).apply()
    }

    /** Çöpteki notun geldiği klasör (null ise ana klasör). */
    fun copKaynagi(c: Context, uri: String): String? = sp(c).getString("cop:$uri", null)

    fun copKaynagiKaydet(c: Context, uri: String, klasor: String?) {
        if (klasor == null) return
        sp(c).edit().putString("cop:$uri", klasor).apply()
    }

    fun copKaynagiSil(c: Context, uri: String) {
        sp(c).edit().remove("cop:$uri").apply()
    }

    fun kaynakModu(c: Context): Boolean = sp(c).getBoolean("kaynak_modu", false)

    fun kaynakModuKaydet(c: Context, acik: Boolean) {
        sp(c).edit().putBoolean("kaynak_modu", acik).apply()
    }

    // --- Editör tipografisi ---

    /** 0 = sistem, 1 = serif, 2 = eş aralıklı (mono). */
    fun yaziTipi(c: Context): Int = sp(c).getInt("yazi_tipi", 0)

    fun yaziTipiKaydet(c: Context, tip: Int) {
        sp(c).edit().putInt("yazi_tipi", tip).apply()
    }

    /** Gövde yazısının sp boyutu; başlıklar bundan türetilir. */
    fun yaziBoyu(c: Context): Int = sp(c).getInt("yazi_boyu", 16)

    fun yaziBoyuKaydet(c: Context, sp_: Int) {
        sp(c).edit().putInt("yazi_boyu", sp_).apply()
    }

    /**
     * Kurulmuş örnek şablon setinin sürümü. Yeni sürümle yeni örnekler
     * eklenirse bir kez daha kurulur; kullanıcının sildiği şablonlar bunun
     * dışında geri getirilmez.
     */
    /** "Hoş geldin" notu bir kez denenir; silinen not geri gelmez. */
    fun hosgeldinDenendi(c: Context): Boolean = sp(c).getBoolean("hosgeldin", false)

    fun hosgeldinDenendiKaydet(c: Context) {
        sp(c).edit().putBoolean("hosgeldin", true).apply()
    }

    fun sablonSurumu(c: Context): Int = sp(c).getInt("sablon_surumu", 0)

    fun sablonSurumuKaydet(c: Context, surum: Int) {
        sp(c).edit().putInt("sablon_surumu", surum).apply()
    }

    fun siralama(c: Context): Int = sp(c).getInt("siralama", 0)

    fun siralamaKaydet(c: Context, s: Int) {
        sp(c).edit().putInt("siralama", s).apply()
    }

    fun widgetNotu(c: Context, widgetId: Int): String? =
        sp(c).getString("widget_$widgetId", null)

    fun widgetNotuKaydet(c: Context, widgetId: Int, uri: String) {
        sp(c).edit().putString("widget_$widgetId", uri).apply()
    }

    fun widgetNotuSil(c: Context, widgetId: Int) {
        sp(c).edit().remove("widget_$widgetId").apply()
    }

    // --- Kilit ---

    fun pinOzeti(c: Context): String? = sp(c).getString("pin_ozet", null)

    fun pinTuzu(c: Context): String? = sp(c).getString("pin_tuz", null)

    fun pinKaydet(c: Context, ozet: String?, tuz: String?) {
        val d = sp(c).edit()
        if (ozet == null) d.remove("pin_ozet").remove("pin_tuz") else d.putString("pin_ozet", ozet).putString("pin_tuz", tuz)
        d.apply()
    }

    fun pinHataSayisi(c: Context): Int = sp(c).getInt("pin_hata", 0)

    fun pinHataSayisiKaydet(c: Context, sayi: Int) {
        sp(c).edit().putInt("pin_hata", sayi).apply()
    }

    fun pinBeklemeBitis(c: Context): Long = sp(c).getLong("pin_bekleme", 0L)

    fun pinBeklemeBitisKaydet(c: Context, zaman: Long) {
        sp(c).edit().putLong("pin_bekleme", zaman).apply()
    }

    fun kilitliNotlar(c: Context): Set<String> =
        sp(c).getStringSet("kilitli_notlar", emptySet()) ?: emptySet()

    fun kilitliNotDegistir(c: Context, uri: String): Boolean {
        val s = kilitliNotlar(c).toMutableSet()
        val eklendi = if (!s.add(uri)) { s.remove(uri); false } else true
        sp(c).edit().putStringSet("kilitli_notlar", s).apply()
        return eklendi
    }

    /** Eklenen görseller 2048 piksele küçültülür ve EXIF'i atılır (varsayılan açık). */
    fun gorselKucult(c: Context): Boolean = sp(c).getBoolean("gorsel_kucult", true)

    fun gorselKucultKaydet(c: Context, acik: Boolean) {
        sp(c).edit().putBoolean("gorsel_kucult", acik).apply()
    }

    fun ekranGizle(c: Context): Boolean = sp(c).getBoolean("ekran_gizle", false)

    fun ekranGizleKaydet(c: Context, acik: Boolean) {
        sp(c).edit().putBoolean("ekran_gizle", acik).apply()
    }

    /** Parmak izi kilidi ayrı bir seçimdir; PIN kurmak onu kendiliğinden açmaz. */
    fun parmakIzi(c: Context): Boolean = sp(c).getBoolean("parmak_izi", false)

    fun parmakIziKaydet(c: Context, acik: Boolean) {
        sp(c).edit().putBoolean("parmak_izi", acik).apply()
    }

    // --- Taşımada korunan değiştirme tarihi ---

    /**
     * Not kopyalanarak taşındıysa dosyanın tarihi bugüne kayar. Gerçek tarih
     * burada saklanır; not bir daha yazıldığında kayıt silinir.
     */
    fun zamanDamgasi(c: Context, uri: String): Long = sp(c).getLong("zaman:$uri", 0L)

    /** Damganın yanında ne zaman yazıldığı da saklanır; bkz. [korunanZaman]. */
    fun zamanDamgasiKaydet(c: Context, uri: String, zaman: Long) {
        val d = sp(c).edit()
        if (zaman <= 0) d.remove("zaman:$uri").remove("zamanyaz:$uri")
        else d.putLong("zaman:$uri", zaman).putLong("zamanyaz:$uri", System.currentTimeMillis())
        d.apply()
    }

    /**
     * Listede ve yedekte gösterilecek tarih: korunan tarih varsa o, yoksa
     * dosyanın kendi tarihi. Not uygulama dışında (Obsidian, Syncthing)
     * sonradan düzenlendiyse dosyanın tarihi daha yenidir ve o geçer.
     */
    fun gosterilenZaman(c: Context, uri: String, dosyaZamani: Long): Long {
        val damga = zamanDamgasi(c, uri)
        if (damga <= 0) return dosyaZamani
        val yazilma = sp(c).getLong("zamanyaz:$uri", 0L)
        return korunanZaman(damga, yazilma, dosyaZamani)
    }

    /**
     * Saf karar: damga yazıldıktan sonra dosya değiştiyse damga eskimiştir.
     * Yazılma anı bilinmeyen eski damgalar geçerli sayılır. Dosya damgadan
     * hemen önce yazıldığı için birkaç saniyelik pay bırakılır.
     */
    fun korunanZaman(damga: Long, yazilma: Long, dosyaZamani: Long): Long =
        if (yazilma > 0 && dosyaZamani > yazilma + DAMGA_PAYI) dosyaZamani else damga

    private const val DAMGA_PAYI = 10_000L

    // --- Oluşturma zamanı ---

    /**
     * Notun ilk oluşturulduğu an. Dosya sistemi bunu güvenilir saklamıyor (SAF
     * hiç vermiyor), dosyanın içine yazmak da notu değiştirmek olurdu; bu
     * yüzden burada tutulur. Bu özellikten önce oluşturulan notlarda 0'dır.
     */
    fun olusturma(c: Context, uri: String): Long = sp(c).getLong("olusturma:$uri", 0L)

    fun olusturmaKaydet(c: Context, uri: String, zaman: Long) {
        val d = sp(c).edit()
        if (zaman <= 0) d.remove("olusturma:$uri") else d.putLong("olusturma:$uri", zaman)
        d.apply()
    }

    /** Notun adresi değiştiğinde ona bağlı tüm ayarları yeni adrese taşır. */
    fun adresTasi(c: Context, eski: String, yeni: String) = adresleriTasi(c, mapOf(eski to yeni))

    /**
     * Birden çok notun adresi değişti (klasör yeniden adlandırma): hepsi tek
     * seferde taşınır. Not başına çağırmak, 1.000 notluk klasörde bütün ayarları
     * 1.000 kez okuyup yazmak demekti.
     */
    fun adresleriTasi(c: Context, degisim: Map<String, String>) {
        val tasinan = degisim.filter { it.key != it.value }
        if (tasinan.isEmpty()) return
        val d = sp(c).edit()
        var degisti = false

        val sabitler = sabitler(c)
        if (sabitler.any { it in tasinan }) {
            d.putStringSet("sabitler", sabitler.mapTo(HashSet()) { tasinan[it] ?: it })
            degisti = true
        }

        val kilitliler = kilitliNotlar(c)
        if (kilitliler.any { it in tasinan }) {
            d.putStringSet("kilitli_notlar", kilitliler.mapTo(HashSet()) { tasinan[it] ?: it })
            degisti = true
        }

        for ((eski, yeni) in tasinan) {
            val damga = zamanDamgasi(c, eski)
            if (damga > 0) {
                d.remove("zaman:$eski").putLong("zaman:$yeni", damga)
                val yazilma = sp(c).getLong("zamanyaz:$eski", 0L)
                if (yazilma > 0) d.remove("zamanyaz:$eski").putLong("zamanyaz:$yeni", yazilma)
                degisti = true
            }
            val olusturma = olusturma(c, eski)
            if (olusturma > 0) {
                d.remove("olusturma:$eski").putLong("olusturma:$yeni", olusturma)
                degisti = true
            }
        }

        // Notu gösteren widget'lar da yeni adresi izlesin.
        for ((anahtar, deger) in sp(c).all) {
            if (anahtar.startsWith("widget_") && deger is String && deger in tasinan) {
                d.putString(anahtar, tasinan.getValue(deger))
                degisti = true
            }
        }

        if (degisti) d.apply()
    }

    // --- Hatırlatıcılar ---

    fun hatirlatici(c: Context, uri: String): Long = sp(c).getLong("hat:$uri", 0L)

    fun hatirlaticiKaydet(c: Context, uri: String, zaman: Long) {
        if (zaman <= 0) sp(c).edit().remove("hat:$uri").apply()
        else sp(c).edit().putLong("hat:$uri", zaman).apply()
    }

    /** Hatırlatıcının tekrarı: [Hatirlatici.TEKRAR_YOK] ya da gün/hafta/ay. */
    fun hatirlaticiTekrari(c: Context, uri: String): Int = sp(c).getInt("hattekrar:$uri", 0)

    /** Tekrarların sayıldığı ilk zaman; ayın sonu gibi günler kaymasın diye ayrı tutulur. */
    fun hatirlaticiCapasi(c: Context, uri: String): Long = sp(c).getLong("hatcapa:$uri", 0L)

    fun hatirlaticiTekrariKaydet(c: Context, uri: String, tekrar: Int, capa: Long) {
        if (tekrar <= 0) {
            sp(c).edit().remove("hattekrar:$uri").remove("hatcapa:$uri").apply()
        } else {
            sp(c).edit().putInt("hattekrar:$uri", tekrar).putLong("hatcapa:$uri", capa).apply()
        }
    }

    fun tumHatirlaticilar(c: Context): Map<String, Long> =
        sp(c).all.filterKeys { it.startsWith("hat:") }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix("hat:") to it } }
            .toMap()
}
