# Architecture

## Modules

Metadator is split by responsibility, not by kind. Each engine has a contract module (`*:api`,
plain Kotlin) and one or more implementations; features see the contracts only.

```
app ──────────────► every module (the only place implementations are named)
feature:* ────────► core:*, *:api
core:ui ──────────► core:designsystem, core:domain (what two features show alike)
core:domain ──────► core:model, core:common, tags:api, library:api, lookup:api, lyrics:api
core:database ────► core:domain, tags:api, library:api (the ports its stores implement)
tags:taglib ──────► tags:api, library:api (opens files through AudioFileOpener)
lookup:* , lyrics:lrclib ─► their api, core:network
```

Why more modules than a single app module: the engines are interchangeable (TagLib today, maybe
another tag library tomorrow; several metadata sources), and the API/implementation split is what
lets one be swapped in one line of DI (`app/.../di/AppModules.kt`) without touching features. It
also keeps the domain plain Kotlin, so the logic that protects users' files is tested on the JVM
in seconds.

`AndroidFeatureConventionPlugin` enforces the rule: a feature that depends on an implementation
or on another feature fails the build.

## Layers

- **Domain** (`core:domain`): use cases and pure logic. `SaveTagChangesUseCase`,
  `RestoreBackupUseCase`, `LoadTrackUseCase`, `LookupService`, `BatchRunner`, `TrackPositions`,
  `FileNamePattern`, library filtering and grouping, `CheckLosslessUseCase` with the FLAC decoder
  and the spectrum it reads ([lossless-check.md](lossless-check.md)), `FindCoversUseCase` with
  the scoring of covers ([cover-finder.md](cover-finder.md)), what the batch editor finds and
  merges (`FindAlbumUseCase` with the releases it proposes, and `AlbumMerge`: [Editing several
  songs](#editing-several-songs)), `GarbledText`, which tells text decoded with the wrong
  character set and puts it right (`text`), and `LibraryCoverScanner`, which finds the songs
  without a cover (`artwork`).
- **Ports** (`*:api`): `TagReader`, `TagWriter`, `TagBackupStore`, `AudioLibrary`, `TrackTagCache`,
  `AudioFileOpener`, `AudioFileInfo`, `WriteAccess`, `MediaIndexer`, `MetadataProvider`,
  `ArtworkDownloader`, `LyricsProvider`, `PlayerController`. A few ports are the domain's own,
  having no engine to belong to: `ImageSampler`, which decodes pictures for the cover finder
  (`AndroidImageSampler` in `core:data`), and the stores of what the checks of the library found,
  `QualityStore` (`FileQualityStore` in `core:data`) and `CoverCheckStore` (`RoomCoverCheckStore`
  in `core:database`).
- **Implementations**: TagLib, MediaStore, Room, DataStore, Ktor, Media3.
- **Presentation** (`feature:*`): screens and ViewModels. What two features show alike is in
  `core:ui`, since a feature cannot depend on another: the cover finder's sheet and its ViewModel
  (`core/ui/.../cover`), opened by the editor and by the batch editor.

The library is MediaStore's, completed from the files (`TagCompletedAudioLibrary`). Android's
scanner does not index every tag of every format: a FLAC's `DATE` never becomes a year, and before
Android 11 there is no album artist or genre at all. A song with a field MediaStore has no value
for has its tags read once, and what was read is cached in Room (`track_tags`) against the file's
modification date and size. MediaStore's values win where they read right, and the file fills the
gaps, so "Needs attention" means the file itself lacks the field.

**Text MediaStore decoded wrong is the file's own.** Android's scanner decodes a tag marked Latin-1
with a guess at its real character set, and old taggers wrote whatever bytes they had under that
mark: Turkish in Windows-1254, or UTF-8. A wrong guess shows "MoÄŸollar" for "Moğollar", or Chinese
characters in a Turkish name, and split an album in two when its songs gave their album artist both
ways. `GarbledText` (`core:domain`, `text`) tells such text from text that is right:

- UTF-8 read as Windows-1252 or Latin-1 ("Ã¶" for "ö"), when the bytes the text stands for are
  UTF-8 without a fault and decode to letters of one script per word. Text that is right almost
  never is: it would need an accented capital followed by a symbol.
- Turkish in Windows-1254 read as Latin-1 ("Moðollar"), when swapping the six letters the two
  differ in (ð Ð þ Þ ý Ý for ğ Ğ ş Ş ı İ) gives text that is Turkish by its letters and its shape:
  only Turkish letters, ğ after exactly one vowel, and something only Turkish has (ç or ü, a ğ in a
  word of one vowel harmony, an ş before a consonant…). Icelandic, which has those six letters
  itself, is left as it is; a short word both could spell ("Kýr") too.
- Characters of another script against Latin letters, in text that is mostly Latin: what a wrong
  guess at an Asian character set gives ("Mo" and a Chinese character and "llar"). That cannot be
  undone from the text; only the file can say what it was.

A song whose title, artist, album or album artist looks garbled has its file read too, once per
version of the file like the rest (`track_tags` keeps the title for it), and the library shows the
file's text where it looks less garbled than MediaStore's. Where the file itself is garbled, as
TagLib reads it too, the editor says so above the fields, with what each field most likely says
("Album artist: Moğollar?"), and one action that puts them right as changes to save
(`TagMap.textRepairs`). Those songs are the library's "Garbled text" list (`Track.hasGarbledText`,
worked out once for each version of the library, not at each letter typed in the search), and the
batch editor puts all of theirs right at once: "Repair garbled text", first of its options where
any of its songs has some (`BatchEdit.repairText`), writes each file's own text put right, backed up
and undone as any batch. Its card shows three of the changes, then how many more there are and in
which fields: the lyrics and the comment are put right too, where a wrong repair shows least. Selecting the whole list (Ctrl+A) is what the inspector beside it shows as
that batch.

**A list shows each song's own cover** (`TrackArtwork`, `Track.artwork`): MediaStore's thumbnail
of the picture in the song's file (`ContentResolver.loadThumbnail`, which reads it out of the file
once and keeps it), asked for at the size it is shown, through a Coil fetcher of the app's
(`TrackArtworkFetcher`, in the image loader the application builds). MediaStore's album art, one
picture for the whole album from whichever song it read first, is only used before Android 10,
where there is no such thumbnail. The cover is cached under the song and the version of its file
(when it was last written): a cover saved to a song is a new picture under a new key, where the
one it replaced was cached under the old. Without that the lists kept showing the cover a song had
before it was saved, for as long as the app ran. A song whose file holds no picture, and whose
folder has none, shows the note in its place, as the editor shows none; that it has none is
remembered for that version of the file, so that its file is not opened again each time its row
comes back in sight.

