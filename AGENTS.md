# AGENTS.md

Metamatik, built on Metadator, is an Android tag editor for local music: it lists the songs on the
device, edits their tags and covers, finds metadata, covers and lyrics online, says whether a
lossless file is really lossless, edits many songs at once, and plays them. It is used with fingers
on a phone and with a keyboard and a mouse on a desktop (Samsung DeX). Nothing leaves the device but
a song's title, artist and album, when the user searches online.

This file is the map and the rules. How each part works, and why, is in [`docs/`](docs/README.md).

---

## 1. Modules

| Module | What it is | Rule |
|---|---|---|
| `:app` | Composition root: `App`, `MainActivity`, `ExternalEditorActivity`, the navigation shell, DI wiring, flavors. | The only module that names implementations. |
| `:core:model` | `Track`, `ContentRef`, `UserSettings`… | Plain Kotlin. |
| `:core:common` | Dispatchers, `StringProvider`, text helpers. | Plain Kotlin. |
| `:core:domain` | Use cases: load, save, undo, lookup, lyrics, batch, library queries, the lossless check and the check of the whole library (`quality`), the cover finder (`cover`), garbled text (`text`), the check of the library's covers (`artwork`). | Plain Kotlin. Tested on the JVM with fakes. |
| `:core:data` | DataStore settings, Android strings, decoding pictures for the cover finder, the lossless check's verdicts. | |
| `:core:database` | Room: tag backups for undo, the tags read from files for the library, its cover checks. | |
| `:core:network` | Ktor client, cover downloads. | Plain Kotlin. |
| `:core:designsystem` | Theme, tokens, blur, shared components over Material 3 Expressive, what a mouse adds (`Pointer.kt`). | No feature knowledge. |
| `:core:ui` | `BaseViewModel`, messages, artwork and its accent color, a picked picture (`image`), track rows, field labels, keyboard shortcuts (`input`), the cover finder's sheet (`cover`). | What two features show alike goes here, not in one of them. |
| `:core:navigation` | Keys, `Navigator`, scenes, motion. | Every `NavKey` lives here. |
| `:tags:api` / `:tags:taglib` | Reading and writing tags. | TagLib is an `implementation` dependency: no TagLib type leaves `:tags:taglib`. |
| `:library:api` / `:library:mediastore` | The device's songs, file access, write permission, rescans. | |
| `:lookup:api` / `:lookup:musicbrainz` / `:lookup:deezer` / `:lookup:itunes` | Online metadata and covers. | Plain Kotlin. No API keys. |
| `:lyrics:api` / `:lyrics:lrclib` | Lyrics: finding them, and reading LRC and TTML. | Plain Kotlin. |
| `:player:api` / `:player:media3` | The music player. | |
| `:feature:*` | `library`, `editor`, `batch`, `player`, `settings`. | Depend on `:core:*` and `*:api` only, never on an implementation or another feature. The `metadator.android.feature` convention fails the build otherwise. |

Build setup: `buildSrc` holds the convention plugins (`metadator.jvm.library`,
`metadator.android.library`, `metadator.android.compose`, `metadator.android.feature`,
`metadator.android.application`) and `ProjectConfig` (minSdk 26, compile 37.1, target 37, Java 17).
Versions are in `gradle/libs.versions.toml`; the app's version in the root `build.gradle.kts`.

## 2. Rules

### Data safety (read [docs/saving-tags.md](docs/saving-tags.md) before touching a write path)

- **Write only what changed.** A save applies `TagChanges` to the file as it is at that moment;
  every key the user did not touch is written back exactly as read. Never build a property map
  from a list of known fields: TagLib removes every key missing from the map.
- **Pictures are written only when the cover changed**, and replacing or removing the cover keeps
  every other picture.
- **Multi-value fields are lists end to end.** Never join and split them on a separator in between.
- **Every write is backed up first** (`TagBackupStore`) and can be undone.
- **Files are `ContentRef`s (`content://` URIs), never paths.** A path is only read to ask the
  media scanner to rescan.
- **Write access is asked for before writing** (`WriteAccess`), for all files of a batch at once.

### Layers

- `*:api`, `:core:model`, `:core:common` and `:core:domain` are plain Kotlin: no Android, no
  Compose.
- Business logic is a use case in `:core:domain`. Framework work goes behind a port in an `*:api`
  module, implemented next to it.
- What the user can cause is a result, not an exception: `SaveOutcome`, `TagReadResult`,
  `LookupResult`, `AccessResult`.
- Test a port with a fake (see `core/domain/src/testFixtures/.../Fakes.kt`), not with a mock of
  the framework.

### ViewModels

- MVI on `BaseViewModel<Intent, State, Effect>`: one immutable `state`; `effects` for whoever is
  on screen now (dropped if nobody listens); `messages` for snackbars (buffered until shown).
- A ViewModel never holds a `Context`. Text comes from `StringProvider`; Android work goes through
  a port (`ImageSource`, `ArtworkAccentSource`, `WriteAccess`).
