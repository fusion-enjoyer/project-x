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
}
