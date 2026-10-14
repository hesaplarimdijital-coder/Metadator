/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.common.StringProvider
import com.bobbyesp.metadator.core.domain.batch.AlbumMerge
import com.bobbyesp.metadator.core.domain.batch.BatchEdit
import com.bobbyesp.metadator.core.domain.batch.BatchItem
import com.bobbyesp.metadator.core.domain.batch.BatchProgress
import com.bobbyesp.metadator.core.domain.batch.BatchRunner
import com.bobbyesp.metadator.core.domain.batch.FileNamePattern
import com.bobbyesp.metadator.core.domain.batch.FilePositions
import com.bobbyesp.metadator.core.domain.batch.PositionEdit
import com.bobbyesp.metadator.core.domain.batch.TrackNumbering
import com.bobbyesp.metadator.core.domain.batch.changesFor
import com.bobbyesp.metadator.core.domain.cover.CoverRequest
import com.bobbyesp.metadator.core.domain.editor.TrackPositions
import com.bobbyesp.metadator.core.domain.editor.withMultiValueMode
import com.bobbyesp.metadator.core.domain.lookup.AlbumProposal
import com.bobbyesp.metadator.core.domain.lookup.AlbumQuery
import com.bobbyesp.metadator.core.domain.lookup.AlbumSong
import com.bobbyesp.metadator.core.domain.lookup.ReleasePosition
import com.bobbyesp.metadator.core.domain.lookup.positionsFor
import com.bobbyesp.metadator.core.domain.save.RestoreBackupUseCase
import com.bobbyesp.metadator.core.domain.save.RestoreOutcome
import com.bobbyesp.metadator.core.domain.settings.SettingsRepository
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.metadator.core.ui.viewmodel.UiMessage
import com.bobbyesp.metadator.library.api.AccessResult
import com.bobbyesp.metadator.library.api.AudioFileInfo
import com.bobbyesp.metadator.library.api.AudioLibrary
import com.bobbyesp.metadator.library.api.WriteAccess
import com.bobbyesp.metadator.lookup.api.ArtworkDownloader
import com.bobbyesp.metadator.tags.api.ArtworkChange
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.PictureType
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagReadResult
import com.bobbyesp.metadator.tags.api.TagReader
import com.bobbyesp.metadator.tags.api.TagSnapshot
import com.bobbyesp.metadator.tags.api.applying
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What the selected files have in a field: one shared value, different ones, or nothing. */
sealed interface CommonValue {
    data class Same(val values: List<String>) : CommonValue

    data object Mixed : CommonValue

    data object Empty : CommonValue
}

/** @param durationMs how long the song is, as the library says; null where it does not know */
data class BatchFile(
    val item: BatchItem,
    val title: String,
    val tags: TagMap,
    val durationMs: Long? = null,
)

data class BatchState(
    val loading: Boolean = true,
    /** How many songs were handed over: what the head says while they are read. */
    val expected: Int = 0,
    val files: List<BatchFile> = emptyList(),
    val common: Map<String, CommonValue> = emptyMap(),
    /** The fields the user set, and to what. Untouched fields keep each file's own value. */
    val edits: Map<String, List<String>> = emptyMap(),
    /**
     * Track and disc numbers for some songs, each its own: from the release a lookup found, or the
     * discs a merge read from the albums' names. Untouched songs keep theirs.
     */
    val positions: Map<ContentRef, FilePositions> = emptyMap(),
    val cover: ArtworkChange = ArtworkChange.Unchanged,
    /** A cover was chosen and is being fetched: it is not in [cover] yet. */
    val loadingCover: Boolean = false,
    val numbering: TrackNumbering? = null,
    val pattern: String = FileNamePattern.Suggestions.first(),
    val usePattern: Boolean = false,
    val progress: BatchProgress? = null,
    val separator: String = "; ",
    /** The albums the songs are on, as they are in the files, when they are on more than one. */
    val merge: AlbumMerge? = null,
) {
    /** How many songs are being edited: the ones read, or until then the ones to read. */
    val count: Int
        get() = if (loading) expected else files.size

    val fileNamePattern: FileNamePattern?
        get() = FileNamePattern(pattern).takeIf { usePattern && it.isValid }

    val edit: BatchEdit
        get() =
            BatchEdit(
                fields = edits,
                artwork = cover,
                numbering = numbering,
                fileNamePattern = fileNamePattern,
                positions = positions,
            )

    /**
     * Whether the edits make the songs one album: one title and one album artist for all, which is
     * what the library groups an album by.
     */
    val mergesAlbums: Boolean
        get() =
            merge != null &&
                edits[TagField.Album.key].orEmpty().any { it.isNotBlank() } &&
                edits[TagField.AlbumArtist.key].orEmpty().isNotEmpty()

    /** How the first files would read with the pattern, for a live preview. */
    val patternPreview: List<Pair<String, Map<String, List<String>>?>>
        get() {
            val parsed = FileNamePattern(pattern).takeIf { it.isValid } ?: return emptyList()
            return files.take(3).map { it.item.displayName to parsed.parse(it.item.displayName) }
        }

    /** Not while a cover is on its way: applying then would leave it out without saying so. */
    val canApply: Boolean
        get() = !edit.isEmpty && files.isNotEmpty() && progress?.isRunning != true && !loadingCover

    /**
     * The tags each song will have once [edit] is applied: what a cover is looked for by, so that
     * an album named here is searched for under its new name.
     */
    val editedTags: List<TagMap>
        get() {
            val pending = edit
            return files.mapIndexed { index, file ->
                val changes =
                    pending.changesFor(
                        file.item,
                        index,
                        files.size,
                        TagSnapshot(file.tags, emptyList()),
                    )
                file.tags.applying(changes.fields)
            }
        }
}

