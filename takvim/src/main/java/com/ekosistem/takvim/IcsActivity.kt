package com.ekosistem.takvim

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.AyarSatiri
import com.ekosistem.tasarim.Tasarim
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * `.ics` içe aktarma: dosyayı okur, ne kadar etkinlik bulduğunu söyler, hedef takvimi seçtirir ve yazar.
 * Başka uygulamadan ("Takvim ile aç"/paylaş) ya da Ayarlar'dan seçilen dosyayla açılır.
 */
class IcsActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private var liste: List<IcsEtkinlik> = emptyList()
    private var takvimler: List<Takvim> = emptyList()
    private var hedef = 0L
    private var calisiyor = false
    private lateinit var ozet: TextView
    private lateinit var alt: TextView
    private lateinit var dugme: TextView
    private lateinit var ilerleme: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ics)
        ozet = findViewById(R.id.icsOzet)
        alt = findViewById(R.id.icsAlt)
        dugme = findViewById(R.id.btnIceAktar)
        ilerleme = findViewById(R.id.icsIlerleme)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        val vurgu = Tasarim.vurgu(this)
        dugme.backgroundTintList = ColorStateList.valueOf(vurgu)
        dugme.setTextColor(Tasarim.vurguUzeri(this))
        dugme.setOnClickListener { baslat() }

        ozet.setText(R.string.ics_okunuyor)
        if (!TakvimDeposu.izinVar(this)) {
            ozet.setText(R.string.izin_baslik)
            alt.setText(R.string.izin_aciklama)
            return
        }
        val uri = veri()
        if (uri == null) {
            ozet.setText(R.string.ics_bulunamadi)
            return
        }
        yurutucu.execute {
            val metin = oku(uri)
            val bulunan = metin?.let { Ics.oku(it, TimeZone.getDefault()) }.orEmpty()
            val t = TakvimDeposu.takvimler(this).filter { it.yazilabilir }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                liste = bulunan
                takvimler = t
                hazir()
            }
        }
    }

    private fun veri(): Uri? {
        val i = intent
        i.data?.let { return it }
        @Suppress("DEPRECATION")
        return if (i.action == Intent.ACTION_SEND) i.getParcelableExtra(Intent.EXTRA_STREAM) else null
    }

    /** En çok 20 MB okunur: büyük yedeklerin tamamı belleğe sığmayabilir. */
    private fun oku(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { girdi ->
            val tampon = java.io.ByteArrayOutputStream()
            val parca = ByteArray(16 * 1024)
            var toplam = 0
            while (true) {
                val n = girdi.read(parca)
                if (n < 0) break
                toplam += n
                if (toplam > 20 * 1024 * 1024) return@use null
                tampon.write(parca, 0, n)
            }
            tampon.toString("UTF-8")
        }
    } catch (_: Exception) {
        null
    }

    private fun hazir() {
        if (liste.isEmpty()) {
            ozet.setText(R.string.ics_bos)
            alt.setText(R.string.ics_bos_aciklama)
            return
        }
        if (takvimler.isEmpty()) {
            ozet.setText(R.string.yazilabilir_yok)
            return
        }
        val ana = liste.count { it.oncekiOrnek == null }
        ozet.text = getString(R.string.ics_n_etkinlik, ana)
        val tz = TimeZone.getDefault()
        val ilk = liste.minOf { if (it.tumGun) Gun.utcGun(it.baslangic) else Gun.yerelGun(it.baslangic, tz) }
        val son = liste.maxOf { if (it.tumGun) Gun.utcGun(it.baslangic) else Gun.yerelGun(it.baslangic, tz) }
        alt.text = getString(R.string.ics_aralik, Metinler.tamTarihKisa(ilk), Metinler.tamTarihKisa(son)) +
            "\n" + getString(R.string.ics_tekrar_notu)
        val tercih = Depo.varsayilanTakvim(this)
        hedef = (takvimler.firstOrNull { it.id == tercih } ?: takvimler.first()).id
        findViewById<View>(R.id.icsKutu).visibility = View.VISIBLE
        dugme.visibility = View.VISIBLE
        hedefiYaz()
    }

    private fun hedefiYaz() {
        val satir = findViewById<View>(R.id.satirHedef)
        val t = takvimler.firstOrNull { it.id == hedef }
        AyarSatiri.kur(satir, R.drawable.ic_takvim, t?.renk ?: Tasarim.vurgu(this), getString(R.string.ics_hedef), t?.ad.orEmpty())
        satir.setOnClickListener {
            if (calisiyor) return@setOnClickListener
            val s = AltSayfa(this).baslik(getString(R.string.ics_hedef))
            for (k in takvimler) s.madde(R.drawable.ic_takvim, k.ad, secili = k.id == hedef) { hedef = k.id; hedefiYaz() }
            s.goster()
        }
    }

    private fun baslat() {
        if (calisiyor) return
        calisiyor = true
        dugme.alpha = 0.5f
        ilerleme.visibility = View.VISIBLE
        ilerleme.max = liste.size
        yurutucu.execute {
            val s = IcsDeposu.iceAktar(this, hedef, liste) { yapilan, toplam ->
                if (yapilan % 5 == 0 || yapilan == toplam) runOnUiThread { ilerleme.max = toplam; ilerleme.progress = yapilan }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                calisiyor = false
                ilerleme.visibility = View.GONE
                dugme.visibility = View.GONE
                findViewById<View>(R.id.icsKutu).visibility = View.GONE
                ozet.text = getString(R.string.ics_bitti, s.eklenen)
                alt.text = buildString {
                    if (s.atlanan > 0) append(getString(R.string.ics_atlanan, s.atlanan)).append('\n')
                    if (s.hatali > 0) append(getString(R.string.ics_hatali, s.hatali))
                }.trim()
                // Ana ekran depo değişince kendiliğinden yenilenir; kullanıcı "Geri"yle döner.
            }
        }
    }
}
