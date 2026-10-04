Notlar düz Markdown dosyalarıdır. İşaretler yalnızca düzenlediğin satırda görünür, diğer satırlarda gizlenir. Aşağıdaki her örnekte üstte yazdığın, altta uygulamadaki görünüşü var.

## Başlıklar

Notun ilk satırı her zaman başlığıdır. Notun içinde bölüm açmak için satırın başına diyez ve boşluk koy. Başlık olan notta menüdeki İçindekiler ile bölümler arasında gezinebilirsin.

~~~ornek
# Büyük başlık
## Orta başlık
### Küçük başlık
~~~

## Vurgu

~~~ornek
**kalın**, *italik*, ~~üstü çizili~~ ve `kod`
~~~

## Listeler

Enter'a basınca liste kendiliğinden sürer. Girintiyi biçim çubuğundaki düğmelerle değiştirebilirsin.

~~~ornek
- madde
  - girintili madde
1. numaralı madde
~~~

## Görevler

Kutuya dokununca işaretlenir. Bütün notlardaki görevler alttaki çubuktan açılan Görevler ekranında toplanır. Son tarih için görev satırındayken biçim çubuğundaki tarih düğmesine bas ve Son tarih seç'i seç; Görevler ekranı tarihe göre sıralar, geciken görev kırmızı görünür.

~~~ornek
- [ ] raporu gönder 📅 2026-10-10
- [x] faturayı öde
~~~

## Alıntı ve bilgi kutuları

~~~ornek
> Bir alıntı satırı
~~~

Bilgi kutusu ilk satırda türüyle başlar, sonraki satırlar da büyüktür işaretiyle sürer. Türler: not, ipucu, uyarı, hata, soru, örnek, tamam, alıntı (Obsidian'ın İngilizce adları da olur: note, tip, warning…). Obsidian'da da kutu olarak açılır.

~~~ornek
> [!ipucu] Başlık
> Kutunun içi.
~~~

~~~ornek
> [!uyarı]
> Başlık yazılmazsa türün adı başlık olur.
~~~

## Bağlantılar ve etiketler

İki köşeli parantez yazınca not önerileri açılır. Bağlantıya dokununca o not açılır, yoksa o adla oluşturulur. Etikete dokununca o etiketi taşıyan notlar listelenir. Web adresi telefondaki tarayıcıda açılır; uygulamanın interneti yoktur.

~~~ornek
[[Başka bir not]] ve #etiket
[ornek.com](https://ornek.com)
~~~

## Görseller

Biçim çubuğundaki görsel düğmesiyle galeriden seç ya da fotoğraf çek. Görsel notun yanındaki ekler klasörüne kopyalanır ve nota şöyle yazılır:

~~~yalin
![](ekler/foto.jpg)
~~~

Görsele dokununca Düzenle balonu çıkar; basılı tutunca menü açılır; basılı tutup sürükleyince görsel satırı taşınır. Ayarlar'da açıksa görseller 2048 piksele küçültülür ve konum gibi EXIF bilgileri silinir.

## Kod ve ayırıcı

~~~ornek
```
kod bloğu: içinde işaretler yorumlanmaz
```
~~~

~~~ornek
---
~~~

## Kısa yollar

- Ana ekranda nota basılı tut: çoklu seçim. Taşı, sabitle, paylaş, sil; üç noktadan birleştir ve etiket ekle.
- Notu sola kaydır: çöpe. Sağa kaydır: klasöre taşı.
- Alttaki çubuk: Görevler, bugünün notu, yeni not, şablonlar ve menü.
- Editördeki kitap düğmesi okuma görünümüne geçer.
- Editör menüsünde (üç nokta) içindekiler, odak modu, kaynak modu, hatırlatıcı, kilit, şifreleme, sürüm geçmişi, bölme, paylaşma ve yazdırma var. Menünün üstündeki kutuya yazarak ara.
- Başka uygulamada metin seç ya da paylaş: Notlara ekle.
- Ana ekran widget'ları, hızlı ayarlar döşemesi ve uygulama simgesine basılı tutunca açılan kısayollar da var.

## Kilit ve şifreleme

- Not kilidi notu uygulamada gizler; dosya klasörde düz metin kalır.
- Parolayla şifrele dosyanın kendisini şifreler (Android 8 ve sonrası). Klasöre ya da yedeğe erişen biri okuyamaz. Parolayı unutursan not kurtarılamaz.

## Eşitleme

Klasörü Obsidian ile açabilir, Syncthing ile eşitleyebilirsin. Not iki cihazda aynı anda değişirse Syncthing'in bıraktığı çakışma kopyası listede kırmızı etiketle görünür; dokununca iki sürüm karşılaştırılır ve biri seçilir ya da ikisi birleştirilir.
