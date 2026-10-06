package com.ekosistem.notlar

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Notun bir satırının editör dışındaki görünüşü: kart özeti, widget'lar ve
 * sekme önizlemesi hepsi buradan geçer; not her yerde uygulamadakiyle aynı
 * görünsün. İşaretler gizlenir, biçim kalır: başlık kalın ve büyük, `**kalın**`,
 * `*eğik*`, `~~çizili~~`, `` `kod` ``, bağlantı ve #etiket vurgu renginde,
 * Obsidian kutusunun `> [!tip] Başlık` satırı türünün renginde kalın başlık
 * (başlık yoksa türün adı, editördeki gibi), görsel satırı gizli.
 *
 * Ayrıştırma ([coz]) saf Kotlin: testlerde denenir. Renkli hâli [bicimli].
 */
object NotOnizleme {

    /** Satır içi parça; [bicim] bit bayrakları. */
    class Parca(val metin: String, val bicim: Int = 0)

    class Satir(
        val parcalar: List<Parca>,
        /** 1–6 başlık düzeyi, 0 başlık değil. */
        val baslik: Int = 0,
        /** `> [!tür]` başlık satırı ise tür. */
        val kutuTuru: String? = null,
        /** `>` ile başlayan alıntı/kutu gövdesi. */
        val alinti: Boolean = false
    ) {
        /** Önek ("• ", "☐ ") dahil düz metin. */
        val metin: String get() = parcalar.joinToString("") { it.metin }
    }

    const val KALIN = 1
    const val EGIK = 2
    const val CIZIK = 4
    const val KOD = 8
    const val BAGLANTI = 16
    /** #etiket: vurgu renginde, bağlantı gibi altı çizili değil (editördeki gibi). */
    const val ETIKET = 64
    /** Madde, görev ve alıntı işareti; özette yer almaz. */
    const val ONEK = 32

    /** Kart özeti ve başlık için: önek ve işaret yok, yalnız okunan metin. */
    fun sade(satir: String): String =
        coz(satir).parcalar.filter { it.bicim and ONEK == 0 }.joinToString("") { it.metin }.trim()

    fun coz(ham: String): Satir {
        val girinti = ham.takeWhile { it == ' ' || it == '\t' }
        var s = ham.trim()
        if (s.isEmpty()) return Satir(emptyList())
        if (s == "---" || s == "***" || s == "___") return Satir(emptyList())
        // Görsel satırı: editörde resim olarak görünür, yazı olarak değil.
        if (GORSEL_SATIRI.matches(s)) return Satir(emptyList())

        MarkdownBicimci.CALLOUT.find(s)?.let { m ->
            val baslik = m.groupValues[3].trim().ifEmpty { m.groupValues[1] }
            return Satir(satirIci(baslik), kutuTuru = m.groupValues[1])
        }

        val parcalar = mutableListOf<Parca>()
        val ic = if (girinti.length >= 2) "  " else ""
        var baslik = 0
        var alinti = false
        val baslikEslesme = BASLIK.find(s)
        val onay = ONAY.find(s)
        when {
            baslikEslesme != null -> {
                baslik = baslikEslesme.groupValues[1].length
                s = baslikEslesme.groupValues[2]
            }
            s.startsWith(">") -> {
                alinti = true
                s = s.trimStart('>', ' ')
                parcalar.add(Parca("▍ ", ONEK))
            }
            onay != null -> {
                val isaretli = !onay.groupValues[1].equals(" ", true)
                s = s.substring(onay.value.length)
                parcalar.add(Parca(ic + if (isaretli) "☑ " else "☐ ", ONEK))
            }
            MADDE.containsMatchIn(s) -> {
                s = s.substring(2)
                parcalar.add(Parca("$ic• ", ONEK))
            }
            ic.isNotEmpty() -> parcalar.add(Parca(ic, ONEK))
        }
        parcalar.addAll(satirIci(s))
        return Satir(parcalar, baslik = baslik, alinti = alinti)
    }

    /** Satır içi işaretler: kalın, eğik, çizili, kod, bağlantı, etiket, görsel. */
    fun satirIci(metin: String, ust: Int = 0): List<Parca> {
        val sonuc = mutableListOf<Parca>()
        var son = 0
        for (m in SATIR_ICI.findAll(metin)) {
            if (m.range.first > son) sonuc.add(Parca(metin.substring(son, m.range.first), ust))
            val g = m.groups
            when {
                g[1] != null -> sonuc.addAll(satirIci(g[1]!!.value, ust or KALIN))
                g[2] != null -> sonuc.addAll(satirIci(g[2]!!.value, ust or KALIN))
                g[3] != null -> sonuc.addAll(satirIci(g[3]!!.value, ust or CIZIK))
                g[4] != null -> sonuc.addAll(satirIci(g[4]!!.value, ust or EGIK))
                g[5] != null -> sonuc.addAll(satirIci(g[5]!!.value, ust or EGIK))
                g[6] != null -> sonuc.add(Parca(g[6]!!.value, ust or KOD))
                // [[not|görünen ad]] görünen adıyla, [[not]] adıyla.
                g[7] != null -> sonuc.add(Parca((g[8]?.value ?: g[7]!!.value).trim(), ust or BAGLANTI))
                g[9] != null -> sonuc.add(Parca(g[9]!!.value, ust or BAGLANTI))
                g[10] != null -> sonuc.add(Parca(g[10]!!.value, ust or ETIKET))
                g[11] != null -> sonuc.add(Parca(g[11]!!.value, ust or BAGLANTI))
                else -> {} // satır içi görsel: gizlenir
            }
            son = m.range.last + 1
        }
        if (son < metin.length) sonuc.add(Parca(metin.substring(son), ust))
        return sonuc.filter { it.metin.isNotEmpty() }
    }

