/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.common.formatDuration
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.player.api.PlaybackState

/** Where the times of a [SeekLine] go. */
internal enum class SeekTimes {
    /** Either side of the line, as a bar has room for. */
    Beside,

    /** Under the line, at its ends, as a player with the width to spare sets them. */
    Below,

    /** Nowhere: a bar with the controls beside the line has no room left for them. */
    None,
}

internal object SeekLineDefaults {
    /** The line itself, in both players. */
    val Thickness: Dp = 4.dp

    /** The knob that shows while the pointer is over the line or a finger has it. */
    val KnobRadius: Dp = 6.dp

    /** How tall the line is to a pointer in a bar, where it lies between two rows of text. */
    val BarHitHeight: Dp = 16.dp

    /** How tall it is to a finger in the full player, where there is room around it. */
    val TouchHitHeight: Dp = 24.dp
}

/**
 * Where the song is, as a desktop's player shows it: a thin line, which a click or a drag moves and
 * the arrow keys once it has the focus, with the times at its ends. Its knob shows while the
 * pointer is over it or a finger has it. The same line in the bar docked under the panels and in
 * the full player, at the size each has room for.
 *
 * It reads [position] itself, as the player reports it, twice a second: plenty for a line this
 * thin, and only this is composed again for it.
 *
 * @param times where the times go: beside the line, under it, or nowhere
 * @param hitHeight how tall the line is to a finger or a pointer, the line itself included
 */
@Composable
internal fun SeekLine(
    playback: PlaybackState,
    position: PlaybackPosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    times: SeekTimes = SeekTimes.Beside,
    hitHeight: Dp = SeekLineDefaults.BarHitHeight,
) {
    val positionMs = position.positionMs
    val duration = playback.durationMs
    // While dragged, where the finger or the pointer is, not where the song is.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction =
        dragged ?: if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val elapsed = formatDuration((fraction * duration).toLong())
    val total = formatDuration(duration)
    when (times) {
        SeekTimes.Beside ->
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeText(elapsed, MaterialTheme.typography.labelSmall)
                Line(
                    fraction = fraction,
                    positionMs = positionMs,
                    duration = duration,
                    dragging = dragged != null,
                    onDrag = { dragged = it },
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f).height(hitHeight),
                )
                TimeText(total, MaterialTheme.typography.labelSmall)
            }
        SeekTimes.Below ->
            Column(modifier) {
                Line(
                    fraction = fraction,
                    positionMs = positionMs,
                    duration = duration,
                    dragging = dragged != null,
                    onDrag = { dragged = it },
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth().height(hitHeight),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeText(elapsed, MaterialTheme.typography.labelMedium)
                    TimeText(total, MaterialTheme.typography.labelMedium)
                }
            }
        SeekTimes.None ->
            Line(
                fraction = fraction,
                positionMs = positionMs,
                duration = duration,
                dragging = dragged != null,
                onDrag = { dragged = it },
                onSeek = onSeek,
                modifier = modifier.height(hitHeight),
            )
    }
}

/**
 * The line: the played part of it in the text color on a hairline rail, and the knob at its end.
 *
 * @param dragging whether a finger or the pointer has it, which is when the knob shows too
 * @param onDrag told where a drag has it, and null when it lets go
 */
@Composable
private fun Line(
    fraction: Float,
    positionMs: Long,
    duration: Long,
    dragging: Boolean,
    onDrag: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    // The gestures are keyed on the duration alone, so they read the seek they are given now.
    val seek by rememberUpdatedState(onSeek)
    val positionLabel = stringResource(R.string.seek_position)
    Box(
        modifier =
            modifier
                .hoverable(interactions)
                .semantics {
                    contentDescription = positionLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        seek((target.coerceIn(0f, 1f) * duration).toLong())
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
                    seek((positionMs + step).coerceIn(0, duration))
                    true
                }
                .focusable(interactionSource = interactions)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        seek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    // Where the drag has got to, kept here: what is composed with it is a frame
                    // behind.
                    var at = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            at = (it.x / size.width).coerceIn(0f, 1f)
                            onDrag(at)
                        },
                        onDragEnd = {
                            seek((at * duration).toLong())
                            onDrag(null)
                        },
                        onDragCancel = { onDrag(null) },
                    ) { change, _ ->
                        at = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDrag(at)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val rail = colors.lineStrong
        val played = colors.text
        val knob = hovered || dragging
        Canvas(Modifier.fillMaxSize()) {
            val thickness = SeekLineDefaults.Thickness.toPx()
            val top = (size.height - thickness) / 2
            val radius = CornerRadius(thickness / 2)
            drawRoundRect(rail, Offset(0f, top), Size(size.width, thickness), radius)
            drawRoundRect(played, Offset(0f, top), Size(size.width * fraction, thickness), radius)
            if (knob) {
                drawCircle(
                    played,
                    SeekLineDefaults.KnobRadius.toPx(),
                    Offset(size.width * fraction, size.height / 2),
                )
            }
        }
    }
}

@Composable
private fun TimeText(text: String, style: TextStyle) {
    Text(text, style = style, color = Studio.colors.textTertiary, maxLines = 1)
}

/** How far an arrow key moves the song, as a desktop player's does. */
private const val SEEK_STEP_MS = 5_000L
