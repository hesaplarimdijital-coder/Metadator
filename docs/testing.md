# Testing

## What is tested where

| Level | What | Where |
|---|---|---|
| JVM unit | Tag merge and diff, pictures, multi-values (`TagChangesTest`) | `tags/api` |
| JVM unit | Save, verify, restore, failure paths, the backups a save and a batch keep (a batch's kept from the saves made beside it until its result is dismissed), a batch stopped by a backup that cannot be kept, with in-memory fakes of TagLib's behaviour (`SaveTagChangesUseCaseTest`, `BatchTest`) | `core/domain` |
| JVM unit | Track positions, drafts, multi-value mode, library search/sort/grouping, completing the library from the files, file-name patterns, batch numbering, lookup merging and proposals | `core/domain` |
| JVM unit | The batch editor: an album artist set for all reaching every file in both multi-value modes, a field a format drops, numbers of each song's own in each file's style; the albums of a batch and their merge, disc suffixes; finding the songs' album with fake sources, the releases grouped, agreed on and ranked, which song is which track and when that is not sure (`BatchTest`, `AlbumMergeTest`, `AlbumLookupTest`) | `core/domain` |
| JVM unit | The FLAC decoder, bit for bit against three IETF test files; the verdicts of the lossless check on synthetic audio with a known spectrum; WAV reading (`core/domain/.../quality`) | `core/domain` |
| JVM unit | The check of the whole library: each lossless song once, again only once its file changed, a song added later, a verdict the editor made (`LibraryQualityScannerTest`) | `core/domain` |
| JVM unit | Garbled text: UTF-8 read as Windows-1252 or Latin-1, once or twice over, and Turkish in Windows-1254 read as Latin-1, put right; Icelandic, Faroese and other text that is right left alone; a wrong guess at an Asian character set recognized; the repairs offered for a file's tags (`GarbledTextTest`). The library showing a garbled song's text from its file, read once per version (`GarbledLibraryTextTest`) | `core/domain` |
| JVM unit | The check of the library's covers: each song once, a song saved since read again first, a file that could not be opened tried again at the next start, nothing read while other work is at the files (`LibraryCoverScannerTest`) | `core/domain` |
| JVM unit | The "No cover" and "Garbled text" filters: the songs they list, albums and artists without a cover listed whole and counted once (`LibraryCoverFilterTest`); a batch putting each file's garbled text right, a field set for all winning over it (`BatchRepairTextTest`) | `core/domain` |
| JVM unit | Reading a picture's size from its header, telling copies of a picture apart, the score of a cover, what is asked for one song and for the songs of an album, and the whole search with fake sources (`core/domain/.../cover`) | `core/domain` |
| JVM unit | MusicBrainz, Deezer, iTunes and LRCLIB parsing and error mapping, with Ktor's `MockEngine` | `lookup/*`, `lyrics/lrclib` |
| JVM unit | Ranking, LRC parsing, text normalization | `lookup/api`, `lyrics/api`, `core/common` |
| JVM unit | A text field's echo of what the screen keeps: typed text said back, a value the screen sets on its own, texts it skips or turns down (`TextMirrorTest`) | `core/designsystem` |
| JVM unit | Which items a rectangle dragged with the mouse touches, with a bar pinned at the top of the list too, and how fast it scrolls the list (`MarqueeGeometryTest`) | `core/designsystem` |
| JVM unit | What a click on a song does, by the keys held and the pointer that clicked: a mouse's opens it, a finger's picks it while songs are selected (`SongClickTest`) | `feature/library` |
| JVM unit | What the inspector beside a list shows of it: up to a hundred songs selected, the batch editor for them in the order listed; more, said rather than opened; one, its editor; none, the song clicked while it is listed; albums selected, their songs (`InspectedTest`). When the sidebar says the cover check waits (`CoversWaitingTest`) | `feature/library` |
| JVM unit | The back stack: a collection's arrow leaving it in one press, a detail with unsaved changes kept, a choice taking the place of what is beside the list, the selection never over unsaved changes and taking away only what the list asked for, not a file opened by hand (`BackStackNavigatorTest`) | `core/navigation` |
| JVM unit | The batch editor's field of several values: a separator typed after a value, a value deleted back to it, a separator typed without its space, values the screen sets on its own while typed ones are on their way (`TypedValuesTest`) | `feature/batch` |
| JVM unit | The batch editor's ViewModel: the songs opened with what they have in common, a song gone since it was chosen left out and said, nothing applied while a cover is on its way, access refused writing nothing, Undo all putting every song back and closing, and saying how many it put back when not all (`BatchViewModelTest`) | `feature/batch` |
| JVM unit | The library's ViewModel: a range selected from the song selected last or opened, a drag selecting what it touches of what is listed, a batch done clearing the selection, a place of the sidebar starting afresh and Back going back to the one before, a collection's screen listing its songs alone and handing nothing to the checks (`LibraryViewModelTest`) | `feature/library` |
| JVM unit | The editor's ViewModel: a song opened, what was typed typed again after the process died, a save and its Undo, a change the file already holds, write access refused before writing and when asked, a failed write, what is typed while a save writes, lyrics not looked for without a title, garbled text repaired field by field, a failure nothing explains said (`EditorViewModelTest`) | `feature/editor` |

Run them all with `./gradlew test`. CI runs them on every pull request (see [ci.md](ci.md)).

## On an emulator, by a script

Metamatik's APK is used on an emulator after each build (`.github/smoke/smoke.py`, see
[ci.md](ci.md#the-apk-is-then-used-githubsmoke)). The script goes through the app as a person
would, and stops at nothing: each step is tried whatever became of the one before, and the report
says which held.

| What is tried | How it is told |
|---|---|
| The build before this one lists the songs; this one is installed over it, as an update | The songs listed with no question first, every place of the library opens, nothing in the crash log. A migration of the database that does not hold closes the app at its first query |
| The app opens, asks to see the music, and lists four songs copied to the device | What the screen says |
| F1 lists the shortcuts; Ctrl+F searches; Ctrl+A, Ctrl+E, Ctrl+K, Ctrl+S and Ctrl+W do what the list says | What the screen says after each |
| Esc in the search field: the first takes out what was typed and keeps the caret, the second gives the focus up | The field's text and focus, the list narrowed and whole again, and the place the sidebar shows, which stays. Not required |
| Settings, then About: who developed the app, in English and in Turkish | The page says "Developed by Project Ark" ("Project Ark tarafından geliştirildi"). Not required |
| The lossless check: a genuine FLAC, one made from an MP3 of 128 kbps, one upsampled to 96 kHz, and an MP3 it has nothing to say about | The verdict on the card |
| The check of the whole library: the same four songs in the table, and the Fake FLAC list | Each row's quality, a dash for the MP3, and only the fake one in the list |
| The cover finder, for one song and for the four together, against the real sources | The sheet's best match and its reasons. Not required: the sources are not ours |
| Saving the cover, in the editor and for all four | Each file read back with FFprobe: it has a picture, and its tags and its audio are what they were |
| The library's rows after each save | The pixels of each saved song's cover in its row, before and after: a different picture, not the one cached before the save |
| The same cover chosen again, once the song has it | The editor's foot says nothing is changed, at once or after a save |
| The album's metadata: Ctrl+L in the batch editor of the four songs, the sheet that looks it up, Esc | The sheet lists what the sources found, as a screenshot shows, and no file is written. Not required: the sources are not ours |
| Every place of the library (albums, artists, folders, songs) on a wide window, on one with no room for the sidebar, and on a phone; then again with sixteen albums that have covers and a compilation of two artists without album artists, flung through, the pointer resting on albums | The app is still running after each, and what the crash log says if not |
| The window made smaller, another density, a phone held upright, night, and back | The activity is the same object, and what was open is still open |
| A narrow window | One pane at a time |
| A mouse plugged in: the second button, Ctrl and Shift with a click, a song in a window of its own | The menu, the selection, the second activity. Not required: the mouse is made up with `uinput`, and steered by where the input system says its pointer is |
| An album's screen left with one press: its arrow, Esc, the Back key; the Back key at the start of the app with a mouse as the pointer, and after a tap for contrast | What is in front after each press. Not required |
| The editor beside the library following the selection: Ctrl+click on a song, on a second, a plain click; the check boxes of an album's screen, of the Songs and of the Fake FLAC list, and Shift on a box | The editor's title, "Edit 2 songs", the songs checked. Not required |
| A rectangle dragged over the rows with the mouse: plain, with Ctrl, ended by Esc in the middle | The rows selected and the editor that opens or does not. Not required |
| An SD card: two songs put where only a scan finds them, then Ctrl+R; their folder, their verdicts, and a cover saved to one of them | The songs listed, under the card's own volume in MediaStore; the folder and the editor say the card's name; the file read back from the card as above |
| A cover saved to a song takes it off the list of songs without one, within seconds | The list and the sidebar's count, read until the song is gone. Not required: the sources are not ours |
| An album's songs on the SD card edited together: the album artist set for both | Both files read back with FFprobe: the album artist, and every other tag, the picture and the audio as they were. Not required |
| Turkish | What the screen says; the About page in Turkish |
| How it looks: the library by day and by night, the editor beside it, a phone's screen | Screenshots, for a person to look at. Not required |
| A song played from the library, then the app in the background | The time beside the docked bar's line, read twice while it plays; the media session's state ten seconds after Home. Not required |
| A song saved twice in a row on a phone, an artist typed back in and saved without Enter, then the song put back | What says it was saved stands clear of Save, above the keyboard, one at a time; the title stays in its field; the artist is still in the file and nothing is left to save; the file read back with FFprobe is as it was. Not required |
| The player's bar, the selection's bar and a menu on a phone | Screenshots of the glass, for a person to look at. Not required |
| The player's bar opens the full-screen player, wide and on a phone's layout; Back closes it | The player's own buttons, and the library where it was. Not required |
| Songs made for the next steps and found by a rescan: a few without a cover, three with garbled text (UTF-8 read as Windows-1252, Latin-1 with the byte 0xF0), a control that is right, two albums to merge, a long album | Their number in the sidebar. Not required |
| The songs without a cover and the songs with garbled text: counted in the sidebar, listed apart and by the chips, the control not among them, no album reading as another script | The sidebar's counts, the lists, the chips' counts. Not required |
| The garbled title put right in the editor, and the garbled album artists of a list put right in the batch editor, with the switch off at first | The files read back with FFprobe say "Moğollar" and the rest is as it was; the list is empty after. Not required |
| An album shows the cover of its first song that has one; the second button on an album: Open, Play, Shuffle, Edit all, Select, and with several selected Edit together and Merge albums; Esc closes it | The pixels of the tiles, the menu's items. Not required: the mouse is made up with `uinput` |
| Two albums merged: the batch editor's card, one title, apply | The files read back (one album, one album artist, the rest as it was), and the library lists one album where there were two. Not required |
| A long album: End goes to its last song, Home to its first; a drag from the cover or the name selects nothing; a rectangle dragged below the list's edge scrolls it and keeps selecting | The rows in view and the songs selected. Not required |
| A file opened by hand (Ctrl+O) stays beside the library when a search hides the song clicked before it; a song clicked goes | The editor's title or "Pick a song". Not required: the system's picker is not ours |
| How fast it is: starting cold, typing in the editor, scrolling a library of 300 songs on a wide window and on a phone, and the memory held then ([performance.md](performance.md)) | Numbers in the report, to compare between builds. Not required |
| Three hundred songs selected: "N songs selected" with Edit together beside the list and no editor, then Ctrl+E, then Home | The placeholder, "Edit N songs", and no transaction too large in the log. Not required |
| Nothing crashed | The device's crash log |

The steps from "Esc in the search field" to "Three hundred songs selected" were written from the code of the screens and are tried by the script apart from the rest: none is required, and a run that is late leaves out those that matter least and says so in its report (`SMOKE_TIME_LIMIT`, in seconds, 2400 unless set).

It is one pass on one emulator (Android 17, x86-64), with the files it makes itself and an SD card
the emulator makes up (or the system makes out of a file, when the emulator has none). It says the
build works there; it does not say how the app feels, and it is not Samsung DeX.

## Conventions

- Ports are tested with fakes, not mocks of the framework. `FakeTagFiles` behaves like TagLib:
  a write replaces the whole property map, and keys a format cannot hold are dropped.
- The fakes are `core/domain`'s test fixtures (`src/testFixtures`), which every feature's tests
  get too: a ViewModel is tested with the domain's real use cases on fake ports. A port of the
  Android modules (`core:ui`'s `ImageSource`) is faked in the tests of the feature that uses it,
  and each feature's `MainDispatcherRule` runs what the ViewModel launches on the test's
  scheduler.
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
selected together. Its "Find metadata" needs the network too: try a whole album, one without an
album tag, one of two discs and a compilation, and see which songs get numbers; then merge an album
the library splits by its album artists, and one split into "CD1" and "CD2".
