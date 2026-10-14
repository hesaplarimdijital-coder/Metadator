/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.dissolved
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController

/**
 * The player as a desktop's bar, docked under the panels of a wide window: the song on the left,
 * the controls and where it is in the middle, the way to its lyrics and to the full player on the
 * right. It is the collapsed content of [PlayerSheet] there, as [MiniPlayerBar] is on a phone, and
 * shares the same pieces with the full player.
 *
 * @param position where the song is, which only the line of the song reads
 * @param ownContentPresence how much of what only the bar has is there, from 0 to 1, read while
 *   drawing so that a transition does not recompose the bar
 */
@Composable
internal fun DockedPlayerBar(
    track: Track,
    playback: PlaybackState,
    position: PlaybackPosition,
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
                TransportControls(
                    playback = playback,
                    player = player,
                    playButtonModifier = playButtonModifier,
                    sizes = TransportSizes.Docked,
                    othersModifier = Modifier.dissolved(ownContentPresence),
                )
                SeekLine(
                    playback = playback,
                    position = position,
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
