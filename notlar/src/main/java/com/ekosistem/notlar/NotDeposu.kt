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
    val kilitli: Boolean = false,
    /** Onay kutusu sayısı ve işaretli olanlar; kartta "3/7" rozeti. */
    val gorev: Int = 0,
    val biten: Int = 0
)

/** Editörden listeye iletilen değişiklik (bkz. [NotDeposu.sonDuzenleme]). */
data class Duzenleme(
    val uri: Uri,
    val metin: String,
    val zaman: Long,
    /** Yeni notun klasörü; var olan not kendi klasöründe kalır. */
    val klasor: String?,
    val silindi: Boolean = false
)

/** Bir notun içindeki tek bir görev satırı. */
data class Gorev(
    val notUri: Uri,
    val notBasligi: String,
    val satirNo: Int,
    val metin: String,
    val isaretli: Boolean,
    /** Son tarihin gün numarası ([SonTarih]); yoksa null. */
    val sonGun: Long? = null
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
        val mevcut = cocukBul(k, ".trash")
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
        val mevcut = cocukBul(k, Gorseller.EKLER)
        if (mevcut != null && mevcut.isDirectory) return mevcut
        return if (olustur) k.createDirectory(Gorseller.EKLER) else null
    }

    private fun ozelKlasor(ad: String): Boolean = ad.startsWith(".") || ad == Gorseller.EKLER

    // --- Klasörler ---

    fun klasorAdlari(): List<String> =
        girdiler(kok().uri)
            .filter { it.dizin && !ozelKlasor(it.ad) }
            .map { it.ad }
            .sortedWith(compareBy(Collator.getInstance(tr)) { it })

    fun klasorBul(ad: String): DocumentFile? =
        cocukBul(kok(), ad)?.takeIf { it.isDirectory }

    /**
     * DocumentFile.findFile'ın karşılığı. O, klasördeki her dosyanın adını
     * sağlayıcıya ayrı ayrı soruyordu: kökte 1.000 not varken her kayıtta
     * (geçmiş klasörünü bulmak için) 1.000 sorgu. Bu tek sorgu yapar.
     */
    fun cocukBul(dizin: DocumentFile, ad: String): DocumentFile? {
        val g = girdiler(dizin.uri).firstOrNull { it.ad == ad } ?: return null
        return if (g.uri.scheme == "file") {
            DocumentFile.fromFile(File(g.uri.path ?: return null))
        } else {
            DocumentFile.fromTreeUri(context, g.uri)
        }
    }

    /** Dizindeki bütün adlar tek sorguda; tekil ad üretirken döngüde kullanılır. */
    fun cocukAdlari(dizin: DocumentFile): Set<String> =
        girdiler(dizin.uri).mapTo(HashSet()) { it.ad }

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
        // Adres klasör adını içerir: sabitleme, not kilidi, hatırlatıcı ve
        // taslak adrese bağlı. Önceden yeniden adlandırma bunları koparıyordu;
        // kilitli bir notun klasörünü yeniden adlandırmak kilidini açıyordu.
        val eskiAdresler = girdiler(klasor.uri).filter { !it.dizin }.associate { it.ad to it.uri }
        val oldu = try {
            klasor.renameTo(temiz)
        } catch (_: Exception) {
            false
        }
        if (!oldu) return false
        klasorBul(temiz)?.let { yeniKlasor ->
            val degisim = girdiler(yeniKlasor.uri)
                .filter { !it.dizin }
                .mapNotNull { g -> eskiAdresler[g.ad]?.let { it to g.uri } }
            ayarlariTopluTasi(degisim)
        }
        klasorGecmisiniTasi(eski, temiz)
        return true
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

    /** Klasör adının dosya sisteminde kullanılacak hali (arayüz de aynısını göstersin). */
    fun adTemizle(ad: String): String =
        ad.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").take(40).let {
            if (it.startsWith(".")) "" else it
        }

    // --- Listeleme ---

    fun notlariListele(sorgu: String?, klasorAdi: String? = null): List<Not> =
        listele(sorgu, klasorAdi, icerikGerekli = false)

    /**
     * [icerikGerekli]: etiket, bağlantı, görev gibi içeriğe bakan işler için
     * her notun ilk 8 KB'ı önbelleğe alınır; [onizlemeIcerigi] oradan okur.
     */
    private fun listele(sorgu: String?, klasorAdi: String?, icerikGerekli: Boolean): List<Not> {
        onbellegiHazirla()
        val kokDizin = kok()
        val baslangic = if (klasorAdi == null) kokDizin else klasorBul(klasorAdi) ?: return emptyList()
        val sonuc = mutableListOf<Not>()
        val temizSorgu = Arama.ifade(sorgu)
        val gorulen = mutableSetOf<String>()
        // Klasör içindeyken de notun klasörü bilinsin ki taşıma geri alınabilsin.
        val notlar = mutableListOf<Pair<Girdi, String?>>()
        notGirdileri(baslangic.uri, klasorAdi, notlar)
        val icerikLazim = icerikGerekli || temizSorgu != null
        onceOku(notlar.map { it.first }, icerikLazim)
        val sabitler = Prefs.sabitler(context)
        for ((g, klasor) in notlar) {
            val adres = g.uri.toString()
            gorulen.add(adres)
            val kayit = onizleme(g, icerikLazim) ?: continue
            var eslesmeSatiri: String? = null
            if (temizSorgu != null) {
                val icerik = kayit.icerik.orEmpty()
                // Kilitli notta yalnızca dosya adı aranır; içeriği aramaya sızmaz.
                val kilitli = Kilit.notKilitli(context, adres)
                // Şifreli notun verisi aranmaz (rastgele harfler sahte eşleşme verirdi).
                val sifreli = Sifreleme.sifreliMi(icerik)
                val aranacak = if (kilitli || sifreli) g.ad else g.ad + "\n" + icerik
                if (!Arama.sadelestir(aranacak).contains(temizSorgu)) continue
                if (!kilitli && !sifreli) {
                    eslesmeSatiri = icerik.lineSequence()
                        .firstOrNull { Arama.sadelestir(it).contains(temizSorgu) }
                        ?.let { mdTemizle(it) }
                        ?.takeIf { it.isNotBlank() }
                }
            }
            sonuc.add(notYap(g, kayit, sabitler, klasor, eslesmeSatiri))
        }
        if (klasorAdi == null && temizSorgu == null) {
            // Soğuk açılışta anında gösterilecek "son bilinen liste".
            ONBELLEK.anaListeyiYaz(notlar.associate { (g, k) -> g.uri.toString() to ListeOnbellegi.AnaGirdi(g.ad, k) })
        }
        if (klasorAdi == null && ONBELLEK.boyut > gorulen.size + ONBELLEK_PAYI) {
            // Silinen, taşınan ya da başka klasöre geçilince eskiyen kayıtlar.
            ONBELLEK.yalnizcaBunlarKalsin(gorulen)
        }
        if (ONBELLEK.kirli) ONBELLEK.diskeYaz(onbellekDosyasi())
        return sirala(sonuc)
    }

    private fun onbellekDosyasi() = File(context.cacheDir, "liste-onbellegi")

    /**
     * Son bilinen "Tümü" listesi, hiçbir klasör taranmadan. Soğuk açılışta
     * liste 1-2 saniye boş kalmasın diye hemen gösterilir; gerçek tarama
     * bitince yerine geçer. Kilit, sabitleme ve korunan tarih güncel ayardan.
     */
    fun onbellektenListe(): List<Not> {
        onbellegiHazirla()
        val sabitler = Prefs.sabitler(context)
        val notlar = ONBELLEK.anaListe().mapNotNull { (adres, g) ->
            val k = ONBELLEK.kayit(adres) ?: return@mapNotNull null
            val kilitli = Kilit.notKilitli(context, adres)
            Not(
                uri = Uri.parse(adres),
                ad = g.ad,
                baslik = k.baslik,
                ozet = if (kilitli) "" else k.ozet,
                degistirilme = Prefs.gosterilenZaman(context, adres, k.degistirilme),
                sabit = sabitler.contains(adres),
                klasor = g.klasor,
                kilitli = kilitli,
                gorev = if (kilitli) 0 else k.gorev,
                biten = if (kilitli) 0 else k.biten
            )
        }
        return sirala(notlar)
    }

    /** Süreç başladıktan sonraki ilk listede diskteki önbellek yüklenir. */
    private fun onbellegiHazirla() {
        if (onbellekYuklendi) return
        synchronized(ONBELLEK) {
            if (onbellekYuklendi) return
            ONBELLEK.disktenOku(onbellekDosyasi())
            onbellekYuklendi = true
        }
    }

    /**
     * Arama kutusuna dokunulunca çağrılır: notların içeriği arka planda belleğe
     * alınır. İlk aramada 1.000 not seçilen klasörden okunurken 5 saniye
     * bekleniyordu; kullanıcı yazarken bu iş bitmiş olur.
     */
    fun aramaIcinHazirla() {
        listele(null, null, icerikGerekli = true)
    }

    /** Notun tamamı: önbellekteki içerik sınırın altındaysa zaten tamdır. */
    private fun tamIcerik(uri: Uri): String {
        val onbellekte = ONBELLEK.icerik(uri.toString())
        if (onbellekte != null && onbellekte.toByteArray(Charsets.UTF_8).size < ONIZLEME_SINIRI) {
            return onbellekte
        }
        return oku(uri)
    }

    /** Listelemede önbelleğe alınan ilk 8 KB; yoksa dosyadan okunur. */
    private fun onizlemeIcerigi(uri: Uri): String =
        ONBELLEK.icerik(uri.toString()) ?: oku(uri, ONIZLEME_SINIRI)

    fun sirala(notlar: List<Not>): List<Not> {
        val collator = Collator.getInstance(tr)
        val karsilastirici = when (Prefs.siralama(context)) {
            1 -> compareBy<Not> { it.degistirilme }
            2 -> Comparator<Not> { a, b -> collator.compare(a.baslik, b.baslik) }
            3 -> Comparator<Not> { a, b -> collator.compare(b.baslik, a.baslik) }
            else -> compareByDescending { it.degistirilme }
        }
        return notlar.sortedWith(compareByDescending<Not> { it.sabit }.then(karsilastirici))
    }

    /** Bir dizindeki tek girdi; özellikleri tek seferde okunmuş halde. */
    class Girdi(
        val uri: Uri,
        val ad: String,
        val dizin: Boolean,
        val degistirilme: Long,
        /** Bilinmiyorsa -1 (bazı sağlayıcılar vermez). */
        val boyut: Long
    )

    /**
     * Dizinin içeriği, özellikleriyle birlikte. DocumentFile her girdinin adını,
     * türünü ve tarihini ayrı ayrı sağlayıcıya soruyordu (seçilen klasörde not
     * başına 3 sorgu); burada klasör başına tek sorgu yapılır.
     */
    fun girdiler(dizinUri: Uri): List<Girdi> {
        if (dizinUri.scheme == "file") {
            val dizin = File(dizinUri.path ?: return emptyList())
            return dizin.listFiles()?.map {
                Girdi(Uri.fromFile(it), it.name, it.isDirectory, it.lastModified(), it.length())
            } ?: emptyList()
        }
        val sonuc = mutableListOf<Girdi>()
        try {
            val cocuklar = DocumentsContract.buildChildDocumentsUriUsingTree(
                dizinUri, DocumentsContract.getDocumentId(dizinUri)
            )
            context.contentResolver.query(cocuklar, GIRDI_SUTUNLARI, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val kimlik = c.getString(0) ?: continue
                    val ad = c.getString(1) ?: continue
                    sonuc.add(
                        Girdi(
                            uri = DocumentsContract.buildDocumentUriUsingTree(dizinUri, kimlik),
                            ad = ad,
                            dizin = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            degistirilme = if (c.isNull(3)) 0L else c.getLong(3),
                            boyut = if (c.isNull(4)) -1L else c.getLong(4)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return sonuc
    }

    /** Klasörleri gezip not dosyalarını, bulundukları klasörün adıyla toplar. */
    private fun notGirdileri(dizinUri: Uri, etiket: String?, hedef: MutableList<Pair<Girdi, String?>>) {
        for (g in girdiler(dizinUri)) {
            if (g.dizin) {
                // Şablonlar yalnızca kendi çipi seçiliyken listelenir; yoksa
                // "Tümü" listesine, aramaya ve görevlere karışırlardı.
                val sablonKlasoru = etiket == null && g.ad == Sablonlar.KLASOR
                if (!ozelKlasor(g.ad) && !sablonKlasoru) notGirdileri(g.uri, etiket ?: g.ad, hedef)
            } else if (notDosyasi(g.ad)) {
                hedef.add(g to etiket)
            }
        }
    }

    /**
     * Önbellekte olmayan notları aynı anda birkaç iş parçacığıyla okur.
     * Seçilen klasörde her okuma sistemin dosya aracısına bir istek; tek tek
     * okununca 1.000 notun ilk yüklenmesi emülatörde 11 saniye sürüyordu.
     */
    private fun onceOku(girdiler: List<Girdi>, icerikGerekli: Boolean) {
        val eksik = girdiler.filter { g ->
            val k = ONBELLEK.al(g.uri.toString(), g.degistirilme, g.boyut)
            k == null || (icerikGerekli && k.icerik == null)
        }
        if (eksik.size < PARALEL_ESIGI) return // az sayıda not: iş parçacığı açmaya değmez
        val havuz = Executors.newFixedThreadPool(OKUYUCU_SAYISI)
        try {
            havuz.invokeAll(eksik.map { g -> java.util.concurrent.Callable { onizleme(g, icerikGerekli) } })
        } finally {
            havuz.shutdown()
        }
    }

    /**
     * Notun başlık ve özeti: dosya değişmediyse önbellekten, değiştiyse
     * okunarak. [icerikGerekli] ise içerik de bellekte olmalı (arama vb.).
     */
    private fun onizleme(g: Girdi, icerikGerekli: Boolean): ListeOnbellegi.Kayit? {
        val adres = g.uri.toString()
        val mevcut = ONBELLEK.al(adres, g.degistirilme, g.boyut)
        if (mevcut != null && (!icerikGerekli || mevcut.icerik != null)) return mevcut
        val icerik = okuKesin(g.uri, ONIZLEME_SINIRI) ?: return mevcut
        val (baslik, ozet) = onizlemeCikar(icerik, g.ad)
        val (gorev, biten) = gorevSayaci(icerik)
        val yeni = ListeOnbellegi.Kayit(g.degistirilme, g.boyut, baslik, ozet, icerik, gorev, biten)
        ONBELLEK.koy(adres, yeni)
        return yeni
    }

    fun copListele(): List<Not> {
        val cop = copKlasoru(false) ?: return emptyList()
        return girdiler(cop.uri)
            .filter { !it.dizin && notDosyasi(it.ad) }
            .mapNotNull { g -> onizleme(g, false)?.let { notYap(g, it, emptySet(), null, null) } }
            .sortedByDescending { it.degistirilme }
    }

    /** Tüm notlardaki onay kutusu satırlarını toplar. */
    fun gorevleriListele(tamamlananlar: Boolean): List<Gorev> {
        val sonuc = mutableListOf<Gorev>()
        for (not in listele(null, null, icerikGerekli = true)) {
            if (not.kilitli) continue
            val satirlar = tamIcerik(not.uri).lines()
            satirlar.forEachIndexed { indeks, satir ->
                val eslesme = MarkdownBicimci.ONAY.find(satir) ?: return@forEachIndexed
                val isaretli = !eslesme.groupValues[2].equals(" ", true)
                if (isaretli && !tamamlananlar) return@forEachIndexed
                val ham = satir.substring(eslesme.value.length)
                val metin = SonTarih.temizle(ham)
                if (metin.isEmpty()) return@forEachIndexed
                sonuc.add(Gorev(not.uri, not.baslik, indeks, metin, isaretli, SonTarih.gun(ham)))
            }
        }
        // Açık görevler önce; aralarında tarihliler en yakın tarihten başlar.
        // Sıralama kararlı: aynı tarihtekiler not sırasını korur.
        return sonuc.sortedWith(compareBy<Gorev>({ it.isaretli }, { it.sonGun ?: Long.MAX_VALUE }))
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
        g: Girdi,
        kayit: ListeOnbellegi.Kayit,
        sabitler: Set<String>,
        klasor: String?,
        eslesme: String?
    ): Not {
        val adres = g.uri.toString()
        // Kopyalanarak taşınmış ya da yedekten gelen notun gerçek tarihi ayrıca saklanır.
        val kilitli = Kilit.notKilitli(context, adres)
        return Not(
            uri = g.uri,
            ad = g.ad,
            baslik = kayit.baslik,
            // Kilitli notun içeriği listeye, widget'a ve göreve hiç çıkmaz.
            ozet = if (kilitli) "" else kayit.ozet,
            degistirilme = Prefs.gosterilenZaman(context, adres, g.degistirilme),
            sabit = sabitler.contains(adres),
            klasor = klasor,
            eslesme = if (kilitli) null else eslesme,
            kilitli = kilitli,
            gorev = if (kilitli) 0 else kayit.gorev,
            biten = if (kilitli) 0 else kayit.biten
        )
    }

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
        if (tamam) ONBELLEK.sil(uri.toString())
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

    /**
     * Yedekten gelen "İş/2026/plan.md" gibi yolun klasörlerini sırayla bulur
     * ya da kurar. Önceden iç içe klasörler "İş2026" diye birleşiyordu.
     */
    fun dizinZinciri(klasorler: List<String>): DocumentFile {
        var dizin = kok()
        for (ham in klasorler) {
            val ad = adTemizle(ham)
            if (ad.isEmpty()) continue
            dizin = cocukBul(dizin, ad)?.takeIf { it.isDirectory }
                ?: dizin.createDirectory(ad)
                ?: return dizin
        }
        return dizin
    }

    /**
     * Belirli bir dizinde not oluşturur; ad çakışırsa "ad-2" olur. Toplu geri
     * yüklemede dizin her not için yeniden listelenmesin diye bilinen adlar
     * verilir; dönen çift notun adresi ve diskteki son adıdır.
     */
    fun dizindeOlustur(
        dizin: DocumentFile,
        icerik: String,
        ad: String,
        mevcutAdlar: Set<String>
    ): Pair<Uri, String>? = dosyaOlusturAdli(dizin, icerik, ad, mevcutAdlar)

    /**
     * Notun tarihini verilen zamana çeker (yedekten ya da Keep'ten gelen not).
     * Uygulama deposunda dosyanın kendi tarihi değişir; seçilen klasörde (SAF)
     * değiştirilemediği için ayrıca saklanır.
     */
    fun tarihiKoru(uri: Uri, zaman: Long, olusturma: Long = 0L) {
        // Dışarıdan gelen notun oluşturma anı aktarma anı değildir: kaynak
        // biliyorsa (Keep) o yazılır, bilmiyorsa (zip) bilinmiyor kalır.
        Prefs.olusturmaKaydet(context, uri.toString(), olusturma)
        if (zaman <= 0) return
        val yol = if (uri.scheme == "file") uri.path else null
        if (yol != null && File(yol).setLastModified(zaman)) return
        Prefs.zamanDamgasiKaydet(context, uri.toString(), zaman)
    }

    private fun dosyaOlustur(hedef: DocumentFile, icerik: String, istenenAd: String? = null): Uri? =
        dosyaOlusturAdli(hedef, icerik, istenenAd, null)?.first

    private fun dosyaOlusturAdli(
        hedef: DocumentFile,
        icerik: String,
        istenenAd: String?,
        bilinenAdlar: Set<String>?
    ): Pair<Uri, String>? {
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
        val mevcutAdlar = bilinenAdlar ?: cocukAdlari(hedef)
        while ("$tekilAd.md" in mevcutAdlar || tekilAd in mevcutAdlar) {
            tekilAd = "$ad-$i"
            i++
        }
        val f = hedef.createFile("text/markdown", tekilAd) ?: return null
        var sonAd = f.name ?: tekilAd
        if (!sonAd.endsWith(".md", true) && !sonAd.endsWith(".txt", true)) {
            sonAd = if (f.renameTo("$tekilAd.md")) "$tekilAd.md" else sonAd
        }
        if (!yaz(f.uri, icerik)) return null
        // Yeni not, çoğaltma, bölme, paylaşımdan gelen not: hepsi buradan doğar.
        Prefs.olusturmaKaydet(context, f.uri.toString(), System.currentTimeMillis())
        return f.uri to sonAd
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
        val zaman = Prefs.gosterilenZaman(context, uri.toString(), f.lastModified())
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
        if (ad in cocukAdlari(hedef)) return null // ad çakışması: kopyalama yolu adı tekilleştirir

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
        // Kopya yeni dosya olarak doğdu; oluşturma anı asıl notunki kalsın.
        Prefs.olusturmaKaydet(context, yeni.toString(), Prefs.olusturma(context, uri.toString()))
        if (zaman > 0) Prefs.zamanDamgasiKaydet(context, yeni.toString(), zaman)
        try {
            f.delete()
        } catch (_: Exception) {
        }
        return yeni
    }

    /** Adres değişti: sabitleme, not kilidi, widget ve hatırlatıcı yeni adrese geçer. */
    private fun ayarlariTasi(eski: Uri, yeni: Uri) = ayarlariTopluTasi(listOf(eski to yeni))

    /** Adresi değişen notların ayarları (sabitleme, kilit, widget, taslak, hatırlatıcı). */
    private fun ayarlariTopluTasi(degisim: List<Pair<Uri, Uri>>) {
        val tasinan = degisim.map { (e, y) -> e.toString() to y.toString() }.filter { it.first != it.second }
        if (tasinan.isEmpty()) return
        Prefs.adresleriTasi(context, tasinan.toMap())
        val taslaklar = Taslaklar(context)
        for ((e, y) in tasinan) {
            taslaklar.tasi(e, y)
            val hatirlatma = Prefs.hatirlatici(context, e)
            if (hatirlatma > 0) {
                val tekrar = Prefs.hatirlaticiTekrari(context, e)
                val capa = Prefs.hatirlaticiCapasi(context, e).takeIf { it > 0 } ?: hatirlatma
                Hatirlatici.kaldir(context, e)
                Hatirlatici.kur(context, y, hatirlatma, tekrar, capa)
            }
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
        Hatirlatici.kaldir(context, uri.toString())
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
    /** Not çöp klasöründe mi (hatırlatıcı çöpteki not için çalmasın). */
    fun copteMi(uri: Uri): Boolean =
        copKlasoru(false)?.listFiles()?.any { it.uri == uri } == true

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


    // --- Not hakkında ---

    /**
     * "Not hakkında" sayfasının diskten gelen kısmı. [kokAdi] seçilen klasörün
     * adıdır; null ise not uygulamanın kendi deposunda. [olusturma] 0 ise bu
     * özellikten önce oluşmuş ya da kaynağı bilinmeyen bir aktarma.
     */
    class NotBilgisi(
        val kokAdi: String?,
        val yol: List<String>,
        val boyut: Long,
        val degistirilme: Long,
        val olusturma: Long
    )

    fun notBilgisi(uri: Uri): NotBilgisi? {
        val f = docGetir(uri) ?: return null
        val k = kok()
        return NotBilgisi(
            kokAdi = if (k.uri.scheme == "file") null else k.name,
            yol = goreliParcalar(uri) ?: listOfNotNull(f.name),
            boyut = f.length(),
            degistirilme = Prefs.gosterilenZaman(context, uri.toString(), f.lastModified()),
            olusturma = Prefs.olusturma(context, uri.toString())
        )
    }

    // --- Etiketler ve bağlantılar ---

    /** Tüm notlardaki #etiketleri toplar. */
    fun etiketleriListele(): List<String> {
        val bulunan = sortedSetOf<String>(Collator.getInstance(tr))
        for (not in listele(null, null, icerikGerekli = true)) {
            for (e in MarkdownBicimci.ETIKET.findAll(onizlemeIcerigi(not.uri))) {
                bulunan.add(e.groupValues[1])
            }
        }
        return bulunan.toList()
    }

    fun etiketliNotlar(etiket: String): List<Not> {
        val kucuk = etiket.lowercase(tr)
        return listele(null, null, icerikGerekli = true).filter { not ->
            MarkdownBicimci.ETIKET.findAll(onizlemeIcerigi(not.uri))
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
        return listele(null, null, icerikGerekli = true).filter { aday ->
            aday.uri != not.uri && MarkdownBicimci.BAGLANTI.findAll(onizlemeIcerigi(aday.uri))
                .any { hedefler.contains(it.groupValues[1].trim().lowercase(tr)) }
        }
    }

    // --- Sürüm geçmişi ---

    /**
     * Geçmiş klasörleri her kayıtta aranmaz, adresleri bellekte tutulur.
     * Seçilen klasörün kökünde 1.000 not varken ".gecmis"i bulmak her
     * kayıtta kökü listelemek demekti; editörden dönüş bunu bekliyordu.
     */
    private fun gecmisKlasoru(olustur: Boolean): DocumentFile? {
        val k = kok()
        val anahtar = k.uri.toString()
        GECMIS_ADRESLERI[anahtar]?.let { return dizinBelgesi(it) }
        val dizin = cocukBul(k, ".gecmis")?.takeIf { it.isDirectory }
            ?: (if (olustur) k.createDirectory(".gecmis") else null)
            ?: return null
        GECMIS_ADRESLERI[anahtar] = dizin.uri
        return dizin
    }

    private fun dizinBelgesi(uri: Uri): DocumentFile? =
        if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            DocumentFile.fromTreeUri(context, uri)
        }

    private fun belgeSil(uri: Uri): Boolean = try {
        if (uri.scheme == "file") {
            File(uri.path ?: "").delete()
        } else {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        false
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
        val bellekAnahtari = kok.uri.toString() + "|" + anahtar
        GECMIS_ADRESLERI[bellekAnahtari]?.let { return dizinBelgesi(it) }
        val dizin = cocukBul(kok, anahtar)?.takeIf { it.isDirectory }
            ?: (if (olustur) kok.createDirectory(anahtar) else null)
            ?: return null
        GECMIS_ADRESLERI[bellekAnahtari] = dizin.uri
        return dizin
    }

    /**
     * Notun bütün eski sürümlerini siler. Not şifrelenince çağrılır: geçmişteki
     * sürümler düz metindir, kalsalar şifrelemenin anlamı olmazdı.
     */
    fun gecmisiSil(uri: Uri): Boolean {
        val dizin = gecmisDizini(uri, false) ?: return true
        GECMIS_ADRESLERI.values.removeAll { it == dizin.uri }
        return try {
            // Bazı sağlayıcılar dolu klasörü silmez; önce içi boşaltılır.
            dizin.listFiles().forEach { it.delete() }
            dizin.delete()
        } catch (_: Exception) {
            false
        }
    }

    /** Not taşınınca geçmişi de yeni anahtarına geçer; yoksa taşınan notun geçmişi kaybolurdu. */
    private fun gecmisiTasi(eskiAnahtar: String?, yeni: Uri) {
        eskiAnahtar ?: return
        val yeniAnahtar = gecmisAnahtari(goreliParcalar(yeni) ?: return)
        if (eskiAnahtar == yeniAnahtar) return
        val kok = gecmisKlasoru(false) ?: return
        val eski = cocukBul(kok, eskiAnahtar)?.takeIf { it.isDirectory } ?: return
        if (yeniAnahtar in cocukAdlari(kok)) return
        try {
            eski.renameTo(yeniAnahtar)
        } catch (_: Exception) {
        }
        GECMIS_ADRESLERI.clear()
    }

    /**
     * Klasör yeniden adlandırılınca içindeki notların geçmiş klasörleri de
     * ("Eski__not" → "Yeni__not") adlandırılır; yoksa geçmiş kopardı.
     */
    private fun klasorGecmisiniTasi(eski: String, yeni: String) {
        val kok = gecmisKlasoru(false) ?: return
        val onek = eski + "__"
        val mevcut = cocukAdlari(kok)
        for (g in girdiler(kok.uri)) {
            if (!g.dizin || !g.ad.startsWith(onek)) continue
            val hedef = yeni + "__" + g.ad.removePrefix(onek)
            if (hedef in mevcut) continue
            try {
                dizinBelgesi(g.uri)?.renameTo(hedef)
            } catch (_: Exception) {
            }
        }
        GECMIS_ADRESLERI.clear()
    }

    /** Kaydetmeden önceki hali gizli klasöre yedekler (en fazla 20 sürüm). */
    fun gecmiseYaz(uri: Uri, icerik: String) {
        if (icerik.isBlank()) return
        val damga = System.currentTimeMillis().toString()
        var dizin = gecmisDizini(uri, true) ?: return
        var dosya = dizin.createFile("text/markdown", damga)
        if (dosya == null) {
            // Bellekteki adres bayatlamış olabilir (klasör dışarıdan silindi/taşındı).
            GECMIS_ADRESLERI.clear()
            dizin = gecmisDizini(uri, true) ?: return
            dosya = dizin.createFile("text/markdown", damga) ?: return
        }
        yaz(dosya.uri, icerik)

        // Tek sorgu: listFiles her dosyanın adını ayrıca soruyordu.
        val surumler = girdiler(dizin.uri).filter { !it.dizin }.sortedBy { it.ad }
        if (surumler.size > GECMIS_SINIRI) {
            surumler.take(surumler.size - GECMIS_SINIRI).forEach { belgeSil(it.uri) }
        }
    }

    data class Surum(val uri: Uri, val zaman: Long)

    fun gecmisiListele(uri: Uri): List<Surum> {
        val dizin = gecmisDizini(uri, false) ?: return emptyList()
        return girdiler(dizin.uri)
            .filter { !it.dizin }
            .mapNotNull { g ->
                val damga = g.ad.removeSuffix(".md").toLongOrNull() ?: return@mapNotNull null
                Surum(g.uri, damga)
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

        /**
         * Editörde son kaydedilen ya da silinen not. Liste, klasörleri baştan
         * taramadan önce bunu kendi kartına uygular; seçilen klasörde tam tarama
         * 1-2 saniye sürdüğü için düzenlenen kart o süre eski kalıyordu.
         */
        @Volatile
        var sonDuzenleme: Duzenleme? = null

        const val KLASOR_SILINDI = 0
        const val KLASOR_KISMEN = 1
        const val KLASOR_HATA = 2

        private const val GECMIS_SINIRI = 20

        /** Kök adresi (ve "kök|anahtar") → geçmiş klasörünün adresi. */
        private val GECMIS_ADRESLERI = java.util.concurrent.ConcurrentHashMap<String, Uri>()

        /** Liste, arama ve etiketler notun yalnızca bu kadarına bakar. */
        const val ONIZLEME_SINIRI = 8192
        private const val OZET_UZUNLUGU = 150
        private const val BASLIK_UZUNLUGU = 80

        /** Tüm uygulama için tek önbellek (widget, editör ve liste paylaşır). */
        val ONBELLEK = ListeOnbellegi()

        @Volatile
        private var onbellekYuklendi = false

        /** Önbellek, görülen not sayısını bu kadar aşınca eskiler atılır. */
        private const val ONBELLEK_PAYI = 50

        /** Önbellekte olmayan bu kadar not varsa paralel okunur. */
        private const val PARALEL_ESIGI = 8
        private const val OKUYUCU_SAYISI = 4

        private val GIRDI_SUTUNLARI = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        /**
         * Kart başlığı ve özeti. İlk dolu satır başlıktır; özet sonraki satırlardan
         * 150 karakter dolana kadar toplanır. Önceden 8 KB'daki bütün satırlar
         * temizlenip sonra kırpılıyordu; 400 satırlık notta yüzlerce boşa düzenli
         * ifade işlemi demekti ve listenin yavaşlığının asıl sebebiydi.
         */
        fun onizlemeCikar(icerik: String, dosyaAdi: String): Pair<String, String> {
            var baslik: String? = null
            val ozet = StringBuilder()
            for (satir in icerik.lineSequence()) {
                if (satir.isBlank()) continue
                // Şifreli notun veri satırı listede görünmez; kapak yeter.
                if (Sifreleme.veriSatiriMi(satir)) continue
                if (baslik == null) {
                    baslik = mdTemizle(satir)
                    continue
                }
                val temiz = mdTemizle(satir)
                if (temiz.isEmpty()) continue
                if (ozet.isNotEmpty()) ozet.append(' ')
                ozet.append(temiz)
                if (ozet.length >= OZET_UZUNLUGU) break
            }
            val ad = baslik?.takeIf { it.isNotBlank() }
                ?: dosyaAdi.removeSuffix(".md").removeSuffix(".txt")
            return ad.take(BASLIK_UZUNLUGU) to ozet.take(OZET_UZUNLUGU).toString()
        }

        /** Notun onay kutusu sayısı ve işaretli olanlar (ilk 8 KB'a bakılır). */
        fun gorevSayaci(icerik: String): Pair<Int, Int> {
            var gorev = 0
            var biten = 0
            for (satir in icerik.lineSequence()) {
                val m = MarkdownBicimci.ONAY.find(satir) ?: continue
                gorev++
                if (!m.groupValues[2].equals(" ", true)) biten++
            }
            return gorev to biten
        }

        /** Kart önizlemesi için satırdaki Markdown işaretlerini söker. */
        fun mdTemizle(satir: String): String =
            satir.trim()
                // Görsel bağlantısı önizlemede ham metin olarak görünmesin.
                .replace(MarkdownBicimci.GORSEL, "")
                .replace(MarkdownBicimci.GORSEL_WIKI, "")
                // [metin](adres) kartta yalnızca metin olarak görünsün.
                .replace(MarkdownBicimci.MD_BAGLANTI) { it.groupValues[1] }
                .trimStart('#', '>', ' ')
                .removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- [X]").removePrefix("- ")
                .replace(ISARETLER, "")
                .trim()

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