package com.ekosistem.notlar

import android.app.Activity
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Yalnız debug: tanıtım ekran görüntüleri için örnek notlar (klasörler,
 * sabitler, bilgi kutusu, son tarihli görevler, bağlantılar, açık sekmeler).
 * Uygulama deposundaki notlar silinir (şablonlar kalır).
 *
 *   adb shell am start -n com.ekosistem.notlar/.TanitimVerisiActivity --es dil tr
 */
class TanitimVerisiActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tr = intent.getStringExtra("dil") != "en"
        val uygulama = applicationContext
        // Görünmez ekran açılır açılmaz kapanmalı; iş yazma sırasında sürer.
        NotDeposu.yazici.execute { kur(uygulama, tr) }
        finish()
    }

    private fun kur(c: android.content.Context, tr: Boolean) {
        fun s(turkce: String, ingilizce: String) = if (tr) turkce else ingilizce
        val depo = NotDeposu(c)
        for (n in depo.notlariListele(null, null)) {
            if (n.klasor != Sablonlar.KLASOR) depo.kaliciSil(n.uri)
        }
        for (k in depo.klasorAdlari()) if (k != Sablonlar.KLASOR) depo.klasorSil(k)
        for (a in Prefs.sabitSirasi(c)) Prefs.sabitDegistir(c, a)
        Sekmeler.degistir(c) { it.hepsiniKapat() }

        val gun = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun sonra(g: Int) = gun.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, g) }.time)
        val isKlasoru = s("İş", "Work")
        val kisisel = s("Kişisel", "Personal")
        depo.klasorOlustur(isKlasoru)
        depo.klasorOlustur(kisisel)

        val okuma = depo.notOlustur(
            s(
                "Okuma notları\n\n**Bu ay** — üç kitap, akşamları telefonsuz.\n\n> Az eşya, çok zaman. Ekranı kapatınca gün uzuyor.\n\n" +
                    "Defter tutmak gibi: *yavaş*, ama aklımda kalıyor.\n\nSıradaki: [[Kitap listesi]] #kitap",
                "Reading notes\n\n**This month** — three books, evenings without the phone.\n\n> Fewer things, more time. Turn off the screen and the day gets longer.\n\n" +
                    "Like keeping a journal: *slow*, but it sticks.\n\nNext: [[Book list]] #books"
            ),
            kisisel
        )
        val toplanti = depo.notOlustur(
            s(
                "Toplantı notları\n\n## Pazartesi · ekip\n- Yeni sürüm cuma günü\n- Tasarım geri bildirimleri toplanacak\n\n" +
                    "## Yapılacaklar\n- [ ] Sunum dosyasını hazırla 📅 ${sonra(2)}\n- [ ] Ayşe'ye rapor gönder 📅 ${sonra(4)}\n- [x] Toplantı odasını ayarla\n\n" +
                    "Kayıt: [toplantı bağlantısı](https://meet.google.com/abc-defg-hij) #iş",
                "Meeting notes\n\n## Monday · team\n- New release on Friday\n- Collect the design feedback\n\n" +
                    "## To do\n- [ ] Prepare the slides 📅 ${sonra(2)}\n- [ ] Send the report to Ayşe 📅 ${sonra(4)}\n- [x] Book the meeting room\n\n" +
                    "Recording: [meeting link](https://meet.google.com/abc-defg-hij) #work"
            ),
            isKlasoru
        )
        val plan = depo.notOlustur(
            s(
                "Bu haftanın planı\n\nHafta başında sakin bir başlangıç. #plan\n\n> [!tip] Önce en zorunu yap\n> Sabah ilk iş, telefonu açmadan.\n\n" +
                    "## Yapılacaklar\n- [x] Fatura ödemeleri\n- [x] Eski telefonu sıfırla\n- [ ] Kitaplığı düzenle 📅 ${sonra(1)}\n- [ ] Annemi ara\n- [ ] Bisikletin lastiğini değiştir\n\n" +
                    "## Notlar\nCuma akşamı **erken yat**, cumartesi sabah yürüyüş. Bkz. [[Okuma notları]]",
                "This week's plan\n\nA calm start to the week. #plan\n\n> [!tip] Do the hardest thing first\n> First thing in the morning, before opening the phone.\n\n" +
                    "## To do\n- [x] Pay the bills\n- [x] Reset the old phone\n- [ ] Tidy the bookshelf 📅 ${sonra(1)}\n- [ ] Call mom\n- [ ] Fix the bike tyre\n\n" +
                    "## Notes\nFriday night **sleep early**, Saturday morning walk. See [[Reading notes]]"
            )
        )
        val alisveris = depo.notOlustur(
            s(
                "Alışveriş listesi\n\n- [x] Ekmek\n- [x] Süt\n- [ ] Yumurta\n- [ ] Domates, biber\n- [ ] Zeytinyağı\n- [ ] Kahve",
                "Shopping list\n\n- [x] Bread\n- [x] Milk\n- [ ] Eggs\n- [ ] Tomatoes, peppers\n- [ ] Olive oil\n- [ ] Coffee"
            )
        )
        depo.notOlustur(
            s(
                "Tatil fikirleri\n\n- Kaş — sakin koylar, dalış\n- Safranbolu — eski konaklar\n- Kapadokya — sabah balonları\n- Datça — badem ve deniz",
                "Holiday ideas\n\n- Kaş — quiet coves, diving\n- Safranbolu — old mansions\n- Cappadocia — morning balloons\n- Datça — almonds and the sea"
            ),
            kisisel
        )
        depo.notOlustur(
            s(
                "${Sablonlar.bugununBasligi()}\n\n- [ ] Sabah yürüyüşü\n- [x] Kahvaltı\n\nGüzel bir gün. Akşam [[Bu haftanın planı]]na bakılacak.",
                "${Sablonlar.bugununBasligi()}\n\n- [ ] Morning walk\n- [x] Breakfast\n\nA good day. Check [[This week's plan]] in the evening."
            )
        )

        listOfNotNull(plan, alisveris).forEach { Prefs.sabitDegistir(c, it.toString()) }
        Sekmeler.degistir(c) { d ->
            plan?.let { d.ac(it.toString()) }
            toplanti?.let { d.yeniSekme(it.toString()) }
            okuma?.let { d.yeniSekme(it.toString()) }
            plan?.let { u -> d.sekmeler.firstOrNull { it.adres == u.toString() }?.let { d.sec(it.id) } }
        }
        NotWidget.hepsiniGuncelle(c)
    }
}
