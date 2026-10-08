package com.ekosistem.takvim

import android.app.Activity
import android.os.Bundle
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Yalnız debug: tanıtım ekran görüntüleri için örnek takvimler ve etkinlikler.
 * Telefondaki yerel takvimler silinir, yerine Kişisel / İş / Spor kurulur;
 * etkinlikler bugüne göre (bu hafta ve bu ay) yerleşir. Takvim izni önceden
 * verilmeli (`pm grant`).
 *
 *   adb shell am start -n com.ekosistem.takvim/.TanitimVerisiActivity --es dil tr
 *   adb shell am start -n com.ekosistem.takvim/.TanitimVerisiActivity --es widget GundemWidget
 */
class TanitimVerisiActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --es widget SaatWidget: veri yerine o widget'ı ana ekrana ekletir (Android 8+).
        intent.getStringExtra("widget")?.let { ad ->
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.appwidget.AppWidgetManager.getInstance(this)
                    .requestPinAppWidget(android.content.ComponentName(this, "$packageName.$ad"), null, null)
            }
            finish()
            return
        }
        val tr = intent.getStringExtra("dil") != "en"
        Executors.newSingleThreadExecutor().execute {
            kur(tr)
            runOnUiThread { finish() }
        }
    }

    private fun kur(tr: Boolean) {
        fun s(turkce: String, ingilizce: String) = if (tr) turkce else ingilizce
        for (t in TakvimDeposu.takvimler(this)) {
            if (t.hesapTuru == CalendarContract.ACCOUNT_TYPE_LOCAL) TakvimDeposu.yerelTakvimSil(this, t)
        }
        val kisisel = TakvimDeposu.yerelTakvimOlustur(this, s("Kişisel", "Personal"), 0xFF0F766E.toInt()) ?: return
        val isTakvimi = TakvimDeposu.yerelTakvimOlustur(this, s("İş", "Work"), 0xFF2563EB.toInt()) ?: return
        val spor = TakvimDeposu.yerelTakvimOlustur(this, s("Spor", "Sport"), 0xFFEA580C.toInt()) ?: return

        val dilim = TimeZone.getDefault()
        val bugun = Calendar.getInstance(dilim).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        fun an(gun: Int, saat: Int, dakika: Int = 0): Long =
            (bugun.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, gun); set(Calendar.HOUR_OF_DAY, saat); set(Calendar.MINUTE, dakika)
            }.timeInMillis
        fun utcGun(gun: Int): Long {
            val y = (bugun.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, gun) }
            return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(y.get(Calendar.YEAR), y.get(Calendar.MONTH), y.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        fun saatli(
            takvim: Long, gun: Int, saat: Int, dk: Int, sure: Int, baslik: String,
            konum: String = "", kural: String? = null, aciklama: String = ""
        ) =
            TakvimDeposu.ekle(
                this,
                Etkinlik(
                    takvimId = takvim, baslik = baslik, konum = konum, aciklama = aciklama,
                    baslangic = an(gun, saat, dk), bitis = an(gun, saat, dk) + sure * 60_000L,
                    zamanDilimi = dilim.id, kural = kural, hatirlaticilar = listOf(10)
                )
            )
        fun tumGun(takvim: Long, gun: Int, gunSayisi: Int, baslik: String) =
            TakvimDeposu.ekle(
                this,
                Etkinlik(
                    takvimId = takvim, baslik = baslik, baslangic = utcGun(gun), bitis = utcGun(gun + gunSayisi),
                    tumGun = true, musaitlik = Etkinlik.UYGUN
                )
            )

        // Haftalık koşu: bu haftanın pazartesisinden başlar.
        val pazartesiFarki = ((bugun.get(Calendar.DAY_OF_WEEK) + 5) % 7)
        saatli(spor, -pazartesiFarki, 7, 0, 60, s("Sabah koşusu", "Morning run"), kural = "FREQ=WEEKLY;BYDAY=MO,WE,FR")
        saatli(
            isTakvimi, 0, 9, 30, 60, s("Ekip toplantısı", "Team meeting"), "Google Meet",
            aciklama = "https://meet.google.com/abc-defg-hij"
        )
        saatli(kisisel, 0, 12, 30, 60, s("Öğle yemeği · Ayşe", "Lunch with Ayşe"), "Karaköy")
        saatli(spor, 0, 18, 30, 60, "Pilates")
        saatli(kisisel, 1, 11, 0, 45, s("Diş randevusu", "Dentist"))
        tumGun(kisisel, 1, 1, s("Annemin doğum günü", "Mom's birthday"))
        saatli(isTakvimi, 2, 14, 0, 120, s("Proje sunumu", "Project demo"), s("Toplantı odası 3", "Room 3"))
        saatli(kisisel, -2, 19, 0, 120, s("Kitap kulübü", "Book club"))
        saatli(isTakvimi, 3, 10, 0, 30, s("Bütçe görüşmesi", "Budget review"))
        tumGun(kisisel, 5, 3, s("Kapadokya gezisi", "Cappadocia trip"))
        saatli(kisisel, 8, 20, 0, 150, s("Konser", "Concert"), "Zorlu PSM")
        tumGun(kisisel, 12, 1, s("Kira son gün", "Rent due"))
        saatli(kisisel, -6, 15, 0, 30, s("Doktor kontrolü", "Doctor check-up"))
        saatli(isTakvimi, 15, 9, 0, 480, s("Eğitim günü", "Training day"))
    }
}
