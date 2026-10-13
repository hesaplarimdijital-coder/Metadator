# Metamatik documentation

Metamatik is built on [Metadator](https://github.com/BobbyESP/Metadator). How each part of the app
works, and why it was built that way. The rules for working on the code are in
[`AGENTS.md`](../AGENTS.md).

| Document | Covers |
|---|---|
| [architecture.md](architecture.md) | Modules, layers, ViewModels, navigation, theme, DI |
| [saving-tags.md](saving-tags.md) | How a save keeps every tag it didn't change, backups and undo, write access, rescans |
| [lossless-check.md](lossless-check.md) | How the editor tells a real FLAC from one made out of an MP3, and what it cannot tell |
| [cover-finder.md](cover-finder.md) | Where covers are looked for, how they are compared, and how the one to use is chosen |
| [desktop.md](desktop.md) | Samsung DeX and other desktops: windows, keyboard shortcuts, the mouse |
| [testing.md](testing.md) | What is tested where, and what still needs a device |
| [performance.md](performance.md) | How each screen is kept cheap to draw, and how fast the app is measured to be |
| [ci.md](ci.md) | What runs on a pull request, the release build and its secrets |
| [renovation-plan.md](renovation-plan.md) | The plan behind 2.0: the audit of 1.x, the UX goals and the phases |

## Status of 2.0

| Area | State |
|---|---|
| Domain (save, undo, lookup, batch, library queries, lossless check and the check of the whole library, cover finder) | Done. Unit tested on the JVM. |
| Online sources (MusicBrainz, Deezer, iTunes, LRCLIB) | Done. Unit tested with a mock HTTP engine. |
| Android layers and UI | The Studio look: panels on a ground, a sidebar, a table of songs, the editor as an inspector and the player docked under them ([architecture.md](architecture.md#navigation)). Built and used on an Android 17 emulator after every build of Metamatik (see [testing.md](testing.md#on-an-emulator-by-a-script)); still to be checked on phones and tablets ([testing.md](testing.md#not-yet-verified)). |
| Desktop use (windows, keyboard, mouse) | Tried on the emulator as a desktop; still to be checked on Samsung DeX (see [desktop.md](desktop.md#how-far-it-has-been-tried)). |
| Lyrics sync editor | Not started (planned for 2.1). |
