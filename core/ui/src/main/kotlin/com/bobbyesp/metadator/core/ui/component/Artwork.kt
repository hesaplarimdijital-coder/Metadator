/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.bobbyesp.metadator.core.designsystem.component.heldWhileClosing

/**
 * A cover: an embedded picture's bytes, a `content://` URI or a URL, or a song's own
 * ([com.bobbyesp.metadator.core.model.TrackArtwork]). While there is none, or it fails, a note on a
 * tonal surface takes its place, the same size, so lists never jump. A picture comes and goes
 * through a fade over the note, and one that takes the place of another starts from the other: a
 * song just saved shows the cover it had until the new one is decoded, not a blank between the two.
 *
 * @param cacheKey a key shared by the places that show this same picture at different sizes. One
 *   that appears while another is on screen (a shared element) then starts from the picture already
 *   loaded instead of from nothing.
 */
@Composable
fun ArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    placeholderIconFraction: Float = 0.45f,
    cacheKey: String? = null,
) {
    var failed by remember(model) { mutableStateOf(false) }
    // Where the last picture was cached: the next request starts from it.
    val lastShown = remember { LastShown() }
    val motion = MaterialTheme.motionScheme
    Box(
        modifier =
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(placeholderIconFraction),
        )
        val showing = model != null && !failed
        // The picture on its way out is the one that was shown, not the nothing that replaces it.
        val shown = heldWhileClosing(showing, model)
        AnimatedVisibility(
            visible = showing,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.defaultEffectsSpec()),
        ) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(shown)
                        .crossfade(true)
                        .memoryCacheKey(cacheKey)
                        .placeholderMemoryCacheKey(
                            cacheKey?.let { MemoryCache.Key(it) } ?: lastShown.key
                        )
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { lastShown.key = it.result.memoryCacheKey },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private class LastShown(var key: MemoryCache.Key? = null)
