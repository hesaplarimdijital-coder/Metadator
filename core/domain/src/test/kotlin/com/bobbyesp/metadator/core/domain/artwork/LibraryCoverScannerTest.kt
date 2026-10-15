/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.artwork

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.tags.api.AudioProperties
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagReadResult
import com.bobbyesp.metadator.tags.api.TagReader
import com.bobbyesp.metadator.tags.api.TagSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCoverScannerTest {
    private val withCover = track(1)
    private val without = track(2)
    private val unsupported = track(3)

    /** Files by whether they hold a picture: null for a format TagLib reads no tags of. */
    private class CoverFiles(private val files: Map<ContentRef, Boolean?>) : TagReader {
        val reads = mutableListOf<ContentRef>()

        override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult {
            reads += ref
            if (ref !in files) return TagReadResult.NotFound
            val hasCover = files[ref] ?: return TagReadResult.Unsupported
            val pictures =
                if (hasCover) listOf(EmbeddedPicture(byteArrayOf(1, 2, 3), "image/jpeg"))
                else emptyList()
            return TagReadResult.Success(TagSnapshot(TagMap.Empty, pictures))
        }

        override suspend fun readAudioProperties(ref: ContentRef): AudioProperties? = null
    }

    private val files =
        CoverFiles(mapOf(withCover.ref to true, without.ref to false, unsupported.ref to null))

    /** A scanner working in the test's background scope, as it works in the app's. */
    private fun TestScope.scanner(
        store: CoverCheckStore,
        reader: TagReader = files,
        busy: Flow<Boolean> = flowOf(false),
    ): LibraryCoverScanner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LibraryCoverScanner(
            reader = reader,
            store = store,
            dispatchers = AppDispatchers(dispatcher, dispatcher),
            scope = backgroundScope,
            busy = busy,
        )
    }

    @Test
    fun `every song is checked, and those without a picture are listed`() = runTest {
        val store = MemoryCoverCheckStore()
        val scanner = scanner(store)

        scanner.submit(listOf(withCover, without, unsupported))
        runCurrent()

        val state = scanner.state.value
        assertEquals(setOf(without.id), state.withoutCover)
        assertTrue(state.lacksCover(without))
        assertFalse(state.lacksCover(unsupported))
        assertEquals(3, state.checked)
        assertEquals(3, state.total)
        assertFalse(state.isRunning)
        assertEquals(
            mapOf(withCover.id to true, without.id to false, unsupported.id to null),
            store.checks.mapValues { it.value.hasCover },
        )
    }

    @Test
    fun `a song checked before is not read again`() = runTest {
        val kept =
            CoverCheck(without.id, without.dateModifiedEpochSeconds, without.sizeBytes, false)
        val reader = CoverFiles(emptyMap())
        val scanner = scanner(MemoryCoverCheckStore(listOf(kept)), reader)

        scanner.submit(listOf(without))
        runCurrent()

        assertEquals(setOf(without.id), scanner.state.value.withoutCover)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `a song saved with a cover is read again, before songs never read`() = runTest {
        val before = CoverCheck(withCover.id, withCover.dateModifiedEpochSeconds - 60, 900, false)
        val scanner = scanner(MemoryCoverCheckStore(listOf(before)))

        scanner.submit(listOf(without, withCover))
        runCurrent()

        assertEquals(listOf(withCover.ref, without.ref), files.reads)
        assertEquals(setOf(without.id), scanner.state.value.withoutCover)
    }

    @Test
    fun `a file that could not be opened is tried again next time, an unsupported one is not`() =
        runTest {
            val store = MemoryCoverCheckStore()
            val gone = track(4)
            val scanner = scanner(store)

            scanner.submit(listOf(unsupported, gone))
            runCurrent()

            assertEquals(2, scanner.state.value.checked)
            assertEquals(setOf(unsupported.id), store.checks.keys)
        }

    @Test
    fun `a song gone from the library is forgotten`() = runTest {
        val store = MemoryCoverCheckStore()
        val scanner = scanner(store)

        scanner.submit(listOf(withCover, without))
        runCurrent()
        scanner.submit(listOf(withCover))
        runCurrent()

        assertEquals(setOf(withCover.id), store.checks.keys)
        assertEquals(emptySet<TrackId>(), scanner.state.value.withoutCover)
        assertEquals(1, scanner.state.value.total)
    }

    @Test
    fun `nothing is read while more urgent work is`() = runTest {
        val busy = MutableStateFlow(true)
        val scanner = scanner(MemoryCoverCheckStore(), busy = busy)

        scanner.submit(listOf(withCover, without))
        runCurrent()
        assertTrue(files.reads.isEmpty())
        assertTrue(scanner.state.value.isRunning)

        busy.value = false
        runCurrent()
        assertEquals(2, files.reads.size)
        assertEquals(setOf(without.id), scanner.state.value.withoutCover)
    }

    @Test
    fun `a large library is checked and kept whole`() = runTest {
        val songs = (1L..60L).map(::track)
        val store = MemoryCoverCheckStore()
        val reader = CoverFiles(songs.associate { it.ref to (it.id.value % 2 == 0L) })
        val scanner = scanner(store, reader)

        scanner.submit(songs)
        runCurrent()

        assertEquals(60, store.checks.size)
        assertEquals(30, scanner.state.value.withoutCover.size)
        assertFalse(scanner.state.value.isRunning)
    }

    private fun track(id: Long) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://media/external/audio/media/$id"),
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            albumId = 1,
            durationMs = 1_000,
            trackNumber = id.toInt(),
            discNumber = null,
            year = 2000,
            genre = null,
            mimeType = "audio/mpeg",
            sizeBytes = 1_000 + id,
            displayName = "$id.mp3",
            folder = "Music/",
            dateAddedEpochSeconds = 1_700_000_000,
            dateModifiedEpochSeconds = 1_700_000_000 + id,
            artworkRef = null,
        )
}