sealed interface BatchIntent {
    data class SetField(val key: String, val values: List<String>) : BatchIntent

    data class ResetField(val key: String) : BatchIntent

    data class SetCoverFromUri(val uri: String) : BatchIntent

    /** Uses the cover an online source has at [url]. */
    data class SetCoverFromUrl(val url: String) : BatchIntent

    /** Asks for the covers the online sources have for these songs. */
    data object FindCover : BatchIntent

    /** Asks for the releases the online sources have these songs on. */
    data object FindMetadata : BatchIntent

    /**
     * Fills the fields with what is known of [proposal], and the songs found on it with their track
     * and disc numbers: to be checked, and written with the rest.
     */
    data class UseAlbum(val proposal: AlbumProposal) : BatchIntent

    /**
     * Makes the songs one album, called [album], by [albumArtists]; with [discNumbers], each song
     * also gets the disc its album's name said.
     */
    data class MergeAlbums(
        val album: String,
        val albumArtists: List<String>,
        val discNumbers: Boolean,
    ) : BatchIntent

    /** Each song keeps its own track and disc numbers. */
    data object ResetPositions : BatchIntent

    data object RemoveCovers : BatchIntent

    data object KeepCovers : BatchIntent

    data class SetNumbering(val numbering: TrackNumbering?) : BatchIntent

    data class SetPattern(val pattern: String) : BatchIntent

    data class UsePattern(val enabled: Boolean) : BatchIntent

    data object Apply : BatchIntent

    data object Cancel : BatchIntent

    data object UndoAll : BatchIntent

    data object Dismiss : BatchIntent
}

sealed interface BatchEffect {
    data object Close : BatchEffect

    /** Show the covers there are for [request], for one of them to be chosen. */
    class FindCover(val request: CoverRequest) : BatchEffect

    /** Show the releases there are for [query], for one of them to be chosen. */
    class FindMetadata(val query: AlbumQuery) : BatchEffect
}

