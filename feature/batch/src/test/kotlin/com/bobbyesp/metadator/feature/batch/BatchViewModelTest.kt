/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import com.bobbyesp.metadator.core.domain.FakeBackupStore
import com.bobbyesp.metadator.core.domain.FakeIndexer
import com.bobbyesp.metadator.core.domain.FakeLibrary
import com.bobbyesp.metadator.core.domain.FakeSettings
import com.bobbyesp.metadator.core.domain.FakeStrings
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.FakeWriteAccess
import com.bobbyesp.metadator.core.domain.batch.BatchRunner
import com.bobbyesp.metadator.core.domain.cover.FakeDownloader
import com.bobbyesp.metadator.core.domain.fakeTrack
import com.bobbyesp.metadator.core.domain.save.BackupHold
import com.bobbyesp.metadator.core.domain.save.RestoreBackupUseCase
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.ui.image.ImageInfo
import com.bobbyesp.metadator.core.ui.image.ImageSource
import com.bobbyesp.metadator.core.ui.image.PickedImage
import com.bobbyesp.metadator.core.ui.viewmodel.UiMessage
import com.bobbyesp.metadator.library.api.AccessResult
import com.bobbyesp.metadator.tags.api.ArtworkChange
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val tracks = (1L..3L).map { fakeTrack(it, artist = "Daft Punk", album = "Album $it") }
    private val originals = tracks.associate {
        it.ref to
            TagSnapshot(
                TagMap.of(
                    "TITLE" to listOf(it.title),
                    "ARTIST" to listOf("Daft Punk"),
                    "ALBUM" to listOf(it.album!!),
                ),
                emptyList(),
            )
    }
    private val files = FakeTagFiles(originals)
    private val backups = FakeBackupStore()
    private val indexer = FakeIndexer()
    private val access = FakeWriteAccess()
    private val images = SlowImages()
    private val strings = FakeStrings()
    private lateinit var runner: BatchRunner

    /** The batch editor of the songs [ids], as the app makes it. */
    private fun TestScope.batch(ids: List<Long> = tracks.map { it.id.value }): BatchViewModel {
        val hold = BackupHold()
        val save = SaveTagChangesUseCase(files, files, backups, indexer, hold)
        runner = BatchRunner(this, save, backups, hold)
        return BatchViewModel(
            ids = ids,
            reader = files,
            library = FakeLibrary(tracks),
            runner = runner,
            restore = RestoreBackupUseCase(backups, files, indexer),
            writeAccess = access,
            settings = FakeSettings(),
            images = images,
            artwork = FakeDownloader(),
            strings = strings,
        )
    }

    /** What [batch] says to the user, as it says it. */
    private fun TestScope.messagesOf(batch: BatchViewModel): List<UiMessage> =
        mutableListOf<UiMessage>().also { said ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                batch.messages.collect { said += it }
            }
        }

    /** What [batch] asks of the screen. */
    private fun TestScope.effectsOf(batch: BatchViewModel): List<BatchEffect> =
        mutableListOf<BatchEffect>().also { asked ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                batch.effects.collect { asked += it }
            }
        }

    private fun BatchViewModel.setAlbum(album: String) =
        onIntent(BatchIntent.SetField(TagField.Album.key, listOf(album)))

    @Test
    fun `the songs open with what they have in common`() = runTest {
        val batch = batch()
        advanceUntilIdle()

        val state = batch.state.value
        assertFalse(state.loading)
        assertEquals(3, state.files.size)
        assertEquals(CommonValue.Same(listOf("Daft Punk")), state.common[TagField.Artist.key])
        assertEquals(CommonValue.Mixed, state.common[TagField.Album.key])
        assertEquals(CommonValue.Empty, state.common[TagField.Genre.key])
    }

    @Test
    fun `a song gone since it was chosen is left out, and said`() = runTest {
        val batch = batch(ids = listOf(1, 2, 99))
        val said = messagesOf(batch)
        advanceUntilIdle()

        assertEquals(2, batch.state.value.files.size)
        assertEquals(listOf(strings.plural(R.plurals.batch_left_out, 1, 1)), said.map { it.text })
    }

    @Test
    fun `nothing is applied while a cover is on its way`() = runTest {
        val batch = batch()
        advanceUntilIdle()
        batch.setAlbum("Discovery")
        assertTrue(batch.state.value.canApply)

        images.hold()
        batch.onIntent(BatchIntent.SetCoverFromUri("content://picked/cover"))
        advanceUntilIdle()
        assertTrue(batch.state.value.loadingCover)
        assertFalse(batch.state.value.canApply)
        batch.onIntent(BatchIntent.Apply)
        advanceUntilIdle()
        assertTrue(files.writes.isEmpty())

        images.release()
        advanceUntilIdle()
        assertTrue(batch.state.value.cover is ArtworkChange.Replace)
        assertTrue(batch.state.value.canApply)
    }

    @Test
    fun `access refused writes nothing, and says so`() = runTest {
        access.granted = false
        access.answer = AccessResult.Denied
        val batch = batch()
        val said = messagesOf(batch)
        advanceUntilIdle()
        batch.setAlbum("Discovery")

        batch.onIntent(BatchIntent.Apply)
        advanceUntilIdle()

        assertEquals(1, access.requests.size)
        assertTrue(files.writes.isEmpty())
        assertNull(batch.state.value.progress)
        assertEquals(listOf(strings.get(R.string.batch_access_denied)), said.map { it.text })
    }

    @Test
    fun `undo all puts every song back, and closes`() = runTest {
        val batch = batch()
        val said = messagesOf(batch)
        val effects = effectsOf(batch)
        advanceUntilIdle()
        batch.setAlbum("Discovery")
        batch.onIntent(BatchIntent.Apply)
        advanceUntilIdle()
        assertEquals(3, batch.state.value.progress?.saved?.size)
        files.files.values.forEach { assertEquals(listOf("Discovery"), it.tags["ALBUM"]) }

        batch.onIntent(BatchIntent.UndoAll)
        advanceUntilIdle()

        assertEquals(originals, files.files)
        assertEquals(strings.plural(R.plurals.batch_undone, 3, 3), said.last().text)
        assertEquals(listOf(BatchEffect.Close), effects)
        assertNull(runner.progress.value)
    }
}

/** Pictures picked by URI, each a PNG; once [hold] is called, each waits for [release]. */
private class SlowImages : ImageSource {
    private var gate: CompletableDeferred<Unit>? = null

    fun hold() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
    }

    override suspend fun read(uri: String): PickedImage {
        gate?.await()
        return PickedImage(byteArrayOf(0x89.toByte(), 'P'.code.toByte()), "image/png")
    }

    override suspend fun inspect(bytes: ByteArray): ImageInfo? = null
}
