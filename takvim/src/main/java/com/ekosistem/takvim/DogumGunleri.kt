package com.ekosistem.takvim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Rehberdeki doğum günlerini takvime katar (isteğe bağlı: ayarlardan açılır, rehber izni ister).
 * Veri telefondan çıkmaz, depoya yazılmaz; her yenilemede rehberden okunup tüm gün etkinliği
 * olarak listelere eklenir. Etkinlik kimliği negatif ([OrnekAc]): dokununca kişi açılır.
 */
object DogumGunleri {
    /** Doğum günü etkinliklerinin rengi (takvim renklerinden ayırt edilsin diye ayrı). */
    const val RENK = 0xFFDB2777.toInt()

    private var onbellek: Pair<Long, List<DogumGunu.Kisi>>? = null

    fun izinVar(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun etkin(c: Context) = Depo.dogumGunleri(c) && izinVar(c)

    fun onbellegiTemizle() {
        onbellek = null
    }

    /** Rehberdeki doğum günü kayıtları; kısa süre bellekte tutulur (ekran her yenilendiğinde sorgulanmasın). */
    private fun kisiler(c: Context): List<DogumGunu.Kisi> {
        val simdi = SystemClock.elapsedRealtime()
        onbellek?.let { if (simdi - it.first < 30_000L) return it.second }
        val liste = ArrayList<DogumGunu.Kisi>()
        try {
            c.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                ),
                null
            )?.use { k ->
                while (k.moveToNext()) {
                    val t = DogumGunu.coz(k.getString(2)) ?: continue
                    val ad = k.getString(1).orEmpty().ifBlank { continue }
                    liste.add(DogumGunu.Kisi(k.getLong(0), ad, t.second, t.third, t.first))
                }
            }
        } catch (_: RuntimeException) {
        }
        onbellek = simdi to liste
        return liste
    }

    /** [ilkGun]..[sonGun] arası doğum günleri, tüm gün etkinliği olarak. İzin/ayar kapalıysa boş. */
    fun ornekler(c: Context, ilkGun: Int, sonGun: Int): List<Ornek> {
        if (!etkin(c)) return emptyList()
        val tz = TimeZone.getDefault()
        return DogumGunu.gecisler(kisiler(c), ilkGun, sonGun).map { g ->
            Ornek.olustur(
                etkinlikId = -maxOf(1L, g.kisi.id), takvimId = -1L,
                baslik = c.getString(R.string.dogum_gunu_baslik, g.kisi.ad),
                konum = g.yas?.let { c.getString(R.string.dogum_yas, it) }.orEmpty(),
                baslangic = Gun.utcGunBasi(g.gun), bitis = Gun.utcGunBasi(g.gun + 1),
                tumGun = true, renk = RENK, tekrarli = true, tz = tz
            )
        }
    }
}
