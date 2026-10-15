# On a desktop: windows, a keyboard and a mouse

Samsung DeX turns a phone into a desktop: the app is a window that is dragged to any size, on a
monitor that comes and goes with the cable, used with a keyboard and a mouse and often no touch at
all. A Chromebook, a tablet with a keyboard cover and Android's own desktop windows are the same
thing to an app. This is what the app does about each of the three.

Nothing here is DeX-only code. It is what Android asks of an app on any large screen, plus two
entries in the manifest that only Samsung reads.

## Windows

- **The activities follow the window while running.** Both declare `configChanges` for everything
  a window on a desktop changes: its size as it is dragged, the display it is on and that
  display's density, the keyboard and mouse being plugged in, and day and night. Compose lays the
  screen out again for each. Recreating the activity instead flashes on every resize and closes
  whatever sheet or dialog was open.
- **The app keeps running when the phone is docked or undocked.** One UI restarts the apps that are
  open when DeX starts, unless they say they handle the new display themselves:
  `com.samsung.android.keepalive.density` and
  `com.samsung.android.multidisplay.keep_process_alive` in the manifest say so. It is the
  `configChanges` above that make it true.
- **Day and night are among the changes followed**, so each activity calls `enableEdgeToEdge()`
  again in `onConfigurationChanged` (`MetadatorActivity`, which both are): nothing else would give
  the system bars the icons that go with the new theme.
- **A size to open at, and a smallest size** (`<layout>` in the manifest): the main window opens
  wide enough for the library and the editor side by side, and neither window can be dragged
  narrower than a phone.
- **No touchscreen is asked for** (`uses-feature android.hardware.touchscreen`, not required).
- **A wide window is laid out as a desktop tool.** From 600 dp wide: the library's panel with its
  sidebar and its table of songs, the editor beside it as an inspector 380 dp wide, and the
  player's bar docked under both ([architecture.md](architecture.md#navigation)). The table drops
  columns as its panel narrows, an editor that fills a window goes to two columns from 760 dp, and
  narrower than 600 dp each screen is a phone's. All of it changes while a window is dragged, not
  only on a rotation. The widths and heights where a layout changes are read together in
  `StudioDefaults`.

**Choosing another song changes the song in the editor.** On a wide window the library stays
beside the editor, and a desktop is used by clicking one song after another. Each used to open an
editor over the last, for Esc to walk back through one by one, each still holding its song. A list
now shows what was chosen with `Navigator.showDetail`, which puts it in place of what is beside
the list, an editor or the batch editor alike, unless that one has changes nobody saved
(`Navigator.hold`, told by the editor, and by the batch editor while it has edits not applied or
its dialog of how applying them went): then the new one opens over it, and Back finds the unsaved
one where it was. Nothing else is left under it: an editor that held changes when another song was
chosen, and has saved them since, goes at the next choice, and a song chosen again while it holds
changes comes up rather than open twice. Left there, they came back as old songs once the one over
them went.

**The inspector follows the selection** (`InspectorFollowsSelection` in the library): two songs or
more selected are the batch editor for them, one is its editor, and with nothing selected it is the
song last opened with a click, or nothing. Albums, artists or folders selected are their songs:
two albums selected are the batch editor for both, which offers to merge them.
More than a hundred songs are not followed: the batch editor reads every song it is given, and
Ctrl+A on a large library had it read thousands of files nobody asked for. The inspector says how
many are selected instead, with Edit together (Ctrl+E) to open the batch editor for them on purpose.
A selection that grows (a range, a drag) is shown once it has rested 300 ms, since each step would
read every song again; an ended selection shows at once what stands in for it. It never comes over
unsaved changes (`Navigator.showSelection`): they stay in sight until saved or thrown away, and the
selection is shown from its next change. A song or a file opened by hand stays until the selection
changes, and stays when the list changes under it: a search or a smart list that leaves out the
song clicked before closes that song's editor, when it is what is beside the list, and nothing
else. The song's own save taking it off the list does not close it: repaired in "Garbled text",
given a cover in "No cover", saved into another album while that album is open, the song leaves
the list and its editor stays, with "Saved" and its Undo, until another song is clicked or what is
listed is changed (the search, a filter or a chip, the place): only those are judged by what they
leave out (`LibraryState.clicked`). On a phone nothing follows: the selection is edited together
from its bar.

