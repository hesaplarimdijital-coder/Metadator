/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.Choice
import com.bobbyesp.metadator.core.designsystem.component.ConnectedChoices
import com.bobbyesp.metadator.core.designsystem.component.GlassSurface
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.dissolved
import com.bobbyesp.metadator.core.domain.lyrics.TrackLyrics
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The player, full screen, where the window is wider than it is tall: the song on the left, and on
 * the right its lyrics or what plays next. Like [NowPlayingContent] it is the expanded content of
 * [PlayerSheet], and each of the modifiers the sheet ties the bar's pieces with goes on exactly one
 * element here too.
 *
 * @param position where the song is, which only the seek line and the lyrics read
 * @param compact whether the controls do not fit under the cover and float over the lyrics instead
 * @param settled whether the sheet is at rest, open. Only then is anything glass: while the sheet
 *   moves its content is drawn in the transition's overlay, where a frosted surface flickers. And
 *   only then does the album show.
 * @param ownContentPresence how much of what only this has is there, as in [NowPlayingContent]
 */
@Composable
internal fun NowPlayingWideContent(
    track: Track,
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    lyrics: TrackLyrics?,
    compact: Boolean,
    settled: Boolean,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBarModifier: Modifier = Modifier,
    artworkShape: Shape = MaterialTheme.shapes.extraLarge,
    artworkModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    ownContentPresence: () -> Float = { 1f },
) {
    // What the floating controls are glass over: the backdrop and the lyrics. Both are recorded
    // here as siblings of the controls, never as their ancestors, or the controls would blur
    // themselves.
    val haze = rememberHazeState()
    val colors = Studio.colors
    Surface(modifier = modifier.fillMaxSize(), color = colors.ground, contentColor = colors.text) {
        Box(Modifier.fillMaxSize()) {
            PlayerBackdrop(track, Modifier.matchParentSize().hazeSource(haze))
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        // Over the backdrop, which is the surface and stays as it is.
                        .dissolved(ownContentPresence)
                        .safeDrawingPadding()
                        .padding(horizontal = Spacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
            ) {
                SongColumn(
                    track = track,
                    playback = playback,
                    position = position,
                    player = player,
                    withControls = !compact,
                    settled = settled,
                    onClose = onClose,
                    onEdit = { onEdit(track.ref.uri) },
                    modifier = Modifier.weight(SONG_COLUMN_WEIGHT).fillMaxHeight(),
                    topBarModifier = topBarModifier,
                    artworkShape = artworkShape,
                    artworkModifier = artworkModifier,
                    titleModifier = titleModifier,
                    artistModifier = artistModifier,
                    playButtonModifier = playButtonModifier,
                )
                SidePanel(
                    playback = playback,
                    position = position,
                    player = player,
                    lyrics = lyrics,
                    compact = compact,
                    settled = settled,
                    haze = haze,
                    onEdit = { onEdit(track.ref.uri) },
                    playButtonModifier = playButtonModifier,
                    modifier = Modifier.weight(1f - SONG_COLUMN_WEIGHT).fillMaxHeight(),
                )
            }
        }
    }
}

