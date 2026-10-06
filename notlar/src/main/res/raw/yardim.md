## Headings

The first line of a note is always its title. To start a section inside the note, begin the line with a hash and a space. In a note with headings, Contents in the menu jumps between sections.

~~~ornek
# Large heading
## Medium heading
### Small heading
~~~

## Emphasis

~~~ornek
**bold**, *italic*, ~~strikethrough~~ and `code`
~~~

## Lists

Press Enter and the list continues by itself. Change the indent with the buttons on the formatting bar.

~~~ornek
- item
  - indented item
1. numbered item
~~~

## Tasks

Tap the box to check it. Tasks from all notes are collected on the Tasks screen, opened from the bottom bar. For a due date, put the cursor on the task line, tap the date button on the formatting bar and choose Set due date; the Tasks screen sorts by date and overdue tasks turn red.

~~~ornek
- [ ] send the report 📅 2026-10-10
- [x] pay the bill
~~~

## Quotes and callouts

~~~ornek
> A quoted line
~~~

A callout starts with its type on the first line, and the following lines continue with the greater-than sign. Types: note, tip, warning, error, question, example, success, quote (the Turkish names work too). Obsidian shows it as a callout as well.

~~~ornek
> [!tip] Title
> Inside the callout.
~~~

~~~ornek
> [!warning]
> Without a title, the type becomes the title.
~~~

## Links and tags

Type two square brackets and note suggestions appear. Tap a link to open that note; if it doesn't exist it is created with that name. Tap a tag to list the notes that have it. Web addresses open in your phone's browser; the app itself has no internet access.

~~~ornek
[[Another note]] and #tag
[example.com](https://example.com)
~~~

## Images

Use the image button on the formatting bar to pick from the gallery or take a photo. The image is copied to the attachments folder next to your notes and written into the note like this:

~~~yalin
![](ekler/photo.jpg)
~~~

Tap an image for the Edit bubble; long-press for the menu; long-press and drag to move the image line. If it's on in Settings, images are shrunk to 2048 px and EXIF details such as location are removed.

## Code and dividers

~~~ornek
```
code block: markers inside are not interpreted
```
~~~

~~~ornek
---
~~~

## Shortcuts

- Long-press a note on the main screen: multi-select. Move, pin, share, delete; from the three dots, merge and add a tag.
- Swipe a note left: trash. Swipe right: move to folder.
- Bottom bar: Tasks, today's note, new note, templates and the menu.
- The book button in the editor switches to reading view.
- The editor menu (three dots) has contents, focus mode, source mode, reminder, lock, encryption, version history, split, share and print. Type in the box at the top of the menu to search it.
- Select or share text in another app: Add to notes.
- There are also home-screen widgets, a quick settings tile and shortcuts on a long-press of the app icon.

## Lock and encryption

- Note lock hides the note in the app; the file stays plain text in the folder.
- Encrypt with password encrypts the file itself (Android 8 and later). Someone with the folder or a backup can't read it. If you forget the password, the note can't be recovered.

## Sync

You can open the folder in Obsidian and sync it with Syncthing. If a note changes on two devices at once, the conflict copy Syncthing leaves appears in the list with a red label; tap it to compare the two versions and keep one or merge both.
