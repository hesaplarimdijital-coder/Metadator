/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.get
import com.bobbyesp.metadator.core.navigation.overlay.OverlaySceneStrategy

/**
 * Draws each destination on the ground. The screens draw their panels, or their content on a phone,
 * not what is under them, so that without it a screen fading over another showed the other through
 * it, as two screens drawn over each other. With it, one covers the other as it arrives.
 *
 * Not under an overlay: a sheet or a dialog is meant to leave the screen it opens over in sight.
 */
@Composable
internal fun <T : Any> rememberGroundNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator { entry ->
        if (entry.metadata[OverlaySceneStrategy.Companion.OverlayKey] != null) entry.Content()
        else Ground { entry.Content() }
    }
}

@Composable
private fun Ground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        propagateMinConstraints = true,
    ) {
        content()
    }
}