- What must survive process death goes in the `SavedStateHandle` (the editor's unsaved text
  changes, the library's tab and search).

### Navigation

- One back stack and one Navigation 3 `NavDisplay` (`MetadatorNavDisplay`). Keys carry ids or URIs,
  never models.
- Features get a `Navigator`; they never touch the stack. A list opens what is chosen in it with
  `showDetail`, which replaces the detail on top unless that one `hold`s unsaved changes.
- On wide windows, list-detail puts the editor beside the library. A destination is told whether it
  shares the window (`LocalPaneContext`); it never measures the window itself.
- From 600 dp wide the shell lays the screens out as panels on a ground (`LocalPanels`): it keeps
  the gaps and the docked player's room, and each screen draws its own panel with
  `Modifier.studioPanel`. The inspector's width is held by a pane expansion anchor, not measured.
- The player is not a destination: the shell measures the window and passes it a `PlayerLayout`.
- Transitions live in `core/navigation/.../motion/NavigationMotion.kt`.

### Desktop (read [docs/desktop.md](docs/desktop.md) before touching input or the manifest)

- The activities are not recreated when the window is resized, moved to another display or
  switched between day and night (`configChanges`). A screen must follow those while running:
  measure with `BoxWithConstraints` or the pane context, read density and the configuration from
  their locals, and never keep a size in pixels across recompositions.
- A screen offers its keys with `KeyboardShortcuts`; the activity's `ShortcutDispatcher` gives it
  the keys no view took. Never `Modifier.onKeyEvent` on a screen: it only sees keys while the focus
  is inside it. A new shortcut needs nothing else to show up in the list of shortcuts.
- What a song can be made to do with a long press must also be in its `ContextMenuArea`, for the
  mouse's second button. A button that is only an icon is wrapped in a `Tooltip`.

### Theme

- `MetadatorTheme` is the Studio look on Material 3 Expressive, with one `MotionScheme.expressive()`
  instance for the app's lifetime. Colors come from `Studio.colors` (neutral panels on a darker
  ground, one accent), which also fill Material's roles: no color literals in a screen.
- A destination with its own color nests `MetadatorAccentTheme`, which changes only the accent (the
  player, and the editor when the setting asks for it, from the cover).
- Use Material components through the design system; don't restyle them per screen. Spacing comes
  from `Spacing` and `StudioDefaults`, grouped-list shapes from `GroupShapes`. Buttons are
  `StudioButton`/`StudioIconButton`, menus are `ActionMenu`/`PopupMenu`, exclusive options are
  `ConnectedChoices`, text fields are `TonalTextField` (no outlined fields; input a screen refuses
  is refused with an `inputTransformation`), a toolbar's search is `StudioSearchField`, chips are
  `ToggleChip`/`ActionChip`/`LabelChip`, what floats over the screen is a `GlassSurface`, a sheet
  that searches online is made of `SheetQueryField`/`SheetButton`/`SheetMessage`, and what scrolls
  says where it is with `Modifier.scrollbar`.
- Blur comes from `MetadatorBlurDefaults`, and stands in for shadows: `Modifier.frosted` (Haze)
  for a surface floating over content (`GlassSurface` for a bar or a menu), `Modifier.outOfFocus`
  for what something opens over, `Modifier.dissolved` for what comes and goes as one surface turns
  into another. The blur is on what floats, never around it. A `hazeSource` is a sibling of what it
  blurs, never an ancestor, and nothing is frosted while a shared element transition runs.
- No deprecated API: the build has no deprecation warnings, keep it so. Animate with the theme's
  `motionScheme`, not hand-written tweens. The player sheet, predictive back, and one thing taking
  the place of another through a fade (`ThroughFocus`, `fadeThrough`) are the exceptions, and say
  why.
- A phone's screens come over each other (`NavigationMotion.push`/`pop`); panels on a ground
  change with a short shift through a fade. A list re-sorted, filtered or of another tab fades
  through (`fadeThrough`), never crossfades; a menu keeps its items while it closes
  (`heldWhileClosing`); a choice in a sheet closes it with its slide first (`rememberSheetClosing`).
- Regular expressions run on Android's engine (ICU), stricter than the JVM's the tests use: escape
  every literal `}` and `]`.

## 3. Build, test, verify

| What | Command |
|---|---|
| Debug APK (no Google services) | `./gradlew :app:assembleFossDebug` |
| Unit tests, every module | `./gradlew test` |
| Format, then verify | `./gradlew spotlessApply`, then `./gradlew spotlessCheck` |

- Spotless formats Kotlin with ktfmt 0.64 (kotlinlang style) and adds the license header from
  `spotless/copyright.txt`. Run it before every commit.
- CI (`.github/workflows/ci.yml`) runs `spotlessCheck`, `test`, `lint` and the debug build of both
  flavors on every pull request and on every push to `master` and `v2`. `release.yml` is started by
  hand and builds the signed release APKs. `metamatik-apk.yml` builds Metamatik's APK, under an
  application id of its own, on every push to `master` (and to `metadator-one`, the branch kept in
  another repository where it was built), and tries it on an emulator. See
  [docs/ci.md](docs/ci.md).
- The `playstore` flavor adds Firebase Crashlytics and Play in-app review. Firebase is only applied
  when `app/google-services.json` exists, so forks and the FOSS flavor build without it.

## 4. Conventions

- Everything is in English: code, comments, docs, commits. The app ships English, Spanish and
  Turkish strings; a new string needs all three (lint reports a missing one as an error).
- Commits follow `type(scope): summary`; the body explains why.
- Comments and KDoc say why, not what.
- Docs describe the app as it is now. When code changes, update the doc that describes it.
