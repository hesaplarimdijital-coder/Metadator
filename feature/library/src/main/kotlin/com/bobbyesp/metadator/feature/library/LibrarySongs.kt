/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.ContextMenuArea
import com.bobbyesp.metadator.core.designsystem.component.HeldKeys
import com.bobbyesp.metadator.core.designsystem.component.MarqueeSelectionState
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.marqueeSelection
import com.bobbyesp.metadator.core.designsystem.component.rememberMarqueeSelectionState
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.component.trackHeldKeys
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.model.TrackSort
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.LocalShownDetail
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.component.CollectionCard
import com.bobbyesp.metadator.core.ui.component.TrackColumns
import com.bobbyesp.metadator.core.ui.component.TrackRow
import com.bobbyesp.metadator.core.ui.component.TrackTableHeader
import com.bobbyesp.metadator.core.ui.component.TrackTableRow
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.hasHardwareKeyboard
import kotlinx.coroutines.launch

/**
 * The songs as a table, on a wide window: a header that sorts them and selects them all, and a row
 * for each with its columns. Too narrow for a table, the songs are the phone's rows.
 *
 * The library's songs and a collection's are this one list, selected and opened alike: a copy for
 * each drifted, and a collection's songs could not be selected at all. A collection gives its cover
 * and name as [header], which scrolls away over its songs, and [pinned], which stays at the top
 * with the columns once it has: its selection's bar. Its songs keep their own order, an album's by
 * disc and track, so its columns sort nothing ([sortable]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongsTable(
    tracks: List<Track>,
    state: LibraryState,
    actions: LibraryActions,
    header: (@Composable () -> Unit)? = null,
    pinned: (@Composable () -> Unit)? = null,
    sortable: Boolean = true,
) {
    val listState = rememberLazyListState()
    SongListShortcuts(listState)
    val onIntent = actions.onIntent
    // Ctrl and Shift, as they were when a song was clicked.
    val heldKeys = remember { HeldKeys() }
    // Read when a song is clicked, not closed over: a row's callbacks stay the same while the
    // library changes around it (a song checked, the player moving on), and the row is skipped.
    val latest = rememberUpdatedState(state)
    val menu = rememberSongMenu(latest, actions)
    val activeUri = (LocalShownDetail.current as? Editor)?.uri
    val allSelected = state.selection.size == tracks.size
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TrackColumns.MinWidth) {
            SongRows(tracks, state, actions, listState, heldKeys, inCard = false, header, pinned)
            return@BoxWithConstraints
        }
        val columns = TrackColumns.forWidth(maxWidth - 16.dp)
        val marquee = rememberSongMarquee(listState, latest, onIntent)
        val columnHeads: @Composable () -> Unit = {
            TrackTableHeader(
                columns = columns,
                sort = state.sort.sort.takeIf { sortable },
                ascending = state.sort.ascending,
                onSort =
                    if (sortable) {
                        { sort: TrackSort ->
                            onIntent(
                                if (sort == state.sort.sort) LibraryIntent.ToggleSortDirection
                                else LibraryIntent.Sort(sort)
                            )
                        }
                    } else null,
                selection =
                    when {
                        state.selection.isEmpty() -> ToggleableState.Off
                        allSelected -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    },
                onToggleAll = {
                    onIntent(
                        if (allSelected) LibraryIntent.ClearSelection else LibraryIntent.SelectAll
                    )
                },
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            // Over the library's songs the columns stay where they are. A collection's come under
            // its cover and name, and stay at the top once those have scrolled away.
            if (header == null) columnHeads()
            LazyColumn(
                modifier =
                    Modifier.weight(1f)
                        .trackHeldKeys(heldKeys)
                        .scrollbar(listState)
                        .marqueeSelection(marquee),
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            ) {
                if (header != null) {
                    item(key = HeaderKey, contentType = HeaderKey) { header() }
                    stickyHeader(key = PinnedKey, contentType = PinnedKey) {
                        Column(Modifier.background(Studio.colors.panel)) {
                            pinned?.invoke()
                            columnHeads()
                        }
                    }
                }
                items(tracks, key = { it.id.value }, contentType = { "track" }) { track ->
                    val selected = track.id in state.selection
                    ContextMenuArea(
                        actions = { menu(track, selected) },
                        modifier = Modifier.animateItem(),
                    ) {
                        TrackTableRow(
                            track = track,
                            columns = columns,
                            onClick = { onSongClick(track, latest.value, heldKeys, actions) },
                            onToggle = { onSongToggle(track, heldKeys, actions) },
                            onLongClick = { onIntent(LibraryIntent.ToggleSelection(track.id)) },
                            quality = state.quality.of(track),
                            selected = selected,
                            active = track.ref.uri == activeUri,
                            isPlaying = track.id == state.playingId,
                            isPlaybackRunning = state.playbackRunning,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The songs on a phone: rows on a card, below the filters, or below a collection's cover and name
 * ([header]), which scroll away with them.
 */
