package com.ekosistem.takvim

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.AyarSatiri
import com.ekosistem.tasarim.Tasarim
import java.util.concurrent.Executors
import com.ekosistem.tasarim.R as TR

/** Ayarlar: görünüm, takvimlerin görünürlüğü, yeni etkinlik varsayılanları, bildirim ve izin durumu. */
class AyarlarActivity : AppCompatActivity() {

    private val yurutucu = Executors.newSingleThreadExecutor()

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

        val baslangic = findViewById<View>(R.id.satirBaslangicGorunum)
        AyarSatiri.kur(baslangic, R.drawable.ic_ay, vurgu, getString(R.string.baslangic_gorunumu), gorunumAdi(Depo.baslangicGorunumu(this)))
        baslangic.setOnClickListener { baslangicGorunumuSec() }

        val haftaBasi = findViewById<View>(R.id.satirHaftaBasi)
        AyarSatiri.kur(haftaBasi, R.drawable.ic_hafta, vurgu, getString(R.string.hafta_basi), haftaBasiAdi(Depo.haftaBasiAyari(this)))
        haftaBasi.setOnClickListener { haftaBasiSec() }

        val haftaNo = findViewById<View>(R.id.satirHaftaNo)
        AyarSatiri.kur(haftaNo, R.drawable.ic_gun, vurgu, getString(R.string.hafta_numaralari), null)
        AyarSatiri.anahtar(haftaNo, Depo.haftaNumaralari(this)) { Depo.haftaNumaralariKaydet(this, it) }

        takvimleriKur(vurgu)

