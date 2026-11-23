package com.ekosistem.galeri

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

/** Bir ekranın gösterdiği durum: tek yönlü akış, ekran yalnız bunu çizer. */
sealed class MedyaDurumu {
    object Yukleniyor : MedyaDurumu()
    object IzinYok : MedyaDurumu()
    data class Hazir(val medyalar: List<Medya>, val albumler: List<Album>) : MedyaDurumu()
}

/**
 * Ekran döndürülünce ya da tema değişince liste yeniden sorgulanmaz; durum burada yaşar.
 * Veriyi [MedyaDeposu] verir, sorgu arka planda.
 */
class MedyaViewModel(uygulama: Application) : AndroidViewModel(uygulama) {

    private val yurutucu = Executors.newSingleThreadExecutor()
    private val _durum = MutableLiveData<MedyaDurumu>(MedyaDurumu.Yukleniyor)
    val durum: LiveData<MedyaDurumu> = _durum
    @Volatile private var nesil = 0
    private var kaynak: Kaynak = Kaynak.Hepsi
    private var albumlerGerekli = false

    /** [kaynak] listesini yükler; [taze] ise medya deposu yeniden okunur. */
    fun yukle(kaynak: Kaynak, albumler: Boolean = false, taze: Boolean = false) {
        val c = getApplication<Application>()
        this.kaynak = kaynak
        albumlerGerekli = albumler
        if (!Izin.okunabilir(c)) {
            _durum.value = MedyaDurumu.IzinYok
            return
        }
        // İlk açılışta "yükleniyor"; yenilemede eski liste ekranda kalsın, titremesin.
        if (_durum.value !is MedyaDurumu.Hazir) _durum.value = MedyaDurumu.Yukleniyor
        val n = ++nesil
        yurutucu.execute {
            val liste = MedyaDeposu.kaynaktan(c, kaynak, taze)
            val a = if (albumler) Albumler.kur(MedyaDeposu.onbellek ?: liste) else emptyList()
            if (n == nesil) _durum.postValue(MedyaDurumu.Hazir(liste, a))
        }
    }

    fun yenile(taze: Boolean = true) = yukle(kaynak, albumlerGerekli, taze)

    override fun onCleared() {
        yurutucu.shutdown()
    }
}
