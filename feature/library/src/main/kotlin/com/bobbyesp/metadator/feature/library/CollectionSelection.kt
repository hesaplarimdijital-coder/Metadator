/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.metadator.core.designsystem.component.GlassSurface
import com.bobbyesp.metadator.core.designsystem.component.HeldKeys
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.fadeThrough
import com.bobbyesp.metadator.core.designsystem.component.heldWhileClosing
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.hasHardwareKeyboard
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import dev.chrisbanes.haze.HazeState

/*
 * The albums, the artists and the folders are selected as the songs are: a click with Ctrl or
 * Shift held, a long press, or Select in the menu of the mouse's second button. What is selected is
 * played, or its songs edited together; albums selected are merged, which is the same: the batch
 * editor, given songs of two albums or more, offers to give them one album and one album artist.
 */

/**
 * As a song is clicked ([songClick]): Shift takes everything up to this one, Ctrl this one alone,
 * and neither opens it; while some are selected, a finger's click adds to them or takes away, and a
 * mouse's opens this one and lets the others go, as in a file manager.
 */
internal fun onCollectionClick(
    collection: TrackCollection,
    state: LibraryState,
    heldKeys: HeldKeys,
    actions: LibraryActions,
) {
    val click =
        songClick(
            shift = heldKeys.shift,
            ctrl = heldKeys.ctrl,
            mouse = heldKeys.mouse,
            selecting = state.selectingCollections,
        )
    heldKeys.clear()
    when (click) {
        SongClick.Range -> actions.onIntent(LibraryIntent.SelectCollectionsTo(collection.key))
        SongClick.Toggle -> actions.onIntent(LibraryIntent.ToggleCollection(collection.key))
        SongClick.Open -> {
            if (state.selectingCollections) {
                actions.onIntent(LibraryIntent.ClearCollectionSelection)
            }
            actions.onOpenCollection(collection)
        }
    }
}

/**
 * What an album's, an artist's or a folder's menu holds, for the mouse's second button: opening it,
 * what can be done with all its songs at once, and selecting it. On one of several selected, it is
 * also the selection's. The text is read here, once; the menu is made when it opens, for what it
 * opens on, from the library as it is then.
 */
@Composable
internal fun rememberCollectionMenu(
    state: State<LibraryState>,
    actions: LibraryActions,
): (TrackCollection, Boolean) -> List<MenuAction> {
    val onIntent = actions.onIntent
    val openLabel = stringResource(R.string.open_collection)
    val playLabel = stringResource(R.string.collection_play)
    val shuffleLabel = stringResource(R.string.collection_shuffle)
    val editAllLabel = stringResource(R.string.collection_edit_all)
    val selectLabel = stringResource(R.string.select_song)
    val deselectLabel = stringResource(R.string.deselect_song)
    val editTogetherLabel = stringResource(R.string.edit_together)
    val mergeLabel = stringResource(R.string.merge_albums)
    // A click with Ctrl held selects: said where the mouse is, which is where it is done.
    val ctrlClick =
        if (hasHardwareKeyboard()) {
            stringResource(CoreUiR.string.key_ctrl) +
                " " +
                stringResource(CoreUiR.string.mouse_click)
        } else null
    val labels =
        listOf(
            openLabel,
            playLabel,
            shuffleLabel,
            editAllLabel,
            selectLabel,
            deselectLabel,
            editTogetherLabel,
            mergeLabel,
        )
    return remember(state, actions, labels, ctrlClick) {
        { collection, selected ->
            val library = state.value
            val ofSeveral = selected && library.collectionSelection.size > 1
            listOfNotNull(
                MenuAction(openLabel, collection.icon) { actions.onOpenCollection(collection) },
                MenuAction(playLabel, Icons.Rounded.PlayArrow) {
                    onIntent(LibraryIntent.PlayCollection(collection, shuffle = false))
                },
                MenuAction(shuffleLabel, Icons.Rounded.Shuffle) {
                    onIntent(LibraryIntent.PlayCollection(collection, shuffle = true))
                },
                MenuAction(editAllLabel, Icons.Rounded.EditNote) {
                    actions.onEditTracks(collection.tracks)
                },
                MenuAction(
                    if (selected) deselectLabel else selectLabel,
                    if (selected) Icons.Rounded.Close else Icons.Rounded.Check,
                    shortcut = ctrlClick,
                ) {
                    onIntent(LibraryIntent.ToggleCollection(collection.key))
                },
                MenuAction(editTogetherLabel, Icons.Rounded.EditNote) {
                        actions.editTogether(library.selectedCollectionTracks)
                    }
                    .takeIf { ofSeveral },
                MenuAction(mergeLabel, MergeIcon) {
                        actions.editTogether(library.selectedCollectionTracks)
                    }
                    .takeIf { ofSeveral && collection is TrackCollection.Album },
            )
        }
    }
}

