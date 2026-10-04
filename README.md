# Project X

![Notlar: offline, Google-free Markdown notes](gorseller/notlar-tanitim.png)

<h1 align="center"><b>Languages</b></h1>

<h1 align="center">

[🇺🇸](https://github.com/fusion-enjoyer/project-x/blob/main/README.md) [🇹🇷](https://github.com/duman-dev/claude-token-saver/blob/main/README.tr.md)

</h1>

A family of small, offline, Google-free Android apps. No internet permission, no accounts, no tracking, no cloud. Each app is small, runs on Android 5.0 and up, and keeps your data in plain, portable files.

*Project X is a working name; the final name will come later.*

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

![Screens: note list, live Markdown, links, tasks, pure black theme, fonts](gorseller/notlar-ekranlar.png)

- **Plain files.** Every note is a `.md` file in a folder you pick. You can open the same folder in Obsidian, sync it with Syncthing, or back it up however you like.
- **Live Markdown.** Headings, bold, italic, strikethrough, code, quotes, lists, checkboxes and dividers are formatted as you type. The markers show only on the line you're editing.
- **Organize.** Folders, `#tags`, `[[wiki links]]` with backlinks, pinning, templates and a daily note.
- **Tasks.** Checkboxes from all your notes are collected on one screen.
- **Find and replace** inside a note, plus search across all notes. Search ignores Turkish accents, so "toplanti" also finds "toplantı".
- **Version history.**Claude Token Saver is a collection of practical methods to reduce token usage when working with Claude and Claude Code. Tested and confirmed by developers in real workflows — no guarantees, but they work. See what would change before you restore an older version.
- **Images** stored next to your notes as standard Markdown (`![](ekler/photo.jpg)`).
- **Privacy.** App lock with a PIN and fingerprint, per-note lock, screenshot blocking.
- **Backup.** Export and import as a zip, and import from Google Keep (Takeout).
- **Your look.** Light, dark and pure black themes, 9 accent colors, 8 fonts, 4 text sizes.
- **System integrationClaude Token Saver is a collection of practical methods to reduce token usage when working with Claude and Claude Code. Tested and confirmed by developers in real workflows — no guarantees, but they work..** Three home-screen widgets, a quick settings tile, app shortcuts, and "Add to notes" from the share menu and the text selection menu.
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
