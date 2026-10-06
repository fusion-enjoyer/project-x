package com.ekosistem.notlar

import java.io.ByteArrayInputStream
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
 *
 * Ayrıca son "Tümü" listesinde hangi notların olduğu ([anaListe]) saklanır:
 * uygulama soğuk açılınca liste, klasörler taranmadan önce buradan hemen
 * gösterilir, gerçek tarama bitince yerine geçer.
 */
class ListeOnbellegi {

    class Kayit(
        val degistirilme: Long,
        val boyut: Long,
        val baslik: String,
        val ozet: String,
        /** İlk [NotDeposu.ONIZLEME_SINIRI] bayt; diskten gelen kayıtta null. */
        @Volatile var icerik: String? = null,
        /** Onay kutusu sayısı ve işaretli olanlar (kart rozeti "3/7"). */
        val gorev: Int = 0,
        val biten: Int = 0
    )

    /** Ana listedeki bir notun, önizleme dışında kalan bilgisi. */
    data class AnaGirdi(val ad: String, val klasor: String?)

    private val kayitlar = ConcurrentHashMap<String, Kayit>()

    @Volatile
    private var anaListe: Map<String, AnaGirdi> = emptyMap()

    /** Diske yazılmamış değişiklik var mı? */
    @Volatile
    var kirli = false
        private set

    fun al(adres: String, degistirilme: Long, boyut: Long): Kayit? {
        val k = kayitlar[adres] ?: return null
        return if (k.degistirilme == degistirilme && k.boyut == boyut) k else null
    }

    fun icerik(adres: String): String? = kayitlar[adres]?.icerik

    /** Tarih/boyut denetimi olmadan; yalnızca geçici (anlık) liste için. */
    fun kayit(adres: String): Kayit? = kayitlar[adres]

    fun anaListe(): Map<String, AnaGirdi> = anaListe

    fun anaListeyiYaz(yeni: Map<String, AnaGirdi>) {
        if (yeni == anaListe) return
        anaListe = yeni
        kirli = true
    }

    fun koy(adres: String, kayit: Kayit) {
        val eski = kayitlar.put(adres, kayit)
        if (eski == null || eski.degistirilme != kayit.degistirilme || eski.boyut != kayit.boyut ||
            eski.baslik != kayit.baslik || eski.ozet != kayit.ozet ||
            eski.gorev != kayit.gorev || eski.biten != kayit.biten
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
        anaListe = emptyMap()
        kirli = true
    }

    // --- Disk ---

    /*
     * Dosya biçimi (sürüm 4):
     *   sürüm, ortak önek, kayıt sayısı,
     *   her kayıt: adres (önekten sonrası), tarih, boyut, başlık, özet,
     *              görev sayısı, biten görev,
     *              ana listede mi, [ad, klasör var mı, klasör]
     * Seçilen klasörde her adres ~130 karakterlik aynı başlangıçla gelir;
     * önek bir kez yazılır, adres de iki kez yazılmaz. 1.000 notta dosya
     * 518 KB'tan yarısına iner, soğuk açılışta okuma süresi de onunla.
     */
    fun diskeYaz(dosya: File): Boolean {
        val anlik = kayitlar.entries.map { it.key to it.value }
        val ana = anaListe
        val onek = ortakOnek(anlik.map { it.first })
        val bayt = ByteArrayOutputStream()
        DataOutputStream(bayt).use { d ->
            d.writeInt(SURUM)
            d.writeUTF(onek)
            d.writeInt(anlik.size)
            for ((adres, k) in anlik) {
                d.writeUTF(adres.substring(onek.length))
                d.writeLong(k.degistirilme)
                d.writeLong(k.boyut)
                d.writeUTF(k.baslik)
                d.writeUTF(k.ozet)
                d.writeShort(k.gorev.coerceAtMost(Short.MAX_VALUE.toInt()))
                d.writeShort(k.biten.coerceAtMost(Short.MAX_VALUE.toInt()))
                val g = ana[adres]
                d.writeBoolean(g != null)
                if (g != null) {
                    d.writeUTF(g.ad)
                    d.writeBoolean(g.klasor != null)
                    d.writeUTF(g.klasor ?: "")
                }
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
            // Tek seferde okunur; akıştan küçük parçalarla okumak eski telefonda yavaş.
            DataInputStream(ByteArrayInputStream(dosya.readBytes())).use { d ->
                if (d.readInt() != SURUM) return
                val onek = d.readUTF()
                val sayi = d.readInt()
                val ana = HashMap<String, AnaGirdi>(sayi * 2)
                repeat(sayi) {
                    val adres = onek + d.readUTF()
                    val kayit = Kayit(
                        d.readLong(), d.readLong(), d.readUTF(), d.readUTF(),
                        gorev = d.readShort().toInt(), biten = d.readShort().toInt()
                    )
                    if (d.readBoolean()) {
                        val ad = d.readUTF()
                        val klasorVar = d.readBoolean()
                        val klasor = d.readUTF()
                        ana[adres] = AnaGirdi(ad, if (klasorVar) klasor else null)
                    }
                    // Bellekte daha yeni bir kayıt varsa ona dokunma.
                    kayitlar.putIfAbsent(adres, kayit)
                }
                if (anaListe.isEmpty()) anaListe = ana
            }
        } catch (_: Exception) {
        }
    }

    private fun ortakOnek(adresler: List<String>): String {
        if (adresler.isEmpty()) return ""
        var onek = adresler[0]
        for (a in adresler) {
            var i = 0
            val sinir = minOf(onek.length, a.length)
            while (i < sinir && onek[i] == a[i]) i++
            onek = onek.substring(0, i)
            if (onek.isEmpty()) break
        }
        return onek
    }

    companion object {
        /** 5: özetler NotOnizleme ile ([!tip] gibi işaretler gizli). Eski dosya yok sayılır, liste bir kez yeniden kurulur. */
        private const val SURUM = 5
    }
}
