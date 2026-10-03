package com.ekosistem.takvim

import java.util.TimeZone

/** Telefonun takvim deposundaki bir takvim (Google, DAVx5, yerel…). */
data class Takvim(
    val id: Long,
    val ad: String,
    val hesap: String,
    val hesapTuru: String,
    val renk: Int,
    val gorunur: Boolean,
    val yazilabilir: Boolean,
    val sahip: String
)

/**
 * Bir etkinliğin tek bir gerçekleşmesi (depodaki `Instances` satırı). Tekrarlayan
 * etkinlik her tekrarında ayrı bir örnektir. Günler [Gun] biçimindedir ve
 * yereldir; tüm gün etkinliğinde UTC gece yarısından çözülür.
 */
data class Ornek(
    val etkinlikId: Long,
    val takvimId: Long,
    val baslik: String,
    val konum: String,
    val baslangic: Long,
    val bitis: Long,
    val tumGun: Boolean,
    val renk: Int,
    val tekrarli: Boolean,
    val ilkGun: Int,
    /** Son gün, dahil. Gece yarısında biten etkinlik ertesi günü almaz. */
    val sonGun: Int,
    /** İlk gündeki başlangıç dakikası (tüm gün için 0). */
    val baslangicDk: Int,
    /** Son gündeki bitiş dakikası (tüm gün için 1440). */
    val bitisDk: Int
) {
    /** [gun] günündeki dilimin başlangıç dakikası (zaman ızgarası için). */
    fun gunBaslangicDk(gun: Int): Int = if (gun == ilkGun) baslangicDk else 0

    fun gunBitisDk(gun: Int): Int = if (gun == sonGun) bitisDk else 1440

    fun gunuIcerir(gun: Int) = gun in ilkGun..sonGun
    val cokGunlu get() = sonGun > ilkGun

    companion object {
        fun olustur(
            etkinlikId: Long,
            takvimId: Long,
            baslik: String,
            konum: String,
            baslangic: Long,
            bitis: Long,
            tumGun: Boolean,
            renk: Int,
            tekrarli: Boolean,
            tz: TimeZone
        ): Ornek {
            if (tumGun) {
                val ilk = Gun.utcGun(baslangic)
                val son = if (bitis > baslangic) Gun.utcGun(bitis - 1) else ilk
                return Ornek(
                    etkinlikId, takvimId, baslik, konum, baslangic, bitis, true, renk, tekrarli,
                    ilk, maxOf(ilk, son), 0, 1440
                )
            }
            val ilk = Gun.yerelGun(baslangic, tz)
            val son = if (bitis > baslangic) Gun.yerelGun(bitis - 1, tz) else ilk
            val basDk = Gun.yerelDakika(baslangic, tz)
            val bitDk = if (bitis > baslangic) Gun.yerelDakika(bitis - 1, tz) + 1 else basDk
            val sonGun = maxOf(ilk, son)
            return Ornek(
                etkinlikId, takvimId, baslik, konum, baslangic, bitis, false, renk, tekrarli,
                ilk, sonGun, basDk, if (sonGun > ilk) bitDk else maxOf(bitDk, basDk)
            )
        }
    }
}

/** Etkinlik düzenleme/ayrıntı modeli (depodaki `Events` satırı + hatırlatıcılar). */
data class Etkinlik(
    val id: Long = 0,
    val takvimId: Long = 0,
    val baslik: String = "",
    val konum: String = "",
    val aciklama: String = "",
    /** Tüm günde UTC gece yarısı, değilse an (ms). */
    val baslangic: Long = 0,
    /** Tüm günde son günün ertesi UTC gece yarısı. Tekrarlayanlarda süre ayrıca verilir. */
    val bitis: Long = 0,
    val tumGun: Boolean = false,
    val zamanDilimi: String = "",
    val kural: String? = null,
    val hatirlaticilar: List<Int> = emptyList(),
    val renk: Int = 0,
    /** Bu bir istisna ise asıl tekrarlayan etkinliğin kimliği, değilse 0. */
    val asilId: Long = 0,
    val yazilabilir: Boolean = true,
    val takvimAdi: String = "",
    val durum: Int = 0
)

/** Tekrarlayan etkinliği düzenlerken/silerken hangi kısmın değişeceği. */
enum class Kapsam { BU, BUNDAN_SONRA, HEPSI }
