package com.ekosistem.saat

import android.app.Activity
import android.os.Bundle

/**
 * Yalnız debug: tanıtım ekran görüntüleri için örnek veri (alarmlar,
 * klasörler, şehirler, zamanlayıcılar, kronometre). Var olan veri silinir.
 *
 *   adb shell am start -n com.ekosistem.saat/.TanitimVerisiActivity --es dil tr
 *   adb shell am start -n com.ekosistem.saat/.TanitimVerisiActivity --es widget SaatWidget
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
        fun s(turkce: String, ingilizce: String) = if (tr) turkce else ingilizce

        val isKlasoru = s("İş", "Work")
        val ilac = s("İlaç", "Medicine")
        Depo.klasorleriKaydet(this, listOf(isKlasoru, ilac))
        Depo.kaydet(
            this,
            listOf(
                Alarm(1, 6, 45, gunler = Alarm.HAFTA_ICI, etiket = s("Kalk, koşu", "Up, run"), klasor = isKlasoru, gorev = Gorev.KOLAY),
                Alarm(2, 7, 30, gunler = Alarm.HAFTA_ICI, etiket = s("İşe çık", "Leave for work"), klasor = isKlasoru),
                Alarm(3, 9, 0, gunler = Alarm.HER_GUN, etiket = s("Vitamin", "Vitamins"), klasor = ilac),
                Alarm(4, 9, 30, gunler = Alarm.HAFTA_SONU, etiket = s("Hafta sonu", "Weekend"), acik = false),
                Alarm(5, 21, 0, gunler = Alarm.HER_GUN, etiket = s("Akşam ilacı", "Evening pill"), klasor = ilac),
                Alarm(6, 23, 0, etiket = s("Uyku vakti", "Bedtime"), acik = false)
            )
        )

        Depo.sehirleriKaydet(this, listOf("Europe/London", "America/New_York", "Asia/Tokyo", "Europe/Berlin"))
        Depo.sehirEtiketiKaydet(this, "Europe/Berlin", s("Annem", "Mom"))
        Depo.sehirEtiketiKaydet(this, "Europe/London", s("Ofis", "Office"))

        val dk = 60_000L
        Depo.zamanlayicilariKaydet(
            this,
            listOf(
                Zamanlayici(1, 25 * dk, kalanDurgun = 17 * dk + 42_000, etiket = "Pomodoro"),
                Zamanlayici(2, 4 * dk, etiket = s("Çay", "Tea")),
                Zamanlayici(3, 12 * dk, etiket = s("Makarna", "Pasta"))
            )
        )
        Depo.kronometreKaydet(
            this,
            Kronometre(
                birikmis = 5 * dk + 42_310,
                turlar = listOf(81_200L, 2 * dk + 47_900, 4 * dk + 12_450),
                duvar = 0
            )
        )

        AlarmKurucu.hepsiniKur(this)
        finish()
    }
}
