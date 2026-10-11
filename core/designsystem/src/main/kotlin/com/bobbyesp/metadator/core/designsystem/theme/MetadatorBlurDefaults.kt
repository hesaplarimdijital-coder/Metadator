/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3

/**
 * Where Metadator blurs, and how much. Material has no blur tokens, so these stand in for elevation
 * where a surface floats over content that moves beneath it: frosting what is behind the surface
 * tells the two apart better than a shadow, and keeps the content's colors in view. The surface
 * keeps its Material color role, only translucent.
 *
 * Two kinds of blur, for two jobs:
 * - **Frosted surfaces** ([surfaceStyle], [glassStyle] and [frosted], on Haze): a bar, a menu or
 *   the wide player's controls over content recorded with `Modifier.hazeSource`. The blur is on the
 *   surface, never around it: a halo of blur around a floating element read as an effect beside it
 *   rather than as depth.
 * - **Content out of focus** ([outOfFocus], on `Modifier.blur`): the screen behind something that
 *   opens over it. And [dissolved], for what comes and goes as one surface turns into another.
 *
 * Below Android 12 neither blurs: a frosted surface falls back to its container color, nearly
 * opaque, and content that would go out of focus is dimmed instead.
 */
object MetadatorBlurDefaults {

    /**
     * Behind a frosted surface: enough that text beneath cannot be read, not so much that its
     * colors turn to mud.
     */
    val SurfaceRadius: Dp = 24.dp

    /**
     * The screen behind what opens over it: out of focus but still recognizable, so it is clear
     * what was left behind.
     */
    val BehindOverlayRadius: Dp = 12.dp

    /**
     * What is [dissolved], at its faintest: clearly out of focus, but its shapes still there to
     * sharpen into place.
     */
    val DissolveRadius: Dp = 12.dp

    /** Whether the device can blur at all; where it cannot, a dim does the job. */
    val isBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * How much of the container color covers the blur. Enough for the content colors on it to keep
     * their contrast whatever scrolls beneath; a dark container needs more, because light content
     * shows through it more.
     */
    private const val LightContainerOpacity = 0.76f
    private const val DarkContainerOpacity = 0.82f

    /**
     * A little grain, which keeps a wide blur of flat colors from banding. Less than Haze's
     * default: Material surfaces are flat, and more reads as texture.
     */
    private const val NoiseFactor = 0.06f

    /**
     * How dark content out of focus gets. Where it is also blurred a touch is enough; where it
     * cannot be, the dim is all that sets it back, and has to be Material's scrim.
     */
    internal const val BlurredScrimOpacity = 0.16f
    internal const val SharpScrimOpacity = 0.48f

    /**
     * How much of its tone veils a glass bar's blur: less than a menu's, so that what scrolls
     * beneath reads through the bar as through glass; still enough for its text to keep its
     * contrast over a bright cover.
     */
    private const val LightGlassOpacity = 0.66f
    private const val DarkGlassOpacity = 0.72f

    /**
     * A glass [containerColor], for a bar floating over a list: [surfaceStyle], more see-through.
     */
    @Composable
    @ReadOnlyComposable
    fun glassStyle(containerColor: Color): HazeBlurStyle =
        HazeBlurStyle.Material3(containerColor) {
            blurRadius(SurfaceRadius)
            noiseFactor(NoiseFactor)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        containerColor.copy(
                            alpha =
                                if (containerColor.luminance() >= 0.5f) LightGlassOpacity
                                else DarkGlassOpacity
                        )
                    )
                )
            )
        }

    /**
     * A frosted [containerColor]: what is behind the surface, blurred, under the container color at
     * [LightContainerOpacity] or [DarkContainerOpacity]. Pass the role the surface would have used
     * as a solid color, so the frosted one sits at the same place in the color scheme.
     */
    @Composable
    @ReadOnlyComposable
    fun surfaceStyle(containerColor: Color): HazeBlurStyle =
        HazeBlurStyle.Material3(containerColor) {
            blurRadius(SurfaceRadius)
            noiseFactor(NoiseFactor)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        containerColor.copy(
                            alpha =
                                if (containerColor.luminance() >= 0.5f) LightContainerOpacity
                                else DarkContainerOpacity
                        )
                    )
                )
            )
        }
}

