/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.domain.FakeBackupStore
import com.bobbyesp.metadator.core.domain.FakeIndexer
import com.bobbyesp.metadator.core.domain.FakeLibrary
import com.bobbyesp.metadator.core.domain.FakeStrings
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.artwork.LibraryCoverScanner
import com.bobbyesp.metadator.core.domain.artwork.MemoryCoverCheckStore
import com.bobbyesp.metadator.core.domain.batch.BatchEdit
import com.bobbyesp.metadator.core.domain.batch.BatchItem
import com.bobbyesp.metadator.core.domain.batch.BatchRunner
import com.bobbyesp.metadator.core.domain.fakeTrack
import com.bobbyesp.metadator.core.domain.library.collectionKey
import com.bobbyesp.metadator.core.domain.quality.CheckLosslessUseCase
import com.bobbyesp.metadator.core.domain.quality.FakeFileOpener
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityScanner
import com.bobbyesp.metadator.core.domain.quality.MemoryQualityStore
import com.bobbyesp.metadator.core.domain.save.BackupHold
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController
import com.bobbyesp.metadator.player.api.RepeatMode
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /**
     * Songs 1 to 5, listed in that order: the first three on one album, the last two on another.
     */
    private val tracks =
        (1L..5L).map { id ->
            fakeTrack(id, artist = "Daft Punk", album = if (id <= 3) "Discovery" else "Homework")
        }
    private val files =
        FakeTagFiles(
            tracks.associate {
                it.ref to TagSnapshot(TagMap.of("TITLE" to listOf(it.title)), emptyList())
            }
        )
    private val backups = FakeBackupStore()

    /** The device's songs, as the library reads them: a test saves a song's tags here. */
    private val device = FakeLibrary(tracks)
    private lateinit var covers: LibraryCoverScanner
    private lateinit var batches: BatchRunner

    /** A library's ViewModel as the app makes one, for the library or for a collection's screen. */
    private fun TestScope.libraryViewModel(): LibraryViewModel {
        val dispatchers = AppDispatchers(main.dispatcher, main.dispatcher)
        val check = CheckLosslessUseCase(FakeFileOpener(emptyMap()), dispatchers)
        // The checks work in the background, as in the app's scope.
        val quality =
            LibraryQualityScanner(check, MemoryQualityStore(), dispatchers, backgroundScope)
        covers = LibraryCoverScanner(files, MemoryCoverCheckStore(), dispatchers, backgroundScope)
        val hold = BackupHold()
        val save = SaveTagChangesUseCase(files, files, backups, FakeIndexer(), hold)
        batches = BatchRunner(this, save, backups, hold)
        return LibraryViewModel(
            library = device,
            player = FakePlayer(),
            quality = quality,
            covers = covers,
            batches = batches,
            strings = FakeStrings(),
            dispatchers = dispatchers,
            savedState = SavedStateHandle(),
        )
    }

    /** The library's own, started as its screen starts it. */
    private fun TestScope.startedLibrary(): LibraryViewModel =
        libraryViewModel().also {
            it.onIntent(LibraryIntent.Start)
            advanceUntilIdle()
        }

    private fun ids(vararg ids: Long) = ids.mapTo(mutableSetOf()) { TrackId(it) }

    /** Song [id] as it is listed at first, as a row hands it over when it is clicked. */
    private fun song(id: Long) = tracks.single { it.id == TrackId(id) }

    /** Saves song [id] as [edit] makes it: the library has that version of it from then on. */
    private fun save(id: Long, edit: (Track) -> Track) {
        device.tracks.value = device.tracks.value.map { if (it.id == TrackId(id)) edit(it) else it }
    }

    private val LibraryViewModel.selection
        get() = state.value.selection

    @Test
    fun `a range is selected from the song selected last`() = runTest {
        val library = startedLibrary()

        library.onIntent(LibraryIntent.ToggleSelection(TrackId(2)))
        library.onIntent(LibraryIntent.SelectRangeTo(TrackId(4)))

        assertEquals(ids(2, 3, 4), library.selection)
    }

    @Test
    fun `a range starts from the song opened with a click`() = runTest {
        val library = startedLibrary()

        library.onIntent(LibraryIntent.Opened(song(5)))
        library.onIntent(LibraryIntent.SelectRangeTo(TrackId(3)))

        assertEquals(TrackId(5), library.state.value.clicked?.id)
        assertEquals(ids(3, 4, 5), library.selection)
    }

    @Test
    fun `a song clicked stays when its own save takes it off a smart list`() = runTest {
        val library = startedLibrary()
        library.onIntent(LibraryIntent.ShowSection(LibrarySection.NeedsAttention))
        advanceUntilIdle()
        library.onIntent(LibraryIntent.Opened(song(2)))

        // Its album artist filled in and saved: it needs no attention any more.
        save(2) { it.copy(albumArtist = "Daft Punk") }
        advanceUntilIdle()

        val state = library.state.value
        assertFalse(TrackId(2) in state.listing.listedIds)
        // Still the song clicked: its editor stays beside the list, with what the save said.
        assertEquals(TrackId(2), state.clicked?.id)
        // Another place that lists it, as the songs all do, keeps it.
        library.onIntent(LibraryIntent.ShowSection(LibrarySection.Browse(LibraryTab.Songs)))
        advanceUntilIdle()
        assertEquals(TrackId(2), library.state.value.clicked?.id)
    }

    @Test
    fun `a song clicked goes when a search leaves it out, and when the place does`() = runTest {
        val library = startedLibrary()
        library.onIntent(LibraryIntent.Opened(song(2)))

        library.onIntent(LibraryIntent.Search("Homework"))
        advanceUntilIdle()
        assertNull(library.state.value.clicked)

        // Saved out of a search, then the albums: the same songs, with no new listing.
        library.onIntent(LibraryIntent.Opened(song(4)))
        save(4) { it.copy(album = "Discovery") }
        advanceUntilIdle()
        assertEquals(TrackId(4), library.state.value.clicked?.id)
        library.onIntent(LibraryIntent.ShowSection(LibrarySection.Browse(LibraryTab.Albums)))
        assertNull(library.state.value.clicked)
    }

    @Test
    fun `a song clicked in a collection stays when its own save takes it out of it`() = runTest {
        val collection = libraryViewModel()
        collection.onIntent(
            LibraryIntent.ShowCollection(
                CollectionType.Album,
                tracks.first().collectionKey(CollectionType.Album)!!,
            )
        )
        advanceUntilIdle()
        collection.onIntent(LibraryIntent.Opened(song(2)))

        save(2) { it.copy(album = "Homework") }
        advanceUntilIdle()

        val state = collection.state.value
        assertEquals(listOf(TrackId(1), TrackId(3)), state.tracks.map { it.id })
        assertEquals(TrackId(2), state.clicked?.id)
    }

    @Test
    fun `a drag selects what it touches of what is listed, and a range goes on from it`() =
        runTest {
            val library = startedLibrary()

            library.onIntent(LibraryIntent.SelectIds(ids(1, 2), anchor = TrackId(1)))
            assertEquals(ids(1, 2), library.selection)
            // A song that is not listed is left out.
            library.onIntent(LibraryIntent.SelectIds(ids(2, 99), add = true))
            assertEquals(ids(1, 2), library.selection)
            library.onIntent(LibraryIntent.SelectRangeTo(TrackId(3)))
            assertEquals(ids(1, 2, 3), library.selection)
            // A drag without Ctrl takes the place of what was selected.
            library.onIntent(LibraryIntent.SelectIds(ids(4, 5)))
            assertEquals(ids(4, 5), library.selection)
        }

    @Test
    fun `a batch done clears the selection`() = runTest {
        val library = startedLibrary()
        library.onIntent(LibraryIntent.SelectAll)
        assertEquals(5, library.selection.size)

        batches.start(
            tracks.take(2).map { BatchItem(it.ref, it.displayName) },
            BatchEdit(fields = mapOf("ALBUM" to listOf("Alive 2007"))),
        )
        advanceUntilIdle()

        assertTrue(library.selection.isEmpty())
    }

    @Test
    fun `a place of the sidebar starts afresh, and Back goes back to the one before`() = runTest {
        val library = startedLibrary()
        library.onIntent(LibraryIntent.SelectTab(LibraryTab.Albums))
        library.onIntent(LibraryIntent.ToggleSelection(TrackId(1)))

        library.onIntent(LibraryIntent.ShowSection(LibrarySection.NeedsAttention))
        var state = library.state.value
        assertEquals(LibraryTab.Songs, state.tab)
        assertTrue(state.filter.needsAttention)
        assertTrue(state.selection.isEmpty())

        library.onIntent(LibraryIntent.BackToPlace)
        state = library.state.value
        assertEquals(LibrarySection.Browse(LibraryTab.Albums), state.section)
        assertFalse(state.filter.needsAttention)
        assertEquals(listOf(LibrarySection.Browse(LibraryTab.Songs)), state.history)
    }

    @Test
    fun `a collection's screen lists its songs alone, and hands nothing to the checks`() = runTest {
        val discovery = tracks.take(3)
        val collection = libraryViewModel()

        collection.onIntent(
            LibraryIntent.ShowCollection(
                CollectionType.Album,
                discovery.first().collectionKey(CollectionType.Album)!!,
            )
        )
        advanceUntilIdle()
        runCurrent()

        val state = collection.state.value
        assertEquals("Discovery", state.collection?.title)
        assertEquals(discovery.map { it.id }, state.tracks.map { it.id })
        assertEquals(5, state.totalTracks)
        collection.onIntent(LibraryIntent.SelectAll)
        assertEquals(ids(1, 2, 3), collection.selection)
        collection.onIntent(LibraryIntent.SelectIds(ids(3, 4)))
        assertEquals(ids(3), collection.selection)
        // The library's own screen hands its songs to the checks; this one hands none.
        assertEquals(0, covers.state.value.total)
    }

    @Test
    fun `the library hands all its songs to the check of covers`() = runTest {
        startedLibrary()
        runCurrent()

        assertEquals(5, covers.state.value.total)
    }
}

/** A player that plays nothing. */
private class FakePlayer : PlayerController {
    override val state = MutableStateFlow(PlaybackState())

    override fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean) = Unit

    override fun togglePlayPause() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun skipToNext() = Unit

    override fun skipToPrevious() = Unit

    override fun skipToQueueItem(index: Int) = Unit

    override fun setShuffle(enabled: Boolean) = Unit

    override fun setRepeatMode(mode: RepeatMode) = Unit

    override fun stop() = Unit
}
