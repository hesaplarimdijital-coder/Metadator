# The cover finder

"Find a cover online" looks for a cover in every enabled source, compares what it finds, and puts
the cover to use first, with what speaks for it and against it. It is in the editor, for one song,
and in the batch editor, for the songs of an album at once.

It is the "smart" part of the app, and it is worth being exact about what that means: **a set of
rules and a picture comparison that run on the device**, not a language model and not a service.
Every reason shown in the sheet is one of the things the score was made of.

## Where covers come from

- **A search** in every enabled source (`LookupService`): MusicBrainz, whose covers are the Cover
  Art Archive's; Deezer; and the iTunes Store. The same search the metadata lookup makes, by the
  song's title, artist and album.
- **The file's own ids.** A file tagged from MusicBrainz carries the id of its release
  (`MUSICBRAINZ_ALBUMID`). A source can name the cover of that very release without searching
  (`MetadataProvider.artworkFor`), and that cover is taken as the right one.

One cover comes back with every song of its album that matched, so covers are first reduced to one
entry each, spoken for by the song that matched best. Only the twelve best are looked at any
closer: each costs two downloads.

## What is looked at

For each of those covers (`FindCoversUseCase`):

- **A small copy of it** (250 px) is downloaded and decoded (`ImageSampler`, Android's decoders
  behind a port) into an `ImageSample`: a 32 by 32 grayscale copy.
- **The first 64 KB of the picture itself**, which is where a JPEG, PNG or WebP says how large it
  is (`imageDimensions`). The size a source claims is not trusted: a source asked for 1500 px gives
  what it has.

A cover of which neither could be had does not exist (a source names covers it does not have) and
is left out. So is one that is a single flat color: what a source shows in place of a missing
cover.

From the sample comes a **fingerprint** (`ImageSample.fingerprint`): a perceptual hash, 64 bits
that say which of the picture's coarsest frequencies are above their median. Resizing, compressing
again or brightening a picture changes a few of them; another picture changes about half. Covers
whose fingerprints are within 10 bits are copies of one picture.

While it works, the sheet says how far it has got (`CoverSearchProgress`: asking the sources, then
how many of the covers named have been looked at): a wait of some seconds is at least a moving one.

That is used twice:

- **Copies from several sources become one suggestion**, its largest copy, and count for it. Two
  catalogues do not agree on a wrong cover by chance.
- **The cover the file already has** is fingerprinted too. The same picture found online is very
  likely the right one, whatever the tags say, and a larger copy of it is what is usually wanted.

## The score

`CoverScoring` gives each cover a score from 0 to 1:

| Part | Weight | What it measures |
|---|---|---|
| The release | 0.70 | How well the source's album, artist and song agree with the tags. With an album in the tags it weighs most; without one, the song is all there is. Notes a catalogue adds to a name ("(Deluxe Edition)", " - Single") are not counted as differences. An ISRC equal to the file's makes the song a certain match. |
| The year | 0.08 | The same year; a year off counts for most of it, since reissues carry their own. |
| The size | 0.10 | The short edge of the picture, up to 1400 px. Past that a cover only gets heavier, and it is stored again in every song of its album. |
| Agreement | 0.07 | Two sources with the same picture, or three. |
| The shape | 0.05 | Square, or not: a picture that is not is often a scan of the sleeve. |

Then what overrides the sum:

- The cover of the release the file's id names: at least 0.98.
- The picture the file already has, where the release is not plainly another: at least 0.90.
- A compilation of several artists, for a song whose tags do not say it is from one: 0.10 less.
- A picture under 500 px: never above 0.84, so it is never called the best match.

From 0.85 a cover is the "best match", from 0.6 a "possible match", below that a "weak match": the
same steps as the metadata lookup (`MatchConfidence`). A measure that could not be taken counts
for half, neither for the cover nor against it.

## For one song, and for an album

The finder is asked with a `CoverRequest`: what is known of the song, and the cover it has now.
`CoverRequest.forSongs` makes it from tags, for one song or for several that are to share a cover:

- **The first song speaks for all of them.** The sources are searched by song, so its title,
  artist, album and year are what is asked. Any song of an album finds the album.
- **An id counts only when every song carries the same.** The release id they all have names their
  release; the id one of them has would make its cover the best match for all on the word of one.
- **The songs are taken as they will be**, with what the screen has not yet written: the editor's
  unsaved fields, and in the batch editor the fields set for every song and the tags read from the
  file names (`BatchState.editedTags`). An album renamed there is searched under its new name.
- **The cover they have now** is the one chosen on the screen, or else the one in the file: the
  first song's in the batch editor, which reads it for this, having listed the files without
  their pictures.

The sheet and its ViewModel are shared (`CoverFinderSheet`, `CoverFinderViewModel` in
`core/ui/.../cover`): a feature cannot depend on another, and both show the same thing. Each screen
opens the sheet with its request and is handed the cover chosen, as a URL. It downloads it and
keeps it as an unsaved change: nothing is written until Save in the editor, or "Apply to all" in
the batch editor, whose button in the sheet says how many songs the cover is for.

The rest of an album, its title, album artists, date, genres and where each song is on it, the
batch editor finds with "Find metadata", beside its fields, which searches for several of the songs
and not the first only ([architecture.md](architecture.md#editing-several-songs)). Choosing a
release there leaves the cover as it is: the cover is this finder's.

## What leaves the device

What a metadata search already sent, to the same sources: the song's title, artist and album; to
the iTunes Store also the two letters of the device's region, to search the right country's
catalogue; and, for a file that carries one, its MusicBrainz release id to the Cover Art Archive.
The pictures are compared on the device.

## Changing it

- The weights are constants of `CoverScoring`, and `CoverScoringTest` says what each is meant to
  achieve ("the cover of another album of the song is not a confident choice"). A new signal is a
  field of `CoverEvidence`, a part of the score, and a `CoverReason` so that the sheet can say it.
- A new source of covers is a `MetadataProvider`, bound in `AppModules`. The finder needs nothing
  else: it asks `LookupService`.
- Covers are asked for at up to 1500 px (iTunes) and 1200 px (Cover Art Archive); Deezer has 1000.
  Larger ones exist, and cost several megabytes in every file of an album.