**A collection is a list with its own beside it.** Opening an album, an artist or a folder from
the library starts it with nothing beside it (`Navigator.goTo` drops what the library showed there,
unless it holds unsaved changes), and the arrow at its top leaves it with what was opened beside it
in one press (`Navigator.leave`): an editor with unsaved changes stays, beside the library. The
arrow used to be Back, whose first step was the song beside the collection. With nothing selected,
Esc does what the arrow does, as a shortcut of the collection's own: left to Android's fallback,
an Esc from a keyboard it does not know (the emulator's) was never made a Back.

**A song can be opened in a window of its own** (the menu on a song, below): two editors side by
side, or one beside the library. It is `ExternalEditorActivity`, the editor other apps open with
"Edit with", started by the app itself in a new task for each song
(`rememberNewWindowOpener` in the library).

## Back

Back comes from the system's gesture or button, from Esc (Android's fallback for an Esc nobody
took), and from a mouse's back button. All three arrive as the same Back, and each press goes back
one step:

1. A sheet, a dialog or a menu closes: each is a window of its own and takes Back first.
2. The full player closes. From the moment it is asked to open the screens take no Back at all
   (`MetadatorApp` turns off their dispatcher of navigation events while `PlayerSheetExpansion`
   says it is open): a screen composed after the player, as the library is again once back from a
   collection, came before it and ended a selection unseen; and with the off switch on how far it
   was open, a spring at rest a hair short of the top let the library's history take each press
   while the player stayed. Esc is also the player's own shortcut, since Android makes a Back of
   an Esc only for a keyboard whose map says so.
3. The selection ends, in the library or a collection.
4. What is beside the list goes: the editor or the batch editor. An editor with unsaved changes asks
   first.
