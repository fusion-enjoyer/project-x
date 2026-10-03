package com.ekosistem.saat

import kotlin.random.Random

/**
 * Kapatma görevi: alarm, çözülünce susan basit işlemlerle kapatılır (uyku
 * sersemliği kapatmasın). Saf mantık; arayüz [CalmaActivity]'de. Alarm yine de
 * susma süresinde kendiliğinden susar, görev kimseyi çalan alarmda hapsetmez.
 */
object Gorev {

    const val YOK = 0
    const val KOLAY = 1
    const val ORTA = 2
    const val ZOR = 3

    data class Soru(val metin: String, val cevap: Int)

    /** Zorluk kadar işlem: 1 → 1 toplama, 2 → 2 çarpma, 3 → 3 çarpma + toplama. */
    fun sorular(zorluk: Int, rastgele: Random = Random.Default): List<Soru> =
        List(zorluk.coerceIn(0, ZOR)) { soru(zorluk, rastgele) }

    private fun soru(zorluk: Int, r: Random): Soru = when (zorluk) {
        KOLAY -> {
            val a = r.nextInt(10, 60)
            val b = r.nextInt(10, 60)
            Soru("$a + $b", a + b)
        }
        ORTA -> {
            val a = r.nextInt(6, 13)
            val b = r.nextInt(3, 10)
            Soru("$a × $b", a * b)
        }
        else -> {
            val a = r.nextInt(12, 20)
            val b = r.nextInt(6, 10)
            val c = r.nextInt(10, 50)
            Soru("$a × $b + $c", a * b + c)
        }
    }

    /** Girilen yazı soruyu doğru çözüyor mu (boş ve taşan girdi yanlış sayılır). */
    fun dogruMu(soru: Soru, girdi: String): Boolean = girdi.toIntOrNull() == soru.cevap
}
