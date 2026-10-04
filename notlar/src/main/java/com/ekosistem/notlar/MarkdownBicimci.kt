package com.ekosistem.notlar

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.core.content.ContextCompat

/**
 * Notu yazarken canlı biçimlendirir. Dosyadaki metne dokunulmaz; yalnızca
 * span eklenir. İmlecin bulunduğu satırda Markdown işaretleri görünür,
 * diğer satırlarda gizlenir (Obsidian "live preview" davranışı).
 */
class MarkdownBicimci(private val context: Context) {

    private val soluk = ContextCompat.getColor(context, R.color.metin_ikincil)
    private val kodZemin = ContextCompat.getColor(context, R.color.kart)
    private val gecikmis = ContextCompat.getColor(context, R.color.fark_silindi)

    /** Bugünün gün numarası; her taramada bir kez hesaplanır, satır başına değil. */
    private var bugunNo = 0L

    /** Taranan metnin düz hâli; span'ler değişir, metin tarama boyunca değişmez. */
    private var duzMetin = ""
    private val yogunluk = context.resources.displayMetrics.density
    private var vurgu = Renkler.vurgu(context)
    private var vurguUzeri = Renkler.vurguUzeri(context)

    fun renkleriYenile() {
        vurgu = Renkler.vurgu(context)
        vurguUzeri = Renkler.vurguUzeri(context)
    }

    /**
     * Gövde boyutu (sp) ayarlardan gelir; başlıklar bundan türetilir. Metin
     * alanının TABAN boyutu da gövdedir — imleç taban boyuta göre çizildiği
     * için taban büyük olursa gövdede kocaman bir imleç görünür (yaşandı).
     */
    var govdeSp = Prefs.yaziBoyu(context)

    fun boyutlariYenile() {
        govdeSp = Prefs.yaziBoyu(context)
    }

    fun baslikSp(): Int = govdeSp + 8

    /** Kaynak modunda hiçbir biçim uygulanmaz; ham Markdown görünür. */
    var kaynakModu = Prefs.kaynakModu(context)

    /** Görselleri çözmek için gereken depo; yoksa yer tutucu çizilir. */
    var depo: NotDeposu? = null

    /** Arka planda çözülen görsel gelince biçimlendirme yenilensin. */
    var gorselHazir: (() -> Unit)? = null

    /** Görselin sığacağı genişlik; her biçimlendirmede tazelenir. */
    private var satirGenisligi = 0

    /**
     * [basliksiz]: ilk satır not başlığı sayılmaz (yardımdaki tek başına örnekler
     * gibi parça metinler için); not biçimlenirken hep false.
     */
    fun uygula(s: Editable, imlec: Int, genislik: Int, basliksiz: Boolean = false) {
        temizle(s)
        satirGenisligi = genislik
        if (s.isEmpty()) return

        if (kaynakModu) {
            s.setSpan(TypefaceSpan("monospace"), 0, s.length, EE)
            return
        }

        // Çok büyük notlarda yazarken takılmamak için sade biçimlendirmeye düşülür.
        if (s.length > BUYUK_NOT_SINIRI) {
            sadeBicimle(s)
            return
        }

        var bas = 0
        var satirNo = 0
        var kodBlogunda = false
        // Açık bir bilgi kutusunun rengi; alıntı olmayan satırda kapanır.
        var calloutRengi: Int? = null
        // Her tuşta bütün not taranır. Satırlar düz metinden kesilir: Editable'dan
        // kesmek (subSequence) her satır için span'leriyle yeni bir kopya üretiyordu.
        val duz = s.toString()
        duzMetin = duz
        bugunNo = SonTarih.bugun()
        while (bas <= s.length) {
            var son = duz.indexOf('\n', bas)
            if (son < 0) son = s.length
            val aktif = imlec in bas..son
            val satir = duz.substring(bas, son)
            // ``` ile açılıp kapanan kod bloğu: içinde başka işaret yorumlanmaz.
            val baslikSatiri = satirNo == 0 && !basliksiz
            val cit = !baslikSatiri && KOD_CITI.containsMatchIn(satir)
            val callout = if (cit || kodBlogunda || baslikSatiri) null else CALLOUT.find(satir)
            when {
                cit -> {
                    calloutRengi = null
                    kodSatiri(s, bas, son, cit = true)
                    kodBlogunda = !kodBlogunda
                }
                kodBlogunda -> kodSatiri(s, bas, son, cit = false)
                callout != null -> {
                    val renk = turRengi(callout.groupValues[1])
                    calloutRengi = renk
                    calloutBasligi(s, bas, son, callout, renk, aktif)
                }
                calloutRengi != null && ALINTI.containsMatchIn(satir) ->
                    calloutSatiri(s, bas, son, satir, calloutRengi, aktif)
                else -> {
                    calloutRengi = null
                    satirBicimle(s, bas, son, satir, baslikSatiri, aktif, genislik)
                }
            }
            if (son >= s.length) break
            bas = son + 1
            satirNo++
        }
    }