@Composable
internal fun SongCards(
    tracks: List<Track>,
    state: LibraryState,
    actions: LibraryActions,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    SongListShortcuts(listState)
    val heldKeys = remember { HeldKeys() }
    SongRows(tracks, state, actions, listState, heldKeys, inCard = true, header = header)
}

/**
 * The songs as rows, each with what the lossless check found or its length. On a phone they lie on
 * a card of their own; in a panel too narrow for a table, on the panel. [header] and [pinned] are a
 * collection's, as for [SongsTable].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRows(
    tracks: List<Track>,
    state: LibraryState,
    actions: LibraryActions,
    listState: LazyListState,
    heldKeys: HeldKeys,
    inCard: Boolean,
    header: (@Composable () -> Unit)? = null,
    pinned: (@Composable () -> Unit)? = null,
) {
    val onIntent = actions.onIntent
    val latest = rememberUpdatedState(state)
    val menu = rememberSongMenu(latest, actions)
    val activeUri = (LocalShownDetail.current as? Editor)?.uri
    val count = tracks.size
    val marquee = rememberSongMarquee(listState, latest, onIntent)
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .trackHeldKeys(heldKeys)
                .scrollbar(listState)
                .marqueeSelection(marquee),
        state = listState,
        contentPadding =
            if (inCard) {
                PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    bottom = Spacing.floatingClearance,
                )
            } else PaddingValues(vertical = 4.dp),
    ) {
        if (header != null) item(key = HeaderKey, contentType = HeaderKey) { header() }
        if (pinned != null) {
            stickyHeader(key = PinnedKey, contentType = PinnedKey) {
                Box(Modifier.background(Studio.colors.panel)) { pinned() }
            }
        }
        itemsIndexed(tracks, key = { _, track -> track.id.value }) { index, track ->
            val selected = track.id in state.selection
            ContextMenuArea(
                actions = { menu(track, selected) },
                modifier =
                    Modifier.animateItem()
                        .then(
                            if (inCard) {
                                val shape = cardRowShape(index, count)
                                Modifier.clip(shape).background(Studio.colors.panel, shape)
                            } else Modifier
                        ),
            ) {
                TrackRow(
                    track = track,
                    onClick = { onSongClick(track, latest.value, heldKeys, actions) },
                    onLongClick = { onIntent(LibraryIntent.ToggleSelection(track.id)) },
                    quality = state.quality.of(track),
                    selected = selected,
                    active = track.ref.uri == activeUri,
                    isPlaying = track.id == state.playingId,
                    isPlaybackRunning = state.playbackRunning,
                    showDivider = index > 0,
                )
            }
        }
    }
}

/** The keys of what a collection puts over its songs; the songs' own are numbers. */
private const val HeaderKey = "header"
private const val PinnedKey = "pinned"

/**
 * A rectangle dragged with the mouse over the songs selects them, as in a file manager
 * ([marqueeSelection]); with Ctrl, it adds them to what was selected as it began. What a collection
 * puts over its songs is not a song, and keeps its own clicks.
 */
