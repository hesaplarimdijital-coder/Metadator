# Saving tags

Saving is where 1.x lost users' data, so this path has the strictest rules and the most tests
(`core/domain/src/test/.../save/SaveTagChangesUseCaseTest.kt`).

## What 1.x got wrong

- It wrote a property map built from 15 known fields. TagLib's `setProperties` removes every key
  missing from the map, so every save deleted ReplayGain, MusicBrainz ids, BPM, ISRC and any custom
  tag.
- Every save re-embedded a cover, from MediaStore's (often downscaled) album-art cache, replacing
  every picture in the file.
- Multi-value fields were joined with ", " and split on ",", so "Tyler, The Creator" became two
  artists.

## How 2.0 saves

The editor keeps a `TagDraft`: the snapshot it read and the user's edits. What gets saved is the
difference, `TagChanges`: changed keys (an empty list removes a key) and an `ArtworkChange`
(`Unchanged`, `Replace`, `Remove`).

`SaveTagChangesUseCase`:
1. Reads the file as it is now, so changes made elsewhere since the editor opened survive.
2. Applies the changed keys to that map. Untouched keys keep their exact values. If the file
   already holds the result, nothing is written (`SaveOutcome.NothingToSave`): the editor reads
   the file again, so the change does not stay on screen, and says there was nothing to save.
3. Pictures: not written at all when the cover didn't change. Replacing or removing the cover
   touches only the front cover; back covers, artist pictures and the rest stay. Choosing the
   picture the file already has (the cover finder puts it first when a source has it too) is no
   change in the draft at all.
4. Backs up the file's tags and pictures (`TagBackupStore`, Room plus picture files) and drops
   the oldest backups beyond the newest 100 (`BackupPolicy`). A batch (`BatchRunner`) drops them
   only once, when it ends, cancelled or not, and keeps all of its own even past 100, so "Undo
   all" can restore every one of its songs. From the batch's start until its result is dismissed
   (or another batch editor opens), a save made beside it, in the editor or in another app's
   window, keeps them too: every save and the batch share one `BackupHold`, which counts the
   backups begun since, and a prune keeps at least that many of the newest. The first save after
   that prunes back to 100.
5. Writes. If the write fails, it writes the backup back; if that fails too, the backup is kept.
6. Reads again and reports changed keys the format did not store (`SaveOutcome.Saved.notStored`).
7. Asks MediaStore to rescan the file, so the library and other players see the new tags.

Undo (`RestoreBackupUseCase`) writes a backup back and rescans.

After a save the editor reads the file again and shows what it now holds. Its fields stay editable
while a save writes: what was edited meanwhile is made again on what was read
(`TagDraft.changesSince`, `TagDraft.applying`) and kept for process death like any other edit, so a
slow save does not take it with it.

Multi-value fields are lists from the file to the chips in the editor and back. The only place
values are joined is the user's choice in Settings ("Join them into one value"), applied to the
fields they changed, just before writing. What is typed into a field of chips becomes a value on
Enter, on the separator, or when the field lets go of the focus; so Save lets go of the focus
before it saves, or it would write the field without the value being typed, or being edited.

Track and disc totals follow the format: `3/12` in one field for ID3 and MP4, a separate
`TRACKTOTAL` for Vorbis comments (FLAC, Ogg, Opus), or whatever key the file already uses
(`TrackPositions`).

## Write access

Android asks differently on each version (`MediaStoreWriteAccess`):
- up to 9: the storage permission, once;
- 10: a prompt per file, which MediaStore only offers after refusing a write
  (`RecoverableAccessCache` keeps it until shown);
- 11 and later: `MediaStore.createWriteRequest`, one prompt for every file of a batch, before
  anything is written.

A file opened from another app with write access needs nothing; one shared read-only cannot be
written, and the editor says so.

A song on an SD card is asked for the same way from Android 10 on: MediaStore manages the card as
it manages the device. Up to 9 the storage permission does not reach a removable card, and a save
there fails with the error the system gives; only the system's folder picker could grant it, and
the app does not ask for that.

## Process death

The editor's text changes are kept in the `SavedStateHandle`, as the map of lists they are, and
reapplied on a freshly read file. Nothing is encoded as a key is typed: the system writes the map
out when it saves the state. A new cover is not kept: its bytes are too large for saved state.