    private fun temizle(s: Editable) {
        for (span in s.getSpans(0, s.length, AbsoluteSizeSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, StyleSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, StrikethroughSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, ForegroundColorSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, BackgroundColorSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, TypefaceSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, OnayKutusuSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, GizliSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, AlintiSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, AyracSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, MaddeSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, GorselSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, UnderlineSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, KodBlokSpan::class.java)) s.removeSpan(span)
        for (span in s.getSpans(0, s.length, CalloutSpan::class.java)) s.removeSpan(span)
    }

    // --- Bilgi kutusu (callout) ---

    private fun turRengi(tur: String): Int {
        val sira = calloutRenkSirasi(tur) ?: return vurgu
        val secenek = Renkler.SECENEKLER[sira]
        return if (Renkler.geceMi(context)) secenek.koyu else secenek.acik
    }

    /** Kutunun ortak kısmı: gövde boyu (satır ölçümü için, bkz. satirBicimle) ve zemin. */
    private fun calloutZemini(s: Editable, bas: Int, son: Int, renk: Int) {
        val satirSonu = minOf(son + 1, s.length)
        s.setSpan(AbsoluteSizeSpan(govdeSp, true), bas, if (son > bas) son else satirSonu, EE)
        val zemin = (renk and 0x00FFFFFF) or 0x24000000
        s.setSpan(CalloutSpan(renk, zemin, yogunluk), bas, satirSonu, EE)
    }

    /**
     * `> [!tür] Başlık` satırı. İşaret gizlenir, başlık türün renginde kalın
     * görünür; başlık yazılmamışsa tür adının kendisi başlık olur.
     */
    private fun calloutBasligi(
        s: Editable,
        bas: Int,
        son: Int,
        eslesme: MatchResult,
        renk: Int,
        aktif: Boolean
    ) {
        calloutZemini(s, bas, son, renk)
        val baslik = eslesme.groupValues[3].trimEnd()
        val (yaziBas, yaziSon) = if (baslik.isNotEmpty()) {
            val b = bas + eslesme.value.length - eslesme.groupValues[3].length
            b to b + baslik.length
        } else {
            val b = bas + eslesme.value.indexOf("[!") + 2
            b to b + eslesme.groupValues[1].length
        }
        isaret(s, bas, yaziBas, aktif)
        isaret(s, yaziSon, son, aktif)
        if (yaziSon <= yaziBas) return
        s.setSpan(StyleSpan(Typeface.BOLD), yaziBas, yaziSon, EE)
        s.setSpan(ForegroundColorSpan(renk), yaziBas, yaziSon, EE)
        if (baslik.isNotEmpty()) satirIci(s, yaziBas, yaziSon, aktif)
    }

