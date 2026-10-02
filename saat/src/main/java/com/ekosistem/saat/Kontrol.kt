package com.ekosistem.saat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ekosistem.tasarim.AltSayfa
import com.ekosistem.tasarim.R as TR

/**
 * "Alarmın kesin çalması için": alarmı etkileyen telefon ayarları. Zorunlu
 * olanlardan biri eksikse alarm listesinin üstünde uyarı çıkar; pil
 * kısıtlaması yalnız uygulamaları öldürdüğü bilinen üreticilerde uyarılır.
 */
object Kontrol {

    enum class Tur { BILDIRIM, TAM_EKRAN, TAM_ZAMANLI, PIL }

    data class Madde(val tur: Tur, val tamam: Boolean, val zorunlu: Boolean)

    private val SERT_URETICILER = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "vivo",
        "iqoo", "meizu", "samsung", "asus", "tecno", "infinix"
    )

    private val uretici: String get() = Build.MANUFACTURER.lowercase()

    fun maddeler(context: Context): List<Madde> {
        val liste = mutableListOf(Madde(Tur.BILDIRIM, Bildirimler.izinVar(context), true))
        if (Build.VERSION.SDK_INT >= 34) {
            liste.add(Madde(Tur.TAM_EKRAN, Bildirimler.tamEkranVar(context), true))
        }
        if (Build.VERSION.SDK_INT >= 31) {
            liste.add(Madde(Tur.TAM_ZAMANLI, AlarmKurucu.tamZamanliKurulabilir(context), true))
        }
        if (Build.VERSION.SDK_INT >= 23) {
            val guc = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            liste.add(
                Madde(Tur.PIL, guc.isIgnoringBatteryOptimizations(context.packageName), uretici in SERT_URETICILER)
            )
        }
        return liste
    }

    /** Liste üstündeki uyarıda gösterilecek ilk eksik; yoksa null. */
    fun uyari(context: Context): Madde? = maddeler(context).firstOrNull { !it.tamam && it.zorunlu }

    fun ad(context: Context, tur: Tur): String = context.getString(
        when (tur) {
            Tur.BILDIRIM -> R.string.k_bildirim
            Tur.TAM_EKRAN -> R.string.k_tam_ekran
            Tur.TAM_ZAMANLI -> R.string.k_tam_zamanli
            Tur.PIL -> R.string.k_pil
        }
    )

    private fun ozet(context: Context, m: Madde): String = when (m.tur) {
        Tur.BILDIRIM -> context.getString(R.string.k_bildirim_ozet)
        Tur.TAM_EKRAN -> context.getString(R.string.k_tam_ekran_ozet)
        Tur.TAM_ZAMANLI -> context.getString(R.string.k_tam_zamanli_ozet)
        Tur.PIL -> if (m.tamam) context.getString(R.string.k_pil_tamam) else pilYolu(context)
    }

    private fun pilYolu(context: Context): String = context.getString(
        when (uretici) {
            "xiaomi", "redmi", "poco" -> R.string.pil_xiaomi
            "huawei", "honor" -> R.string.pil_huawei
            "samsung" -> R.string.pil_samsung
            "oppo", "realme", "oneplus" -> R.string.pil_oppo
            "vivo", "iqoo" -> R.string.pil_vivo
            else -> R.string.pil_genel
        }
    )

    /** Eksik ayarı düzeltecek sistem sayfasını açar. */
    fun duzelt(activity: Activity, tur: Tur) {
        val paket = Uri.parse("package:" + activity.packageName)
        val niyet = when (tur) {
            Tur.BILDIRIM -> {
                if (Build.VERSION.SDK_INT >= 33 && !Depo.bildirimIstendi(activity) &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Depo.bildirimIstendiKaydet(activity)
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    return
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paket)
                }
            }
            Tur.TAM_EKRAN -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, paket)
            } else {
                return
            }
            Tur.TAM_ZAMANLI -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, paket)
            } else {
                return
            }
            Tur.PIL -> if (Build.VERSION.SDK_INT >= 23) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                return
            }
        }
        // Bazı üreticiler sayfayı kaldırmış olabilir: uygulama bilgisine düş.
        runCatching { activity.startActivity(niyet) }.onFailure {
            runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paket)) }
        }
    }

    fun sayfaGoster(activity: Activity) {
        val maddeler = maddeler(activity)
        val eksikVar = maddeler.any { !it.tamam }
        val sayfa = AltSayfa(activity)
            .baslik(activity.getString(R.string.kontrol_baslik))
            .mesaj(activity.getString(if (eksikVar) R.string.kontrol_eksik else R.string.kontrol_tamam))
        val kutu = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val d = activity.resources.displayMetrics.density
        for (m in maddeler) kutu.addView(satir(activity, m, d) { sayfa.kapat(); duzelt(activity, m.tur) })
        sayfa.icerik(kutu)
            .madde(TR.drawable.ic_onay_isaret, activity.getString(TR.string.kapat)) {}
            .goster()
    }

    private fun satir(activity: Activity, m: Madde, d: Float, tikla: () -> Unit): LinearLayout {
        val satir = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            if (!m.tamam) {
                isClickable = true
                setOnClickListener { tikla() }
            }
        }
        val renk = ContextCompat.getColor(activity, if (m.tamam) TR.color.tamam else TR.color.tehlike)
        val ikon = ImageView(activity).apply {
            setImageResource(if (m.tamam) TR.drawable.ic_onay_isaret else R.drawable.ic_unlem)
            imageTintList = ColorStateList.valueOf(renk)
            setBackgroundResource(R.drawable.bg_daire)
            backgroundTintList = ColorStateList.valueOf((renk and 0x00FFFFFF) or 0x24000000)
            val p = (7 * d).toInt()
            setPadding(p, p, p, p)
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        satir.addView(ikon, LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()))
        val yazilar = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        yazilar.addView(TextView(activity).apply {
            text = ad(activity, m.tur)
            textSize = 15f
            setTextColor(ContextCompat.getColor(activity, TR.color.metin))
        })
        yazilar.addView(TextView(activity).apply {
            text = ozet(activity, m)
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, TR.color.metin_ikincil))
        })
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = (14 * d).toInt()
        satir.addView(yazilar, lp)
        return satir
    }
}
