package com.ekosistem.takvim

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.BosDurum
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.ipucuVer
import java.util.TimeZone
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/**
 * Ana ekran: Ay (ve üst katı Yıl), Hafta (7 ya da 3 gün), Gün (ızgara ya da
 * liste) ve Gündem görünümleri. Etkinlikler telefonun
 * takvim deposundan okunur; depo değişince (senkron, başka uygulama) ekran
 * kendiliğinden yenilenir.
 */
class MainActivity : AppCompatActivity() {

    private var mod = Depo.GORUNUM_AY
    /** Seçili gün; ay görünümünde ay da buradan, hafta/gün görünümünde dönem de buradan gelir. */
    private var ref = 0
    private var bugun = 0
    private var vurgu = 0

    private var ornekler: List<Ornek> = emptyList()
    private var takvimler: List<Takvim> = emptyList()
    private var yuklenen: Pair<Int, Int>? = null
    private var yuklendi = false
    private var gundemGunSayisi = GUNDEM_ILK
    private var gundemYukleniyor = false
    private var bekleyenDavet = 0
    private var zamanKaydirmaGerekli = true

    private val yurutucu = Executors.newSingleThreadExecutor()
    private var nesil = 0
    private val isleyici = Handler(Looper.getMainLooper())

    private lateinit var baslik: TextView
    private lateinit var altBaslik: TextView
    private lateinit var btnBugun: TextView
    private lateinit var btnOnceki: View
    private lateinit var btnSonraki: View
    private lateinit var icerikAy: ScrollView
    private lateinit var icerikZaman: View
    private lateinit var icerikGundem: ScrollView
    private lateinit var bosDurum: View
    private lateinit var ayIzgara: AyIzgarasi
    private lateinit var ayGunAdlari: LinearLayout
    private lateinit var ayGunBasligi: TextView
    private lateinit var ayListe: LinearLayout
    private lateinit var haftaBasligi: HaftaBasligi
    private lateinit var tumGunSeridi: TumGunSeridi
    private lateinit var zamanKaydirici: ScrollView
    private lateinit var zamanIzgara: ZamanIzgarasi
    private lateinit var gundemListe: LinearLayout
    private lateinit var btnGorunum: View
    private lateinit var icerikYil: ScrollView
    private lateinit var yilIzgara: YilIzgarasi
    private lateinit var icerikGunListe: ScrollView
    private lateinit var gunListeKutusu: LinearLayout

    private val yenileIs = Runnable { yenile() }

    private val gozlemci = object : ContentObserver(isleyici) {
        override fun onChange(selfChange: Boolean) {
            isleyici.removeCallbacks(yenileIs)
            isleyici.postDelayed(yenileIs, 300)
        }
    }

    /** Gece yarısı: bugün değişir, ekran (bugün halkası, şimdi çizgisi) kendiliğinden yenilenir. */
    private val geceYarisi = object : Runnable {
        override fun run() {
            gunDegisti()
            geceYarisiniKur()
        }
    }

