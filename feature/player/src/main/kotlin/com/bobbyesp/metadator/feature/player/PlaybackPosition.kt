/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import com.bobbyesp.metadator.player.api.PlaybackState

/**
 * Where the song is, read apart from the rest of what plays. The player reports it twice a second
 * while a song plays: read with the rest, it composed the whole player again each time, when only a
 * line, a slider and the lyrics move with it. The [PlaybackState] the player's screens are handed
 * leaves it out (its position is 0); this is where it is read, while drawing, in an effect, or in
 * the small composable that shows it as text.
 */
@Stable
internal class PlaybackPosition(private val state: State<PlaybackState>) {
    val positionMs: Long
        get() = state.value.positionMs

    /** How far into the song, from 0 to 1. */
    val progress: Float
        get() = state.value.progress
}
