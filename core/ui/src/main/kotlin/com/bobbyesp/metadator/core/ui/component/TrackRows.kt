/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.common.formatDuration
import com.bobbyesp.metadator.core.designsystem.component.FactChip
import com.bobbyesp.metadator.core.designsystem.component.PlayingBars
import com.bobbyesp.metadator.core.designsystem.component.StudioCheckbox
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.quality.TrackQuality
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackSort
import com.bobbyesp.metadator.core.ui.R

/**
 * A song as a phone lists it: its cover, its name and where it is from, and what the lossless check
 * found or how long it is. Tapping opens it, a long press starts selecting. A song missing
 * essential tags says which under its name, so the library shows what to fix next.
 *
 * The row draws no surface of its own: it lies on the card of the list it is in, with a hairline
 * above it unless it is the first.
 *
 * @param active whether it is the song open beside the list
 * @param isPlaybackRunning whether the playing song is actually sounding, not paused
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    quality: TrackQuality = TrackQuality.NotApplicable,
    selected: Boolean = false,
    active: Boolean = false,
    isPlaying: Boolean = false,
    isPlaybackRunning: Boolean = isPlaying,
    showDivider: Boolean = false,
) {
    val colors = Studio.colors
    val selectedLabel = stringResource(R.string.song_selected)
    val tint by
        animateColorAsState(
            targetValue =
                when {
                    selected -> colors.accentSoft
                    active -> colors.hover
                    else -> Color.Transparent
                },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "TrackRowTint",
        )
    Column(modifier.background(tint)) {
        if (showDivider) StudioDivider(Modifier.padding(start = 72.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .combinedClickable(
                        interactionSource = null,
                        indication = ripple(),
                        onLongClickLabel = stringResource(R.string.select),
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                    .semantics { if (selected) stateDescription = selectedLabel }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackCover(track, selected, size = 46.dp, corner = 9.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying || active) colors.accent else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                missingTagsText(track.missingFields)?.let { MissingLine(it) }
            }
            when {
                isPlaying -> {
                    val nowPlaying = stringResource(R.string.now_playing)
                    PlayingBars(
                        playing = isPlaybackRunning,
                        color = colors.accent,
                        modifier = Modifier.semantics { contentDescription = nowPlaying },
                    )
                }
                quality is TrackQuality.Checked -> QualityBadge(quality, track.formatLabel)
                else ->
                    Text(
                        formatDuration(track.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
            }
        }
    }
}

/** "Artist · Album", with what is unknown said so. */
@Composable
private fun Track.subtitle(): String =
    listOf(
            artist ?: stringResource(R.string.unknown_artist),
            album ?: stringResource(R.string.unknown_album),
        )
        .joinToString(" · ")

/** Why a song needs attention, under what is known of it. */
@Composable
private fun MissingLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = Studio.colors.warning,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Studio.colors.warning,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The cover, which shows a check while it is selected: the thing the finger is on is the thing that
 * answers.
 */
@Composable
private fun TrackCover(track: Track, selected: Boolean, size: Dp, corner: Dp) {
    val motion = MaterialTheme.motionScheme
    val radius by
        animateDpAsState(
            targetValue = if (selected) size / 2 else corner,
            animationSpec = motion.fastSpatialSpec(),
            label = "CoverCorner",
        )
    Box(contentAlignment = Alignment.Center) {
        ArtworkImage(
            model = track.artwork,
            contentDescription = null,
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(radius),
        )
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
}

/**
 * The columns of a table of songs, as many as fit: the ones least needed go first as it narrows,
 * the year before the format, the format before the album. The title, the artist, the quality and
 * the length always stay.
 */
@Immutable
data class TrackColumns(val album: Boolean, val year: Boolean, val format: Boolean) {
    companion object {
        /** The narrowest a table is laid out at; narrower, the songs are a list. */
        val MinWidth: Dp = 560.dp

        fun forWidth(width: Dp): TrackColumns =
            TrackColumns(album = width >= 660.dp, year = width >= 900.dp, format = width >= 780.dp)
    }
}

/** The cells of a row of the table, laid out alike in its header and in each of its songs. */
@Composable
private fun TableCells(
    columns: TrackColumns,
    modifier: Modifier = Modifier,
    check: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    artist: @Composable () -> Unit,
    album: @Composable () -> Unit,
    year: @Composable () -> Unit,
    format: @Composable () -> Unit,
    quality: @Composable () -> Unit,
    duration: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { check() }
        Row(
            Modifier.weight(2.3f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = title,
        )
        Box(Modifier.weight(1.3f)) { artist() }
        if (columns.album) Box(Modifier.weight(1.4f)) { album() }
        if (columns.year) Box(Modifier.width(44.dp)) { year() }
        if (columns.format) Box(Modifier.width(64.dp)) { format() }
        Box(Modifier.width(140.dp)) { quality() }
        Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) { duration() }
    }
}

/**
 * The header of a table of songs: what each column is, sorted by what it is pressed on, and a
 * checkbox that selects every song listed.
 *
 * @param sort what the songs are sorted by, and [ascending] which way; null for songs in an order
 *   of their own, as an album's by disc and track, whose columns then sort nothing ([onSort] null)
 */
