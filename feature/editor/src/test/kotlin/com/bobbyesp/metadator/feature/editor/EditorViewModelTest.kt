/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.domain.FakeBackupStore
import com.bobbyesp.metadator.core.domain.FakeFileInfo
import com.bobbyesp.metadator.core.domain.FakeIndexer
import com.bobbyesp.metadator.core.domain.FakeLibrary
import com.bobbyesp.metadator.core.domain.FakeSettings
import com.bobbyesp.metadator.core.domain.FakeStrings
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.FakeWriteAccess
import com.bobbyesp.metadator.core.domain.cover.FakeDownloader
import com.bobbyesp.metadator.core.domain.editor.LoadTrackUseCase
import com.bobbyesp.metadator.core.domain.lyrics.FindLyricsUseCase
import com.bobbyesp.metadator.core.domain.quality.CheckLosslessUseCase
import com.bobbyesp.metadator.core.domain.quality.FakeFileOpener
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityScanner
import com.bobbyesp.metadator.core.domain.quality.MemoryQualityStore
import com.bobbyesp.metadator.core.domain.save.BackupHold
import com.bobbyesp.metadator.core.domain.save.RestoreBackupUseCase
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.viewmodel.UiMessage
import com.bobbyesp.metadator.library.api.AccessResult
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.PictureType
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import com.bobbyesp.metadator.tags.api.TagWriteResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val ref = ContentRef("content://media/external/audio/media/1")
    private val cover =
        EmbeddedPicture(byteArrayOf(1, 2, 3), "image/jpeg", type = PictureType.FrontCover)
    private val original =
        TagSnapshot(
            TagMap.of(
                "TITLE" to listOf("Halo"),
                "ARTIST" to listOf("Beyoncé"),
                "BPM" to listOf("80"),
            ),
            listOf(cover),
        )

    private val files = FakeTagFiles(mapOf(ref to original))
    private val writer = SlowWriter(files)
    private val backups = FakeBackupStore()
    private val indexer = FakeIndexer()
    private val access = FakeWriteAccess()
    private val lyrics = FakeLyrics()
    private val images = FakeImageSource()
    private val strings = FakeStrings()

    /** The editor of [ref], as the app makes it, with [savedState] as the system gives it. */
    private fun TestScope.editor(
        savedState: SavedStateHandle = SavedStateHandle()
    ): EditorViewModel {
        val dispatchers = AppDispatchers(main.dispatcher, main.dispatcher)
        val check = CheckLosslessUseCase(FakeFileOpener(emptyMap()), dispatchers)
        return EditorViewModel(
            ref = ref,
            loadTrack =
                LoadTrackUseCase(FakeLibrary(), FakeFileInfo(mapOf(ref to "halo.mp3")), files),
            saveChanges = SaveTagChangesUseCase(files, writer, backups, indexer, BackupHold()),
            restoreBackup = RestoreBackupUseCase(backups, files, indexer),
            writeAccess = access,
            findLyricsUseCase = FindLyricsUseCase(lyrics),
            checkLossless = check,
            libraryQuality =
                LibraryQualityScanner(check, MemoryQualityStore(), dispatchers, backgroundScope),
            artworkDownloader = FakeDownloader(),
            images = images,
            settingsRepository = FakeSettings(),
            player = FakePlayer(),
            strings = strings,
            savedState = savedState,
        )
    }

    /** What [editor] says to the user, as it says it. */
    private fun TestScope.messagesOf(editor: EditorViewModel): List<UiMessage> =
        mutableListOf<UiMessage>().also { said ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                editor.messages.collect { said += it }
            }
        }

    /** What the system gives an editor made again after the process died. */
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any>(it) })

    private fun EditorViewModel.type(key: String, vararg values: String) =
        onIntent(EditorIntent.SetField(key, values.toList()))

    private val EditorViewModel.draft
        get() = state.value.draft!!

    @Test
    fun `a song opens with the file's tags to edit`() = runTest {
        val editor = editor()
        advanceUntilIdle()

        assertEquals(EditorStatus.Ready, editor.state.value.status)
        assertEquals(original.tags, editor.draft.tags)
        assertFalse(editor.state.value.isDirty)
    }

    @Test
    fun `what was typed before the process died is typed again`() = runTest {
        val saved = SavedStateHandle()
        val before = editor(saved)
        advanceUntilIdle()
        before.type("TITLE", "Halo (Live)")

        val after = editor(saved.afterProcessDeath())
        advanceUntilIdle()

        assertEquals(listOf("Halo (Live)"), after.draft.tags["TITLE"])
        assertEquals(setOf("TITLE"), after.draft.changedKeys)
    }

    @Test
    fun `a save writes the file, shows it as it is now, and offers Undo`() = runTest {
        val saved = SavedStateHandle()
        val editor = editor(saved)
        val said = messagesOf(editor)
        advanceUntilIdle()

        editor.type("TITLE", "Halo (Live)")
        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()

        assertEquals(listOf("Halo (Live)"), files.files.getValue(ref).tags["TITLE"])
        assertFalse(editor.state.value.saving)
        assertFalse(editor.state.value.isDirty)
        assertEquals(listOf("Halo (Live)"), editor.draft.original.tags["TITLE"])
        val message = said.single()
        assertEquals(strings.get(R.string.saved), message.text)
        assertEquals(strings.get(R.string.undo), message.actionLabel)
        assertNotNull(message.onAction)
        // Nothing is left to type again if the process dies now.
        val after = editor(saved.afterProcessDeath())
        advanceUntilIdle()
        assertFalse(after.state.value.isDirty)
    }

    @Test
    fun `Undo puts back what the save wrote`() = runTest {
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()
        editor.type("TITLE", "Halo (Live)")
        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()

        said.single().onAction!!.invoke()
        advanceUntilIdle()

        assertEquals(original, files.files.getValue(ref))
        assertEquals(listOf("Halo"), editor.draft.tags["TITLE"])
        assertEquals(strings.get(R.string.undone), said.last().text)
    }

    @Test
    fun `a change the file already holds is not written, and the editor shows the file`() =
        runTest {
            val editor = editor()
            val said = messagesOf(editor)
            advanceUntilIdle()
            editor.type("TITLE", "Halo (Live)")
            // The same title was given to the file elsewhere meanwhile.
            files.files[ref] =
                original.copy(tags = original.tags.with("TITLE", listOf("Halo (Live)")))

            editor.onIntent(EditorIntent.Save)
            advanceUntilIdle()

            assertTrue(files.writes.isEmpty())
            assertFalse(editor.state.value.saving)
            assertFalse(editor.state.value.isDirty)
            assertEquals(listOf("Halo (Live)"), editor.draft.tags["TITLE"])
            assertEquals(strings.get(R.string.save_nothing_to_write), said.single().text)
        }

    @Test
    fun `access refused before writing saves nothing and keeps what was typed`() = runTest {
        access.granted = false
        access.answer = AccessResult.Denied
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()
        editor.type("TITLE", "Halo (Live)")

        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()

        assertEquals(original, files.files.getValue(ref))
        assertFalse(editor.state.value.saving)
        assertEquals(listOf("Halo (Live)"), editor.draft.tags["TITLE"])
        assertEquals(strings.get(R.string.write_access_denied), said.single().text)
    }

    @Test
    fun `a write that needs access, refused when asked, keeps what was typed`() = runTest {
        // Android 10 lets the app think it can write, and refuses the write.
        files.writeResult = TagWriteResult.NeedsAccess
        access.answer = AccessResult.Denied
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()
        editor.type("TITLE", "Halo (Live)")

        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()

        assertEquals(1, access.requests.size)
        assertFalse(editor.state.value.saving)
        assertEquals(listOf("Halo (Live)"), editor.draft.tags["TITLE"])
        assertTrue(backups.backups.isEmpty())
        assertEquals(strings.get(R.string.write_access_denied), said.single().text)
    }

    @Test
    fun `a failed write keeps what was typed`() = runTest {
        files.failNextWrites = 1
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()
        editor.type("TITLE", "Halo (Live)")

        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()

        assertEquals(original, files.files.getValue(ref))
        assertFalse(editor.state.value.saving)
        assertEquals(setOf("TITLE"), editor.draft.changedKeys)
        assertEquals(
            strings.get(R.string.save_failed_restored, "disk on fire"),
            said.single().text,
        )
    }

    @Test
    fun `what is typed while a save writes is still there after it`() = runTest {
        val editor = editor()
        advanceUntilIdle()
        editor.type("TITLE", "Halo (Live)")
        writer.hold()
        editor.onIntent(EditorIntent.Save)
        advanceUntilIdle()
        assertTrue(editor.state.value.saving)

        editor.type("ARTIST", "Beyoncé", "Jay-Z")
        writer.release()
        advanceUntilIdle()

        val stored = files.files.getValue(ref).tags
        assertEquals(listOf("Halo (Live)"), stored["TITLE"])
        assertEquals(listOf("Beyoncé"), stored["ARTIST"])
        assertFalse(editor.state.value.saving)
        assertEquals(listOf("Halo (Live)"), editor.draft.tags["TITLE"])
        assertEquals(listOf("Beyoncé", "Jay-Z"), editor.draft.tags["ARTIST"])
        assertEquals(setOf("ARTIST"), editor.draft.changedKeys)
    }

    @Test
    fun `lyrics are not looked for without a title`() = runTest {
        files.files[ref] = original.copy(tags = original.tags.with("TITLE", emptyList()))
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()

        editor.onIntent(EditorIntent.FindLyrics)
        advanceUntilIdle()

        assertTrue(lyrics.queries.isEmpty())
        assertFalse(editor.state.value.findingLyrics)
        assertEquals(strings.get(R.string.lyrics_need_title_artist), said.single().text)
    }

    @Test
    fun `repairing garbled text changes only the garbled fields`() = runTest {
        files.files[ref] =
            TagSnapshot(
                TagMap.of(
                    "TITLE" to listOf("Bir Sana Bir Bana"),
                    "ARTIST" to listOf("MoÄŸollar", "Cahit Berkay"),
                    "ALBUM" to listOf("Aşk"),
                ),
                emptyList(),
            )
        val editor = editor()
        advanceUntilIdle()

        editor.onIntent(EditorIntent.RepairText)

        assertEquals(
            mapOf("ARTIST" to listOf("Moğollar", "Cahit Berkay")),
            editor.draft.changes.fields,
        )
    }

    @Test
    fun `a failure nothing else explains says something went wrong`() = runTest {
        // Measuring the cover, after the song is read, has no handler of its own.
        images.failure = IllegalStateException("no decoder")
        val editor = editor()
        val said = messagesOf(editor)
        advanceUntilIdle()

        assertEquals(EditorStatus.Ready, editor.state.value.status)
        assertEquals(listOf(strings.get(CoreUiR.string.error_unexpected)), said.map { it.text })
    }
}