class BatchViewModel(
    private val refs: List<String>,
    private val reader: TagReader,
    private val library: AudioLibrary,
    private val fileInfo: AudioFileInfo,
    private val runner: BatchRunner,
    private val restore: RestoreBackupUseCase,
    private val writeAccess: WriteAccess,
    private val settings: SettingsRepository,
    private val images: CoverReader,
    private val artwork: ArtworkDownloader,
    private val strings: StringProvider,
) : BaseViewModel<BatchIntent, BatchState, BatchEffect>(BatchState(expected = refs.size)) {

    init {
        runner.clear()
        runner.progress.onEach { setState { copy(progress = it) } }.launchIn(viewModelScope)
        settings.settings
            .onEach { setState { copy(separator = it.multiValueSeparator) } }
            .launchIn(viewModelScope)
        load()
    }

    override fun handleIntent(intent: BatchIntent) {
        when (intent) {
            is BatchIntent.SetField ->
                setState { copy(edits = edits + (intent.key to intent.values)) }
            is BatchIntent.ResetField -> setState { copy(edits = edits - intent.key) }
            is BatchIntent.SetCoverFromUri ->
                setCover(R.string.batch_cover_unreadable) { images.read(intent.uri) }
            is BatchIntent.SetCoverFromUrl ->
                setCover(R.string.batch_cover_download_failed) {
                    artwork.download(intent.url)?.let { it.bytes to it.mimeType }
                }
            BatchIntent.FindCover -> findCover()
            BatchIntent.FindMetadata -> findMetadata()
            is BatchIntent.UseAlbum -> useAlbum(intent.proposal)
            is BatchIntent.MergeAlbums -> mergeAlbums(intent)
            BatchIntent.ResetPositions -> setState { copy(positions = emptyMap()) }
            BatchIntent.RemoveCovers -> setState { copy(cover = ArtworkChange.Remove) }
            BatchIntent.KeepCovers -> setState { copy(cover = ArtworkChange.Unchanged) }
            is BatchIntent.SetNumbering -> setState { copy(numbering = intent.numbering) }
            is BatchIntent.SetPattern -> setState { copy(pattern = intent.pattern) }
            is BatchIntent.UsePattern -> setState { copy(usePattern = intent.enabled) }
            BatchIntent.Apply -> apply()
            BatchIntent.Cancel -> runner.cancel()
            BatchIntent.UndoAll -> undoAll()
            BatchIntent.Dismiss -> {
                runner.clear()
                sendEffect(BatchEffect.Close)
            }
        }
    }

    private fun load() =
        launch(onError = { setState { copy(loading = false) } }) {
            val gate = Semaphore(PARALLEL_READS)
            val files =
                kotlinx.coroutines.coroutineScope {
                    refs
                        .map { uri ->
                            async {
                                gate.withPermit {
                                    val ref = ContentRef(uri)
                                    val track = library.findByRef(ref)
                                    val name =
                                        track?.displayName
                                            ?: fileInfo.describe(ref)?.displayName
                                            ?: uri.substringAfterLast('/')
                                    val tags =
                                        (reader.read(ref, includePictures = false)
                                                as? TagReadResult.Success)
                                            ?.snapshot
                                            ?.tags
                                    tags?.let {
                                        BatchFile(
                                            BatchItem(ref, name),
                                            track?.title ?: name,
                                            it,
                                            track?.durationMs?.takeIf { ms -> ms > 0 },
                                        )
                                    }
                                }
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                }
            setState {
                copy(
                    loading = false,
                    files = files,
                    common = commonValues(files),
                    merge = AlbumMerge.of(files.map { it.tags }),
                )
            }
        }

    /**
     * Makes the picture [source] gives the cover of every song, or says [failure] if it gives none.
     */
    private fun setCover(failure: Int, source: suspend () -> Pair<ByteArray, String>?) =
        launch(onError = { setState { copy(loadingCover = false) } }) {
            setState { copy(loadingCover = true) }
            val image = source()
            setState { copy(loadingCover = false) }
            if (image == null) {
                showMessage(UiMessage(strings.get(failure)))
                return@launch
            }
            val (bytes, mime) = image
            setState {
                copy(
                    cover =
                        ArtworkChange.Replace(
                            EmbeddedPicture(bytes, mime, type = PictureType.FrontCover)
                        )
                )
            }
        }

    /**
     * Has the screen show the covers there are for these songs. They are looked for as the songs
     * will be once the edit is applied, and with the cover they have now: the one chosen here, or
     * the first song's, read from its file for this. The files were listed without their pictures.
     */
    private fun findCover() {
        val state = currentState
        val first = state.files.firstOrNull() ?: return
        launch {
            val chosen = (state.cover as? ArtworkChange.Replace)?.picture
            val current =
                chosen
                    ?: (reader.read(first.item.ref) as? TagReadResult.Success)?.snapshot?.frontCover
            sendEffect(
                BatchEffect.FindCover(
                    CoverRequest.forSongs(
                        songs = state.editedTags,
                        titleFallback = first.title,
                        durationMs = reader.readAudioProperties(first.item.ref)?.durationMs,
                        currentCover = current?.data,
                    )
                )
            )
        }
    }

    /**
     * Has the screen show the releases there are for these songs, looked for by the album most of
     * them will be on once the edit is applied, and its artist: corrected here, they are searched
     * under the new names.
     */
    private fun findMetadata() {
        val state = currentState
        if (state.files.isEmpty()) return
        val edited = state.editedTags
        val artists =
            edited.mostCommon(TagField.AlbumArtist.key) ?: edited.mostCommon(TagField.Artist.key)
        sendEffect(
            BatchEffect.FindMetadata(
                AlbumQuery(
                    album = edited.mostCommon(TagField.Album.key).orEmpty(),
                    artist = artists.orEmpty(),
                    songs = albumSongs(state, edited),
                )
            )
        )
    }

    /**
     * Fills the fields the release knows, for every song, and the numbers of the songs found on it.
     * Its title is not taken: the songs keep theirs. Nothing is written before "Apply to all".
     */
    private fun useAlbum(proposal: AlbumProposal) {
        val state = currentState
        val found = proposal.positionsFor(albumSongs(state, state.editedTags))
        val fields =
            buildMap<String, List<String>> {
                put(TagField.Album.key, listOf(proposal.album))
                if (proposal.albumArtists.isNotEmpty()) {
                    put(TagField.AlbumArtist.key, proposal.albumArtists)
                }
                proposal.date?.let { put(TagField.Date.key, listOf(it)) }
                if (proposal.genres.isNotEmpty()) put(TagField.Genre.key, proposal.genres)
            }
        val numbers =
            state.files
                .zip(found)
                .mapNotNull { (file, position) ->
                    position?.let { file.item.ref to it.asFilePositions() }
                }
                .filterNot { (_, set) -> set.isEmpty }
                .toMap()
        setState { copy(edits = edits + fields, positions = positions.merged(numbers)) }
        showMessage(UiMessage(strings.get(R.string.batch_lookup_applied, proposal.album)))
    }

    /** Sets the one title and album artist the merged album will have, and the discs if asked. */
    private fun mergeAlbums(intent: BatchIntent.MergeAlbums) {
        val album = intent.album.trim()
        val artists = intent.albumArtists.map { it.trim() }.filter { it.isNotEmpty() }
        if (album.isEmpty() || artists.isEmpty()) return
        setState {
            val discs =
                if (intent.discNumbers) {
                    merge
                        ?.discs
                        .orEmpty()
                        .mapNotNull { (index, disc) ->
                            files.getOrNull(index)?.let {
                                it.item.ref to FilePositions(disc = PositionEdit(number = disc))
                            }
                        }
                        .toMap()
                } else emptyMap()
            copy(
                edits =
                    edits +
                        mapOf(
                            TagField.Album.key to listOf(album),
                            TagField.AlbumArtist.key to artists,
                        ),
                positions = positions.merged(discs),
            )
        }
    }

    private fun commonValues(files: List<BatchFile>): Map<String, CommonValue> =
        EditableFields.associate { field ->
            val values = files.map { it.tags[field.key] }.distinct()
            field.key to
                when {
                    values.size == 1 && values.single().isEmpty() -> CommonValue.Empty
                    values.size == 1 -> CommonValue.Same(values.single())
                    else -> CommonValue.Mixed
                }
        }

    private fun apply() {
        val state = currentState
        if (!state.canApply) return
        launch {
            val refs = state.files.map { it.item.ref }
            if (!writeAccess.canWrite(refs) && writeAccess.request(refs) != AccessResult.Granted) {
                showMessage(UiMessage(strings.get(R.string.batch_access_denied), long = true))
                return@launch
            }
            val current = settings.settings.first()
            runner.start(state.files.map { it.item }, state.edit) { changes ->
                changes.withMultiValueMode(current.multiValueMode, current.multiValueSeparator)
            }
        }
    }

    private fun undoAll() {
        val backups = currentState.progress?.backups.orEmpty()
        launch {
            val restored = backups.count { restore(it) == RestoreOutcome.Restored }
            showMessage(UiMessage(strings.plural(R.plurals.batch_undone, restored, restored)))
            runner.clear()
            sendEffect(BatchEffect.Close)
        }
    }

    /** The songs as an album search sees them, as they will be once the edit is applied. */
    private fun albumSongs(state: BatchState, edited: List<TagMap>): List<AlbumSong> =
        state.files.zip(edited) { file, tags ->
            AlbumSong(
                title = tags.first(TagField.Title.key)?.ifBlank { null } ?: file.title,
                durationMs = file.durationMs,
                trackNumber = TrackPositions.read(tags, TagField.TrackNumber).number.toIntOrNull(),
                discNumber = TrackPositions.read(tags, TagField.DiscNumber).number.toIntOrNull(),
            )
        }

    companion object {
        val EditableFields =
            listOf(
                TagField.Artist,
                TagField.Album,
                TagField.AlbumArtist,
                TagField.Date,
                TagField.Genre,
                TagField.Composer,
                TagField.Comment,
            )
        private const val PARALLEL_READS = 4
    }
}

/** The value most of these songs have in [key], the first of them on a tie; null where none has. */
private fun List<TagMap>.mostCommon(key: String): String? = mapNotNull { tags ->
    tags.first(key)?.trim()?.ifEmpty { null }
}
    .groupingBy { it }
    .eachCount()
    .maxByOrNull { it.value }
    ?.key

/** These positions, with [other]'s parts in place of theirs for the songs it has. */
private fun Map<ContentRef, FilePositions>.merged(
    other: Map<ContentRef, FilePositions>
): Map<ContentRef, FilePositions> =
    this + other.mapValues { (ref, positions) -> (this[ref] ?: FilePositions()) + positions }

/** What of a place on a release a song gets: what is known, the rest kept as the file has it. */
private fun ReleasePosition.asFilePositions(): FilePositions =
    FilePositions(
        track = PositionEdit(number = track, total = trackTotal).takeUnless { it.isEmpty },
        disc = PositionEdit(number = disc, total = discTotal).takeUnless { it.isEmpty },
    )