@Composable
private fun rememberSongMarquee(
    listState: LazyListState,
    state: State<LibraryState>,
    onIntent: (LibraryIntent) -> Unit,
): MarqueeSelectionState {
    // What was selected as the drag began. Written and read by the drag alone, never in
    // composition.
    val before = remember { mutableStateOf(emptySet<TrackId>()) }
    return rememberMarqueeSelectionState(
        listState = listState,
        onStart = { before.value = state.value.selection },
        onSelect = { touched ->
            val ids = touched.keys.mapTo(LinkedHashSet()) { TrackId(it as Long) }
            // Always the whole selection, never an addition: the rectangle shrinks back, and what
            // it touched on the way is to be let go again.
            onIntent(
                LibraryIntent.SelectIds(
                    ids = if (touched.additive) before.value + ids else ids,
                    anchor = (touched.anchor as? Long)?.let { TrackId(it) },
                )
            )
        },
        // A song's key is its id; a collection's header and pinned bar have names.
        selectable = { key, _ -> key is Long },
    )
}

private fun onSongClick(
    track: Track,
    state: LibraryState,
    heldKeys: HeldKeys,
    actions: LibraryActions,
) {
    val click = songClick(heldKeys.shift, heldKeys.ctrl, heldKeys.mouse, state.selecting)
    heldKeys.clear()
    when (click) {
        SongClick.Range -> actions.onIntent(LibraryIntent.SelectRangeTo(track.id))
        SongClick.Toggle -> actions.onIntent(LibraryIntent.ToggleSelection(track.id))
        SongClick.Open -> {
            if (state.selecting) actions.onIntent(LibraryIntent.ClearSelection)
            actions.onOpenTrack(track)
        }
    }
}

/** What a click on a song does ([songClick]). */
internal enum class SongClick {
    /** Takes every song from the last one selected or opened to this one. */
    Range,

    /** Puts the song in the selection, or takes it out. */
    Toggle,

    /** Opens the song, and ends the selection there was. */
    Open,
}

/**
 * As a file manager selects: Shift takes everything up to the song, Ctrl the song alone, and
 * neither opens it. Without them, a click with the [mouse] opens the song and ends the selection,
 * as on a desktop; a finger's, while songs are [selecting], adds to them or takes away, as on a
 * phone. The pointer that clicked tells the two apart, not the window: a phone has a mouse plugged
 * in, and a narrow window on a desktop is still clicked with one.
 */
internal fun songClick(shift: Boolean, ctrl: Boolean, mouse: Boolean, selecting: Boolean) =
    when {
        shift -> SongClick.Range
        ctrl || (selecting && !mouse) -> SongClick.Toggle
        else -> SongClick.Open
    }

/**
 * A click on a song's box: with Shift, everything up to the song, as a click on its row takes; else
 * the song alone, in or out. The box is part of the row: Shift on it took the song alone.
 */
private fun onSongToggle(track: Track, heldKeys: HeldKeys, actions: LibraryActions) {
    val shift = heldKeys.shift
    heldKeys.clear()
    actions.onIntent(
        if (shift) LibraryIntent.SelectRangeTo(track.id)
        else LibraryIntent.ToggleSelection(track.id)
    )
}

/** The corners of the row at [index] of a card of [count]: the card's own at its ends. */
internal fun cardRowShape(index: Int, count: Int): Shape {
    val radius = 18.dp
    return RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == count - 1) radius else 0.dp,
        bottomEnd = if (index == count - 1) radius else 0.dp,
    )
}

/**
 * What a song's menu holds: for the mouse's second button, and the same as a long press offers. The
 * text is read here, once; the menu is made when it opens, for the song it opens on, from the
 * library as it is then.
 */
