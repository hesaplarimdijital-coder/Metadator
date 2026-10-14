/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController
import com.bobbyesp.metadator.player.api.RepeatMode

/**
 * The sizes of the transport controls where they are: the buttons that change how the queue plays,
 * the ones that skip, the play button between them, and the gap between all of them.
 */
@Immutable
internal class TransportSizes(
    val mode: Dp,
    val modeIcon: Dp,
    val skip: Dp,
    val skipIcon: Dp,
    val play: Dp,
    val gap: Dp,
) {
    companion object {
        /** In the bar docked under the panels: a desktop's, for a pointer. */
        val Docked =
            TransportSizes(
                mode = 32.dp,
                modeIcon = 18.dp,
                skip = 32.dp,
                skipIcon = 22.dp,
                play = 38.dp,
                gap = Spacing.small,
            )

        /**
         * In the bar floating over the lyrics of a phone on its side: a finger's, in little room.
         */
        val Floating =
            TransportSizes(
                mode = 40.dp,
                modeIcon = 20.dp,
                skip = 40.dp,
                skipIcon = 24.dp,
                play = 48.dp,
                gap = Spacing.extraSmall,
            )

        /** On the full player, under the cover: the docked bar's, grown for a stage. */
        val Stage =
            TransportSizes(
                mode = 44.dp,
                modeIcon = 22.dp,
                skip = 48.dp,
                skipIcon = 28.dp,
                play = 64.dp,
                gap = Spacing.medium,
            )
    }
}

/**
 * Shuffle, previous, play, next and repeat in a row, the same in the docked bar and in the full
 * player. The play button is the one tied to the other player's by the sheet, through
 * [playButtonModifier]; whether shuffle and repeat are on is said by their tint, the accent, and
 * nothing more.
 *
 * @param othersModifier on every button but the play button: what the bar does with what only it
 *   has, while the play button travels
 */
// The play button is tied to the other player's by a modifier of its own, before the row's.
@Suppress("ModifierParameter")
@Composable
internal fun TransportControls(
    playback: PlaybackState,
    player: PlayerController,
    playButtonModifier: Modifier,
    modifier: Modifier = Modifier,
    sizes: TransportSizes = TransportSizes.Stage,
    othersModifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sizes.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = { player.setShuffle(!playback.shuffle) },
            tint = if (playback.shuffle) colors.accent else colors.textSecondary,
            size = sizes.mode,
            iconSize = sizes.modeIcon,
            modifier = othersModifier,
        )
        StudioIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            onClick = player::skipToPrevious,
            tint = colors.text,
            size = sizes.skip,
            iconSize = sizes.skipIcon,
            modifier = othersModifier,
        )
        PlayPauseButton(
            isPlaying = playback.isPlaying,
            onToggle = player::togglePlayPause,
            // The sheet's modifier first: it is what sizes the button on the way to the other
            // player.
            modifier = playButtonModifier.size(sizes.play),
        )
        StudioIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.next),
            onClick = player::skipToNext,
            enabled = playback.hasNext,
            tint = colors.text,
            size = sizes.skip,
            iconSize = sizes.skipIcon,
            modifier = othersModifier,
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
                if (playback.repeatMode != RepeatMode.Off) colors.accent else colors.textSecondary,
            size = sizes.mode,
            iconSize = sizes.modeIcon,
            modifier = othersModifier,
        )
    }
}

/**
 * The row along the top of the full player: the way down at its start, and at its end the way to
 * the editor, which is where the song's tags are. It is the handle the sheet is dragged down by.
 *
 * @param onEdit null while nothing plays, when there is nothing to edit
 */
@Composable
internal fun PlayerTopRow(
    onClose: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth().heightIn(min = PlayerTopRowHeight).padding(Spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
            size = TopRowButtonSize,
            iconSize = TopRowIconSize,
        )
        if (onEdit != null) {
            StudioButton(
                text = stringResource(R.string.edit_tags),
                onClick = onEdit,
                icon = Icons.Rounded.Edit,
                style = StudioButtonStyle.Secondary,
            )
        }
    }
}

/** A phone's, as the back buttons of its screens are: the full player is used with a finger too. */
private val TopRowButtonSize = 44.dp
private val TopRowIconSize = 22.dp

/** The row's height: its button and the padding around it, for whatever lines up with it. */
internal val PlayerTopRowHeight = TopRowButtonSize + Spacing.extraSmall * 2
