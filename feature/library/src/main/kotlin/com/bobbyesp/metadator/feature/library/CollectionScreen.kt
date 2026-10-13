/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.component.ContextMenuArea
import com.bobbyesp.metadator.core.designsystem.component.LoadingScreen
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.quality.LibraryQualityState
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.LocalShownDetail
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.component.TrackColumns
import com.bobbyesp.metadator.core.ui.component.TrackRow
import com.bobbyesp.metadator.core.ui.component.TrackTableRow

/**
 * The songs of one album, artist or folder, with what to do with all of them at once: its cover and
 * its name over them, and the songs as the library lists them, a table on a wide window.
 */
@Composable
internal fun CollectionScreen(
    title: String,
    collection: TrackCollection?,
    quality: LibraryQualityState,
    playingTrack: Track?,
    isPlaybackRunning: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenTrack: (Track) -> Unit,
    onOpenInNewWindow: (Track) -> Unit,
    onPlay: (tracks: List<Track>, index: Int, shuffle: Boolean) -> Unit,
    onEditAll: (List<Track>) -> Unit,
) {
    val panels = LocalPanels.current
    val activeUri = (LocalShownDetail.current as? Editor)?.uri
    val editLabel = stringResource(R.string.edit_song)
    val playLabel = stringResource(R.string.play_song)
    val newWindowLabel = stringResource(R.string.open_in_new_window)
    val menu = { track: Track, index: Int, tracks: List<Track> ->
        listOf(
            MenuAction(editLabel, Icons.Rounded.Edit) { onOpenTrack(track) },
            MenuAction(playLabel, Icons.Rounded.PlayArrow) { onPlay(tracks, index, false) },
            MenuAction(newWindowLabel, Icons.AutoMirrored.Rounded.OpenInNew) {
                onOpenInNewWindow(track)
            },
        )
    }
    Column(
        Modifier.fillMaxSize()
            .then(if (panels) Modifier.studioPanel() else Modifier.statusBarsPadding())
    ) {
        if (showBack) {
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
        if (collection == null) {
            LoadingScreen()
            return@Column
        }
        val tracks = collection.tracks
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val table = panels && maxWidth >= TrackColumns.MinWidth
            val columns = TrackColumns.forWidth(maxWidth - 16.dp)
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollbar(listState),
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = if (table) 8.dp else Spacing.screen,
                        end = if (table) 8.dp else Spacing.screen,
                        bottom = if (panels) 8.dp else Spacing.floatingClearance,
                    ),
            ) {
                item(key = "header") {
                    CollectionHeader(
                        title = title,
                        collection = collection,
                        onPlay = { onPlay(tracks, 0, false) },
                        onShuffle = { onPlay(tracks, 0, true) },
                        onEditAll = { onEditAll(tracks) },
                    )
                }
                itemsIndexed(tracks, key = { _, it -> it.id.value }) { index, track ->
                    val shape = cardRowShape(index, tracks.size)
                    ContextMenuArea(
                        actions = { menu(track, index, tracks) },
                        modifier =
                            if (table) Modifier
                            else Modifier.clip(shape).background(Studio.colors.panel, shape),
                    ) {
                        if (table) {
                            TrackTableRow(
                                track = track,
                                columns = columns,
                                onClick = { onOpenTrack(track) },
                                onToggle = { onOpenTrack(track) },
                                quality = quality.of(track),
                                active = track.ref.uri == activeUri,
                                isPlaying = track.id == playingTrack?.id,
                                isPlaybackRunning = isPlaybackRunning,
                            )
                        } else {
                            TrackRow(
                                track = track,
                                onClick = { onOpenTrack(track) },
                                onLongClick = { onOpenTrack(track) },
                                quality = quality.of(track),
                                active = track.ref.uri == activeUri,
                                isPlaying = track.id == playingTrack?.id,
                                isPlaybackRunning = isPlaybackRunning,
                                showDivider = index > 0,
                            )
                        }
                    }
                }
            }
        }
    }
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
