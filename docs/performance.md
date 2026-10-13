# Performance

The app should open at once, scroll a library of thousands of songs without a stutter on a phone
and on a desktop's window, and never keep typing in the editor waiting. This is how each screen is
kept cheap to draw, and how that is measured after every build.

## What is measured

The emulator run (`.github/smoke/smoke.py`, see [testing.md](testing.md#on-an-emulator-by-a-script))
ends with **How fast it is**, which puts numbers in its report:

- **Starting cold**: the app stopped, then started three times with `am start -W`; the middle of
  the three `TotalTime`s.
- **Typing in the editor**: sixty letters typed into a song's title, then the change discarded.
- **Scrolling**: three hundred songs (one song with a cover, copied), flung up and down through the
  table on a wide window and through the rows on a phone.
- **Memory**: what the app holds once it has scrolled, as `dumpsys meminfo` counts it (PSS).

For each frame of typing and scrolling, the report gives the app's own part of it: from the input
being handled to the frame's drawing being handed to the render thread (`HandleInputStart` to
`SyncStart` in `dumpsys gfxinfo framestats`). That is composition, layout and recording what to
draw: the part this code decides. The whole frame is not compared: the emulator draws without a
graphics card of its own, so every frame is late there whatever the app does, and `gfxinfo` calls
all of them janky.

The numbers are for comparing one build with the next on the same emulator. They say nothing of
how fast a phone is.

## What keeps a frame cheap

Compose draws a screen again from the state it reads, and skips what reads nothing that changed.
Most of the work here is making sure that a change touches only what shows it.

- **What is pressed keeps its identity.** A screen sends its ViewModel intents through
  `rememberIntents(viewModel)`, one function for as long as the ViewModel lives; the library's
  actions are one remembered object (`LibraryActions`). A function made again at each change of the
  state would be new to every row it was handed to, and no row could be skipped.
- **A row closes over its song, not over the screen's state.** What a click does depends on the
  selection, the keys held and the layout; the row reads them when it is clicked
  (`rememberUpdatedState` in `LibrarySongs.kt`), so a row is drawn again only when its own song
  changes.
- **The domain's types are stable.** What the domain hands the screens is immutable data, declared
  so in `compose_stability.conf`. Without it a song's quality, made again equal to the last, was
  taken for a change, and every row of the library was drawn again each time a song was checked.
- **What changes often is followed on its own.** The player reports the position twice a second.
  The library and the editor follow only which song plays and whether it does
  (`map { … }.distinctUntilChanged()` in their ViewModels), and the window only whether anything
  plays (`derivedStateOf` in `MetadatorApp`). Before, the whole window was composed again twice a
  second while music played.
- **What moves is drawn, not composed.** A switch's thumb is placed in the layout phase
  (`Modifier.offset { }`) and its track drawn behind it (`drawBehind`), so its animation recomposes
  nothing; the bars of the player read how far it is open while drawing.
- **Covers are asked for once.** A row builds its image request once per picture, and a song
  whose file holds no picture is remembered as such (`TrackArtworkFetcher`), so a list flung
  through does not open the file again each time the row comes back in sight
  ([architecture.md](architecture.md) on covers).
- **Sums are made where the list is.** The size of the songs shown is summed by the ViewModel,
  once per change of the list, not by the screen each time it is drawn; a number format is made
  once per language.
- **The items of a lazy list have keys of their own.** A key is what a list knows an item by while
  it scrolls; two items with one key close the app as soon as both are in sight. Albums are keyed
  by what makes them the album they are, not by MediaStore's album id, which a compilation without
  album artists shares between several albums ([architecture.md](architecture.md)).

## Measured

Each build's report has its own numbers. These are the ones of the build before the work above
and of the first one with it.

| Build | Starting cold | Memory |
|---|---|---|
| Before this work (`1dfa53d`) | 2763 ms | 78 MB |
