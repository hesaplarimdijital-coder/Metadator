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
- **A drag over the rows costs what the selection needs.** The rectangle asks for the whole
  selection at each row it crosses (`LibraryIntent.SelectIds`): the library checks it against the
  ids of what is listed, made once with the listing (`Listing.listedIds`), not at each row, and the
  inspector only counts what is selected until the selection rests, then reads the list once for
  what to show (`Inspected` in `LibrarySection.kt`).
- **A search lists what was typed last.** The library is filtered, sorted and grouped off the main
  thread for the latest query only (`conflate` in `LibraryViewModel`): at most one listing no longer
  wanted, then the latest, where a fast typist queued one a letter. The checks are handed the
  library once for each version of it, not at each letter; what the counts and filters ask of a
  song is worked out once for it (`Track.needsAttention`, `Track.fileExtension`); and an artist's
  or a folder's songs are sorted with one comparator for all, not a collator made for each.
- **The domain's types are stable.** What the domain hands the screens is immutable data, declared
  so in `compose_stability.conf`. Without it a song's quality, made again equal to the last, was
  taken for a change, and every row of the library was drawn again each time a song was checked.
- **What changes often is followed on its own.** The player reports the position twice a second.
  The library and the editor follow only which song plays and whether it does
  (`map { … }.distinctUntilChanged()` in their ViewModels), the window only whether anything plays
  (`derivedStateOf` in `MetadatorApp`), and the player's own bars and screens what plays without
  where it is: only the slider, the line under the bar and the lyrics read the position
  (`PlaybackPosition`). Before, the whole window and the whole player were composed again twice a
  second while music played.
- **What moves is drawn, not composed.** A switch's thumb is placed in the layout phase
  (`Modifier.offset { }`) and its track drawn behind it (`drawBehind`), so its animation recomposes
  nothing; the bars of the player read how far it is open while drawing.
- **Covers are asked for once.** A row builds its image request once per picture, and a song
  whose file holds no picture is remembered as such for five minutes (`TrackArtworkFetcher`), so a
  list flung through does not open the file again each time the row comes back in sight
  ([architecture.md](architecture.md) on covers).
- **Sums are made where the list is.** The size of the songs shown is summed by the ViewModel,
  once per change of the list, not by the screen each time it is drawn; a number format is made
  once per language.
- **The items of a lazy list have keys of their own.** A key is what a list knows an item by while
  it scrolls; two items with one key close the app as soon as both are in sight. Albums are keyed
  by what makes them the album they are, not by MediaStore's album id, which a compilation without
  album artists shares between several albums ([architecture.md](architecture.md)).

## Reading the numbers

Each build's report has its own. They compare only between runs on machines about as fast as each
other, and the runs do not all get one: of two runs within the hour, the second took four times
as long to copy the songs to the device, a step the app has no part in, and its app started in
3500 ms where the first one's had started in 1676 ms, though nothing in the build had changed how
it starts. So the step begins by saying how fast the emulator is: the time its shell takes to
count to 100,000.

Two builds are best compared on one machine: a push with `[perf-ab <run id>]` in its message times
this build and the APK of that earlier run on one emulator, alternately, twice each, with the same
library of three hundred songs from the start ([ci.md](ci.md)).

For typing and scrolling, the app's thread a frame: the middle frame, then the slowest tenth.

| On a fast run | Before the work above (`1dfa53d`) | After it (`4aada86`) |
|---|---|---|
| Starting cold | 1676 ms | 1901 ms |
| Typing in the editor | 79 ms, 137 ms | 90 ms, 136 ms |
| Scrolling the table on a wide window | 52 ms, 79 ms | 53 ms, 83 ms |
| Scrolling a phone's rows | 43 ms, 74 ms | 41 ms, 51 ms |
| Memory held | 76 MB | 84 MB |

The two runs were about as fast: the steps the app has no part in took them the same time (the
second's shell counted to 100,000 in 794 ms; the first's was not asked). The numbers are the same
within what runs vary by, the same build having held 76 MB in one run and 84 MB in another, but
for the slowest frames of a phone's rows. That is to be expected: what the work above saves is in
what the run does not do, scrolling while music plays and while the library is being checked.
