/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.metadator.core.designsystem.component.LoadingScreen
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioDefaults
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The songs of one album, artist or folder, with what to do with all of them at once: its cover and
 * its name over them, and the songs as the library lists them, a table on a wide window. They are
 * selected as the library's are, with the same bar for what to do with them: under the name on a
 * wide window, where it stays once the name has scrolled away; floating over the songs on a phone.
 *
 * @param state the library narrowed to this collection ([LibraryIntent.ShowCollection])
 * @param onBack leaves the collection with what was opened beside it
 */
@Composable
internal fun CollectionScreen(
    title: String,
    collection: TrackCollection?,
    state: LibraryState,
    actions: LibraryActions,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val panels = LocalPanels.current
    val onIntent = actions.onIntent
    // Back ends the selection first, as in the library.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.selecting,
        onBackCompleted = { onIntent(LibraryIntent.ClearSelection) },
    )
    CollectionShortcuts(title, state, actions, onBack)
    // The songs, recorded for the selection's bar that floats over them on a phone.
    val haze = rememberHazeState()
    val motion = MaterialTheme.motionScheme
    Box(
        Modifier.fillMaxSize()
            .then(if (panels) Modifier.studioPanel() else Modifier.statusBarsPadding())
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.selecting && !panels,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                label = "CollectionHead",
            ) { selecting ->
                when {
                    selecting -> PhoneSelectionHead(state, onIntent)
                    showBack -> BackRow(panels, onBack)
                    else -> Box(Modifier.fillMaxWidth())
                }
            }
            if (collection == null) {
                LoadingScreen()
                return@Column
            }
            val header: @Composable () -> Unit = {
                CollectionHeader(
                    title = title,
                    collection = collection,
                    onPlay = { onIntent(LibraryIntent.PlayAll(shuffle = false)) },
                    onShuffle = { onIntent(LibraryIntent.PlayAll(shuffle = true)) },
                    onEditAll = { actions.onEditTracks(collection.tracks) },
                )
            }
            Box(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .then(if (panels) Modifier else Modifier.hazeSource(haze))
            ) {
                if (panels) {
                    SongsTable(
                        tracks = state.tracks,
                        state = state,
                        actions = actions,
                        header = header,
                        pinned = { SelectionStrip(state, actions) },
                        sortable = false,
                    )
                } else {
                    SongCards(state.tracks, state, actions, header = header)
                }
            }
        }
        if (!panels) {
            AnimatedVisibility(
                visible = state.selecting,
                enter =
                    slideInVertically(motion.defaultSpatialSpec()) { it } +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    slideOutVertically(motion.fastSpatialSpec()) { it } +
                        fadeOut(motion.fastEffectsSpec()),
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        // Above the mini player when one is showing, as in the library.
                        .padding(
                            bottom =
                                if (state.playerActive) StudioDefaults.FloatingBarClearance
                                else Spacing.large
                        ),
            ) {
                SelectionBar(state, actions, onIntent, glass = haze)
            }
        }
    }
}

@Composable
private fun BackRow(panels: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            size = if (panels) 36.dp else 44.dp,
            iconSize = 22.dp,
        )
    }
}

/**
 * What to do with the songs selected, under the collection's name on a wide window while some are:
 * the library's own bar over its songs.
 */
@Composable
private fun SelectionStrip(state: LibraryState, actions: LibraryActions) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state.selecting,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionActions(state, actions)
        }
    }
}

/**
 * What a collection's songs do from the keyboard, as the library's do: Ctrl+A selects them all,
 * Ctrl+E and Enter edit the selection together, Esc ends it; with nothing selected, Esc leaves the
 * collection, as its arrow does. An Esc nobody takes was left to Android to make a Back of, and one
 * injected from a keyboard it does not know was never made one: on the emulator two presses left
 * the album open.
 */
@Composable
private fun CollectionShortcuts(
    title: String,
    state: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
) {
    val onIntent = actions.onIntent
    val editSelection = { actions.onEditTracks(state.selectedTracks) }
    KeyboardShortcuts(
        title = title,
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_A,
                    stringResource(R.string.select_all),
                    ctrl = true,
                    enabled = state.tracks.isNotEmpty(),
                ) {
                    onIntent(LibraryIntent.SelectAll)
                },
                Shortcut(
                    KeyEvent.KEYCODE_E,
                    stringResource(R.string.edit_together),
                    ctrl = true,
                    enabled = state.selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ENTER,
                    stringResource(R.string.edit_together),
                    enabled = state.selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ESCAPE,
                    stringResource(
                        if (state.selecting) R.string.close_selection else R.string.back
                    ),
                ) {
                    if (state.selecting) onIntent(LibraryIntent.ClearSelection) else onBack()
                },
            ),
    )
}

/** The collection's cover and name, how many songs it has, and what to do with all of them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionHeader(
    title: String,
    collection: TrackCollection,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onEditAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (collection !is TrackCollection.Folder) {
            ArtworkImage(
                model = collection.artwork,
                contentDescription = null,
                modifier = Modifier.size(132.dp),
                shape = RoundedCornerShape(14.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                        ),
                    color = Studio.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    collectionDetails(collection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Studio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudioButton(
                    text = stringResource(R.string.collection_play),
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                    style = StudioButtonStyle.Primary,
                )
                StudioButton(
                    text = stringResource(R.string.collection_shuffle),
                    onClick = onShuffle,
                    icon = Icons.Rounded.Shuffle,
                )
                StudioButton(
                    text = stringResource(R.string.collection_edit_all),
                    onClick = onEditAll,
                    icon = Icons.Rounded.EditNote,
                )
            }
        }
    }
}

/**
 * How many songs [collection] has and, for a folder, where it is: two folders can have the same
 * name, on the device and on an SD card, or as the "CD1" of two albums. Shortened in the middle,
 * where neither the count nor the folder it is in is.
 */
@Composable
internal fun collectionDetails(collection: TrackCollection): String {
    val count =
        pluralStringResource(R.plurals.song_count, collection.tracks.size, collection.tracks.size)
    val parent = (collection as? TrackCollection.Folder)?.parent?.ifEmpty { null }
    return listOfNotNull(count, parent).joinToString(" · ")
}
