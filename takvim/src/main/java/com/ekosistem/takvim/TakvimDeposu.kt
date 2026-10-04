package com.ekosistem.takvim

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Telefonun ortak takvim deposuyla (CalendarProvider) tek konuşma noktası.
 * Etkinlikler orada yaşar: DAVx5 gibi senkron uygulamaları da oraya yazar,
 * tekrar kurallarını (her ayın 2. salısı…) depo açar. Hata durumunda
 * (izin geri alındı, depo yok) boş döner, çökmez.
 */
object TakvimDeposu {

    fun izinVar(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun Int.opak() = this or 0xFF000000.toInt()

    // ---- Takvimler ----

    fun takvimler(c: Context): List<Takvim> {
        val liste = ArrayList<Takvim>()
        try {
            c.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(
                    Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
                    Calendars.CALENDAR_COLOR, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.OWNER_ACCOUNT
                ),
                null, null, "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { k ->
                while (k.moveToNext()) {
                    liste.add(
                        Takvim(
                            id = k.getLong(0),
                            ad = k.getString(1).orEmpty(),
                            hesap = k.getString(2).orEmpty(),
                            hesapTuru = k.getString(3).orEmpty(),
                            renk = k.getInt(4).opak(),
                            gorunur = k.getInt(5) != 0,
                            yazilabilir = k.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                            sahip = k.getString(7).orEmpty()
                        )
                    )
                }
            }
        } catch (_: RuntimeException) {
        }
        return liste
    }