/**
 * The keys of the albums, the artists or the folders, as the songs have them: Ctrl A selects all of
 * them, Ctrl E or Enter edits the songs of those selected together, and Esc, or back, ends the
 * selection.
 */
@Composable
internal fun CollectionPlaceShortcuts(
    tab: LibraryTab,
    collections: List<TrackCollection>,
    state: LibraryState,
    actions: LibraryActions,
) {
    val onIntent = actions.onIntent
    val selecting = state.selectingCollections
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { onIntent(LibraryIntent.ClearCollectionSelection) },
    )
    val editSelection = { actions.editTogether(state.selectedCollectionTracks) }
    KeyboardShortcuts(
        title = stringResource(tab.label),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_A,
                    stringResource(R.string.select_all),
                    ctrl = true,
                    enabled = collections.isNotEmpty(),
                ) {
                    onIntent(LibraryIntent.SelectAllCollections)
                },
                Shortcut(
                    KeyEvent.KEYCODE_E,
                    stringResource(R.string.edit_together),
                    ctrl = true,
                    enabled = selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ENTER,
                    stringResource(R.string.edit_together),
                    enabled = selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ESCAPE,
                    stringResource(R.string.close_selection),
                    enabled = selecting,
                ) {
                    onIntent(LibraryIntent.ClearCollectionSelection)
                },
            ),
    )
}

/**
 * [head], or while albums, artists or folders are selected, how many and what can be done with
 * them, as for the songs selected.
 *
 * @param wide whether it is the panel's header, which holds what can be done with them; on a phone
 *   the head says how many, and that floats over the list ([CollectionSelectionBar])
 */
@Composable
internal fun CollectionSelectionHead(
    state: LibraryState,
    actions: LibraryActions,
    wide: Boolean,
    head: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = state.selectingCollections,
        transitionSpec = { fadeThrough() },
        label = "CollectionSelectionHead",
    ) { selecting ->
        when {
            !selecting -> head()
            wide -> PanelCollectionSelection(state, actions)
            else -> PhoneCollectionSelectionHead(state, actions.onIntent)
        }
    }
}

/** The panel's header while albums, artists or folders are selected. */
@Composable
private fun PanelCollectionSelection(state: LibraryState, actions: LibraryActions) {
    val onIntent = actions.onIntent
    // What it said while the selection was there, as it fades: not "0 selected".
    val shown = heldWhileClosing(state.selectingCollections, state.shownSelection)
    val albums = shown.tab == LibraryTab.Albums
    val editTogether = { actions.editTogether(state.selectedCollectionTracks) }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            selectedCollectionsText(shown),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = Studio.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (albums) {
            StudioButton(
                text = stringResource(R.string.merge_albums),
                onClick = editTogether,
                icon = MergeIcon,
                style = StudioButtonStyle.Primary,
                enabled = shown.count > 1,
            )
        }
        StudioButton(
            text = stringResource(R.string.edit_together),
            onClick = editTogether,
            icon = Icons.Rounded.EditNote,
            style = if (albums) StudioButtonStyle.Secondary else StudioButtonStyle.Primary,
            shortcut = shortcutHint(KeyEvent.KEYCODE_E, ctrl = true),
        )
        StudioButton(
            text = stringResource(R.string.play_selection),
            onClick = { onIntent(LibraryIntent.PlayCollectionSelection) },
            icon = Icons.Rounded.PlayArrow,
        )
        StudioButton(
            text = stringResource(R.string.close_selection),
            onClick = { onIntent(LibraryIntent.ClearCollectionSelection) },
            style = StudioButtonStyle.Ghost,
            shortcut = shortcutHint(KeyEvent.KEYCODE_ESCAPE),
        )
    }
}