@Composable
private fun rememberSongMenu(
    state: State<LibraryState>,
    actions: LibraryActions,
): (Track, Boolean) -> List<MenuAction> {
    val onIntent = actions.onIntent
    val editLabel = stringResource(R.string.edit_song)
    val findCoverLabel = stringResource(R.string.find_cover)
    val playLabel = stringResource(R.string.play_song)
    val selectLabel = stringResource(R.string.select_song)
    val deselectLabel = stringResource(R.string.deselect_song)
    val editTogetherLabel = stringResource(R.string.edit_together)
    val newWindowLabel = stringResource(R.string.open_in_new_window)
    val accent = Studio.colors.accent
    // A click with Ctrl held selects: said where the mouse is, which is where it is done.
    val ctrlClick =
        if (hasHardwareKeyboard()) {
            stringResource(CoreUiR.string.key_ctrl) +
                " " +
                stringResource(CoreUiR.string.mouse_click)
        } else null
    val labels =
        listOf(
            editLabel,
            findCoverLabel,
            playLabel,
            selectLabel,
            deselectLabel,
            editTogetherLabel,
            newWindowLabel,
        )
    return remember(state, actions, labels, accent, ctrlClick) {
        { track, selected ->
            val library = state.value
            listOfNotNull(
                MenuAction(editLabel, Icons.Rounded.Edit) { actions.onOpenTrack(track) },
                MenuAction(findCoverLabel, Icons.Rounded.AutoAwesome, iconTint = accent) {
                    actions.onFindCover(track)
                },
                MenuAction(playLabel, Icons.Rounded.PlayArrow) {
                    onIntent(LibraryIntent.Play(track))
                },
                MenuAction(
                    if (selected) deselectLabel else selectLabel,
                    if (selected) Icons.Rounded.Close else Icons.Rounded.Check,
                    shortcut = ctrlClick,
                ) {
                    onIntent(LibraryIntent.ToggleSelection(track.id))
                },
                // On one of several selected songs, the menu is also the selection's.
                MenuAction(editTogetherLabel, Icons.Rounded.EditNote) {
                        actions.onEditTracks(library.selectedTracks)
                    }
                    .takeIf { selected && library.selection.size > 1 },
                MenuAction(newWindowLabel, Icons.AutoMirrored.Rounded.OpenInNew) {
                    actions.onOpenInNewWindow(track)
                },
            )
        }
    }
}

/**
 * The keys that move through a long list, for when nothing in it has the focus to take them. Home
 * and End go to the list's first and last items: a collection's cover and name come before its
 * songs, and End counted from those stopped short of the last song.
 */
@Composable
private fun SongListShortcuts(listState: LazyListState) {
    val scope = rememberCoroutineScope()
    KeyboardShortcuts(
        title = stringResource(R.string.tab_songs),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_PAGE_DOWN,
                    stringResource(CoreUiR.string.shortcut_page_down),
                    repeats = true,
                ) {
                    scope.launch { listState.scrollBy(listState.pageHeight()) }
                },
                Shortcut(
                    KeyEvent.KEYCODE_PAGE_UP,
                    stringResource(CoreUiR.string.shortcut_page_up),
                    repeats = true,
                ) {
                    scope.launch { listState.scrollBy(-listState.pageHeight()) }
                },
                Shortcut(KeyEvent.KEYCODE_MOVE_HOME, stringResource(R.string.shortcut_list_start)) {
                    scope.launch { listState.scrollToItem(0) }
                },
                Shortcut(KeyEvent.KEYCODE_MOVE_END, stringResource(R.string.shortcut_list_end)) {
                    scope.launch {
                        listState.scrollToItem(
                            (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        )
                    }
                },
            ),
    )
}

/** Most of what is in view: a line or two stay, to keep the place. */
private fun LazyListState.pageHeight(): Float = layoutInfo.viewportSize.height * 0.85f

/**
 * The albums, as a grid of covers. A click opens one; with Ctrl or Shift held, or a long press, it
 * selects, as on the songs; the mouse's second button opens its menu.
 */
@Composable
internal fun AlbumGrid(
    albums: List<TrackCollection.Album>,
    state: LibraryState,
    actions: LibraryActions,
    wide: Boolean,
) {
    val gridState = rememberLazyGridState()
    CollectionPlaceShortcuts(LibraryTab.Albums, albums, state, actions)
    val onIntent = actions.onIntent
    // Ctrl and Shift, as they were when an album was clicked.
    val heldKeys = remember { HeldKeys() }
    // Read when an album is clicked, not closed over, as the songs' rows read the library.
    val latest = rememberUpdatedState(state)
    val menu = rememberCollectionMenu(latest, actions)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (wide) 168.dp else 150.dp),
        modifier = Modifier.fillMaxSize().trackHeldKeys(heldKeys).scrollbar(gridState),
        state = gridState,
        contentPadding =
            if (wide) PaddingValues(10.dp)
            else
                PaddingValues(
                    start = Spacing.screen - 8.dp,
                    end = Spacing.screen - 8.dp,
                    bottom = Spacing.floatingClearance,
                ),
    ) {
        items(albums, key = { it.key }) { album ->
            val selected = album.key in state.collectionSelection
            ContextMenuArea(
                actions = { menu(album, selected) },
                modifier = Modifier.animateItem(),
            ) {
                CollectionCard(
                    title = album.title,
                    subtitle =
                        listOfNotNull(album.artist, album.year?.toString())
                            .joinToString(" · ")
                            .ifEmpty { null },
                    artwork = album.artwork,
                    onClick = { onCollectionClick(album, latest.value, heldKeys, actions) },
                    onLongClick = { onIntent(LibraryIntent.ToggleCollection(album.key)) },
                    selected = selected,
                )
            }
        }
    }
}

