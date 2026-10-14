/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.ui.component.ArtworkImage

/**
 * What the full player is drawn on, in either layout: the ground, with the cover out of focus under
 * a veil of it, so that the stage takes a wash of the song's colors while the panels keep their
 * grays and the text on it its contrast. A new song's cover fades in over the last one's. Where the
 * device cannot blur, the theme's own colors stand in, which the cover has already tinted through
 * the accent.
 */
@Composable
internal fun PlayerBackdrop(track: Track, modifier: Modifier = Modifier) {
    val colors = Studio.colors
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Box(modifier.background(Brush.verticalGradient(listOf(colors.accentSoft, colors.ground))))
        return
    }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.background(colors.ground).onSizeChanged { bounds = it }) {
        Crossfade(
            targetState = track,
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            modifier = Modifier.align(Alignment.Center),
            label = "Backdrop",
        ) { shown ->
            // Blurred small and then stretched over the screen: the same picture as blurring it
            // at full size, for a fraction of the work on every frame the lyrics move. Stretched
            // past the edges, where a blur fades out.
            ArtworkImage(
                model = shown.artwork,
                contentDescription = null,
                modifier =
                    Modifier.size(BackdropSample)
                        .graphicsLayer {
                            val scale =
                                maxOf(bounds.width, bounds.height) / size.width * BACKDROP_OVERSCAN
                            scaleX = scale
                            scaleY = scale
                        }
                        .blur { radius = BlurRadiusSpec.uniform(BackdropBlur) },
                shape = RectangleShape,
                cacheKey = shown.artworkCacheKey,
            )
        }
        Box(Modifier.matchParentSize().background(colors.ground.copy(alpha = BACKDROP_VEIL)))
    }
}

private val BackdropSample = 96.dp
private val BackdropBlur = 16.dp
private const val BACKDROP_OVERSCAN = 1.5f

/**
 * How much of the ground covers the cover: a wash of the song's colors, not a picture of it, so
 * that the lyrics over it read as on a panel.
 */
private const val BACKDROP_VEIL = 0.78f
