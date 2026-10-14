/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.core.domain.FakeBackupStore
import com.bobbyesp.metadator.core.domain.FakeIndexer
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.editor.withMultiValueMode
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.MultiValueMode
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
        val runner =
            BatchRunner(this, SaveTagChangesUseCase(files, files, FakeBackupStore(), FakeIndexer()))

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

    /** The path the batch editor's "Apply to all" takes, with the multi-value mode applied last. */
    private suspend fun TestScope.applyToAll(
        files: FakeTagFiles,
        edit: BatchEdit,
        mode: MultiValueMode = MultiValueMode.Separate,
    ): BatchProgress {
        val runner =
            BatchRunner(this, SaveTagChangesUseCase(files, files, FakeBackupStore(), FakeIndexer()))
        runner.start(files.files.keys.map { BatchItem(it, it.uri.substringAfterLast('/')) }, edit) {
            it.withMultiValueMode(mode, "; ")
        }
        advanceUntilIdle()
        // Whatever runs this scope's coroutines, the batch is done before its files are read.
        while (runner.isRunning) yield()
        return runner.progress.value!!
    }

    private fun albumArtistFiles() =
        FakeTagFiles(
            linkedMapOf(
                ContentRef("content://1/a.flac") to
                    TagSnapshot(TagMap.of("ALBUMARTIST" to listOf("Old")), emptyList()),
                ContentRef("content://2/b.mp3") to
                    TagSnapshot(TagMap.of("ARTIST" to listOf("Daft Punk")), emptyList()),
                ContentRef("content://3/c.m4a") to
                    TagSnapshot(TagMap.of("ALBUMARTIST" to listOf("A", "B")), emptyList()),
                ContentRef("content://4/d.flac") to
                    TagSnapshot(TagMap.of("ALBUMARTIST" to listOf("Daft Punk")), emptyList()),
            )
        )

    @Test
    fun `an album artist set for all reaches every file, whatever each had`() = runTest {
        val files = albumArtistFiles()

        val progress =
            applyToAll(
                files,
                BatchEdit(fields = mapOf("ALBUMARTIST" to listOf("Daft Punk", "Pharrell"))),
            )

        assertEquals(4, progress.saved.size)
        files.files.values.forEach { snapshot ->
            assertEquals(listOf("Daft Punk", "Pharrell"), snapshot.tags["ALBUMARTIST"])
        }
        assertEquals(listOf("Daft Punk"), files.files.values.elementAt(1).tags["ARTIST"])
    }

    @Test
    fun `an album artist set for all is joined in every file when values are joined`() = runTest {
        val files = albumArtistFiles()

        applyToAll(
            files,
            BatchEdit(fields = mapOf("ALBUMARTIST" to listOf("Daft Punk", "Pharrell"))),
            MultiValueMode.Joined,
        )

        files.files.values.forEach { snapshot ->
            assertEquals(listOf("Daft Punk; Pharrell"), snapshot.tags["ALBUMARTIST"])
        }
    }

    @Test
    fun `a field a format drops is told, not counted as kept`() = runTest {
        val files =
            FakeTagFiles(
                mapOf(ContentRef("content://1/a.wav") to TagSnapshot(TagMap.Empty, emptyList())),
                unsupportedKeys = setOf("ALBUMARTIST"),
            )

        val progress =
            applyToAll(
                files,
                BatchEdit(
                    fields = mapOf("ALBUM" to listOf("X"), "ALBUMARTIST" to listOf("Daft Punk"))
                ),
            )

        assertEquals(1, progress.saved.size)
        assertEquals(mapOf("ALBUMARTIST" to 1), progress.notStored)
    }

    @Test
    fun `positions are set file by file, in each file's style, the rest kept`() = runTest {
        val mp3 = ContentRef("content://1/a.mp3")
        val flac = ContentRef("content://2/b.flac")
        val other = ContentRef("content://3/c.mp3")
        val files =
            FakeTagFiles(
                linkedMapOf(
                    mp3 to
                        TagSnapshot(
                            TagMap.of(
                                "TRACKNUMBER" to listOf("5/10"),
                                "DISCNUMBER" to listOf("1/2"),
                            ),
                            emptyList(),
                        ),
                    flac to
                        TagSnapshot(
                            TagMap.of("TRACKNUMBER" to listOf("5"), "TRACKTOTAL" to listOf("10")),
                            emptyList(),
                        ),
                    other to TagSnapshot(TagMap.of("TRACKNUMBER" to listOf("7")), emptyList()),
                )
            )

        applyToAll(
            files,
            BatchEdit(
                positions =
                    mapOf(
                        mp3 to FilePositions(track = PositionEdit(3, 12), disc = PositionEdit(2)),
                        flac to FilePositions(track = PositionEdit(total = 12)),
                    )
            ),
        )

        val first = files.files.getValue(mp3).tags
        assertEquals(listOf("3/12"), first["TRACKNUMBER"])
        assertEquals(listOf("2/2"), first["DISCNUMBER"])
        val second = files.files.getValue(flac).tags
        assertEquals(listOf("5"), second["TRACKNUMBER"])
        assertEquals(listOf("12"), second["TRACKTOTAL"])
        assertEquals(listOf("7"), files.files.getValue(other).tags["TRACKNUMBER"])
    }

    @Test
    fun `a track total keeps the number read from the file name`() {
        val ref = ContentRef("content://1")
        val edit =
            BatchEdit(
                fileNamePattern = FileNamePattern("{track} - {title}"),
                positions = mapOf(ref to FilePositions(track = PositionEdit(total = 12))),
            )

        val changes =
            edit.changesFor(
                BatchItem(ref, "03 - Digital Love.mp3"),
                0,
                1,
                TagSnapshot(TagMap.of("TRACKNUMBER" to listOf("9/9")), emptyList()),
            )

        assertEquals(listOf("03/12"), changes.fields["TRACKNUMBER"])
        assertEquals(listOf("Digital Love"), changes.fields["TITLE"])
    }

    @Test
    fun `positions that set nothing are no edit`() {
        assertTrue(
            BatchEdit(positions = mapOf(ContentRef("content://1") to FilePositions())).isEmpty
        )
        assertFalse(
            BatchEdit(
                    positions =
                        mapOf(ContentRef("content://1") to FilePositions(disc = PositionEdit(1)))
                )
                .isEmpty
        )
    }
}
