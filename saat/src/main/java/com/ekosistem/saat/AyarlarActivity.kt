package com.ekosistem.saat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.AyarSatiri
import com.ekosistem.tasarim.Tasarim
import com.ekosistem.tasarim.R as TR

/** Ayarlar: tema, çalarken ses tuşları, yaklaşan bildirim, susma süresi, güvenilirlik. */
class AyarlarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ayarlar)
        findViewById<View>(R.id.btnGeri).setOnClickListener { finish() }
        satirlariKur()
    }

    override fun onResume() {
        super.onResume()
        satirlariKur()
    }

    private fun satirlariKur() {
        val vurgu = Tasarim.vurgu(this)
        val notr = 0xFF5F5E5A.toInt()
        val kirmizi = ContextCompat.getColor(this, TR.color.tehlike)

        val tema = findViewById<View>(R.id.satirTema)
        AyarSatiri.kur(tema, R.drawable.ic_ayar_gorunum, vurgu, getString(R.string.tema), temaAdi())
        tema.setOnClickListener { temaSec() }

        val sesTusu = findViewById<View>(R.id.satirSesTusu)
        AyarSatiri.kur(sesTusu, R.drawable.ic_ayar_alarm, vurgu, getString(R.string.ses_tuslari), sesTusuAdi(Depo.sesTusu(this)))
        sesTusu.setOnClickListener { sesTusuSec() }

        val yaklasan = findViewById<View>(R.id.satirYaklasan)
        AyarSatiri.kur(yaklasan, R.drawable.ic_ayar_alarm, vurgu, getString(R.string.yaklasan_bildirim), yaklasanAdi(Depo.yaklasanDk(this)))
        yaklasan.setOnClickListener { yaklasanSec() }

        val susma = findViewById<View>(R.id.satirSusma)
        AyarSatiri.kur(susma, R.drawable.ic_ayar_alarm, vurgu, getString(R.string.susma), getString(R.string.susma_ozet, Depo.susmaDk(this)))
        susma.setOnClickListener { susmaSec() }

        val kontrol = findViewById<View>(R.id.satirKontrol)
        val eksik = Kontrol.uyari(this)
        AyarSatiri.kur(
            kontrol, R.drawable.ic_ayar_kalkan, if (eksik != null) kirmizi else ContextCompat.getColor(this, TR.color.tamam),
            getString(R.string.kontrol_baslik),
            eksik?.let { getString(R.string.kontrol_uyari, Kontrol.ad(this, it.tur).lowercase()) }
                ?: getString(R.string.kontrol_tamam)
        )
        kontrol.setOnClickListener { Kontrol.sayfaGoster(this) }

        val dene = findViewById<View>(R.id.satirDene)
        AyarSatiri.kur(dene, R.drawable.ic_ayar_alarm, notr, getString(R.string.alarmi_dene), getString(R.string.alarmi_dene_ozet))
        dene.setOnClickListener { alarmiDene() }

        val saatModu = findViewById<View>(R.id.satirSaatModu)
        AyarSatiri.kur(saatModu, R.drawable.ic_saat_modu, vurgu, getString(R.string.saat_modu), getString(R.string.saat_modu_ozet))
        saatModu.setOnClickListener { startActivity(android.content.Intent(this, SaatModuActivity::class.java)) }

        val koruyucu = findViewById<View>(R.id.satirEkranKoruyucu)
        AyarSatiri.kur(koruyucu, R.drawable.ic_saat_modu, notr, getString(R.string.ekran_koruyucu), getString(R.string.ekran_koruyucu_ozet))
        koruyucu.setOnClickListener {
            // Telefonun ekran koruyucu ayarı; üreticiye göre yoksa genel ayarlara düşer.
            runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)) }
                .onFailure { runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)) } }
        }

        val surum = findViewById<View>(R.id.satirSurum)
        AyarSatiri.kur(
            surum, R.drawable.ic_ayar_bilgi, notr, getString(R.string.surum),
            "${BuildConfig.VERSION_NAME} · ${getString(R.string.izin_yok_rozet)}"
        )
        AyarSatiri.oksuz(surum)
    }

    private fun temaAdi(): String = getString(
        when (Depo.tema(this)) {
            1 -> R.string.tema_acik
            2 -> R.string.tema_siyah
            else -> R.string.tema_sistem
        }
    )

    private fun temaSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.tema))
        listOf(R.string.tema_sistem, R.string.tema_acik, R.string.tema_siyah).forEachIndexed { i, ad ->
            sayfa.madde(R.drawable.ic_ayar_gorunum, getString(ad), secili = Depo.tema(this) == i) {
                Depo.temaKaydet(this, i)
                SaatApp.temaUygula(i)
            }
        }
        sayfa.goster()
    }

    private fun sesTusuAdi(deger: Int): String = getString(
        when (deger) {
            1 -> R.string.ses_tusu_kapat
            2 -> R.string.ses_tusu_hicbiri
            else -> R.string.ses_tusu_ertele
        }
    )

    private fun sesTusuSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.ses_tuslari))
        for (i in 0..2) {
            sayfa.madde(R.drawable.ic_alarm, sesTusuAdi(i), secili = Depo.sesTusu(this) == i) {
                Depo.sesTusuKaydet(this, i)
                satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun yaklasanAdi(dk: Int): String = when {
        dk <= 0 -> getString(R.string.yaklasan_kapali)
        dk < 60 -> getString(R.string.yaklasan_once, getString(R.string.dakika_n, dk))
        else -> getString(R.string.yaklasan_once, getString(R.string.saat_n, dk / 60))
    }

    private fun yaklasanSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.yaklasan_bildirim))
        for (dk in intArrayOf(0, 30, 60, 120)) {
            sayfa.madde(R.drawable.ic_alarm, yaklasanAdi(dk), secili = Depo.yaklasanDk(this) == dk) {
                Depo.yaklasanDkKaydet(this, dk)
                AlarmKurucu.hepsiniKur(this)
                satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun susmaSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.susma))
        for (dk in intArrayOf(5, 10, 15, 30)) {
            sayfa.madde(R.drawable.ic_alarm, getString(R.string.dakika_n, dk), secili = Depo.susmaDk(this) == dk) {
                Depo.susmaDkKaydet(this, dk)
                satirlariKur()
            }
        }
        sayfa.goster()
    }

    /**
     * Bütün yolu dener: sistem alarmı, ön plan hizmeti, kilit ekranında açılma,
     * ses. Ayarlarda bir eksik varsa kullanıcı daha gece olmadan görür.
     */
    private fun alarmiDene() {
        val an = System.currentTimeMillis() + 10_000
        val c = java.util.Calendar.getInstance().apply { timeInMillis = an }
        Depo.yaz(
            this,
            Alarm(
                id = MainActivity.DENEME_ID,
                saat = c.get(java.util.Calendar.HOUR_OF_DAY),
                dakika = c.get(java.util.Calendar.MINUTE),
                etiket = getString(R.string.deneme),
                kademeliSn = 0,
                ertelemeSiniri = 1,
                kurulanZaman = an
            )
        )
        AlarmKurucu.hepsiniKur(this)
        Toast.makeText(this, R.string.deneme_kuruldu, Toast.LENGTH_LONG).show()
    }
}
