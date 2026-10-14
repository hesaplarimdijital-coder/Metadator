# Testing

## What is tested where

| Level | What | Where |
|---|---|---|
| JVM unit | Tag merge and diff, pictures, multi-values (`TagChangesTest`) | `tags/api` |
| JVM unit | Save, verify, restore, failure paths, with in-memory fakes of TagLib's behaviour (`SaveTagChangesUseCaseTest`) | `core/domain` |
| JVM unit | Track positions, drafts, multi-value mode, library search/sort/grouping, completing the library from the files, file-name patterns, batch numbering, lookup merging and proposals | `core/domain` |
| JVM unit | The FLAC decoder, bit for bit against three IETF test files; the verdicts of the lossless check on synthetic audio with a known spectrum; WAV reading (`core/domain/.../quality`) | `core/domain` |
| JVM unit | The check of the whole library: each lossless song once, again only once its file changed, a song added later, a verdict the editor made (`LibraryQualityScannerTest`) | `core/domain` |
| JVM unit | Garbled text: UTF-8 read as Windows-1252 or Latin-1, once or twice over, and Turkish in Windows-1254 read as Latin-1, put right; Icelandic, Faroese and other text that is right left alone; a wrong guess at an Asian character set recognized; the repairs offered for a file's tags (`GarbledTextTest`). The library showing a garbled song's text from its file, read once per version (`GarbledLibraryTextTest`) | `core/domain` |
| JVM unit | The check of the library's covers: each song once, a song saved since read again first, a file that could not be opened tried again at the next start, nothing read while other work is at the files (`LibraryCoverScannerTest`) | `core/domain` |
| JVM unit | Reading a picture's size from its header, telling copies of a picture apart, the score of a cover, what is asked for one song and for the songs of an album, and the whole search with fake sources (`core/domain/.../cover`) | `core/domain` |
| JVM unit | MusicBrainz, Deezer, iTunes and LRCLIB parsing and error mapping, with Ktor's `MockEngine` | `lookup/*`, `lyrics/lrclib` |
| JVM unit | Ranking, LRC parsing, text normalization | `lookup/api`, `lyrics/api`, `core/common` |
| JVM unit | Which items a rectangle dragged with the mouse touches, and how fast it scrolls the list (`MarqueeGeometryTest`) | `core/designsystem` |

Run them all with `./gradlew test`. CI runs them on every pull request (see [ci.md](ci.md)).

## On an emulator, by a script