/**
 * The artists or the folders, as rows: on the panel on a wide window, on a card on a phone. They
 * are opened, selected and given a menu as the albums are.
 */
@Composable
internal fun CollectionList(
    tab: LibraryTab,
    collections: List<TrackCollection>,
    state: LibraryState,
    actions: LibraryActions,
    wide: Boolean,
) {
    val listState = rememberLazyListState()
    CollectionPlaceShortcuts(tab, collections, state, actions)
    val onIntent = actions.onIntent
    val heldKeys = remember { HeldKeys() }
    val latest = rememberUpdatedState(state)
    val menu = rememberCollectionMenu(latest, actions)
    LazyColumn(
        modifier = Modifier.fillMaxSize().trackHeldKeys(heldKeys).scrollbar(listState),
        state = listState,
        contentPadding =
            if (wide) PaddingValues(8.dp)
            else
                PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    bottom = Spacing.floatingClearance,
                ),
    ) {
        itemsIndexed(collections, key = { _, it -> it.key }) { index, collection ->
            val shape = cardRowShape(index, collections.size)
            val selected = collection.key in state.collectionSelection
            ContextMenuArea(
                actions = { menu(collection, selected) },
                modifier =
                    Modifier.animateItem()
                        .then(
                            if (wide) Modifier
                            else Modifier.clip(shape).background(Studio.colors.panel, shape)
                        ),
            ) {
                CollectionRow(
                    collection = collection,
                    onClick = { onCollectionClick(collection, latest.value, heldKeys, actions) },
                    onLongClick = { onIntent(LibraryIntent.ToggleCollection(collection.key)) },
                    selected = selected,
                    showDivider = !wide && index > 0,
                )
            }
        }
    }
}

/**
 * An artist or a folder: its picture or icon, its name, and how many songs and where. Selected, it
 * is tinted with the accent and a check takes the place of its picture, as on a song's row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionRow(
    collection: TrackCollection,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val tint by
        animateColorAsState(
            targetValue =
                when {
                    selected -> colors.accentSoft
                    hovered -> colors.hover
                    else -> Color.Transparent
                },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "CollectionRowTint",
        )
    val selectedLabel = stringResource(CoreUiR.string.song_selected)
    val shape = RoundedCornerShape(10.dp)
    Column(modifier) {
        if (showDivider) StudioDivider(Modifier.padding(start = 66.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(shape)
                    .background(tint, shape)
                    .combinedClickable(
                        interactionSource = interactions,
                        indication = ripple(),
                        role = Role.Button,
                        onLongClickLabel = stringResource(CoreUiR.string.select),
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                    .semantics { if (selected) stateDescription = selectedLabel }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (collection.artwork != null && collection !is TrackCollection.Folder) {
                    ArtworkImage(
                        model = collection.artwork,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        shape =
                            if (collection is TrackCollection.Artist) CircleShape
                            else RoundedCornerShape(9.dp),
                    )
                } else {
                    CollectionIcon(
                        if (collection is TrackCollection.Folder) Icons.Outlined.Folder
                        else Icons.Outlined.Person
                    )
                }
                SelectedCheck(selected, size = 42.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    collectionDetails(collection),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CollectionIcon(icon: ImageVector) {
    Box(
        Modifier.size(42.dp).background(Studio.colors.accentSoft, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Studio.colors.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}
