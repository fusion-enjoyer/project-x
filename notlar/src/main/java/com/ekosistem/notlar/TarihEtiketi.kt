package com.ekosistem.notlar

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Görev son tarihinin okunur hali: "Bugün", "Yarın", "3 gün gecikti",
 * "2 gün kaldı", bir haftadan uzaksa "12 Eki" (başka yıldaysa yıl da).
 * Görevler ekranı ve widget'lar ortak kullanır. Tarih biçimleri pahalı
 * olduğu için ilk gerektiğinde kurulur ve örnek boyunca saklanır.
 */
class TarihEtiketi(private val context: Context) {

    /** Etiketler bu güne göre; liste her yenilendiğinde [tazele] çağrılır. */
    var bugun: Long = SonTarih.bugun()
        private set

    private var buYilBicimi: SimpleDateFormat? = null
    private var yilliBicim: SimpleDateFormat? = null
    private val takvim: Calendar = Calendar.getInstance()

    fun tazele() {
        bugun = SonTarih.bugun()
    }

    /** Son tarihe kalan gün; geçmişse eksi. */
    fun fark(gun: Long): Int = (gun - bugun).toInt()

    fun etiket(gun: Long): String {
        val r = context.resources
        val fark = fark(gun)
        return when {
            fark == 0 -> r.getString(R.string.bugun)
            fark == 1 -> r.getString(R.string.yarin)
            fark < 0 -> r.getQuantityString(R.plurals.gun_gecikti, -fark, -fark)
            fark < 7 -> r.getQuantityString(R.plurals.gun_kaldi, fark, fark)
            else -> {
                val (yil, ay, g) = SonTarih.tarih(gun)
                takvim.clear()
                takvim.set(yil, ay - 1, g)
                // Bu yılın tarihinde yıl yazılmaz.
                val bicim = if (yil == SonTarih.tarih(bugun).first) {
                    buYilBicimi ?: bicim("MMMd").also { buYilBicimi = it }
                } else {
                    yilliBicim ?: bicim("yMMMd").also { yilliBicim = it }
                }
                bicim.format(takvim.time)
            }
        }
    }

    private fun bicim(iskelet: String): SimpleDateFormat =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), iskelet), Locale.getDefault())
}