    /** Kutunun gövde satırı: `>` gizlenir, metin normal renkte biçimlenir. */
    private fun calloutSatiri(s: Editable, bas: Int, son: Int, satir: String, renk: Int, aktif: Boolean) {
        calloutZemini(s, bas, son, renk)
        val isaretBoyu = ALINTI.find(satir)?.value?.length ?: 0
        isaret(s, bas, bas + isaretBoyu, aktif)
        satirIci(s, bas + isaretBoyu, son, aktif)
    }

    /** Kod bloğu satırı: eş aralıklı yazı, satır boyu zemin; çit (```) soluk. */
    private fun kodSatiri(s: Editable, bas: Int, son: Int, cit: Boolean) {
        val satirSonu = minOf(son + 1, s.length)
        s.setSpan(AbsoluteSizeSpan(govdeSp, true), bas, if (son > bas) son else satirSonu, EE)
        s.setSpan(KodBlokSpan(kodZemin, (10 * yogunluk).toInt()), bas, satirSonu, EE)
        if (son > bas) s.setSpan(TypefaceSpan("monospace"), bas, son, EE)
        if (cit && son > bas) s.setSpan(ForegroundColorSpan(soluk), bas, son, EE)
    }

    private fun sadeBicimle(s: Editable) {
        val ilkSonu = s.indexOf('\n')
        val baslikSonu = if (ilkSonu < 0) s.length else ilkSonu
        if (baslikSonu > 0) {
            s.setSpan(StyleSpan(Typeface.BOLD), 0, baslikSonu, EE)
            s.setSpan(AbsoluteSizeSpan(baslikSp(), true), 0, baslikSonu, EE)
        }
    }

    private fun satirBicimle(
        s: Editable,
        bas: Int,
        son: Int,
        satir: String,
        baslikSatiri: Boolean,
        aktif: Boolean,
        genislik: Int
    ) {
        if (baslikSatiri) {
            if (son > bas) {
                s.setSpan(StyleSpan(Typeface.BOLD), bas, son, EE)
                s.setSpan(AbsoluteSizeSpan(baslikSp(), true), bas, son, EE)
            } else if (son < s.length) {
                // Boş başlık satırı da başlık boyunda dursun: boyut satır sonu
                // karakterine verilir. Yoksa satır gövde boyuna iner, imleç
                // küçülür ve başlığı yazmaya başlayınca satır zıplar.
                s.setSpan(AbsoluteSizeSpan(baslikSp(), true), son, son + 1, EE)
            }
            if (onayKutusu(s, bas, son, satir)) return
            // Başlık satırında da kalın/italik/kod gibi işaretler çalışsın.
            satirIci(s, bas, son, aktif)
            return
        }

        /*
         * Gövde satırına taban boyutuyla aynı olsa bile boyut span'ı konur:
         * span, satır ölçümünü yeniden tetikliyor. Bu olmadan yalnızca
         * GorselSpan içeren satırın yüksekliği hesaplanmıyor ve görselden
         * sonraki satırlar görselin üstüne biniyordu (yaşandı).
         */
        val boyutSonu = if (son > bas) son else minOf(son + 1, s.length)
        s.setSpan(AbsoluteSizeSpan(govdeSp, true), bas, boyutSonu, EE)

        val ayrac = AYRAC.matches(satir)
        if (ayrac && genislik > 0) {
            if (aktif) {
                s.setSpan(ForegroundColorSpan(soluk), bas, son, EE)
            } else {
                s.setSpan(AyracSpan(genislik, soluk), bas, son, EE)
            }
            return
        }

        val baslik = BASLIK.find(satir)
        if (baslik != null) {
            val seviye = baslik.groupValues[1].length
            val boyut = when (seviye) {
                1 -> govdeSp + 6
                2 -> govdeSp + 3
                3 -> govdeSp + 1
                else -> govdeSp
            }
            s.setSpan(AbsoluteSizeSpan(boyut, true), bas, son, EE)
            s.setSpan(StyleSpan(Typeface.BOLD), bas, son, EE)
            isaret(s, bas, bas + baslik.value.length, aktif)
            satirIci(s, bas + baslik.value.length, son, aktif)
            return
        }

        val alinti = ALINTI.find(satir)
        if (alinti != null) {
            s.setSpan(AlintiSpan(vurgu, yogunluk), bas, minOf(son + 1, s.length), EE)
            s.setSpan(ForegroundColorSpan(soluk), bas, son, EE)
            isaret(s, bas, bas + alinti.value.length, aktif)
            satirIci(s, bas + alinti.value.length, son, aktif)
            return
        }

        if (onayKutusu(s, bas, son, satir)) return

        val madde = MADDE.find(satir)
        if (madde != null) {
            val girinti = madde.groupValues[1].length
            s.setSpan(MaddeSpan(), bas + girinti, bas + madde.value.length, EE)
            satirIci(s, bas + madde.value.length, son, aktif)
            return
        }

        satirIci(s, bas, son, aktif)
    }

