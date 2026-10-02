package com.ekosistem.saat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Alarm sesi ve titreşimi. Kurallar (arastirma/saat-rakipler.md §3):
 * - Ses **alarm kanalından** çıkar; telefon sessiz ya da titreşimdeyken de duyulur.
 * - Seçilen ses açılamazsa (dosya silinmiş, kilit açılmadan medya okunamıyor)
 *   uygulamanın kendi ürettiği ses çalar. Alarm hiçbir koşulda sessiz kalmaz.
 * - Alarm ses düzeyi sıfırsa çalarken yarıya çıkarılır, bitince geri alınır.
 * - Telefonda konuşuluyorsa kısık çalar, görüşmeyi bastırmaz.
 */
class AlarmSesi(private val context: Context) {

    private val ses = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val titresici = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val isleyici = Handler(Looper.getMainLooper())

    private var oynatici: MediaPlayer? = null
    private var yerlesik: AudioTrack? = null
    private var eskiDuzey = -1
    private var odak: AudioFocusRequest? = null
    private var baslangic = 0L
    private var kademeliMs = 0L
    private var tavan = 1f

    private val nitelikler: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val yukselt = object : Runnable {
        override fun run() {
            val gecen = SystemClock.elapsedRealtime() - baslangic
            val oran = if (kademeliMs <= 0) 1f else (gecen.toFloat() / kademeliMs).coerceIn(0f, 1f)
            // Kulak sesi doğrusal duymaz: kare alınınca yükseliş eşit hissedilir.
            val duzey = (BASLANGIC_DUZEYI + (1f - BASLANGIC_DUZEYI) * oran * oran) * tavan
            oynatici?.setVolume(duzey, duzey)
            yerlesik?.setVolume(duzey)
            if (oran < 1f) isleyici.postDelayed(this, ADIM_MS)
        }
    }

    fun baslat(alarm: Alarm) {
        val gorusmede = ses.mode == AudioManager.MODE_IN_CALL ||
            ses.mode == AudioManager.MODE_IN_COMMUNICATION
        tavan = if (gorusmede) GORUSME_DUZEYI else 1f
        if (!gorusmede && ses.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            eskiDuzey = 0
            runCatching {
                ses.setStreamVolume(
                    AudioManager.STREAM_ALARM, ses.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0
                )
            }
        }
        odakAl()
        baslangic = SystemClock.elapsedRealtime()
        kademeliMs = alarm.kademeliSn * 1000L
        if (alarm.ses == Alarm.SES_YERLESIK || !dosyaCal(alarm.ses)) yerlesikCal()
        isleyici.post(yukselt)
        if (alarm.titresim) titret()
    }

    private fun sesAdresi(secim: String): Uri? =
        if (secim.isNotEmpty()) {
            Uri.parse(secim)
        } else {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

    private fun dosyaCal(secim: String): Boolean {
        val adres = sesAdresi(secim) ?: return false
        return try {
            val o = MediaPlayer()
            o.setAudioAttributes(nitelikler)
            o.setDataSource(context, adres)
            o.isLooping = true
            o.setVolume(0f, 0f)
            o.setOnErrorListener { _, _, _ ->
                // Çalarken bozulursa yerleşik sese geç.
                oynatici?.release()
                oynatici = null
                yerlesikCal()
                true
            }
            o.prepare()
            o.start()
            oynatici = o
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Kendi ürettiğimiz iki tonlu çan: dosya gerektirmez, her koşulda çalar. */
    private fun yerlesikCal() {
        if (yerlesik != null) return
        val ornekler = cinOrnekleri()
        val bicim = AudioFormat.Builder()
            .setSampleRate(ORNEK_HIZI)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        try {
            val iz = AudioTrack(
                nitelikler, bicim, ornekler.size * 2, AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            iz.write(ornekler, 0, ornekler.size)
            iz.setLoopPoints(0, ornekler.size, -1)
            iz.setVolume(BASLANGIC_DUZEYI * tavan)
            iz.play()
            yerlesik = iz
        } catch (_: Exception) {
            // Ses aygıtı hiç açılamıyorsa titreşim ve ekran kalır.
        }
    }

    private fun titret() {
        val v = titresici ?: return
        if (!v.hasVibrator()) return
        val desen = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(desen, 0), nitelikler)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(desen, 0, nitelikler)
        }
    }

    private fun odakAl() {
        if (Build.VERSION.SDK_INT >= 26) {
            val istek = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(nitelikler)
                .setOnAudioFocusChangeListener { }
                .build()
            odak = istek
            ses.requestAudioFocus(istek)
        } else {
            @Suppress("DEPRECATION")
            ses.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    fun durdur() {
        isleyici.removeCallbacks(yukselt)
        oynatici?.let { runCatching { it.stop() }; it.release() }
        oynatici = null
        yerlesik?.let { runCatching { it.stop() }; it.release() }
        yerlesik = null
        titresici?.cancel()
        if (Build.VERSION.SDK_INT >= 26) {
            odak?.let { ses.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            ses.abandonAudioFocus(null)
        }
        odak = null
        if (eskiDuzey >= 0) {
            runCatching { ses.setStreamVolume(AudioManager.STREAM_ALARM, eskiDuzey, 0) }
            eskiDuzey = -1
        }
    }

    companion object {
        private const val ORNEK_HIZI = 22_050
        private const val ADIM_MS = 250L
        private const val BASLANGIC_DUZEYI = 0.06f
        private const val GORUSME_DUZEYI = 0.15f

        /** 1,4 sn'lik döngü: iki kısa çan ve sessizlik. */
        fun cinOrnekleri(): ShortArray {
            val toplam = (ORNEK_HIZI * 1.4).toInt()
            val dizi = ShortArray(toplam)
            fun cin(baslaSn: Double, frekans: Double) {
                val bas = (baslaSn * ORNEK_HIZI).toInt()
                val uzunluk = (0.45 * ORNEK_HIZI).toInt()
                for (i in 0 until uzunluk) {
                    val t = i.toDouble() / ORNEK_HIZI
                    val zarf = exp(-t * 7.0) * minOf(1.0, t * 400.0)
                    val deger = zarf * (0.7 * sin(2 * PI * frekans * t) + 0.3 * sin(2 * PI * frekans * 2.0 * t))
                    val j = bas + i
                    if (j < toplam) dizi[j] = (dizi[j] + deger * 26_000).toInt().coerceIn(-32_767, 32_767).toShort()
                }
            }
            cin(0.0, 880.0)
            cin(0.22, 1318.5)
            return dizi
        }
    }
}
