package com.ekosistem.takvim

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.AyarSatiri
import com.ekosistem.tasarim.Tasarim
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/**
 * Takvimler: hesaba göre gruplu (Google adresi, DAVx5 hesabı, "Bu telefonda"); her
 * takvim renk noktası, adı ve görünürlük anahtarıyla. Telefondaki takvimler eklenir,
 * yeniden adlandırılır, rengi değişir, silinir (sunucu takvimleri sunucuda yönetilir).
 * Rehberdeki doğum günleri de burada.
 */
class TakvimlerActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private lateinit var kutu: LinearLayout
    private var takvimler: List<Takvim> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_takvimler)
        kutu = findViewById(R.id.takvimlerKutusu)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        yukle()
    }

    private fun yukle() {
        yurutucu.execute {
            val liste = TakvimDeposu.takvimler(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                takvimler = liste
                ciz()
            }
        }
    }

    private fun yerel(t: Takvim) = t.hesapTuru == CalendarContract.ACCOUNT_TYPE_LOCAL

    private fun ciz() {
        kutu.removeAllViews()
        val vurgu = Tasarim.vurgu(this)
        // Önce telefondakiler (bizim yönettiklerimiz), sonra hesaplar adlarına göre.
        val telefonda = takvimler.filter { yerel(it) }
        val hesaplar = takvimler.filterNot { yerel(it) }.groupBy { it.hesap }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val telefonKutusu = bolum(getString(R.string.bu_telefonda))
        telefonda.forEachIndexed { i, t ->
            if (i > 0) ayrac(telefonKutusu)
            takvimSatiri(telefonKutusu, t)
        }
        if (telefonda.isNotEmpty()) ayrac(telefonKutusu)
        val yeni = layoutInflater.inflate(TR.layout.item_ayar, telefonKutusu, false)
        AyarSatiri.kur(yeni, R.drawable.ic_arti, vurgu, getString(R.string.yeni_takvim), null)
        AyarSatiri.oksuz(yeni)
        yeni.isClickable = true
        yeni.setOnClickListener { yeniTakvim() }
        telefonKutusu.addView(yeni)

        for ((hesap, liste) in hesaplar) {
            val k = bolum(hesap.ifBlank { getString(R.string.takvim) })
            liste.forEachIndexed { i, t ->
                if (i > 0) ayrac(k)
                takvimSatiri(k, t)
            }
        }

        val diger = bolum(getString(R.string.diger))
        val dogum = layoutInflater.inflate(TR.layout.item_ayar, diger, false)
        AyarSatiri.kur(dogum, R.drawable.ic_takvim, DogumGunleri.RENK, getString(R.string.dogum_satir), getString(R.string.dogum_ozet))
        AyarSatiri.anahtar(dogum, DogumGunleri.etkin(this)) { acik -> dogumDegisti(acik) }
        diger.addView(dogum)
    }

    private fun bolum(baslik: String): LinearLayout {
        val t = layoutInflater.inflate(R.layout.item_bolum_basligi, kutu, false) as TextView
        t.text = baslik
        kutu.addView(t)
        val k = layoutInflater.inflate(R.layout.item_ayar_kutusu, kutu, false) as LinearLayout
        kutu.addView(k)
        return k
    }

    private fun ayrac(k: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(ContextCompat.getColor(this, TR.color.ayrac))
        val d = resources.displayMetrics.density
        k.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (0.5f * d).toInt().coerceAtLeast(1)).apply { leftMargin = (42 * d).toInt() })
    }

    /** Renk noktası + ad + görünürlük anahtarı; telefondaki takvimde "⋯" menüsü. */
    private fun takvimSatiri(k: LinearLayout, t: Takvim) {
        val d = resources.displayMetrics.density
        val satir = layoutInflater.inflate(TR.layout.item_ayar, k, false)
        AyarSatiri.kur(satir, 0, Renk.yuzey(this, t.renk), t.ad.ifBlank { getString(R.string.basliksiz) },
            if (!t.yazilabilir) getString(R.string.salt_okunur) else null)
        // Büyük ikon kartı yerine küçük renk noktası: satırlar sade, renk yine tanınır.
        satir.findViewById<ImageView>(TR.id.ayarIkon).apply {
            setImageDrawable(null)
            setPadding(0, 0, 0, 0)
            setBackgroundResource(R.drawable.bg_nokta)
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                width = (14 * d).toInt(); height = (14 * d).toInt(); rightMargin = (2 * d).toInt(); leftMargin = (2 * d).toInt()
            }
        }
        AyarSatiri.anahtar(satir, t.gorunur) { acik ->
            yurutucu.execute { TakvimDeposu.gorunurDegistir(this, t.id, acik) }
        }
        if (yerel(t)) {
            val daha = ImageButton(this).apply {
                setImageResource(R.drawable.ic_daha)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@TakvimlerActivity, TR.color.metin_ikincil))
                setBackgroundResource(android.R.color.transparent)
                contentDescription = getString(R.string.takvim_secenekleri, t.ad)
                setOnClickListener { secenekler(t) }
            }
            val grup = satir as LinearLayout
            grup.addView(daha, grup.childCount - 2, LinearLayout.LayoutParams((48 * d).toInt(), (40 * d).toInt()))
        }
        k.addView(satir)
    }

    private fun secenekler(t: Takvim) {
        AltSayfa(this).baslik(t.ad)
            .madde(R.drawable.ic_duzenle, getString(R.string.yeniden_adlandir)) { adlandir(t) }
            .madde(R.drawable.ic_renk, getString(R.string.renk)) {
                RenkSecici.sayfa(this, getString(R.string.renk), t.renk) { renk ->
                    yurutucu.execute { TakvimDeposu.yerelTakvimGuncelle(this, t, renk = renk); runOnUiThread { yukle() } }
                }.goster()
            }
            .madde(R.drawable.ic_sil, getString(R.string.takvimi_sil), tehlikeli = true) { silSor(t) }
            .goster()
    }

    private fun adlandir(t: Takvim) {
        AltSayfa(this).baslik(getString(R.string.yeniden_adlandir))
            .girdi(getString(R.string.takvim_adi), t.ad, getString(R.string.kaydet)) { ad ->
                yurutucu.execute { TakvimDeposu.yerelTakvimGuncelle(this, t, ad = ad); runOnUiThread { yukle() } }
            }
            .goster()
    }

    private fun yeniTakvim() {
        AltSayfa(this).baslik(getString(R.string.yeni_takvim))
            .girdi(getString(R.string.takvim_adi), "", getString(R.string.olustur)) { ad ->
                // Kullanılmayan ilk palet rengi; sonra ⋯ → Renk ile değişir.
                val kullanilan = takvimler.map { it.renk }.toSet()
                val renk = RenkSecici.PALET.firstOrNull { it !in kullanilan } ?: RenkSecici.PALET[takvimler.size % RenkSecici.PALET.size]
                yurutucu.execute {
                    val id = TakvimDeposu.yerelTakvimOlustur(this, ad, renk)
                    runOnUiThread {
                        if (id == null) android.widget.Toast.makeText(this, R.string.takvim_olusmadi, android.widget.Toast.LENGTH_LONG).show()
                        yukle()
                    }
                }
            }
            .goster()
    }

    /** Silmeden önce içindeki etkinlik sayısını söyler; geri alınamaz. */
    private fun silSor(t: Takvim) {
        yurutucu.execute {
            val n = TakvimDeposu.etkinlikSayisi(this, t.id)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val mesaj = if (n == 0) getString(R.string.takvim_sil_bos, t.ad) else getString(R.string.takvim_sil_dolu, t.ad, n)
                AltSayfa(this).mesaj(mesaj)
                    .madde(R.drawable.ic_sil, getString(R.string.takvimi_sil), tehlikeli = true) { sil(t) }
                    .madde(R.drawable.ic_geri, getString(R.string.vazgec)) {}
                    .goster()
            }
        }
    }

    private fun sil(t: Takvim) {
        yurutucu.execute {
            val tamam = TakvimDeposu.yerelTakvimSil(this, t)
            if (tamam && Depo.varsayilanTakvim(this) == t.id) Depo.varsayilanTakvimKaydet(this, 0L)
            runOnUiThread {
                if (!tamam) android.widget.Toast.makeText(this, R.string.takvim_silinemedi, android.widget.Toast.LENGTH_LONG).show()
                yukle()
            }
        }
    }

    // ---- Doğum günleri (rehber izni yalnız açınca istenir) ----

    private fun dogumDegisti(acik: Boolean) {
        DogumGunleri.onbellegiTemizle()
        Depo.dogumGunleriKaydet(this, acik)
        if (acik && !DogumGunleri.izinVar(this)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), IZIN_REHBER)
        }
        ciz()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == IZIN_REHBER && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Depo.dogumGunleriKaydet(this, false)
            android.widget.Toast.makeText(this, R.string.dogum_izin_yok, android.widget.Toast.LENGTH_LONG).show()
        }
        ciz()
    }

    companion object {
        private const val IZIN_REHBER = 31
    }
}
