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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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

    /** Alarm listesinde seçili klasör; null = Tümü. */
    private var seciliKlasor: String? = null

    /** Yatay kaydırma satır ya da çip şeridinde başladıysa sekme değişmez. */
    private var kaydirmaSatirdaBasladi = false
    private lateinit var kronometre: KronometreSekmesi
    private lateinit var zamanlayici: ZamanlayiciSekmesi
    private lateinit var dunya: DunyaSekmesi
    private val isleyici = Handler(Looper.getMainLooper())

    /** Sekmeler arası yatay kaydırma: sola kaydırınca sonraki sekme, sağa kaydırınca önceki. */
    private val kaydirmaAlgilayici by lazy {
        val d = resources.displayMetrics.density
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || kaydirmaSatirdaBasladi) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // Belirgin ve yataya yakın bir hareket; dikey kaydırma listeyi bozmasın.
                if (kotlin.math.abs(dx) < 80 * d || kotlin.math.abs(dx) < 2 * kotlin.math.abs(dy)) return false
                if (kotlin.math.abs(vx) < 500 * d) return false
                sekmeyeGec(sekme + if (dx < 0) 1 else -1, if (dx < 0) 1 else -1)
                return true
            }
        })
    }

    /** Alarm dışındaki sekmede geri tuşu uygulamadan çıkarmaz, Alarm sekmesine döndürür. */
    private val geriSekme = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Zamanlayıcıda tuş takımı açıksa önce listeye dön, sonra Alarm sekmesine.
            if (sekme == SEKME_ZAMANLAYICI && zamanlayici.girisiKapat()) return
            sekmeyeGec(SEKME_ALARM, -1)
        }
    }
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
        seciliKlasor = savedInstanceState?.getString("klasor")
        kronometre = KronometreSekmesi(this)
        zamanlayici = ZamanlayiciSekmesi(this)
        dunya = DunyaSekmesi(this, bosDurum)

        findViewById<ImageButton>(R.id.btnYeni).apply {
            imageTintList = ColorStateList.valueOf(Tasarim.vurguUzeri(this@MainActivity))
            setOnClickListener {
                when (sekme) {
                    SEKME_ZAMANLAYICI -> zamanlayici.yeniDugmesi()
                    SEKME_DUNYA -> dunya.sehirEkle()
                    else -> yeniAlarm()
                }
            }
        }
        findViewById<View>(R.id.btnAyarlar).setOnClickListener {
            startActivity(Intent(this, AyarlarActivity::class.java))
        }
        findViewById<View>(R.id.uyariSeridi).setOnClickListener { Kontrol.sayfaGoster(this) }
        findViewById<View>(R.id.btnTatil).setOnClickListener { tatilSayfasi() }
        findViewById<View>(R.id.btnSaatModu).setOnClickListener {
            startActivity(Intent(this, SaatModuActivity::class.java))
        }
        findViewById<View>(R.id.tatilSeridi).setOnClickListener { tatilSayfasi() }
        ipucuVer(findViewById(R.id.btnYeni), findViewById(R.id.btnAyarlar), findViewById(R.id.btnTatil), findViewById(R.id.btnSaatModu))
        onBackPressedDispatcher.addCallback(this, geriSekme)
        sekmeleriKur()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            kaydirmaSatirdaBasladi = listedeMi(R.id.alarmListesi, ev) || listedeMi(R.id.dunyaListesi, ev) ||
                ustundeMi(R.id.klasorKaydirici, ev) || ustundeMi(R.id.zamanHazirKaydirici, ev)
        }
        kaydirmaAlgilayici.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Dokunma, görünen bir yatay kaydırıcının (çip şeridi) kendi alanında mı? */
    private fun ustundeMi(kimlik: Int, ev: MotionEvent): Boolean {
        val v = findViewById<View>(kimlik) ?: return false
        if (!v.isShown) return false
        val kutu = android.graphics.Rect()
        return v.getGlobalVisibleRect(kutu) && kutu.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    /** Dokunma, verilen kapsayıcının görünen çocuklarından birinin üstünde mi? */
    private fun listedeMi(kapsayiciId: Int, ev: MotionEvent): Boolean {
        val kapsayici = findViewById<ViewGroup>(kapsayiciId) ?: return false
        if (!kapsayici.isShown) return false
        val kutu = android.graphics.Rect()
        for (i in 0 until kapsayici.childCount) {
            val c = kapsayici.getChildAt(i)
            if (c.getGlobalVisibleRect(kutu) && kutu.contains(ev.rawX.toInt(), ev.rawY.toInt())) return true
        }
        return false
    }

    /** Sekme içeriğini oluşturan görünümler (alt çubuk ve üst başlık hariç). */
    private val icerikKimlikleri = intArrayOf(
        R.id.kaydirici, R.id.sekmeDunya, R.id.sekmeKronometre, R.id.sekmeZamanlayici, R.id.bosDurum
    )

    /**
     * Sekme değişimi: eski içeriğin anlık görüntüsü yan tarafa kayarak soluk,
     * yeni içerik karşı taraftan kayarak gelir; alt çubuktaki hap da yumuşakça
     * yer değiştirir. [yon] +1: sağdaki sekmeye geçiş (içerik sola akar).
     */
    private fun sekmeyeGec(yeni: Int, yon: Int) {
        if (yeni !in SEKME_ALARM..SEKME_KRONOMETRE || yeni == sekme) return
        val kok = findViewById<FrameLayout>(R.id.sekmeIcerik)
        val eski = icerikAnligi(kok)
        val eskiUst = kok.top
        sekme = yeni
        sekmeDurumunuGuncelle(animasyonlu = true)
        yenile()
        gecisiOynat(kok, eski, eskiUst, yon)
    }

    private fun icerikAnligi(kok: FrameLayout): Bitmap? {
        if (kok.width == 0 || kok.height == 0) return null
        return runCatching {
            val bmp = Bitmap.createBitmap(kok.width, kok.height, Bitmap.Config.ARGB_8888)
            val tuval = Canvas(bmp)
            tuval.drawColor(ContextCompat.getColor(this, TR.color.zemin))
            for (id in icerikKimlikleri) {
                val v = findViewById<View>(id)
                if (v.visibility != View.VISIBLE) continue
                tuval.save()
                tuval.translate(v.left.toFloat(), v.top.toFloat())
                v.draw(tuval)
                tuval.restore()
            }
            bmp
        }.getOrNull()
    }

    private fun gecisiOynat(kok: FrameLayout, eski: Bitmap?, eskiUst: Int, yon: Int) {
        val d = resources.displayMetrics.density
        val mesafe = kok.width * 0.28f * yon
        val sure = 280L
        val yumusak = android.view.animation.DecelerateInterpolator(1.6f)
        if (eski != null) {
            val perde = ImageView(this).apply {
                setImageBitmap(eski)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // Alt çubuğun ve alt sis perdesinin altında kalır: çubuk yerinde durur.
            kok.addView(
                perde, kok.indexOfChild(findViewById(R.id.altSis)),
                FrameLayout.LayoutParams(eski.width, eski.height, Gravity.TOP or Gravity.START)
            )
            // Üstteki şeritler (tatil, klasör) sekmeyle değişince içerik alanı kayar;
            // anlık görüntü eski konumunda kalsın.
            kok.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    kok.viewTreeObserver.removeOnPreDrawListener(this)
                    perde.translationY = (eskiUst - kok.top).toFloat()
                    return true
                }
            })
            perde.animate().translationX(-mesafe).alpha(0f).setDuration(sure).setInterpolator(yumusak)
                .withEndAction { kok.removeView(perde); eski.recycle() }.start()
        }
        for (id in icerikKimlikleri) {
            val v = findViewById<View>(id)
            if (v.visibility != View.VISIBLE) continue
            v.animate().cancel()
            v.translationX = mesafe
            v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(sure).setInterpolator(yumusak).start()
        }
        // Başlık ve şeritler de yumuşakça belirir.
        for (id in intArrayOf(R.id.baslik, R.id.altBaslik, R.id.klasorKaydirici, R.id.tatilSeridi)) {
            val v = findViewById<View>(id)
            if (v.visibility != View.VISIBLE) continue
            v.animate().cancel()
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(220).start()
        }
    }

    override fun onSaveInstanceState(durum: Bundle) {
        super.onSaveInstanceState(durum)
        durum.putInt("sekme", sekme)
        durum.putString("klasor", seciliKlasor)
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
        klasorSeridiniYaz()
        findViewById<View>(R.id.btnTatil).visibility = if (sekme == SEKME_ALARM) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnSaatModu).visibility = if (sekme == SEKME_DUNYA) View.VISIBLE else View.GONE
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
        val gorunen = seciliKlasor?.let { k -> alarmlar.filter { it.klasor == k } } ?: alarmlar
        if (gorunen.isEmpty()) {
            val klasorde = seciliKlasor != null
            BosDurum.goster(
                bosDurum, R.drawable.ic_alarm,
                getString(if (klasorde) R.string.bos_klasor_baslik else R.string.bos_alarm_baslik),
                getString(if (klasorde) R.string.bos_klasor_aciklama else R.string.bos_alarm_aciklama),
                getString(R.string.alarm_ekle) to { yeniAlarm() }
            )
            return
        }
        bosDurum.visibility = View.GONE
        val simdi = System.currentTimeMillis()
        for (a in gorunen) liste.addView(kart(a, simdi))
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
            gunCipleri(gunler, alarm, acik)
            if (alarm.aralikli) {
                // Süzgeçli aralık: gün çiplerinin altında tarih aralığı da yazılır.
                ayrinti.visibility = View.VISIBLE
                ayrinti.text = aralikMetni(alarm)
            } else {
                ayrinti.visibility = View.GONE
            }
        } else {
            gunler.visibility = View.GONE
            ayrinti.text = if (alarm.tarih != 0) {
                ayrinti.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_takvim, 0, 0, 0)
                // compoundDrawableTintList Android 6'da geldi; uyumlu yol:
                TextViewCompat.setCompoundDrawableTintList(ayrinti, ColorStateList.valueOf(soluk))
                if (alarm.aralikli) aralikMetni(alarm)
                else "${Metinler.tarih(alarm.tarih)} · ${getString(R.string.bir_kez)}"
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
        // Sola kaydır: sil; sağa kaydır: klasöre taşı. Uzun basma seçenekleri de duruyor.
        val sarmal = KaydirmaSatiri(this)
        sarmal.icerik(v)
        sarmal.sola = KaydirmaSatiri.silme(this, getString(R.string.sil)) { alarmiSil(alarm) }
        sarmal.saga = KaydirmaSatiri.klasor(this, getString(R.string.klasore_tasi)) { klasorSec(alarm) }
        sarmal.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
        return sarmal
    }

    private fun yeniAlarm() {
        startActivity(
            Intent(this, DuzenleActivity::class.java).apply {
                seciliKlasor?.let { putExtra(DuzenleActivity.EK_KLASOR, it) }
            }
        )
    }

    /** Silme (kaydırarak ya da menüden): hemen silinir, beş saniye "Geri al" şeridi kalır. */
    private fun alarmiSil(alarm: Alarm) {
        Depo.sil(this, alarm.id)
        AlarmKurucu.iptal(this, alarm.id)
        Bildirimler.ertelemeyiKaldir(this, alarm.id)
        yenile()
        GeriAl.goster(this, getString(R.string.alarm_silindi)) {
            Depo.yaz(this, alarm)
            AlarmKurucu.hepsiniKur(this)
            yenile()
        }
    }

    /** Alarmı klasöre taşı / klasörden çıkar / yeni klasör aç. */
    private fun klasorSec(alarm: Alarm) {
        val sayfa = AltSayfa(this).baslik(getString(R.string.klasore_tasi))
        fun tasi(ad: String) {
            Depo.yaz(this, (Depo.alarm(this, alarm.id) ?: alarm).copy(klasor = ad))
            yenile()
        }
        sayfa.madde(TR.drawable.ic_kapat, getString(R.string.klasorsuz), secili = alarm.klasor.isEmpty()) { tasi("") }
        for (k in Depo.klasorler(this)) {
            sayfa.madde(R.drawable.ic_klasor, k, secili = alarm.klasor == k) { tasi(k) }
        }
        sayfa.madde(R.drawable.ic_arti, getString(R.string.yeni_klasor)) {
            yeniKlasorSor { ad -> tasi(ad) }
        }
        sayfa.goster()
    }

    private fun yeniKlasorSor(tamam: (String) -> Unit) {
        AltSayfa(this).baslik(getString(R.string.yeni_klasor))
            .girdi(getString(R.string.klasor_ipucu), "", getString(R.string.tamam)) { ad ->
                val temiz = ad.take(24)
                if (temiz !in Depo.klasorler(this)) Depo.klasorleriKaydet(this, Depo.klasorler(this) + temiz)
                tamam(temiz)
            }
            .goster()
    }

    /** Alarm sekmesinin üstündeki klasör çipleri: Tümü, klasörler, yeni klasör. */
    private fun klasorSeridiniYaz() {
        val kaydirici = findViewById<View>(R.id.klasorKaydirici)
        val kutu = findViewById<LinearLayout>(R.id.klasorKutusu)
        val klasorler = Depo.klasorler(this)
        val alarmVar = Depo.alarmlar(this).any { it.id != DENEME_ID }
        if (sekme != SEKME_ALARM || (!alarmVar && klasorler.isEmpty())) {
            kaydirici.visibility = View.GONE
            return
        }
        // Silinen klasör seçili kaldıysa Tümü'ne dön.
        if (seciliKlasor != null && seciliKlasor !in klasorler) seciliKlasor = null
        kaydirici.visibility = View.VISIBLE
        kutu.removeAllViews()
        val d = resources.displayMetrics.density
        val metin = ContextCompat.getColor(this, TR.color.metin)
        val pastel = Tasarim.pastel(vurgu)
        fun cip(ad: String, secili: Boolean, ikon: Boolean = false): TextView = TextView(this).apply {
            text = ad
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (secili) vurgu else metin)
            setTypeface(null, if (secili) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_chip_hedef)
            backgroundTintList = ColorStateList.valueOf(if (secili) pastel else ContextCompat.getColor(this@MainActivity, TR.color.kart))
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (48 * d).toInt()).apply {
                rightMargin = (8 * d).toInt()
            }
        }
        kutu.addView(cip(getString(R.string.tum_alarmlar), seciliKlasor == null).apply {
            setOnClickListener { seciliKlasor = null; yenile() }
        })
        for (k in klasorler) {
            kutu.addView(cip(k, seciliKlasor == k).apply {
                setOnClickListener { seciliKlasor = k; yenile() }
                setOnLongClickListener { klasorSecenekleri(k); true }
            })
        }
        kutu.addView(cip("＋ " + getString(R.string.klasor), false).apply {
            setTextColor(vurgu)
            setOnClickListener { yeniKlasorSor { ad -> seciliKlasor = ad; yenile() } }
        })
    }

    private fun klasorSecenekleri(ad: String) {
        AltSayfa(this).baslik(ad)
            .madde(R.drawable.ic_cogalt, getString(R.string.yeniden_adlandir)) {
                AltSayfa(this).baslik(getString(R.string.yeniden_adlandir))
                    .girdi(getString(R.string.klasor_ipucu), ad, getString(R.string.tamam)) { yeni ->
                        Depo.klasorDegistir(this, ad, yeni.take(24))
                        if (seciliKlasor == ad) seciliKlasor = yeni.take(24)
                        yenile()
                    }.goster()
            }
            .madde(R.drawable.ic_sil, getString(R.string.klasoru_sil), tehlikeli = true) {
                Depo.klasorDegistir(this, ad, "")
                yenile()
            }
            .goster()
    }

    private fun aralikMetni(alarm: Alarm): String =
        "${Metinler.kisaTarih(alarm.tarih)} – ${Metinler.kisaTarih(alarm.tarihBitis)}"

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
        sayfa.madde(R.drawable.ic_klasor, getString(R.string.klasore_tasi)) { klasorSec(alarm) }
        sayfa.madde(R.drawable.ic_sil, getString(R.string.sil), tehlikeli = true) { alarmiSil(alarm) }
        sayfa.goster()
    }

    private class Hap(val no: Int, val kutu: LinearLayout, val ikon: ImageView, val yazi: TextView)

    private val haplar = ArrayList<Hap>()

    private fun sekmeleriKur() {
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        if (haplar.isEmpty()) {
            val d = resources.displayMetrics.density
            val sekmeler = listOf(
                Triple(SEKME_ALARM, R.drawable.ic_alarm, R.string.alarm),
                Triple(SEKME_DUNYA, R.drawable.ic_dunya, R.string.dunya_saati),
                Triple(SEKME_ZAMANLAYICI, R.drawable.ic_zamanlayici, R.string.zamanlayici),
                Triple(SEKME_KRONOMETRE, R.drawable.ic_kronometre, R.string.kronometre)
            )
            for ((no, ikon, ad) in sekmeler) {
                val kapsayici = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    contentDescription = getString(ad)
                    setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                    setBackgroundResource(R.drawable.bg_sekme)
                    setOnClickListener { sekmeyeGec(no, if (no > sekme) 1 else -1) }
                }
                val simge = ImageView(this).apply {
                    setImageResource(ikon)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                kapsayici.addView(simge, LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
                val yazi = TextView(this).apply {
                    text = getString(ad)
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(vurgu)
                    maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                kapsayici.addView(yazi, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = (6 * d).toInt() })
                kutu.addView(kapsayici, LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f))
                haplar.add(Hap(no, kapsayici, simge, yazi))
                ipucuVer(kapsayici)
            }
        }
        sekmeDurumunuGuncelle(animasyonlu = false)
    }

    /**
     * Seçili sekme hap olur (simge + ad), diğerleri yalnız simge. Yer değişimi,
     * hap rengi ve simge renkleri [animasyonlu] ise yumuşak geçişle olur.
     */
    private fun sekmeDurumunuGuncelle(animasyonlu: Boolean) {
        geriSekme.isEnabled = sekme != SEKME_ALARM
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val d = resources.displayMetrics.density
        val pasif = ContextCompat.getColor(this, TR.color.metin_ikincil)
        val pastel = Tasarim.pastel(vurgu)
        if (animasyonlu) {
            val gecis = TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(Fade())
                duration = 260
                interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            }
            TransitionManager.beginDelayedTransition(kutu, gecis)
        }
        for (h in haplar) {
            val secili = h.no == sekme
            h.kutu.isSelected = secili
            h.yazi.visibility = if (secili) View.VISIBLE else View.GONE
            h.kutu.layoutParams = if (secili) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (44 * d).toInt())
            } else {
                LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f)
            }
            val hedefHap = if (secili) pastel else (pastel and 0x00FFFFFF)
            val hedefSimge = if (secili) vurgu else pasif
            val eskiHap = (h.kutu.backgroundTintList?.defaultColor) ?: (pastel and 0x00FFFFFF)
            val eskiSimge = h.ikon.imageTintList?.defaultColor ?: hedefSimge
            if (animasyonlu) {
                android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), eskiHap, hedefHap).apply {
                    duration = 260
                    addUpdateListener { h.kutu.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                    start()
                }
                android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), eskiSimge, hedefSimge).apply {
                    duration = 260
                    addUpdateListener { h.ikon.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                    start()
                }
            } else {
                h.kutu.backgroundTintList = ColorStateList.valueOf(hedefHap)
                h.ikon.imageTintList = ColorStateList.valueOf(hedefSimge)
            }
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
