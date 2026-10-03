package com.ekosistem.saat

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.io.File

/**
 * Alarmlar ve ayarlar **cihaz korumalı depoda** durur (Android 7+). Telefon
 * gece güncellemeyle yeniden başlayıp kilit açılmadan sabah olursa normal depo
 * okunamaz; alarm yine de kurulup çalabilsin diye (Direct Boot).
 */
object Depo {

    fun baglam(context: Context): Context {
        val uygulama = context.applicationContext ?: context
        return if (Build.VERSION.SDK_INT >= 24) {
            uygulama.createDeviceProtectedStorageContext()
        } else {
            uygulama
        }
    }

    private val kilit = Any()

    private fun dosya(context: Context) = File(baglam(context).filesDir, "alarmlar.json")

    fun alarmlar(context: Context): List<Alarm> = synchronized(kilit) {
        val f = dosya(context)
        if (!f.exists()) return emptyList()
        runCatching { Alarm.jsondanListe(f.readText()) }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.saat }, { it.dakika }, { it.id }))
    }

    fun alarm(context: Context, id: Int): Alarm? = alarmlar(context).firstOrNull { it.id == id }

    /** Yarım kalmayan yazma: önce geçici dosya, sonra yeniden adlandırma. */
    fun kaydet(context: Context, alarmlar: List<Alarm>) = synchronized(kilit) {
        val f = dosya(context)
        f.parentFile?.mkdirs()
        val gecici = File(f.parentFile, f.name + ".yeni")
        gecici.writeText(Alarm.listedenJson(alarmlar))
        if (!gecici.renameTo(f)) {
            f.delete()
            gecici.renameTo(f)
        }
    }

    /** Ekler ya da aynı kimlikli alarmı değiştirir. */
    fun yaz(context: Context, alarm: Alarm) = synchronized(kilit) {
        val liste = alarmlar(context).filter { it.id != alarm.id } + alarm
        kaydet(context, liste)
    }

    fun sil(context: Context, id: Int) = synchronized(kilit) {
        kaydet(context, alarmlar(context).filter { it.id != id })
    }

    fun yeniId(context: Context): Int = (alarmlar(context).maxOfOrNull { it.id } ?: 0) + 1

    // --- Ayarlar ---

    fun ayarlar(context: Context): SharedPreferences =
        baglam(context).getSharedPreferences("saat", Context.MODE_PRIVATE)

    /** 0 sistem, 1 açık, 2 saf siyah. */
    fun tema(c: Context): Int = ayarlar(c).getInt("tema", 0)
    fun temaKaydet(c: Context, tema: Int) = ayarlar(c).edit().putInt("tema", tema).apply()

    /** Alarm çalarken ses tuşları: 0 ertele, 1 kapat, 2 hiçbir şey. */
    fun sesTusu(c: Context): Int = ayarlar(c).getInt("ses_tusu", 0)
    fun sesTusuKaydet(c: Context, deger: Int) = ayarlar(c).edit().putInt("ses_tusu", deger).apply()

    /** Yaklaşan alarm bildirimi kaç dakika önce; 0 = kapalı. */
    fun yaklasanDk(c: Context): Int = ayarlar(c).getInt("yaklasan_dk", 60)
    fun yaklasanDkKaydet(c: Context, dk: Int) = ayarlar(c).edit().putInt("yaklasan_dk", dk).apply()

    /** Kimse dokunmazsa alarm kaç dakika çalsın. */
    fun susmaDk(c: Context): Int = ayarlar(c).getInt("susma_dk", 10)
    fun susmaDkKaydet(c: Context, dk: Int) = ayarlar(c).edit().putInt("susma_dk", dk).apply()

    // --- Kronometre ve zamanlayıcılar (küçük; ayarlarla aynı dosyada) ---

    fun kronometre(c: Context): Kronometre =
        Kronometre.jsondan(ayarlar(c).getString("kronometre", null)).yenidenBaslatmaSonrasi(
            android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis()
        )
    fun kronometreKaydet(c: Context, k: Kronometre) = ayarlar(c).edit().putString("kronometre", k.json()).apply()

    fun zamanlayicilar(c: Context): List<Zamanlayici> =
        Zamanlayici.jsondanListe(ayarlar(c).getString("zamanlayicilar", null))

    fun zamanlayicilariKaydet(c: Context, liste: List<Zamanlayici>) =
        ayarlar(c).edit().putString("zamanlayicilar", Zamanlayici.listedenJson(liste)).commit()

    fun zamanlayici(c: Context, id: Int): Zamanlayici? = zamanlayicilar(c).firstOrNull { it.id == id }

    fun zamanlayiciYaz(c: Context, z: Zamanlayici) =
        zamanlayicilariKaydet(c, zamanlayicilar(c).map { if (it.id == z.id) z else it }
            .let { l -> if (l.any { it.id == z.id }) l else l + z })

    fun zamanlayiciSil(c: Context, id: Int) = zamanlayicilariKaydet(c, zamanlayicilar(c).filter { it.id != id })

    /** Dünya saatindeki şehirler (saat dilimi kimlikleri, eklenme sırasıyla). */
    fun sehirler(c: Context): List<String> = runCatching {
        val d = org.json.JSONArray(ayarlar(c).getString("sehirler", "[]"))
        List(d.length()) { d.getString(it) }
    }.getOrDefault(emptyList())

    fun sehirleriKaydet(c: Context, liste: List<String>) =
        ayarlar(c).edit().putString("sehirler", org.json.JSONArray(liste.distinct()).toString()).apply()

    /** Alarm klasörleri (adlar, oluşturulma sırasıyla). Boş klasör de kalır. */
    fun klasorler(c: Context): List<String> = runCatching {
        val d = org.json.JSONArray(ayarlar(c).getString("klasorler", "[]"))
        List(d.length()) { d.getString(it) }
    }.getOrDefault(emptyList())

    fun klasorleriKaydet(c: Context, liste: List<String>) =
        ayarlar(c).edit().putString("klasorler", org.json.JSONArray(liste.distinct()).toString()).apply()

    /** Klasörü yeniden adlandırır ya da (yeniAd boşsa) siler; içindeki alarmlar yeni adı alır / klasörsüz kalır. */
    fun klasorDegistir(c: Context, eski: String, yeniAd: String) = synchronized(kilit) {
        val liste = klasorler(c).map { if (it == eski) yeniAd else it }.filter { it.isNotBlank() }
        klasorleriKaydet(c, liste)
        kaydet(c, alarmlar(c).map { if (it.klasor == eski) it.copy(klasor = yeniAd) else it })
    }

    /** Zamanlayıcı hazır ayarları (ms, gösterim sırasıyla); hiç değiştirilmediyse varsayılanlar. */
    fun hazirSureler(c: Context): List<Long> = runCatching {
        val m = ayarlar(c).getString("hazir_sureler", null) ?: return@runCatching VARSAYILAN_HAZIR
        val d = org.json.JSONArray(m)
        List(d.length()) { d.getLong(it) }
    }.getOrDefault(VARSAYILAN_HAZIR)

    fun hazirSurelerKaydet(c: Context, liste: List<Long>) =
        ayarlar(c).edit().putString("hazir_sureler", org.json.JSONArray(liste.distinct()).toString()).apply()

    private val VARSAYILAN_HAZIR = listOf(60_000L, 180_000L, 300_000L, 600_000L, 900_000L, 1_800_000L)

    /** Son girilen süre: yeni zamanlayıcı ekranı onunla açılır. */
    fun sonSure(c: Context): String = ayarlar(c).getString("son_sure", "500") ?: "500"
    fun sonSureKaydet(c: Context, rakamlar: String) = ayarlar(c).edit().putString("son_sure", rakamlar).apply()

    /** Tatil modunun bittiği an (ms); gelecekte değilse tatil yok. */
    fun tatilBitis(c: Context): Long = ayarlar(c).getLong("tatil_bitis", 0)
    fun tatilBitisKaydet(c: Context, an: Long) = ayarlar(c).edit().putLong("tatil_bitis", an).apply()
    fun tatilVar(c: Context, simdi: Long = System.currentTimeMillis()): Boolean = tatilBitis(c) > simdi

    /** Bildirim izni bir kez istendi mi (Android 13+). */
    fun bildirimIstendi(c: Context): Boolean = ayarlar(c).getBoolean("bildirim_istendi", false)
    fun bildirimIstendiKaydet(c: Context) = ayarlar(c).edit().putBoolean("bildirim_istendi", true).apply()
}
