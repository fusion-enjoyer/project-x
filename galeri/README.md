<p align="center">
  <b>English</b> · <a href="README.tr.md">Türkçe</a> · <a href="../README.md">← Project X</a>
</p>

# Gallery

Your photos and videos, offline. No internet permission, no account, no cloud.

**Version 0.1.0 (in development)** · Android 5.0+

Gallery reads your phone's own media storage; it keeps no database of its own and never changes your files. Edits are always saved as a **new copy**, the original stays as it was.

## Features (MVP)

- **Photos:** every photo and video, newest first, grouped by month.
- **Albums:** your phone's folders (Camera first), with cover and count.
- **Search:** file name, album, month and year ("october 2024", "ekim"), type ("video", "screenshot", "gif"). Turkish and English month names always work; accents and case don't matter.
- **Viewer:** pinch or double-tap to zoom, swipe for the next photo, tap to hide the bars. Videos open in your phone's video player.
- **Rotate:** the preview turns at once; "Save copy" writes the rotated photo.
- **Crop:** drag the frame's corners and edges, or pick Free, Square, 4:3, 3:4, 16:9, 9:16; rotate left/right on the same screen. Cut from the full-resolution original, even 48 MP photos on old phones.
- **Share** and **Details** (date, size, resolution, album).
- Open images from other apps with "Open with Gallery".
- Android 14 "Allow limited access" is supported.

Copies go to `Pictures/Galeri` with the original's date, so they sit right next to it.

## Planned

GIF playback, slideshow, locked folder, delete and move, editing in place.
