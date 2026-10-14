/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.common.formatDuration
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController

internal val PlaybackState.hasUpNext: Boolean
    get() = currentIndex < queue.lastIndex

/**
 * What plays after the current song, as rows on one card, the way the library lists songs on a
 * phone: each with its cover, its name and where it is from, and how long it is, a hairline between
 * them. Tapping one jumps to it.
 *
 * @param itemModifier on every row: where the list is wider than the rows should be
 */
internal fun LazyListScope.upNextRows(
    playback: PlaybackState,
    player: PlayerController,
    itemModifier: Modifier = Modifier,
) {
    val upNext = playback.queue.drop(playback.currentIndex + 1).take(MAX_UP_NEXT)
    itemsIndexed(upNext, key = { index, it -> "${it.id.value}:$index" }) { index, next ->
        val shape = cardRowShape(index, upNext.size)
        UpNextRow(
            track = next,
            onClick = { player.skipToQueueItem(playback.currentIndex + 1 + index) },
            showDivider = index > 0,
            modifier = itemModifier.clip(shape).background(Studio.colors.panel, shape),
        )
    }
}

/** The corners of the row at [index] of a card of [count]: the card's own at its ends. */
@Composable
private fun cardRowShape(index: Int, count: Int): Shape {
    val corner = MaterialTheme.shapes.extraLarge.topStart
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) corner else ZeroCornerSize,
        topEnd = if (first) corner else ZeroCornerSize,
        bottomStart = if (last) corner else ZeroCornerSize,
        bottomEnd = if (last) corner else ZeroCornerSize,
    )
}

/**
 * One song of the queue, laid out as the library's rows are. It draws no card of its own: it lies
 * on the card of the list, with a hairline above it unless it is the first. The row the pointer is
 * on is lit, as a desktop's rows are.
 */
@Composable
private fun UpNextRow(
    track: Track,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val tint by
        animateColorAsState(
            targetValue = if (hovered) colors.hover else Color.Transparent,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "UpNextRowTint",
        )
    val artist = track.artist ?: stringResource(R.string.unknown_artist)
    Column(modifier) {
        if (showDivider) StudioDivider(Modifier.padding(start = RowDividerInset))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = RowMinHeight)
                    .background(tint)
                    .clickable(
                        interactionSource = interactions,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(horizontal = RowSidePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                model = track.artwork,
                contentDescription = null,
                modifier = Modifier.size(RowCoverSize),
                shape = RoundedCornerShape(9.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(artist, track.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

private const val MAX_UP_NEXT = 30

/* The library's row, measure for measure: the hairline starts where the text does. */
private val RowMinHeight = 64.dp
private val RowSidePadding = 14.dp
private val RowGap = 12.dp
private val RowCoverSize = 46.dp
private val RowDividerInset = RowSidePadding + RowCoverSize + RowGap