**A group (an album, an artist) shows the first of its songs' covers there is**
(`CollectionArtwork`, made once with the group in `core/model`): its first six songs, in its order,
tried one after the other by a fetcher of its own (`CollectionArtworkFetcher`), each as a song's
cover is. The first song's alone stood for the group, and an album whose first song had no picture
showed the note in the grid and over its songs, though the rest of them had one. A song found to
have none is remembered as above, so a grid flung back over an album asks for nothing it was
already told; a request given up on stops before the next song. The group's cover is cached under
a key of its own, made of the songs it is looked for in and their versions: which song it comes
from is only known once it is found. A song's own cover, in the editor and the player, is still
the song's alone.

**Which songs have no cover is found in the background** (`LibraryCoverScanner`, one for the app,
as the lossless check is): a row only learns it when it fails to show one. Each song's file is read
with TagLib for its pictures, a picture of any kind counting, since the app shows the first one
where there is no front cover. What was found is kept in Room (`cover_checks`) against the song and
the version of its file, its modification date and size: a restart reads nothing again, and a song
saved with a cover is read again first, before the songs not read yet. A file TagLib reads no tags
of is kept as unknown; one that could not be opened is tried again at the next start. The library
hands it every song it lists (`submit`, beside the lossless check's, in its own list and not in a
collection's), and it works through them one file at a time, yielding between two, and waits while
the lossless check of the library or a batch is at the files. The TagLib binding has no read of
pictures that skips their bytes (`getPictures` copies them too, and cannot tell a format it does
not read from a file without pictures), so it reads the tags with their pictures, as the editor
does.

What it found (`LibraryCoverState`) is the library's "No cover" list, with how far the check has
got beside its count while it runs, and a filter of the songs, the albums and the artists
(`LibraryFilter.noCover`): the songs without a cover; the albums and artists with at least one
such song, each listed whole (`lackingCover`), since a cover is found for an album, not for those
of its songs that lack one. The chip says which it is and counts it, and says it in full when
pointed at. What the check finds lists the songs again only while they are narrowed to those
without a cover: it finds more every few songs. While it waits for the lossless check to be done
with the files, the list says it waits rather than how far it has got (`coversWaiting`): the
lossless check's first pass over a large library can take an hour, and a share that did not move
read as a slow check.

**Albums are grouped by album artist and title** (the artist where there is no album artist), and
known by those two (`TrackCollection.Album.key`), not by MediaStore's album id: MediaStore gives
the songs of one title in a folder one id, whoever they are by, so a compilation without album
artists is one id and several albums. Keyed by the id, two of them had one key, and the grid of
albums closed the app as soon as both were in sight.

**SD cards and USB drives are part of the library.** MediaStore lists the songs of every mounted
volume in one collection, and a song on a card is read, checked and written through the same
`content://` URIs as one on the device. What differs is where it is (`StorageVolumes`):

- A folder is relative to its own volume, so `Music/Album/` on a card would be taken for the
  folder of that name on the device, and the two listed as one. A folder on any other volume is
  shown under the volume's own name, as the system gives it ("SD card", or the card's brand).
