package com.ekosistem.notlar

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class Not(
    val uri: Uri,
    val ad: String,
    val baslik: String,
    val ozet: String,
    val degistirilme: Long,
    val sabit: Boolean,
    val klasor: String? = null,
    val eslesme: String? = null,
    val kilitli: Boolean = false
)

/** Bir notun içindeki tek bir görev satırı. */
data class Gorev(
    val notUri: Uri,
    val notBasligi: String,
    val satirNo: Int,
    val metin: String,
    val isaretli: Boolean
)

/**
 * Notlar düz Markdown dosyası olarak saklanır: kullanıcı klasör seçtiyse SAF
 * üzerinden o klasörde, seçmediyse uygulamanın kendi deposunda. Alt klasörler
 * uygulamada "klasör" olarak görünür; çöp kutusu gizli ".trash" klasörüdür.
 */
class NotDeposu(private val context: Context) {

    private val tr: Locale = Locale.forLanguageTag("tr-TR")

    fun kok(): DocumentFile {
        val uriStr = Prefs.klasorUri(context)
        if (uriStr != null) {
            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                if (doc != null && doc.canWrite()) return doc
            } catch (_: Exception) {
            }
        }
        val dir = File(context.filesDir, "notlar")
        if (!dir.exists()) dir.mkdirs()
        return DocumentFile.fromFile(dir)
    }

    private fun copKlasoru(olustur: Boolean): DocumentFile? {
        val k = kok()
        val mevcut = k.findFile(".trash")
        if (mevcut != null && mevcut.isDirectory) return mevcut
        return if (olustur) k.createDirectory(".trash") else null
    }

    /**
     * Görsellerin kopyalandığı klasör. Gizli değil — kullanıcı dosya
     * yöneticisinde ya da Obsidian'da görsellerini görebilsin diye. Buna karşılık
     * uygulamada klasör olarak listelenmez, içinde not aranmaz.
     */
    fun eklerKlasoru(olustur: Boolean): DocumentFile? {
        val k = kok()
        val mevcut = k.findFile(Gorseller.EKLER)
        if (mevcut != null && mevcut.isDirectory) return mevcut
        return if (olustur) k.createDirectory(Gorseller.EKLER) else null
    }

    private fun ozelKlasor(ad: String): Boolean = ad.startsWith(".") || ad == Gorseller.EKLER

    // --- Klasörler ---

    fun klasorAdlari(): List<String> =
        kok().listFiles()
            .filter { it.isDirectory && !ozelKlasor(it.name ?: ".") }
            .mapNotNull { it.name }
            .sortedWith(compareBy(Collator.getInstance(tr)) { it })

    fun klasorBul(ad: String): DocumentFile? =
        kok().listFiles().firstOrNull { it.isDirectory && it.name == ad }

    fun klasorOlustur(ad: String): Boolean {
        val temiz = adTemizle(ad)
        if (temiz.isEmpty()) return false
        if (klasorBul(temiz) != null) return true
        return kok().createDirectory(temiz) != null
    }

    fun klasorYenidenAdlandir(eski: String, yeni: String): Boolean {
        val temiz = adTemizle(yeni)
        if (temiz.isEmpty() || temiz == eski) return false
        val klasor = klasorBul(eski) ?: return false
        return try {
            klasor.renameTo(temiz)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Klasörün notlarını ana klasöre taşır, klasör boşaldıysa siler.
     *
     * Seçilen klasörde (SAF) silme, içindekilerle birlikte yapılır ve çöpe
     * uğramaz. Klasörde alt klasör, görsel, PDF ya da Obsidian dosyası kaldıysa
     * (veya bir not taşınamadıysa) klasöre dokunulmaz, [KLASOR_KISMEN] döner.
     */
    fun klasorSil(ad: String): Int {
        val klasor = klasorBul(ad) ?: return KLASOR_HATA
        val hedef = kok()
        for (f in klasor.listFiles()) {
            val dosyaAdi = f.name ?: continue
            if (f.isFile && notDosyasi(dosyaAdi)) {
                hedefeTasi(f.uri, hedef, klasor)
            }
        }
        if (klasor.listFiles().isNotEmpty()) return KLASOR_KISMEN
        return try {
            if (klasor.delete()) KLASOR_SILINDI else KLASOR_HATA
        } catch (_: Exception) {
            KLASOR_HATA
        }
    }

    private fun adTemizle(ad: String): String =
        ad.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").take(40).let {
            if (it.startsWith(".")) "" else it
        }

    // --- Listeleme ---

    fun notlariListele(sorgu: String?, klasorAdi: String? = null): List<Not> {
        val baslangic = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        val temizSorgu = sorgu?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(tr)
        // Klasör içindeyken de notun klasörü bilinsin ki taşıma geri alınabilsin.
        topla(baslangic, sonuc, Prefs.sabitler(context), temizSorgu, klasorAdi)
        return sirala(sonuc)
    }

    private fun sirala(notlar: List<Not>): List<Not> {
        val collator = Collator.getInstance(tr)
        val karsilastirici = when (Prefs.siralama(context)) {
            1 -> compareBy<Not> { it.degistirilme }
            2 -> Comparator<Not> { a, b -> collator.compare(a.baslik, b.baslik) }
            3 -> Comparator<Not> { a, b -> collator.compare(b.baslik, a.baslik) }
            else -> compareByDescending { it.degistirilme }
        }
        return notlar.sortedWith(compareByDescending<Not> { it.sabit }.then(karsilastirici))
    }

    private fun topla(
        dir: DocumentFile,
        sonuc: MutableList<Not>,
        sabitler: Set<String>,
        sorgu: String?,
        etiket: String?
    ) {
        for (f in dir.listFiles()) {
            val ad = f.name ?: continue
            if (f.isDirectory) {
                // Şablonlar yalnızca kendi çipi seçiliyken listelenir; yoksa
                // "Tümü" listesine, aramaya ve görevlere karışırlardı.
                val sablonKlasoru = etiket == null && ad == Sablonlar.KLASOR
                if (!ozelKlasor(ad) && !sablonKlasoru) {
                    topla(f, sonuc, sabitler, sorgu, etiket ?: ad)
                }
                continue
            }
            if (!notDosyasi(ad)) continue
            val icerik = oku(f.uri, 8192)
            var eslesmeSatiri: String? = null
            if (sorgu != null) {
                // Kilitli notta yalnızca dosya adı aranır; içeriği aramaya sızmaz.
                val kilitli = Kilit.notKilitli(context, f.uri.toString())
                val aranacak = if (kilitli) ad else ad + "\n" + icerik
                if (!aranacak.lowercase(tr).contains(sorgu)) continue
                if (!kilitli) {
                    eslesmeSatiri = icerik.lines()
                        .firstOrNull { it.lowercase(tr).contains(sorgu) }
                        ?.let { mdTemizle(it) }
                        ?.takeIf { it.isNotBlank() }
                }
            }
            sonuc.add(notYap(f, icerik, sabitler, etiket, eslesmeSatiri))
        }
    }

    fun copListele(): List<Not> {
        val cop = copKlasoru(false) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        for (f in cop.listFiles()) {
            val ad = f.name ?: continue
            if (!f.isFile || !notDosyasi(ad)) continue
            sonuc.add(notYap(f, oku(f.uri, 1024), emptySet(), null, null))
        }
        return sonuc.sortedByDescending { it.degistirilme }
    }

    /** Tüm notlardaki onay kutusu satırlarını toplar. */
    fun gorevleriListele(tamamlananlar: Boolean): List<Gorev> {
        val sonuc = mutableListOf<Gorev>()
        for (not in notlariListele(null, null)) {
            if (not.kilitli) continue
            val satirlar = oku(not.uri).lines()
            satirlar.forEachIndexed { indeks, satir ->
                val eslesme = MarkdownBicimci.ONAY.find(satir) ?: return@forEachIndexed
                val isaretli = !eslesme.groupValues[2].equals(" ", true)
                if (isaretli && !tamamlananlar) return@forEachIndexed
                val metin = satir.substring(eslesme.value.length).trim()
                if (metin.isEmpty()) return@forEachIndexed
                sonuc.add(Gorev(not.uri, not.baslik, indeks, metin, isaretli))
            }
        }
        return sonuc
    }

    /** Bir görev satırının işaretini değiştirir. */
    fun gorevDegistir(gorev: Gorev): Boolean {
        val satirlar = oku(gorev.notUri).lines().toMutableList()
        if (gorev.satirNo !in satirlar.indices) return false
        val satir = satirlar[gorev.satirNo]
        val eslesme = MarkdownBicimci.ONAY.find(satir) ?: return false
        val girinti = eslesme.groupValues[1].length
        val isaretli = !eslesme.groupValues[2].equals(" ", true)
        val yeniIsaret = if (isaretli) " " else "x"
        satirlar[gorev.satirNo] =
            satir.substring(0, girinti + 3) + yeniIsaret + satir.substring(girinti + 4)
        return yaz(gorev.notUri, satirlar.joinToString("\n"))
    }

    private fun notDosyasi(ad: String): Boolean =
        ad.endsWith(".md", true) || ad.endsWith(".txt", true)

    private fun notYap(
        f: DocumentFile,
        icerik: String,
        sabitler: Set<String>,
        klasor: String?,
        eslesme: String?
    ): Not {
        val satirlar = icerik.lines()
        val ilkIndex = satirlar.indexOfFirst { it.isNotBlank() }
        val ilk = if (ilkIndex >= 0) mdTemizle(satirlar[ilkIndex]) else ""
        val dosyaAdi = f.name ?: "not"
        val baslik = ilk.ifBlank { dosyaAdi.removeSuffix(".md").removeSuffix(".txt") }
        val ozet = if (ilkIndex >= 0) {
            satirlar.drop(ilkIndex + 1)
                .filter { it.isNotBlank() }
                .joinToString(" ") { mdTemizle(it) }
                .take(150)
        } else ""
        val adres = f.uri.toString()
        // Kopyalanarak taşınmış notun gerçek tarihi ayrıca saklanır.
        val korunan = Prefs.zamanDamgasi(context, adres)
        val kilitli = Kilit.notKilitli(context, adres)
        return Not(
            uri = f.uri,
            ad = dosyaAdi,
            baslik = baslik.take(80),
            // Kilitli notun içeriği listeye, widget'a ve göreve hiç çıkmaz.
            ozet = if (kilitli) "" else ozet,
            degistirilme = if (korunan > 0) korunan else f.lastModified(),
            sabit = sabitler.contains(adres),
            klasor = klasor,
            eslesme = if (kilitli) null else eslesme,
            kilitli = kilitli
        )
    }

    /** Kart önizlemesi için satırdaki Markdown işaretlerini söker. */
    private fun mdTemizle(satir: String): String =
        satir.trim()
            // Görsel bağlantısı önizlemede ham metin olarak görünmesin.
            .replace(MarkdownBicimci.GORSEL, "")
            .replace(MarkdownBicimci.GORSEL_WIKI, "")
            .trimStart('#', '>', ' ')
            .removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- [X]").removePrefix("- ")
            .replace(ISARETLER, "")
            .trim()

    // --- Okuma / yazma ---

    fun oku(uri: Uri, limit: Int = Int.MAX_VALUE): String = okuKesin(uri, limit) ?: ""

    /**
     * Okuma başarısızsa null döner. Editör bunu kullanır: okunamayan notu boş
     * sanıp açarsa kullanıcının yazdığı ilk harf asıl notun üzerine yazılırdı.
     */
    fun okuKesin(uri: Uri, limit: Int = Int.MAX_VALUE): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { akis ->
                val bytes = if (limit == Int.MAX_VALUE) {
                    akis.readBytes()
                } else {
                    val tampon = ByteArray(limit)
                    var toplam = 0
                    while (toplam < limit) {
                        val n = akis.read(tampon, toplam, limit - toplam)
                        if (n < 0) break
                        toplam += n
                    }
                    tampon.copyOf(toplam)
                }
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun yaz(uri: Uri, metin: String): Boolean {
        val bayt = metin.toByteArray(Charsets.UTF_8)
        val tamam = if (uri.scheme == "file") {
            // Uygulama deposu: geçici dosya + yeniden adlandırma, yarım dosya kalmaz.
            val yol = uri.path ?: return false
            DosyaYazici.atomikYaz(File(yol), bayt)
        } else {
            saglayiciyaYaz(uri, bayt)
        }
        // Not yeniden yazıldı; artık dosyanın kendi tarihi geçerli.
        if (tamam && Prefs.zamanDamgasi(context, uri.toString()) > 0) {
            Prefs.zamanDamgasiKaydet(context, uri.toString(), 0L)
        }
        return tamam
    }

    /**
     * Kullanıcının seçtiği klasör (SAF) yeniden adlandırarak değiştirmeye izin
     * vermez; adres değişir, sabitleme ve kilit kaybolurdu. Burada dosya yerinde
     * yazılır ve diske işlenir. Yarıda kalma riskine karşı editör aynı metni önce
     * [Taslaklar]'a koyar, dosya yazılınca taslağı siler.
     */
    private fun saglayiciyaYaz(uri: Uri, bayt: ByteArray): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { akis ->
                    akis.write(bayt)
                    akis.flush()
                    try {
                        akis.fd.sync()
                    } catch (_: Exception) {
                        // Bazı sağlayıcılar (ör. ağ sürücüleri) sync desteklemez.
                    }
                }
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun notOlustur(icerik: String, klasorAdi: String? = null, ad: String? = null): Uri? {
        val hedef = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: kok()
        return dosyaOlustur(hedef, icerik, ad)
    }

    private fun dosyaOlustur(hedef: DocumentFile, icerik: String, istenenAd: String? = null): Uri? {
        var ad = istenenAd ?: icerik.lines().firstOrNull { it.isNotBlank() }
            ?.let { mdTemizle(it) }
            ?.take(40)
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "")
            ?.trim()
            ?: ""
        if (ad.isBlank()) ad = "not"
        ad = ad.removeSuffix(".md").removeSuffix(".txt")
        var tekilAd = ad
        var i = 2
        while (hedef.findFile("$tekilAd.md") != null || hedef.findFile(tekilAd) != null) {
            tekilAd = "$ad-$i"
            i++
        }
        val f = hedef.createFile("text/markdown", tekilAd) ?: return null
        val sonAd = f.name ?: tekilAd
        if (!sonAd.endsWith(".md", true) && !sonAd.endsWith(".txt", true)) {
            f.renameTo("$tekilAd.md")
        }
        return if (yaz(f.uri, icerik)) f.uri else null
    }

    fun docGetir(uri: Uri): DocumentFile? =
        if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }

    // --- Taşıma / silme (yeni konumun adresini döndürür ki geri alınabilsin) ---

    /**
     * Notu başka bir klasöre taşır. Önce gerçek taşıma denenir — dosya aynı
     * dosya kalır, değiştirilme tarihi korunur. Sağlayıcı taşımayı desteklemezse
     * kopyalanıp silinir; o durumda tarih ayrıca saklanır, yoksa taşınan (ve
     * geri alınan) her not "az önce oluşturulmuş" gibi görünürdü.
     */
    private fun hedefeTasi(uri: Uri, hedef: DocumentFile, kaynakUst: DocumentFile? = null): Uri? {
        val f = docGetir(uri) ?: return null
        val zaman = Prefs.zamanDamgasi(context, uri.toString()).takeIf { it > 0 } ?: f.lastModified()
        val gecmisAnahtari = goreliParcalar(uri)?.let { gecmisAnahtari(it) }
        val yeni = gercektenTasi(f, hedef, kaynakUst)
            ?: kopyalayarakTasi(f, uri, hedef, zaman)
            ?: return null
        ayarlariTasi(uri, yeni)
        gecmisiTasi(gecmisAnahtari, yeni)
        return yeni
    }

    private fun gercektenTasi(
        f: DocumentFile,
        hedef: DocumentFile,
        kaynakUst: DocumentFile?
    ): Uri? {
        val ad = f.name ?: return null
        if (hedef.findFile(ad) != null) return null // ad çakışması: kopyalama yolu adı tekilleştirir

        // Uygulama deposu düz dosya sistemi: yeniden adlandırmak taşımaktır.
        if (f.uri.scheme == "file" && hedef.uri.scheme == "file") {
            val kaynakDosya = f.uri.path?.let { File(it) } ?: return null
            val hedefDizin = hedef.uri.path?.let { File(it) } ?: return null
            val hedefDosya = File(hedefDizin, ad)
            return if (kaynakDosya.renameTo(hedefDosya)) Uri.fromFile(hedefDosya) else null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val ust = kaynakUst ?: return null
        return try {
            DocumentsContract.moveDocument(context.contentResolver, f.uri, ust.uri, hedef.uri)
        } catch (_: Exception) {
            null
        }
    }

    private fun kopyalayarakTasi(
        f: DocumentFile,
        uri: Uri,
        hedef: DocumentFile,
        zaman: Long
    ): Uri? {
        val icerik = oku(uri)
        val ad = (f.name ?: "not.md").removeSuffix(".md").removeSuffix(".txt")
        val yeni = dosyaOlustur(hedef, icerik, ad) ?: return null
        if (zaman > 0) Prefs.zamanDamgasiKaydet(context, yeni.toString(), zaman)
        try {
            f.delete()
        } catch (_: Exception) {
        }
        return yeni
    }

    /** Adres değişti: sabitleme, not kilidi, widget ve hatırlatıcı yeni adrese geçer. */
    private fun ayarlariTasi(eski: Uri, yeni: Uri) {
        val e = eski.toString()
        val y = yeni.toString()
        if (e == y) return
        Prefs.adresTasi(context, e, y)
        Taslaklar(context).tasi(e, y)
        val hatirlatma = Prefs.hatirlatici(context, e)
        if (hatirlatma > 0) {
            Hatirlatici.kaldir(context, e)
            Hatirlatici.kur(context, y, hatirlatma)
        }
    }

    /** Silinen notun hangi klasörden geldiği kaydedilir ki geri alınca oraya dönsün. */
    fun copeTasi(uri: Uri): Uri? {
        val ust = ustDizin(uri)
        val kaynakKlasor = ust?.name?.takeIf { ust.uri != kok().uri }
        val cop = copKlasoru(true) ?: return null
        val yeni = hedefeTasi(uri, cop, ust) ?: return null
        Prefs.copKaynagiKaydet(context, yeni.toString(), kaynakKlasor)
        return yeni
    }

    fun geriYukle(uri: Uri): Uri? {
        val kaynakKlasor = Prefs.copKaynagi(context, uri.toString())
        val hedef = kaynakKlasor?.let { klasorBul(it) } ?: kok()
        val yeni = hedefeTasi(uri, hedef, copKlasoru(false))
        Prefs.copKaynagiSil(context, uri.toString())
        yeni?.let { Prefs.copKaynagiSil(context, it.toString()) }
        return yeni
    }

    fun klasoreTasi(uri: Uri, klasorAdi: String?): Uri? {
        val hedef = if (klasorAdi == null) kok() else klasorBul(klasorAdi) ?: return null
        return hedefeTasi(uri, hedef, ustDizin(uri))
    }

    fun kaliciSil(uri: Uri): Boolean {
        sabitTemizle(uri)
        Prefs.copKaynagiSil(context, uri.toString())
        Prefs.zamanDamgasiKaydet(context, uri.toString(), 0L)
        Taslaklar(context).sil(uri.toString())
        return docGetir(uri)?.delete() ?: false
    }

    /** Bir notun hangi klasörde olduğunu bulur (ana klasördeyse null). */
    fun notunKlasoru(uri: Uri): String? {
        for (klasor in klasorAdlari()) {
            val dizin = klasorBul(klasor) ?: continue
            if (dizin.listFiles().any { it.uri == uri }) return klasor
        }
        return null
    }

    /** Notun bulunduğu dizin: ana klasör, bir alt klasör ya da çöp kutusu. */
    private fun ustDizin(uri: Uri): DocumentFile? {
        val k = kok()
        if (k.listFiles().any { it.uri == uri }) return k
        for (klasor in klasorAdlari()) {
            val dizin = klasorBul(klasor) ?: continue
            if (dizin.listFiles().any { it.uri == uri }) return dizin
        }
        val cop = copKlasoru(false)
        if (cop != null && cop.listFiles().any { it.uri == uri }) return cop
        return null
    }

    private fun sabitTemizle(uri: Uri) {
        val id = uri.toString()
        if (Prefs.sabitler(context).contains(id)) Prefs.sabitDegistir(context, id)
    }


    // --- Etiketler ve bağlantılar ---

    /** Tüm notlardaki #etiketleri toplar. */
    fun etiketleriListele(): List<String> {
        val bulunan = sortedSetOf<String>(Collator.getInstance(tr))
        for (not in notlariListele(null, null)) {
            for (e in MarkdownBicimci.ETIKET.findAll(oku(not.uri, 8192))) {
                bulunan.add(e.groupValues[1])
            }
        }
        return bulunan.toList()
    }

    fun etiketliNotlar(etiket: String): List<Not> {
        val kucuk = etiket.lowercase(tr)
        return notlariListele(null, null).filter { not ->
            MarkdownBicimci.ETIKET.findAll(oku(not.uri, 8192))
                .any { it.groupValues[1].lowercase(tr) == kucuk }
        }
    }

    /** Başlığı verilen notu bulur; yoksa null. */
    fun baslikIleBul(baslik: String): Not? {
        val kucuk = baslik.trim().lowercase(tr)
        return notlariListele(null, null).firstOrNull {
            it.baslik.lowercase(tr) == kucuk ||
                it.ad.removeSuffix(".md").removeSuffix(".txt").lowercase(tr) == kucuk
        }
    }

    /** Bu nota [[bağlantı]] ile işaret eden notlar. */
    fun geriBaglantilar(not: Not): List<Not> {
        val hedefler = setOf(
            not.baslik.lowercase(tr),
            not.ad.removeSuffix(".md").removeSuffix(".txt").lowercase(tr)
        )
        return notlariListele(null, null).filter { aday ->
            aday.uri != not.uri && MarkdownBicimci.BAGLANTI.findAll(oku(aday.uri, 8192))
                .any { hedefler.contains(it.groupValues[1].trim().lowercase(tr)) }
        }
    }

    // --- Sürüm geçmişi ---

    private fun gecmisKlasoru(olustur: Boolean): DocumentFile? {
        val k = kok()
        val mevcut = k.findFile(".gecmis")
        if (mevcut != null && mevcut.isDirectory) return mevcut
        return if (olustur) k.createDirectory(".gecmis") else null
    }

    /**
     * Notun ana klasöre göre yolu: ["fikir.md"] ya da ["İş", "fikir.md"].
     * Önce adresin kendisinden çıkarılır (hızlı); sağlayıcı adresi anlamsız bir
     * kimlikle veriyorsa klasörler tek tek taranır.
     */
    private fun goreliParcalar(uri: Uri): List<String>? {
        val k = kok()
        try {
            if (uri.scheme == "file" && k.uri.scheme == "file") {
                val kokYolu = File(k.uri.path ?: return null).canonicalPath + "/"
                val yol = File(uri.path ?: return null).canonicalPath
                if (yol.startsWith(kokYolu)) return yol.removePrefix(kokYolu).split('/')
            } else if (uri.scheme == "content" && k.uri.scheme == "content") {
                val kokKimligi = DocumentsContract.getTreeDocumentId(k.uri) + "/"
                val kimlik = DocumentsContract.getDocumentId(uri)
                if (kimlik.startsWith(kokKimligi)) return kimlik.removePrefix(kokKimligi).split('/')
            }
        } catch (_: Exception) {
        }
        val ad = docGetir(uri)?.name ?: return null
        val ust = ustDizin(uri) ?: return null
        return if (ust.uri == k.uri) listOf(ad) else listOf(ust.name ?: return null, ad)
    }

    private fun gecmisDizini(uri: Uri, olustur: Boolean): DocumentFile? {
        val kok = gecmisKlasoru(olustur) ?: return null
        val anahtar = gecmisAnahtari(goreliParcalar(uri) ?: return null)
        return kok.findFile(anahtar)?.takeIf { it.isDirectory }
            ?: if (olustur) kok.createDirectory(anahtar) else null
    }

    /** Not taşınınca geçmişi de yeni anahtarına geçer; yoksa taşınan notun geçmişi kaybolurdu. */
    private fun gecmisiTasi(eskiAnahtar: String?, yeni: Uri) {
        eskiAnahtar ?: return
        val yeniAnahtar = gecmisAnahtari(goreliParcalar(yeni) ?: return)
        if (eskiAnahtar == yeniAnahtar) return
        val kok = gecmisKlasoru(false) ?: return
        val eski = kok.findFile(eskiAnahtar)?.takeIf { it.isDirectory } ?: return
        if (kok.findFile(yeniAnahtar) != null) return
        try {
            eski.renameTo(yeniAnahtar)
        } catch (_: Exception) {
        }
    }

    /** Kaydetmeden önceki hali gizli klasöre yedekler (en fazla 20 sürüm). */
    fun gecmiseYaz(uri: Uri, icerik: String) {
        if (icerik.isBlank()) return
        val dizin = gecmisDizini(uri, true) ?: return
        val damga = System.currentTimeMillis().toString()
        val dosya = dizin.createFile("text/markdown", damga) ?: return
        yaz(dosya.uri, icerik)

        val surumler = dizin.listFiles().filter { it.isFile }.sortedBy { it.name }
        if (surumler.size > GECMIS_SINIRI) {
            surumler.take(surumler.size - GECMIS_SINIRI).forEach { it.delete() }
        }
    }

    data class Surum(val uri: Uri, val zaman: Long)

    fun gecmisiListele(uri: Uri): List<Surum> {
        val dizin = gecmisDizini(uri, false) ?: return emptyList()
        return dizin.listFiles()
            .filter { it.isFile }
            .mapNotNull { dosya ->
                val damga = (dosya.name ?: "").removeSuffix(".md").toLongOrNull() ?: return@mapNotNull null
                Surum(dosya.uri, damga)
            }
            .sortedByDescending { it.zaman }
    }

    companion object {
        /**
         * Nota yazan her iş (kaydet, taşı, sil) bu tek iş parçacığında sırayla
         * çalışır. Ayrı ayrı çalıştıklarında "klasöre taşı" kaydı beklemiyor,
         * son yazılanlar eski konuma gidip notu ikiye bölüyordu.
         */
        val yazici: ExecutorService = Executors.newSingleThreadExecutor()

        /**
         * Editörün son kaydı. Liste, tazelemeden önce bunu bekler; yoksa notu
         * düzenleyip geri dönünce kartta bir süre eski özet kalırdı.
         */
        @Volatile
        var bekleyenKayit: Future<*>? = null

        const val KLASOR_SILINDI = 0
        const val KLASOR_KISMEN = 1
        const val KLASOR_HATA = 2

        private const val GECMIS_SINIRI = 20

        /**
         * Sürüm geçmişi klasörünün adı. Ana klasördeki not yalnızca adıyla
         * anılır (eski kayıtlar bozulmasın); alt klasördeki not klasör adıyla
         * birlikte. Önceden yalnızca ad kullanılıyordu ve "İş/fikir" ile
         * "Kişisel/fikir" aynı geçmişi paylaşıyordu.
         */
        fun gecmisAnahtari(parcalar: List<String>): String {
            val ad = parcalar.last().removeSuffix(".md").removeSuffix(".txt")
            return (parcalar.dropLast(1) + ad).joinToString("__")
        }
        private val ISARETLER = Regex("\\*{1,3}|~~|__|`|\\[\\[|]]")
    }
}