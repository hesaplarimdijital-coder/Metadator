/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.common.StringProvider
import com.bobbyesp.metadator.core.domain.batch.BatchRunner
import com.bobbyesp.metadator.core.domain.library.LibraryFilter
import com.bobbyesp.metadator.core.domain.library.Storage
import com.bobbyesp.metadator.core.domain.library.albums
import com.bobbyesp.metadator.core.domain.library.artists
import com.bobbyesp.metadator.core.domain.library.countByStorage
import com.bobbyesp.metadator.core.domain.library.filtered
import com.bobbyesp.metadator.core.domain.library.folders
import com.bobbyesp.metadator.core.domain.library.sorted
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityScanner
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityState
import com.bobbyesp.metadator.core.model.SortOrder
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.model.TrackSort
import com.bobbyesp.metadator.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.metadator.core.ui.viewmodel.UiMessage
import com.bobbyesp.metadator.library.api.AudioLibrary
import com.bobbyesp.metadator.player.api.PlayerController
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

enum class LibraryTab {
    Songs,
    Albums,
    Artists,
    Folders,
}

/** A place the library's sidebar goes to: a way of browsing it, or a list of songs it keeps. */
sealed interface LibrarySection {
    data class Browse(val tab: LibraryTab) : LibrarySection

    /** The songs missing essential tags. */
    data object NeedsAttention : LibrarySection

    /** The lossless files found to have come from lossy ones. */
    data object FakeLossless : LibrarySection

    /** The songs on one volume: the device's own storage, an SD card. */
    data class OnStorage(val storage: Storage) : LibrarySection
}

/**
 * What the library lists, with the filter and order it was listed by. A change of the query is a
 * new list taking the place of the old; the same query lists the songs as they change, each row
 * keeping its place. The screen tells the two apart by this.
 */
data class Listing(
    val filter: LibraryFilter = LibraryFilter(),
    val sort: SortOrder = SortOrder(),
    val tracks: List<Track> = emptyList(),
    val albums: List<TrackCollection.Album> = emptyList(),
    val artists: List<TrackCollection.Artist> = emptyList(),
    val folders: List<TrackCollection.Folder> = emptyList(),
    /** How much room the songs listed take, added up once here rather than on every frame. */
    val totalBytes: Long = 0,
)

data class LibraryState(
    val loading: Boolean = true,
    val totalTracks: Int = 0,
    val listing: Listing = Listing(),
    val tab: LibraryTab = LibraryTab.Songs,
    /** The filter as chosen, which the list follows a moment later ([listing]). */
    val filter: LibraryFilter = LibraryFilter(),
    val sort: SortOrder = SortOrder(),
    val formats: List<String> = emptyList(),
    val needsAttentionCount: Int = 0,
    val selection: Set<TrackId> = emptySet(),
    val refreshing: Boolean = false,
    val playingId: TrackId? = null,
    val playerActive: Boolean = false,
    val playbackRunning: Boolean = false,
    /** What the lossless check found, for every song of the library. */
    val quality: LibraryQualityState = LibraryQualityState(),
    /** How many of the library's songs were found to be fake lossless files. */
    val fakeCount: Int = 0,
    /** How many songs each volume holds. */
    val storages: List<Pair<Storage, Int>> = emptyList(),
) {
    val tracks: List<Track>
        get() = listing.tracks

    val albums: List<TrackCollection.Album>
        get() = listing.albums

    val artists: List<TrackCollection.Artist>
        get() = listing.artists

    val folders: List<TrackCollection.Folder>
        get() = listing.folders

    val selecting: Boolean
        get() = selection.isNotEmpty()

    val selectedTracks: List<Track>
        get() = tracks.filter { it.id in selection }

    /** Where the sidebar is: the smart list or volume the songs are narrowed to, else the tab. */
    val section: LibrarySection
        get() {
            val storage = filter.storage
            return when {
                filter.fakeLossless -> LibrarySection.FakeLossless
                filter.needsAttention && tab == LibraryTab.Songs -> LibrarySection.NeedsAttention
                storage != null && tab == LibraryTab.Songs -> LibrarySection.OnStorage(storage)
                else -> LibrarySection.Browse(tab)
            }
        }
}

sealed interface LibraryIntent {
    data object Start : LibraryIntent

    data class Search(val query: String) : LibraryIntent

    data class SelectTab(val tab: LibraryTab) : LibraryIntent

    data object ToggleNeedsAttention : LibraryIntent

    data object ToggleFakeLossless : LibraryIntent

    /** Goes to a place of the sidebar: its tab, with the songs narrowed as it narrows them. */
    data class ShowSection(val section: LibrarySection) : LibraryIntent

    data class ToggleFormat(val format: String) : LibraryIntent

