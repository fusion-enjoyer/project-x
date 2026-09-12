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
}
