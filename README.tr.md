# Project X

![Notlar: offline, Google-free Markdown notes](gorseller/notlar-tanitim.png)

<h1 align="center">

[🇺🇸](https://github.com/fusion-enjoyer/project-x/blob/main/README.md) [🇹🇷](https://github.com/fusion-enjoyer/project-x/blob/main/README.tr.md)

</h1>

Google'sız, çevrimdışı, küçük Android uygulamalarından oluşan bir aile. İnternet izni yok, hesap yok, takip yok, bulut yok. Her uygulama küçük, Android 5.0 ve üstünde çalışır, verini düz ve taşınabilir dosyalarda tutar.

*Project X geçici bir ad; asıl ad sonra gelecek.*

## Uygulamalar

| Uygulama | Durum |
|---|---|
| **Notlar** (`notlar/`) | Hazır, v0.24.0 |
| Saat | Planlandı |
| Takvim | Planlandı |
| Rehber | Planlandı |
| Galeri | Planlandı |
| Müzik | Planlandı |
| Başlatıcı (launcher) | Planlandı |

## Notlar

Yolunuza çıkmayan bir Markdown not uygulaması.

![Ekranlar](gorseller/notlar-ekranlar.png)

- **Düz dosyalar.** Her not, seçtiğin klasörde düz bir `.md` dosyasıdır. Aynı klasörü Obsidian ile açabilir, Syncthing ile eşitleyebilir veya dilediğin gibi yedekleyebilirsin.

- **Canlı Markdown.** Başlık, kalın, italik, üstü çizili, kod, alıntı, liste, onay kutusu, ayırıcı ve Obsidian tarzı bilgi kutuları (`> [!tip]`) yazarken biçimlendirilir. İşaretler yalnız düzenlediğin satırda görünür.

- **Obsidian gibi sekmeler.** Birkaç notu aynı anda açık tut. Klavye kapalıyken havada duran çubukta geri, ileri, nota git, yeni sekme ve sekmelerin var; sekmeler önizlemeli kartlar olarak görünür. Uygulama kapansa da hatırlanır.

- **Düzenle.** Klasörler, `#etiket`, `[[wiki bağlantı]]` ve geri bağlantılar, sabitleme, şablonlar, günlük not, içindekiler, notu bölme ve notları birleştirme.

- **Görevler.** Bütün notlardaki onay kutuları tek bir Görevler ekranında toplanır; son tarih (`📅 2026-10-20`, Obsidian Tasks biçimi) ve yinelenen hatırlatıcılar.

- **Bul ve değiştir.** Not içinde bul ve değiştir, ayrıca bütün notlarda arama. Arama Türkçe karakterleri göz ardı eder, yani "toplanti" araması "toplantı" kelimesini de bulur.

- **Sürüm geçmişi.** Eski bir sürüme dönmeden önce nelerin değişeceğini görebilirsin. Kaydırarak geri yüklenen ya da silinen çöp kutusu.

- **Resimler.** Notlarının yanında standart Markdown olarak saklanır (`![](ekler/foto.jpg)`). Kameradan doğrudan nota fotoğraf, notu görsel olarak paylaşma, yazdırma ya da PDF.

- **Gizlilik.** PIN ve parmak izi ile uygulama kilidi, nota özel kilit, parolayla şifrelenmiş notlar (AES-256, Android 8+), ekran görüntüsü engelleme.

- **Eşitlemeye uygun.** Syncthing çakışma kopyalarını bulur ve çözer.

- **Yedekleme.** Zip olarak dışa ve içe aktarma, ayrıca Google Keep'ten (Takeout) içe aktarma.

- **Görünüm.** Açık, koyu ve saf siyah tema, 9 vurgu rengi ya da telefonun kendi rengi (Material You), 8 yazı tipi, 4 metin boyutu.

- **Widget'lar.** On ana ekran widget'ı: işaretlenebilir görevler, bugünün notu, hızlı eylemler, sabitlenmiş notlar, bir klasör ya da etiket, yaklaşanlar, geçmişte bugün, tek not ve dahası. Notlar ana ekranda da uygulamadaki gibi görünür.

- **Sistem entegrasyonu.** Hızlı ayarlar döşemesi, uygulama kısayolları ve paylaşım ile metin seçimi menülerinden "Notlara ekle" seçeneği.

- Türkçe ve İngilizce dil desteği.

**Boyut:** yaklaşık 1,1 MB. **İzinler:** bildirimler (hatırlatıcılar), başlangıçta çalıştır (yeniden başlatmadan sonra hatırlatıcıları geri yüklemek için), biyometri (isteğe bağlı parmak izi kilidi). İnternet izni asla yok.

### Kurulum

APK'yı [Releases](../../releases) sayfasından indirebilirsin.

### Derleme

JDK 17 ve Android SDK (compileSdk 36) gereklidir.

```bash
./gradlew :notlar:assembleRelease
```

İmza ayarı yoksa release sürümü imzasız derlenir.
