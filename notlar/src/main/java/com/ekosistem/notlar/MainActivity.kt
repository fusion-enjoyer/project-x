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
import android.text.Editable
import android.text.TextWatcher
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var depo: NotDeposu
    private lateinit var adapter: NotAdapter
    private lateinit var bosDurum: TextView
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
        if (Prefs.ekranGizle(this)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        // Kilit ekranını NotlarApp yaşam döngüsü açar (arka plandan dönüşte de).
        setContentView(R.layout.activity_main)
        depo = NotDeposu(this)
        vurgu = Renkler.vurgu(this)
        vurguUzeri = Renkler.vurguUzeri(this)

        bosDurum = findViewById(R.id.bosDurum)
        klasorSatiri = findViewById(R.id.klasorSatiri)
        baslikCubugu = findViewById(R.id.baslikCubugu)
        secimCubugu = findViewById(R.id.secimCubugu)
        secimSayi = findViewById(R.id.secimSayi)
        serit = findViewById(R.id.bildirimSeridi)
        seritMetin = findViewById(R.id.bildirimMetin)
        seritEylem = findViewById(R.id.bildirimEylem)

        adapter = NotAdapter(
            onTikla = { not ->
                if (secimModu) secimDegistir(not) else editorAc(not.uri)
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
        secimCubuguKur()
        seritEylem.setTextColor(vurgu)

        onBackPressedDispatcher.addCallback(this, geriTusu)
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { menuGoster() }
        findViewById<ImageButton>(R.id.btnGunluk).setOnClickListener { bugununNotu() }
        sablonlariHazirla()

        intent?.getStringExtra("etiket")?.let { etiket ->
            seciliEtiket = etiket
        }

        findViewById<EditText>(R.id.arama).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sorgu = s?.toString()
                adapter.sorgu = sorgu
                yenile()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (vurgu != Renkler.vurgu(this)) {
            recreate()
            return
        }
        yenile()
    }

    /** Seçim modundayken geri tuşu seçimi kapatır, ekrandan çıkmaz. */
    private val geriTusu = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            secimBitir()
        }
    }

    private fun yeniNotDugmesiKur() {
        val btn = findViewById<TextView>(R.id.btnYeni)
        btn.backgroundTintList = ColorStateList.valueOf(vurgu)
        btn.setTextColor(vurguUzeri)
        val ikon = ContextCompat.getDrawable(this, R.drawable.ic_arti)?.mutate()
        if (ikon != null) {
            DrawableCompat.setTint(ikon, vurguUzeri)
            btn.setCompoundDrawablesRelativeWithIntrinsicBounds(ikon, null, null, null)
        }
        btn.setOnClickListener {
            val i = Intent(this, EditorActivity::class.java)
            seciliKlasor?.let { k -> i.putExtra("klasor", k) }
            startActivity(i)
        }
    }

    // --- Liste ---

    private fun yenile() {
        val aktifSorgu = sorgu
        val aktifKlasor = seciliKlasor
        Thread {
            // Editörden yeni dönüldüyse kaydın bitmesini bekle, yoksa eski özet okunur.
            NotDeposu.bekleyenKayit?.let { kayit ->
                try {
                    kayit.join(2000)
                } catch (_: InterruptedException) {
                }
                NotDeposu.bekleyenKayit = null
            }
            val notlar = try {
                val etiket = seciliEtiket
                if (etiket != null) depo.etiketliNotlar(etiket)
                else depo.notlariListele(aktifSorgu, aktifKlasor)
            } catch (_: Exception) {
                emptyList()
            }
            val klasorler = try {
                depo.klasorAdlari()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (aktifSorgu != sorgu || aktifKlasor != seciliKlasor) return@runOnUiThread
                val mevcutAdresler = notlar.map { it.uri.toString() }.toSet()
                secililer.retainAll(mevcutAdresler)
                if (secimModu && secililer.isEmpty()) secimBitir()
                adapter.secililer = secililer.toSet()
                adapter.guncelle(notlar)
                klasorCubuguGuncelle(klasorler)
                if (notlar.isEmpty()) {
                    bosDurum.setText(
                        if (sorgu.isNullOrBlank()) R.string.bos_durum else R.string.bos_arama
                    )
                    bosDurum.visibility = View.VISIBLE
                } else {
                    bosDurum.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun editorAc(uri: Uri) {
        startActivity(
            Intent(this, EditorActivity::class.java).putExtra("uri", uri.toString())
        )
    }

    // --- Klasör çubuğu ---

    private fun klasorCubuguGuncelle(adlar: List<String>) {
        klasorSatiri.removeAllViews()
        chipEkle(getString(R.string.tumu), seciliKlasor == null, null)
        for (ad in adlar) {
            chipEkle(ad, seciliKlasor == ad, ad)
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
                Thread {
                    val oldu = depo.klasorYenidenAdlandir(eski, yeni)
                    runOnUiThread {
                        if (oldu && seciliKlasor == eski) seciliKlasor = yeni
                        yenile()
                    }
                }.start()
            }
            .goster()
    }

    private fun klasorSilOnayi(ad: String) {
        AltSayfa(this)
            .baslik(getString(R.string.klasoru_sil_ozet))
            .madde(R.drawable.ic_sil, getString(R.string.klasoru_sil), tehlikeli = true) {
                Thread {
                    val oldu = depo.klasorSil(ad)
                    runOnUiThread {
                        if (seciliKlasor == ad) seciliKlasor = null
                        if (oldu) {
                            Toast.makeText(this, R.string.klasor_silindi, Toast.LENGTH_SHORT).show()
                        }
                        yenile()
                    }
                }.start()
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
            .madde(R.drawable.ic_gunluk, getString(R.string.bugunun_notu)) { bugununNotu() }
            .madde(R.drawable.ic_sablon, getString(R.string.sablondan_not)) { sablonSec() }
            .madde(R.drawable.ic_bicim_onay, getString(R.string.gorevler)) {
                startActivity(Intent(this, GorevlerActivity::class.java))
            }
            .madde(R.drawable.ic_sil, getString(R.string.cop_kutusu)) {
                startActivity(Intent(this, TrashActivity::class.java))
            }
            .madde(R.drawable.ic_etiket, getString(R.string.etiketler)) { etiketleriGoster() }
            .madde(R.drawable.ic_sirala, getString(R.string.siralama)) { siralamaSec() }
            .madde(R.drawable.ic_ayarlar, getString(R.string.ayarlar)) {
                startActivity(Intent(this, AyarlarActivity::class.java))
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
        if (Prefs.sablonlarKuruldu(this)) return
        Thread {
            Sablonlar.ornekleriOlustur(this, depo)
            Prefs.sablonlarKurulduKaydet(this)
            runOnUiThread { yenile() }
        }.start()
    }

    /** Bugünün notu varsa açılır, yoksa `gunluk` şablonundan oluşturulur. */
    private fun bugununNotu() {
        Thread {
            val baslik = Sablonlar.bugununBasligi()
            val mevcut = depo.baslikIleBul(baslik)
            val adres = mevcut?.uri
                ?: depo.notOlustur(Sablonlar.gunlukIcerik(this, depo, baslik), null, baslik)
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
                val sayfa = AltSayfa(this).baslik(getString(R.string.sablonlar))
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

    private companion object {
        const val SERIT_SURESI = 5000L
    }
}
