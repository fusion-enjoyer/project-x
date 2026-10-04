package com.ekosistem.notlar

import java.util.Calendar
import java.util.Locale

/**
 * Görev son tarihi, Obsidian Tasks eklentisinin biçiminde:
 * `- [ ] Raporu gönder 📅 2026-10-10`. Dosyada düz metin olarak durur.
 *
 * Tarihler "gün numarası" (1970-01-01'den bu yana gün) olarak karşılaştırılır;
 * saat dilimi ve yaz saati hesaba karışmasın diye takvim günü elle çevrilir.
 */
object SonTarih {

    val DESEN = Regex("📅\\s*(\\d{4})-(\\d{2})-(\\d{2})")

    /** Satırdaki son tarihin gün numarası; yoksa ya da geçersizse null. */
    fun gun(satir: CharSequence): Long? {
        val m = DESEN.find(satir) ?: return null
        val yil = m.groupValues[1].toInt()
        val ay = m.groupValues[2].toInt()
        val gun = m.groupValues[3].toInt()
        if (ay !in 1..12 || gun !in 1..ayinGunleri(yil, ay)) return null
        return gunNumarasi(yil, ay, gun)
    }

    /** Görev metninden tarih işareti atılmış hali (listede ayrıca gösterilir). */
    fun temizle(metin: String): String = metin.replace(DESEN, "").replace(Regex("\\s{2,}"), " ").trim()

    /** Satıra son tarihi yazar; varsa değiştirir. */
    fun yaz(satir: String, yil: Int, ay: Int, gun: Int): String {
        val tarih = String.format(Locale.US, "📅 %04d-%02d-%02d", yil, ay, gun)
        return if (DESEN.containsMatchIn(satir)) {
            satir.replace(DESEN, tarih)
        } else {
            satir.trimEnd() + " " + tarih
        }
    }

    fun bugun(): Long {
        val t = Calendar.getInstance()
        return gunNumarasi(t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH))
    }

    /** Gün numarasının yıl, ay (1-12) ve günü. */
    fun tarih(gunNo: Long): Triple<Int, Int, Int> {
        // Howard Hinnant, "civil_from_days".
        val z = gunNo + 719468
        val donem = Math.floorDiv(z, 146097)
        val gd = z - donem * 146097
        val yd = (gd - gd / 1460 + gd / 36524 - gd / 146096) / 365
        val yilGunu = gd - (365 * yd + yd / 4 - yd / 100)
        val mp = (5 * yilGunu + 2) / 153
        val gun = (yilGunu - (153 * mp + 2) / 5 + 1).toInt()
        val ay = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val yil = (yd + donem * 400 + if (ay <= 2) 1 else 0).toInt()
        return Triple(yil, ay, gun)
    }

    /** Howard Hinnant, "days_from_civil". */
    fun gunNumarasi(yil: Int, ay: Int, gun: Int): Long {
        val y = (if (ay <= 2) yil - 1 else yil).toLong()
        val donem = Math.floorDiv(y, 400L)
        val yd = y - donem * 400
        val m = ay.toLong()
        val yilGunu = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + gun - 1
        val gd = yd * 365 + yd / 4 - yd / 100 + yilGunu
        return donem * 146097 + gd - 719468
    }

    private fun ayinGunleri(yil: Int, ay: Int): Int = when (ay) {
        2 -> if ((yil % 4 == 0 && yil % 100 != 0) || yil % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
