/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.ContextMenuArea
import com.bobbyesp.metadator.core.designsystem.component.HeldKeys
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.component.trackHeldKeys
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongsTable(tracks: List<Track>, state: LibraryState, actions: LibraryActions) {
    val listState = rememberLazyListState()
    SongListShortcuts(state, listState)
    val onIntent = actions.onIntent
    // Ctrl and Shift, as they were when a song was clicked.
    val heldKeys = remember { HeldKeys() }
    val menu = rememberSongMenu(state, actions)
    val activeUri = (LocalShownDetail.current as? Editor)?.uri
    val allSelected = state.selection.size == tracks.size
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TrackColumns.MinWidth) {
            SongRows(tracks, state, actions, listState, heldKeys, inCard = false)
            return@BoxWithConstraints
        }
        val columns = TrackColumns.forWidth(maxWidth - 16.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            TrackTableHeader(
                columns = columns,
                sort = state.sort.sort,
                ascending = state.sort.ascending,
                onSort = { sort ->
                    onIntent(
                        if (sort == state.sort.sort) LibraryIntent.ToggleSortDirection
                        else LibraryIntent.Sort(sort)
                    )
                },
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
            LazyColumn(
                modifier = Modifier.weight(1f).trackHeldKeys(heldKeys).scrollbar(listState),
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            ) {
                items(tracks, key = { it.id.value }, contentType = { "track" }) { track ->
                    val selected = track.id in state.selection
                    ContextMenuArea(
                        actions = { menu(track, selected) },
                        modifier = Modifier.animateItem(),
                    ) {
                        TrackTableRow(
                            track = track,
                            columns = columns,
                            onClick = { onSongClick(track, state, heldKeys, actions) },
                            onToggle = { onIntent(LibraryIntent.ToggleSelection(track.id)) },
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

/** The songs on a phone: rows on a card, below the filters. */
@Composable
internal fun SongCards(tracks: List<Track>, state: LibraryState, actions: LibraryActions) {
    val listState = rememberLazyListState()
    SongListShortcuts(state, listState)
    val heldKeys = remember { HeldKeys() }
    SongRows(tracks, state, actions, listState, heldKeys, inCard = true)
}

/**
 * The songs as rows, each with what the lossless check found or its length. On a phone they lie on
 * a card of their own; in a panel too narrow for a table, on the panel.
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
) {
    val onIntent = actions.onIntent
    val menu = rememberSongMenu(state, actions)
    val activeUri = (LocalShownDetail.current as? Editor)?.uri
    val count = tracks.size
    LazyColumn(
        modifier = Modifier.fillMaxSize().trackHeldKeys(heldKeys).scrollbar(listState),
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
                    onClick = { onSongClick(track, state, heldKeys, actions) },
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

/**
 * As a file manager selects: Shift takes everything up to the song, Ctrl the song alone, and
 * neither opens it; while songs are selected, a click adds to them or takes away.
 */
private fun onSongClick(
    track: Track,
    state: LibraryState,
    heldKeys: HeldKeys,
    actions: LibraryActions,
) {
    val shift = heldKeys.shift
    val ctrl = heldKeys.ctrl
    heldKeys.clear()
    when {
        shift -> actions.onIntent(LibraryIntent.SelectRangeTo(track.id))
        ctrl || state.selecting -> actions.onIntent(LibraryIntent.ToggleSelection(track.id))
        else -> actions.onOpenTrack(track)
    }
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
 * text is read here, once; the menu is made when it opens, for the song it opens on.
 */
@Composable
private fun rememberSongMenu(
    state: LibraryState,
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
    return { track, selected ->
        listOfNotNull(
            MenuAction(editLabel, Icons.Rounded.Edit) { actions.onOpenTrack(track) },
            MenuAction(findCoverLabel, Icons.Rounded.AutoAwesome, iconTint = accent) {
                actions.onFindCover(track)
            },
            MenuAction(playLabel, Icons.Rounded.PlayArrow) { onIntent(LibraryIntent.Play(track)) },
            MenuAction(
                if (selected) deselectLabel else selectLabel,
                if (selected) Icons.Rounded.Close else Icons.Rounded.Check,
                shortcut = ctrlClick,
            ) {
                onIntent(LibraryIntent.ToggleSelection(track.id))
            },
            // On one of several selected songs, the menu is also the selection's.
            MenuAction(editTogetherLabel, Icons.Rounded.EditNote) {
                    actions.onEditTracks(state.selectedTracks)
                }
                .takeIf { selected && state.selection.size > 1 },
            MenuAction(newWindowLabel, Icons.AutoMirrored.Rounded.OpenInNew) {
                actions.onOpenInNewWindow(track)
            },
        )
    }
}

/** The keys that move through a long list, for when nothing in it has the focus to take them. */
@Composable
private fun SongListShortcuts(state: LibraryState, listState: LazyListState) {
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
                        listState.scrollToItem((state.tracks.size - 1).coerceAtLeast(0))
                    }
                },
            ),
    )
}

/** Most of what is in view: a line or two stay, to keep the place. */
private fun LazyListState.pageHeight(): Float = layoutInfo.viewportSize.height * 0.85f

/** The albums, as a grid of covers. */
@Composable
internal fun AlbumGrid(
    albums: List<TrackCollection.Album>,
    onOpen: (TrackCollection) -> Unit,
    wide: Boolean,
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (wide) 168.dp else 150.dp),
        modifier = Modifier.fillMaxSize().scrollbar(gridState),
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
            CollectionCard(
                title = album.title,
                subtitle =
                    listOfNotNull(album.artist, album.year?.toString())
                        .joinToString(" · ")
                        .ifEmpty { null },
                artwork = album.artwork,
                onClick = { onOpen(album) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** The artists or the folders, as rows: on the panel on a wide window, on a card on a phone. */
@Composable
internal fun CollectionList(
    collections: List<TrackCollection>,
    onOpen: (TrackCollection) -> Unit,
    wide: Boolean,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().scrollbar(listState),
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
            CollectionRow(
                collection = collection,
                onClick = { onOpen(collection) },
                showDivider = !wide && index > 0,
                modifier =
                    Modifier.animateItem()
                        .then(
                            if (wide) Modifier
                            else Modifier.clip(shape).background(Studio.colors.panel, shape)
                        ),
            )
        }
    }
}

/** An artist or a folder: its picture or icon, its name, and how many songs and where. */
@Composable
private fun CollectionRow(
    collection: TrackCollection,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(modifier) {
        if (showDivider) StudioDivider(Modifier.padding(start = 66.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(shape)
                    .background(if (hovered) colors.hover else Color.Transparent, shape)
                    .clickable(
                        interactionSource = interactions,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