@Composable
fun TrackTableHeader(
    columns: TrackColumns,
    sort: TrackSort?,
    ascending: Boolean,
    onSort: ((TrackSort) -> Unit)?,
    selection: ToggleableState,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TableCells(
            columns = columns,
            modifier = Modifier.height(34.dp),
            check = { StudioCheckbox(selection, onClick = onToggleAll) },
            title = {
                HeaderCell(R.string.column_title, TrackSort.Title, sort, ascending, onSort)
            },
            artist = {
                HeaderCell(R.string.column_artist, TrackSort.Artist, sort, ascending, onSort)
            },
            album = {
                HeaderCell(R.string.column_album, TrackSort.Album, sort, ascending, onSort)
            },
            year = { HeaderCell(R.string.column_year) },
            format = { HeaderCell(R.string.column_format) },
            quality = { HeaderCell(R.string.column_quality) },
            duration = {
                HeaderCell(
                    R.string.column_duration,
                    TrackSort.Duration,
                    sort,
                    ascending,
                    onSort,
                    end = true,
                )
            },
        )
        StudioDivider()
    }
}

@Composable
private fun HeaderCell(
    label: Int,
    by: TrackSort? = null,
    sort: TrackSort? = null,
    ascending: Boolean = true,
    onSort: ((TrackSort) -> Unit)? = null,
    end: Boolean = false,
) {
    val colors = Studio.colors
    val sorted = by != null && by == sort
    Row(
        modifier =
            Modifier.then(
                    if (by != null && onSort != null) {
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = null,
                                indication = ripple(),
                                role = Role.Button,
                                onClick = { onSort(by) },
                            )
                    } else Modifier
                )
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = if (sorted) colors.textSoft else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (end) TextAlign.End else TextAlign.Start,
        )
        if (sorted) {
            Icon(
                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = colors.textSoft,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * A song as a table on a wide window lists it: a checkbox, its cover and title, where it is from,
 * what its file is and what the lossless check found, and how long it is. The row the pointer is on
 * is lit; the song open beside the table is tinted with the accent.
 *
 * Clicking it is the caller's: with Ctrl or Shift held it selects, as a file manager does.
 *
 * @param active whether it is the song open beside the table
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackTableRow(
    track: Track,
    columns: TrackColumns,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    quality: TrackQuality = TrackQuality.NotApplicable,
    selected: Boolean = false,
    active: Boolean = false,
    isPlaying: Boolean = false,
    isPlaybackRunning: Boolean = isPlaying,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val tint by
        animateColorAsState(
            targetValue =
                when {
                    active -> colors.accentSoft
                    selected -> colors.accentSoft.copy(alpha = colors.accentSoft.alpha * 0.65f)
                    hovered -> colors.hover
                    else -> Color.Transparent
                },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "TrackTableRowTint",
        )
    val shape = RoundedCornerShape(9.dp)
    val selectedLabel = stringResource(R.string.song_selected)
    val secondary = MaterialTheme.typography.bodyMedium
    TableCells(
        columns = columns,
        modifier =
            modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(shape)
                .background(tint, shape)
                .combinedClickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    onLongClickLabel = stringResource(R.string.select),
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                // The row is what is selected, as its checkbox says: a screen reader, and whatever
                // reads the screen, hear it on the row that holds the song's name.
                .semantics {
                    toggleableState = ToggleableState(selected)
                    if (selected) stateDescription = selectedLabel
                },
        check = {
            StudioCheckbox(
                state = if (selected) ToggleableState.On else ToggleableState.Off,
                onClick = onToggle,
            )
        },
        title = {
            ArtworkImage(
                model = track.artwork,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(6.dp),
                placeholderIconFraction = 0.55f,
            )
            Text(
                track.title,
                modifier = Modifier.weight(1f, fill = false),
                style =
                    secondary.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    ),
                color = if (isPlaying) colors.accent else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isPlaying) {
                PlayingBars(
                    playing = isPlaybackRunning,
                    color = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            val missing = missingTagsText(track.missingFields)
            if (missing != null) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = missing,
                    tint = colors.warning,
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        artist = { SecondaryCell(track.artist ?: stringResource(R.string.unknown_artist)) },
        album = { SecondaryCell(track.album ?: stringResource(R.string.unknown_album)) },
        year = { SecondaryCell(track.year?.toString() ?: "") },
        format = { FactChip(track.formatLabel) },
        quality = { QualityBadge(quality, track.formatLabel) },
        duration = { SecondaryCell(formatDuration(track.durationMs), end = true) },
    )
}

@Composable
private fun SecondaryCell(text: String, end: Boolean = false) {
    Text(
        text,
        modifier = if (end) Modifier.fillMaxWidth() else Modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = Studio.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
    )
}

/**
 * An album as a grid shows it: its cover, and its name and artist under it. No card around it: the
 * pointer lights it, as a desktop's file manager lights an icon.
 */
@Composable
fun CollectionCard(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(if (hovered) colors.hover else Color.Transparent, shape)
                .clickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkImage(
            model = artwork,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
