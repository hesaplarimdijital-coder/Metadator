/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.common.formatDuration
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.dissolved
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController
import com.bobbyesp.metadator.player.api.RepeatMode

/**
 * The player as a desktop's bar, docked under the panels of a wide window: the song on the left,
 * the controls and where it is in the middle, the way to its lyrics and to the full player on the
 * right. It is the collapsed content of [PlayerSheet] there, as [MiniPlayerBar] is on a phone, and
 * shares the same pieces with the full player.
 *
 * @param ownContentPresence how much of what only the bar has is there, from 0 to 1, read while
 *   drawing so that a transition does not recompose the bar
 */
@Composable
internal fun DockedPlayerBar(
    track: Track,
    playback: PlaybackState,
    player: PlayerController,
    onOpen: () -> Unit,
    shape: Shape,
    artworkShape: Shape,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    ownContentPresence: () -> Float = { 1f },
) {
    val colors = Studio.colors
    Surface(
        shape = shape,
        color = colors.sidebar,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth().then(modifier),
    ) {
        Row(
            modifier =
                contentModifier
                    .fillMaxWidth()
                    .heightIn(min = DockedBarHeight)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(track, artworkShape, Modifier.size(42.dp).then(artworkModifier))
                Column(Modifier.weight(1f)) {
                    TrackLine(track, titleModifier) { shown ->
                        Text(
                            shown.title,
                            style = TrackNameDefaults.barTitleStyle,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TrackLine(track, artistModifier, follows = true) { shown ->
                        Text(
                            shown.artist ?: stringResource(R.string.unknown_artist),
                            style = TrackNameDefaults.barArtistStyle,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudioIconButton(
                        icon = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        onClick = { player.setShuffle(!playback.shuffle) },
                        tint = if (playback.shuffle) colors.accent else colors.textSecondary,
                        modifier = Modifier.dissolved(ownContentPresence),
                    )
                    StudioIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        onClick = player::skipToPrevious,
                        tint = colors.text,
                        iconSize = 22.dp,
                        modifier = Modifier.dissolved(ownContentPresence),
                    )
                    PlayPauseButton(
                        isPlaying = playback.isPlaying,
                        onToggle = player::togglePlayPause,
                        containerColor = colors.text,
                        contentColor = colors.ground,
                        // The sheet's modifier first: it is what sizes the button on the way out.
                        modifier = playButtonModifier.size(38.dp),
                    )
                    StudioIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        onClick = player::skipToNext,
                        enabled = playback.hasNext,
                        tint = colors.text,
                        iconSize = 22.dp,
                        modifier = Modifier.dissolved(ownContentPresence),
                    )
                    StudioIconButton(
                        icon =
                            if (playback.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne
                            else Icons.Rounded.Repeat,
                        contentDescription =
                            stringResource(
                                when (playback.repeatMode) {
                                    RepeatMode.Off -> R.string.repeat_off
                                    RepeatMode.All -> R.string.repeat_all
                                    RepeatMode.One -> R.string.repeat_one
                                }
                            ),
                        onClick = { player.setRepeatMode(playback.repeatMode.next()) },
                        tint =
                            if (playback.repeatMode != RepeatMode.Off) colors.accent
                            else colors.textSecondary,
                        modifier = Modifier.dissolved(ownContentPresence),
                    )
                }
                DockedSeekBar(
                    playback = playback,
                    onSeek = player::seekTo,
                    modifier =
                        Modifier.widthIn(max = 520.dp).fillMaxWidth().dissolved(ownContentPresence),
                )
            }

            Row(
                modifier = Modifier.weight(1f).dissolved(ownContentPresence),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioButton(
                    text = stringResource(R.string.lyrics),
                    onClick = onOpen,
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    style = StudioButtonStyle.Ghost,
                )
                StudioIconButton(
                    icon = Icons.Rounded.OpenInFull,
                    contentDescription = stringResource(R.string.open_player),
                    onClick = onOpen,
                    iconSize = 16.dp,
                )
            }
        }
    }
}

/** The bar's height, which the shell keeps clear under the panels. */
internal val DockedBarHeight = 64.dp

/**
 * Where the song is, as a desktop's player bar shows it: the times either side of a thin line,
 * which a click or a drag moves, and the arrow keys once it has the focus. Its knob shows while the
 * pointer is over it.
 */
@Composable
private fun DockedSeekBar(
    playback: PlaybackState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    // As the player reports it, twice a second: plenty for a line this thin, and nothing on screen
    // has to be drawn again on every frame for it.
    val position = playback.positionMs
    val duration = playback.durationMs
    // While dragged, where the finger or the pointer is, not where the song is.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val fraction =
        dragged ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val positionLabel = stringResource(R.string.seek_position)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeText(formatDuration((fraction * duration).toLong()))
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(16.dp)
                    .hoverable(interactions)
                    .semantics {
                        contentDescription = positionLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { target ->
                            onSeek((target.coerceIn(0f, 1f) * duration).toLong())
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || duration <= 0) {
                            return@onKeyEvent false
                        }
                        val step =
                            when (event.key) {
                                Key.DirectionLeft -> -SEEK_STEP_MS
                                Key.DirectionRight -> SEEK_STEP_MS
                                else -> return@onKeyEvent false
                            }
                        onSeek((position + step).coerceIn(0, duration))
                        true
                    }
                    .focusable(interactionSource = interactions)
                    .pointerInput(duration) {
                        if (duration <= 0) return@pointerInput
                        detectTapGestures { offset ->
                            onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                        }
                    }
                    .pointerInput(duration) {
                        if (duration <= 0) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = (it.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                dragged?.let { onSeek((it * duration).toLong()) }
                                dragged = null
                            },
                            onDragCancel = { dragged = null },
                        ) { change, _ ->
                            dragged = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            val track = colors.lineStrong
            val played = colors.text
            val knob = hovered || dragged != null
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val thickness = 4.dp.toPx()
                val top = (size.height - thickness) / 2
                val radius = CornerRadius(thickness / 2)
                drawRoundRect(track, Offset(0f, top), Size(size.width, thickness), radius)
                drawRoundRect(
                    played,
                    Offset(0f, top),
                    Size(size.width * fraction, thickness),
                    radius,
                )
                if (knob) {
                    drawCircle(played, 6.dp.toPx(), Offset(size.width * fraction, size.height / 2))
                }
            }
        }
        TimeText(formatDuration(duration))
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Studio.colors.textTertiary,
        maxLines = 1,
    )
}

/** How far an arrow key moves the song, as a desktop player's does. */
private const val SEEK_STEP_MS = 5_000L