    /** "- [ ]" işaretini kutuya çevirir; işaretliyse satırın üstünü çizer. */
    private fun onayKutusu(s: Editable, bas: Int, son: Int, satir: String): Boolean {
        val eslesme = ONAY.find(satir) ?: return false
        val girinti = eslesme.groupValues[1].length
        val isaretli = !eslesme.groupValues[2].equals(" ", true)
        val kutuBas = bas + girinti
        val kutuSon = kutuBas + ONAY_UZUNLUGU
        s.setSpan(
            OnayKutusuSpan(isaretli, vurgu, if (isaretli) vurguUzeri else soluk),
            kutuBas,
            kutuSon,
            EE
        )
        if (isaretli && kutuSon < son) {
            s.setSpan(StrikethroughSpan(), kutuSon, son, EE)
            s.setSpan(ForegroundColorSpan(soluk), kutuSon, son, EE)
        } else if (kutuSon < son) {
            satirIci(s, kutuSon, son, false)
            sonTarihBoya(s, bas, satir, kutuSon - bas)
        }
        return true
    }

    /** Açık görevin son tarihi: geçmişse kırmızı, değilse soluk. [satirBas] satırın metindeki yeri. */
    private fun sonTarihBoya(s: Editable, satirBas: Int, satir: String, aramaBas: Int) {
        val m = SonTarih.DESEN.find(satir, aramaBas) ?: return
        val gun = SonTarih.gun(m.value) ?: return
        val renk = if (gun < bugunNo) gecikmis else soluk
        s.setSpan(ForegroundColorSpan(renk), satirBas + m.range.first, satirBas + m.range.last + 1, EE)
    }