- The Folders tab says where each folder is, beside how many songs it has ("2 songs · SD
  card/Music"): two folders can have the same name, as the "CD1" of two albums do.
- "Rescan music folders" asks the scanner to look again at the device's Music and Download
  folders, and at the whole of each card: music on a card is kept anywhere on it.

Results, not exceptions: everything the user can cause or fix (no permission, file gone, format
not supported, offline) is a sealed result the UI can explain.

## ViewModels

`BaseViewModel<Intent, State, Effect>` (`core:ui`):
- `state`: one immutable `StateFlow`.
- `effects`: commands for whoever is on screen now; dropped if nobody listens, since a command
  carried out later would act on whatever the user is doing by then.
- `messages`: snackbars; buffered until shown, since losing one is a silent failure. A newer one
  takes the place of the one on show, and one with an action (Undo) is as short as one without:
  kept for ten seconds, "Saved, Undo" stood over the next button to press, and a tap meant for
  that undid the save. A press anywhere but on the message dismisses it, and goes on to what it
  was meant for (`dismissesMessages`). The shell keeps them above an editor's foot, where Save is,
  and above the keyboard. The editors pad their whole column for the keyboard, not only what
  scrolls, so their foot rides above it while a field is typed in.
- `launch`: runs work and catches every failure, errors too, since native code throws whatever it
  likes. What the caller does not handle goes to `onUnhandledError`, which logs it (the app's only
  log line, tagged with the ViewModel's name); the editor, the batch editor and the library also
  say "Something went wrong". Swallowed, a failed Undo or rescan left no trace at all.

ViewModels never hold a `Context`. `StringProvider` gives them text; ports give them Android work.
System prompts (permissions, MediaStore's write request) are shown by `ActivityResultHost`, to
which each activity lends its launchers in `onCreate`; a use case suspends until the user answers.

## Navigation

One back stack, one Navigation 3 `NavDisplay` (`MetadatorNavDisplay`), keys in
`core:navigation/Keys.kt`. Scene strategies decide the layout: overlays first, then
list-detail, which puts the editor beside the library on wide windows. Destinations are told
whether they share the window (`LocalPaneContext`), never measure it. The list-detail scaffold is
told not to move the focus to the pane it shows (`paneDirective` in `MetadatorApp`): it would give
it to the library's search field, and open the keyboard, on every rotation to a wide window.

**A window 600 dp wide or more is laid out as panels on a ground**, as a desktop tool is. The
shell says so to every screen (`LocalPanels`): the library is a panel with its sidebar, the editor
and the batch editor are the inspector beside it, and the player's bar is docked under both. The
shell keeps the gaps, and the screens draw the panels: the display is padded by the system's bars
and `StudioDefaults.PanelGap` all around, list-detail's spacer is that same gap, room is kept under
the panels for the docked bar, and each screen puts `Modifier.studioPanel` on itself, or nothing on
a phone. The inspector keeps its width whatever the window's (`StudioDefaults.InspectorWidth`):
list-detail is given a pane expansion anchored that far from the end, and the library takes the
rest. Left to itself, the scaffold splits the window down the middle. A list is told which detail
is beside it (`LocalShownDetail`), and marks that song.

A feature moves through the stack with its `Navigator` and nothing else (`BackStackNavigator`,
tested on the JVM). The stack is the library at its root, a collection gone to from it, what is
shown beside each (an editor or the batch editor: `isList` and `isDetail` in `Keys.kt` say which,
as the entries' metadata tells list-detail), and the screens gone to from those.

- `goTo` adds a destination. A list gone to from a list starts with nothing beside it: what was
  beside the one left was chosen there.
- `showDetail` is what a list uses for what is chosen in it: the choice takes the place of what is
  beside the list, an editor and the batch editor alike, so that on a wide window, where the list
  stays beside its detail, choosing song after song does not pile up an editor for each. A detail
  that holds what must not be thrown away says so with `hold` (the editor, while it has unsaved
  changes; the batch editor, while it has edits not applied or its dialog of how applying them
  went), and stays under the next one instead; nothing else is left under it, and a detail chosen
  again comes up rather than twice. What came back with the stack after the process was gone is
  kept until it has said whether it holds anything.
- `showSelection` is what a list shows on its own as its selection changes: the same, but never over
  a detail that holds something, since nobody chose it. Nothing selected takes away only what the
  list asked for last, its selection or the song clicked in it: a file or a song opened by hand over
  that stays when the list changes under it.
- `leave` is a list's arrow: the list goes with what is beside it, but for what holds something.
- `goBack` takes one step: what Back does.

Back goes back one step at a time, through what is open, the selection, what is beside the list,
the collection, then the library's places, as [desktop.md](desktop.md#back) lists. List-detail is
told to pop one entry at a time (`BackNavigationBehavior.PopLatest`): its default pops until the
panes change, and side by side they never do, so it took no Back at all and Android minimized the
app. While the full player covers the screens, their back is off (a dispatcher of navigation events
of their own, turned off), so that a screen composed after the player cannot take Back before it.
At the start, a desktop's window stays where it is (`StayAtTheStart`, `DesktopWindow` in
`MainActivity`); a phone goes home.

Every transition between screens is in `NavigationMotion`:

- **Forward, between panels** (a window of 600 dp or more): the new screen fades up from 32 dp
  towards the end as the old one fades away towards the start: a short shift through a fade, as a
  desktop tool changes what it shows. A set distance, not a share of the window, which may be as
  wide as a monitor. **Backward** is the reverse. Both run on the theme's motion scheme: the
  default spatial spring for what moves, the default effects spring for the screen arriving and
  the fast one for the screen leaving, so that little of the two shows at once. The springs are
  given a threshold of one pixel, or the transition, with both screens composed, would outlast the
  motion.
- **Forward, on a phone** (`push`): the new screen comes in over the old one from the end of the
  window, and the old one steps back under it, a little smaller and dimmed, the way the back
  gesture finds it. **Backward** (`pop`) is the reverse, and ends as the gesture ends. The slide is
  the theme's spatial spring without its bounce: the width of the window past its place and back
  is a screen that lands and rebounds. The shift through a fade, on a phone, was over in 80 ms: a
  flash, not a move.
- **A screen's content fades in as it is read** (the editor, the batch editor): it is usually
  there before the screen has finished arriving, and dropped into place mid-way. `LoadingScreen`
  shows its indicator only after 400 ms: an indicator for the moment before the content was a
  flash before it, not a sign of waiting.
- **Every destination is drawn on the ground** (`Ground`, an entry decorator): the screens draw
  their panels, or their content on a phone, and nothing under it, so without it a screen fading
  over another showed the other through it. Overlays are left without one, to keep in sight what
  they open over.
- **Predictive back**: the screen shrinks under the finger and shows the one behind, then leaves
  the way the finger goes. `NavDisplay` seeks this transition by the gesture's progress and plays
  what is left of it once released, so it is tweens of one length: each spring would run through
  its own time under the same finger. With gesture navigation every back goes through it.
  Meanwhile the screen behind is out of focus (`BehindTheGesture`, an entry decorator over
  `Modifier.outOfFocus`): most at the start, sharp once uncovered. The blur is a layer that exists
  only during the gesture, and is driven while drawing.

The entry provider is remembered in `MetadatorApp`. Entries are equal only while their content is
the same instance, and `NavDisplay` finishes a back gesture that was let go only if the scene it
was showing equals the one the stack ends on: otherwise the leaving screen is gone at once.

"Edit tags" (a file opened with the app) runs `ExternalEditorActivity` in its own task with only
the editor, so closing it returns to the app that opened the file. It and `MainActivity` are a
`MetadatorActivity`, which composes nothing until the settings are read, then provides their
theme, the snackbar's state and the keyboard's shortcuts.

## Theme

`MetadatorTheme` is `MaterialExpressiveTheme` with `MotionScheme.expressive()` (one instance:
Material keys running shape morphs by their spec) and the Studio colors (`StudioColors`, read as
`Studio.colors`): neutral panels on a darker ground, hairline edges, and one accent. The accent is
one of a few (`StudioAccents`), or the phone's own palette when the user asks for it; nothing else
changes with it, so the app is the same app in any of them. The Studio colors are put in
Material's roles (`toColorScheme`), so that Material's components draw in them without being
restyled. They are a handful of fixed values, made where they are needed: nothing is computed off
the main thread. The player, and the editor when the setting asks for it, nest
`MetadatorAccentTheme`, which takes the accent from the cover's dominant color, brought to a
lightness that reads on the panels; the grays stay the app's. That color comes from
`ArtworkAccentSource` (`:core:ui`), which keeps bitmaps out of ViewModels.

A new scheme costs one recomposition of what is under its theme, and nothing else does: what the
theme provides keeps its instance until the scheme changes, so saving an unrelated setting
recomposes nothing. Schemes are swapped, not faded: `ColorScheme` is immutable and its local is
static, so a fade would recompose everything under the theme on every frame. Both activities wait
for the settings before composing, so the default theme is never shown in place of the user's.

Settings keep the 1.x DataStore file name and theme keys, so an update keeps the user's theme. The
accent is kept under the seed color's key; a seed from before that is none of the accents is read
as the first one.

### Shared components

`:core:designsystem` holds what more than one screen uses, so that every screen speaks the same
desktop vocabulary over Material's components:

- The Studio pieces (`Studio.kt`): `Modifier.studioPanel`, a screen's panel; `StudioButton`, as
  primary, secondary or ghost, with its shortcut written after its label as on a key cap (`Kbd`);
  `StudioIconButton`, square, quiet until pointed at, and named by a tooltip; `SidebarTitle` and
  `SidebarItem`, with a count at the end; `StatusPill` and `FactChip`, what a song is in a few
  words; `StudioCheckbox`, `StudioSwitch`, `StudioDivider` and `BrandMark`. `StudioDefaults` has
  their sizes, the panels' gap and the inspector's width.
- `ConnectedChoices`: a few exclusive options as one segmented control (the theme mode, the
  library's places where there is no sidebar to hold them). Used instead of a tab row or a short
  radio list.
- `ActionMenu`, `PopupMenu`, `PopupMenuGroup`, `StudioMenuItem`: menus as a desktop's, a raised
  surface with a hairline edge and rows short enough to read as one, each with its shortcut at the
  end. `ActionMenu` takes a list of `MenuAction`s and closes itself before running one. Over a
  screen they are glass (see Blur). A menu shows, as it closes, what it showed open
  (`heldWhileClosing`): choosing an item changes the state the menu is built from, and a menu that
  followed the change changed as it faded.
- `TonalTextField`: the app's text field, its label above it as a desktop form has them, over a
  well with a hairline edge. The field with the focus takes the accent on its edge, with a soft
  ring; an `emphasized` one too, quieter, and its label says why (the editor: what will be written
  on saving). `TonalFieldDefaults` has the tones for a field in a dialog or a sheet; `FieldWell`
  and `FieldLabel` draw a field of several values the same way. It is built on the state-based
  `BasicTextField`, under the `value` and `onValueChange` the screens keep: a `TextFieldState` in
  between says what is typed, and takes a value the screen sets on its own (an undo, a lookup).
  What is typed comes back a frame or more late, by when more may have been typed, so a value that
  is only that echo is not set again. A screen that turns some input down does it with an
  `inputTransformation`, as it is typed: one that refused it in `onValueChange` would never say the
  refused text back, and the field would go on showing it. `StudioSearchField` is a toolbar's
  search, the same way.
- `ToggleChip`, `ActionChip`, `LabelChip`: a filter or one option of a few, a chip that does
  something, and one that only says something. The second never looks selected, so it is not read
  as a filter that is on.
- `GlassSurface`: what floats over the screen, as glass (see Blur): the player's bar and the
  library's selection bar on a phone, the player's controls over its lyrics on a phone on its side,
  and the menus.
- `PlayingBars`: the three bars on the row of the song that is playing; still while paused.
- `SectionHeader`, `NavigationItem`, `SwitchItem`, `RadioItem`, `PlaceholderCard`: grouped lists
  (their shapes from `GroupShapes`) and what a screen shows instead of content.
- `RolledIn`: something that turns up by rolling into its place through focus, and leaves the same
  way, at the pace of `ThroughFocus`: the album's chip in the player. `fadeThrough` is one thing
  taking the place of another, the old gone before the new fades in, so that neither is seen
  through the other: the library's list re-sorted or filtered, or another tab's; the lossless
  check's verdict in place of its wait; the batch's dialog done in place of running. Material's
  fade through, in tweens for the same reason as `ThroughFocus`: the one arriving waits.
- `ClosesOnEscape`, `SheetQueryField`, `SheetButton`, `SheetMessage`, `rememberSheetClosing`: what
  the sheets that search online are made of. A choice made in a sheet closes it the way a tap
  beside it does, sliding it away, and then is acted on: taken out of the composition on the
  choice, a sheet was there one frame and gone the next.
- `LoadingScreen`: a screen still loading, which shows its indicator only once the wait has gone on
  for 400 ms.
- `Tooltip`, `ContextMenuArea`, `HeldKeys`, `Modifier.marqueeSelection`: what a mouse adds. The
  name of a button that is only an icon, a menu on the second button, the keys held during a
  click, and a rectangle dragged over a list to select ([desktop.md](desktop.md#mouse)).

`:core:ui` holds what two features show alike: `TrackTableHeader` and `TrackTableRow`, the table of
songs, whose columns drop out as its panel narrows (`TrackColumns`: the year, then the format, then
the album; the title, the artist, the quality and the length stay); `TrackRow`, a song on a phone;
`CollectionCard`, an album, an artist or a folder; `QualityBadge`, what the lossless check found in
a song ([lossless-check.md](lossless-check.md#the-whole-library)); and `ArtworkImage`, a cover of
any kind (a picture's bytes, a URI, a URL, a song's own), with the note in its place while there is
none. A picture comes and goes through a fade over the note, and one taking the place of another
starts from the other (Coil's placeholder, under the key of the last picture shown), so that a
song just saved shows the cover it had until the new one is decoded. The editor's cover and the
player's start from the library's thumbnail of the same song (`TrackArtwork.cacheKey`), so the
cover is there as the screen opens and sharpens.

Keys are not handled by screens with `Modifier.onKeyEvent`: a screen offers shortcuts with
`KeyboardShortcuts` (`core:ui`), and the activity's `ShortcutDispatcher` gives them the keys no
view took ([desktop.md](desktop.md#keyboard)).

Animations use the theme's motion scheme (`MaterialTheme.motionScheme`): spatial specs for what
moves or changes size, effects specs for fades. They are kept for changes of state the user caused
or should notice: a selection, a song starting or pausing, a tab switching.

### Blur

Material has no blur tokens; `MetadatorBlurDefaults` (`:core:designsystem`) is where the app decides
where it blurs, and by how much. Blur stands in for elevation: where Material would cast a shadow
under something that floats over content, the app blurs instead. There are two kinds of blur, for
two jobs.

**A surface floating over content that moves beneath it is frosted, not shadowed.** What is behind
it shows through, blurred, under its Material container color made translucent. It is
[Haze](https://github.com/chrisbanes/haze): the content is recorded with `Modifier.hazeSource`, and
the surface applies `Modifier.frosted` with `MetadatorBlurDefaults.surfaceStyle(containerRole)`.
The surface's own container becomes transparent and loses its shadow. Nothing floats bare like
this now: the glass below is built on it.

**What floats over the screen is glass** (`GlassSurface`, with
`MetadatorBlurDefaults.glassStyle`): frosted as above, more see-through, with a hairline edge
lighter along its top, and nothing drawn around it. The blur is on the surface, never around it:
the bars and the menus were once lifted by a halo, the content around them out of focus, which read
as an effect beside them rather than as depth. That is the player's bar on a phone, frosted with
what the shell records, the library's selection bar, with the list the library records, the
player's controls floating over its lyrics on a phone on its side, with the lyrics and the
backdrop the player records, and every menu's groups. While the player's bar travels into the
full player it is solid, since nothing frosts during a shared element transition. The bar docked
under the panels is not glass: it is a panel of the window, which nothing floats over.

- **The source is a sibling of what frosts it, never an ancestor.** A surface inside its own source
  would blur itself.
- **Content passes beneath the surface padded, not inset**, or there is nothing there to frost.
- **Not while a shared element transition runs.** What travels is drawn in the transition's
  overlay, where a frosted surface flickers: it is its solid color until the transition settles.

Two things are recorded. The shell records every screen (`LocalBackdropHaze`) for what floats over
all of them: the player's bar, which is its sibling there, and the menus, which are windows of
their own. A screen with a toolbar of its own records its lists itself, since the toolbar is inside
what the shell records.

**What something opens over goes out of focus** (`Modifier.outOfFocus`): blurred with
`Modifier.blur` and dimmed, by a fraction read while drawing so a gesture can drive it. The shell
does it to everything behind the player as it opens.

**What comes and goes as one surface turns into another does so through focus**
(`Modifier.dissolved`): as faint and as blurred as it is absent, sharpening as it shows. It is
`Modifier.blur` too, so unlike a frosted surface it can run during a shared element transition.
The player's sheet does it to what its bar and its full screen do not share.

Below Android 12 nothing blurs: a frosted surface falls back to its container color, nearly opaque,
and what would go out of focus is only dimmed, and more.

## Editing several songs

The batch editor (`feature/batch`) is opened for a selection ("Edit together"), an album ("Edit
all"), or by the inspector, for up to a hundred songs selected beside it (more are opened only on
purpose, from the placeholder that says how many), and edits its songs as one: a field set there
is set on every song, and a field left alone keeps each song's own value (`BatchEdit`). Besides
the fields, it sets one cover for all, picked or found online
([cover-finder.md](cover-finder.md)), numbers the tracks in order, reads tags from file names, and
gives some songs **numbers of their own** (`BatchEdit.positions`): a track or disc number that
differs from song to song, from the release found online or the disc a merged album's title said.
A number is written in each file's own style (`TrackPositions`), and what a position leaves out, a
total or a number, the file keeps.

It is handed its songs by their MediaStore ids (`BatchEditor`), and finds their files again through
the library as it opens: their URIs were five times the size in the saved back stack. A song
deleted meanwhile, while the editor waited on the stack or the process was gone, is left out, and
the editor says how many were, as it does for a file it cannot read.

- **A field of several values** (artists, album artists, genres, composers) shows them joined with
  the separator of the settings, and takes what is typed split on it, a list from then on
  (`splitValues`). What is typed is shown as typed while the screen holds the values it was taken
  for, and while they are still on their way to it (`TypedValues`): shown joined again from the
  values, "Daft Punk; " was "Daft Punk", and the separator typed for a second value was gone before
  it could be typed.
- **Find metadata** (Ctrl+L, the editor's key) finds the release the songs are from. The sources
  know tracks, each with its album, not albums, so `FindAlbumUseCase` asks them for the album under
  its own name, which finds the songs of an album in the iTunes Store and the title track
  elsewhere, then for up to eight of the songs, spread over the batch: one search at a time, a
  second apart, as MusicBrainz asks, and few enough not to have the iTunes Store turn the app away.
  The query starts from the album and album artist most songs will have, and can be changed in the
  sheet. Each search sends what a search for one song sends: a title, the artist and the album.
- **The releases** (`albumProposals`) are the tracks found grouped by album title and first
  credited artist, as they compare: a track crediting a featured artist ("Daft Punk, Romanthony &
  Todd Edwards") is still on Daft Punk's album. Each source has one say in what a release is (its
  album artists, its date, the year agreed first and then the most precise day, its genres, its
  track count), and a tie goes to the better match. They are ranked by how well their title and
  artist match what was searched, how many of the songs are on them, how close their track count
  is to the number of songs, how many sources have them and whether they have a cover.
- **Choosing a release** fills the album, album artists, date and genres for every song, those it
  has, and gives the songs found on it their track and disc numbers (`positionsFor`): a song is
  found when a track has its title, compared without featured artists and remaster notes, and is
  the same recording by its length, or where a length is missing sits where the file already says.
  A song not found keeps its numbers, and gets only the track total, where the file says its disc
  or the release has one. Sources that disagree on where a song is say nothing: a number is never
  guessed. The titles are left alone. It is all shown as changed, and written with "Apply to all".
- **Merging albums**: when the songs are on more than one album, as the library tells albums apart
  (title, and album artist or else artist), a card at the top lists them with their songs. One
  album spelled two ways, with an album artist on some songs only, or split into "CD1" and "CD2",
  is several albums there. "Merge into one album" sets one title and one album artist for every
  song, those of the album with the most songs unless others are chosen from the albums' or typed
  (`AlbumMerge`). When the titles differ only by a disc suffix ("CD 2", "(Disc 1)"), the title is
  proposed without it, and each song the disc its album's title said. The inspector shows the card
  for any songs selected across albums, as they are selected, so it only proposes, and lists the
  largest three of more than four albums until asked for the rest.
- **Applying** writes it all through `BatchRunner`, file by file, each backed up first and undone
  together ([saving-tags.md](saving-tags.md)). A field a song's format cannot hold is dropped by
  the format and the song still saved; the dialog after the batch says which fields some songs
  dropped (`BatchProgress.notStored`). What no song's outcome says, a backup that cannot be kept
  on a full disk or in a broken database, stops the batch where it got to
  (`BatchProgress.failure`): the dialog counts the songs saved, says "Something went wrong", and
  "Undo all" still puts them back. The runner works in the app's scope, which has no handler: a
  failure thrown on from there ended the app.

## The player

`PlaybackService` is a Media3 `MediaSessionService` with ExoPlayer: background playback, the
system's media notification and lock-screen controls. Its player is built with the audio renderer
and the audio containers only (FLAC, WAV, MP4, AMR, Ogg, Matroska, ADTS, AC-3, AC-4, MP3), so R8
drops the rest of Media3; a new container is one more line there. `Media3PlayerController`
connects to it on first use, and again on the first use after its session went away (the service
stopped), and exposes a `StateFlow<PlaybackState>`, empty while there is no session. It is driven
from the main thread, as Media3 requires. The position is shown as a desktop player shows it
(`SeekLine`): a thin line, which a click, a drag or the arrow keys move, with a knob while the
pointer is over it; between the times in the bar docked on a wide window, with the times under it
in the full player, where there is room, and alone in the controls floating over a phone on its
side, where there is none. The bar on a phone shows it as a hairline along the foot of its glass.

The position is reported twice a second while a song plays, and is read apart from the rest
(`PlaybackPosition`): the sheet hands its bars and screens what plays without it, which stays the
same object while only the position moves, and only those lines, the seek lines and the clock of
the lyrics read where the song is. Read with the rest, it composed the whole player again each
time.

### One surface, two sizes

The player is not a destination. `PlayerSheet` sits in the app shell over the navigation display
and has two states: a bar, and the full screen. On a phone the bar floats over the library and the
collections; on a wide window it is docked under the panels (`docked`, `DockedPlayerBar`), as a
desktop player's bar, and stays under the inspector too. Tapping the bar opens it, and so does
dragging the phone's up: the bar's edges follow the finger out to the edges of the screen while its
corners straighten. Pulling the full player down, or back, closes it.

- It is a shared element transition (`SharedTransitionLayout`) between the two contents of an
  `AnimatedContent`: the bar and the full player.
- The bar's surface and the screen's are one `sharedBounds`, the container, in
  `RemeasureToBounds` mode: its bounds are what animates. What is inside is told to ignore that,
  as the library's own clip-reveal sample does: `skipToLookaheadSize()` so it is laid out once, as
  it will be at rest, and for the full player `skipToLookaheadPosition()` too, so it stays where
  it will be and the container opens over it like a window. Without them everything inside is
  laid out again every frame in bounds that are changing, and the shared elements in it start the
  transition by jumping.
- The cover is a `sharedElement`: the same picture in both, so only the arriving one is drawn.
  Both ask Coil for it under one cache key (`ArtworkImage`'s `cacheKey`), so the one arriving
  starts from the picture already loaded.
- The song's title, its artist and the play button are tied too, and of each **only the version
  the sheet is going to is ever drawn**, from the first frame. So the rule for each is that its
  two versions look the same in the same bounds: then that swap does not show, and it reads as one
  thing moving and growing. Never both at once with one fading into the other: each version has a
  bounds animation of its own, and seeked as this transition is the two are not in step, so they
  show apart, as a double image.
  - The title and the artist are one line each in both players, and the bar's styles are the full
    player's at a smaller size (`TextStyle.resizedTo`, `TrackNameDefaults`), not other roles of the
    type scale: those differ in weight, line height and tracking, and two texts that differ in any
    of them drift apart glyph by glyph. They are in the same colors in both too. They are scaled
    by their height from their start (`scaleToBounds(FillHeight, CenterStart)`), so the two
    coincide for as long as they say the same. They are `sharedBounds`, since a `sharedElement`
    would lay the text out again at every width, with no enter and an exit that snaps, which is
    what draws only one. Both players say the same in each line, so it travels whole. It is not
    clipped to its bounds on the way: on an arc they do not keep the shape of the text, and would
    cut into it. A title too long for its line scrolls in the full player instead of wrapping.
  - The album is the full player's alone and is not part of what travels. It is a `LabelChip`
    under the name that rolls into its place the first time the sheet settles (`RolledIn`): up
    from under it, out of focus from the speed and sharp as it stops. Its place is kept meanwhile,
    so nothing below moves. From then on it stays, and goes with the rest of what only the full
    player has as the sheet is dragged down.
  - A new song replaces the old one's pieces through focus, in both players. Its title and artist
    come in a short way from the side (`TrackLine`), the artist a moment after the title; what
    leaves starts slowly and is quickest as it goes, what arrives is quick and slows to a stop.
    Those are tweens on Material's emphasized accelerate and decelerate curves, since a spring
    cannot end at its quickest. Its cover comes into focus over the old one (`TrackArtwork`),
    which blurs beneath it and stays solid until covered. Nothing is clipped on the way but the
    cover, to its own shape: a blur or a slide cut to a rectangle shows the rectangle.
  - The button is a `sharedElement`, laid out again in the changing bounds. `PlayPauseButton`
    fills the size it is given and draws its icon at half its height, and the bar gives it the
    whole 48 dp touch target: left to Material's 40 dp container inside that target, it jumped to
    fill it on the first frame of the transition. It is filled with the accent in the bars and on
    the screen alike, for the same reason: one that changed color between the two changed it the
    moment it set off.
  - The cover steps back while paused, in the full player only. Both covers scale by as much as
    the sheet is open, since a shared element draws only one of the two.
- All three travel on an arc (`ArcAnimationSpec`), not a straight line: sideways first and up late
  on the way to the screen. That way round because the container's top edge rises at a steady pace
  and clips what gets ahead of it.
- What only one of the two has (the bar's next button and progress; everything else on the screen)
  is not shared. It goes through focus (`Modifier.dissolved`), by how far open the sheet is: the
  bar's is gone a fifth of the way up, the screen's sharpens into place between three and eight
  tenths, and in between there is only the surface changing color. Faded in with the surface from
  the start, the whole screen lay half see-through over the bar.
- Shapes are not animated by shared elements. The container's corner and the cover's are values
  of the same transition (`animateDp`), given to the shape and to the container's overlay clip,
  which shared elements inside inherit.
- `PlayerSheetState` holds a `progress` (0 the bar, 1 the screen) and a `SeekableTransitionState`
  that follows it. A drag sets the progress; a release hands it to a spring with the finger's
  velocity. A tap on the bar opens it with a softer spring, since it starts from rest and has the
  whole way to cover. A navigation transition cannot be held halfway like this, which is why the full player
  stopped being a `NavKey`.
- The bounce is the spring running past either end. The transition cannot go past its ends, so
  that excess (`overshoot`) is drawn on the whole sheet: the screen swells slightly, the bar dips.
- The container moves linearly, so its edge stays under the finger. What it carries eases in and
  out. The corners stay round most of the way and straighten late.
- The surface is never see-through: the surface leaving keeps its place while the one arriving
  fades in over it.
- The full player's list shares its gesture through nested scroll: at its top, pulling further
  down pulls the player down. The back gesture previews the collapse through `NavigationEvent`.
- The whole sheet, bar included, is inside one `MetadatorAccentTheme` seeded from the cover. It is
  one surface: a color that differed between its two sizes would change halfway through.
- `PlayerSheetExpansion` tells the shell how far open the sheet is, which is what takes everything
  behind it out of focus (see Blur). Once the sheet covers the screen the shell stops: the sheet is
  opaque, and what is behind it would be blurred on every frame for nobody to see.

### Layouts

The full player has three layouts (`PlayerLayout`). It is not a destination, so nothing tells it
how much room it has: the shell measures the window and passes the layout that fits.

- `Stacked`: one column with the cover, the controls and the queue (`NowPlayingContent`).
- `SideBySide`, when the window is at least 600 dp wide and wider than tall: the song and its
  controls on the left, and on the right the lyrics or the queue, chosen with `ConnectedChoices`
  (`NowPlayingWideContent`).
- `SideBySideCompact`, the same under 600 dp of height (a phone on its side): there is no room for
  the controls under the cover, so they float over the lyrics in a bar of glass (`GlassSurface`),
  solid while the sheet moves, since nothing frosts during a shared element transition.

Each modifier the sheet ties the bar's pieces with (cover, name, play button, drag handle) goes on
exactly one element in every layout.

All three are the Studio look, and the docked bar grown for a stage: the same pieces, larger.

- Along the top, a row that is the handle the sheet is dragged down by (`PlayerTopRow`): the way
  down at its start, and at its end `Edit tags`, a `StudioButton`, where a Studio screen keeps its
  actions. On a wide window the right panel's `ConnectedChoices` sit on the same line.
- The controls are the docked bar's in the docked bar's order (`TransportControls`): shuffle,
  previous, play, next and repeat, as `StudioIconButton`s around the round play button in the
  accent, at the size of the place they are in (`TransportSizes`: the docked bar's for a pointer,
  the floating bar's for a finger in little room, the stage's under the cover). Shuffle and repeat
  say whether they are on by their tint, the accent, and nothing more, as the docked bar says it.
- The position is the same thin line (`SeekLine`), with its times under it where there is width,
  beside it in the docked bar, and without them in the floating bar, where five buttons leave it
  little enough.
- The queue is rows in the library's style on one card (`UpNext`), with a hairline between them,
  lit under the pointer; on a phone under a `SectionHeader`, on a wide window as the right panel.
- The cover, the song's name in the Studio type scale, and the album as a `LabelChip` under it.

Both layouts are drawn on the ground with the cover blurred beneath it, under a veil of the ground
(`PlayerBackdrop`): a wash of the song's colors on the stage, which is the one screen that is the
song's, while the panels keep their grays and the one accent, the cover's, stays the one accent.
The cover is blurred small and stretched, which looks the same and costs a fraction on every frame
the lyrics move. Below Android 12 the theme's colors take its place. Nothing else decorates the
stage: no pills, no waves, no Material chips.

### Lyrics

The player shows the lyrics a file has in its `LYRICS` tag and nothing else: `LoadLyricsUseCase`
reads them, and nothing is looked up online from the player. Looking them up is the editor's.

- `SongLyrics.parse` (`:lyrics:api`) reads three things into one model: TTML (`Ttml`), LRC and its
  enhanced form with a mark per word (`Lrc`), and plain text. In `SongLyrics.Synced` every line and
  word has an end: its own, or where the next one starts. A timed line with no text is not shown
  but ends the one before it, which is how files mark an instrumental break.
- TTML is XML from a tag, so it is parsed with document types and external entities refused.
- `NowPlayingViewModel` loads the lyrics and the cover's color on every new song, and again each
  time the player is opened: the tags may have been edited in between.
- `PlaybackClock`: the player reports the position twice a second, too coarse to follow a word.
  The clock counts frames between reports and blends each report in rather than jumping to it. It
  is read only while drawing, and only by the line being sung, so nothing recomposes per frame;
  which line is being sung is a `derivedStateOf` that changes once per line.
- `LyricsPane`: the lines are set in a headline of the Studio scale, a weight down from the
  scale's, in the color of text that is not the point (`textSecondary`); the line being sung comes
  forward, a little larger and in the accent, with an effects spec, and the list scrolls it to a
  third of the way down, also with an effects spec so it never bounces. Colors, not opacities: the
  lines are read ahead as text is. Scrolling by hand stops the following until the user asks to go
  back (a `StudioButton`) or leaves the list alone for a while. Tapping a line plays from it. The
  pane fades out at its top and its bottom rather than being cut.
- Where words are timed, the line is drawn by hand from its `TextLayoutResult`, a word at a time:
  the line lit in the text color, and in the accent up to the word being sung and through it in
  step with it, with a soft edge.
  Each word rises a little as it is sung and stays up, and glows while it is sung and shortly
  after, more on a note that is held: the line shines only where the voice is. Words move apart
  from each other, so each row is split between its words and each part is the whole layout,
  clipped and moved. The glow is the word drawn again with a blurred shadow, which falls behind its
  letters, from a layout of its own: a shadow on the whole line would light every word at once,
  and one cut to a word shows the cut. Left to right is assumed.
