/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bobbyesp.metadator.core.model.ThemeMode
import com.bobbyesp.metadator.core.model.UserSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Whether the theme is dark, as the screens that draw their own surfaces need to know. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.FollowSystem -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The app's theme: the Studio look on Material 3 Expressive's components and motion.
 *
 * Neutral panels on a darker ground, one accent: the user's choice of a few, or the phone's own
 * palette (One UI's color palette, Android's wallpaper colors) when asked for. Only the accent
 * changes with the choice, so that the app stays the same app in any of them; being a handful of
 * fixed values, the colors are made where they are needed, with nothing to compute off the main
 * thread.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetadatorTheme(settings: UserSettings = UserSettings(), content: @Composable () -> Unit) {
    val isDark = settings.themeMode.isDark()
    val context = LocalContext.current
    val studio =
        remember(isDark, settings.seedColor, settings.useDynamicColor) {
            if (settings.useDynamicColor && isDynamicColorSupported()) {
                // The system's accent as text on the panel, and its darker tone as a fill: white
                // reads on it in both themes.
                val light = dynamicLightColorScheme(context).primary
                val accent = if (isDark) dynamicDarkColorScheme(context).primary else light
                studioColors(isDark, accent, light)
            } else {
                val preset = StudioAccents.of(settings.seedColor)
                if (isDark) studioColors(true, preset.onDark, preset.fillOnDark)
                else studioColors(false, preset.onLight, preset.fillOnLight)
            }
        }
    val scheme = remember(studio) { studio.toColorScheme() }
    val motionScheme = remember { MotionScheme.expressive() }

    CompositionLocalProvider(LocalDarkTheme provides isDark, LocalStudioColors provides studio) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = motionScheme,
            typography = MetadatorTypography,
            shapes = MetadatorShapes,
            content = content,
        )
    }
}

/**
 * The app's theme with the accent taken from [accent] (a cover's color) for one destination: the
 * player shows the song's colors. Only the accent changes, brought to a lightness that reads on the
 * panels of the theme it is in; the grays stay the app's.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetadatorAccentTheme(accent: Color?, content: @Composable () -> Unit) {
    val base = Studio.colors
    val baseScheme = MaterialTheme.colorScheme
    // The same layers with an accent or without: a song's color arrives after the song, and content
    // that moved into other layers would be composed anew, its state lost. The player's sheet
    // would even take its seekable transition into a second Transition, which throws.
    val studio =
        remember(base, accent) {
            if (accent == null) base
            else {
                val text = readableAccent(accent, base.isDark)
                val fill = readableAccent(accent, isDark = false)
                base.copy(accent = text, accentFill = fill, accentSoft = text.copy(alpha = 0.16f))
            }
        }
    val scheme =
        remember(studio, baseScheme) {
            if (studio === base) baseScheme else studio.toColorScheme()
        }
    CompositionLocalProvider(LocalStudioColors provides studio) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MaterialTheme.motionScheme,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

/**
 * [color] at a lightness that reads as text on the panels of a dark or a light theme, and with
 * white on it in a light one; its hue kept, its saturation tamed so that a loud cover does not make
 * a loud screen.
 */
internal fun readableAccent(color: Color, isDark: Boolean): Color {
    val (hue, saturation, lightness) = color.toHsl()
    val tamed = min(saturation, 0.78f).let { if (it < 0.12f) it else max(it, 0.42f) }
    val target = if (isDark) max(lightness, 0.68f) else min(lightness, 0.40f)
    return hslColor(hue, tamed, target)
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val lightness = (maxC + minC) / 2f
    val delta = maxC - minC
    if (delta == 0f) return Triple(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue =
        when (maxC) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
    return Triple(hue, saturation, lightness)
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val (r, g, b) =
        when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}
