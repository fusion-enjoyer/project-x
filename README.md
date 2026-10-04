# Project X

A family of small, offline, Google-free Android apps. No internet permission, no accounts, no tracking, no cloud. Each app is small, runs on Android 5.0 and up, and keeps your data in plain, portable files.

*Project X is a working name; the final name will come later.*

[Türkçe aşağıda](#türkçe)

## Apps

| App | Status |
|---|---|
| **Notes** (`notlar/`) | Available, v0.19.0 |
| Clock | Planned |
| Calendar | Planned |
| Contacts | Planned |
| Gallery | Planned |
| Music | Planned |
| Launcher | Planned |

## Notes

A Markdown note app that stays out of your way.

- **Plain files.** Every note is a `.md` file in a folder you pick. You can open the same folder in Obsidian, sync it with Syncthing, or back it up however you like.
- **Live Markdown.** Headings, bold, italic, strikethrough, code, quotes, lists, checkboxes and dividers are formatted as you type. The markers show only on the line you're editing.
- **Organize.** Folders, `#tags`, `[[wiki links]]` with backlinks, pinning, templates and a daily note.
- **Tasks.** Checkboxes from all your notes are collected on one screen.
- **Find and replace** inside a note, plus search across all notes. Search ignores Turkish accents, so "toplanti" also finds "toplantı".
- **Version history.** See what would change before you restore an older version.
- **Images** stored next to your notes as standard Markdown (`![](ekler/photo.jpg)`).
- **Privacy.** App lock with a PIN and fingerprint, per-note lock, screenshot blocking.
- **Backup.** Export and import as a zip, and import from Google Keep (Takeout).
- **Your look.** Light, dark and pure black themes, 9 accent colors, 8 fonts, 4 text sizes.
- **System integration.** Three home-screen widgets, a quick settings tile, app shortcuts, and "Add to notes" from the share menu and the text selection menu.
- Turkish and English.

**Size:** about 0.9 MB. **Permissions:** notifications (reminders), run at startup (to restore reminders after a reboot), biometrics (optional fingerprint unlock). Never internet.

### Install

Download the APK from [Releases](../../releases).

### Build

You need JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew :notlar:assembleRelease
```

Without a signing key the release APK is unsigned. To sign it, point the Gradle property `imzaDosyasi` (for example in `~/.gradle/gradle.properties`) to a `.properties` file containing `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

Unit tests:

```bash
./gradlew :notlar:testDebugUnitTest
```

### Principles

- **No internet permission. Ever.**
- **Portability over lock-in.** Notes are plain Markdown and are not encrypted. The note lock hides a note; it does not encrypt it.
- **Small and old-phone friendly.** Classic Android views, no Compose, no new dependencies, minSdk 21. A feature that needs a newer Android turns itself off quietly on older phones instead of breaking the app.

## License

[GPL-3.0](LICENSE). You may use, study, change and share this code, but anything you distribute that is based on it must also be released under GPL-3.0 with its source code.

---

## Türkçe

Google'sız, çevrimdışı, küçük Android uygulamalarından oluşan bir aile. İnternet izni yok, hesap yok, takip yok, bulut yok. Her uygulama küçük, Android 5.0 ve üstünde çalışır, verini düz ve taşınabilir dosyalarda tutar.

*Project X geçici bir ad; asıl ad sonra gelecek.*

### Uygulamalar

| Uygulama | Durum |
|---|---|
| **Notlar** (`notlar/`) | Hazır, v0.19.0 |
| Saat | Planlandı |
| Takvim | Planlandı |
| Rehber | Planlandı |
| Galeri | Planlandı |
| Müzik | Planlandı |
| Başlatıcı (launcher) | Planlandı |

### Notlar

- Her not, seçtiğin klasörde düz bir `.md` dosyası. Aynı klasörü Obsidian ile açabilir, Syncthing ile eşitleyebilirsin.
- Yazarken canlı Markdown: başlık, kalın, italik, üstü çizili, kod, alıntı, liste, onay kutusu. İşaretler yalnız düzenlediğin satırda görünür.
- Klasörler, `#etiket`, `[[wiki bağlantı]]` ve geri bağlantılar, sabitleme, şablonlar, günlük not.
- Bütün notlardaki onay kutuları tek bir Görevler ekranında.
- Not içinde bul ve değiştir, bütün notlarda Türkçe karakterden bağımsız arama.
- Sürüm geçmişi: eski sürüme dönmeden önce neyin değişeceğini gösterir.
- Uygulama kilidi (PIN + parmak izi), not kilidi, ekran görüntüsü engelleme.
- Zip yedekleme ve Google Keep'ten içe aktarma.
- Açık, koyu ve saf siyah tema, 9 vurgu rengi, 8 yazı tipi.
- Üç ana ekran widget'ı, hızlı ayar döşemesi, kısayollar, "Notlara ekle".

Boyut yaklaşık 0,9 MB. APK'yı [Releases](../../releases) sayfasından indirebilirsin.

Lisans: [GPL-3.0](LICENSE).
