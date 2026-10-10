# Testing

## What is tested where

| Level | What | Where |
|---|---|---|
| JVM unit | Tag merge and diff, pictures, multi-values (`TagChangesTest`) | `tags/api` |
| JVM unit | Save, verify, restore, failure paths, with in-memory fakes of TagLib's behaviour (`SaveTagChangesUseCaseTest`) | `core/domain` |
| JVM unit | Track positions, drafts, multi-value mode, library search/sort/grouping, completing the library from the files, file-name patterns, batch numbering, lookup merging and proposals | `core/domain` |
| JVM unit | The FLAC decoder, bit for bit against three IETF test files; the verdicts of the lossless check on synthetic audio with a known spectrum; WAV reading (`core/domain/.../quality`) | `core/domain` |
| JVM unit | Reading a picture's size from its header, telling copies of a picture apart, the score of a cover, what is asked for one song and for the songs of an album, and the whole search with fake sources (`core/domain/.../cover`) | `core/domain` |
| JVM unit | MusicBrainz, Deezer, iTunes and LRCLIB parsing and error mapping, with Ktor's `MockEngine` | `lookup/*`, `lyrics/lrclib` |
| JVM unit | Ranking, LRC parsing, text normalization | `lookup/api`, `lyrics/api`, `core/common` |

Run them all with `./gradlew test`. CI runs them on every pull request (see [ci.md](ci.md)).

## On an emulator, by a script

The APK of the `metadator-dex` branch is used on an emulator after each build
(`.github/smoke/smoke.py`, see [ci.md](ci.md#the-apk-is-then-used-githubsmoke)). The script goes
through the app as a person would, and stops at nothing: each step is tried whatever became of
the one before, and the report says which held.

| What is tried | How it is told |
|---|---|
| The app opens, asks to see the music, and lists four songs copied to the device | What the screen says |
| F1 lists the shortcuts; Ctrl+F searches; Ctrl+A, Ctrl+E, Ctrl+K, Ctrl+S and Ctrl+W do what the list says | What the screen says after each |
| The lossless check: a genuine FLAC, one made from an MP3 of 128 kbps, one upsampled to 96 kHz, and an MP3 it has nothing to say about | The verdict on the card |
| The cover finder, for one song and for the four together, against the real sources | The sheet's best match and its reasons. Not required: the sources are not ours |
| Saving the cover, in the editor and for all four | Each file read back with FFprobe: it has a picture, and its tags and its audio are what they were |
| The window made smaller, another density, a phone held upright, night, and back | The activity is the same object, and what was open is still open |
| A narrow window | One pane at a time |
| A mouse plugged in: the second button, Ctrl and Shift with a click, a song in a window of its own | The menu, the selection, the second activity. Not required: the mouse is made up with `uinput`, and each click is aimed by where the system says the one before landed |
| An SD card: two songs put where only a scan finds them, then Ctrl+R; their folder, their verdicts, and a cover saved to one of them | The songs listed, under the card's own volume in MediaStore; the folder and the editor say the card's name; the file read back from the card as above |
| Turkish | What the screen says |
| Nothing crashed | The device's crash log |

It is one pass on one emulator (Android 15, x86-64), with the files it makes itself and an SD card
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
on a high-resolution download. The cover finder needs the network: try an album whose single has
another cover, a song without an album tag, and a file that already has a small cover. From the
batch editor, try a whole album, an album whose songs have no cover yet, and songs of two albums
selected together.