    /** Hiç takvimi olmayan (ör. Google'sız) telefonda hesapsız yerel takvim açar. */
    fun yerelTakvimOlustur(c: Context, ad: String, renk: Int): Long? {
        return try {
            val uri = Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, YEREL_HESAP)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            val v = ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, YEREL_HESAP)
                put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, YEREL_HESAP)
                put(Calendars.CALENDAR_DISPLAY_NAME, ad)
                put(Calendars.CALENDAR_COLOR, renk)
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, YEREL_HESAP)
                put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
                put(Calendars.VISIBLE, 1)
                put(Calendars.SYNC_EVENTS, 1)
            }
            c.contentResolver.insert(uri, v)?.let { ContentUris.parseId(it) }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun gorunurDegistir(c: Context, takvimId: Long, gorunur: Boolean) {
        try {
            val v = ContentValues().apply {
                put(Calendars.VISIBLE, if (gorunur) 1 else 0)
                put(Calendars.SYNC_EVENTS, if (gorunur) 1 else 0)
            }
            c.contentResolver.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, takvimId), v, null, null)
        } catch (_: RuntimeException) {
        }
    }

    // ---- Okuma ----

    /** [ilkGun]..[sonGun] (dahil) arasındaki bütün görünür takvimlerin etkinlik örnekleri. */
    fun ornekler(c: Context, ilkGun: Int, sonGun: Int, aciklamaDahil: Boolean = false): List<Ornek> {
        val liste = ArrayList<Ornek>()
        val tz = TimeZone.getDefault()
        val gosterRed = Depo.reddedilenleriGoster(c)
        try {
            val b = Instances.CONTENT_BY_DAY_URI.buildUpon()
            ContentUris.appendId(b, Gun.julian(ilkGun).toLong())
            ContentUris.appendId(b, Gun.julian(sonGun).toLong())
            c.contentResolver.query(
                b.build(),
                arrayOf(
                    Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION,
                    Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.RRULE,
                    Instances.EVENT_COLOR, Instances.CALENDAR_COLOR, Instances.STATUS, Instances.SELF_ATTENDEE_STATUS,
                    Instances.ORIGINAL_ID, if (aciklamaDahil) Instances.DESCRIPTION else Instances.EVENT_LOCATION,
                    Instances.AVAILABILITY
                ),
                "${Calendars.VISIBLE}=1", null, "${Instances.BEGIN} ASC, ${Instances.END} DESC"
            )?.use { k ->
                while (k.moveToNext()) {
                    if (k.getInt(10) == Events.STATUS_CANCELED) continue
                    val red = k.getInt(11) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                    if (red && !gosterRed) continue
                    val renk = (if (!k.isNull(8)) k.getInt(8) else k.getInt(9)).opak()
                    liste.add(
                        Ornek.olustur(
                            etkinlikId = k.getLong(0), takvimId = k.getLong(1),
                            baslik = k.getString(2).orEmpty(), konum = k.getString(3).orEmpty(),
                            baslangic = k.getLong(4), bitis = k.getLong(5), tumGun = k.getInt(6) != 0,
                            renk = renk,
                            tekrarli = !k.getString(7).isNullOrBlank() || !k.isNull(12),
                            tz = tz,
                            aciklama = if (aciklamaDahil) k.getString(13).orEmpty() else "",
                            reddedildi = red,
                            uygun = k.getInt(14) == Events.AVAILABILITY_FREE,
                            davetBekliyor = k.getInt(11) == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED
                        )
                    )
                }
            }
        } catch (_: RuntimeException) {
        }
        liste.addAll(DogumGunleri.ornekler(c, ilkGun, sonGun))
        return liste
    }

    /** [ilkGun]–[sonGun] arasında yanıtlanmamış davet sayısı (tekrarlayan davet bir kez sayılır). */
    fun bekleyenDavetSayisi(c: Context, ilkGun: Int, sonGun: Int): Int {
        val idler = HashSet<Long>()
        try {
            val b = Instances.CONTENT_BY_DAY_URI.buildUpon()
            ContentUris.appendId(b, Gun.julian(ilkGun).toLong())
            ContentUris.appendId(b, Gun.julian(sonGun).toLong())
            c.contentResolver.query(
                b.build(), arrayOf(Instances.EVENT_ID),
                "${Calendars.VISIBLE}=1 AND ${Instances.SELF_ATTENDEE_STATUS}=${CalendarContract.Attendees.ATTENDEE_STATUS_INVITED}" +
                    // Durumu boş (NULL) etkinlik de sayılır: SQL'de NULL != 2 doğru değildir.
                    " AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS}!=${Events.STATUS_CANCELED})",
                null, null
            )?.use { k -> while (k.moveToNext()) idler.add(k.getLong(0)) }
        } catch (_: RuntimeException) {
        }
        return idler.size
    }

    fun etkinlik(c: Context, id: Long): Etkinlik? {
        try {
            c.contentResolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, id),
                arrayOf(
                    Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION,
                    Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE,
                    Events.RRULE, Events.ORIGINAL_ID, Events.EVENT_COLOR, Events.CALENDAR_COLOR,
                    Events.CALENDAR_ACCESS_LEVEL, Events.CALENDAR_DISPLAY_NAME, Events.STATUS,
                    Events.ORGANIZER, Events.OWNER_ACCOUNT, Events.SELF_ATTENDEE_STATUS, Events.AVAILABILITY
                ),
                null, null, null
            )?.use { k ->
                if (!k.moveToFirst()) return null
                val tumGun = k.getInt(8) != 0
                val bas = k.getLong(5)
                val bit = when {
                    !k.isNull(6) && k.getLong(6) > 0 -> k.getLong(6)
                    else -> bas + Tekrar.sureMs(k.getString(7)).let { if (it == 0L && tumGun) Gun.GUN_MS else it }
                }
                val renk = (if (!k.isNull(12)) k.getInt(12) else k.getInt(13)).opak()
                return Etkinlik(
                    id = k.getLong(0), takvimId = k.getLong(1), baslik = k.getString(2).orEmpty(),
                    aciklama = k.getString(3).orEmpty(), konum = k.getString(4).orEmpty(),
                    baslangic = bas, bitis = bit, tumGun = tumGun,
                    zamanDilimi = k.getString(9).orEmpty().ifEmpty { TimeZone.getDefault().id },
                    kural = k.getString(10)?.takeIf { it.isNotBlank() },
                    hatirlaticilar = hatirlaticilar(c, id), renk = renk,
                    ozelRenk = if (k.isNull(12)) 0 else k.getInt(12).opak(),
                    asilId = if (k.isNull(11)) 0 else k.getLong(11),
                    yazilabilir = k.getInt(14) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                    takvimAdi = k.getString(15).orEmpty(), durum = k.getInt(16),
                    davetliler = davetliler(c, id), organizator = k.getString(17).orEmpty(),
                    sahipHesap = k.getString(18).orEmpty(), benimDurumum = k.getInt(19),
                    musaitlik = k.getInt(20)
                )
            }
        } catch (_: RuntimeException) {
        }
        return null
    }

    fun davetliler(c: Context, etkinlikId: Long): List<Davetli> {
        val liste = ArrayList<Davetli>()
        try {
            c.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                arrayOf(CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_STATUS),
                "${CalendarContract.Attendees.EVENT_ID}=?", arrayOf(etkinlikId.toString()), null
            )?.use { k ->
                while (k.moveToNext()) liste.add(Davetli(k.getString(0).orEmpty(), k.getString(1).orEmpty(), k.getInt(2)))
            }
        } catch (_: RuntimeException) {
        }
        return liste
    }

    /** Davete verilen yanıtı yazar (Evet/Belki/Hayır); senkronlu takvimde sunucuya gider. */
    fun yanitla(c: Context, e: Etkinlik, durum: Int): Boolean {
        return try {
            val cr = c.contentResolver
            val v = ContentValues().apply { put(CalendarContract.Attendees.ATTENDEE_STATUS, durum) }
            cr.update(
                CalendarContract.Attendees.CONTENT_URI, v,
                "${CalendarContract.Attendees.EVENT_ID}=? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL}=? COLLATE NOCASE",
                arrayOf(e.id.toString(), e.sahipHesap)
            )
            val ev = ContentValues().apply { put(Events.SELF_ATTENDEE_STATUS, durum) }
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, e.id), ev, null, null) > 0
        } catch (_: RuntimeException) {
            false
        }
    }

    fun hatirlaticilar(c: Context, etkinlikId: Long): List<Int> {
        val liste = ArrayList<Int>()
        try {
            c.contentResolver.query(
                Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
                "${Reminders.EVENT_ID}=?", arrayOf(etkinlikId.toString()), "${Reminders.MINUTES} ASC"
            )?.use { k ->
                while (k.moveToNext()) {
                    val method = k.getInt(1)
                    if (method == Reminders.METHOD_EMAIL || method == Reminders.METHOD_SMS) continue
                    val dk = k.getInt(0)
                    liste.add(if (dk < 0) 10 else dk)   // -1 = takvimin varsayılanı
                }
            }
        } catch (_: RuntimeException) {
        }
        return liste.distinct().sorted()
    }

    /** [etkinlikId] serisinin [oncekiBitis]'ten önce başlayan örneklerinin sayısı. */
    private fun oncekiOrnekSayisi(c: Context, etkinlikId: Long, ilk: Long, oncekiBitis: Long): Int {
        try {
            val b = Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, ilk - Gun.GUN_MS)
            ContentUris.appendId(b, oncekiBitis)
            c.contentResolver.query(
                b.build(), arrayOf(Instances.BEGIN), "${Instances.EVENT_ID}=?", arrayOf(etkinlikId.toString()), null
            )?.use { k ->
                var n = 0
                while (k.moveToNext()) if (k.getLong(0) < oncekiBitis) n++
                return n
            }
        } catch (_: RuntimeException) {
        }
        return 0
    }

    // ---- Yazma ----

    private fun degerler(e: Etkinlik): ContentValues {
        val v = ContentValues()
        v.put(Events.CALENDAR_ID, e.takvimId)
        v.put(Events.TITLE, e.baslik)
        v.put(Events.DESCRIPTION, e.aciklama)
        v.put(Events.EVENT_LOCATION, e.konum)
        v.put(Events.DTSTART, e.baslangic)
        val dilim = if (e.tumGun) "UTC" else e.zamanDilimi.ifEmpty { TimeZone.getDefault().id }
        v.put(Events.EVENT_TIMEZONE, dilim)
        v.put(Events.EVENT_END_TIMEZONE, dilim)
        v.put(Events.ALL_DAY, if (e.tumGun) 1 else 0)
        v.put(Events.HAS_ALARM, if (e.hatirlaticilar.isEmpty()) 0 else 1)
        v.put(Events.AVAILABILITY, e.musaitlik)
        if (e.ozelRenk != 0) v.put(Events.EVENT_COLOR, e.ozelRenk) else v.putNull(Events.EVENT_COLOR)
        if (e.kural != null) {
            v.put(Events.RRULE, e.kural)
            v.put(Events.DURATION, Tekrar.sureYaz(e.baslangic, e.bitis, e.tumGun))
            v.putNull(Events.DTEND)
        } else {
            v.putNull(Events.RRULE)
            v.putNull(Events.DURATION)
            v.put(Events.DTEND, e.bitis)
        }
        return v
    }

    private fun hatirlaticilariYaz(c: Context, etkinlikId: Long, dakikalar: List<Int>) {
        c.contentResolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(etkinlikId.toString()))
        for (dk in dakikalar.distinct()) {
            val v = ContentValues().apply {
                put(Reminders.EVENT_ID, etkinlikId)
                put(Reminders.MINUTES, dk)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            }
            try {
                c.contentResolver.insert(Reminders.CONTENT_URI, v)
            } catch (_: RuntimeException) {
                // Takvim bu hatırlatıcı yöntemini ya da sayısını kabul etmiyor; kalanlar yazılsın.
            }
        }
    }

    private fun sahipEkle(c: Context, v: ContentValues, takvimId: Long) {
        takvimler(c).firstOrNull { it.id == takvimId }?.let { t ->
            if (t.hesapTuru != CalendarContract.ACCOUNT_TYPE_LOCAL && t.sahip.isNotEmpty()) {
                v.put(Events.ORGANIZER, t.sahip)
            }
        }
    }

    /** Yeni etkinlik; kimliğini ya da başarısızsa null döner. */
    fun ekle(c: Context, e: Etkinlik): Long? {
        return try {
            val v = degerler(e)
            sahipEkle(c, v, e.takvimId)
            val id = c.contentResolver.insert(Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) } ?: return null
            hatirlaticilariYaz(c, id, e.hatirlaticilar)
            id
        } catch (_: RuntimeException) {
            null
        }
    }

    /**
     * Düzenlenen etkinliği kaydeder. [eski] depodaki hali, [ornekBas] düzenlenen
     * örneğin depodaki başlangıç anı, [yeni] formdaki değerler (başlangıç/bitiş
     * düzenlenen örneğe göredir). Tekrarlayan etkinlikte [kapsam] hangi kısmın
     * değişeceğini söyler. Etkinliğin (yeni olabilir) kimliği ya da null.
     */
    fun guncelle(c: Context, eski: Etkinlik, ornekBas: Long, yeni: Etkinlik, kapsam: Kapsam): Long? {
        return try {
            val tz = TimeZone.getDefault()
            val tekrarli = eski.kural != null
            when {
                !tekrarli -> tekGuncelle(c, eski.id, yeni)
                kapsam == Kapsam.HEPSI || ilkOrnekMi(eski, ornekBas, tz) && kapsam == Kapsam.BUNDAN_SONRA ->
                    tekGuncelle(c, eski.id, yeni.copy(
                        baslangic = kaydirilmisBaslangic(eski, ornekBas, yeni, tz),
                        bitis = kaydirilmisBaslangic(eski, ornekBas, yeni, tz) + (yeni.bitis - yeni.baslangic)
                    ))
                kapsam == Kapsam.BU -> {
                    val v = istisnaDegerleri(yeni, ornekBas)
                    val uri = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eski.id)
                    val id = c.contentResolver.insert(uri, v)?.let { ContentUris.parseId(it) } ?: return null
                    hatirlaticilariYaz(c, id, yeni.hatirlaticilar)
                    id
                }
                else -> bolVeEkle(c, eski, ornekBas, yeni, tz)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    /**
     * Tekrarın tek örneği için değerler. Depo istisnada `calendar_id`, `dtend`
     * ve `rrule` yazmayı reddeder (emülatörde "Exceptions can't overwrite dtend"
     * ile görüldü): bitiş DURATION ile verilir, depo DTEND'i kendisi hesaplar.
     */
    private fun istisnaDegerleri(e: Etkinlik, ornekBas: Long): ContentValues {
        val v = ContentValues()
        v.put(Events.ORIGINAL_INSTANCE_TIME, ornekBas)
        v.put(Events.STATUS, Events.STATUS_CONFIRMED)
        v.put(Events.TITLE, e.baslik)
        v.put(Events.DESCRIPTION, e.aciklama)
        v.put(Events.EVENT_LOCATION, e.konum)
        v.put(Events.DTSTART, e.baslangic)
        v.put(Events.DURATION, Tekrar.sureYaz(e.baslangic, e.bitis, e.tumGun))
        v.put(Events.EVENT_TIMEZONE, if (e.tumGun) "UTC" else e.zamanDilimi.ifEmpty { TimeZone.getDefault().id })
        v.put(Events.ALL_DAY, if (e.tumGun) 1 else 0)
        v.put(Events.HAS_ALARM, if (e.hatirlaticilar.isEmpty()) 0 else 1)
        v.put(Events.AVAILABILITY, e.musaitlik)
        return v
    }

    private fun tekGuncelle(c: Context, id: Long, e: Etkinlik): Long? {
        val n = c.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), degerler(e), null, null)
        if (n < 1) return null
        hatirlaticilariYaz(c, id, e.hatirlaticilar)
        return id
    }

    private fun ilkOrnekMi(eski: Etkinlik, ornekBas: Long, tz: TimeZone): Boolean =
        if (eski.tumGun) Gun.utcGun(ornekBas) <= Gun.utcGun(eski.baslangic) else ornekBas <= eski.baslangic

    /**
     * "Hepsi": formda düzenlenen örneğin gün kayması ana etkinliğe uygulanır
     * (3. tekrarı bir gün ileri aldıysan seri bir gün kayar); saat formdakidir.
     */
    private fun kaydirilmisBaslangic(eski: Etkinlik, ornekBas: Long, yeni: Etkinlik, tz: TimeZone): Long {
        fun gunu(an: Long, tumGun: Boolean) = if (tumGun) Gun.utcGun(an) else Gun.yerelGun(an, tz)
        val kayma = gunu(yeni.baslangic, yeni.tumGun) - gunu(ornekBas, eski.tumGun)
        val asilGun = gunu(eski.baslangic, eski.tumGun) + kayma
        return if (yeni.tumGun) Gun.utcGunBasi(asilGun)
        else Gun.yerelAn(asilGun, Gun.yerelDakika(yeni.baslangic, tz), tz)
    }

    /** "Bu ve sonrakiler": eski seri bölünen örnekten önce biter, yenisi oradan başlar. */
    private fun bolVeEkle(c: Context, eski: Etkinlik, ornekBas: Long, yeni: Etkinlik, tz: TimeZone): Long? {
        val kural = eski.kural ?: return null
        val oncekiGun = (if (eski.tumGun) Gun.utcGun(ornekBas) else Gun.yerelGun(ornekBas, tz)) - 1
        val oncekiAn = ornekBas - 1000L
        val sayi = Tekrar.sayi(kural)
        val onceki = if (sayi > 0) oncekiOrnekSayisi(c, eski.id, eski.baslangic, ornekBas) else 0

        // Tam değer kümesiyle güncellenir: depo yalnız RRULE değişince örnekleri yenilemeyebiliyor.
        val kesik = degerler(eski.copy(kural = Tekrar.kes(kural, oncekiAn, oncekiGun, eski.tumGun)))
        if (c.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eski.id), kesik, null, null) < 1) return null

        // Kural değişmediyse ve sayıyla bitiyorsa yeni seri kalan sayıyı alır.
        val yeniKural = if (yeni.kural == kural && sayi > 0) Tekrar.sayiyiDegistir(kural, maxOf(1, sayi - onceki)) else yeni.kural
        return ekle(c, yeni.copy(kural = yeniKural))
    }

    /** Silmeyi geri almak için gerekenler ([Tur] nasıl geri alınacağını söyler). */
    class SilmeKaydi(val tur: Tur, val etkinlik: Etkinlik, val ornekBas: Long, val iptalId: Long = 0)

    enum class Tur {
        /** Etkinlik satırı silindi; geri alma onu yeniden ekler (yeni kimlikle). */
        TAM,
        /** Tekrarın bir örneği iptal istisnasıyla gizlendi; geri alma istisnayı siler. */
        ISTISNA,
        /** Seri bölünen örnekten önce kesildi; geri alma eski kuralı yazar. */
        KESIK,
        /** Değiştirilmiş tek örnek iptal edildi; geri alma durumu eski haline getirir. */
        DURUM
    }

    fun sil(c: Context, eski: Etkinlik, ornekBas: Long, kapsam: Kapsam): SilmeKaydi? {
        return try {
            val tz = TimeZone.getDefault()
            val cr = c.contentResolver
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eski.id)
            when {
                // Tekrarın değiştirilmiş tek örneği: silmek o örneği iptal etmektir (seri yerinde kalır).
                eski.asilId > 0 ->
                    if (cr.update(uri, ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null, null) > 0)
                        SilmeKaydi(Tur.DURUM, eski, ornekBas) else null
                eski.kural == null -> if (cr.delete(uri, null, null) > 0) SilmeKaydi(Tur.TAM, eski, ornekBas) else null
                kapsam == Kapsam.HEPSI || kapsam == Kapsam.BUNDAN_SONRA && ilkOrnekMi(eski, ornekBas, tz) ->
                    if (cr.delete(uri, null, null) > 0) SilmeKaydi(Tur.TAM, eski, ornekBas) else null
                kapsam == Kapsam.BU -> {
                    val v = ContentValues().apply {
                        put(Events.ORIGINAL_INSTANCE_TIME, ornekBas)
                        put(Events.STATUS, Events.STATUS_CANCELED)
                    }
                    cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eski.id), v)
                        ?.let { SilmeKaydi(Tur.ISTISNA, eski, ornekBas, ContentUris.parseId(it)) }
                }
                else -> {
                    val oncekiGun = (if (eski.tumGun) Gun.utcGun(ornekBas) else Gun.yerelGun(ornekBas, tz)) - 1
                    val v = degerler(eski.copy(kural = Tekrar.kes(eski.kural, ornekBas - 1000L, oncekiGun, eski.tumGun)))
                    if (cr.update(uri, v, null, null) > 0) SilmeKaydi(Tur.KESIK, eski, ornekBas) else null
                }
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    /** [sil]'in geri alması. Başarılıysa true. */
    fun geriAl(c: Context, k: SilmeKaydi): Boolean {
        return try {
            val cr = c.contentResolver
            when (k.tur) {
                Tur.TAM -> ekle(c, k.etkinlik.copy(id = 0)) != null
                Tur.ISTISNA -> cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, k.iptalId), null, null) > 0
                Tur.KESIK -> cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, k.etkinlik.id), degerler(k.etkinlik), null, null) > 0
                Tur.DURUM -> cr.update(
                    ContentUris.withAppendedId(Events.CONTENT_URI, k.etkinlik.id),
                    ContentValues().apply { put(Events.STATUS, k.etkinlik.durum) }, null, null
                ) > 0
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    private const val YEREL_HESAP = "Takvim"
}