        val varTakvim = findViewById<View>(R.id.satirVarsayilanTakvim)
        AyarSatiri.kur(varTakvim, R.drawable.ic_takvim, vurgu, getString(R.string.varsayilan_takvim), getString(R.string.yukleniyor))
        yurutucu.execute {
            val t = TakvimDeposu.takvimler(this).filter { it.yazilabilir }
            val tercih = Depo.varsayilanTakvim(this)
            val ad = t.firstOrNull { it.id == tercih }?.ad ?: getString(R.string.otomatik)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                AyarSatiri.ozet(varTakvim, ad)
                varTakvim.setOnClickListener { varsayilanTakvimSec(t) }
            }
        }

        val sure = findViewById<View>(R.id.satirVarsayilanSure)
        AyarSatiri.kur(sure, R.drawable.ic_saat, vurgu, getString(R.string.varsayilan_sure), Metinler.sure(this, Depo.varsayilanSure(this)))
        sure.setOnClickListener { sureSec() }

        val hat = findViewById<View>(R.id.satirVarsayilanHatirlatma)
        AyarSatiri.kur(hat, R.drawable.ic_zil, vurgu, getString(R.string.varsayilan_hatirlatma), hatirlatmaAdi(Depo.varsayilanHatirlatma(this)))
        hat.setOnClickListener { hatirlatmaSec() }

        val ert = findViewById<View>(R.id.satirErteleme)
        AyarSatiri.kur(ert, R.drawable.ic_zil, vurgu, getString(R.string.erteleme), Metinler.sure(this, Depo.ertelemeDk(this)))
        ert.setOnClickListener { ertelemeSec() }

        val bildirim = findViewById<View>(R.id.satirBildirim)
        val acik = NotificationManagerCompat.from(this).areNotificationsEnabled()
        AyarSatiri.kur(
            bildirim, R.drawable.ic_zil, if (acik) ContextCompat.getColor(this, TR.color.tamam) else kirmizi,
            getString(R.string.bildirimler),
            getString(if (acik) R.string.bildirim_acik_ozet else R.string.bildirim_kapali_ozet)
        )
        bildirim.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }

        val izin = findViewById<View>(R.id.satirIzin)
        val izinVar = TakvimDeposu.izinVar(this)
        AyarSatiri.kur(
            izin, R.drawable.ic_ayar_kalkan, if (izinVar) ContextCompat.getColor(this, TR.color.tamam) else kirmizi,
            getString(R.string.takvim_izni), getString(if (izinVar) R.string.izin_var_ozet else R.string.izin_yok_ozet)
        )
        izin.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }

        val ice = findViewById<View>(R.id.satirIceAktar)
        AyarSatiri.kur(ice, R.drawable.ic_ayar_ice, vurgu, getString(R.string.ice_aktar_satir), getString(R.string.ice_aktar_ozet))
        ice.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), ISTEK_ICE
            )
        }
        val disa = findViewById<View>(R.id.satirDisaAktar)
        AyarSatiri.kur(disa, R.drawable.ic_ayar_disa, vurgu, getString(R.string.disa_aktar_satir), getString(R.string.disa_aktar_ozet))
        disa.setOnClickListener { disaAktarSec() }

        val surum = findViewById<View>(R.id.satirSurum)
        AyarSatiri.kur(surum, R.drawable.ic_ayar_bilgi, notr, getString(R.string.surum), "${BuildConfig.VERSION_NAME} · ${getString(R.string.izin_yok_rozet)}")
        AyarSatiri.oksuz(surum)
    }

    // ---- Takvimler ----

    private fun takvimleriKur(vurgu: Int) {
        val kutu = findViewById<LinearLayout>(R.id.takvimKutusu)
        yurutucu.execute {
            val liste = TakvimDeposu.takvimler(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                kutu.removeAllViews()
                if (liste.isEmpty()) {
                    val satir = layoutInflater.inflate(TR.layout.item_ayar, kutu, false)
                    AyarSatiri.kur(satir, R.drawable.ic_takvim, vurgu, getString(R.string.takvim_yok_baslik), getString(R.string.takvimler_bos_ozet))
                    AyarSatiri.oksuz(satir)
                    kutu.addView(satir)
                    return@runOnUiThread
                }
                liste.forEachIndexed { i, t ->
                    if (i > 0) kutu.addView(layoutInflater.inflate(R.layout.item_ayrac, kutu, false))
                    val satir = layoutInflater.inflate(TR.layout.item_ayar, kutu, false)
                    val ozet = listOfNotNull(t.hesap.takeIf { it.isNotEmpty() && it != t.ad && t.hesapTuru != android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL }, if (!t.yazilabilir) getString(R.string.salt_okunur) else null).joinToString(" · ")
                    AyarSatiri.kur(satir, R.drawable.ic_takvim, t.renk, t.ad, ozet)
                    satir.findViewById<ImageView>(TR.id.ayarIkon).imageTintList = ColorStateList.valueOf(Tasarim.uzerindekiRenk(t.renk))
                    AyarSatiri.anahtar(satir, t.gorunur) { acik ->
                        yurutucu.execute { TakvimDeposu.gorunurDegistir(this, t.id, acik) }
                    }
                    kutu.addView(satir)
                }
            }
        }
    }

    private fun varsayilanTakvimSec(yazilabilir: List<Takvim>) {
        val sayfa = AltSayfa(this).baslik(getString(R.string.varsayilan_takvim))
        val tercih = Depo.varsayilanTakvim(this)
        sayfa.madde(R.drawable.ic_takvim, getString(R.string.otomatik), secili = tercih == 0L) {
            Depo.varsayilanTakvimKaydet(this, 0L); satirlariKur()
        }
        for (t in yazilabilir) {
            sayfa.madde(R.drawable.ic_takvim, t.ad, secili = tercih == t.id) {
                Depo.varsayilanTakvimKaydet(this, t.id); satirlariKur()
            }
        }
        sayfa.goster()
    }

    // ---- Seçimler ----

    private fun temaAdi() = getString(
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
                TakvimApp.temaUygula(i)
            }
        }
        sayfa.goster()
    }

    private fun gorunumAdi(g: Int) = getString(
        when (g) {
            Depo.GORUNUM_HAFTA -> R.string.gorunum_hafta
            Depo.GORUNUM_GUN -> R.string.gorunum_gun
            Depo.GORUNUM_GUNDEM -> R.string.gorunum_gundem
            else -> R.string.gorunum_ay
        }
    )

    private fun baslangicGorunumuSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.baslangic_gorunumu))
        val ikonlar = listOf(R.drawable.ic_ay, R.drawable.ic_hafta, R.drawable.ic_gun, R.drawable.ic_gundem)
        for (g in 0..3) {
            sayfa.madde(ikonlar[g], gorunumAdi(g), secili = Depo.baslangicGorunumu(this) == g) {
                Depo.baslangicGorunumuKaydet(this, g); satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun haftaBasiAdi(v: Int) = when (v) {
        0 -> Metinler.haftaGunuUzun(0)
        5 -> Metinler.haftaGunuUzun(5)
        6 -> Metinler.haftaGunuUzun(6)
        else -> getString(R.string.sistem_ayari)
    }

    private fun haftaBasiSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.hafta_basi))
        for (v in intArrayOf(-1, 0, 6, 5)) {
            sayfa.madde(R.drawable.ic_hafta, haftaBasiAdi(v), secili = Depo.haftaBasiAyari(this) == v) {
                Depo.haftaBasiKaydet(this, v); satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun sureSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.varsayilan_sure))
        for (dk in intArrayOf(15, 30, 45, 60, 90, 120)) {
            sayfa.madde(R.drawable.ic_saat, Metinler.sure(this, dk), secili = Depo.varsayilanSure(this) == dk) {
                Depo.varsayilanSureKaydet(this, dk); satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun hatirlatmaAdi(dk: Int) = if (dk < 0) getString(R.string.hatirlatma_yok) else Metinler.hatirlatici(this, dk, false)

    private fun hatirlatmaSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.varsayilan_hatirlatma))
        for (dk in intArrayOf(-1, 0, 5, 10, 15, 30, 60)) {
            sayfa.madde(R.drawable.ic_zil, hatirlatmaAdi(dk), secili = Depo.varsayilanHatirlatma(this) == dk) {
                Depo.varsayilanHatirlatmaKaydet(this, dk); satirlariKur()
            }
        }
        sayfa.goster()
    }

    private fun ertelemeSec() {
        val sayfa = AltSayfa(this).baslik(getString(R.string.erteleme))
        for (dk in intArrayOf(5, 10, 15, 30, 60)) {
            sayfa.madde(R.drawable.ic_zil, Metinler.sure(this, dk), secili = Depo.ertelemeDk(this) == dk) {
                Depo.ertelemeDkKaydet(this, dk); satirlariKur()
            }
        }
        sayfa.goster()
    }

    // ---- .ics içe/dışa aktarma ----

    private var disaAktarIdleri: List<Long> = emptyList()

    private fun disaAktarSec() {
        yurutucu.execute {
            val liste = TakvimDeposu.takvimler(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val sayfa = AltSayfa(this).baslik(getString(R.string.disa_baslik))
                if (liste.size > 1) sayfa.madde(R.drawable.ic_takvim, getString(R.string.tum_takvimler)) { dosyaSec(liste.map { it.id }) }
                for (t in liste) sayfa.madde(R.drawable.ic_takvim, t.ad) { dosyaSec(listOf(t.id)) }
                sayfa.goster()
            }
        }
    }

    private fun dosyaSec(idler: List<Long>) {
        disaAktarIdleri = idler
        val tarih = Metinler.dosyaTarihi(System.currentTimeMillis())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/calendar")
                .putExtra(Intent.EXTRA_TITLE, "takvim-$tarih.ics"),
            ISTEK_DISA
        )
    }

    @Deprecated("startActivityForResult ile birlikte")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            ISTEK_ICE -> startActivity(Intent(this, IcsActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            ISTEK_DISA -> yurutucu.execute {
                val etkinlikler = IcsDeposu.disaAktar(this, disaAktarIdleri)
                val metin = Ics.yaz(etkinlikler, System.currentTimeMillis())
                val yazildi = try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(metin.toByteArray(Charsets.UTF_8)) } != null
                } catch (_: Exception) {
                    false
                }
                runOnUiThread {
                    val mesaj = when {
                        !yazildi -> getString(R.string.disa_hata)
                        etkinlikler.isEmpty() -> getString(R.string.disa_bos)
                        else -> getString(R.string.disa_bitti, etkinlikler.count { it.oncekiOrnek == null })
                    }
                    android.widget.Toast.makeText(this, mesaj, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ISTEK_ICE = 21
        private const val ISTEK_DISA = 22
    }
}
