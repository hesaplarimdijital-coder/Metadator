/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.common.StringProvider
import com.bobbyesp.metadator.core.domain.artwork.LibraryCoverScanner
import com.bobbyesp.metadator.core.domain.artwork.LibraryCoverState
import com.bobbyesp.metadator.core.domain.batch.BatchRunner
import com.bobbyesp.metadator.core.domain.library.LibraryFilter
import com.bobbyesp.metadator.core.domain.library.Storage
import com.bobbyesp.metadator.core.domain.library.albums
import com.bobbyesp.metadator.core.domain.library.artists
import com.bobbyesp.metadator.core.domain.library.collection
import com.bobbyesp.metadator.core.domain.library.countByStorage
import com.bobbyesp.metadator.core.domain.library.countLackingCover
import com.bobbyesp.metadator.core.domain.library.filtered
import com.bobbyesp.metadator.core.domain.library.folders
import com.bobbyesp.metadator.core.domain.library.lackingCover
import com.bobbyesp.metadator.core.domain.library.sorted
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityScanner
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityState
import com.bobbyesp.metadator.core.domain.text.hasGarbledText
import com.bobbyesp.metadator.core.model.CollectionType
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    /** The songs whose files hold no cover. */
    data object NoCover : LibrarySection

    /** The songs whose text still looks garbled once the library has read their files. */
    data object GarbledText : LibrarySection

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
) {
    /**
     * The songs listed, by id, made once with the listing, off the main thread: a drag over the
     * rows checks what it touched against them at every row it crosses ([LibraryIntent.SelectIds]),
     * and each made them all again.
     */
    val listedIds: Set<TrackId> = tracks.mapTo(HashSet(tracks.size)) { it.id }
}

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
    /** What the check of covers found, and how far it has got. */
    val covers: LibraryCoverState = LibraryCoverState(),
    /** How many of the library's songs, albums and artists lack a cover, of those checked. */
    val noCoverCount: Int = 0,
    val albumsWithoutCover: Int = 0,
    val artistsWithoutCover: Int = 0,
    /** How many of the library's songs have text that still looks garbled. */
    val garbledCount: Int = 0,
    /**
     * The album, artist or folder this lists, on its screen ([LibraryIntent.ShowCollection]): its
     * songs are [tracks], in its own order. Null in the library.
     */
    val collection: TrackCollection? = null,
    /**
     * The song last opened with a click, while it is listed: what the inspector beside the list
     * shows once nothing is selected, and where a range taken with Shift starts.
     */
    val clicked: TrackId? = null,
    /**
     * The places of the library visited before this one, the last one last: where Back goes, as a
     * browser's does, before it leaves the library.
     */
    val history: List<LibrarySection> = emptyList(),
    /**
     * The albums, artists or folders selected, by key, of the place shown: to be played or edited
     * together, and albums to be merged. The songs selected are [selection].
     */
    val collectionSelection: Set<String> = emptySet(),
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

    /**
     * Whether the check of covers is waiting for the lossless check to be done with the files,
     * which it is given as work that matters more (`busy` in the app's modules): its share does not
     * move meanwhile, which the first pass over a large lossless library makes an hour.
     */
    val coversWaiting: Boolean
        get() = covers.isRunning && quality.isRunning

    val selectedTracks: List<Track>
        get() = tracks.filter { it.id in selection }

    /** The albums, the artists or the folders, when they are what the place shown lists. */
    val shownCollections: List<TrackCollection>
        get() =
            when (tab) {
                LibraryTab.Songs -> emptyList()
                LibraryTab.Albums -> albums
                LibraryTab.Artists -> artists
                LibraryTab.Folders -> folders
            }

    val selectingCollections: Boolean
        get() = collectionSelection.isNotEmpty()

    /** The songs of the albums, artists or folders selected, in the order they are listed. */
    val selectedCollectionTracks: List<Track>
        get() =
            shownCollections
                .filter { it.key in collectionSelection }
                .flatMap { it.tracks }
                .distinctBy { it.id }

    /** Where the sidebar is: the smart list or volume the songs are narrowed to, else the tab. */
    val section: LibrarySection
        get() {
            val storage = filter.storage
            return when {
                filter.fakeLossless -> LibrarySection.FakeLossless
                filter.garbledText && tab == LibraryTab.Songs -> LibrarySection.GarbledText
                filter.needsAttention && tab == LibraryTab.Songs -> LibrarySection.NeedsAttention
                filter.noCover && tab == LibraryTab.Songs -> LibrarySection.NoCover
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

    /** Narrows to the songs without a cover; the albums and artists with one such song. */
    data object ToggleNoCover : LibraryIntent

    data object ToggleGarbledText : LibraryIntent

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

    /**
     * Lists the songs of one album, artist or folder, in its own order, rather than the library's:
     * a collection's screen, whose selection, ranges and playing are of those songs alone.
     */
    data class ShowCollection(val type: CollectionType, val key: String) : LibraryIntent

    /** A song was opened with a click ([LibraryState.clicked]). */
    data class Opened(val id: TrackId) : LibraryIntent

    /**
     * Selects exactly [ids] or, with [add], adds them to what is selected: what a drag over the
     * rows asks for, as it goes. A range taken with Shift next starts from [anchor], when given.
     * Songs not listed are left out.
     */
    data class SelectIds(
        val ids: Set<TrackId>,
        val add: Boolean = false,
        val anchor: TrackId? = null,
    ) : LibraryIntent

    /** Goes back to the place visited before this one ([LibraryState.history]). */
    data object BackToPlace : LibraryIntent

    /** Adds an album, artist or folder to the selection, or takes it out of it. */
    data class ToggleCollection(val key: String) : LibraryIntent

    /**
     * Selects every album, artist or folder from the one last selected to this one, as a click with
     * Shift does.
     */
    data class SelectCollectionsTo(val key: String) : LibraryIntent

    /** Selects every album, artist or folder the place shown lists. */
    data object SelectAllCollections : LibraryIntent

    data object ClearCollectionSelection : LibraryIntent

    /** Plays the songs of an album, an artist or a folder, from the first or shuffled. */
    data class PlayCollection(val collection: TrackCollection, val shuffle: Boolean) : LibraryIntent

    data object PlayCollectionSelection : LibraryIntent
}

class LibraryViewModel(
    private val library: AudioLibrary,
    private val player: PlayerController,
    private val quality: LibraryQualityScanner,
    private val covers: LibraryCoverScanner,
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

    /** Whether this lists one collection, for its screen ([LibraryIntent.ShowCollection]). */
    private var listsCollection = false
    private var observing: Job? = null
    private var coverCounts: Job? = null

    /** The song a range is selected from: the last one selected on its own, or opened. */
    private var selectionAnchor: TrackId? = null

    /** The album, artist or folder a range is selected from, by key. */
    private var collectionAnchor: String? = null

    private data class Query(val filter: LibraryFilter, val sort: SortOrder)

    /** A query, with the songs without a cover when it is narrowed to them. */
    private data class Listed(val query: Query, val withoutCover: Set<TrackId>)

    /** The library as last listed, for what is counted of it apart from the listing. */
    private val latest = MutableStateFlow<List<Track>>(emptyList())

    /**
     * The songs whose text looks garbled, worked out once for each version of the library, not at
     * each letter typed in the search ([garbledIn]).
     */
    private var garbled: Pair<List<Track>, Set<TrackId>>? = null

    init {
        savedState
            .get<List<String>>(KEY_HISTORY)
            ?.mapNotNull { it.toPlace() }
            ?.let { places ->
                setState { copy(history = places) }
            }
        quality.state
            .onEach { checked ->
                setState {
                    copy(
                        quality = checked,
                        // Counted for the library's sidebar; a collection's screen has none.
                        fakeCount =
                            if (listsCollection) 0
                            else checked.qualities.values.count { it.isFake },
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
            .onEach { done ->
                if (done) {
                    handleIntent(LibraryIntent.ClearSelection)
                    // Albums merged are one album now: the ones selected are not there to select.
                    handleIntent(LibraryIntent.ClearCollectionSelection)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: LibraryIntent) {
        val place = currentState.section
        when (intent) {
            LibraryIntent.Start -> startObserving()
            is LibraryIntent.Search -> {
                savedState[KEY_SEARCH] = intent.query
                updateQuery { copy(filter = filter.copy(search = intent.query)) }
            }
            is LibraryIntent.SelectTab -> {
                savedState[KEY_TAB] = intent.tab.name
                setState {
                    copy(tab = intent.tab, selection = emptySet(), collectionSelection = emptySet())
                }
            }
            LibraryIntent.ToggleNeedsAttention ->
                updateQuery { copy(filter = filter.copy(needsAttention = !filter.needsAttention)) }
            LibraryIntent.ToggleFakeLossless ->
                updateQuery { copy(filter = filter.copy(fakeLossless = !filter.fakeLossless)) }
            LibraryIntent.ToggleNoCover ->
                updateQuery { copy(filter = filter.copy(noCover = !filter.noCover)) }
            LibraryIntent.ToggleGarbledText ->
                updateQuery { copy(filter = filter.copy(garbledText = !filter.garbledText)) }
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
            is LibraryIntent.ShowCollection -> showCollection(intent.type, intent.key)
            is LibraryIntent.Opened -> {
                selectionAnchor = intent.id
                setState { copy(clicked = intent.id) }
            }
            is LibraryIntent.SelectIds -> {
                intent.anchor?.let { selectionAnchor = it }
                setState {
                    val chosen = intent.ids.filterTo(mutableSetOf()) { it in listing.listedIds }
                    copy(selection = if (intent.add) selection + chosen else chosen)
                }
            }
            LibraryIntent.BackToPlace -> {
                val history = currentState.history
                val previous = history.lastOrNull() ?: return
                keepHistory(history.dropLast(1))
                showSection(previous)
                return
            }
            is LibraryIntent.ToggleCollection -> {
                collectionAnchor = intent.key
                setState {
                    copy(
                        collectionSelection =
                            if (intent.key in collectionSelection) collectionSelection - intent.key
                            else collectionSelection + intent.key
                    )
                }
            }
            is LibraryIntent.SelectCollectionsTo -> selectCollectionsTo(intent.key)
            LibraryIntent.SelectAllCollections ->
                setState {
                    copy(collectionSelection = shownCollections.mapTo(mutableSetOf()) { it.key })
                }
            LibraryIntent.ClearCollectionSelection -> {
                collectionAnchor = null
                setState { copy(collectionSelection = emptySet()) }
            }
            is LibraryIntent.PlayCollection ->
                player.play(intent.collection.tracks, shuffle = intent.shuffle)
            LibraryIntent.PlayCollectionSelection -> {
                player.play(currentState.selectedCollectionTracks)
                setState { copy(collectionSelection = emptySet()) }
            }
        }
        // A place left for another is where Back comes to, as in a browser.
        if (currentState.section != place) {
            keepHistory((currentState.history + place).takeLast(MAX_HISTORY))
        }
    }

    /** As a range of songs is selected, among the albums, artists or folders shown. */
    private fun selectCollectionsTo(key: String) {
        val anchor = collectionAnchor
        if (anchor == null) collectionAnchor = key
        setState {
            val keys = shownCollections.map { it.key }
            val from = if (anchor == null) -1 else keys.indexOf(anchor)
            val to = keys.indexOf(key)
            when {
                to < 0 -> this
                // Nothing to start from, or it is no longer listed: this one alone.
                from < 0 -> copy(collectionSelection = collectionSelection + key)
                else ->
                    copy(
                        collectionSelection =
                            collectionSelection + keys.subList(minOf(from, to), maxOf(from, to) + 1)
                    )
            }
        }
    }

    private fun keepHistory(history: List<LibrarySection>) {
        savedState[KEY_HISTORY] = ArrayList(history.map { it.saved })
        setState { copy(history = history) }
    }

    private fun showSection(section: LibrarySection) {
        val tab = (section as? LibrarySection.Browse)?.tab ?: LibraryTab.Songs
        savedState[KEY_TAB] = tab.name
        setState { copy(tab = tab, selection = emptySet(), collectionSelection = emptySet()) }
        updateQuery {
            copy(
                filter =
                    filter.copy(
                        needsAttention = section == LibrarySection.NeedsAttention,
                        fakeLossless = section == LibrarySection.FakeLossless,
                        storage = (section as? LibrarySection.OnStorage)?.storage,
                        noCover = section == LibrarySection.NoCover,
                        garbledText = section == LibrarySection.GarbledText,
                    )
            )
        }
    }

    private fun updateQuery(transform: Query.() -> Query) {
        query.update(transform)
        setState { copy(filter = query.value.filter, sort = query.value.sort) }
    }

    /**
     * A collection's screen needs its own songs and what is selected of them, nothing more: no
     * other list made from the library, no count for the sidebar, and no second check of every song
     * for the lossless check, which the library's own does. A burst of changes, as a batch saving
     * song after song, is caught up with once it is over, not one change after another.
     */
    private fun showCollection(type: CollectionType, key: String) {
        if (observing?.isActive == true) return
        listsCollection = true
        // The songs it showed last: found again by them once an edit has given it another key.
        var shown: List<TrackId> = emptyList()
        observing =
            library
                .observeTracks()
                .conflate()
                .map { all ->
                    val found = all.collection(type, key, shown)
                    if (found != null) shown = found.tracks.map { it.id }
                    presentCollection(all.size, found)
                }
                .flowOn(dispatchers.default)
                .catch {
                    setState { copy(loading = false) }
                    showMessage(UiMessage(strings.get(R.string.library_load_failed)))
                }
                .onEach { presented -> setState { presented(this) } }
                .launchIn(viewModelScope)
    }

    private fun presentCollection(
        total: Int,
        found: TrackCollection?,
    ): LibraryState.() -> LibraryState {
        val tracks = found?.tracks.orEmpty()
        val listing = Listing(tracks = tracks, totalBytes = tracks.sumOf { it.sizeBytes })
        val ids = listing.listedIds
        return {
            copy(
                loading = false,
                totalTracks = total,
                collection = found,
                listing = listing,
                selection = selection.filterTo(mutableSetOf()) { it in ids },
                clicked = clicked?.takeIf { it in ids },
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startObserving() {
        if (observing?.isActive == true) return
        countCovers()
        // Only which songs are fake bears on what is listed; the rest of what the check finds is
        // shown, not filtered by, and comes on its own above.
        val fakes =
            quality.state
                .map { checked -> checked.qualities.filterValues { it.isFake }.keys }
                .distinctUntilChanged()
        // Which songs lack a cover bears on what is listed only while it is narrowed to them: the
        // check finds more every few songs, which is not worth listing everything again for.
        val queried = query.flatMapLatest { current ->
            if (current.filter.noCover) {
                covers.state
                    .map { it.withoutCover }
                    .distinctUntilChanged()
                    .map { Listed(current, it) }
            } else flowOf(Listed(current, emptySet()))
        }
        observing =
            combine(library.observeTracks(), queried, fakes) { tracks, listed, fake ->
                    // The whole library, not what is listed: the checks go through all of it.
                    quality.submit(tracks)
                    covers.submit(tracks)
                    latest.value = tracks
                    present(tracks, listed.query, fake, listed.withoutCover)
                }
                .flowOn(dispatchers.default)
                .catch {
                    setState { copy(loading = false) }
                    showMessage(UiMessage(strings.get(R.string.library_load_failed)))
                }
                .onEach { presented -> setState { presented(this) } }
                .launchIn(viewModelScope)
    }

    /**
     * What the sidebar and the chips count of covers: the songs, and the albums and artists with
     * one, as the check finds them. Nothing listed changes with it, unless narrowed to them. The
     * library's alone: a collection's screen shows none of it, and counted at every step of the
     * check, its songs were handed to it again for nothing.
     */
    private fun countCovers() {
        if (coverCounts?.isActive == true) return
        coverCounts =
            combine(latest, covers.state) { all, found ->
                    val lacks = { track: Track -> track.id in found.withoutCover }
                    CoverCounts(
                        state = found,
                        songs = all.count(lacks),
                        albums = all.countLackingCover(CollectionType.Album, lacks),
                        artists = all.countLackingCover(CollectionType.Artist, lacks),
                    )
                }
                .flowOn(dispatchers.default)
                .onEach { counts ->
                    setState {
                        copy(
                            covers = counts.state,
                            noCoverCount = counts.songs,
                            albumsWithoutCover = counts.albums,
                            artistsWithoutCover = counts.artists,
                        )
                    }
                }
                .launchIn(viewModelScope)
    }

    /** Everything derived from the library and the query, computed off the main thread. */
    private fun present(
        all: List<Track>,
        query: Query,
        fakes: Set<String>,
        withoutCover: Set<TrackId>,
    ): LibraryState.() -> LibraryState {
        val filter = query.filter
        val isFake = { track: Track -> track.ref.uri in fakes }
        val lacksCover = { track: Track -> track.id in withoutCover }
        val garbledIds = garbledIn(all)
        val isGarbled = { track: Track -> track.id in garbledIds }
        val visible = all.filtered(filter, isFake, lacksCover, isGarbled).sorted(query.sort)
        // Narrowed to the songs without a cover, an album, an artist or a folder is listed whole:
        // a cover is found for the album, not for those of its songs that lack one.
        val grouped =
            if (filter.noCover)
                all.filtered(filter.copy(noCover = false), isFake, isGarbled = isGarbled)
            else visible
        val albums =
            grouped.albums().let { if (filter.noCover) it.lackingCover(lacksCover) else it }
        val artists =
            grouped.artists().let { if (filter.noCover) it.lackingCover(lacksCover) else it }
        val folders =
            grouped.folders().let { if (filter.noCover) it.lackingCover(lacksCover) else it }
        val formats = all.map { it.fileExtension }.filter { it.isNotEmpty() }.distinct().sorted()
        val needsAttention = all.count { it.needsAttention }
        val storages = all.countByStorage()
        // Made here, off the main thread, with the ids of what it lists.
        val listing =
            Listing(
                filter = query.filter,
                sort = query.sort,
                tracks = visible,
                albums = albums,
                artists = artists,
                folders = folders,
                totalBytes = visible.sumOf { it.sizeBytes },
            )
        val visibleIds = listing.listedIds
        val collections: List<TrackCollection> = albums + artists + folders
        val listedKeys = collections.mapTo(mutableSetOf()) { it.key }
        return {
            copy(
                loading = false,
                totalTracks = all.size,
                listing = listing,
                formats = formats,
                needsAttentionCount = needsAttention,
                garbledCount = garbledIds.size,
                storages = storages,
                selection = selection.filterTo(mutableSetOf()) { it in visibleIds },
                clicked = clicked?.takeIf { it in visibleIds },
                collectionSelection =
                    collectionSelection.filterTo(mutableSetOf()) { it in listedKeys },
            )
        }
    }

    /** The songs of [all] whose text looks garbled, worked out again only for another library. */
    private fun garbledIn(all: List<Track>): Set<TrackId> {
        garbled?.let { (of, ids) -> if (of === all) return ids }
        val ids = all.filter { it.hasGarbledText }.mapTo(HashSet()) { it.id }
        garbled = all to ids
        return ids
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
        const val KEY_HISTORY = "history"

        /** How many places Back goes through: a few steps back, not the whole day's browsing. */
        const val MAX_HISTORY = 10
        val MIN_REFRESH = 900.milliseconds
    }

    /** A failure of what the user pressed (Rescan) is said on screen, not only logged. */
    override fun onUnhandledError(error: Throwable) {
        super.onUnhandledError(error)
        showMessage(unexpectedErrorMessage(strings))
    }
}

/** A place as the saved state keeps it, and back ([toPlace]). */
private val LibrarySection.saved: String
    get() =
        when (this) {
            is LibrarySection.Browse -> "browse:${tab.name}"
            LibrarySection.NeedsAttention -> "attention"
            LibrarySection.FakeLossless -> "fake"
            LibrarySection.NoCover -> "nocover"
            LibrarySection.GarbledText -> "garbled"
            is LibrarySection.OnStorage ->
                when (storage) {
                    Storage.Device -> "device"
                    is Storage.Removable -> "removable:${storage.name}"
                }
        }

private fun String.toPlace(): LibrarySection? =
    when {
        startsWith("browse:") ->
            LibraryTab.entries
                .firstOrNull { it.name == removePrefix("browse:") }
                ?.let { LibrarySection.Browse(it) }
        this == "attention" -> LibrarySection.NeedsAttention
        this == "fake" -> LibrarySection.FakeLossless
        this == "nocover" -> LibrarySection.NoCover
        this == "garbled" -> LibrarySection.GarbledText
        this == "device" -> LibrarySection.OnStorage(Storage.Device)
        startsWith("removable:") ->
            LibrarySection.OnStorage(Storage.Removable(removePrefix("removable:")))
        else -> null
    }

/** What the check of covers found, as the sidebar and the chips count it. */
private data class CoverCounts(
    val state: LibraryCoverState,
    val songs: Int,
    val albums: Int,
    val artists: Int,
)
