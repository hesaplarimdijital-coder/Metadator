/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorBlurDefaults
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.frosted
import dev.chrisbanes.haze.HazeState

/**
 * A surface floating over what scrolls, as glass: what is beneath shows through it, blurred, under
 * a veil of the raised tone, and a hairline edge, lighter along its top, catches the light. Nothing
 * is drawn around it, neither a halo nor a shadow: the glass itself sets it apart from what it
 * floats over, and its content is drawn on it.
 *
 * Where nothing is recorded beneath it ([backdrop] null, as while it travels in a shared element
 * transition, where a frosted surface flickers) it is the raised tone, solid. Where the device
 * cannot blur at all (Android 11 and below) it is solid too, and keeps a shadow to float by.
 *
 * @param backdrop what is recorded beneath it with `Modifier.hazeSource`: a sibling of the surface,
 *   never an ancestor, which would blur the surface into itself
 * @param onClick makes the whole surface a button, with [onClickLabel] said for it
 */
@Composable
fun GlassSurface(
    backdrop: HazeState?,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = Studio.colors
    // Light from above: the top edge brighter than the rest, as on a pane of glass.
    val edge =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.9f),
                    colors.lineStrong,
                )
            )
        }
    val source = backdrop?.takeIf { MetadatorBlurDefaults.isBlurSupported }
    Box(
        modifier =
            modifier
                .then(
                    when {
                        source != null ->
                            Modifier.frosted(
                                state = source,
                                style = MetadatorBlurDefaults.glassStyle(colors.raisedHigh),
                                shape = shape,
                            )
                        MetadatorBlurDefaults.isBlurSupported ->
                            Modifier.background(colors.raisedHigh, shape)
                        else -> Modifier.shadow(6.dp, shape).background(colors.raisedHigh, shape)
                    }
                )
                .border(1.dp, edge, shape)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            onClickLabel = onClickLabel,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else Modifier
                ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.text) { content() }
    }
}