    private val saatDegisti = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = gunDegisti()
    }

    private val kaydirmaAlgilayici by lazy {
        val d = resources.displayMetrics.density
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || mod == Depo.GORUNUM_GUNDEM || cokParmak || zamanIzgara.surukleniyor) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) < 80 * d || kotlin.math.abs(dx) < 2 * kotlin.math.abs(dy)) return false
                if (kotlin.math.abs(vx) < 500 * d) return false
                git(if (dx < 0) 1 else -1)
                return true
            }
        })
    }

    /** Dokunuşta ikinci parmak indi mi: sıkıştırma yatay kaydırma (sayfa değiştirme) sayılmasın. */
    private var cokParmak = false

    /**
     * Ay ve Yıl'da iki parmakla sıkıştırma: açınca daha ayrıntılı (Yıl → Ay,
     * Kompakt → Yığılı → Ayrıntılı), kapatınca daha toplu. Aynı seçenekler
     * Görünüm düğmesinde de var (gizli jest tek yol olmasın).
     */
    private val ayOlcekAlgilayici by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var toplam = 1f
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                toplam = 1f
                return mod == Depo.GORUNUM_AY || mod == Depo.GORUNUM_YIL
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                toplam *= detector.scaleFactor
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                when {
                    toplam > 1.25f -> yogunluguDegistir(1)
                    toplam < 0.8f -> yogunluguDegistir(-1)
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vurgu = Tasarim.vurgu(this)
        bugun = Gun.bugun(System.currentTimeMillis(), TimeZone.getDefault())

        baslik = findViewById(R.id.baslik)
        altBaslik = findViewById(R.id.altBaslik)
        btnBugun = findViewById(R.id.btnBugun)
        btnOnceki = findViewById(R.id.btnOnceki)
        btnSonraki = findViewById(R.id.btnSonraki)
        icerikAy = findViewById(R.id.icerikAy)
        icerikZaman = findViewById(R.id.icerikZaman)
        icerikGundem = findViewById(R.id.icerikGundem)
        bosDurum = findViewById(R.id.bosDurum)
        ayIzgara = findViewById(R.id.ayIzgara)
        ayGunAdlari = findViewById(R.id.ayGunAdlari)
        ayGunBasligi = findViewById(R.id.ayGunBasligi)
        ayListe = findViewById(R.id.ayListe)
        haftaBasligi = findViewById(R.id.haftaBasligi)
        tumGunSeridi = findViewById(R.id.tumGunSeridi)
        zamanKaydirici = findViewById(R.id.zamanKaydirici)
        zamanIzgara = findViewById(R.id.zamanIzgara)
        gundemListe = findViewById(R.id.gundemListe)
        btnGorunum = findViewById(R.id.btnGorunum)
        icerikYil = findViewById(R.id.icerikYil)
        yilIzgara = findViewById(R.id.yilIzgara)
        icerikGunListe = findViewById(R.id.icerikGunListe)
        gunListeKutusu = findViewById(R.id.gunListeKutusu)

        mod = savedInstanceState?.getInt("mod") ?: Depo.baslangicGorunumu(this)
        ref = savedInstanceState?.getInt("ref") ?: gidilecekGun() ?: bugun
        if (savedInstanceState == null) kisayoluIsle(intent, false)

        findViewById<ImageButton>(R.id.btnYeni).apply {
            imageTintList = ColorStateList.valueOf(Tasarim.vurguUzeri(this@MainActivity))
            setOnClickListener { yeniEtkinlik() }
        }
        findViewById<View>(R.id.btnAyarlar).setOnClickListener { startActivity(Intent(this, AyarlarActivity::class.java)) }
        btnBugun.setOnClickListener { bugunuGoster() }
        findViewById<View>(R.id.btnAra).setOnClickListener { startActivity(Intent(this, AramaActivity::class.java)) }
        // Dar ekranda ve büyük yazıda "Ekim 2026" üç düğmenin yanında kesilmesin: yazı küçülür.
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            baslik, 20, (resources.getDimension(R.dimen.baslik_en_buyuk) / resources.displayMetrics.scaledDensity).toInt(), 1,
            android.util.TypedValue.COMPLEX_UNIT_SP
        )
        btnGorunum.setOnClickListener { gorunumuDegistir() }
        // Apple Takvim'deki gibi: ay başlığına dokununca yıl açılır (aynı yol Görünüm düğmesinde de var).
        baslik.setOnClickListener { if (mod == Depo.GORUNUM_AY) modDegistir(Depo.GORUNUM_YIL) }
        btnOnceki.setOnClickListener { git(-1) }
        btnSonraki.setOnClickListener { git(1) }
        findViewById<View>(R.id.uyariSeridi).setOnClickListener { bildirimIzniIste() }
        ipucuVer(findViewById(R.id.btnYeni), findViewById(R.id.btnAyarlar), findViewById(R.id.btnAra), btnBugun, btnGorunum, btnOnceki, btnSonraki)

        ayIzgara.gunSecildi = { gunSec(it) }
        haftaBasligi.gunSecildi = { g -> ref = g; modDegistir(Depo.GORUNUM_GUN) }
        tumGunSeridi.ornekTiklandi = { ornekAc(it) }
        zamanIzgara.ornekTiklandi = { ornekAc(it) }
        zamanIzgara.bosUzunBasildi = { g, dk -> yeniEtkinlik(g, dk) }
        zamanIzgara.tasinabilir = { o -> tasinabilir(o) }
        zamanIzgara.tasindi = { o, g, bas, bit -> ornekTasindi(o, g, bas, bit) }
        zamanIzgara.saatCarpani = Depo.saatOlcegi(this)
        zamanIzgara.olcekDegisti = { Depo.saatOlcegiKaydet(this, it) }
        yilIzgara.aySecildi = { ay -> aydanAyaGit(ay) }
        // Ayrıntılı ayda ızgara ekranın boyunu doldurur: kapsayıcının yüksekliği değişince (döndürme) yeniden hesaplanır.
        icerikAy.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) ayIzgara.hedefYukseklik = ayrintiliYukseklik()
        }
        icerikGundem.viewTreeObserver.addOnScrollChangedListener { gundemKaydirildi() }

        sekmeleriKur()
        icerikleriAyarla()
        ustBilgiyiYaz()
    }

    /** `content://com.android.calendar/time/<ms>` çağrısı verilen güne gider. */
    private fun gidilecekGun(): Int? {
        val uri = intent?.data ?: return null
        if (uri.host != "com.android.calendar" || uri.pathSegments.firstOrNull() != "time") return null
        val ms = uri.lastPathSegment?.toLongOrNull() ?: return null
        return Gun.yerelGun(ms, TimeZone.getDefault())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("mod", mod)
        outState.putInt("ref", ref)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        gidilecekGun()?.let { ref = it; yeniGorunum() }
        kisayoluIsle(intent, true)
    }

    /** Simge kısayolları: "Bugün" bugüne, "Gündem" gündem görünümüne götürür. */
    private fun kisayoluIsle(i: Intent?, uygula: Boolean) {
        when (i?.action) {
            "com.ekosistem.takvim.BUGUN" -> { ref = bugun; mod = Depo.baslangicGorunumu(this) }
            "com.ekosistem.takvim.GUNDEM" -> { ref = bugun; mod = Depo.GORUNUM_GUNDEM }
            else -> return
        }
        zamanKaydirmaGerekli = true
        if (uygula) yeniGorunum()
    }

    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, gozlemci)
        } catch (_: RuntimeException) {
        }
        registerReceiver(saatDegisti, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        })
        geceYarisiniKur()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(gozlemci)
        unregisterReceiver(saatDegisti)
        isleyici.removeCallbacks(geceYarisi)
        isleyici.removeCallbacks(yenileIs)
        if (gosterilenGorunum != null) {
            isleyici.removeCallbacks(altBaslikGeriYaz)
            altBaslikGeriYaz.run()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        bugun = Gun.bugun(System.currentTimeMillis(), TimeZone.getDefault())
        ayGunAdlariniKur()
        zamanIzgara.saatCarpani = Depo.saatOlcegi(this)
        yenile()
        uyariyiYaz()
        geriAlGoster()
    }

    /** Ayrıntı ekranında etkinlik silindiyse kısa süre "Silindi · Geri al" şeridi çıkar. */
    private fun geriAlGoster() {
        val k = GeriAlDeposu.al() ?: return
        GeriAl.goster(this, getString(R.string.silindi)) {
            yurutucu.execute {
                val tamam = TakvimDeposu.geriAl(this, k)
                runOnUiThread {
                    if (!tamam) android.widget.Toast.makeText(this, R.string.geri_alinamadi, android.widget.Toast.LENGTH_LONG).show()
                    yenile()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> cokParmak = false
            MotionEvent.ACTION_POINTER_DOWN -> cokParmak = true
        }
        ayOlcekAlgilayici.onTouchEvent(ev)
        kaydirmaAlgilayici.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun geceYarisiniKur() {
        isleyici.removeCallbacks(geceYarisi)
        val tz = TimeZone.getDefault()
        val simdi = System.currentTimeMillis()
        val sonraki = Gun.yerelAn(Gun.yerelGun(simdi, tz) + 1, 0, tz)
        isleyici.postDelayed(geceYarisi, maxOf(1000L, sonraki - simdi + 1000L))
    }

    private fun gunDegisti() {
        val eskiBugun = bugun
        bugun = Gun.bugun(System.currentTimeMillis(), TimeZone.getDefault())
        // Bugünü izleyen kullanıcı yeni güne geçer; başka bir güne bakıyorsa yeri korunur.
        if (ref == eskiBugun) ref = bugun
        yenile()
    }

    // ---- İzin ve durum ----

    private fun izinKontrol(): Boolean = TakvimDeposu.izinVar(this)

    private fun izinIste() {
        val liste = arrayListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33 && !Depo.bildirimIstendi(this)) {
            liste.add(Manifest.permission.POST_NOTIFICATIONS)
            Depo.bildirimIstendiKaydet(this)
        }
        Depo.izinIstendiKaydet(this)
        ActivityCompat.requestPermissions(this, liste.toTypedArray(), IZIN_TAKVIM)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        yenile()
        uyariyiYaz()
    }

    private fun bildirimIzniIste() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), IZIN_BILDIRIM)
            return
        }
        Bildirimler.kanalKur(this)
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(i) }
    }

    private fun uyariyiYaz() {
        val kapali = izinKontrol() && !NotificationManagerCompat.from(this).areNotificationsEnabled()
        findViewById<View>(R.id.uyariSeridi).visibility = if (kapali) View.VISIBLE else View.GONE
    }

    // ---- Veri ----

    private fun ayBasiGunu() = Gun.gun(Gun.yil(ref), Gun.ay(ref), 1)

    private fun ayIlkGunu() = Gun.haftaBasi(ayBasiGunu(), Depo.haftaBasi(this))

    private fun aySatirSayisi(): Int {
        val ofset = ayBasiGunu() - ayIlkGunu()
        return (ofset + Gun.ayinGunSayisi(Gun.yil(ref), Gun.ay(ref)) + 6) / 7
    }

    private fun aralik(): Pair<Int, Int> = when (mod) {
        Depo.GORUNUM_AY -> ayIlkGunu().let { it to it + aySatirSayisi() * 7 - 1 }
        Depo.GORUNUM_YIL -> Gun.gun(Gun.yil(ref), 1, 1) to Gun.gun(Gun.yil(ref), 12, 31)
        // 3 günlük görünüm seçili günden başlar (Apple'ın çok günlü görünümü gibi); 7 gün haftanın başından.
        Depo.GORUNUM_HAFTA -> if (Depo.haftaGunSayisi(this) == 3) ref to ref + 2
            else Gun.haftaBasi(ref, Depo.haftaBasi(this)).let { it to it + 6 }
        Depo.GORUNUM_GUN -> ref to ref
        else -> ref to ref + gundemGunSayisi - 1
    }

    /**
     * Görünüm ya da gün değişti: yüklü veri yeni aralığı kapsıyorsa (ör. Yıl'dan bir aya
     * inince) yalnızca çiz, değilse depoyu oku. Çizimler aralık dışını kendileri süzer.
     */
    private fun yeniGorunum() {
        icerikleriAyarla()
        ustBilgiyiYaz()
        val (ilk, son) = aralik()
        val kapsar = yuklenen?.let { it.first <= ilk && it.second >= son } == true
        if (izinKontrol() && yuklendi && kapsar) goster() else yenile()
    }

    private fun yenile() {
        bugun = Gun.bugun(System.currentTimeMillis(), TimeZone.getDefault())
        ustBilgiyiYaz()
        if (!izinKontrol()) {
            ornekler = emptyList()
            takvimler = emptyList()
            yuklendi = false
            yuklenen = null
            izinEkrani()
            return
        }
        val (ilk, son) = aralik()
        val n = ++nesil
        yurutucu.execute {
            val t = Hiz.olc("depo.takvimler") { TakvimDeposu.takvimler(this) }
            val o = Hiz.olc("depo.ornekler(${son - ilk + 1} gün)") { TakvimDeposu.ornekler(this, ilk, son) }
            val davet = if (mod == Depo.GORUNUM_GUNDEM) TakvimDeposu.bekleyenDavetSayisi(this, bugun, bugun + 365) else bekleyenDavet
            runOnUiThread {
                if (n == nesil && !isDestroyed) {
                    takvimler = t
                    ornekler = o
                    bekleyenDavet = davet
                    yuklenen = ilk to son
                    yuklendi = true
                    gundemYukleniyor = false
                    goster()
                }
            }
        }
    }

    // ---- Çizim ----

    private fun icerikleriAyarla() {
        val gunListesi = mod == Depo.GORUNUM_GUN && Depo.gunListe(this)
        icerikAy.visibility = if (mod == Depo.GORUNUM_AY) View.VISIBLE else View.GONE
        icerikYil.visibility = if (mod == Depo.GORUNUM_YIL) View.VISIBLE else View.GONE
        icerikZaman.visibility = if ((mod == Depo.GORUNUM_HAFTA || mod == Depo.GORUNUM_GUN) && !gunListesi) View.VISIBLE else View.GONE
        icerikGunListe.visibility = if (gunListesi) View.VISIBLE else View.GONE
        icerikGundem.visibility = if (mod == Depo.GORUNUM_GUNDEM) View.VISIBLE else View.GONE
        val oklar = if (mod == Depo.GORUNUM_GUNDEM) View.INVISIBLE else View.VISIBLE
        btnOnceki.visibility = oklar
        btnSonraki.visibility = oklar
        btnGorunum.visibility = if (mod == Depo.GORUNUM_GUNDEM) View.GONE else View.VISIBLE
        gorunumDugmesiniYaz()
        baslik.isClickable = mod == Depo.GORUNUM_AY
        if (mod == Depo.GORUNUM_AY) {
            androidx.core.view.ViewCompat.replaceAccessibilityAction(
                baslik, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.yil_gorunumune_gec), null
            )
        } else {
            androidx.core.view.ViewCompat.removeAccessibilityAction(baslik, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
        sekmeDurumunuGuncelle(false)
    }

    private fun ustBilgiyiYaz() = Hiz.olc("ustBilgi") {
        ustBilgiyiYazIc()
        gosterilenGorunum?.let { altBaslik.text = it }
    }

    private fun ustBilgiyiYazIc() {
        btnBugun.text = Gun.ayinGunu(bugun).toString()
        val ilkSon = aralik()
        when (mod) {
            Depo.GORUNUM_AY -> {
                baslik.text = Metinler.ayYil(ref)
                val ay = Gun.ay(ref)
                val yil = Gun.yil(ref)
                val sayi = ornekler.filter { o -> (o.ilkGun..o.sonGun).any { Gun.ay(it) == ay && Gun.yil(it) == yil } }.size
                altBaslik.text = if (!yuklendi || takvimler.isEmpty()) "" else if (sayi == 0) getString(R.string.bu_ay_yok) else getString(R.string.bu_ay_n, sayi)
            }
            Depo.GORUNUM_YIL -> {
                baslik.text = Gun.yil(ref).toString()
                altBaslik.text = if (!yuklendi || takvimler.isEmpty() || yuklenen != ilkSon) ""
                    else if (ornekler.isEmpty()) getString(R.string.bu_yil_yok) else getString(R.string.yil_etkinlik_n, ornekler.size)
            }
            Depo.GORUNUM_HAFTA -> if (ilkSon.second - ilkSon.first < 6) {
                baslik.text = Metinler.ayYil(ilkSon.first + 1)
                altBaslik.text = Metinler.gunAyKisa(ilkSon.first) + " – " + Metinler.gunAyKisa(ilkSon.second)
            } else {
                baslik.text = Metinler.ayYil(ilkSon.first + 3)
                altBaslik.text = getString(
                    R.string.hafta_aralik, Gun.isoHafta(ilkSon.first + Math.floorMod(-Depo.haftaBasi(this), 7)),
                    Metinler.gunAyKisa(ilkSon.first), Metinler.gunAyKisa(ilkSon.second)
                )
            }
            Depo.GORUNUM_GUN -> {
                baslik.text = Metinler.gunAy(ref)
                val gun = Metinler.haftaGunuUzun(Gun.haftaGunu(ref))
                altBaslik.text = if (ref == bugun) getString(R.string.gun_bugun, gun) else if (Gun.yil(ref) != Gun.yil(bugun)) "$gun · ${Gun.yil(ref)}" else gun
            }
            else -> {
                baslik.text = getString(R.string.gorunum_gundem)
                altBaslik.text = if (ref == bugun) getString(R.string.yaklasan) else getString(R.string.gundem_itibariyle, Metinler.tamTarih(ref))
            }
        }
    }

    private fun icerikleriGizle() {
        for (v in listOf(icerikAy, icerikYil, icerikZaman, icerikGunListe, icerikGundem)) v.visibility = View.GONE
    }

    private fun bosDurumGizle() {
        bosDurum.visibility = View.GONE
    }

    private fun izinEkrani() {
        icerikleriGizle()
        val ayardan = Depo.izinIstendi(this) && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CALENDAR)
        BosDurum.goster(
            bosDurum, R.drawable.ic_takvim, getString(R.string.izin_baslik), getString(R.string.izin_aciklama),
            if (ayardan) getString(R.string.izin_ayarlar) to {
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
                Unit
            }
            else getString(R.string.izin_ver) to { izinIste() }
        )
    }

    private fun goster() {
        if (!izinKontrol()) return izinEkrani()
        ustBilgiyiYaz()
        if (takvimler.isEmpty()) {
            icerikleriGizle()
            BosDurum.goster(
                bosDurum, R.drawable.ic_takvim, getString(R.string.takvim_yok_baslik), getString(R.string.takvim_yok_aciklama),
                getString(R.string.takvim_olustur) to { yerelTakvimOlustur { yenile() } }
            )
            return
        }
        bosDurumGizle()
        Hiz.olc("goster mod=$mod (${ornekler.size} örnek)") {
            icerikleriAyarla()
            when (mod) {
                Depo.GORUNUM_AY -> ayiCiz()
                Depo.GORUNUM_YIL -> yiliCiz()
                Depo.GORUNUM_GUN -> if (Depo.gunListe(this)) gunListesiniCiz() else zamaniCiz()
                Depo.GORUNUM_HAFTA -> zamaniCiz()
                else -> gundemiCiz()
            }
        }
    }

    private fun gunlereBol(): Map<Int, List<Ornek>> {
        val harita = HashMap<Int, ArrayList<Ornek>>()
        val (ilk, son) = aralik()
        for (o in ornekler) {
            for (g in maxOf(o.ilkGun, ilk)..minOf(o.sonGun, son)) harita.getOrPut(g) { ArrayList() }.add(o)
        }
        return harita
    }

    private fun gunSirasi(a: Ornek, b: Ornek): Int = when {
        a.tumGun != b.tumGun -> if (a.tumGun) -1 else 1
        a.baslangic != b.baslangic -> a.baslangic.compareTo(b.baslangic)
        else -> a.baslik.compareTo(b.baslik)
    }

    // ---- Ay ----

    private fun ayGunAdlariniKur() {
        ayGunAdlari.removeAllViews()
        val d = resources.displayMetrics.density
        val haftaBasi = Depo.haftaBasi(this)
        if (Depo.haftaNumaralari(this)) {
            ayGunAdlari.addView(View(this), LinearLayout.LayoutParams((30 * d).toInt(), 1))
        }
        for (i in 0 until 7) {
            val t = TextView(this)
            t.text = Metinler.haftaGunuKisa((haftaBasi + i) % 7)
            t.gravity = Gravity.CENTER
            t.textSize = 12f
            t.setTypeface(null, android.graphics.Typeface.BOLD)
            t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
            t.maxLines = 1
            ayGunAdlari.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun ayiCiz() {
        val gunler = Hiz.olc("ay.gunlereBol") { gunlereBol() }
        val ozetler = HashMap<Int, GunOzeti>()
        Hiz.olc("ay.ozetler") {
            for ((g, liste) in gunler) {
                val sirali = liste.sortedWith(::gunSirasi)
                val renkler = sirali.map { Renk.yuzey(this, it.renk) }
                ozetler[g] = GunOzeti(
                    renkler.distinct().take(3).toIntArray(), liste.size,
                    if (yogunluk == Depo.AY_KOMPAKT) emptyList()
                    else sirali.mapIndexed { i, o -> GunEtkinligi(renkler[i], o.baslik.ifBlank { getString(R.string.basliksiz) }) }
                )
            }
        }
        val ayrintili = yogunluk == Depo.AY_AYRINTILI
        ayGunBasligi.visibility = if (ayrintili) View.GONE else View.VISIBLE
        ayListe.visibility = if (ayrintili) View.GONE else View.VISIBLE
        Hiz.olc("ay.izgara") {
            ayIzgara.yogunluk = yogunluk
            ayIzgara.hedefYukseklik = ayrintiliYukseklik()
            ayIzgara.haftaBasi = Depo.haftaBasi(this)
            ayIzgara.haftaNumaralari = Depo.haftaNumaralari(this)
            ayIzgara.ilkGun = ayIlkGunu()
            ayIzgara.satirSayisi = aySatirSayisi()
            ayIzgara.ay = Gun.ay(ref)
            ayIzgara.bugun = bugun
            ayIzgara.secili = ref
            ayIzgara.gunler = ozetler
        }

        if (!ayrintili) Hiz.olc("ay.liste") { ayListeyiYaz(gunler) }
    }

    private val yogunluk get() = Depo.ayYogunlugu(this)

    /** Ayrıntılı ayda ızgaranın yüksekliği: gün adlarının altından yüzen çubuğun üstüne kadar. */
    private fun ayrintiliYukseklik(): Int {
        val h = icerikAy.height
        if (h == 0) return 0
        return h - icerikAy.paddingBottom - ayGunAdlari.bottom - (4 * resources.displayMetrics.density).toInt()
    }

    /** Ay sekmesinin katları, toplu → ayrıntılı: Yıl, Kompakt, Ayrıntılı. */
    private fun ayKati(): Int = when {
        mod == Depo.GORUNUM_YIL -> 0
        yogunluk == Depo.AY_AYRINTILI -> 2
        else -> 1
    }

    private fun ayKatinaGec(kat: Int) {
        android.transition.TransitionManager.beginDelayedTransition(findViewById(R.id.icerik), ChangeBounds().setDuration(200))
        if (kat == 0) {
            modDegistir(Depo.GORUNUM_YIL)
        } else {
            Depo.ayYogunluguKaydet(this, if (kat == 2) Depo.AY_AYRINTILI else Depo.AY_KOMPAKT)
            if (mod == Depo.GORUNUM_YIL) modDegistir(Depo.GORUNUM_AY) else goster()
        }
        gorunumDugmesiniYaz()
    }

    /** Sıkıştırma: +1 daha ayrıntılı, -1 daha toplu (uçlarda durur). */
    private fun yogunluguDegistir(yon: Int) {
        val su = ayKati()
        val yeni = (su + yon).coerceIn(0, 2)
        if (yeni == su) return
        ayKatinaGec(yeni)
        icerikAy.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    // ---- Yıl ----

    private fun yiliCiz() {
        val sayilar = HashMap<Int, Int>()
        Hiz.olc("yil.sayilar(${ornekler.size})") {
            for ((g, liste) in gunlereBol()) sayilar[g] = liste.size
        }
        yilIzgara.yil = Gun.yil(ref)
        yilIzgara.haftaBasi = Depo.haftaBasi(this)
        yilIzgara.bugun = bugun
        yilIzgara.isiHaritasi = Depo.yilIsiHaritasi(this)
        yilIzgara.sayilar = sayilar
    }

    /** Yıl görünümünde bir aya dokunuldu: o ay açılır; bu aysa bugün, değilse ayın 1'i seçili. */
    private fun aydanAyaGit(ay: Int) {
        val yil = Gun.yil(ref)
        ref = if (Gun.yil(bugun) == yil && Gun.ay(bugun) == ay) bugun else Gun.gun(yil, ay, 1)
        modDegistir(Depo.GORUNUM_AY)
    }

    // ---- Gün (liste) ----

    /** Günün etkinlikleri kart olarak; saatli etkinlikler arasında yarım saatten uzun boşluk varsa yazılır. */
    private fun gunListesiniCiz() {
        gunListeKutusu.removeAllViews()
        val d = resources.displayMetrics.density
        val liste = ornekler.filter { it.gunuIcerir(ref) }.sortedWith(::gunSirasi)
        if (liste.isEmpty()) {
            val t = TextView(this)
            t.text = getString(R.string.etkinlik_yok)
            t.textSize = 15f
            t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
            t.setPadding(0, (8 * d).toInt(), 0, 0)
            gunListeKutusu.addView(t)
            return
        }
        var sonBitis = -1
        for (o in liste) {
            if (!o.tumGun) {
                val bas = o.gunBaslangicDk(ref)
                if (sonBitis >= 0 && bas - sonBitis >= 30) {
                    val t = TextView(this)
                    t.text = getString(R.string.bos_zaman, Metinler.sure(this, bas - sonBitis))
                    t.textSize = 13f
                    t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
                    t.setPadding((16 * d).toInt(), (2 * d).toInt(), 0, (10 * d).toInt())
                    gunListeKutusu.addView(t)
                }
                sonBitis = maxOf(sonBitis, o.gunBitisDk(ref))
            }
            kartEkle(gunListeKutusu, o, ref)
        }
    }

    // ---- Görünüm düğmesi: her dokunuş sekmenin bir sonraki görünümüne geçer ----

    /**
     * Ay: Kompakt → Ayrıntılı → Yıl → Kompakt · Hafta: 7 gün ↔ 3 gün · Gün: saat
     * ızgarası ↔ liste. Menü açılmaz; yeni görünümün adı alt başlıkta kısa süre görünür.
     */
    private fun gorunumuDegistir() {
        when (mod) {
            Depo.GORUNUM_AY, Depo.GORUNUM_YIL -> ayKatinaGec(when (ayKati()) { 1 -> 2; 2 -> 0; else -> 1 })
            Depo.GORUNUM_HAFTA -> {
                Depo.haftaGunSayisiKaydet(this, if (Depo.haftaGunSayisi(this) == 7) 3 else 7)
                sekmeAdlariniYaz()
                zamanKaydirmaGerekli = true
                yeniGorunum()
            }
            Depo.GORUNUM_GUN -> {
                Depo.gunListeKaydet(this, !Depo.gunListe(this))
                zamanKaydirmaGerekli = true
                yeniGorunum()
            }
            else -> return
        }
        gorunumDugmesiniYaz()
        btnGorunum.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        gorunumAdiniGoster()
    }

    /** Şu anki görünümün simgesi ve adı (ekran okuyucu: "Görünüm: Ayrıntılı"). */
    private fun gorunumBilgisi(): Pair<Int, Int>? = when (mod) {
        Depo.GORUNUM_YIL -> R.drawable.ic_yil to R.string.gorunum_yil
        Depo.GORUNUM_AY -> if (yogunluk == Depo.AY_AYRINTILI) R.drawable.ic_yogun_ayrintili to R.string.ay_ayrintili
            else R.drawable.ic_yogun_kompakt to R.string.ay_kompakt
        Depo.GORUNUM_HAFTA -> if (Depo.haftaGunSayisi(this) == 3) R.drawable.ic_uc_gun to R.string.uc_gun
            else R.drawable.ic_hafta to R.string.yedi_gun
        Depo.GORUNUM_GUN -> if (Depo.gunListe(this)) R.drawable.ic_liste to R.string.gun_liste
            else R.drawable.ic_gun to R.string.gun_izgara
        else -> null
    }

    private fun gorunumDugmesiniYaz() {
        val (ikon, ad) = gorunumBilgisi() ?: return
        (btnGorunum as ImageButton).setImageResource(ikon)
        btnGorunum.contentDescription = getString(R.string.gorunum_dugmesi, getString(ad))
    }

    /** Görünüm değişince alt başlıkta kısa süre duran ad; o sürede depo yenilense de silinmez. */
    private var gosterilenGorunum: String? = null

    private val altBaslikGeriYaz = Runnable {
        gosterilenGorunum = null
        altBaslik.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
        ustBilgiyiYaz()
    }

    private fun gorunumAdiniGoster() {
        val (_, ad) = gorunumBilgisi() ?: return
        isleyici.removeCallbacks(altBaslikGeriYaz)
        gosterilenGorunum = getString(ad)
        altBaslik.text = gosterilenGorunum
        altBaslik.setTextColor(vurgu)
        isleyici.postDelayed(altBaslikGeriYaz, 1400)
    }

    private fun ayListeyiYaz(gunler: Map<Int, List<Ornek>>) {
        ayGunBasligi.text = if (ref == bugun) getString(R.string.gun_bugun_baslik, Metinler.gunBaslik(ref)) else Metinler.gunBaslik(ref)
        ayListe.removeAllViews()
        val liste = (gunler[ref] ?: emptyList()).sortedWith(::gunSirasi)
        if (liste.isEmpty()) {
            val t = TextView(this)
            t.text = getString(R.string.etkinlik_yok)
            t.textSize = 15f
            t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
            t.setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            ayListe.addView(t)
        }
        // Çok yoğun günde (ör. yüzlerce içe aktarılmış etkinlik) ilk 40 kart; gerisi Gündem'de.
        Hiz.olc("ay.kartlar(${liste.size})") {
            for ((i, o) in liste.take(AY_LISTE_SINIRI).withIndex()) ayListe.addView(kartHazirla(i, o, ref))
        }
        if (liste.size > AY_LISTE_SINIRI) {
            val t = TextView(this)
            t.text = getString(R.string.gunde_daha, liste.size - AY_LISTE_SINIRI)
            t.textSize = 14f
            t.setTextColor(ContextCompat.getColor(this, TR.color.metin_ikincil))
            t.setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            ayListe.addView(t)
        }
    }

    /** Ay görünümündeki kartlar ay değiştikçe yeniden şişirilmez: ilk [i] kart görünümleri tekrar kullanılır. */
    private val kartHavuzu = ArrayList<View>()

    private fun kartHazirla(i: Int, o: Ornek, gun: Int): View {
        val v = kartHavuzu.getOrNull(i) ?: LayoutInflater.from(this).inflate(R.layout.item_etkinlik, ayListe, false).also { kartHavuzu.add(it) }
        kartiDoldur(v, o, gun)
        return v
    }

    private fun kartEkle(kap: ViewGroup, o: Ornek, gun: Int) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_etkinlik, kap, false)
        kartiDoldur(v, o, gun)
        kap.addView(v)
    }

    private fun kartiDoldur(v: View, o: Ornek, gun: Int) {
        v.findViewById<View>(R.id.etkRenk).backgroundTintList = ColorStateList.valueOf(Renk.yuzey(this, o.renk))
        v.findViewById<TextView>(R.id.etkBaslik).text = o.baslik.ifBlank { getString(R.string.basliksiz) }
        v.findViewById<TextView>(R.id.etkAlt).text = Metinler.ornekAltYazisi(this, o, gun)
        v.setOnClickListener { ornekAc(o) }
    }

    // ---- Hafta ve Gün ----

    private fun zamaniCiz() {
        val (ilk, son) = aralik()
        val adet = son - ilk + 1
        val haftaGorunumu = mod == Depo.GORUNUM_HAFTA
        haftaBasligi.visibility = if (haftaGorunumu) View.VISIBLE else View.GONE
        haftaBasligi.ilkGun = ilk
        haftaBasligi.gunSayisi = adet
        haftaBasligi.bugun = bugun
        haftaBasligi.hafta = if (haftaGorunumu && adet == 7 && Depo.haftaNumaralari(this))
            getString(R.string.hafta_kisa, Gun.isoHafta(ilk + Math.floorMod(-Depo.haftaBasi(this), 7))) else ""
        val kesisen = ornekler.filter { it.sonGun >= ilk && it.ilkGun <= son }
        tumGunSeridi.gunleriAyarla(ilk, adet)
        tumGunSeridi.ornekler = kesisen.filter { it.tumGun }
        zamanIzgara.bugun = bugun
        zamanIzgara.gunSayisi = adet
        zamanIzgara.ilkGun = ilk
        zamanIzgara.ornekler = kesisen.filter { !it.tumGun }
        if (zamanKaydirmaGerekli) {
            zamanKaydirmaGerekli = false
            zamanKaydirici.post {
                val simdi = Gun.yerelDakika(System.currentTimeMillis(), TimeZone.getDefault())
                val hedef = if (bugun in ilk..son) maxOf(0, simdi - 90) else 7 * 60
                zamanKaydirici.scrollTo(0, zamanIzgara.dakikaY(hedef).toInt())
            }
        }
    }

    // ---- Gündem ----

    private fun gundemiCiz() {
        val gunler = gunlereBol()
        gundemListe.removeAllViews()
        val (ilk, son) = aralik()
        var varMi = false
        val inflater = LayoutInflater.from(this)
        // Yanıt bekleyen davetler (Apple'ın "Gelen kutusu"): gündemin başında tek kart, dokununca liste.
        if (bekleyenDavet > 0) {
            val v = inflater.inflate(R.layout.item_etkinlik, gundemListe, false)
            v.findViewById<View>(R.id.etkRenk).backgroundTintList = ColorStateList.valueOf(vurgu)
            v.findViewById<TextView>(R.id.etkBaslik).text = getString(R.string.davet_bekliyor_n, bekleyenDavet)
            v.findViewById<TextView>(R.id.etkAlt).text = getString(R.string.davet_bekliyor_alt)
            v.setOnClickListener { startActivity(Intent(this, AramaActivity::class.java).putExtra(AramaActivity.EK_DAVETLER, true)) }
            gundemListe.addView(v)
            varMi = true
        }
        for (g in ilk..son) {
            val liste = (gunler[g] ?: continue).sortedWith(::gunSirasi)
            varMi = true
            val b = inflater.inflate(R.layout.item_gun_basligi, gundemListe, false)
            val bug = g == bugun
            b.findViewById<TextView>(R.id.gunSayi).apply {
                text = Gun.ayinGunu(g).toString()
                setTextColor(if (bug) vurgu else ContextCompat.getColor(this@MainActivity, TR.color.metin))
            }
            b.findViewById<TextView>(R.id.gunAd).apply {
                text = Metinler.gunGoreli(this@MainActivity, g, bugun).let { if (g - bugun in -1..1) "$it · ${Metinler.haftaGunuUzun(Gun.haftaGunu(g))}" else Metinler.haftaGunuUzun(Gun.haftaGunu(g)) }
                setTextColor(if (bug) vurgu else ContextCompat.getColor(this@MainActivity, TR.color.metin))
            }
            b.findViewById<TextView>(R.id.gunAy).text = Metinler.ayYil(g)
            gundemListe.addView(b)
            for (o in liste) kartEkle(gundemListe, o, g)
        }
        if (!varMi) {
            icerikGundem.visibility = View.GONE
            BosDurum.goster(
                bosDurum, R.drawable.ic_gundem, getString(R.string.gundem_bos_baslik), getString(R.string.gundem_bos_aciklama),
                getString(R.string.etkinlik_ekle) to { yeniEtkinlik() }
            )
        }
    }

    private fun gundemKaydirildi() {
        if (mod != Depo.GORUNUM_GUNDEM || gundemYukleniyor || !yuklendi) return
        val toplam = gundemListe.height
        if (toplam == 0 || gundemGunSayisi >= GUNDEM_EN_COK) return
        val kalan = toplam - (icerikGundem.scrollY + icerikGundem.height)
        if (kalan < 300 * resources.displayMetrics.density) {
            gundemYukleniyor = true
            gundemGunSayisi += GUNDEM_ADIM
            yenile()
        }
    }

    // ---- Gezinme ----

    private fun git(yon: Int) {
        val eski = ref
        ref = when (mod) {
            Depo.GORUNUM_AY -> Gun.ayEkle(ref, yon)
            Depo.GORUNUM_YIL -> Gun.ayEkle(ref, 12 * yon)
            Depo.GORUNUM_HAFTA -> ref + Depo.haftaGunSayisi(this) * yon
            Depo.GORUNUM_GUN -> ref + yon
            else -> ref
        }
        if (ref == eski) return
        zamanKaydirmaGerekli = mod != Depo.GORUNUM_GUN && bugun !in ref..ref
        yeniGorunum()
        kaydir(yon)
    }

    private fun bugunuGoster() {
        val yon = if (bugun > ref) 1 else if (bugun < ref) -1 else 0
        ref = bugun
        zamanKaydirmaGerekli = true
        gundemGunSayisi = GUNDEM_ILK
        yeniGorunum()
        if (yon != 0) kaydir(yon)
    }

    /** Dönem değişince içerik kısa süre yandan kayarak gelir. */
    private fun kaydir(yon: Int) {
        val hedef: View = when (mod) {
            Depo.GORUNUM_AY -> icerikAy
            Depo.GORUNUM_YIL -> icerikYil
            Depo.GORUNUM_GUNDEM -> icerikGundem
            Depo.GORUNUM_GUN -> if (Depo.gunListe(this)) icerikGunListe else icerikZaman
            else -> icerikZaman
        }
        val d = resources.displayMetrics.density
        hedef.animate().cancel()
        hedef.translationX = 36f * d * yon
        hedef.alpha = 0.4f
        hedef.animate().translationX(0f).alpha(1f).setDuration(160).start()
    }

    private fun gunSec(g: Int) {
        // Ayrıntılı ayda günün etkinlikleri hücrede zaten yazılı; dokunuş doğrudan o günü açar.
        if (g == ref || yogunluk == Depo.AY_AYRINTILI) {
            ref = g
            // Seçili güne ikinci dokunuş o günün saatli görünümünü açar.
            modDegistir(Depo.GORUNUM_GUN)
            return
        }
        ref = g
        yeniGorunum()
    }

    private fun modDegistir(yeni: Int) {
        if (yeni == mod) return
        zamanIzgara.secimiBirak()
        mod = yeni
        zamanKaydirmaGerekli = true
        gundemGunSayisi = GUNDEM_ILK
        yeniGorunum()
    }

    // ---- Sürükle-bırak ----

    /** Saatli, tek günlük, yazılabilir takvimdeki gerçek etkinlik (doğum günü değil) taşınabilir. */
    private fun tasinabilir(o: Ornek): Boolean =
        o.etkinlikId > 0 && !o.tumGun && !o.cokGunlu && takvimler.any { it.id == o.takvimId && it.yazilabilir }

    /**
     * Saat ızgarasında bırakılan etkinlik: tekrarlayansa kapsam sorulur, başkasının
     * daveti taşınmaz (sunucuda düzenleyen kişinin olur). Sonra "Geri al" şeridi.
     */
    private fun ornekTasindi(o: Ornek, gun: Int, basDk: Int, bitDk: Int) {
        val tz = TimeZone.getDefault()
        val yeniBas = Gun.yerelAn(gun, basDk, tz)
        val yeniBit = Gun.yerelAn(gun, bitDk, tz)
        if (yeniBas == o.baslangic && yeniBit == o.bitis) return zamanIzgara.geriKoy()
        val sureDegisti = yeniBas == o.baslangic
        yurutucu.execute {
            val e = TakvimDeposu.etkinlik(this, o.etkinlikId)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (e == null || !e.yazilabilir) {
                    zamanIzgara.geriKoy()
                    android.widget.Toast.makeText(this, R.string.tasinamadi, android.widget.Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (e.davetliler.isNotEmpty() && e.organizator.isNotEmpty() && !e.organizator.equals(e.sahipHesap, true)) {
                    zamanIzgara.geriKoy()
                    android.widget.Toast.makeText(this, R.string.tasinamaz_davet, android.widget.Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val uygula = { k: Kapsam -> tasimayiUygula(e, o, yeniBas, yeniBit, k, sureDegisti) }
                if (e.kural == null) {
                    uygula(Kapsam.HEPSI)
                    return@runOnUiThread
                }
                var secildi = false
                AltSayfa(this).baslik(getString(R.string.tasima_kapsam))
                    .madde(R.drawable.ic_gun, getString(R.string.kapsam_bu)) { secildi = true; uygula(Kapsam.BU) }
                    .madde(R.drawable.ic_gundem, getString(R.string.kapsam_sonrakiler)) { secildi = true; uygula(Kapsam.BUNDAN_SONRA) }
                    .madde(R.drawable.ic_tekrar, getString(R.string.kapsam_hepsi)) { secildi = true; uygula(Kapsam.HEPSI) }
                    .kapaninca { if (!secildi) zamanIzgara.geriKoy() }
                    .goster()
            }
        }
    }

    private fun tasimayiUygula(e: Etkinlik, o: Ornek, yeniBas: Long, yeniBit: Long, kapsam: Kapsam, sureDegisti: Boolean) {
        yurutucu.execute {
            val id = TakvimDeposu.guncelle(this, e, o.baslangic, e.copy(baslangic = yeniBas, bitis = yeniBit), kapsam)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (id == null) {
                    zamanIzgara.geriKoy()
                    android.widget.Toast.makeText(this, R.string.tasinamadi, android.widget.Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                yenile()
                // "Bu ve sonrakiler" seriyi böler; geri almak yeni seriyi silip eskisini uzatmak olurdu, şerit yok.
                if (e.kural != null && kapsam == Kapsam.BUNDAN_SONRA) return@runOnUiThread
                GeriAl.goster(this, getString(if (sureDegisti) R.string.sure_degisti else R.string.tasindi)) {
                    yurutucu.execute {
                        val tamam = tasimayiGeriAl(e, o, id, yeniBas, yeniBit, kapsam)
                        runOnUiThread {
                            if (!tamam) android.widget.Toast.makeText(this, R.string.geri_alinamadi, android.widget.Toast.LENGTH_LONG).show()
                            yenile()
                        }
                    }
                }
            }
        }
    }

    /** Taşımayı geri alır: tek etkinlik/tüm seri eski saatine döner, "yalnız bu" istisnası silinir. */
    private fun tasimayiGeriAl(e: Etkinlik, o: Ornek, id: Long, yeniBas: Long, yeniBit: Long, kapsam: Kapsam): Boolean {
        if (e.kural != null && kapsam == Kapsam.BU) {
            return runCatching {
                contentResolver.delete(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
            }.getOrDefault(false)
        }
        val simdiki = TakvimDeposu.etkinlik(this, id) ?: return false
        return TakvimDeposu.guncelle(this, simdiki, yeniBas, simdiki.copy(baslangic = o.baslangic, bitis = o.bitis), Kapsam.HEPSI) != null
    }

    // ---- Etkinlik açma/ekleme ----

    private fun ornekAc(o: Ornek) {
        runCatching { startActivity(OrnekAc.niyet(this, o)) }
    }

    private fun yeniEtkinlik(gun: Int = ref, dakika: Int = -1) {
        if (!izinKontrol()) return izinIste()
        if (takvimler.none { it.yazilabilir }) {
            AltSayfa(this)
                .mesaj(getString(R.string.yazilabilir_yok))
                .madde(R.drawable.ic_takvim, getString(R.string.takvim_olustur)) {
                    yerelTakvimOlustur { yeniEtkinlik(gun, dakika) }
                }
                .goster()
            return
        }
        startActivity(
            Intent(this, DuzenleActivity::class.java)
                .putExtra(DuzenleActivity.EK_GUN, gun).putExtra(DuzenleActivity.EK_DAKIKA, dakika)
        )
    }

    private fun yerelTakvimOlustur(bitti: () -> Unit) {
        yurutucu.execute {
            val id = TakvimDeposu.yerelTakvimOlustur(this, getString(R.string.takvim_telefon), 0xFF0F766E.toInt())
            runOnUiThread {
                if (id == null) {
                    android.widget.Toast.makeText(this, R.string.takvim_olusmadi, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    takvimler = TakvimDeposu.takvimler(this)
                    bitti()
                }
            }
        }
    }

    // ---- Alt sekmeler (Saat ile aynı hap düzeni) ----

    private class Hap(val no: Int, val kutu: LinearLayout, val ikon: ImageView, val yazi: TextView, val ad: Int)

    private val haplar = ArrayList<Hap>()

    private fun sekmeleriKur() {
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val d = resources.displayMetrics.density
        val sekmeler = listOf(
            Triple(Depo.GORUNUM_AY, R.drawable.ic_ay, R.string.gorunum_ay),
            Triple(Depo.GORUNUM_HAFTA, R.drawable.ic_hafta, R.string.gorunum_hafta),
            Triple(Depo.GORUNUM_GUN, R.drawable.ic_gun, R.string.gorunum_gun),
            Triple(Depo.GORUNUM_GUNDEM, R.drawable.ic_gundem, R.string.gorunum_gundem)
        )
        for ((no, ikon, ad) in sekmeler) {
            val kapsayici = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = getString(ad)
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                setBackgroundResource(R.drawable.bg_sekme)
                setOnClickListener { modDegistir(no) }
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
            kapsayici.addView(
                yazi,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = (6 * d).toInt() }
            )
            kutu.addView(kapsayici, LinearLayout.LayoutParams(0, (48 * d).toInt(), 1f))
            haplar.add(Hap(no, kapsayici, simge, yazi, ad))
            ipucuVer(kapsayici)
        }
        sekmeAdlariniYaz()
        sekmeDurumunuGuncelle(false)
    }

    /** Hafta sekmesi 3 gün seçiliyse "3 gün" adını alır. */
    private fun sekmeAdlariniYaz() {
        for (h in haplar) {
            val ad = if (h.no == Depo.GORUNUM_HAFTA && Depo.haftaGunSayisi(this) == 3) R.string.gorunum_uc_gun else h.ad
            h.yazi.text = getString(ad)
            h.kutu.contentDescription = getString(ad)
        }
    }

    /** Seçili sekme hap olur (simge + ad), diğerleri yalnız simge. */
    private fun sekmeDurumunuGuncelle(animasyonlu: Boolean) {
        if (haplar.isEmpty()) return
        val kutu = findViewById<LinearLayout>(R.id.sekmeler)
        val d = resources.displayMetrics.density
        val pasif = ContextCompat.getColor(this, TR.color.metin_ikincil)
        val pastel = Tasarim.pastel(vurgu)
        if (animasyonlu) {
            val gecis = TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(Fade())
                duration = 220
            }
            TransitionManager.beginDelayedTransition(kutu, gecis)
        }
        val sekme = if (mod == Depo.GORUNUM_YIL) Depo.GORUNUM_AY else mod
        for (h in haplar) {
            val secili = h.no == sekme
            h.kutu.isSelected = secili
            h.yazi.visibility = if (secili) View.VISIBLE else View.GONE
            h.kutu.layoutParams = if (secili) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (48 * d).toInt())
            } else {
                LinearLayout.LayoutParams(0, (48 * d).toInt(), 1f)
            }
            h.kutu.backgroundTintList = ColorStateList.valueOf(if (secili) pastel else (pastel and 0x00FFFFFF))
            h.ikon.imageTintList = ColorStateList.valueOf(if (secili) vurgu else pasif)
        }
    }

    companion object {
        private const val IZIN_TAKVIM = 10
        private const val IZIN_BILDIRIM = 11
        private const val GUNDEM_ILK = 45
        private const val GUNDEM_ADIM = 60
        private const val GUNDEM_EN_COK = 365
        private const val AY_LISTE_SINIRI = 40
    }
}