/**
 * What is recorded of the screen for whatever floats over all of it to blur: the menus, and the
 * shell's own player bar. The shell records it and provides it; a screen never does.
 *
 * `null` where nothing is recorded (a preview, the player, an overlay in a window of its own):
 * there a menu keeps Material's container and shadow. Never frost with it something drawn inside
 * the screen itself, which is inside what it records: that takes a source of the screen's own.
 */
val LocalBackdropHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Frosts this surface with [style], blurring what [state] records beneath it. The surface's own
 * container must then be transparent, and its shadow gone: the frost is what sets it apart.
 *
 * The source (`Modifier.hazeSource`) is a sibling of what it frosts, never an ancestor: a surface
 * inside its own source would blur itself. And the content passes beneath the surface padded, not
 * inset, or there is nothing there to frost.
 *
 * [HazeInput.Sources] rather than the backdrop, because it also reaches across windows. Clipped to
 * [shape], the surface's own, because the blur is drawn to the rectangle of the layout.
 */
fun Modifier.frosted(
    state: HazeState,
    style: HazeBlurStyle,
    shape: Shape = RectangleShape,
): Modifier = clip(shape).hazeBlur(input = HazeInput.Sources(state), style = style)

/**
 * Takes this out of focus as something opens over it: blurred and dimmed with [scrim] in step with
 * [fraction], from 0 (as it is) to 1. Below Android 12 it is only dimmed, and more.
 *
 * @param fraction read while drawing, so a gesture can drive it without recomposing anything
 */
@Composable
fun Modifier.outOfFocus(fraction: () -> Float, scrim: Color): Modifier {
    // Only while there is a blur to draw: it puts everything under it in a layer of its own, which
    // nothing needs otherwise.
    val blurring by
        remember(fraction) {
            derivedStateOf { MetadatorBlurDefaults.isBlurSupported && fraction() > 0f }
        }
    val scrimOpacity =
        if (MetadatorBlurDefaults.isBlurSupported) MetadatorBlurDefaults.BlurredScrimOpacity
        else MetadatorBlurDefaults.SharpScrimOpacity
    val blurred =
        if (blurring) {
            blur {
                radius =
                    BlurRadiusSpec.uniform(
                        MetadatorBlurDefaults.BehindOverlayRadius * fraction().coerceIn(0f, 1f)
                    )
            }
        } else this
    return blurred.drawWithContent {
        drawContent()
        val dim = fraction().coerceIn(0f, 1f) * scrimOpacity
        if (dim > 0f) drawRect(scrim, alpha = dim)
    }
}

/**
 * Brings this in, or takes it away, through focus: as faint and as blurred as it is absent, by
 * [presence] from 0 (gone) to 1 (as it is). On the way in it sharpens as it shows. For what one
 * surface has and the surface it turns into has not, which a plain fade would lay, half
 * see-through, over what is still there. Below Android 12 it only fades.
 *
 * `Modifier.blur` and not Haze, so it can run during a shared element transition. What travels in
 * that transition is drawn in its overlay, outside this, and stays sharp.
 *
 * @param presence read while drawing, so a gesture can drive it without recomposing anything
 */
@Composable
fun Modifier.dissolved(presence: () -> Float): Modifier {
    val absence = remember(presence) { { 1f - presence() } }
    return defocused(absence).graphicsLayer { alpha = presence().coerceIn(0f, 1f) }
}

/**
 * Takes this out of focus by [amount], from 0 (sharp) to 1, and nothing else: it stays as solid as
 * it was. For what something else is taking the place of, and must not be seen through meanwhile.
 * Below Android 12 it does nothing.
 *
 * @param amount read while drawing, so an animation can drive it without recomposing anything
 * @param bounded whether the blur stops at the edges of this, which is right for what fills its
 *   rectangle (a picture); left unbounded it is right for what does not (a pill, a line of text),
 *   which a blur cut to a rectangle would show the corners of
 */
@Composable
fun Modifier.defocused(amount: () -> Float, bounded: Boolean = false): Modifier {
    // Only on the way: sharp there is nothing to blur, and no layer to pay for.
    val blurring by
        remember(amount) {
            derivedStateOf { MetadatorBlurDefaults.isBlurSupported && amount() > 0f }
        }
    if (!blurring) return this
    return blur {
        radius =
            BlurRadiusSpec.uniform(MetadatorBlurDefaults.DissolveRadius * amount().coerceIn(0f, 1f))
        edgeTreatment =
            if (bounded) BlurredEdgeTreatment.Rectangle else BlurredEdgeTreatment.Unbounded
    }
}
