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
- **The layouts were already made for it.** List-detail puts the editor beside the library on a
  wide window, the editor goes to two columns from 760 dp, and the player has layouts of its own.
  They now change while a window is dragged, not only on a rotation.

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
- When two screens on show want the same key, the one that came last has it: the editor over the
  library beside it.

The list of shortcuts is built from what is registered, so it is always the truth: in the app
(F1, Ctrl+/, or "Keyboard shortcuts" in the library's menu while a keyboard is attached), and in
the system's own list (`onProvideKeyboardShortcuts`).

**To add a shortcut**, add a `Shortcut` to the screen's `KeyboardShortcuts`. Do not handle keys
with `Modifier.onKeyEvent` on a screen: it only works while the focus is inside.

### The shortcuts

| Where | Keys | Does |
|---|---|---|
| Everywhere | F1, Ctrl+/ | Shows the shortcuts |
| | Esc | Goes back, or closes what is open (Android's own) |
| | Tab, arrows, Enter | Move the focus, and use what has it (Compose's own) |
| Player | Space | Plays or pauses |
| | Ctrl+→, Ctrl+← | Next and previous song |
| Library | Ctrl+F | Goes to the search field |
| | Ctrl+A | Selects every song |
| | Ctrl+E, Enter | Edits the selected songs together |
| | Ctrl+1 … Ctrl+4 | Songs, albums, artists, folders |
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
| | Ctrl+W | Closes |

## Mouse

`core/designsystem/.../component/Pointer.kt` has the three things a mouse adds.

- **The second button opens a menu** (`ContextMenuArea`), where the pointer is, with what can be
  done with a song: edit, play, select, edit the selection together, open in a new window. The
  click is taken before the song sees it. Android does not tell the buttons of a mouse apart for a
  tap, so left alone a right click would open the song.
- **Ctrl and Shift change what a click selects** (`HeldKeys`, `Modifier.trackHeldKeys`): Ctrl adds
  or removes one song, Shift takes every song from the last one selected to this one
  (`LibraryIntent.SelectRangeTo`). As in a file manager.
- **A button that is only an icon says what it does** when the pointer rests on it (`Tooltip`). The
  text is the one a screen reader was already given.

And two things that need no code of the app's: the wheel scrolls every list, and Material's
components show where the pointer and the focus are.

**A picture dropped on the editor's cover becomes the cover** (`Modifier.imageDropTarget` in the
editor): dragged from a file manager in a window beside the app. The app asks for the permission
that goes with the drag, which lasts as long as the activity.

## Not yet verified

All of this was written and compiled without a device or DeX at hand. See
[testing.md](testing.md#not-yet-verified) for what to try first.
