/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryQualityScannerTest {
    private val flac = track(1, "real.flac")
    private val fake = track(2, "fake.wav")
    private val mp3 = track(3, "song.mp3")

    private val files =
        mapOf(
            flac.ref to fixture("music-16bit-stereo.flac"),
            fake.ref to wav(noise(44_100, 16, shape = cutAt(16_000.0)), sampleRate = 44_100),
            mp3.ref to ByteArray(8_192) { (it * 7).toByte() },
        )

    private class MemoryStore(initial: List<StoredVerdict> = emptyList()) : QualityStore {
        val saved = initial.toMutableList()

        override suspend fun load(): List<StoredVerdict> = saved.toList()

        override suspend fun save(verdict: StoredVerdict) {
            saved.removeAll { it.uri == verdict.uri }
            saved += verdict
        }
    }

    /**
     * A scanner working in the test's background scope, as it works in the app's. advanceUntilIdle
     * leaves the background alone once nothing else is left, so the tests run it with runCurrent:
     * none of the work waits on time.
     */
    private fun TestScope.scanner(
        store: QualityStore,
        opened: Map<ContentRef, ByteArray> = files,
    ): LibraryQualityScanner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(dispatcher, dispatcher)
        return LibraryQualityScanner(
            check = CheckLosslessUseCase(FakeFileOpener(opened), dispatchers),
            store = store,
            dispatchers = dispatchers,
            scope = backgroundScope,
        )
    }

    @Test
    fun `every lossless song is checked and remembered, and a lossy one is not`() = runTest {
        val store = MemoryStore()
        val scanner = scanner(store)

        scanner.submit(listOf(flac, fake, mp3))
        runCurrent()

        val state = scanner.state.value
        assertTrue(state.of(flac) is TrackQuality.Checked)
        assertEquals(TrackQuality.Checked(LosslessVerdict.Transcoded), state.of(fake))
        assertTrue(state.of(fake).isFake)
        assertEquals(TrackQuality.NotApplicable, state.of(mp3))
        assertEquals(2, state.checked)
        assertEquals(2, state.total)
        assertFalse(state.isRunning)
        assertEquals(setOf(flac.ref.uri, fake.ref.uri), store.saved.map { it.uri }.toSet())
    }

    @Test
    fun `a song checked before is not checked again`() = runTest {
        val kept = StoredVerdict(flac.ref.uri, flac.sizeBytes, flac.dateModifiedEpochSeconds, null)
        // Nothing can be opened: a song that were checked again would come out unreadable.
        val scanner =
            scanner(
                MemoryStore(listOf(kept.copy(verdict = LosslessVerdict.Lossless))),
                opened = emptyMap(),
            )

        scanner.submit(listOf(flac))
        runCurrent()

        assertEquals(TrackQuality.Checked(LosslessVerdict.Lossless), scanner.state.value.of(flac))
    }

    @Test
    fun `a song whose file changed since is checked again`() = runTest {
        val before =
            StoredVerdict(
                uri = fake.ref.uri,
                sizeBytes = fake.sizeBytes,
                modifiedEpochSeconds = fake.dateModifiedEpochSeconds - 60,
                verdict = LosslessVerdict.Lossless,
            )
        val scanner = scanner(MemoryStore(listOf(before)))

        scanner.submit(listOf(fake))
        runCurrent()

        assertEquals(TrackQuality.Checked(LosslessVerdict.Transcoded), scanner.state.value.of(fake))
    }

    @Test
    fun `a song added to the library later is checked too`() = runTest {
        val scanner = scanner(MemoryStore())

        scanner.submit(listOf(flac))
        runCurrent()
        scanner.submit(listOf(flac, fake))
        runCurrent()

        assertEquals(TrackQuality.Checked(LosslessVerdict.Transcoded), scanner.state.value.of(fake))
        assertEquals(2, scanner.state.value.checked)
    }

    @Test
    fun `a verdict made elsewhere is kept, and the song is not checked again`() = runTest {
        val store = MemoryStore()
        // Nothing can be opened: a song checked here would come out unreadable.
        val scanner = scanner(store, opened = emptyMap())

        scanner.record(flac, LosslessVerdict.Lossless)
        scanner.submit(listOf(flac))
        runCurrent()

        assertEquals(TrackQuality.Checked(LosslessVerdict.Lossless), scanner.state.value.of(flac))
        assertEquals(listOf(flac.ref.uri), store.saved.map { it.uri })
    }

    private fun track(id: Long, name: String) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://media/external/audio/media/$id"),
            title = name.substringBefore('.'),
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            albumId = 1,
            durationMs = 1_000,
            trackNumber = id.toInt(),
            discNumber = null,
            year = 2000,
            genre = null,
            mimeType = null,
            sizeBytes = 1_000 + id,
            displayName = name,
            folder = "Music/",
            dateAddedEpochSeconds = 1_700_000_000,
            dateModifiedEpochSeconds = 1_700_000_000 + id,
            artworkRef = null,
        )
}
