package com.ekosistem.notlar

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Not listesinin önbelleği. Liste her açılışta her notu okuyup özetini
 * çıkarıyordu; 1.000 notta emülatörde 4-10 saniye sürüyordu. Artık bir notun
 * tarihi ve boyutu değişmediyse önceki başlık/özet kullanılır.
 *
 *   dosya listesi (klasör başına tek sorgu)
 *        │
 *        ├─ tarih+boyut önbellektekiyle aynı ──▶ başlık/özet önbellekten
 *        └─ değişmiş ya da yeni ──────────────▶ dosya okunur, özet çıkarılır
 *
 * Başlık ve özet diske de yazılır ki uygulama soğuk açılışta da dosya
 * okumasın. İçerik (arama, etiket, görev için ilk 8 KB) yalnızca bellekte
 * tutulur; diskte tutmak notların bir kopyasını daha çıkarmak olurdu.
 * Kilit, sabitleme ve korunan tarih burada tutulmaz: her listede güncel
 * ayardan okunur.
 */
class ListeOnbellegi {

    class Kayit(
        val degistirilme: Long,
        val boyut: Long,
        val baslik: String,
        val ozet: String,
        /** İlk [NotDeposu.ONIZLEME_SINIRI] bayt; diskten gelen kayıtta null. */
        @Volatile var icerik: String? = null
    )

    private val kayitlar = ConcurrentHashMap<String, Kayit>()

    /** Diske yazılmamış değişiklik var mı? */
    @Volatile
    var kirli = false
        private set

    fun al(adres: String, degistirilme: Long, boyut: Long): Kayit? {
        val k = kayitlar[adres] ?: return null
        return if (k.degistirilme == degistirilme && k.boyut == boyut) k else null
    }

    fun icerik(adres: String): String? = kayitlar[adres]?.icerik

    fun koy(adres: String, kayit: Kayit) {
        val eski = kayitlar.put(adres, kayit)
        if (eski == null || eski.degistirilme != kayit.degistirilme || eski.boyut != kayit.boyut ||
            eski.baslik != kayit.baslik || eski.ozet != kayit.ozet
        ) kirli = true
    }

    /**
     * Not yazıldı: kayıt atılır. Onay kutusunu işaretlemek boyutu değiştirmez
     * ve bazı dosya sistemlerinde tarih saniye hassasiyetindedir; yalnızca
     * tarih+boyuta güvenilseydi eski özet kalabilirdi.
     */
    fun sil(adres: String) {
        if (kayitlar.remove(adres) != null) kirli = true
    }

    /** Tüm liste tarandıktan sonra artık olmayan notları atar. */
    fun yalnizcaBunlarKalsin(adresler: Set<String>) {
        if (kayitlar.keys.retainAll(adresler)) kirli = true
    }

    val boyut: Int get() = kayitlar.size

    fun temizle() {
        kayitlar.clear()
        kirli = true
    }

    // --- Disk ---

    fun diskeYaz(dosya: File): Boolean {
        val bayt = ByteArrayOutputStream()
        DataOutputStream(bayt).use { d ->
            d.writeInt(SURUM)
            val anlik = kayitlar.entries.toList()
            d.writeInt(anlik.size)
            for ((adres, k) in anlik) {
                d.writeUTF(adres)
                d.writeLong(k.degistirilme)
                d.writeLong(k.boyut)
                d.writeUTF(k.baslik)
                d.writeUTF(k.ozet)
            }
        }
        val tamam = DosyaYazici.atomikYaz(dosya, bayt.toByteArray())
        if (tamam) kirli = false
        return tamam
    }

    /** Bozuk ya da eski biçimli dosya sessizce yok sayılır; liste yeniden kurulur. */
    fun disktenOku(dosya: File) {
        if (!dosya.isFile) return
        try {
            DataInputStream(dosya.inputStream().buffered()).use { d ->
                if (d.readInt() != SURUM) return
                val sayi = d.readInt()
                repeat(sayi) {
                    val adres = d.readUTF()
                    val kayit = Kayit(d.readLong(), d.readLong(), d.readUTF(), d.readUTF())
                    // Bellekte daha yeni bir kayıt varsa ona dokunma.
                    kayitlar.putIfAbsent(adres, kayit)
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val SURUM = 1
    }
}