5. The collection goes.
6. The library goes back to the place visited before (songs, albums, a list such as "Needs
   attention", a card): the last ten are kept, like a browser's, with the library's saved state.
7. At the start, a phone goes home, as Android does from any app. A desktop's window stays as it
   is (`StayAtTheStart`): Android would minimize it, and a second press of Esc or of the mouse's
   button took the window from under the pointer. It is the last to be asked, as the first added
   of those that take Back, and it is off while the full player is open at all: after a change of
   layout, Back went to it rather than to the player, which stayed open.

Steps 4 and 5 are list-detail's, which used to take none of them: it went back until its panes
changed, and with the library and the editor side by side they stay as they are whatever is in
them, so Back went to Android, which minimized the app. It now takes one entry at a time
(`BackNavigationBehavior.PopLatest`).

What a desktop's window is, `DesktopWindow` in `MainActivity` says, from three things Android tells
any app: the window is one among others (multi-window mode, which DeX, Android's desktop windows,
Chromebooks and freeform windows put every app in, and a phone's split screen too), its display has
no touchscreen (the monitor DeX runs on), or what last pointed at it was a mouse rather than a
finger. Android does not say where a Back came from, a mouse's button and the gesture alike; a
mouse in hand is the nearest thing to knowing. DeX says it is DeX only through fields of Samsung's
own, read by reflection, which nothing here does.

## Keyboard

### How a key reaches a screen

Compose tells a screen about a key only while something in that screen has the focus. On a
desktop that is not enough: after a click with a mouse nothing has it, and Ctrl+S must still save.

So each activity owns a `ShortcutDispatcher` (`core/ui/.../input`) and hands it every key its
views left alone, from `onKeyDown`; both get this from `MetadatorActivity`. Screens say what they
offer while they are showing:

```kotlin
KeyboardShortcuts(
    title = stringResource(R.string.shortcuts_editor),
    shortcuts = listOf(
        Shortcut(KeyEvent.KEYCODE_S, stringResource(R.string.save), ctrl = true) {
            onIntent(EditorIntent.Save)
        },
    ),
)
```

What follows from asking last, after the views:

- A text field takes what it types and its own shortcuts first. A shortcut made of a plain key
  (Space, Enter, Page Down) never fires while typing, and Ctrl+A in a field selects its text, not
  every song.
- The library's search field takes Esc too (`StudioSearchField`): it clears what is typed, and with
  nothing typed gives up the focus. An Esc nobody takes is Back, and Back in the library goes to
  the place visited before: an Esc meant for the field changed the list under it.
- A dialog, a sheet and a menu are windows of their own. What is under them gets no keys, so
  Ctrl+S does not save behind a dialog.
- Esc closes them. A dialog and a menu do it themselves; Material's sheet does not, since its
  window takes the key for a cancel it ignores, so every sheet's content goes in
  `ClosesOnEscape`. That content takes the focus as the sheet opens: the key only reaches what
  holds the focus, and a sheet opened with Ctrl+K has had nothing in it clicked.
- When two screens on show want the same key, the one that came last has it: the editor over the
  library beside it.

The list of shortcuts is built from what is registered, so it is always the truth: in the app
(F1, Ctrl+/, or "Keyboard shortcuts" in the library's menu while a keyboard is attached), and in
the system's own list (`onProvideKeyboardShortcuts`).

The list is longer than a small window. Whoever opened it with a key has no wheel to move it with,
so it takes the focus as it opens: the arrows, Page Up, Page Down, Home and End move through it,
Tab goes on to its button, and Esc closes it. A scrollbar at its edge stays in sight: without
one, a list cut short looks like a list that ends there.

**To add a shortcut**, add a `Shortcut` to the screen's `KeyboardShortcuts`. Do not handle keys
with `Modifier.onKeyEvent` on a screen: it only works while the focus is inside.

### The shortcuts

| Where | Keys | Does |
|---|---|---|
| Everywhere | F1, Ctrl+/ | Shows the shortcuts |
| | Esc | Goes back, or closes what is open (Android's own; the sheets' own, `ClosesOnEscape`) |
| | Tab, arrows, Enter | Move the focus, and use what has it (Compose's own) |
| Player | Space | Plays or pauses |
| | Ctrl+→, Ctrl+← | Next and previous song |
| Library | Ctrl+F | Goes to the search field |
| | Esc, in the search field | Clears it; with nothing typed, leaves it |
| | Ctrl+A | Selects every song |
| | Ctrl+E, Enter | Edits the selected songs together |
| | Esc | Ends the selection |
| | Ctrl+1 … Ctrl+4 | Songs, albums, artists, folders: the sidebar's first places |
| | Ctrl+O | Opens a file |
| | Ctrl+R, F5 | Rescans the music folders |
| | Ctrl+, | Settings |
| | Page Up, Page Down, Home, End | Move through the list of songs |
| Albums, artists, folders | Ctrl+A | Selects every one listed |
| | Ctrl+E, Enter | Edits the songs of those selected together |
| | Esc | Ends the selection |
| A collection | Ctrl+A | Selects every song of the album, artist or folder |
| | Ctrl+E, Enter | Edits the selected songs together |
| | Esc | Ends the selection; with nothing selected, leaves the collection |
| | Page Up, Page Down, Home, End | Move through its songs |
| Editor | Ctrl+S | Saves |
| | Ctrl+L | Finds metadata |
| | Ctrl+Shift+L | Finds lyrics |
| | Ctrl+K | Finds a cover |
| | Ctrl+Shift+K | Picks a cover from the device |
| | Ctrl+P | Plays or pauses the song |
| | Ctrl+W | Closes the editor (asks first, with unsaved changes) |
| | Page Up, Page Down | Move through the fields |
| Editing several songs | Ctrl+S | Applies to all |
| | Ctrl+K | Finds a cover for all |
| | Ctrl+L | Finds the album's metadata |
| | Ctrl+W | Closes |

## Mouse

`core/designsystem/.../component/Pointer.kt` has the three things a mouse adds, and
`Scrollbar.kt` a fourth.

- **The second button opens a menu** (`ContextMenuArea`), where the pointer is, with what can be
  done with a song: edit, play, select, edit the selection together, open in a new window. The
  click is taken before the song sees it. Android does not tell the buttons of a mouse apart for a
  tap, so left alone a right click would open the song. An album, an artist and a folder have one
  too (`rememberCollectionMenu`): open, play, shuffle, edit all its songs, select; on one of several
  selected, edit them together, and for albums, merge them.
- **Ctrl and Shift change what a click selects** (`HeldKeys`, `Modifier.trackHeldKeys`): Ctrl adds
  or removes one song, Shift takes every song from the last one selected or opened to this one
  (`LibraryIntent.SelectRangeTo`), on a row and on its box alike, in the library and in a
  collection, whose songs are taken in its own order. As in a file manager. Without either, a
  click with the mouse opens the song and ends the selection there was, as a file manager does; a
  finger's, while songs are selected, adds the song to them or takes it away, as on a phone. The
  pointer that clicked tells them apart (`HeldKeys.mouse`), not the window: a phone has a mouse
  plugged in, and a narrow window on a desktop is still clicked with one. Albums, artists and
  folders are selected the same way (`LibraryIntent.ToggleCollection`, `SelectCollectionsTo`), or
  with a long press, and show a check while they are. Over them the panel's header then says how
  many, with what can be done with them: play them, edit their songs together, and for albums,
  merge them. Merging is editing together under another name: the batch editor opens beside the
  library with every song of the albums, and offers to give them one album and one album artist.
- **A rectangle dragged with the first button selects** (`Modifier.marqueeSelection`,
  `MarqueeSelection.kt`, for a lazy list), as in a file manager: pressed anywhere over the list, on
  a row or beside the rows, and dragged past the touch slop, a rectangle in the accent follows the
  pointer and every item it touches is selected; with Ctrl held as the button went down, what it
  touches is added to what was selected. Near the top or bottom edge, and past it, the list
  scrolls, faster the further out, and the rectangle keeps hold of where it began. A click that
  does not move is still the row's, the second button still opens the menu, and a finger, a stylus
  and the wheel do what they did. A release, Esc or the pointer lost end it, and what it selected
  stays. The songs have it, the library's and a collection's, in the table and in rows
  (`LibraryIntent.SelectIds`, the whole selection each time, with a Shift+click next taking its
  range from the song where the drag began). A collection's cover and name and its pinned bar are
  not songs and keep their own clicks; while the bar is pinned at the top it is the list's top
  edge, under which the rows scroll out of sight.
- **A button that is only an icon says what it does** when the pointer rests on it (`Tooltip`). The
  text is the one a screen reader was already given. The pointer is an arrow over it, even at the
  end of a text field, whose I-beam would otherwise reach it.
- **What scrolls says where it is** (`Modifier.scrollbar`, Material's own behind it): the songs,
  the albums, the editor's fields, the batch editor and the sheets that search show a scrollbar
  while they move, and the list of shortcuts shows one all the time. It is a sign, not a handle.
- **The table of songs works as a desktop's.** A click on a row opens the song beside it, a click
  on its box selects it, and a click on a column's head sorts by that column, the other way on a
  second click; the head's box selects every song or none. A collection's songs are the same table
  under its cover and name (`SongsTable` with a header), selected the same way, with the same bar
  for what to do with them; its columns sort nothing, since its songs are in its own order, an
  album's by disc and track. The player's bar has a seek line that a
  click or a drag moves, and the arrow keys once it has the focus.

And two things that need no code of the app's: the wheel scrolls every list, and Material's
components show where the pointer and the focus are.

**A picture dropped on the editor's cover becomes the cover** (`Modifier.imageDropTarget` in the
editor): dragged from a file manager in a window beside the app. The app asks for the permission
that goes with the drag, which lasts as long as the activity.

## How far it has been tried

On an Android 17 emulator made to look like a desktop (1920 x 1080 at the density of a monitor,
a keyboard with real keys, a mouse made up with `uinput`), by the script that uses each APK
([testing.md](testing.md#on-an-emulator-by-a-script)): the shortcuts, the list of them, Esc, Tab
and Enter, the window resized and moved to another density, night, a narrow window, the second
button's menu, a song in a window of its own, Ctrl+click and Shift+click, and the tooltip and the
arrow of a pointer resting on an icon button.

Not on Samsung DeX itself: docking and undocking, and what One UI does with the two lines of the
manifest that only it reads, have not been seen. See [testing.md](testing.md#not-yet-verified) for
what to try first.