    /** Ekrandaki renkler (uygulama ya da widget teması). */
    class Stil(
        val vurgu: Int,
        val soluk: Int,
        val gece: Boolean
    ) {
        fun kutuRengi(tur: String): Int {
            val sira = MarkdownBicimci.calloutRenkSirasi(tur) ?: return vurgu
            val secenek = Renkler.SECENEKLER[sira]
            return if (gece) secenek.koyu else secenek.acik
        }
    }

    /** Uygulama ekranlarının renkleri (sekme önizlemesi, görevler). */
    fun uygulamaStili(c: android.content.Context): Stil = Stil(
        Renkler.vurgu(c),
        androidx.core.content.ContextCompat.getColor(c, R.color.metin_ikincil),
        Renkler.geceMi(c)
    )

    /**
     * Satırın biçimli hâli. [kutuRengi] doluysa satır o kutunun gövdesidir:
     * soldaki çizgi kutunun renginde (editördeki renkli kutu gibi).
     */
    fun bicimli(ham: String, stil: Stil, kutuRengi: Int? = null): CharSequence =
        bicimli(coz(ham), stil, kutuRengi)

    fun bicimli(satir: Satir, stil: Stil, kutuRengi: Int? = null): CharSequence {
        val sb = SpannableStringBuilder()
        for (p in satir.parcalar) {
            val bas = sb.length
            sb.append(p.metin)
            val son = sb.length
            if (son == bas) continue
            fun span(o: Any) = sb.setSpan(o, bas, son, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (p.bicim and KALIN != 0 && p.bicim and EGIK != 0) span(StyleSpan(Typeface.BOLD_ITALIC))
            else if (p.bicim and KALIN != 0) span(StyleSpan(Typeface.BOLD))
            else if (p.bicim and EGIK != 0) span(StyleSpan(Typeface.ITALIC))
            if (p.bicim and CIZIK != 0) span(StrikethroughSpan())
            if (p.bicim and KOD != 0) span(TypefaceSpan("monospace"))
            if (p.bicim and BAGLANTI != 0) {
                span(ForegroundColorSpan(stil.vurgu))
                span(android.text.style.UnderlineSpan())
            }
            if (p.bicim and ETIKET != 0) span(ForegroundColorSpan(stil.vurgu))
            if (p.bicim and ONEK != 0) span(ForegroundColorSpan(if (satir.alinti) kutuRengi ?: stil.soluk else stil.soluk))
        }
        if (sb.isEmpty()) return sb
        when {
            satir.kutuTuru != null -> {
                sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(stil.kutuRengi(satir.kutuTuru)), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            satir.baslik > 0 -> {
                sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val boy = when (satir.baslik) { 1 -> 1.3f; 2 -> 1.2f; 3 -> 1.1f; else -> 1f }
                if (boy > 1f) sb.setSpan(RelativeSizeSpan(boy), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private val BASLIK = Regex("^(#{1,6})\\s+(.*)$")
    private val ONAY = Regex("^[-*+] \\[(.)] ")
    private val MADDE = Regex("^[-*+] ")
    private val GORSEL_SATIRI = Regex("^(!\\[[^\\]]*]\\([^)]*\\)|!\\[\\[[^\\]]*]])\\s*$")

    /**
     * 1–2 kalın, 3 çizili, 4–5 eğik, 6 kod, 7–8 wiki bağlantı (ad, görünen ad),
     * 9 bağlantı metni, 10 etiket, 11 adres; grupsuz eşleşme satır içi görsel.
     * Alt çizgili eğik yalnız kelime sınırında (dosya_adi gibi yazılar bozulmasın).
     */
    private val SATIR_ICI = Regex(
        "\\*\\*(.+?)\\*\\*" +
            "|(?<![\\p{L}\\p{N}])__(.+?)__(?![\\p{L}\\p{N}])" +
            "|~~(.+?)~~" +
            "|\\*(?![\\s*])(.+?)(?<![\\s*])\\*" +
            "|(?<![\\p{L}\\p{N}_])_(?![\\s_])(.+?)(?<![\\s_])_(?![\\p{L}\\p{N}_])" +
            "|`([^`]+)`" +
            "|!\\[\\[[^\\]]*]]|!\\[[^\\]]*]\\([^)]*\\)" +
            "|\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?]]" +
            "|\\[([^\\]]+)]\\([^)]*\\)" +
            "|(?<![\\p{L}\\p{N}#&/])(#[\\p{L}\\p{N}_/-]*\\p{L}[\\p{L}\\p{N}_/-]*)" +
            "|((?:https?://|www\\.)[^\\s<>()]+)"
    )
}
