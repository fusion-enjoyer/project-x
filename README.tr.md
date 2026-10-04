# Project X

![Notlar: offline, Google-free Markdown notes](gorseller/notlar-tanitim.png)

<h1 align="center">

[🇺🇸]() [🇹🇷]()

</h1>

Google'sız, çevrimdışı, küçük Android uygulamalarından oluşan bir aile. İnternet izni yok, hesap yok, takip yok, bulut yok. Her uygulama küçük, Android 5.0 ve üstünde çalışır, verini düz ve taşınabilir dosyalarda tutar.

*Project X geçici bir ad; asıl ad sonra gelecek.*

## Uygulamalar

| Uygulama | Durum |
|---|---|
| **Notlar** (`notlar/`) | Hazır, v0.19.0 |
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

- **Canlı Markdown.** Başlık, kalın, italik, üstü çizili, kod, alıntı, liste, onay kutusu ve ayırıcılar yazarken biçimlendirilir. İşaretler yalnız düzenlediğin satırda görünür.

- **Düzenle.** Klasörler, `#etiket`, `[[wiki bağlantı]]` ve geri bağlantılar, sabitleme, şablonlar ve günlük not.

- **Görevler.** Bütün notlardaki onay kutuları tek bir Görevler ekranında toplanır.

- **Bul ve değiştir.** Not içinde bul ve değiştir, ayrıca bütün notlarda arama. Arama Türkçe karakterleri göz ardı eder, yani "toplanti" araması "toplantı" kelimesini de bulur.

- **Sürüm geçmişi.** Eski bir sürüme dönmeden önce nelerin değişeceğini görebilirsin.

- **Resimler.** Notlarınızın yanında standart Markdown olarak saklanır (`![](ekler/foto.jpg)`).

- **Gizlilik.** PIN ve parmak izi ile uygulama kilidi, nota özel kilit, ekran görüntüsü engelleme.

- **Yedekleme.** Zip olarak dışa ve içe aktarma, ayrıca Google Keep'ten (Takeout) içe aktarma.

- **Görünüm.** Açık, koyu ve saf siyah tema, 9 vurgu rengi, 8 yazı tipi, 4 metin boyutu.

- **Sistem entegrasyonu.** Üç ana ekran widget'ı, hızlı ayarlar döşemesi, uygulama kısayolları ve paylaşım ile metin seçimi menülerinden "Notlara ekle" seçeneği.

- Türkçe ve İngilizce dil desteği.

**Boyut:** yaklaşık 0,9 MB. **İzinler:** bildirimler (hatırlatıcılar), başlangıçta çalıştır (yeniden başlatmadan sonra hatırlatıcıları geri yüklemek için), biyometri (isteğe bağlı parmak izi kilidi). İnternet izni asla yok.

### Kurulum

APK'yı [Releases](../../releases) sayfasından indirebilirsin.

### Derleme

JDK 17 ve Android SDK (compileSdk 36) gereklidir.

```bash
./gradlew :notlar:assembleRelease