    /** Satır içi işaretler: görsel, kod, kalın, italik, üstü çizili. */
    private fun satirIci(s: Editable, bas: Int, son: Int, aktif: Boolean) {
        if (son <= bas) return
        // Tarama dışından çağrılırsa (düz metin eskiyse) Editable'dan kesilir.
        val metin = if (duzMetin.length == s.length) duzMetin.substring(bas, son) else s.subSequence(bas, son).toString()

        // Görsel önce: kapladığı aralıkta başka işaret aranmaz, yoksa dosya
        // adındaki * ya da _ yüzünden görselin üstüne span binerdi.
        val gorseller = gorselleriIsle(s, bas, metin, aktif)

        val kodlar = mutableListOf<IntRange>()
        for (m in KOD.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            kodlar.add(m.range)
            val ic = m.groups[1] ?: continue
            s.setSpan(TypefaceSpan("monospace"), bas + ic.range.first, bas + ic.range.last + 1, EE)
            s.setSpan(
                BackgroundColorSpan(kodZemin),
                bas + ic.range.first,
                bas + ic.range.last + 1,
                EE
            )
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }

        for (m in KALIN.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            val ic = m.groups[1] ?: continue
            s.setSpan(
                StyleSpan(Typeface.BOLD),
                bas + ic.range.first,
                bas + ic.range.last + 1,
                EE
            )
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }

        for (m in ITALIK.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            val ic = m.groups[1] ?: continue
            s.setSpan(
                StyleSpan(Typeface.ITALIC),
                bas + ic.range.first,
                bas + ic.range.last + 1,
                EE
            )
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }

        for (m in CIZILI.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            val ic = m.groups[1] ?: continue
            s.setSpan(StrikethroughSpan(), bas + ic.range.first, bas + ic.range.last + 1, EE)
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }

        // #etiket: tamamı vurgu renginde, işaret gizlenmez (aranabilir kalsın)
        for (m in ETIKET.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            s.setSpan(
                ForegroundColorSpan(vurgu),
                bas + m.range.first,
                bas + m.range.last + 1,
                EE
            )
        }

        // [metin](https://...): metin bağlantı gibi görünür, adres gizlenir.
        val webler = mutableListOf<IntRange>()
        for (m in MD_BAGLANTI.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range) || kapsaniyor(kodlar, m.range)) continue
            webler.add(m.range)
            val ic = m.groups[1] ?: continue
            s.setSpan(ForegroundColorSpan(vurgu), bas + ic.range.first, bas + ic.range.last + 1, EE)
            s.setSpan(UnderlineSpan(), bas + ic.range.first, bas + ic.range.last + 1, EE)
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }
        // Düz web adresi: vurgu renginde, altı çizili; dokununca açılır.
        for (m in URL.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range) || kapsaniyor(kodlar, m.range) || kapsaniyor(webler, m.range)) continue
            s.setSpan(ForegroundColorSpan(vurgu), bas + m.range.first, bas + m.range.last + 1, EE)
            s.setSpan(UnderlineSpan(), bas + m.range.first, bas + m.range.last + 1, EE)
        }

        // [[bağlantı]]: içerik vurgu renginde ve altı çizili, köşeli parantezler gizli
        for (m in BAGLANTI.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
            val ic = m.groups[1] ?: continue
            s.setSpan(ForegroundColorSpan(vurgu), bas + ic.range.first, bas + ic.range.last + 1, EE)
            s.setSpan(UnderlineSpan(), bas + ic.range.first, bas + ic.range.last + 1, EE)
            isaret(s, bas + m.range.first, bas + ic.range.first, aktif)
            isaret(s, bas + ic.range.last + 1, bas + m.range.last + 1, aktif)
        }
    }

    /**
     * `![](ekler/ad.jpg)` ve Obsidian'ın `![[ad.jpg]]` biçimini görsele çevirir.
     * İmleç o satırdayken ham metin kalır — yoksa görseli düzenlemek imkânsız olurdu.
     */
    private fun gorselleriIsle(
        s: Editable,
        bas: Int,
        metin: String,
        aktif: Boolean
    ): List<IntRange> {
        val araliklar = mutableListOf<IntRange>()
        val bulunanlar = GORSEL.findAll(metin).map { it to it.groupValues[2] } +
            GORSEL_WIKI.findAll(metin).map { it to it.groupValues[1] }

        for ((m, yol) in bulunanlar) {
            araliklar.add(m.range)
            if (aktif) {
                // Düzenlenebilir kalsın; sadece soluklaştırılır.
                s.setSpan(
                    ForegroundColorSpan(soluk),
                    bas + m.range.first,
                    bas + m.range.last + 1,
                    EE
                )
                continue
            }
            val yerelDepo = depo
            val adres = if (yerelDepo == null) null else {
                Gorseller.adres(yerelDepo, yol) { gorselHazir?.invoke() }
            }
            val bitmap = if (adres == null || satirGenisligi <= 0) null else {
                Gorseller.bitmap(adres, satirGenisligi).also { hazirBitmap ->
                    if (hazirBitmap == null) {
                        Gorseller.yukle(context, adres, satirGenisligi) { gorselHazir?.invoke() }
                    }
                }
            }
            s.setSpan(
                GorselSpan(
                    bitmap,
                    satirGenisligi,
                    yol.substringAfterLast('/'),
                    soluk,
                    soluk,
                    yogunluk
                ),
                bas + m.range.first,
                bas + m.range.last + 1,
                EE
            )
        }
        return araliklar
    }

    private fun kapsaniyor(araliklar: List<IntRange>, aralik: IntRange): Boolean =
        araliklar.any { aralik.first <= it.last && it.first <= aralik.last }

    /** İşaret karakterleri: imleç o satırdaysa soluk görünür, değilse gizlenir. */
    private fun isaret(s: Editable, bas: Int, son: Int, aktif: Boolean) {
        if (son <= bas) return
        if (aktif) {
            s.setSpan(ForegroundColorSpan(soluk), bas, son, EE)
        } else {
            s.setSpan(GizliSpan(), bas, son, EE)
        }
    }

    companion object {
        const val ONAY_UZUNLUGU = 5
        private const val BUYUK_NOT_SINIRI = 40000
        private const val EE = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

        val ONAY = Regex("^([ \\t]*)- \\[([ xX])\\]")
        private val BASLIK = Regex("^(#{1,6}) ")
        private val ALINTI = Regex("^> ?")
        private val MADDE = Regex("^([ \\t]*)- ")
        private val AYRAC = Regex("^(-{3,}|\\*{3,}|_{3,})\\s*$")
        private val KOD = Regex("`([^`\\n]+)`")
        private val KALIN = Regex("\\*\\*([^*\\n]+)\\*\\*")
        private val ITALIK = Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])")
        private val CIZILI = Regex("~~([^~\\n]+)~~")
        val ETIKET = Regex("(?<![\\w/])#([\\p{L}\\p{N}_-]{1,40})")
        // "!" ile başlayan gömme görseldir, bağlantı değil.
        val BAGLANTI = Regex("(?<!!)\\[\\[([^\\[\\]\\n]{1,80})]]")
        val GORSEL = Regex("!\\[([^\\]\\n]*)]\\(([^)\\n]+)\\)")
        /** Düz web adresi. Sondaki nokta, virgül, parantez adrese dahil edilmez. */
        val URL = Regex("(?<![\\w@/])https?://[^\\s<>\"'`\\[\\]()]*[^\\s<>\"'`\\[\\]().,;:!?]")

        /** Markdown bağlantısı: [metin](https://adres) */
        val MD_BAGLANTI = Regex("(?<!!)\\[([^\\[\\]\\n]+)]\\((https?://[^\\s)]+)\\)")

        /** Kod bloğu çiti: satır ``` ile başlar (dil adı gelebilir). */
        val KOD_CITI = Regex("^\\s*```")

        val GORSEL_WIKI = Regex("!\\[\\[([^\\[\\]\\n]{1,120})]]")

        /** Bilgi kutusu başlığı: `> [!uyarı]- Başlık` (tür, katlama işareti, başlık). */
        val CALLOUT = Regex("^> ?\\[!([\\p{L}\\p{N}_-]{1,30})]([+-]?)[ \\t]*(.*)$")

        /**
         * Callout türünün rengi, [Renkler.SECENEKLER] sırasıyla. Obsidian'ın
         * İngilizce adları ve Türkçe karşılıkları tanınır; bilinmeyen tür null
         * döner ve kutu vurgu rengini alır.
         */
        fun calloutRenkSirasi(tur: String): Int? = when (Arama.sadelestir(tur)) {
            "note", "not", "info", "bilgi", "todo", "yapilacak" -> 5
            "tip", "hint", "important", "ipucu", "onemli",
            "abstract", "summary", "tldr", "ozet" -> 6
            "success", "check", "done", "basari", "tamam" -> 7
            "question", "help", "faq", "soru" -> 0
            "warning", "caution", "attention", "uyari", "dikkat" -> 1
            "failure", "fail", "missing", "danger", "error", "bug", "hata", "tehlike" -> 2
            "example", "ornek" -> 4
            "quote", "cite", "alinti" -> 8
            else -> null
        }
    }
}
