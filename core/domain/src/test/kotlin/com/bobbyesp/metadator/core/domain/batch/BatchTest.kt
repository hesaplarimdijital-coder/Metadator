/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.core.domain.FakeBackupStore
import com.bobbyesp.metadator.core.domain.FakeIndexer
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.save.RestoreBackupUseCase
import com.bobbyesp.metadator.core.domain.save.RestoreOutcome
import com.bobbyesp.metadator.core.domain.save.SaveOutcome
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.tags.api.TagChanges
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchTest {

    @Test
    fun `patterns read tags out of file names`() {
        val pattern = FileNamePattern("{track} - {artist} - {title}")
        assertEquals(
            mapOf(
                "TRACKNUMBER" to listOf("03"),
                "ARTIST" to listOf("AC/DC"),
                "TITLE" to listOf("Back In Black - Live"),
            ),
            pattern.parse("03 - AC/DC - Back In Black - Live.flac"),
        )
        assertNull(pattern.parse("no separators here.mp3"))
    }

    @Test
    fun `patterns format file names from tags`() {
        val tags =
            TagMap.of(
                "ARTIST" to listOf("AC/DC"),
                "TITLE" to listOf("Hells Bells"),
                "TRACKNUMBER" to listOf("1/10"),
            )
        assertEquals(
            "1 - AC_DC - Hells Bells",
            FileNamePattern("{track} - {artist} - {title}").format(tags),
        )
        assertNull(FileNamePattern("{album} - {title}").format(tags))
    }

    @Test
    fun `invalid patterns are recognised`() {
        assertFalse(FileNamePattern("no placeholders").isValid)
        assertFalse(FileNamePattern("{title} {title}").isValid)
        assertFalse(FileNamePattern("{nope}").isValid)
    }

    @Test
    fun `a batch sets common fields, numbers tracks and keeps the rest`() = runTest {
        val refs = (1..3).map { ContentRef("content://$it") }
        val files =
            FakeTagFiles(
                refs.associateWith {
                    TagSnapshot(
                        TagMap.of("TITLE" to listOf(it.uri), "BPM" to listOf("120")),
                        emptyList(),
                    )
                }
            )
        val backups = FakeBackupStore()
        val runner =
            BatchRunner(
                this,
                SaveTagChangesUseCase(files, files, backups, FakeIndexer()),
                backups,
            )

        runner.start(
            refs.mapIndexed { index, ref -> BatchItem(ref, "song$index.mp3") },
            BatchEdit(fields = mapOf("ALBUM" to listOf("Live")), numbering = TrackNumbering()),
        )
        advanceUntilIdle()

        val progress = runner.progress.value!!
        assertFalse(progress.isRunning)
        assertEquals(3, progress.saved.size)
        refs.forEachIndexed { index, ref ->
            val tags = files.files.getValue(ref).tags
            assertEquals(listOf("Live"), tags["ALBUM"])
            assertEquals(listOf("${index + 1}/3"), tags["TRACKNUMBER"])
            assertEquals(listOf("120"), tags["BPM"])
            assertEquals(listOf(ref.uri), tags["TITLE"])
        }
    }

    /** Three songs, and a runner and a save that keep only [keep] backups. */
    private class Songs(scope: CoroutineScope, keep: Int) {
        val refs = (1..3).map { ContentRef("content://$it") }
        val originals = refs.associateWith {
            TagSnapshot(TagMap.of("TITLE" to listOf(it.uri)), emptyList())
        }
        val files = FakeTagFiles(originals)
        val backups = FakeBackupStore()
        val indexer = FakeIndexer()
        val save = SaveTagChangesUseCase(files, files, backups, indexer, keep = keep)
        val runner = BatchRunner(scope, save, backups, keep = keep)
        val items = refs.mapIndexed { index, ref -> BatchItem(ref, "song$index.mp3") }
        val edit = BatchEdit(fields = mapOf("ALBUM" to listOf("Live")))
    }

    @Test
    fun `a batch keeps the backup of every song it saved, however few are kept`() = runTest {
        // Pruned after each song, a batch of three keeping one backup could undo only its last.
        val songs = Songs(this, keep = 1)

        songs.runner.start(songs.items, songs.edit)
        advanceUntilIdle()

        val progress = songs.runner.progress.value!!
        assertEquals(1, songs.backups.prunes.size)
        assertEquals(3, progress.backups.size)
        val restore = RestoreBackupUseCase(songs.backups, songs.files, songs.indexer)
        progress.backups.forEach { assertEquals(RestoreOutcome.Restored, restore(it)) }
        assertEquals(songs.originals, songs.files.files)
    }

    @Test
    fun `the next save drops a batch's backups beyond those kept`() = runTest {
        val songs = Songs(this, keep = 1)
        songs.runner.start(songs.items, songs.edit)
        advanceUntilIdle()

        val outcome =
            songs.save(songs.refs.first(), TagChanges(fields = mapOf("TITLE" to listOf("x"))))
                as SaveOutcome.Saved

        assertEquals(setOf(outcome.backupId), songs.backups.backups.keys)
    }

    @Test
    fun `a cancelled batch prunes too, and keeps what it backed up`() = runTest {
        val songs = Songs(this, keep = 1)
        var seen = 0

        // Cancelled while the third song is being saved, before its backup: Room's save throws.
        songs.runner.start(songs.items, songs.edit) { changes ->
            if (++seen == 3) songs.runner.cancel()
            changes
        }
        advanceUntilIdle()

        val progress = songs.runner.progress.value!!
        assertTrue(progress.wasCancelled)
        assertFalse(songs.runner.isRunning)
        assertEquals(2, progress.saved.size)
        assertEquals(1, songs.backups.prunes.size)
        assertEquals(progress.backups.toSet(), songs.backups.backups.keys)
    }
}