/** The head of the phone's library while albums, artists or folders are selected. */
@Composable
private fun PhoneCollectionSelectionHead(state: LibraryState, onIntent: (LibraryIntent) -> Unit) {
    val shown = heldWhileClosing(state.selectingCollections, state.shownSelection)
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.close_selection),
            onClick = { onIntent(LibraryIntent.ClearCollectionSelection) },
            size = 44.dp,
            iconSize = 22.dp,
        )
        Text(
            selectedCollectionsText(shown),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = Studio.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StudioIconButton(
            icon = Icons.Rounded.SelectAll,
            contentDescription = stringResource(R.string.select_all),
            onClick = { onIntent(LibraryIntent.SelectAllCollections) },
            size = 44.dp,
            iconSize = 22.dp,
        )
    }
}

/**
 * What to do with the albums, artists or folders selected on a phone, on a pane of glass over the
 * list, where the songs selected have theirs. Albums can be merged as well: the bar has room for
 * that beside the other two only with those as icons.
 *
 * @param glass the list beneath, recorded for the glass to blur
 */
@Composable
internal fun CollectionSelectionBar(
    state: LibraryState,
    actions: LibraryActions,
    glass: HazeState,
    modifier: Modifier = Modifier,
) {
    val onIntent = actions.onIntent
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state.selectingCollections,
        enter =
            slideInVertically(motion.defaultSpatialSpec()) { it } +
                fadeIn(motion.defaultEffectsSpec()),
        exit =
            slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        modifier =
            modifier
                .navigationBarsPadding()
                // Where the songs' bar is: above the mini player when one is showing.
                .padding(bottom = if (state.playerActive) 96.dp else Spacing.large),
    ) {
        val shown = heldWhileClosing(state.selectingCollections, state.shownSelection)
        val editTogether = { actions.editTogether(state.selectedCollectionTracks) }
        val play = { onIntent(LibraryIntent.PlayCollectionSelection) }
        // The buttons' corners are the bar's, less its padding: the two curves stay parallel.
        GlassSurface(backdrop = glass, shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shown.tab == LibraryTab.Albums) {
                    StudioIconButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play_selection),
                        onClick = play,
                        size = 44.dp,
                        iconSize = 22.dp,
                    )
                    StudioIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit_together),
                        onClick = editTogether,
                        size = 44.dp,
                        iconSize = 22.dp,
                    )
                    StudioButton(
                        text = stringResource(R.string.merge_albums),
                        onClick = editTogether,
                        icon = MergeIcon,
                        style = StudioButtonStyle.Primary,
                        enabled = shown.count > 1,
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                } else {
                    StudioButton(
                        text = stringResource(R.string.play_selection),
                        onClick = play,
                        icon = Icons.Rounded.PlayArrow,
                        style = StudioButtonStyle.Ghost,
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                    StudioButton(
                        text = stringResource(R.string.edit_together),
                        onClick = editTogether,
                        icon = Icons.Rounded.Edit,
                        style = StudioButtonStyle.Primary,
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                }
            }
        }
    }
}

/**
 * A check over an artist's picture or a folder's icon while it is selected, as over a song's cover:
 * the thing the finger is on is the thing that answers.
 */
@Composable
internal fun SelectedCheck(selected: Boolean, size: Dp) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = selected,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(Studio.colors.accentFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Studio.colors.onAccentFill,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** How many of what are selected, and of which place: what the selection's head says. */
private data class ShownSelection(val tab: LibraryTab, val count: Int)

private val LibraryState.shownSelection: ShownSelection
    get() = ShownSelection(tab, collectionSelection.size)

@Composable
private fun selectedCollectionsText(shown: ShownSelection): String =
    pluralStringResource(
        when (shown.tab) {
            LibraryTab.Artists -> R.plurals.selected_artists
            LibraryTab.Folders -> R.plurals.selected_folders
            else -> R.plurals.selected_albums
        },
        shown.count,
        shown.count,
    )

/**
 * Edits [tracks] together in the batch editor, which offers to merge them into one album when they
 * are of several. Nothing is opened for none: a selection just ended (a batch saved, the bar on its
 * way out) is still pressed now and then.
 */
private fun LibraryActions.editTogether(tracks: List<Track>) {
    if (tracks.isNotEmpty()) onEditTracks(tracks)
}

/** Two paths becoming one: what merging albums is drawn with. */
private val MergeIcon: ImageVector
    get() = Icons.AutoMirrored.Rounded.MergeType

/** What an album, an artist or a folder is drawn with where it is opened. */
private val TrackCollection.icon: ImageVector
    get() =
        when (this) {
            is TrackCollection.Album -> Icons.Rounded.Album
            is TrackCollection.Artist -> Icons.Rounded.Person
            is TrackCollection.Folder -> Icons.Rounded.FolderOpen
        }
