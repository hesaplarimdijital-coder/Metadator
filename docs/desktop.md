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
  again in `onConfigurationChanged`: nothing else would give the system bars the icons that go
  with the new theme.
- **A size to open at, and a smallest size** (`<layout>` in the manifest): the main window opens
  wide enough for the library and the editor side by side, and neither window can be dragged
  narrower than a phone.
- **No touchscreen is asked for** (`uses-feature android.hardware.touchscreen`, not required).
- **A wide window is laid out as a desktop tool.** From 600 dp wide: the library's panel with its
  sidebar and its table of songs, the editor beside it as an inspector 380 dp wide, and the
  player's bar docked under both ([architecture.md](architecture.md#navigation)). The table drops
  columns as its panel narrows, an editor that fills a window goes to two columns from 760 dp, and
  narrower than 600 dp each screen is a phone's. All of it changes while a window is dragged, not
  only on a rotation.

**Choosing another song changes the song in the editor.** On a wide window the library stays
beside the editor, and a desktop is used by clicking one song after another. Each used to open an
editor over the last, for Esc to walk back through one by one, each still holding its song. A list
now shows what was chosen with `Navigator.showDetail`, which puts it in place of the editor on
top, unless that one has changes nobody saved (`Navigator.hold`, told by the editor): then the new
one opens over it, and Back finds the unsaved one where it was.

**A song can be opened in a window of its own** (the menu on a song, below): two editors side by
side, or one beside the library. It is `ExternalEditorActivity`, the editor other apps open with
"Edit with", started by the app itself in a new task for each song
(`rememberNewWindowOpener` in the library).

## Keyboard

### How a key reaches a screen

Compose tells a screen about a key only while something in that screen has the focus. On a
desktop that is not enough: after a click with a mouse nothing has it, and Ctrl+S must still save.

So each activity owns a `ShortcutDispatcher` (`core/ui/.../input`) and hands it every key its
views left alone, from `onKeyDown`. Screens say what they offer while they are showing:

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
| | Ctrl+A | Selects every song |
| | Ctrl+E, Enter | Edits the selected songs together |
| | Esc | Ends the selection |
| | Ctrl+1 … Ctrl+4 | Songs, albums, artists, folders: the sidebar's first places |
| | Ctrl+O | Opens a file |
| | Ctrl+R, F5 | Rescans the music folders |
| | Ctrl+, | Settings |
| | Page Up, Page Down, Home, End | Move through the list of songs |
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
  tap, so left alone a right click would open the song.
- **Ctrl and Shift change what a click selects** (`HeldKeys`, `Modifier.trackHeldKeys`): Ctrl adds
  or removes one song, Shift takes every song from the last one selected to this one
  (`LibraryIntent.SelectRangeTo`). As in a file manager.
- **A button that is only an icon says what it does** when the pointer rests on it (`Tooltip`). The
  text is the one a screen reader was already given. The pointer is an arrow over it, even at the
  end of a text field, whose I-beam would otherwise reach it.
- **What scrolls says where it is** (`Modifier.scrollbar`, Material's own behind it): the songs,
  the albums, the editor's fields, the batch editor and the sheets that search show a scrollbar
  while they move, and the list of shortcuts shows one all the time. It is a sign, not a handle.
- **The table of songs works as a desktop's.** A click on a row opens the song beside it, a click
  on its box selects it, and a click on a column's head sorts by that column, the other way on a
  second click; the head's box selects every song or none. The player's bar has a seek line that a
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
