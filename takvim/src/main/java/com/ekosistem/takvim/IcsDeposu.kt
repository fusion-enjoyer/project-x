package com.ekosistem.takvim

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import java.util.TimeZone

/** Takvim deposu ↔ `.ics` köprüsü: dışa aktarma için etkinlikleri toplar, içe aktarmada depoya yazar. */
object IcsDeposu {

    /** Seçili takvimlerin bütün etkinlikleri (tekrar istisnaları dahil), `.ics` kayıtları olarak. */
    fun disaAktar(c: Context, takvimIdleri: List<Long>): List<IcsEtkinlik> {
        if (takvimIdleri.isEmpty()) return emptyList()
        val sonuc = ArrayList<IcsEtkinlik>()
        try {
            val idler = takvimIdleri.joinToString(",")
            class Satir(
                val id: Long, val baslik: String, val aciklama: String, val konum: String, val bas: Long, val bit: Long?,
                val sure: String?, val tumGun: Boolean, val dilim: String, val kural: String?, val asilId: Long?, val asilAn: Long?, val durum: Int,
                val uygun: Boolean
            )
            val satirlar = ArrayList<Satir>()
            c.contentResolver.query(
                Events.CONTENT_URI,
                arrayOf(
                    Events._ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
                    Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.RRULE, Events.ORIGINAL_ID,
                    Events.ORIGINAL_INSTANCE_TIME, Events.STATUS, Events.AVAILABILITY
                ),
                "${Events.CALENDAR_ID} IN ($idler) AND ${Events.DELETED}=0", null, "${Events._ID} ASC"
            )?.use { k ->
                while (k.moveToNext()) {
                    satirlar.add(
                        Satir(
                            k.getLong(0), k.getString(1).orEmpty(), k.getString(2).orEmpty(), k.getString(3).orEmpty(),
                            k.getLong(4), if (k.isNull(5)) null else k.getLong(5), k.getString(6), k.getInt(7) != 0,
                            k.getString(8).orEmpty(), k.getString(9)?.takeIf { it.isNotBlank() },
                            if (k.isNull(10)) null else k.getLong(10), if (k.isNull(11)) null else k.getLong(11), k.getInt(12),
                            k.getInt(13) == Events.AVAILABILITY_FREE
                        )
                    )
                }
            }
            val uyarilar = HashMap<Long, MutableList<Int>>()
            val gecerli = satirlar.map { it.id }.toHashSet()
            c.contentResolver.query(
                Reminders.CONTENT_URI, arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD), null, null, null
            )?.use { k ->
                while (k.moveToNext()) {
                    val id = k.getLong(0)
                    if (id !in gecerli) continue
                    if (k.getInt(2) == Reminders.METHOD_EMAIL || k.getInt(2) == Reminders.METHOD_SMS) continue
                    uyarilar.getOrPut(id) { ArrayList() }.add(k.getInt(1).let { if (it < 0) 10 else it })
                }
            }
            val uid = { id: Long -> "$id@takvim" }
            val muaf = HashMap<Long, MutableList<Long>>()
            for (s in satirlar) {
                if (s.asilId != null && s.durum == Events.STATUS_CANCELED && s.asilAn != null) {
                    muaf.getOrPut(s.asilId) { ArrayList() }.add(s.asilAn)
                }
            }
            for (s in satirlar) {
                if (s.durum == Events.STATUS_CANCELED) continue
                val bit = s.bit ?: (s.bas + Tekrar.sureMs(s.sure).let { if (it == 0L && s.tumGun) Gun.GUN_MS else it })
                sonuc.add(
                    IcsEtkinlik(
                        uid = uid(s.asilId ?: s.id), baslik = s.baslik, aciklama = s.aciklama, konum = s.konum,
                        baslangic = s.bas, bitis = bit, tumGun = s.tumGun, zamanDilimi = s.dilim.ifEmpty { TimeZone.getDefault().id },
                        kural = s.kural, hatirlaticilar = uyarilar[s.id].orEmpty().distinct().sorted(),
                        muaf = muaf[s.id].orEmpty().sorted(), oncekiOrnek = if (s.asilId != null) s.asilAn else null,
                        uygun = s.uygun
                    )
                )
            }
        } catch (_: RuntimeException) {
        }
        return sonuc
    }

    class Sonuc(val eklenen: Int, val atlanan: Int, val hatali: Int)

    /**
     * [liste]yi [takvimId] takvimine yazar. Aynı başlık ve başlangıçta zaten bulunan etkinlik atlanır
     * (aynı dosyayı iki kez içe aktarınca çiftlenmesin). [ilerleme] her etkinlikten sonra (yapılan, toplam) alır.
     */
    fun iceAktar(c: Context, takvimId: Long, liste: List<IcsEtkinlik>, ilerleme: (Int, Int) -> Unit): Sonuc {
        val mevcut = HashSet<String>()
        try {
            c.contentResolver.query(
                Events.CONTENT_URI, arrayOf(Events.TITLE, Events.DTSTART),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0 AND ${Events.ORIGINAL_ID} IS NULL", arrayOf(takvimId.toString()), null
            )?.use { k -> while (k.moveToNext()) mevcut.add(k.getString(0).orEmpty() + "|" + k.getLong(1)) }
        } catch (_: RuntimeException) {
        }
        var eklenen = 0
        var atlanan = 0
        var hatali = 0
        val anaId = HashMap<String, Long>()
        val toplam = liste.size
        var yapilan = 0
        // Önce asıl etkinlikler, sonra değiştirilmiş örnekler (ana etkinlik var olmalı).
        val (ornekler, anaListe) = liste.partition { it.oncekiOrnek != null }
        for (e in anaListe) {
            yapilan++
            ilerleme(yapilan, toplam)
            val anahtar = e.baslik + "|" + e.baslangic
            if (anahtar in mevcut) { atlanan++; continue }
            val id = TakvimDeposu.ekle(c, forma(e, takvimId))
            if (id == null) { hatali++; continue }
            eklenen++
            mevcut.add(anahtar)
            anaId[e.uid] = id
            if (e.muaf.isNotEmpty() && e.kural != null) {
                TakvimDeposu.etkinlik(c, id)?.let { ana -> for (an in e.muaf) TakvimDeposu.sil(c, ana, an, Kapsam.BU) }
            }
        }
        for (e in ornekler) {
            yapilan++
            ilerleme(yapilan, toplam)
            val ana = anaId[e.uid]?.let { TakvimDeposu.etkinlik(c, it) }
            if (ana == null) { atlanan++; continue }
            if (TakvimDeposu.guncelle(c, ana, e.oncekiOrnek!!, forma(e, takvimId).copy(kural = null), Kapsam.BU) != null) eklenen++ else hatali++
        }
        return Sonuc(eklenen, atlanan, hatali)
    }

    private fun forma(e: IcsEtkinlik, takvimId: Long) = Etkinlik(
        takvimId = takvimId, baslik = e.baslik, konum = e.konum, aciklama = e.aciklama,
        baslangic = e.baslangic, bitis = e.bitis, tumGun = e.tumGun,
        zamanDilimi = if (e.tumGun) "UTC" else e.zamanDilimi, kural = e.kural, hatirlaticilar = e.hatirlaticilar,
        musaitlik = if (e.uygun) Etkinlik.UYGUN else Etkinlik.MESGUL
    )

    /** Tek etkinliği paylaşmak için `.ics` kaydı. */
    fun tek(c: Context, e: Etkinlik): IcsEtkinlik = IcsEtkinlik(
        uid = "${e.id}@takvim", baslik = e.baslik, aciklama = e.aciklama, konum = e.konum,
        baslangic = e.baslangic, bitis = e.bitis, tumGun = e.tumGun, zamanDilimi = e.zamanDilimi.ifEmpty { TimeZone.getDefault().id },
        kural = e.kural, hatirlaticilar = e.hatirlaticilar, uygun = e.musaitlik == Etkinlik.UYGUN
    )

    @Suppress("unused")
    private fun uriId(id: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, id)
}
