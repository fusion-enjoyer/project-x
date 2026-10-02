package com.ekosistem.saat

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer
import java.util.Calendar
import java.util.TimeZone
import com.ekosistem.tasarim.R as TR

/**
 * Alarm kurma ve düzenleme (taslak 2): tekerlekle saat, tekrar günleri ya da
 * belirli tarih, etiket, ses, yavaş yükselme, titreşim, erteleme ve "bir
 * sonrakini atla". Değişiklik ancak Kaydet'e basınca yazılır.
 */
class DuzenleActivity : AppCompatActivity() {

    private lateinit var alarm: Alarm
    private var yeni = true
    private var vurgu = 0
    private lateinit var tekSaat: SayiTekerlegi
    private lateinit var tekDakika: SayiTekerlegi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duzenle)
        vurgu = Tasarim.vurgu(this)

        val id = intent.getIntExtra(AlarmKurucu.EK_ID, -1)
        val kayitli = if (id >= 0) Depo.alarm(this, id) else null
        yeni = kayitli == null
        alarm = savedInstanceState?.getString("alarm")?.let { Alarm.jsondan(org.json.JSONObject(it)) }
            ?: kayitli
            ?: varsayilan()

        findViewById<TextView>(R.id.duzenleBaslik).setText(if (yeni) R.string.yeni_alarm else R.string.alarmi_duzenle)
        findViewById<View>(R.id.btnVazgec).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSil).apply {
            visibility = if (yeni) View.INVISIBLE else View.VISIBLE
            setOnClickListener { silOnayi() }
        }
        ipucuVer(findViewById(R.id.btnVazgec), findViewById(R.id.btnSil))
        findViewById<TextView>(R.id.btnKaydet).apply {
            backgroundTintList = ColorStateList.valueOf(vurgu)
            setTextColor(Tasarim.vurguUzeri(this@DuzenleActivity))
            setOnClickListener { kaydet() }
        }

        tekSaat = findViewById(R.id.tekSaat)
        tekDakika = findViewById(R.id.tekDakika)
        tekSaat.adet = 24
        tekDakika.adet = 60
        tekSaat.ekranOkuyucuAdi = getString(R.string.alarm)
        tekDakika.ekranOkuyucuAdi = getString(R.string.alarm)
        tekSaat.ayarla(alarm.saat)
        tekDakika.ayarla(alarm.dakika)
        tekSaat.degisti = { alarm = alarm.copy(saat = it, atla = 0); kalanYaz(); atlaYaz() }
        tekDakika.degisti = { alarm = alarm.copy(dakika = it, atla = 0); kalanYaz(); atlaYaz() }

        findViewById<View>(R.id.cipHaftaIci).setOnClickListener {
            alarm = alarm.copy(gunler = if (alarm.gunler == Alarm.HAFTA_ICI) 0 else Alarm.HAFTA_ICI, tarih = 0, atla = 0)
            tekrarYaz()
        }
        findViewById<View>(R.id.cipTarih).setOnClickListener { tarihSec() }
        findViewById<View>(R.id.atlaKutusu).setOnClickListener {
            alarm = Zamanlama.atlamayiDegistir(alarm, System.currentTimeMillis(), TimeZone.getDefault())
            atlaYaz()
            kalanYaz()
        }
        hepsiniYaz()
    }

    override fun onSaveInstanceState(durum: Bundle) {
        super.onSaveInstanceState(durum)
        durum.putString("alarm", alarm.json().toString())
    }

    /** Yeni alarm: en az yarım saat sonraki ilk tam saat (20:53 → 22:00, 20:05 → 21:00). */
    private fun varsayilan(): Alarm {
        val c = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30)
            if (get(Calendar.MINUTE) > 0) add(Calendar.HOUR_OF_DAY, 1)
        }
        return Alarm(id = -1, saat = c.get(Calendar.HOUR_OF_DAY), dakika = 0)
    }

    private fun hepsiniYaz() {
        kalanYaz()
        tekrarYaz()
        ayarlariYaz()
        atlaYaz()
    }

    private fun kalanYaz() {
        val tv = findViewById<TextView>(R.id.duzenleKalan)
        val simdi = System.currentTimeMillis()
        val an = Zamanlama.sonrakiOlagan(alarm.copy(acik = true), simdi, TimeZone.getDefault())
        if (an == null) {
            tv.text = ""
            return
        }
        val kalan = getString(R.string.sonraki_alarm_vurgu, Metinler.kalan(this, an - simdi))
        val tam = "${Metinler.gun(this, an, simdi)} ${Metinler.saat(this, an)} · $kalan"
        val metin = android.text.SpannableString(tam)
        val bas = tam.lastIndexOf(kalan)
        metin.setSpan(android.text.style.ForegroundColorSpan(vurgu), bas, tam.length, 0)
        metin.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), bas, tam.length, 0)
        tv.text = metin
    }

    private fun tekrarYaz() {
        val pastel = Tasarim.pastel(vurgu)
        val metin = ContextCompat.getColor(this, TR.color.metin)
        fun cip(tv: TextView, secili: Boolean) {
            tv.backgroundTintList = ColorStateList.valueOf(
                if (secili) pastel else ContextCompat.getColor(this, TR.color.kart)
            )
            tv.setTextColor(if (secili) vurgu else metin)
            tv.setTypeface(null, if (secili) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tv.isSelected = secili
        }
        cip(findViewById(R.id.cipHaftaIci), alarm.gunler == Alarm.HAFTA_ICI)
        val tarihCipi = findViewById<TextView>(R.id.cipTarih)
        if (alarm.tarih != 0) {
            tarihCipi.text = Metinler.kisaTarih(alarm.tarih)
            tarihCipi.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_takvim, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(tarihCipi, ColorStateList.valueOf(vurgu))
        } else {
            tarihCipi.setText(R.string.tarih_sec)
            tarihCipi.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        }
        cip(tarihCipi, alarm.tarih != 0)

        val kutu = findViewById<LinearLayout>(R.id.gunDugmeleri)
        kutu.removeAllViews()
        val d = resources.displayMetrics.density
        val harfler = Metinler.gunHarfleri(ikiHarf = true)
        val tamAdlar = java.text.DateFormatSymbols.getInstance().weekdays
        val takvimSirasi = intArrayOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        for (g in 0..6) {
            val secili = alarm.gunAcik(g)
            val tv = TextView(this).apply {
                text = harfler[g]
                textSize = 14f
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_gun)
                backgroundTintList = ColorStateList.valueOf(
                    if (secili) vurgu else ContextCompat.getColor(this@DuzenleActivity, TR.color.kart)
                )
                setTextColor(if (secili) Tasarim.vurguUzeri(this@DuzenleActivity) else metin)
                setTypeface(null, if (secili) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                contentDescription = tamAdlar[takvimSirasi[g]]
                isSelected = secili
                setOnClickListener {
                    alarm = alarm.copy(gunler = alarm.gunler xor (1 shl g), tarih = 0, atla = 0)
                    tekrarYaz()
                }
            }
            val lp = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            kutu.addView(tv, lp)
            if (g < 6) kutu.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        kalanYaz()
        atlaYaz()
    }

    private fun ayarlariYaz() {
        val kutu = findViewById<LinearLayout>(R.id.ayarKutusu)
        kutu.removeAllViews()
        satir(kutu, getString(R.string.etiket), alarm.etiket.ifBlank { "—" }) { etiketSor() }
        satir(kutu, getString(R.string.ses), sesAdi(alarm.ses)) { sesSec() }
        satir(kutu, getString(R.string.yavas_yukselme), yukselmeAdi(alarm.kademeliSn)) { yukselmeSec() }
        satir(kutu, getString(R.string.titresim), null, anahtar = alarm.titresim) {
            alarm = alarm.copy(titresim = !alarm.titresim)
            ayarlariYaz()
        }
        satir(kutu, getString(R.string.erteleme), ertelemeOzeti(), ayracYok = true) { ertelemeSec() }
    }

    private fun satir(
        kutu: LinearLayout,
        baslik: String,
        deger: String?,
        anahtar: Boolean? = null,
        ayracYok: Boolean = false,
        tikla: () -> Unit
    ) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_satir, kutu, false)
        v.findViewById<TextView>(R.id.satirBaslik).text = baslik
        v.findViewById<TextView>(R.id.satirDeger).text = deger ?: ""
        if (anahtar != null) {
            v.findViewById<SwitchCompat>(R.id.satirAnahtar).apply {
                visibility = View.VISIBLE
                isChecked = anahtar
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(vurgu, ContextCompat.getColor(this@DuzenleActivity, TR.color.anahtar_kapali))
                )
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
            }
            v.contentDescription = baslik
        }
        v.setOnClickListener { tikla() }
        kutu.addView(v)
        if (!ayracYok) {
            kutu.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@DuzenleActivity, TR.color.ayrac))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                leftMargin = (16 * resources.displayMetrics.density).toInt()
            })
        }
    }

    private fun atlaYaz() {
        val kutu = findViewById<View>(R.id.atlaKutusu)
        val simdi = System.currentTimeMillis()
        if (!alarm.tekrarli || yeni) {
            kutu.visibility = View.GONE
            return
        }
        kutu.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.atlaIkon).imageTintList = ColorStateList.valueOf(vurgu)
        val atlanmis = alarm.atla != 0 && !Zamanlama.atlamaGecti(alarm, simdi, TimeZone.getDefault())
        findViewById<TextView>(R.id.atlaBaslik).setText(
            if (atlanmis) R.string.atlamayi_geri_al else R.string.bir_sonrakini_atla
        )
        val gun = if (atlanmis) {
            Metinler.atlananGun(this, alarm)
        } else {
            Zamanlama.sonrakiOlagan(alarm.copy(atla = 0), simdi, TimeZone.getDefault())
                ?.let { Metinler.gun(this, it, simdi) } ?: ""
        }
        findViewById<TextView>(R.id.atlaAciklama).text =
            getString(if (atlanmis) R.string.atlandi_aciklama else R.string.atla_aciklama, gun)
    }

    // --- Seçim sayfaları ---

    private fun etiketSor() {
        AltSayfa(this)
            .baslik(getString(R.string.etiket))
            .girdi(getString(R.string.etiket_ipucu), alarm.etiket, getString(R.string.tamam)) {
                alarm = alarm.copy(etiket = it.take(40))
                ayarlariYaz()
            }
            .goster()
    }

    private fun sesAdi(ses: String): String = when (ses) {
        "" -> getString(R.string.ses_varsayilan)
        Alarm.SES_YERLESIK -> getString(R.string.ses_yerlesik)
        else -> runCatching { RingtoneManager.getRingtone(this, Uri.parse(ses))?.getTitle(this) }
            .getOrNull() ?: getString(R.string.ses_dosya)
    }

    private fun sesSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.ses))
        sayfa.madde(R.drawable.ic_alarm, getString(R.string.ses_varsayilan), secili = alarm.ses.isEmpty()) {
            alarm = alarm.copy(ses = ""); ayarlariYaz()
        }
        sayfa.madde(R.drawable.ic_alarm, getString(R.string.ses_yerlesik), secili = alarm.ses == Alarm.SES_YERLESIK) {
            alarm = alarm.copy(ses = Alarm.SES_YERLESIK); ayarlariYaz()
        }
        // Telefonun kendi alarm sesleri (çevrimdışı, cihazda).
        runCatching {
            val yonetici = RingtoneManager(this).apply { setType(RingtoneManager.TYPE_ALARM) }
            val imlec = yonetici.cursor
            while (imlec.moveToNext()) {
                val ad = imlec.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val adres = yonetici.getRingtoneUri(imlec.position).toString()
                sayfa.madde(R.drawable.ic_alarm, ad, secili = alarm.ses == adres) {
                    alarm = alarm.copy(ses = adres); ayarlariYaz()
                }
            }
        }
        sayfa.madde(R.drawable.ic_arti, getString(R.string.ses_dosya_sec)) {
            val niyet = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/*")
            runCatching { startActivityForResult(niyet, ISTEK_SES) }
        }
        sayfa.goster()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ISTEK_SES || resultCode != Activity.RESULT_OK) return
        val adres = data?.data ?: return
        // Kalıcı okuma izni: telefon yeniden başlasa da alarm bu dosyayı çalabilsin.
        runCatching {
            contentResolver.takePersistableUriPermission(adres, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        alarm = alarm.copy(ses = adres.toString())
        ayarlariYaz()
    }

    private fun yukselmeAdi(sn: Int): String = when {
        sn <= 0 -> getString(R.string.yukselme_yok)
        sn < 60 -> getString(R.string.yukselme_sn, sn)
        else -> getString(R.string.yukselme_dk, sn / 60)
    }

    private fun yukselmeSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.yavas_yukselme))
        for (sn in intArrayOf(0, 15, 30, 60, 120)) {
            sayfa.madde(R.drawable.ic_alarm, yukselmeAdi(sn), secili = alarm.kademeliSn == sn) {
                alarm = alarm.copy(kademeliSn = sn); ayarlariYaz()
            }
        }
        sayfa.goster()
    }

    private fun ertelemeOzeti(): String =
        if (alarm.ertelemeSiniri == 0) getString(R.string.erteleme_ozet_sinirsiz, alarm.ertelemeDk)
        else getString(R.string.erteleme_ozet, alarm.ertelemeDk, alarm.ertelemeSiniri)

    /** İki adım: önce süre, sonra kaç kez. */
    private fun ertelemeSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.erteleme_suresi))
        for (dk in CalmaActivity.SURELER) {
            sayfa.madde(R.drawable.ic_alarm, getString(R.string.dakika_n, dk), secili = alarm.ertelemeDk == dk) {
                alarm = alarm.copy(ertelemeDk = dk)
                ayarlariYaz()
                ertelemeSiniriSec()
            }
        }
        sayfa.goster()
    }

    private fun ertelemeSiniriSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.erteleme_siniri))
        for (n in intArrayOf(1, 2, 3, 5, 10)) {
            sayfa.madde(R.drawable.ic_alarm, getString(R.string.kez, n), secili = alarm.ertelemeSiniri == n) {
                alarm = alarm.copy(ertelemeSiniri = n); ayarlariYaz()
            }
        }
        sayfa.madde(R.drawable.ic_alarm, getString(R.string.sinirsiz), secili = alarm.ertelemeSiniri == 0) {
            alarm = alarm.copy(ertelemeSiniri = 0); ayarlariYaz()
        }
        sayfa.goster()
    }

    private fun tarihSec() {
        val secici = DatePicker(this)
        val c = Calendar.getInstance()
        if (alarm.tarih != 0) {
            c.clear()
            c.set(alarm.tarih / 10000, (alarm.tarih / 100) % 100 - 1, alarm.tarih % 100)
        }
        secici.init(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), null)
        secici.minDate = System.currentTimeMillis() - 1000
        val sayfa = AltSayfa(this).baslik(getString(R.string.tarih_sec)).icerik(secici)
        sayfa.madde(TR.drawable.ic_onay_isaret, getString(R.string.bu_tarihi_sec)) {
            val anahtar = secici.year * 10000 + (secici.month + 1) * 100 + secici.dayOfMonth
            alarm = alarm.copy(tarih = anahtar, gunler = 0, atla = 0)
            tekrarYaz()
        }
        if (alarm.tarih != 0) {
            sayfa.madde(TR.drawable.ic_kapat, getString(R.string.tarihi_kaldir)) {
                alarm = alarm.copy(tarih = 0)
                tekrarYaz()
            }
        }
        sayfa.goster()
    }

    private fun silOnayi() {
        AltSayfa(this)
            .mesaj(getString(R.string.alarm_silinsin_mi))
            .madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) {
                Depo.sil(this, alarm.id)
                AlarmKurucu.iptal(this, alarm.id)
                Bildirimler.ertelemeyiKaldir(this, alarm.id)
                AlarmKurucu.hepsiniKur(this)
                finish()
            }
            .madde(TR.drawable.ic_kapat, getString(R.string.vazgec)) {}
            .goster()
    }

    private fun kaydet() {
        val simdi = System.currentTimeMillis()
        var kaydedilecek = alarm.copy(
            id = if (yeni) Depo.yeniId(this) else alarm.id,
            acik = true,
            ertelemeSayisi = 0,
            ertelemeZamani = 0,
            kurulanZaman = 0
        )
        // Seçilen tarih ve saat geçmişte kaldıysa tarih kalkar, ilk uygun an kullanılır.
        if (kaydedilecek.tarih != 0 &&
            Zamanlama.sonrakiOlagan(kaydedilecek, simdi, TimeZone.getDefault()) == null
        ) {
            kaydedilecek = kaydedilecek.copy(tarih = 0)
        }
        Bildirimler.ertelemeyiKaldir(this, kaydedilecek.id)
        Depo.yaz(this, kaydedilecek)
        AlarmKurucu.hepsiniKur(this)
        MainActivity.calacakDiye(this, Depo.alarm(this, kaydedilecek.id))
        finish()
    }

    companion object {
        private const val ISTEK_SES = 7
    }
}