Metamatik's APK is used on an emulator after each build (`.github/smoke/smoke.py`, see
[ci.md](ci.md#the-apk-is-then-used-githubsmoke)). The script goes through the app as a person
would, and stops at nothing: each step is tried whatever became of the one before, and the report
says which held.

| What is tried | How it is told |
|---|---|
| The app opens, asks to see the music, and lists four songs copied to the device | What the screen says |
| F1 lists the shortcuts; Ctrl+F searches; Ctrl+A, Ctrl+E, Ctrl+K, Ctrl+S and Ctrl+W do what the list says | What the screen says after each |
| The lossless check: a genuine FLAC, one made from an MP3 of 128 kbps, one upsampled to 96 kHz, and an MP3 it has nothing to say about | The verdict on the card |
| The check of the whole library: the same four songs in the table, and the Fake FLAC list | Each row's quality, a dash for the MP3, and only the fake one in the list |
| The cover finder, for one song and for the four together, against the real sources | The sheet's best match and its reasons. Not required: the sources are not ours |
| Saving the cover, in the editor and for all four | Each file read back with FFprobe: it has a picture, and its tags and its audio are what they were |
| The library's rows after each save | The pixels of each saved song's cover in its row, before and after: a different picture, not the one cached before the save |
| The same cover chosen again, once the song has it | The editor's foot says nothing is changed, at once or after a save |
| Every place of the library (albums, artists, folders, songs) on a wide window, on one with no room for the sidebar, and on a phone; then again with sixteen albums that have covers and a compilation of two artists without album artists, flung through, the pointer resting on albums | The app is still running after each, and what the crash log says if not |
| The window made smaller, another density, a phone held upright, night, and back | The activity is the same object, and what was open is still open |
| A narrow window | One pane at a time |
| A mouse plugged in: the second button, Ctrl and Shift with a click, a song in a window of its own | The menu, the selection, the second activity. Not required: the mouse is made up with `uinput`, and steered by where the input system says its pointer is |
| An SD card: two songs put where only a scan finds them, then Ctrl+R; their folder, their verdicts, and a cover saved to one of them | The songs listed, under the card's own volume in MediaStore; the folder and the editor say the card's name; the file read back from the card as above |
| Turkish | What the screen says |
| How it looks: the library by day and by night, the editor beside it, a phone's screen | Screenshots, for a person to look at. Not required |
| A song played from the library, then the app in the background | The time beside the docked bar's line, read twice while it plays; the media session's state ten seconds after Home. Not required |
| A song saved twice in a row on a phone, an artist typed back in and saved without Enter, then the song put back | What says it was saved stands clear of Save, above the keyboard, one at a time; the title stays in its field; the artist is still in the file and nothing is left to save; the file read back with FFprobe is as it was. Not required |
| The player's bar, the selection's bar and a menu on a phone | Screenshots of the glass, for a person to look at. Not required |
| How fast it is: starting cold, typing in the editor, scrolling a library of 300 songs on a wide window and on a phone, and the memory held then ([performance.md](performance.md)) | Numbers in the report, to compare between builds. Not required |
| Nothing crashed | The device's crash log |

It is one pass on one emulator (Android 17, x86-64), with the files it makes itself and an SD card
the emulator makes up (or the system makes out of a file, when the emulator has none). It says the
build works there; it does not say how the app feels, and it is not Samsung DeX.

## Conventions

- Ports are tested with fakes, not mocks of the framework. `FakeTagFiles` behaves like TagLib:
  a write replaces the whole property map, and keys a format cannot hold are dropped.
- A bug gets a failing test before its fix.

## Not yet verified

The emulator run above is one device, one version and six short songs. Before release, check
on a phone and a tablet, on Android 8, 10 and the latest version:

- Round trip per format (MP3, FLAC, M4A, Ogg, Opus, WAV): edit one field, save, and check with
  another tag editor that every other tag and picture is unchanged. This is the most important
  check and the next automated test to write (instrumented, `tags/taglib/src/androidTest`).
- Write access: a song from the library on Android 10 and 11+, a batch of 20 songs (one prompt on
  11+), a file opened from a file manager, a file shared read-only.
- A real SD card, and a USB drive: songs listed with the card's name on their folders, a save to
  one on Android 10, and the card taken out while a song from it is open.
- Undo after a save and after a batch.
- Process death in the editor with unsaved changes ("Don't keep activities").
- The list-detail layout on a tablet or a foldable, TalkBack on the library and the editor.
- Playback in the background, the media notification, and stopping when the app is swiped away
  while paused.

The desktop features ([desktop.md](desktop.md)) were tried on the emulator with a keyboard and a
made-up mouse, not on Samsung DeX. On DeX, with a keyboard and a mouse:

- Dock and undock the phone with the app open: it should stay where it was, not restart.
- Drag the window from narrow to wide and back, with the lookup sheet open: the layout follows,
  and the sheet stays.
- Every shortcut in the list (F1), with the focus in a text field, on a button, and nowhere.
- Right click, Ctrl+click and Shift+click on songs; a right click must not open the song.
- "Open in a new window" twice, for two songs; a picture dragged from My Files onto the cover.
- Day and night switched while the app is open: the status bar's icons follow.

The lossless check was tuned on short test files ([lossless-check.md](lossless-check.md)). Try it
on a real CD rip, on the same rip encoded to MP3 at 128, 192 and 320 kbps and saved as FLAC, and
on a high-resolution download. Let the library check a collection of a few thousand FLAC files on
an SD card: how long the first pass takes, that the app stays smooth meanwhile, and that a second
start checks nothing again. The cover finder needs the network: try an album whose single has
another cover, a song without an album tag, and a file that already has a small cover. From the
batch editor, try a whole album, an album whose songs have no cover yet, and songs of two albums
selected together.
