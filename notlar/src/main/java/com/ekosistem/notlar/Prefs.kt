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

    /** Örnek şablonlar bir kez oluşturuldu mu? (silinirse geri getirilmez) */
    fun sablonlarKuruldu(c: Context): Boolean = sp(c).getBoolean("sablonlar_kuruldu", false)

    fun sablonlarKurulduKaydet(c: Context) {
        sp(c).edit().putBoolean("sablonlar_kuruldu", true).apply()
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

    fun kilitliNotlar(c: Context): Set<String> =
        sp(c).getStringSet("kilitli_notlar", emptySet()) ?: emptySet()

    fun kilitliNotDegistir(c: Context, uri: String): Boolean {
        val s = kilitliNotlar(c).toMutableSet()
        val eklendi = if (!s.add(uri)) { s.remove(uri); false } else true
        sp(c).edit().putStringSet("kilitli_notlar", s).apply()
        return eklendi
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

    /** Otomatik kilitlenme seçeneğinin sırası (bkz. Kilit.GECIKMELER). */
    fun kilitGecikmesi(c: Context): Int = sp(c).getInt("kilit_gecikme", 0)

    fun kilitGecikmesiKaydet(c: Context, indeks: Int) {
        sp(c).edit().putInt("kilit_gecikme", indeks).apply()
    }

    // --- Taşımada korunan değiştirme tarihi ---

    /**
     * Not kopyalanarak taşındıysa dosyanın tarihi bugüne kayar. Gerçek tarih
     * burada saklanır; not bir daha yazıldığında kayıt silinir.
     */
    fun zamanDamgasi(c: Context, uri: String): Long = sp(c).getLong("zaman:$uri", 0L)

    fun zamanDamgasiKaydet(c: Context, uri: String, zaman: Long) {
        if (zaman <= 0) sp(c).edit().remove("zaman:$uri").apply()
        else sp(c).edit().putLong("zaman:$uri", zaman).apply()
    }

    /** Notun adresi değiştiğinde ona bağlı tüm ayarları yeni adrese taşır. */
    fun adresTasi(c: Context, eski: String, yeni: String) {
        if (eski == yeni) return
        val d = sp(c).edit()

        val sabitler = sabitler(c).toMutableSet()
        if (sabitler.remove(eski)) {
            sabitler.add(yeni)
            d.putStringSet("sabitler", sabitler)
        }

        val kilitliler = kilitliNotlar(c).toMutableSet()
        if (kilitliler.remove(eski)) {
            kilitliler.add(yeni)
            d.putStringSet("kilitli_notlar", kilitliler)
        }

        val damga = zamanDamgasi(c, eski)
        if (damga > 0) d.remove("zaman:$eski").putLong("zaman:$yeni", damga)

        // Notu gösteren widget'lar da yeni adresi izlesin.
        for ((anahtar, deger) in sp(c).all) {
            if (anahtar.startsWith("widget_") && deger == eski) d.putString(anahtar, yeni)
        }

        d.apply()
    }

    // --- Hatırlatıcılar ---

    fun hatirlatici(c: Context, uri: String): Long = sp(c).getLong("hat:$uri", 0L)

    fun hatirlaticiKaydet(c: Context, uri: String, zaman: Long) {
        if (zaman <= 0) sp(c).edit().remove("hat:$uri").apply()
        else sp(c).edit().putLong("hat:$uri", zaman).apply()
    }

    fun tumHatirlaticilar(c: Context): Map<String, Long> =
        sp(c).all.filterKeys { it.startsWith("hat:") }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix("hat:") to it } }
            .toMap()
}
