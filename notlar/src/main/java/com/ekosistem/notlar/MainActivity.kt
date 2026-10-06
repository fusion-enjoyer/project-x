package com.ekosistem.notlar

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : TemelActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: View
    private lateinit var arama: EditText
    private lateinit var aramaGostergesi: View
    private lateinit var aramaTemizle: View
    private lateinit var klasorSatiri: LinearLayout
    private lateinit var liste: RecyclerView

    private lateinit var baslikCubugu: View
    private lateinit var secimCubugu: View
    private lateinit var secimSayi: TextView

    private lateinit var serit: View
    private lateinit var seritMetin: TextView
    private lateinit var seritEylem: TextView
    private var seritKapatici: Runnable? = null

    private var sorgu: String? = null
    private var seciliKlasor: String? = null
    private var seciliEtiket: String? = null
    private val secililer = mutableSetOf<String>()
    private var secimModu = false

    private var vurgu = 0
    private var vurguUzeri = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Renkler.temaStili(this))
        super.onCreate(savedInstanceState)
        // Kilit ekranını NotlarApp yaşam döngüsü açar (arka plandan dönüşte de).
        setContentView(R.layout.activity_main)
        depo = NotDeposu(this)
        vurgu = Renkler.vurgu(this)
        vurguUzeri = Renkler.vurguUzeri(this)

        bosDurum = findViewById(R.id.bosDurum)
        // Yazılar değişip kutu boy değiştirince ortası yeniden hesaplanır.
        bosDurum.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bosDurumuKaydir() }
        arama = findViewById(R.id.arama)
        aramaGostergesi = findViewById(R.id.aramaGostergesi)
        aramaTemizle = findViewById(R.id.aramaTemizle)
        aramaTemizle.setOnClickListener { arama.setText("") }
        klasorSatiri = findViewById(R.id.klasorSatiri)
        baslikCubugu = findViewById(R.id.baslikCubugu)
        secimCubugu = findViewById(R.id.secimCubugu)
        secimSayi = findViewById(R.id.secimSayi)
        serit = findViewById(R.id.bildirimSeridi)
        seritMetin = findViewById(R.id.bildirimMetin)
        seritEylem = findViewById(R.id.bildirimEylem)

        adapter = NotAdapter(
            onTikla = { not ->
                if (secimModu) secimDegistir(not) else notuAc(not)
            },
            onUzunBas = { not -> secimBaslat(not) }
        )
        adapter.vurgu = vurgu
        adapter.kartRengi = ContextCompat.getColor(this, R.color.kart)
        adapter.secimRengi = (vurgu and 0x00FFFFFF) or 0x33000000

        liste = findViewById(R.id.liste)
        liste.layoutManager = LinearLayoutManager(this)
        liste.adapter = adapter
        swipeKur(liste)

        yeniNotDugmesiKur()
        klavyedeCubuguGizle()
        secimCubuguKur()
        seritEylem.setTextColor(vurgu)

        onBackPressedDispatcher.addCallback(this, geriTusu)
        sablonlariHazirla()
        hosgeldinHazirla()
        if (savedInstanceState == null) yeniNotuKurtar()

        intent?.getStringExtra("etiket")?.let { etiket ->
            seciliEtiket = etiket
        }
        // Klasör widget'ının başlığından gelindiyse o klasör seçili açılır.
        intent?.getStringExtra("klasor")?.let { klasor ->
            seciliKlasor = klasor
        }
        // Tema değişince ya da ekran dönünce seçili klasör "Tümü"ne dönüyordu.
        savedInstanceState?.let { durum ->
            seciliKlasor = durum.getString(DURUM_KLASOR)
            durum.getString(DURUM_ETIKET)?.let { seciliEtiket = it }
        }
        onbellektenGoster()
        if (savedInstanceState == null) kisayoluIsle(intent)

        arama.setOnFocusChangeListener { _, odakta ->
            if (odakta && !aramaHazirlandi && !listeIsci.isShutdown) {
                aramaHazirlandi = true
                listeIsci.execute { runCatching { depo.aramaIcinHazirla() } }
            }
        }
        arama.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Boş kutu "arama yok" demek; ekran geri yüklenirken gelen boş
                // değişiklik de açılışta listeyi ikinci kez yüklüyordu.
                val yeni = s?.toString()?.takeIf { it.isNotBlank() }
                if (yeni == sorgu) {
                    aramaYuvasiGuncelle()
                    return
                }
                sorgu = yeni
                adapter.sorgu = sorgu
                // Her harfte bütün notları taramak yerine yazma durunca bir kez ara.
                liste.removeCallbacks(aramaGecikmeli)
                liste.postDelayed(aramaGecikmeli, ARAMA_GECIKMESI)
                // Hızlı biten aramada gösterge hiç görünmesin, yanıp sönmesin.
                aramaSuruyor = true
                liste.removeCallbacks(gostergeyiAc)
                liste.postDelayed(gostergeyiAc, ARAMA_GECIKMESI + GOSTERGE_GECIKMESI)
                aramaYuvasiGuncelle()
            }
        })
    }

    /**
     * Büyük klasörde ilk arama birkaç saniye sürebiliyor; bu sırada eski liste
     * olduğu gibi kaldığı için arama çalışmıyor sanılıyordu. Arama sürerken eski
     * sonuçlar soluklaşır ve kutuda "Aranıyor…" yazar; bitince "temizle" gelir.
     * Dönen gösterge denendi: her karede yeniden çizim, emülatörde aramayı
     * 1,9 sn'den 14,5 sn'ye çıkardı. Eski telefonlarda da bedava değil.
     */
    private var aramaSuruyor = false
    private var gostergeAcik = false
    private val gostergeyiAc = Runnable {
        if (!aramaSuruyor) return@Runnable
        gostergeAcik = true
        aramaYuvasiGuncelle()
    }

    private fun aramaBitti() {
        aramaSuruyor = false
        gostergeAcik = false
        liste.removeCallbacks(gostergeyiAc)
        aramaYuvasiGuncelle()
    }

    private fun aramaYuvasiGuncelle() {
        aramaGostergesi.visibility = if (gostergeAcik) View.VISIBLE else View.GONE
        val saydamlik = if (gostergeAcik) ESKI_SONUC_SAYDAMLIGI else 1f
        liste.alpha = saydamlik
        bosDurum.alpha = saydamlik
        // Yazılan metin sağdaki yazının ya da düğmenin altına kaymasın.
        val sag = ((if (gostergeAcik) 104 else 48) * resources.displayMetrics.density).toInt()
        if (arama.paddingRight != sag) {
            arama.setPadding(arama.paddingLeft, arama.paddingTop, sag, arama.paddingBottom)
        }
        aramaTemizle.visibility =
            if (!gostergeAcik && arama.text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Liste boşken nedeni söylenir: hiç not yok, klasör boş, arama ya da etiket
     * eşleşmedi. Önceden hepsinde aynı gri cümle vardı; boş bir klasörde bile
     * "henüz not yok" yazıyordu.
     */
    private fun bosDurumGuncelle(bos: Boolean) {
        if (!bos) {
            bosDurum.visibility = View.GONE
            return
        }
        val s = sorgu
        val k = seciliKlasor
        val e = seciliEtiket
        var eylem: Pair<String, () -> Unit>? = null
        val (ikon, baslik, aciklama) = when {
            e != null -> Triple(
                R.drawable.ic_etiket,
                getString(R.string.bos_etiket_baslik, e),
                getString(R.string.bos_etiket_aciklama, e)
            )
            s != null && k != null -> {
                eylem = getString(R.string.tum_notlarda_ara) to {
                    seciliKlasor = null
                    yenile()
                }
                Triple(
                    R.drawable.ic_ara,
                    getString(R.string.bos_arama_baslik, s),
                    getString(R.string.bos_arama_klasorde, klasorGorunenAdi(k))
                )
            }
            s != null -> {
                eylem = getString(R.string.aramayi_temizle) to { arama.setText("") }
                Triple(
                    R.drawable.ic_ara,
                    getString(R.string.bos_arama_baslik, s),
                    getString(R.string.bos_arama_aciklama)
                )
            }
            k != null -> Triple(
                R.drawable.ic_klasor,
                getString(R.string.bos_klasor_baslik),
                getString(R.string.bos_klasor_aciklama)
            )
            else -> Triple(
                R.drawable.ic_duzenle,
                getString(R.string.bos_baslik),
                getString(R.string.bos_aciklama)
            )
        }
        // Aramada boş durum üstten kaydırılarak ortaya konur (bkz. bosDurumuKaydir).
        val yer = bosDurum.layoutParams as FrameLayout.LayoutParams
        val aramada = s != null
        val yerci = if (aramada) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
        if (yer.gravity != yerci) {
            yer.gravity = yerci
            yer.bottomMargin = if (aramada) 0 else (CUBUK_PAYI * resources.displayMetrics.density).toInt()
            bosDurum.layoutParams = yer
        }
        BosDurum.goster(bosDurum, ikon, baslik, aciklama, eylem)
        bosDurumuKaydir()
    }

    /** Liste alanının klavye kapalıykenki yüksekliği; aramada boş durum buna göre ortalanır. */
    private var klavyesizYukseklik = 0

    /**
     * Aramada boş durum, klavye kapalıykenki alanın ortasında sabit durur.
     * Görünen alanda ortalanınca klavye açılıp kapandıkça yukarı aşağı
     * kayıyordu; üste sabitlenince de ekranın çok yukarısında kalıyordu.
     * Kenar payı değil kaydırma: klavye açıkken alan kısalınca kutu
     * sıkışmasın, alt kısmı klavyenin arkasında kalsın.
     */
    private fun bosDurumuKaydir() {
        if (sorgu == null) {
            bosDurum.translationY = 0f
            return
        }
        val alan = klavyesizYukseklik.takeIf { it > 0 } ?: (bosDurum.parent as View).height
        val y = resources.displayMetrics.density
        bosDurum.translationY =
            ((alan - CUBUK_PAYI * y - bosDurum.height) / 2).coerceAtLeast(16 * y)
    }

    private fun klasorGorunenAdi(ad: String): String =
        if (ad == Sablonlar.KLASOR) getString(R.string.sablonlar) else ad

    override fun onSaveInstanceState(durum: Bundle) {
        super.onSaveInstanceState(durum)
        durum.putString(DURUM_KLASOR, seciliKlasor)
        durum.putString(DURUM_ETIKET, seciliEtiket)
    }

    override fun onNewIntent(yeni: Intent) {
        super.onNewIntent(yeni)
        kisayoluIsle(yeni)
    }

    /** Simgeye uzun basınca çıkan kısayollar (res/xml-v25/kisayollar.xml). */
    private fun kisayoluIsle(gelen: Intent?) {
        when (gelen?.action) {
            KISAYOL_YENI -> startActivity(Intent(this, EditorActivity::class.java))
            KISAYOL_GUNLUK -> bugununNotu()
            KISAYOL_ARA -> {
                aramaBekliyor = true
                if (hasWindowFocus()) aramayiAc()
            }
            else -> return
        }
        // Ekran döndürülünce ya da geri gelince aynı kısayol tekrar çalışmasın.
        gelen.action = Intent.ACTION_MAIN
    }

    /**
     * Arama kısayolu (widget, simge menüsü) pencere odağı gelince işlenir.
     * Ekran açılırken klavye istenirse Android isteği sessizce yok sayıyordu:
     * pencere henüz odakta değildi, uygulama kilitliyse kilit ekranı öndeydi.
     */
    private var aramaBekliyor = false

    override fun onWindowFocusChanged(odakta: Boolean) {
        super.onWindowFocusChanged(odakta)
        if (odakta && aramaBekliyor) aramayiAc()
    }

    private fun aramayiAc() {
        aramaBekliyor = false
        arama.post { aramaOdakla() }
    }

    override fun onResume() {
        super.onResume()
        if (vurgu != Renkler.vurgu(this)) {
            recreate()
            return
        }
        // Ayarlarda yazı tipi değiştiyse kartlar yeni yazıyla yeniden çizilir.
        val yaziTipi = Prefs.yaziTipi(this)
        if (yaziTipi != sonYaziTipi) {
            sonYaziTipi = yaziTipi
            adapter.notifyDataSetChanged()
        }
        donusZamani = SystemClock.elapsedRealtime()
        // Editörden dönüş: düzenlenen kart hemen, tam tarama arkadan.
        NotDeposu.sonDuzenleme?.let { d ->
            NotDeposu.sonDuzenleme = null
            duzenlemeyiUygula(d)
        }
        yenile()
    }

    override fun onStop() {
        super.onStop()
        // Başka ekrana geçilirken arama odağı bırakılır; dönüşte imleç yanıp sönmesin.
        arama.clearFocus()
        // Ana ekrana dönülürken widget'lar güncel olsun; notlar uygulama
        // dışında (Obsidian, Syncthing) değişmiş olabilir.
        if (!isChangingConfigurations) NotWidget.birazdanGuncelle(this)
    }

    /** Hız ölçümü için: ekrana son dönüş anı. */
    private var donusZamani = 0L

    /** Kartların çizildiği yazı tipi; ayarlardan dönünce karşılaştırılır. */
    private var sonYaziTipi = -1

    /** Seçim modundayken geri tuşu seçimi kapatır, ekrandan çıkmaz. */
    private val geriTusu = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            secimBitir()
        }
    }

    /**
     * Yüzen gezinme çubuğu: görevler, günlük not, yeni not, şablonlar, menü.
     * Arama düğmesi yoktu artık: arama kutusu zaten ekranın üstünde duruyor.
     */
    private fun yeniNotDugmesiKur() {
        val yeni = findViewById<ImageButton>(R.id.navYeni)
        yeni.backgroundTintList = ColorStateList.valueOf(vurgu)
        yeni.imageTintList = ColorStateList.valueOf(vurguUzeri)
        yeni.setOnClickListener {
            val i = Intent(this, EditorActivity::class.java)
            seciliKlasor?.let { k -> i.putExtra("klasor", k) }
            startActivity(i)
        }
        findViewById<ImageButton>(R.id.navGorevler).setOnClickListener {
            startActivity(Intent(this, GorevlerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navGunluk).setOnClickListener { bugununNotu() }
        findViewById<ImageButton>(R.id.navSablon).setOnClickListener { sablonSec() }
        findViewById<ImageButton>(R.id.navMenu).setOnClickListener { menuGoster() }
        ipucuVer(
            findViewById(R.id.navGorevler),
            findViewById(R.id.navGunluk),
            yeni,
            findViewById(R.id.navSablon),
            findViewById(R.id.navMenu)
        )
    }

    /**
     * Klavye açıkken yüzen gezinme çubuğu klavyenin hemen üstüne çıkıp arama
     * sonuçlarını örtüyordu. Klavye açılınca gizlenir, kapanınca geri gelir.
     *
     * Klavyenin açık olduğu, liste alanının ekranın altına ne kadar uzak
     * kaldığından anlaşılır (her Android sürümünde, pencere küçülse de kök
     * dolgusu büyüse de). Önceden pencerenin görünen alanına bakılıyordu; o,
     * klavye kapanmaya başlar başlamaz "kapandı" diyordu, yerleşim ise hâlâ
     * klavyeli boydaydı: çubuk bir an klavyenin üstünde görünüp aşağı iniyordu.
     */
    private fun klavyedeCubuguGizle() {
        val cubuk = findViewById<View>(R.id.gezinmeCubugu)
        val sis = findViewById<View>(R.id.altSis)
        val kok = window.decorView
        val alan = liste.parent as View
        val konum = IntArray(2)
        kok.viewTreeObserver.addOnGlobalLayoutListener {
            alan.getLocationInWindow(konum)
            val alttakiBosluk = kok.height - (konum[1] + alan.height)
            val klavyeAcik = alttakiBosluk > kok.height * KLAVYE_ORANI
            klavyeGorunuyor = klavyeAcik
            if (!klavyeAcik && alan.height != klavyesizYukseklik) {
                klavyesizYukseklik = alan.height
                bosDurumuKaydir()
            }
            val hedef = if (klavyeAcik) View.GONE else View.VISIBLE
            if (cubuk.visibility != hedef) {
                cubuk.visibility = hedef
                sis.visibility = hedef
                if (!klavyeAcik) {
                    // Klavyenin son karesi kalkarken çubuk yavaşça belirsin.
                    for (v in arrayOf(cubuk, sis)) {
                        v.alpha = 0f
                        v.animate().alpha(1f).setDuration(CUBUK_BELIRME).start()
                    }
                    // Klavye kapandıysa arama kutusu odağı bıraksın; yoksa başka
                    // ekranlardan dönünce bile imleç kutuda yanıp sönüyordu.
                    arama.clearFocus()
                }
            }
        }
    }

    /** Yerleşimden okunan klavye durumu (klavyedeCubuguGizle). */
    private var klavyeGorunuyor = false

    /**
     * Kutuya odaklanır ve klavyeyi açar. Pencere odağı geldikten sonra da
     * klavyenin bağlantısı bir an gecikebiliyor: telefonda imleç kutuda
     * yanıp sönüyor ama klavye açılmıyordu. Klavye görünene kadar kısa
     * aralıklarla yeniden istenir (en çok ~1,5 sn).
     */
    private fun aramaOdakla(deneme: Int = 0) {
        if (isFinishing || (deneme > 0 && (klavyeGorunuyor || !arama.hasFocus()))) return
        arama.requestFocus()
        androidx.core.view.WindowCompat.getInsetsController(window, arama)
            .show(androidx.core.view.WindowInsetsCompat.Type.ime())
        val yonetici = getSystemService(INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        yonetici?.showSoftInput(arama, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        if (deneme < KLAVYE_DENEMESI) arama.postDelayed({ aramaOdakla(deneme + 1) }, 250L)
    }

    /**
     * Yeni bir not yazılırken uygulama kapandıysa (çökme, bellek yetmemesi,
     * son uygulamalardan kaydırıp atma) metin taslakta kalmıştır; nota çevrilir.
     * Kayıtla aynı sırada çalışır, böylece editörün kendi kaydı hep önce biter.
     */
    private fun yeniNotuKurtar() {
        val uygulama = applicationContext
        NotDeposu.yazici.execute {
            val taslaklar = Taslaklar(uygulama)
            val taslak = taslaklar.oku(null) ?: return@execute
            if (taslak.metin.isBlank()) {
                taslaklar.sil(null)
                return@execute
            }
            if (depo.notOlustur(taslak.metin) == null) return@execute
            taslaklar.sil(null)
            runOnUiThread {
                Toast.makeText(this, R.string.taslak_kurtarildi, Toast.LENGTH_LONG).show()
                yenile()
            }
        }
    }

    // --- Liste ---

    private val aramaGecikmeli = Runnable { yenile() }

    /**
     * Liste işleri tek sırada çalışır; sırası gelince daha yeni bir istek varsa
     * atlanır. Önceden her yenileme (aramada her harf) ayrı bir iş parçacığında
     * bütün notları tarıyordu ve hepsi aynı anda çalışıp birbirini yavaşlatıyordu.
     */
    private val listeIsci = Executors.newSingleThreadExecutor()
    private val listeNesli = AtomicInteger()
    private var aramaHazirlandi = false

    override fun onDestroy() {
        super.onDestroy()
        listeIsci.shutdown()
    }

    /** Gerçek tarama sonucu ekrana geldi mi? Geldiyse anlık liste artık gösterilmez. */
    private var gercekListeGeldi = false

    /** Hız ölçümü için: ekranın oluştuğu an. */
    private val olusmaZamani = SystemClock.elapsedRealtime()

    private fun acilisOlcumu(ne: String, sayi: Int) {
        if (BuildConfig.DEBUG) {
            Log.d("NotlarHiz", "$ne ekranda: $sayi not, açılıştan ${SystemClock.elapsedRealtime() - olusmaZamani} ms")
        }
    }

    /**
     * Soğuk açılışta son bilinen listeyi hemen gösterir (klasörler taranmadan).
     * Aynı iş sırasında yenilemeden önce çalışır; tarama bitince yerini alır.
     */
    private fun onbellektenGoster() {
        if (seciliEtiket != null) return
        listeIsci.execute {
            val notlar = runCatching { depo.onbellektenListe() }.getOrNull()
            if (notlar.isNullOrEmpty()) return@execute
            runOnUiThread {
                if (gercekListeGeldi || sorgu != null || seciliKlasor != null || seciliEtiket != null) {
                    return@runOnUiThread
                }
                adapter.guncelle(notlar)
                bosDurum.visibility = View.GONE
                acilisOlcumu("anlık liste", notlar.size)
            }
        }
    }

    /**
     * Editörde kaydedilen/silinen notu, tam tarama bitmeden listeye yansıtır.
     * Arama ya da etiket süzgeci açıkken tahmin yürütülmez; tam liste gelir.
     */
    private fun duzenlemeyiUygula(d: Duzenleme) {
        if (sorgu != null || seciliEtiket != null) return
        val liste = adapter.tumNotlar().toMutableList()
        val sira = liste.indexOfFirst { it.uri == d.uri }
        when {
            d.silindi -> if (sira >= 0) liste.removeAt(sira) else return
            sira >= 0 -> {
                val eski = liste[sira]
                val (baslik, ozet) = NotDeposu.onizlemeCikar(d.metin, eski.ad)
                val (gorev, biten) = NotDeposu.gorevSayaci(d.metin)
                liste[sira] = eski.copy(
                    baslik = baslik,
                    ozet = if (eski.kilitli) "" else ozet,
                    degistirilme = d.zaman,
                    gorev = if (eski.kilitli) 0 else gorev,
                    biten = if (eski.kilitli) 0 else biten
                )
            }
            else -> {
                // Yeni not: yalnızca şu an bakılan klasöre aitse eklenir.
                if (seciliKlasor != null && seciliKlasor != d.klasor) return
                val ad = Uri.decode(d.uri.lastPathSegment ?: "").substringAfterLast('/')
                val (baslik, ozet) = NotDeposu.onizlemeCikar(d.metin, ad)
                val (gorev, biten) = NotDeposu.gorevSayaci(d.metin)
                liste.add(
                    Not(
                        d.uri, ad, baslik, ozet, d.zaman, sabit = false, klasor = d.klasor,
                        gorev = gorev, biten = biten
                    )
                )
            }
        }
        adapter.guncelle(depo.sirala(liste))
        bosDurumGuncelle(liste.isEmpty())
        if (BuildConfig.DEBUG) {
            Log.d("NotlarHiz", "düzenleme kartta: dönüşten ${SystemClock.elapsedRealtime() - donusZamani} ms")
        }
    }

    private fun yenile() {
        val aktifSorgu = sorgu
        val aktifKlasor = seciliKlasor
        val nesil = listeNesli.incrementAndGet()
        if (listeIsci.isShutdown) return
        listeIsci.execute {
            if (nesil != listeNesli.get()) return@execute
            // Editörden yeni dönüldüyse kaydın bitmesini bekle, yoksa eski özet okunur.
            NotDeposu.bekleyenKayit?.let { kayit ->
                try {
                    kayit.get(2, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
                NotDeposu.bekleyenKayit = null
            }
            NotDeposu.sonDuzenleme?.let { d ->
                NotDeposu.sonDuzenleme = null
                runOnUiThread { duzenlemeyiUygula(d) }
            }
            val baslangic = SystemClock.elapsedRealtime()
            val notlar = try {
                val etiket = seciliEtiket
                if (etiket != null) depo.etiketliNotlar(etiket)
                else depo.notlariListele(aktifSorgu, aktifKlasor)
            } catch (_: Exception) {
                emptyList()
            }
            if (BuildConfig.DEBUG) {
                // Hız ölçümü: adb logcat -s NotlarHiz
                Log.d("NotlarHiz", "liste: ${notlar.size} not, ${SystemClock.elapsedRealtime() - baslangic} ms, sorgu=${aktifSorgu != null}")
            }
            val klasorler = try {
                depo.klasorAdlari()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (nesil != listeNesli.get()) return@runOnUiThread
                if (aktifSorgu != sorgu || aktifKlasor != seciliKlasor) return@runOnUiThread
                aramaBitti()
                if (!gercekListeGeldi) acilisOlcumu("gerçek liste", notlar.size)
                if (BuildConfig.DEBUG) {
                    Log.d("NotlarHiz", "tam liste ekranda: dönüşten ${SystemClock.elapsedRealtime() - donusZamani} ms")
                }
                gercekListeGeldi = true
                val mevcutAdresler = notlar.map { it.uri.toString() }.toSet()
                secililer.retainAll(mevcutAdresler)
                if (secimModu && secililer.isEmpty()) secimBitir()
                adapter.secililer = secililer.toSet()
                adapter.guncelle(notlar)
                klasorCubuguGuncelle(klasorler)
                bosDurumGuncelle(notlar.isEmpty())
            }
        }
    }

    /**
     * Eşitleme çakışması kopyası editörde değil, asıl notla karşılaştırma
     * ekranında açılır. Asıl not listede yoksa (silinmiş) sıradan not gibidir.
     */
    private fun notuAc(not: Not) {
        val bilgi = Cakisma.coz(not.ad)
        val asil = bilgi?.let { b ->
            adapter.tumNotlar().firstOrNull { it.ad == b.asilAd && it.klasor == not.klasor }
        }
        if (asil == null) {
            editorAc(not.uri)
            return
        }
        startActivity(
            Intent(this, GecmisActivity::class.java)
                .putExtra("uri", asil.uri.toString())
                .putExtra(GecmisActivity.CAKISMA, not.uri.toString())
                .putExtra(GecmisActivity.CAKISMA_ADI, not.ad)
        )
    }

    private fun editorAc(uri: Uri) {
        startActivity(
            Intent(this, EditorActivity::class.java).putExtra("uri", uri.toString())
        )
    }

    // --- Klasör çubuğu ---

    /** Son gösterilen klasör adları (iyimser yeniden adlandırma için). */
    private var sonKlasorler: List<String> = emptyList()

    private fun klasorCubuguGuncelle(adlar: List<String>) {
        sonKlasorler = adlar
        klasorSatiri.removeAllViews()
        chipEkle(getString(R.string.tumu), seciliKlasor == null, null)
        // Şablon klasörü sıradan çip olarak listelenmez; gezinme çubuğundan
        // "şablonları düzenle" seçilirse kapatılabilir çip olarak belirir.
        for (ad in adlar) {
            if (ad == Sablonlar.KLASOR) continue
            chipEkle(ad, seciliKlasor == ad, ad)
        }
        if (seciliKlasor == Sablonlar.KLASOR) {
            chipEkle(getString(R.string.sablonlar), true, null) {
                seciliKlasor = null
                yenile()
            }
        }
        seciliEtiket?.let { etiket ->
            chipEkle("#$etiket", true, null) {
                seciliEtiket = null
                yenile()
            }
        }
        chipEkle("+", false, null) { yeniKlasorDialog(null) }
    }

    private fun chipEkle(
        etiket: String,
        secili: Boolean,
        klasorAdi: String?,
        ozelTikla: (() -> Unit)? = null
    ) {
        val tv = TextView(this)
        tv.text = etiket
        tv.textSize = 13f
        tv.setTypeface(null, if (secili) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(
            if (secili) vurgu else ContextCompat.getColor(this, R.color.metin_ikincil)
        )
        tv.setBackgroundResource(R.drawable.bg_chip)
        if (secili) {
            tv.backgroundTintList = ColorStateList.valueOf((vurgu and 0x00FFFFFF) or 0x26000000)
        }
        val y = resources.displayMetrics.density
        tv.setPadding((14 * y).toInt(), (7 * y).toInt(), (14 * y).toInt(), (7 * y).toInt())
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = (8 * y).toInt()
        tv.layoutParams = lp
        tv.setOnClickListener {
            if (ozelTikla != null) {
                ozelTikla()
            } else {
                seciliKlasor = if (seciliKlasor == klasorAdi) null else klasorAdi
                yenile()
            }
        }
        if (klasorAdi != null) {
            tv.setOnLongClickListener {
                klasorSecenekleri(klasorAdi)
                true
            }
        }
        klasorSatiri.addView(tv)
    }

    private fun klasorSecenekleri(ad: String) {
        AltSayfa(this)
            .baslik(ad)
            .madde(R.drawable.ic_duzenle, getString(R.string.yeniden_adlandir)) {
                klasorYenidenAdlandir(ad)
            }
            .madde(R.drawable.ic_sil, getString(R.string.klasoru_sil), tehlikeli = true) {
                klasorSilOnayi(ad)
            }
            .goster()
    }

    private fun klasorYenidenAdlandir(eski: String) {
        AltSayfa(this)
            .baslik(getString(R.string.yeniden_adlandir))
            .girdi(
                ipucu = getString(R.string.klasor_adi),
                baslangic = eski,
                dugmeMetni = getString(R.string.yeniden_adlandir)
            ) { yeni ->
                val temiz = depo.adTemizle(yeni)
                if (temiz.isEmpty() || temiz == eski || temiz in sonKlasorler) {
                    Thread {
                        depo.klasorYenidenAdlandir(eski, yeni)
                        runOnUiThread { yenile() }
                    }.start()
                    return@girdi
                }
                // Seçilen klasörde Android her dosyayı kendi veritabanında da
                // güncellediği için yeniden adlandırma saniyeler sürebiliyor;
                // çip ve kart etiketleri beklemeden yeni adı gösterir.
                val oncekiSecili = seciliKlasor
                if (seciliKlasor == eski) seciliKlasor = temiz
                adapter.guncelle(adapter.tumNotlar().map { if (it.klasor == eski) it.copy(klasor = temiz) else it })
                klasorCubuguGuncelle(sonKlasorler.map { if (it == eski) temiz else it })
                Thread {
                    val oldu = depo.klasorYenidenAdlandir(eski, yeni)
                    runOnUiThread {
                        if (!oldu) {
                            seciliKlasor = oncekiSecili
                            Toast.makeText(this, R.string.yedek_hata, Toast.LENGTH_SHORT).show()
                        }
                        yenile()
                    }
                }.start()
            }
            .goster()
    }

    private fun klasorSilOnayi(ad: String) {
        AltSayfa(this)
            .mesaj(getString(R.string.klasoru_sil_ozet))
            .madde(R.drawable.ic_sil, getString(R.string.klasoru_sil), tehlikeli = true) {
                NotDeposu.yazici.execute {
                    val sonuc = depo.klasorSil(ad)
                    runOnUiThread {
                        val mesaj = when (sonuc) {
                            NotDeposu.KLASOR_SILINDI -> R.string.klasor_silindi
                            NotDeposu.KLASOR_KISMEN -> R.string.klasor_kismen
                            else -> R.string.yedek_hata
                        }
                        if (sonuc == NotDeposu.KLASOR_SILINDI && seciliKlasor == ad) seciliKlasor = null
                        Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show()
                        yenile()
                    }
                }
            }
            .goster()
    }

    private fun yeniKlasorDialog(tasinacak: List<Not>?) {
        AltSayfa(this)
            .baslik(getString(R.string.yeni_klasor))
            .girdi(
                ipucu = getString(R.string.klasor_adi),
                dugmeMetni = getString(R.string.olustur)
            ) { ad ->
                Thread {
                    depo.klasorOlustur(ad)
                    tasinacak?.forEach { depo.klasoreTasi(it.uri, ad) }
                    runOnUiThread {
                        secimBitir()
                        yenile()
                    }
                }.start()
            }
            .goster()
    }

    // --- Seçim modu ---

    private fun secimCubuguKur() {
        findViewById<ImageButton>(R.id.secimKapat).setOnClickListener { secimBitir() }
        findViewById<ImageButton>(R.id.secimSabitle).setOnClickListener { secilileriSabitle() }
        findViewById<ImageButton>(R.id.secimTasi).setOnClickListener { tasiDialog(secilenNotlar()) }
        findViewById<ImageButton>(R.id.secimPaylas).setOnClickListener { secilileriPaylas() }
        findViewById<ImageButton>(R.id.secimSil).setOnClickListener { silmeyiYap(secilenNotlar()) }
        findViewById<ImageButton>(R.id.secimDiger).setOnClickListener { secimDigerMenu() }
        ipucuVer(
            findViewById(R.id.secimKapat),
            findViewById(R.id.secimSabitle),
            findViewById(R.id.secimTasi),
            findViewById(R.id.secimPaylas),
            findViewById(R.id.secimSil),
            findViewById(R.id.secimDiger)
        )
    }

    private fun secimBaslat(not: Not) {
        secimModu = true
        secililer.add(not.uri.toString())
        secimGorunumuGuncelle()
    }

    private fun secimDegistir(not: Not) {
        val id = not.uri.toString()
        if (!secililer.add(id)) secililer.remove(id)
        if (secililer.isEmpty()) secimBitir() else secimGorunumuGuncelle()
    }

    private fun secimBitir() {
        secimModu = false
        secililer.clear()
        secimGorunumuGuncelle()
    }

    private fun secimGorunumuGuncelle() {
        geriTusu.isEnabled = secimModu
        secimCubugu.visibility = if (secimModu) View.VISIBLE else View.GONE
        baslikCubugu.visibility = if (secimModu) View.INVISIBLE else View.VISIBLE
        secimSayi.text = getString(R.string.secildi, secililer.size)
        adapter.secililer = secililer.toSet()
        adapter.notifyDataSetChanged()
    }

    private fun secilenNotlar(): List<Not> =
        adapter.tumNotlar().filter { secililer.contains(it.uri.toString()) }

    private fun secilileriSabitle() {
        val notlar = secilenNotlar()
        if (notlar.isEmpty()) return
        val hepsiSabit = notlar.all { it.sabit }
        for (not in notlar) {
            val sabit = Prefs.sabitler(this).contains(not.uri.toString())
            if (sabit == hepsiSabit) Prefs.sabitDegistir(this, not.uri.toString())
        }
        // Sabitlenmiş notlar widget'ı da tazelensin.
        NotWidget.hepsiniGuncelle(applicationContext)
        secimBitir()
        yenile()
    }

    private fun secilileriPaylas() {
        val notlar = secilenNotlar()
        if (notlar.isEmpty()) return
        Thread {
            val metin = notlar.joinToString("\n\n---\n\n") { depo.oku(it.uri) }
            runOnUiThread {
                secimBitir()
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, metin)
                Kilit.sistemAraciBekleniyor = true
                startActivity(Intent.createChooser(intent, getString(R.string.paylas)))
            }
        }.start()
    }

    /** Seçim çubuğuna sığmayan işler: birleştirme ve toplu etiket. */
    private fun secimDigerMenu() {
        val notlar = secilenNotlar()
        if (notlar.isEmpty()) return
        val sayfa = AltSayfa(this)
        if (notlar.size >= 2) {
            sayfa.madde(R.drawable.ic_birlestir, getString(R.string.birlestir)) {
                birlestirmeOnayi(notlar)
            }
        }
        sayfa.madde(R.drawable.ic_etiket, getString(R.string.etiket_ekle)) { topluEtiketSec(notlar) }
        sayfa.goster()
    }

    /** Kilitli notun içeriği görülmeden değiştirilmesin. */
    private fun kilitliVarMi(notlar: List<Not>): Boolean {
        if (notlar.none { Kilit.notKilitli(this, it.uri.toString()) }) return false
        Toast.makeText(this, R.string.kilitli_secili, Toast.LENGTH_LONG).show()
        return true
    }

    private fun birlestirmeOnayi(notlar: List<Not>) {
        if (kilitliVarMi(notlar)) return
        AltSayfa(this)
            .mesaj(getString(R.string.birlestir_ozet, notlar.size, notlar.first().baslik))
            .madde(R.drawable.ic_birlestir, getString(R.string.birlestir)) { birlestir(notlar) }
            .goster()
    }

    /**
     * Notlar listedeki sırayla ilk notta toplanır. İlk notun önceki hâli
     * geçmişe, diğerleri çöpe gider: ikisi de geri alınabilir.
     */
    private fun birlestir(notlar: List<Not>) {
        secimBitir()
        val ana = notlar.first()
        NotDeposu.yazici.execute {
            val metinler = notlar.map { depo.oku(it.uri) }
            // Şifreli not okununca şifreli veri gelir; birleştirilse bozulurdu.
            if (metinler.any { Sifreleme.sifreliMi(it) }) {
                runOnUiThread {
                    Toast.makeText(this, R.string.sifreli_secili, Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            val onceki = metinler.first()
            if (onceki.isNotBlank()) depo.gecmiseYaz(ana.uri, onceki)
            val oldu = depo.yaz(ana.uri, TopluIslem.birlestir(metinler))
            if (oldu) notlar.drop(1).forEach { depo.copeTasi(it.uri) }
            NotWidget.hepsiniGuncelle(applicationContext)
            runOnUiThread {
                yenile()
                Toast.makeText(
                    this,
                    if (oldu) R.string.birlestirildi else R.string.yedek_hata,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun topluEtiketSec(notlar: List<Not>) {
        if (kilitliVarMi(notlar)) return
        Thread {
            val etiketler = depo.etiketleriListele()
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.etiket_ekle))
                sayfa.girdi(
                    ipucu = getString(R.string.yeni_etiket),
                    dugmeMetni = getString(R.string.ekle)
                ) { ad -> topluEtiketle(notlar, ad) }
                for (etiket in etiketler) {
                    sayfa.madde(R.drawable.ic_etiket, "#$etiket") { topluEtiketle(notlar, etiket) }
                }
                sayfa.goster()
            }
        }.start()
    }

    /** Etiketi zaten taşıyan nota dokunulmaz. */
    private fun topluEtiketle(notlar: List<Not>, ham: String) {
        val etiket = TopluIslem.etiketTemizle(ham)
        if (etiket.isEmpty()) return
        secimBitir()
        NotDeposu.yazici.execute {
            var sayi = 0
            for (not in notlar) {
                val icerik = depo.oku(not.uri)
                if (Sifreleme.sifreliMi(icerik)) continue
                val yeni = TopluIslem.etiketEkle(icerik, etiket) ?: continue
                if (depo.yaz(not.uri, yeni)) sayi++
            }
            runOnUiThread {
                yenile()
                Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.etiket_eklendi, sayi, sayi),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --- Kaydırma hareketleri ---

    private fun swipeKur(liste: RecyclerView) {
        val y = resources.displayMetrics.density
        val kose = 20f * y
        val silBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE24B4A.toInt() }
        val tasiBoya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vurgu }
        val silIkon = ContextCompat.getDrawable(this, R.drawable.ic_cop)
        val tasiIkon = ContextCompat.getDrawable(this, R.drawable.ic_klasor)?.mutate()
        if (tasiIkon != null) DrawableCompat.setTint(tasiIkon, vurguUzeri)

        val geri = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                hedef: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeDirs(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder
            ): Int = if (secimModu) 0 else super.getSwipeDirs(rv, vh)

            override fun onSwiped(vh: RecyclerView.ViewHolder, yon: Int) {
                val konum = vh.bindingAdapterPosition
                val not = adapter.notAl(konum) ?: return
                /*
                 * Kaydırılan satır, kendisini yenileyene kadar ekranda kayık ve
                 * renkli zeminiyle asılı kalır. Klasör seçilmeden alt sayfa
                 * kapatılırsa turuncu klasör ikonu notun üstünde kalıyordu.
                 */
                liste.post { adapter.notifyItemChanged(konum) }
                liste.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                if (yon == ItemTouchHelper.LEFT) silmeyiYap(listOf(not)) else tasiDialog(listOf(not))
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                durum: Int,
                aktif: Boolean
            ) {
                val v = vh.itemView
                if (dX < 0) {
                    val r = RectF(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
                    c.drawRoundRect(r, kose, kose, silBoya)
                    silIkon?.let { ikonCiz(c, it, v, solda = false) }
                } else if (dX > 0) {
                    val r = RectF(v.left.toFloat(), v.top.toFloat(), v.left + dX, v.bottom.toFloat())
                    c.drawRoundRect(r, kose, kose, tasiBoya)
                    tasiIkon?.let { ikonCiz(c, it, v, solda = true) }
                }
                super.onChildDraw(c, rv, vh, dX, dY, durum, aktif)
            }
        }
        ItemTouchHelper(geri).attachToRecyclerView(liste)
    }

    private fun ikonCiz(c: Canvas, ikon: Drawable, v: View, solda: Boolean) {
        val y = resources.displayMetrics.density
        val boyut = (24 * y).toInt()
        val kenar = (20 * y).toInt()
        val ust = v.top + (v.height - boyut) / 2
        val solX = if (solda) v.left + kenar else v.right - kenar - boyut
        ikon.setBounds(solX, ust, solX + boyut, ust + boyut)
        ikon.draw(c)
    }

    // --- Silme ve taşıma (geri alınabilir) ---

    private fun silmeyiYap(notlar: List<Not>) {
        if (notlar.isEmpty()) return
        secimBitir()
        Thread {
            val yeniAdresler = notlar.mapNotNull { depo.copeTasi(it.uri) }
            NotWidget.hepsiniGuncelle(applicationContext)
            runOnUiThread {
                yenile()
                if (yeniAdresler.isEmpty()) return@runOnUiThread
                seritGoster(getString(R.string.cope_tasindi)) {
                    Thread {
                        yeniAdresler.forEach { depo.geriYukle(it) }
                        runOnUiThread { yenile() }
                    }.start()
                }
            }
        }.start()
    }

    private fun tasiDialog(notlar: List<Not>) {
        if (notlar.isEmpty()) return
        Thread {
            val klasorler = depo.klasorAdlari()
            val mevcutKlasor = notlar.firstOrNull()?.klasor
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.klasore_tasi))
                sayfa.madde(
                    R.drawable.ic_tasi,
                    getString(R.string.ana_klasor),
                    secili = notlar.all { it.klasor == null }
                ) { tasi(notlar, null) }
                for (klasor in klasorler) {
                    sayfa.madde(
                        R.drawable.ic_tasi,
                        klasor,
                        secili = mevcutKlasor == klasor && notlar.all { it.klasor == klasor }
                    ) { tasi(notlar, klasor) }
                }
                sayfa.madde(R.drawable.ic_arti_koyu, getString(R.string.yeni_klasor)) {
                    yeniKlasorDialog(notlar)
                }
                sayfa.goster()
            }
        }.start()
    }

    private fun tasi(notlar: List<Not>, klasor: String?) {
        secimBitir()
        Thread {
            val oncekiKlasorler = notlar.map { it.klasor }
            val yeniAdresler = notlar.mapIndexed { i, not ->
                depo.klasoreTasi(not.uri, klasor) to oncekiKlasorler[i]
            }.filter { it.first != null }
            runOnUiThread {
                yenile()
                if (yeniAdresler.isEmpty()) return@runOnUiThread
                seritGoster(getString(R.string.tasindi)) {
                    Thread {
                        yeniAdresler.forEach { (yeniUri, eskiKlasor) ->
                            yeniUri?.let { depo.klasoreTasi(it, eskiKlasor) }
                        }
                        runOnUiThread { yenile() }
                    }.start()
                }
            }
        }.start()
    }

    // --- Bildirim şeridi ---

    private fun seritGoster(mesaj: String, geriAl: () -> Unit) {
        seritKapatici?.let { serit.removeCallbacks(it) }
        seritMetin.text = mesaj
        serit.visibility = View.VISIBLE
        seritEylem.setOnClickListener {
            seritGizle()
            geriAl()
        }
        val kapatici = Runnable { seritGizle() }
        seritKapatici = kapatici
        serit.postDelayed(kapatici, SERIT_SURESI)
    }

    private fun seritGizle() {
        seritKapatici?.let { serit.removeCallbacks(it) }
        seritKapatici = null
        serit.visibility = View.GONE
    }

    // --- Menü ---

    private fun menuGoster() {
        AltSayfa(this)
            .madde(R.drawable.ic_sil, getString(R.string.cop_kutusu)) {
                startActivity(Intent(this, TrashActivity::class.java))
            }
            .madde(R.drawable.ic_etiket, getString(R.string.etiketler)) { etiketleriGoster() }
            .madde(R.drawable.ic_sirala, getString(R.string.siralama)) { siralamaSec() }
            .madde(R.drawable.ic_ayarlar, getString(R.string.ayarlar)) {
                startActivity(Intent(this, AyarlarActivity::class.java))
            }
            .madde(R.drawable.ic_ayar_bilgi, getString(R.string.yardim)) {
                startActivity(Intent(this, YardimActivity::class.java))
            }
            .goster()
    }

    // --- Günlük not ve şablonlar ---

    /**
     * Örnek şablonlar ilk açılışta sessizce oluşturulur; kullanıcı şablon
     * ekranına girdiğinde onları hazır bulur. Yalnızca bir kez denenir:
     * şablonları silen kullanıcıya her açılışta geri getirmeyelim.
     */
    private fun sablonlariHazirla() {
        if (Prefs.sablonSurumu(this) >= Sablonlar.ORNEK_SURUMU) return
        Thread {
            Sablonlar.ornekleriOlustur(this, depo)
            Prefs.sablonSurumuKaydet(this, Sablonlar.ORNEK_SURUMU)
            runOnUiThread { yenile() }
        }.start()
    }

    /**
     * İlk açılışta, klasörde hiç not yoksa "Hoş geldin" notu konur. Yalnızca bir
     * kez denenir: kullanıcı silerse geri gelmez; dolu bir klasöre (Obsidian
     * kasası gibi) ya da güncelleme alan kullanıcının notlarının arasına girmez.
     * Şablon klasörü listeye karışmadığı için örnek şablonlar boşluğu bozmaz.
     */
    private fun hosgeldinHazirla() {
        if (Prefs.hosgeldinDenendi(this)) return
        Prefs.hosgeldinDenendiKaydet(this)
        Thread {
            val bos = try {
                depo.notlariListele(null).isEmpty()
            } catch (_: Exception) {
                false
            }
            if (!bos) return@Thread
            val metin = resources.openRawResource(R.raw.hosgeldin).bufferedReader().use { it.readText() }
            if (depo.notOlustur(metin, null) != null) runOnUiThread { yenile() }
        }.start()
    }

    /** Bugünün notu varsa açılır, yoksa `gunluk` şablonundan oluşturulur. */
    private fun bugununNotu() {
        Thread {
            val adres = Sablonlar.bugununNotu(this, depo)
            runOnUiThread {
                if (adres == null) {
                    Toast.makeText(this, R.string.sablon_hata, Toast.LENGTH_SHORT).show()
                } else {
                    editorAc(adres)
                }
            }
        }.start()
    }

    private fun sablonSec() {
        Thread {
            val sablonlar = Sablonlar.listele(depo)
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.sablondan_not))
                if (sablonlar.isEmpty()) {
                    sayfa.madde(R.drawable.ic_sablon, getString(R.string.ornek_sablonlar)) {
                        ornekSablonlariOlustur()
                    }
                } else {
                    for (sablon in sablonlar) {
                        // Başlıktaki yer tutucu da doldurulsun; "{{tarih}}" yazmasın.
                        sayfa.madde(
                            R.drawable.ic_sablon,
                            Sablonlar.uygula(this, sablon.baslik, "")
                        ) { sablondanNot(sablon) }
                    }
                    sayfa.madde(R.drawable.ic_duzenle, getString(R.string.sablonlari_duzenle)) {
                        seciliKlasor = Sablonlar.KLASOR
                        seciliEtiket = null
                        yenile()
                    }
                }
                sayfa.goster()
            }
        }.start()
    }

    private fun ornekSablonlariOlustur() {
        Thread {
            val sayi = Sablonlar.ornekleriOlustur(this, depo)
            runOnUiThread {
                yenile()
                if (sayi > 0) sablonSec()
                else Toast.makeText(this, R.string.sablon_hata, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun sablondanNot(sablon: Not) {
        Thread {
            val icerik = Sablonlar.uygula(this, depo.oku(sablon.uri), "")
            // Şablon klasörü seçiliyken yeni not oraya değil ana klasöre gitsin.
            val hedef = seciliKlasor?.takeIf { it != Sablonlar.KLASOR }
            val adres = depo.notOlustur(icerik, hedef)
            runOnUiThread {
                if (adres == null) {
                    Toast.makeText(this, R.string.sablon_hata, Toast.LENGTH_SHORT).show()
                } else {
                    editorAc(adres)
                }
            }
        }.start()
    }

    private fun etiketleriGoster() {
        Thread {
            val etiketler = depo.etiketleriListele()
            runOnUiThread {
                val sayfa = AltSayfa(this).baslik(getString(R.string.etiketler))
                if (etiketler.isEmpty()) {
                    sayfa.madde(R.drawable.ic_etiket, getString(R.string.etiket_yok)) {}
                } else {
                    for (etiket in etiketler) {
                        sayfa.madde(
                            R.drawable.ic_etiket,
                            "#$etiket",
                            secili = seciliEtiket == etiket
                        ) {
                            seciliEtiket = if (seciliEtiket == etiket) null else etiket
                            seciliKlasor = null
                            yenile()
                        }
                    }
                }
                sayfa.goster()
            }
        }.start()
    }

    private fun siralamaSec() {
        val etiketler = listOf(
            R.string.siralama_yeni,
            R.string.siralama_eski,
            R.string.siralama_ad_az,
            R.string.siralama_ad_za
        )
        val secili = Prefs.siralama(this)
        val sayfa = AltSayfa(this).baslik(getString(R.string.siralama))
        etiketler.forEachIndexed { indeks, etiket ->
            sayfa.madde(R.drawable.ic_sirala, getString(etiket), secili = indeks == secili) {
                Prefs.siralamaKaydet(this, indeks)
                yenile()
            }
        }
        sayfa.goster()
    }

    companion object {
        const val SERIT_SURESI = 5000L
        const val ARAMA_GECIKMESI = 200L
        const val GOSTERGE_GECIKMESI = 250L
        const val ESKI_SONUC_SAYDAMLIGI = 0.4f
        const val DURUM_KLASOR = "secili_klasor"
        const val DURUM_ETIKET = "secili_etiket"

        const val KISAYOL_YENI = "com.ekosistem.notlar.YENI_NOT"
        const val KISAYOL_GUNLUK = "com.ekosistem.notlar.GUNLUK"
        const val KISAYOL_ARA = "com.ekosistem.notlar.ARA"

        /** Ekranın bu kadarından fazlası kapandıysa klavye açık sayılır. */
        const val KLAVYE_ORANI = 0.15f
        /** Boş durumun ortalanırken yüzen çubuk için bıraktığı pay (dp). */
        const val CUBUK_PAYI = 64
        const val CUBUK_BELIRME = 150L
        /** Klavye açılmazsa arama kutusu için en çok bu kadar yeniden istenir. */
        const val KLAVYE_DENEMESI = 6
    }
}
