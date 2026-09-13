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
    private val yogunluk = context.resources.displayMetrics.density
    private var vurgu = Renkler.vurgu(context)
    private var vurguUzeri = Renkler.vurguUzeri(context)

    fun renkleriYenile() {
        vurgu = Renkler.vurgu(context)
        vurguUzeri = Renkler.vurguUzeri(context)
    }

    /** Kaynak modunda hiçbir biçim uygulanmaz; ham Markdown görünür. */
    var kaynakModu = Prefs.kaynakModu(context)

    /** Görselleri çözmek için gereken depo; yoksa yer tutucu çizilir. */
    var depo: NotDeposu? = null

    /** Arka planda çözülen görsel gelince biçimlendirme yenilensin. */
    var gorselHazir: (() -> Unit)? = null

    /** Görselin sığacağı genişlik; her biçimlendirmede tazelenir. */
    private var satirGenisligi = 0

    fun uygula(s: Editable, imlec: Int, genislik: Int) {
        temizle(s)
        satirGenisligi = genislik
        if (s.isEmpty()) return

        if (kaynakModu) {
            s.setSpan(AbsoluteSizeSpan(GOVDE_SP, true), 0, s.length, EE)
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
        while (bas <= s.length) {
            var son = s.indexOf('\n', bas)
            if (son < 0) son = s.length
            satirBicimle(s, bas, son, satirNo == 0, imlec in bas..son, genislik)
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
    }

    private fun sadeBicimle(s: Editable) {
        val ilkSonu = s.indexOf('\n')
        val baslikSonu = if (ilkSonu < 0) s.length else ilkSonu
        if (baslikSonu > 0) {
            s.setSpan(StyleSpan(Typeface.BOLD), 0, baslikSonu, EE)
        }
        if (ilkSonu >= 0 && ilkSonu + 1 < s.length) {
            s.setSpan(AbsoluteSizeSpan(GOVDE_SP, true), ilkSonu + 1, s.length, EE)
        }
    }

    private fun satirBicimle(
        s: Editable,
        bas: Int,
        son: Int,
        baslikSatiri: Boolean,
        aktif: Boolean,
        genislik: Int
    ) {
        val satir = s.subSequence(bas, son).toString()

        if (baslikSatiri) {
            if (son > bas) s.setSpan(StyleSpan(Typeface.BOLD), bas, son, EE)
            if (onayKutusu(s, bas, son, satir)) return
            // Başlık satırında da kalın/italik/kod gibi işaretler çalışsın.
            satirIci(s, bas, son, aktif)
            return
        }

        // Boş satırlar da gövde boyutunda olmalı; yoksa satır aralığı bozulur.
        val boyutSonu = if (son > bas) son else minOf(son + 1, s.length)

        val ayrac = AYRAC.matches(satir)
        if (ayrac && genislik > 0) {
            if (aktif) {
                s.setSpan(AbsoluteSizeSpan(GOVDE_SP, true), bas, boyutSonu, EE)
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
                1 -> 22
                2 -> 19
                3 -> 17
                else -> GOVDE_SP
            }
            s.setSpan(AbsoluteSizeSpan(boyut, true), bas, boyutSonu, EE)
            s.setSpan(StyleSpan(Typeface.BOLD), bas, son, EE)
            isaret(s, bas, bas + baslik.value.length, aktif)
            satirIci(s, bas + baslik.value.length, son, aktif)
            return
        }

        s.setSpan(AbsoluteSizeSpan(GOVDE_SP, true), bas, boyutSonu, EE)

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
        }
        return true
    }

    /** Satır içi işaretler: görsel, kod, kalın, italik, üstü çizili. */
    private fun satirIci(s: Editable, bas: Int, son: Int, aktif: Boolean) {
        if (son <= bas) return
        val metin = s.subSequence(bas, son).toString()

        // Görsel önce: kapladığı aralıkta başka işaret aranmaz, yoksa dosya
        // adındaki * ya da _ yüzünden görselin üstüne span binerdi.
        val gorseller = gorselleriIsle(s, bas, metin, aktif)

        for (m in KOD.findAll(metin)) {
            if (kapsaniyor(gorseller, m.range)) continue
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
        const val GOVDE_SP = 16
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
        val GORSEL_WIKI = Regex("!\\[\\[([^\\[\\]\\n]{1,120})]]")
    }
}
