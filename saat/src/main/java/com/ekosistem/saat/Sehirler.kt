package com.ekosistem.saat

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.TimeZone

/**
 * Telefonun saat dilimi veritabanından şehir listesi. Android 7+ şehir ve ülke
 * adlarını telefonun dilinde verir ("Londra", "Birleşik Krallık"); öncesinde
 * kimlikten türetilir ("London").
 */
object Sehirler {

    data class Sehir(val id: String, val ad: String, val ulke: String, val arama: String)

    @Volatile
    private var onbellek: Pair<String, List<Sehir>>? = null

    fun ad(id: String): String {
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching {
                android.icu.text.TimeZoneNames.getInstance(android.icu.util.ULocale.getDefault())
                    .getExemplarLocationName(id)
            }.getOrNull()?.let { if (it.isNotBlank()) return it }
        }
        return DunyaSaati.kimliktenAd(id)
    }

    fun ulke(id: String): String {
        if (Build.VERSION.SDK_INT >= 24) {
            val bolge = runCatching { android.icu.util.TimeZone.getRegion(id) }.getOrNull()
            if (bolge != null && bolge.length == 2) return Locale("", bolge).displayCountry
        }
        return id.substringBefore('/')
    }

    /** Bütün şehirler, ada göre sıralı. Dil değişene kadar bellekte tutulur. */
    fun hepsi(): List<Sehir> {
        val dil = Locale.getDefault().toLanguageTag()
        onbellek?.let { if (it.first == dil) return it.second }
        val liste = TimeZone.getAvailableIDs()
            .filter { DunyaSaati.sehirMi(it) && asilKimlik(it) }
            .map { id ->
                val ad = ad(id)
                val ulke = ulke(id)
                Sehir(id, ad, ulke, DunyaSaati.sadelestir("$ad $ulke ${DunyaSaati.kimliktenAd(id)}"))
            }
            .distinctBy { it.id }
            .sortedBy { DunyaSaati.sadelestir(it.ad) }
        onbellek = dil to liste
        return liste
    }

    /**
     * Takma adları ayıklar ("Asia/Calcutta" yerine "Asia/Kolkata",
     * "Australia/North" yerine "Australia/Darwin"); aynı şehir iki kez çıkmasın.
     */
    private fun asilKimlik(id: String): Boolean {
        if (Build.VERSION.SDK_INT < 24) return true
        return runCatching { android.icu.util.TimeZone.getCanonicalID(id) == id }.getOrDefault(true)
    }

    /** "GMT+3", "GMT−5:30". */
    fun gmt(id: String, simdi: Long = System.currentTimeMillis()): String {
        val dk = TimeZone.getTimeZone(id).getOffset(simdi) / 60_000
        val (isaret, sa, kalan) = DunyaSaati.farkParcalari(dk)
        val i = if (isaret < 0) "−" else "+"
        return if (kalan == 0) "GMT$i$sa" else "GMT$i$sa:%02d".format(kalan)
    }

    /** "Bugün · 2 sa geride", "Yarın · 6 sa ileride", "Bugün · Aynı saat". */
    fun farkMetni(context: Context, id: String, simdi: Long = System.currentTimeMillis()): String {
        val hedef = TimeZone.getTimeZone(id)
        val yerel = TimeZone.getDefault()
        val gun = when (DunyaSaati.gunFarki(hedef, yerel, simdi)) {
            1 -> context.getString(R.string.yarin)
            -1 -> context.getString(R.string.dun)
            else -> context.getString(R.string.bugun)
        }
        val (isaret, sa, dk) = DunyaSaati.farkParcalari(DunyaSaati.farkDk(hedef, yerel, simdi))
        if (isaret == 0) return "$gun · ${context.getString(R.string.ayni_saat)}"
        val sure = when {
            sa > 0 && dk > 0 -> context.getString(R.string.fark_sa_dk, sa, dk)
            sa > 0 -> context.getString(R.string.fark_sa, sa)
            else -> context.getString(R.string.fark_dk, dk)
        }
        val yon = context.getString(if (isaret > 0) R.string.fark_ileri else R.string.fark_geri, sure)
        return "$gun · $yon"
    }
}