    data class Sort(val sort: TrackSort) : LibraryIntent

    data object ToggleSortDirection : LibraryIntent

    data object ClearFilters : LibraryIntent

    data class ToggleSelection(val id: TrackId) : LibraryIntent

    /** Selects every song from the one last selected to this one, as a click with Shift does. */
    data class SelectRangeTo(val id: TrackId) : LibraryIntent

    data object SelectAll : LibraryIntent

    data object ClearSelection : LibraryIntent

    data object Refresh : LibraryIntent

    data class Play(val track: Track) : LibraryIntent

    data class PlayAll(val shuffle: Boolean) : LibraryIntent

    data object PlaySelection : LibraryIntent
}

class LibraryViewModel(
    private val library: AudioLibrary,
    private val player: PlayerController,
    private val quality: LibraryQualityScanner,
    private val batches: BatchRunner,
    private val strings: StringProvider,
    private val dispatchers: AppDispatchers,
    private val savedState: SavedStateHandle,
) :
    BaseViewModel<LibraryIntent, LibraryState, Nothing>(
        LibraryState(
            tab =
                savedState.get<String>(KEY_TAB)?.let { name ->
                    LibraryTab.entries.firstOrNull { it.name == name }
                } ?: LibraryTab.Songs,
            filter = LibraryFilter(search = savedState[KEY_SEARCH] ?: ""),
        )
    ) {
    private val query = MutableStateFlow(Query(currentState.filter, currentState.sort))
    private var observing: Job? = null

    /** The song a range is selected from: the last one selected on its own. */
    private var selectionAnchor: TrackId? = null

    private data class Query(val filter: LibraryFilter, val sort: SortOrder)

    init {
        quality.state
            .onEach { checked ->
                setState {
                    copy(
                        quality = checked,
                        fakeCount = checked.qualities.values.count { it.isFake },
                    )
                }
            }
            .launchIn(viewModelScope)
        // Only what the library shows of the player: its position moves twice a second.
        player.state
            .map { Triple(it.current?.id, it.isActive, it.isPlaying) }
            .distinctUntilChanged()
            .onEach { (playing, active, running) ->
                setState {
                    copy(playingId = playing, playerActive = active, playbackRunning = running)
                }
            }
            .launchIn(viewModelScope)
        // Selecting songs is mostly for editing them together. Once that is done, the selection
        // has served: left standing, it was the first thing to deal with on coming back.
        batches.progress
            .map { it != null && !it.isRunning && it.saved.isNotEmpty() }
            .distinctUntilChanged()
            .onEach { done -> if (done) handleIntent(LibraryIntent.ClearSelection) }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: LibraryIntent) {
        when (intent) {
            LibraryIntent.Start -> startObserving()
            is LibraryIntent.Search -> {
                savedState[KEY_SEARCH] = intent.query
                updateQuery { copy(filter = filter.copy(search = intent.query)) }
            }
            is LibraryIntent.SelectTab -> {
                savedState[KEY_TAB] = intent.tab.name
                setState { copy(tab = intent.tab, selection = emptySet()) }
            }
            LibraryIntent.ToggleNeedsAttention ->
                updateQuery { copy(filter = filter.copy(needsAttention = !filter.needsAttention)) }
            LibraryIntent.ToggleFakeLossless ->
                updateQuery { copy(filter = filter.copy(fakeLossless = !filter.fakeLossless)) }
            is LibraryIntent.ShowSection -> showSection(intent.section)
            is LibraryIntent.ToggleFormat ->
                updateQuery {
                    val formats = filter.formats
                    copy(
                        filter =
                            filter.copy(
                                formats =
                                    if (intent.format in formats) formats - intent.format
                                    else formats + intent.format
                            )
                    )
                }
            is LibraryIntent.Sort -> updateQuery { copy(sort = sort.copy(sort = intent.sort)) }
            LibraryIntent.ToggleSortDirection ->
                updateQuery { copy(sort = sort.copy(ascending = !sort.ascending)) }
            LibraryIntent.ClearFilters ->
                updateQuery { copy(filter = LibraryFilter(search = filter.search)) }
            is LibraryIntent.ToggleSelection -> {
                selectionAnchor = intent.id
                setState {
                    copy(
                        selection =
                            if (intent.id in selection) selection - intent.id
                            else selection + intent.id
                    )
                }
            }
            is LibraryIntent.SelectRangeTo -> {
                val anchor = selectionAnchor
                if (anchor == null) selectionAnchor = intent.id
                setState {
                    val from = tracks.indexOfFirst { it.id == anchor }
                    val to = tracks.indexOfFirst { it.id == intent.id }
                    when {
                        to < 0 -> this
                        // Nothing to start from, or it is no longer listed: this one alone.
                        from < 0 -> copy(selection = selection + intent.id)
                        else ->
                            copy(
                                selection =
                                    selection +
                                        tracks.subList(minOf(from, to), maxOf(from, to) + 1).map {
                                            it.id
                                        }
                            )
                    }
                }
            }
            LibraryIntent.SelectAll ->
                setState { copy(selection = tracks.mapTo(mutableSetOf()) { it.id }) }
            LibraryIntent.ClearSelection -> {
                selectionAnchor = null
                setState { copy(selection = emptySet()) }
            }
            LibraryIntent.Refresh -> refresh()
            is LibraryIntent.Play -> {
                val queue = currentState.tracks
                player.play(queue, startIndex = queue.indexOf(intent.track).coerceAtLeast(0))
            }
            is LibraryIntent.PlayAll ->
                player.play(currentState.tracks, 0, shuffle = intent.shuffle)
            LibraryIntent.PlaySelection -> {
                player.play(currentState.selectedTracks)
                setState { copy(selection = emptySet()) }
            }
        }
    }

    private fun showSection(section: LibrarySection) {
        val tab = (section as? LibrarySection.Browse)?.tab ?: LibraryTab.Songs
        savedState[KEY_TAB] = tab.name
        setState { copy(tab = tab, selection = emptySet()) }
        updateQuery {
            copy(
                filter =
                    filter.copy(
                        needsAttention = section == LibrarySection.NeedsAttention,
                        fakeLossless = section == LibrarySection.FakeLossless,
                        storage = (section as? LibrarySection.OnStorage)?.storage,
                    )
            )
        }
    }

    private fun updateQuery(transform: Query.() -> Query) {
        query.update(transform)
        setState { copy(filter = query.value.filter, sort = query.value.sort) }
    }

    private fun startObserving() {
        if (observing?.isActive == true) return
        // Only which songs are fake bears on what is listed; the rest of what the check finds is
        // shown, not filtered by, and comes on its own above.
        val fakes =
            quality.state
                .map { checked -> checked.qualities.filterValues { it.isFake }.keys }
                .distinctUntilChanged()
        observing =
            combine(library.observeTracks(), query, fakes) { tracks, query, fake ->
                    // The whole library, not what is listed: the check goes through all of it.
                    quality.submit(tracks)
                    present(tracks, query, fake)
                }
                .flowOn(dispatchers.default)
                .catch {
                    setState { copy(loading = false) }
                    showMessage(UiMessage(strings.get(R.string.library_load_failed)))
                }
                .onEach { presented -> setState { presented(this) } }
                .launchIn(viewModelScope)
    }

    /** Everything derived from the library and the query, computed off the main thread. */
    private fun present(
        all: List<Track>,
        query: Query,
        fakes: Set<String>,
    ): LibraryState.() -> LibraryState {
        val visible = all.filtered(query.filter) { it.ref.uri in fakes }.sorted(query.sort)
        val albums = visible.albums()
        val artists = visible.artists()
        val folders = visible.folders()
        val formats = all.map { it.fileExtension }.filter { it.isNotEmpty() }.distinct().sorted()
        val needsAttention = all.count { it.needsAttention }
        val storages = all.countByStorage()
        val totalBytes = visible.sumOf { it.sizeBytes }
        val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
        return {
            copy(
                loading = false,
                totalTracks = all.size,
                listing =
                    Listing(
                        filter = query.filter,
                        sort = query.sort,
                        tracks = visible,
                        albums = albums,
                        artists = artists,
                        folders = folders,
                        totalBytes = totalBytes,
                    ),
                formats = formats,
                needsAttentionCount = needsAttention,
                storages = storages,
                selection = selection.filterTo(mutableSetOf()) { it in visibleIds },
            )
        }
    }

    /**
     * The scan of a library with little new in it is over in a moment: the indicator stays long
     * enough to be seen, and what it was for is said once it is done. Asked for with nothing to
     * show for it, a rescan looked as if it had not happened.
     */
    private fun refresh() {
        launch {
            setState { copy(refreshing = true) }
            val started = TimeSource.Monotonic.markNow()
            try {
                library.refresh()
                delay(MIN_REFRESH - started.elapsedNow())
            } finally {
                setState { copy(refreshing = false) }
            }
            showMessage(UiMessage(strings.get(R.string.library_rescanned)))
        }
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_SEARCH = "search"
        val MIN_REFRESH = 900.milliseconds
    }

    /** A failure of what the user pressed (Rescan) is said on screen, not only logged. */
    override fun onUnhandledError(error: Throwable) {
        super.onUnhandledError(error)
        showMessage(unexpectedErrorMessage(strings))
    }
}
