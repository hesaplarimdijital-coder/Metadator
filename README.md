<div align="center">
    <img src="./assets/metamatik_logo.png" alt="Metamatik icon" width="100" height="100"/>
</div>
<h1 align="center"><b>Metamatik</b></h1>
<div align="center">

**Fix your music's tags, covers and lyrics, on a phone or at a desk. On your device, without losing
a thing.**

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3_Expressive-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat-square&logo=android&logoColor=white)

</div>

Metamatik is built on [Metadator](https://github.com/BobbyESP/Metadator), by BobbyESP: the same
careful tag editor, in the look of a desktop tool, with a check of the whole library for lossless
files that are not.

## Features

- **Edit every tag**: title, artists, album, album artists, year, genres, track and disc numbers,
  credits, comment, BPM, ISRC, lyrics, and any other tag in the file under *All tags*.
- **Never lose data**: Metamatik writes only what you changed, keeps every other tag and picture,
  and backs the file up first. Every save can be undone.
- **Find metadata online**: search MusicBrainz, Deezer and the iTunes Store at once, compare each
  field with what the song has, and pick what to use.
- **Find the right cover**: every source's covers for the song, compared on your device, with the
  one that suits it best first and why: the album and artist match, several sources have the same
  picture, it is a larger copy of the cover the file already has. Up to 1500 px.
- **Know what a FLAC is worth**: the editor looks at the audio of FLAC and WAV files and says
  whether it is real lossless, was made from an MP3 or AAC (and at about what bitrate), or was
  upscaled. The library checks all of its lossless songs on its own: a Quality column in the
  table of songs, and a Fake FLAC list of the ones made from a lossy file.
- **Lyrics**: find plain or synced lyrics on LRCLIB.
- **Edit many songs at once**: set an album artist or a cover for a whole album, find that cover
  online for all of its songs in one go, number tracks, read tags from file names.
- **A library that shows what to fix**: search everything, sort, filter by format, and lists of
  the songs that need attention and of the fake FLACs, first among the filters.
- **A simple music player**, with background playback and the system's media controls.
- **Edit tags** from any file manager or player, no library permission needed.
- **The look of a studio tool**: neutral panels on a darker ground and one accent, with a sidebar,
  a table of songs and the editor beside it as an inspector on a wide window, and bars of glass
  over the list on a phone. Light or dark, an accent of your choice or the phone's own, and the
  editor in the colors of each song's cover if you like.
- **At home on a desktop** (Samsung DeX, a Chromebook, a tablet with a keyboard): a window of any
  size, keyboard shortcuts for everything (F1 lists them), a menu on the right mouse button, Ctrl
  and Shift to select, a cover dropped in from the file manager, and a song in a window of its
  own.
- **Made for Android 17**, which it targets, and tried on an Android 17 emulator after every
  build.

Supported formats: everything TagLib reads, including MP3, FLAC, M4A/AAC/ALAC, Ogg Vorbis, Opus,
WAV, AIFF, WMA and APE.

## Download

The latest build is `metamatik.apk` in the
[Metamatik release](https://github.com/hesaplarimdijital-coder/Metadator/releases/tag/metamatik-apk).
It installs next to Metadator, under an id of its own, and each build installs over the one before.

## Privacy

Metamatik works on your device. The only thing that leaves it is a song's title, artist and album
when you search for metadata, a cover or lyrics, sent to the sources you search (the iTunes Store
is also told your region, to search its catalogue there). Covers are compared, and audio is
checked, on the device. The APK published here is the FOSS build, which sends nothing; a build of
the playstore flavor sends anonymous crash reports.

## Build it

Requirements: JDK 21 and the Android SDK (compile SDK 37).

```bash
./gradlew :app:assembleFossDebug   # an APK without Google services
./gradlew test                     # unit tests
./gradlew spotlessApply            # format before committing
```

How the code is organized, and the rules for changing it, are in [AGENTS.md](AGENTS.md) and
[docs/](docs/README.md).

## Translations

English, Spanish and Turkish are included. Contributions of other languages are welcome: copy
`src/main/res/values/strings.xml` of each module into `values-<language>/`.

## License

GPL-3.0, as Metadator, which Metamatik is built on. See [LICENSE](LICENSE).
