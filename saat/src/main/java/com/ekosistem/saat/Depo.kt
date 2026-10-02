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

    /** Bildirim izni bir kez istendi mi (Android 13+). */
    fun bildirimIstendi(c: Context): Boolean = ayarlar(c).getBoolean("bildirim_istendi", false)
    fun bildirimIstendiKaydet(c: Context) = ayarlar(c).edit().putBoolean("bildirim_istendi", true).apply()
}
