package com.ekosistem.saat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import android.widget.DatePicker
import com.ekosistem.tasarim.AltSayfa
import java.util.Calendar
import com.ekosistem.tasarim.BosDurum
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer
import java.util.TimeZone
import com.ekosistem.tasarim.R as TR

/**
 * Ana ekran: alarm listesi (taslak 1). Üstte bir sonraki alarmın ne zaman
 * çalacağı, eksik telefon ayarı varsa uyarı; altta yüzen sekme çubuğu.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var liste: LinearLayout
    private lateinit var bosDurum: View
    private lateinit var kaydirici: View
    private var vurgu = 0
    private var sekme = SEKME_ALARM
    private lateinit var kronometre: KronometreSekmesi
    private lateinit var zamanlayici: ZamanlayiciSekmesi
    private lateinit var dunya: DunyaSekmesi
    private val isleyici = Handler(Looper.getMainLooper())
    private val dakikalik = object : Runnable {
        override fun run() {
            ustBilgiyiYaz(Depo.alarmlar(this@MainActivity))
            isleyici.postDelayed(this, 1000L * (60 - (System.currentTimeMillis() / 1000) % 60) + 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vurgu = Tasarim.vurgu(this)
        liste = findViewById(R.id.alarmListesi)
        bosDurum = findViewById(R.id.bosDurum)
        kaydirici = findViewById(R.id.kaydirici)
        sekme = savedInstanceState?.getInt("sekme", SEKME_ALARM)
            ?: intent.getIntExtra(EK_SEKME, SEKME_ALARM)
        kronometre = KronometreSekmesi(this)
        zamanlayici = ZamanlayiciSekmesi(this)
        dunya = DunyaSekmesi(this, bosDurum)

        findViewById<ImageButton>(R.id.btnYeni).apply {
            imageTintList = ColorStateList.valueOf(Tasarim.vurguUzeri(this@MainActivity))
            setOnClickListener {
                when (sekme) {
                    SEKME_ZAMANLAYICI -> zamanlayici.yeniDugmesi()
                    SEKME_DUNYA -> dunya.sehirEkle()
                    else -> startActivity(Intent(this@MainActivity, DuzenleActivity::class.java))
                }
            }
        }
        findViewById<View>(R.id.btnAyarlar).setOnClickListener {
            startActivity(Intent(this, AyarlarActivity::class.java))
        }
        findViewById<View>(R.id.uyariSeridi).setOnClickListener { Kontrol.sayfaGoster(this) }
        findViewById<View>(R.id.btnTatil).setOnClickListener { tatilSayfasi() }
        findViewById<View>(R.id.tatilSeridi).setOnClickListener { tatilSayfasi() }
        ipucuVer(findViewById(R.id.btnYeni), findViewById(R.id.btnAyarlar), findViewById(R.id.btnTatil))
        sekmeleriKur()
    }

    override fun onSaveInstanceState(durum: Bundle) {
        super.onSaveInstanceState(durum)
        durum.putInt("sekme", sekme)
    }

    override fun onResume() {
        super.onResume()
        // Ucuz ve kendini onaran: ekran her açıldığında alarmlar yeniden kurulur.
        AlarmKurucu.hepsiniKur(this)
        yenile()
        bildirimIzniIste()
        isleyici.post(dakikalik)
    }

    override fun onPause() {
        super.onPause()
        isleyici.removeCallbacks(dakikalik)
        kronometre.durdur()
        zamanlayici.durdur()
    }

    override fun onNewIntent(yeni: Intent) {
        super.onNewIntent(yeni)
        if (yeni.hasExtra(EK_SEKME)) {
            sekme = yeni.getIntExtra(EK_SEKME, SEKME_ALARM)
            sekmeleriKur()
        }
    }

    private fun yenile() {
        val alarmlar = Depo.alarmlar(this).filter { it.id != DENEME_ID }
        ustBilgiyiYaz(alarmlar)
        uyariyiYaz()
        tatilSeridiniYaz()
        bosDurum.translationY = 0f
        kronometre.goster(sekme == SEKME_KRONOMETRE)
        zamanlayici.goster(sekme == SEKME_ZAMANLAYICI)
        if (sekme != SEKME_ALARM) {
            liste.removeAllViews()
            kaydirici.visibility = View.GONE
            findViewById<View>(R.id.btnYeni).visibility =
                if (sekme == SEKME_KRONOMETRE) View.GONE else View.VISIBLE
            if (sekme != SEKME_DUNYA) bosDurum.visibility = View.GONE
            dunya.goster(sekme == SEKME_DUNYA)
            return
        }
        dunya.goster(false)
        findViewById<View>(R.id.btnYeni).visibility = View.VISIBLE
        kaydirici.visibility = View.VISIBLE
        liste.removeAllViews()
        if (alarmlar.isEmpty()) {
            BosDurum.goster(
                bosDurum, R.drawable.ic_alarm,
                getString(R.string.bos_alarm_baslik), getString(R.string.bos_alarm_aciklama),
                getString(R.string.alarm_ekle) to {
                    startActivity(Intent(this, DuzenleActivity::class.java))
                }
            )
            return
        }
        bosDurum.visibility = View.GONE
        val simdi = System.currentTimeMillis()
        for (a in alarmlar) liste.addView(kart(a, simdi))
    }

    /** "Sonraki alarm 8 sa 12 dk sonra · Yarın 06:30" — kalan süre vurgulu. */
    private fun ustBilgiyiYaz(alarmlar: List<Alarm>) {
        val baslik = findViewById<TextView>(R.id.baslik)
        val alt = findViewById<TextView>(R.id.altBaslik)
        baslik.setText(
            when (sekme) {
                SEKME_DUNYA -> R.string.dunya_saati
                SEKME_ZAMANLAYICI -> R.string.zamanlayici
                SEKME_KRONOMETRE -> R.string.kronometre
                else -> R.string.alarm
            }
        )
        if (sekme != SEKME_ALARM) {
            alt.visibility = View.GONE
            return
        }
        alt.visibility = View.VISIBLE
        val simdi = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val sonraki = alarmlar.filter { it.id != DENEME_ID }
            .mapNotNull { Zamanlama.sonrakiCalma(it, simdi, tz, Depo.tatilBitis(this)) }.minOrNull()
        if (sonraki == null) {
            alt.setText(R.string.acik_alarm_yok)
            return
        }
        val vurguMetni = getString(R.string.sonraki_alarm_vurgu, Metinler.kalan(this, sonraki - simdi))
        val tam = getString(
            R.string.sonraki_alarm, vurguMetni,
            "${Metinler.gun(this, sonraki, simdi)} ${Metinler.saat(this, sonraki)}"
        )
        val metin = SpannableStringBuilder(tam)
        val bas = tam.indexOf(vurguMetni)
        if (bas >= 0) {
            metin.setSpan(ForegroundColorSpan(vurgu), bas, bas + vurguMetni.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            metin.setSpan(StyleSpan(android.graphics.Typeface.BOLD), bas, bas + vurguMetni.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        alt.text = metin
    }

    private fun tatilSeridiniYaz() {
        val serit = findViewById<TextView>(R.id.tatilSeridi)
        val bitis = Depo.tatilBitis(this)
        if (sekme != SEKME_ALARM || bitis <= System.currentTimeMillis()) {
            serit.visibility = View.GONE
            return
        }
        serit.visibility = View.VISIBLE
        serit.text = getString(R.string.tatil_serit, Metinler.tarih(Zamanlama.gunAnahtari(bitis, TimeZone.getDefault())))
    }

    /** Tatil modu: tekrarlı alarmlar seçilen güne kadar durur, sonra kendiliğinden sürer. */
    private fun tatilSayfasi() {
        val simdi = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val sayfa = AltSayfa(this).baslik(getString(R.string.tatil_modu)).mesaj(getString(R.string.tatil_aciklama))
        for (gun in listOf(3, 7, 14)) {
            sayfa.madde(R.drawable.ic_takvim, getString(R.string.tatil_gun_sayisi, gun)) {
                tatilKur(Zamanlama.tatilBitisi(simdi, gun, tz))
            }
        }
        sayfa.madde(R.drawable.ic_takvim, getString(R.string.tatil_tarih_sec)) { tatilTarihSec() }
        if (Depo.tatilVar(this, simdi)) {
            sayfa.madde(TR.drawable.ic_kapat, getString(R.string.tatil_bitir), tehlikeli = true) { tatilKur(0) }
        }
        sayfa.goster()
    }

    private fun tatilTarihSec() {
        val secici = DatePicker(this)
        val yarin = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        secici.init(yarin.get(Calendar.YEAR), yarin.get(Calendar.MONTH), yarin.get(Calendar.DAY_OF_MONTH), null)
        secici.minDate = System.currentTimeMillis() - 1000
        AltSayfa(this).baslik(getString(R.string.tatil_tarih_baslik)).icerik(secici)
            .madde(TR.drawable.ic_onay_isaret, getString(R.string.bu_tarihi_sec)) {
                val an = Zamanlama.gununAni(
                    secici.year * 10000 + (secici.month + 1) * 100 + secici.dayOfMonth, 0, 0, TimeZone.getDefault()
                )
                tatilKur(an)
            }
            .goster()
    }

    private fun tatilKur(bitis: Long) {
        Depo.tatilBitisKaydet(this, bitis)
        AlarmKurucu.hepsiniKur(this)
        yenile()
    }

    private fun uyariyiYaz() {
        val serit = findViewById<View>(R.id.uyariSeridi)
        val eksik = Kontrol.uyari(this)
        if (eksik == null || sekme != SEKME_ALARM) {
            serit.visibility = View.GONE
            return
        }
        serit.visibility = View.VISIBLE
        findViewById<TextView>(R.id.uyariMetin).text =
            getString(R.string.kontrol_uyari, Kontrol.ad(this, eksik.tur).lowercase())
    }

    private fun kart(alarm: Alarm, simdi: Long): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_alarm, liste, false)
        val acik = alarm.acik
        val metin = ContextCompat.getColor(this, if (acik) TR.color.metin else TR.color.metin_ikincil)
        val ikincil = ContextCompat.getColor(this, TR.color.metin_ikincil)
        val soluk = (ikincil and 0x00FFFFFF) or (if (acik) 0xFF000000.toInt() else 0x99000000.toInt())

        v.findViewById<TextView>(R.id.alarmSaat).apply {
            text = Metinler.kucukOgleEki(Metinler.alarmSaati(this@MainActivity, alarm.saat, alarm.dakika))
            setTextColor(metin)
        }
        v.findViewById<TextView>(R.id.alarmEtiket).apply {
            text = alarm.etiket
            setTextColor(soluk)
            visibility = if (alarm.etiket.isBlank()) View.GONE else View.VISIBLE
        }

        val gunler = v.findViewById<LinearLayout>(R.id.alarmGunler)
        val ayrinti = v.findViewById<TextView>(R.id.alarmAyrinti)
        ayrinti.setTextColor(soluk)
        if (alarm.tekrarli) {
            ayrinti.visibility = View.GONE
            gunCipleri(gunler, alarm, acik)
        } else {
            gunler.visibility = View.GONE
            ayrinti.text = if (alarm.tarih != 0) {
                ayrinti.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_takvim, 0, 0, 0)
                // compoundDrawableTintList Android 6'da geldi; uyumlu yol:
                TextViewCompat.setCompoundDrawableTintList(ayrinti, ColorStateList.valueOf(soluk))
                "${Metinler.tarih(alarm.tarih)} · ${getString(R.string.bir_kez)}"
            } else {
                val an = Zamanlama.sonrakiOlagan(alarm, simdi, TimeZone.getDefault())
                val gun = an?.let { Metinler.gun(this, it, simdi) } ?: ""
                listOf(gun, getString(R.string.bir_kez)).filter { it.isNotEmpty() }.joinToString(" · ")
            }
        }

        val rozet = v.findViewById<TextView>(R.id.alarmRozet)
        when {
            Zamanlama.tatilde(alarm, simdi, Depo.tatilBitis(this)) -> {
                rozet.visibility = View.VISIBLE
                rozet.text = getString(R.string.tatilde_rozet)
            }
            acik && alarm.ertelemeZamani > simdi -> {
                rozet.visibility = View.VISIBLE
                rozet.text = getString(R.string.ertelendi_rozet, Metinler.saat(this, alarm.ertelemeZamani))
            }
            acik && alarm.atla != 0 && !Zamanlama.atlamaGecti(alarm, simdi, TimeZone.getDefault()) -> {
                rozet.visibility = View.VISIBLE
                rozet.text = getString(R.string.atlanacak, Metinler.atlananGun(this, alarm))
            }
            else -> rozet.visibility = View.GONE
        }

        val anahtar = v.findViewById<SwitchCompat>(R.id.alarmAnahtar)
        anahtar.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(vurgu, ContextCompat.getColor(this, TR.color.anahtar_kapali))
        )
        anahtar.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        anahtar.isChecked = acik
        anahtar.contentDescription = Metinler.alarmSaati(this, alarm.saat, alarm.dakika)
        anahtar.setOnCheckedChangeListener { _, yeni -> acKapat(alarm, yeni) }

        v.setOnClickListener {
            startActivity(Intent(this, DuzenleActivity::class.java).putExtra(AlarmKurucu.EK_ID, alarm.id))
        }
        v.setOnLongClickListener {
            secenekler(alarm)
            true
        }
        return v
    }

    private fun gunCipleri(kutu: LinearLayout, alarm: Alarm, acik: Boolean) {
        kutu.visibility = View.VISIBLE
        kutu.removeAllViews()
        val d = resources.displayMetrics.density
        val harfler = Metinler.gunHarfleri(ikiHarf = false)
        val pastel = Tasarim.pastel(vurgu)
        val kapali = ContextCompat.getColor(this, TR.color.metin_ikincil)
        for (g in 0..6) {
            val secili = alarm.gunAcik(g)
            val tv = TextView(this)
            tv.text = harfler[g]
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
            if (secili && acik) {
                tv.setBackgroundResource(R.drawable.bg_gun)
                tv.backgroundTintList = ColorStateList.valueOf(pastel)
                tv.setTextColor(vurgu)
            } else {
                tv.setTextColor((kapali and 0x00FFFFFF) or (if (secili) 0xFF000000.toInt() else 0x80000000.toInt()))
            }
            val lp = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt())
            lp.rightMargin = (4 * d).toInt()
            kutu.addView(tv, lp)
        }
        kutu.contentDescription = when (alarm.gunler) {
            Alarm.HER_GUN -> getString(R.string.her_gun)
            Alarm.HAFTA_ICI -> getString(R.string.hafta_ici)
            Alarm.HAFTA_SONU -> getString(R.string.hafta_sonu)
            else -> Metinler.gunHarfleri(ikiHarf = true).filterIndexed { i, _ -> alarm.gunAcik(i) }.joinToString(", ")
        }
    }

    private fun acKapat(alarm: Alarm, acik: Boolean) {
        val guncel = Depo.alarm(this, alarm.id) ?: return
        val yeni = if (acik) {
            guncel.copy(acik = true)
        } else {
            // Kapatılan alarmın ertelemesi de biter.
            Bildirimler.ertelemeyiKaldir(this, alarm.id)
            guncel.copy(acik = false, ertelemeSayisi = 0, ertelemeZamani = 0, kurulanZaman = 0)
        }
        Depo.yaz(this, yeni)
        AlarmKurucu.hepsiniKur(this)
        if (acik) calacakDiye(this, Depo.alarm(this, alarm.id))
        // Kartı ve üst bilgiyi tazele; anahtar animasyonu bitsin diye kısa gecikme.
        isleyici.postDelayed({ if (!isFinishing) yenile() }, 220)
    }

    private fun secenekler(alarm: Alarm) {
        val sayfa = AltSayfa(this).baslik(
            listOf(Metinler.alarmSaati(this, alarm.saat, alarm.dakika), alarm.etiket)
                .filter { it.isNotBlank() }.joinToString(" · ")
        )
        val simdi = System.currentTimeMillis()
        if (alarm.tekrarli && alarm.acik) {
            val atlanmis = alarm.atla != 0 && !Zamanlama.atlamaGecti(alarm, simdi, TimeZone.getDefault())
            sayfa.madde(
                R.drawable.ic_atla,
                getString(if (atlanmis) R.string.atlamayi_geri_al else R.string.bir_sonrakini_atla)
            ) {
                Depo.yaz(this, Zamanlama.atlamayiDegistir(alarm, System.currentTimeMillis(), TimeZone.getDefault()))
                AlarmKurucu.hepsiniKur(this)
                yenile()
            }
        }
        sayfa.madde(R.drawable.ic_cogalt, getString(R.string.cogalt)) {
            Depo.yaz(this, alarm.copy(id = Depo.yeniId(this), atla = 0, ertelemeSayisi = 0, ertelemeZamani = 0, kurulanZaman = 0))
            AlarmKurucu.hepsiniKur(this)
            yenile()
        }
        sayfa.madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) {
            Depo.sil(this, alarm.id)
            AlarmKurucu.iptal(this, alarm.id)
            Bildirimler.ertelemeyiKaldir(this, alarm.id)
            yenile()
            Toast.makeText(this, R.string.alarm_silindi, Toast.LENGTH_SHORT).show()
        }
        sayfa.goster()
    }

    private fun sekmeleriKur() {
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        kutu.removeAllViews()
        val d = resources.displayMetrics.density
        val sekmeler = listOf(
            Triple(SEKME_ALARM, R.drawable.ic_alarm, R.string.alarm),
            Triple(SEKME_DUNYA, R.drawable.ic_dunya, R.string.dunya_saati),
            Triple(SEKME_ZAMANLAYICI, R.drawable.ic_zamanlayici, R.string.zamanlayici),
            Triple(SEKME_KRONOMETRE, R.drawable.ic_kronometre, R.string.kronometre)
        )
        val pasif = ContextCompat.getColor(this, TR.color.metin_ikincil)
        for ((no, ikon, ad) in sekmeler) {
            val secili = no == sekme
            val hap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = getString(ad)
                isSelected = secili
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                if (secili) {
                    setBackgroundResource(R.drawable.bg_sekme)
                    backgroundTintList = ColorStateList.valueOf(Tasarim.pastel(vurgu))
                }
                setOnClickListener {
                    if (sekme != no) {
                        sekme = no
                        sekmeleriKur()
                        yenile()
                    }
                }
            }
            hap.addView(ImageView(this).apply {
                setImageResource(ikon)
                imageTintList = ColorStateList.valueOf(if (secili) vurgu else pasif)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
            if (secili) {
                hap.addView(TextView(this).apply {
                    text = getString(ad)
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(vurgu)
                    maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = (6 * d).toInt()
                })
            } else {
                ipucuVer(hap)
            }
            // Seçili hap içeriği kadar, diğerleri kalan yeri eşit paylaşır.
            val lp = if (secili) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (44 * d).toInt())
            } else {
                LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f)
            }
            kutu.addView(hap, lp)
        }
    }

    /** Android 13+: alarm ekranı bildirimle açıldığı için ilk alarmdan sonra bir kez sorulur. */
    private fun bildirimIzniIste() {
        if (Build.VERSION.SDK_INT < 33 || Depo.bildirimIstendi(this)) return
        if (Depo.alarmlar(this).none { it.id != DENEME_ID }) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        Depo.bildirimIstendiKaydet(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        uyariyiYaz()
    }

    companion object {
        const val SEKME_ALARM = 0
        const val SEKME_DUNYA = 1
        const val SEKME_ZAMANLAYICI = 2
        const val SEKME_KRONOMETRE = 3
        const val EK_SEKME = "sekme"

        /** Ayarlar'daki "Alarmı dene"nin listede görünmeyen alarmı. */
        const val DENEME_ID = 0

        /** "8 sa 12 dk sonra çalacak" — Google Saat'teki gibi kurulunca söylenir. */
        fun calacakDiye(activity: android.app.Activity, alarm: Alarm?) {
            val an = alarm?.let { Zamanlama.sonrakiCalma(it, System.currentTimeMillis(), TimeZone.getDefault(), Depo.tatilBitis(activity)) } ?: return
            Toast.makeText(
                activity,
                activity.getString(R.string.calacak, Metinler.kalan(activity, an - System.currentTimeMillis())),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
