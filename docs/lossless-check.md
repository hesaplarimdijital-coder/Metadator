# The lossless check

A FLAC made from an MP3 is as large as a real one, plays as one, and sounds like the MP3. Nothing
in its tags says so. When a FLAC or a WAV file is opened, the editor looks at its audio and says
what it finds; the library looks at all of them, in the background (see
[the whole library](#the-whole-library)).

## What it says

| Verdict | What was found |
|---|---|
| Looks like real lossless | Sound up to the top of the band the file can hold, or fading into noise before it. |
| Made from a lossy file | A wall: sound right up to a frequency, digital silence right above it, in nearly every moment of the song. Where the wall stands says what bitrate the lossy file most likely had. |
| Probably made from a lossy file | A wall between 19.9 and 20.75 kHz, where an MP3 at 320 kbps and Opus end, and a few real masters too; a shallow wall lower down, or one under 12 kHz, where music may simply have little treble; or the band above 16 kHz coming and going, as an MP3 encoder leaves it. |
| Lossless, but upscaled | No sign of lossy compression, but less in the file than it claims: a high sample rate with nothing above what a CD holds, or 24 bits of which only 16 are used. |
| Can't tell | Too little sound, a sample rate under 32 kHz, or a wall between where encoders cut and where recordings end. |

Under the verdict are the figures it rests on and the spectrum they were read from, with the wall
marked.

**It cannot prove a file genuine.** A lossy encoder at a high enough bitrate keeps the whole band
(AAC at 256 kbps, MP3 at V0, Vorbis at q6 and up), and leaves nothing for a spectrum to show.
"Looks like real lossless" means no sign was found, and the card says so. It can also be wrong the
other way: a master that was itself cut at 20 kHz, or a recording digitized from an FM broadcast,
has a wall no encoder made. Hence "probably" where real masters are known to end.

## How

Everything is plain Kotlin in `core/domain/.../quality`, so it is tested on the JVM and needs no
codec of the device. Nothing is written and nothing leaves the device.

1. **Reading the audio** (`PcmSource`). `FlacSource` reads the stream info, then decodes frames
   wherever it is asked to: it looks for a frame's sync code in a chunk read from the middle of the
   file and keeps a frame only when both of its checksums hold, so no seek table is needed and a
   damaged frame is skipped. `WavSource` reads PCM of 8 to 32 bits and 32-bit floating point.
   `OpenedAudioFile.read` gives them bytes at a position; on Android that is `pread` on the file's
   descriptor (`ParcelAudioFile`).
2. **Taking the spectrum** (`SpectralFrames`). Up to twelve stretches of about a second and a half,
   spread along the song, each cut into overlapping windows. Each window becomes the power in bands
   of 16 FFT bins (about 170 Hz at 44.1 kHz). Windows with nothing but silence are left out.
3. **Looking for the wall** (`LosslessAnalyzer`). For each band from the top down: the usual level
   just below it against the loudest level just above it. A wall is a drop of at least 12 dB onto
   what is within 12 dB of the rounding noise of the file's own samples (digital silence, with room
   for dither), or a drop of 40 dB onto anything. The lowest wall is the cut; a wall in the last
   300 Hz under the top of the band is the converter's own filter and is ignored.
4. **Deciding.** Under 19.9 kHz every usual MP3, AAC and Vorbis setting cuts, and no recording
   does: transcoded. The bitrate is read from where the cut is.

The rounding noise is computed from the bits the samples really use, so a lossy file padded to 24
bits is still seen for what it is.

## The whole library

The editor checks the song it opens. The library checks every other FLAC and WAV file, in the
background, so that it can say which of them are fake without each being opened
(`LibraryQualityScanner`, one for the app, in the app's scope: the check goes on while its screens
come and go).

- **One file at a time, in the library's order.** It works through the library it was last given,
  and starts over from the new one when the library changes: a song added is checked too, one
  removed is not. It yields between two files, the check being heavy.
- **Each file once.** A verdict is kept with what identified the file when it was made, its size
  and when it was last changed (`StoredVerdict`, in `quality/verdicts.tsv` among the app's files,
  `FileQualityStore`), so a file is checked again only once it has changed. A library of a few
  thousand FLAC files takes minutes the first time, and nothing after that.
- **A file it cannot decode** is kept as unreadable, and not tried again until it changes. One file
  the decoder chokes on does not stop the others.
- **The editor's verdict counts too** (`record`): a song checked when it was opened is not checked
  again by the library, and the two say the same.

What it found is in the library: the Quality column of the table of songs (`QualityBadge`: real
lossless, fake, probably fake, upscaled, unclear; a dash for a lossy format, where there is nothing
to find), and the Fake FLAC list in the sidebar, of the songs that have surely or most likely been
through a lossy encoder (`TrackQuality.isFake`), with how far the check has got while it runs.
Where there is no sidebar (a phone, a narrow window), the list is a quick filter, first among them
with Needs attention: they are what is looked for there, and a format only narrows the songs down.

## How it was tuned, and how far that goes

The thresholds come from 182 files, all of them short (6 to 18 seconds):

- 22 genuine ones: 17 of the IETF test files below, short pieces of music in the forms a decoder
  must handle (CD and high resolution, and awkward ones: 8 and 12 bits, mono, 22 and 384 kHz), and
  5 synthetic signals made to be hard (bright, dull, quiet, an old recording's hiss, a master cut
  at 20 kHz).
- 157 made from eight of them with LAME (CBR 96 to 320, V0, V2, V5), FFmpeg's AAC (96 to 256),
  Vorbis (q2 to q8) and Opus (96 and 160), decoded back to FLAC; a few of those dithered, or
  resampled to 96 kHz.
- 3 genuine ones upscaled: resampled to 96 and 192 kHz, and padded to 24 bits.

| | Lossless | Upscaled | Can't tell | Probably | Transcoded |
|---|---|---|---|---|---|
| 22 genuine | 18 | 1 | 1 | 2 | 0 |
| 157 from lossy | 25 | 0 | 3 | 34 | 95 |
| 3 upscaled | 0 | 3 | 0 | 0 | 0 |

The 25 lossy files passed as lossless are full-band encodes: AAC at 256 (and one at 192), MP3 at
V0, Vorbis at q6 and q8, and one MP3 at 320 decoded with noise-shaped dither. That is the limit
described above. The two genuine files under "probably" are the synthetic master cut at 20 kHz and
a mono test file that ends at 11.5 kHz by itself; the genuine "upscaled" one is the 384 kHz file,
which holds nothing above 96 kHz. For LAME at a constant bitrate, the bitrate read from the cut is
the one used in all but two of 48 files.

**This is a small set, and no substitute for a music collection.** It was not possible to try the
check on full commercial albums while writing it. Expect it to be right about the common case, an
MP3 or AAC of 128 to 256 kbps saved as FLAC, and treat "probably" and "can't tell" as what they
say.

The decoder is checked bit for bit, by the MD5 in each file's header, against the 64 files of the
[IETF FLAC decoder testbench](https://github.com/ietf-wg-cellar/flac-test-files) that every decoder
must read. Three small ones are in `core/domain/src/test/resources/quality` (public domain), for
the unit tests.

## Changing it

- The constants of `LosslessAnalyzer` say what each one is for. Change one with the corpus at hand,
  not by reasoning: the first version looked for the steepest drop and took a guitar solo for a
  codec.
- Other lossless formats (ALAC, APE, WavPack) need a decoder of their own behind `PcmSource`. The
  check says nothing for them rather than guess.