/** The song: its cover and its name, and under them its controls where they fit. */
@Composable
private fun SongColumn(
    track: Track,
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    withControls: Boolean,
    settled: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier,
    topBarModifier: Modifier,
    artworkShape: Shape,
    artworkModifier: Modifier,
    titleModifier: Modifier,
    artistModifier: Modifier,
    playButtonModifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The whole row is the handle the sheet is dragged down by, as it is when stacked.
        PlayerTopRow(onClose = onClose, onEdit = onEdit, modifier = topBarModifier)
        Column(
            modifier =
                Modifier.weight(1f)
                    .widthIn(max = SongColumnMaxWidth)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large, Alignment.CenterVertically),
        ) {
            // The cover takes what height the rest leaves, and is as wide as it is tall.
            Cover(
                track = track,
                shape = artworkShape,
                sharedModifier = artworkModifier,
                modifier = Modifier.weight(1f, fill = false).aspectRatio(1f),
            )
            TrackName(track, settled, titleModifier, artistModifier)
            if (withControls) {
                SeekLine(
                    playback = playback,
                    position = position,
                    onSeek = player::seekTo,
                    modifier = Modifier.fillMaxWidth(),
                    times = SeekTimes.Below,
                    hitHeight = SeekLineDefaults.TouchHitHeight,
                )
                TransportControls(
                    playback = playback,
                    player = player,
                    playButtonModifier = playButtonModifier,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private enum class SidePane {
    Lyrics,
    UpNext,
}

/** The lyrics or the queue, whichever is chosen, and over them the controls when [compact]. */
// The play button is tied to the bar's by a modifier of its own, before the panel's.
@Suppress("ModifierParameter")
@Composable
private fun SidePanel(
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    lyrics: TrackLyrics?,
    compact: Boolean,
    settled: Boolean,
    haze: HazeState,
    onEdit: () -> Unit,
    playButtonModifier: Modifier,
    modifier: Modifier,
) {
    var pane by rememberSaveable { mutableStateOf(SidePane.Lyrics) }
    val motion = MaterialTheme.motionScheme
    // What scrolls passes beneath the floating controls, padded to clear them at its end.
    val bottomClearance = if (compact) FloatingControlsClearance else 0.dp
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            // As tall as the song column's top row, so the two read as one line across the top.
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = PlayerTopRowHeight)
                        .padding(vertical = Spacing.small),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectedChoices(
                    choices =
                        listOf(
                            Choice(
                                SidePane.Lyrics,
                                stringResource(R.string.lyrics),
                                Icons.Rounded.Lyrics,
                            ),
                            Choice(
                                SidePane.UpNext,
                                stringResource(R.string.up_next),
                                Icons.AutoMirrored.Rounded.QueueMusic,
                            ),
                        ),
                    selected = pane,
                    onSelect = { pane = it },
                )
            }
            AnimatedContent(
                targetState = pane,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                modifier = Modifier.weight(1f).fillMaxWidth().hazeSource(haze, zIndex = 1f),
                label = "SidePane",
            ) { shown ->
                when (shown) {
                    SidePane.Lyrics ->
                        LyricsPane(
                            lyrics = lyrics,
                            playback = playback,
                            position = position,
                            onSeek = player::seekTo,
                            onEdit = onEdit,
                            compact = compact,
                            bottomClearance = bottomClearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                    SidePane.UpNext -> UpNextPane(playback, player, bottomClearance)
                }
            }
        }
        if (compact) {
            FloatingControls(
                playback = playback,
                position = position,
                player = player,
                glass = haze.takeIf { settled },
                playButtonModifier = playButtonModifier,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.medium),
            )
        }
    }
}

/** The queue as the library lists songs, on a card; or that there is none. */
@Composable
private fun UpNextPane(playback: PlaybackState, player: PlayerController, bottomClearance: Dp) {
    if (!playback.hasUpNext) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomClearance), Alignment.Center) {
            Text(
                stringResource(R.string.queue_empty),
                style = MaterialTheme.typography.titleMedium,
                color = Studio.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().scrollbar(listState),
        contentPadding =
            PaddingValues(top = Spacing.small, bottom = Spacing.extraLarge + bottomClearance),
    ) {
        upNextRows(playback, player)
    }
}

/**
 * The controls as one bar floating over the lyrics, as glass: what is beneath shows through it,
 * blurred. For where there is no height to put them under the cover.
 *
 * @param glass what to be glass over; null while the sheet moves, when it is its tone, solid
 */
// The play button is tied to the bar's by a modifier of its own, before the panel's.
@Suppress("ModifierParameter")
@Composable
private fun FloatingControls(
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    glass: HazeState?,
    playButtonModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        backdrop = glass,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportControls(
                playback = playback,
                player = player,
                playButtonModifier = playButtonModifier,
                sizes = TransportSizes.Floating,
            )
            // No times: on a phone on its side, five buttons leave the line little enough.
            SeekLine(
                playback = playback,
                position = position,
                onSeek = player::seekTo,
                modifier = Modifier.weight(1f).padding(end = Spacing.small),
                times = SeekTimes.None,
                hitHeight = SeekLineDefaults.TouchHitHeight,
            )
        }
    }
}

private const val SONG_COLUMN_WEIGHT = 0.42f
private val SongColumnMaxWidth = 440.dp

/** The room the floating controls take at the bottom of what scrolls beneath them. */
private val FloatingControlsClearance = 80.dp
