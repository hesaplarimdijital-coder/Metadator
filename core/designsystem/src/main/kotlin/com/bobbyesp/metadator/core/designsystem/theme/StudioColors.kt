/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The colors of the Studio look, beyond what Material's roles name: the ground the panels lie on,
 * the panels themselves, the hairlines between things, three shades of text, and what a verdict
 * says (fine, worth a look, wrong) in a color of its own.
 *
 * Neutral grays that hold still whatever the accent, so that the one accent stands out: the panels
 * of a desktop tool, which a phone's dark mode turns nearly black.
 */
@Immutable
data class StudioColors(
    val isDark: Boolean,
    /** Behind the panels, in the gaps between them. */
    val ground: Color,
    val sidebar: Color,
    val panel: Color,
    /** What sits in a panel and is touched: a field, a card, a button that is not the main one. */
    val raised: Color,
    /** What floats over a panel: a menu, a popover. */
    val raisedHigh: Color,
    /** A row under the pointer, or pressed. */
    val hover: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    /** What is read but is not the point: a row's other columns, an inactive item of a list. */
    val textSoft: Color,
    val textSecondary: Color,
    /** Labels and figures: quieter than the text, still read. */
    val textTertiary: Color,
    /** The well a value is typed in, and its edge. */
    val field: Color,
    val fieldLine: Color,
    /** The chosen option of a segmented control, raised out of its track. */
    val segment: Color,
    /** The track of a switch that is off. */
    val switchOff: Color,
    /** Text and icons in the accent, on a panel. */
    val accent: Color,
    /** The fill of the one button a screen is for, with [onAccentFill] on it. */
    val accentFill: Color,
    val onAccentFill: Color,
    /** The tint of what is selected. */
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

/**
 * One of the accents a user can choose, in the shade each theme needs: on a dark panel the accent
 * must be light to be read, and the fill of a button dark enough for white text on it.
 *
 * [id] is what the settings keep: the accent as it is on a light panel.
 */
@Immutable
class StudioAccent(
    val id: Int,
    val name: String,
    val onDark: Color,
    val fillOnDark: Color,
    val onLight: Color,
    val fillOnLight: Color,
)

object StudioAccents {
    val Blue =
        StudioAccent(
            id = 0xFF0B6BDE.toInt(),
            name = "blue",
            onDark = Color(0xFF4C9AFF),
            fillOnDark = Color(0xFF1F6FEB),
            onLight = Color(0xFF0B6BDE),
            fillOnLight = Color(0xFF0B6BDE),
        )
    val Violet =
        StudioAccent(
            id = 0xFF6545C8.toInt(),
            name = "violet",
            onDark = Color(0xFFA594FF),
            fillOnDark = Color(0xFF6E56CF),
            onLight = Color(0xFF6545C8),
            fillOnLight = Color(0xFF6545C8),
        )
    val Teal =
        StudioAccent(
            id = 0xFF0B7F6B.toInt(),
            name = "teal",
            onDark = Color(0xFF3DD6B8),
            fillOnDark = Color(0xFF0B7F6B),
            onLight = Color(0xFF0B7F6B),
            fillOnLight = Color(0xFF0B7F6B),
        )
    val Orange =
        StudioAccent(
            id = 0xFFB4501A.toInt(),
            name = "orange",
            onDark = Color(0xFFFF9F5A),
            fillOnDark = Color(0xFFC4561B),
            onLight = Color(0xFFB4501A),
            fillOnLight = Color(0xFFB4501A),
        )
    val Pink =
        StudioAccent(
            id = 0xFFB52F72.toInt(),
            name = "pink",
            onDark = Color(0xFFFF8AB8),
            fillOnDark = Color(0xFFC2337A),
            onLight = Color(0xFFB52F72),
            fillOnLight = Color(0xFFB52F72),
        )
    val Graphite =
        StudioAccent(
            id = 0xFF3F454E.toInt(),
            name = "graphite",
            onDark = Color(0xFFC9CDD3),
            fillOnDark = Color(0xFF4A5059),
            onLight = Color(0xFF3F454E),
            fillOnLight = Color(0xFF3F454E),
        )

    val All: List<StudioAccent> = listOf(Blue, Violet, Teal, Orange, Pink, Graphite)

    /** The accent kept as [id]; the default for anything else, a color from before included. */
    fun of(id: Int): StudioAccent = All.firstOrNull { it.id == id } ?: Blue
}

/**
 * The Studio colors in a theme: [accent] is the accent as text on its panels, [fill] the color of
 * the main button.
 */
internal fun studioColors(isDark: Boolean, accent: Color, fill: Color): StudioColors =
    if (isDark) {
        StudioColors(
            isDark = true,
            ground = Color(0xFF0B0C0E),
            sidebar = Color(0xFF111316),
            panel = Color(0xFF15171A),
            raised = Color(0xFF1C1F23),
            raisedHigh = Color(0xFF23272C),
            hover = Color(0x0FFFFFFF),
            line = Color(0x12FFFFFF),
            lineStrong = Color(0x21FFFFFF),
            text = Color(0xFFEEF0F2),
            textSoft = Color(0xFFC9CDD3),
            textSecondary = Color(0xFFA6ACB4),
            textTertiary = Color(0xFF80878F),
            field = Color(0xFF1C1F23),
            fieldLine = Color(0x17FFFFFF),
            segment = Color(0xFF2A2E34),
            switchOff = Color(0xFF3A3F46),
            accent = accent,
            accentFill = fill,
            onAccentFill = Color.White,
            accentSoft = accent.copy(alpha = 0.16f),
            success = Color(0xFF46D39A),
            successSoft = Color(0x1F46D39A),
            warning = Color(0xFFF0C05A),
            warningSoft = Color(0x1FF0C05A),
            danger = Color(0xFFFF8B5E),
            dangerSoft = Color(0x22FF8B5E),
        )
    } else {
        StudioColors(
            isDark = false,
            ground = Color(0xFFE8EAED),
            sidebar = Color(0xFFF3F4F6),
            panel = Color(0xFFFFFFFF),
            raised = Color(0xFFF4F5F7),
            raisedHigh = Color(0xFFFFFFFF),
            hover = Color(0x0A111827),
            line = Color(0x14111827),
            lineStrong = Color(0x24111827),
            text = Color(0xFF15171A),
            textSoft = Color(0xFF3F454E),
            textSecondary = Color(0xFF4D545E),
            textTertiary = Color(0xFF6B727C),
            field = Color(0xFFFFFFFF),
            fieldLine = Color(0x1F111827),
            segment = Color(0xFFFFFFFF),
            switchOff = Color(0xFFD3D6DB),
            accent = accent,
            accentFill = fill,
            onAccentFill = Color.White,
            accentSoft = accent.copy(alpha = 0.10f),
            success = Color(0xFF0B7046),
            successSoft = Color(0xFFE2F3EA),
            warning = Color(0xFF875700),
            warningSoft = Color(0xFFFAEFD4),
            danger = Color(0xFFB03F0C),
            dangerSoft = Color(0xFFFCEAE1),
        )
    }

/**
 * Material's roles in the Studio colors, so that what Material draws by itself (a switch, a slider,
 * a sheet, a dialog) is already in them.
 *
 * A screen is drawn on the ground, as the window is: Material's background and surface. What is
 * grouped on it lies on a panel, the lowest of Material's containers; the containers above it are
 * the raised surfaces, a dialog the highest.
 */
internal fun StudioColors.toColorScheme(): ColorScheme {
    val solidSoft = accentSoft.compositeOver(panel)
    val warningContainer = warningSoft.compositeOver(panel)
    val dangerContainer = dangerSoft.compositeOver(panel)
    val outline = lineStrong.compositeOver(panel)
    val outlineVariant = line.compositeOver(panel)
    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF0B0C0E),
            primaryContainer = solidSoft,
            onPrimaryContainer = accent,
            inversePrimary = accentFill,
            secondary = accent,
            onSecondary = Color(0xFF0B0C0E),
            secondaryContainer = segment,
            onSecondaryContainer = text,
            tertiary = success,
            onTertiary = Color(0xFF0B0C0E),
            tertiaryContainer = warningContainer,
            onTertiaryContainer = warning,
            background = ground,
            onBackground = text,
            surface = ground,
            onSurface = text,
            surfaceVariant = raised,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFEEF0F2),
            inverseOnSurface = Color(0xFF15171A),
            error = danger,
            onError = Color(0xFF0B0C0E),
            errorContainer = dangerContainer,
            onErrorContainer = danger,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = raisedHigh,
            surfaceDim = ground,
            surfaceContainerLowest = panel,
            surfaceContainerLow = panel,
            surfaceContainer = raised,
            surfaceContainerHigh = Color(0xFF1E2125),
            surfaceContainerHighest = raisedHigh,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = solidSoft,
            onPrimaryContainer = accent,
            inversePrimary = Color(0xFF4C9AFF),
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE6E8EB),
            onSecondaryContainer = text,
            tertiary = success,
            onTertiary = Color.White,
            tertiaryContainer = warningContainer,
            onTertiaryContainer = warning,
            background = ground,
            onBackground = text,
            surface = ground,
            onSurface = text,
            surfaceVariant = raised,
            onSurfaceVariant = textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF2A2E34),
            inverseOnSurface = Color(0xFFF4F5F7),
            error = danger,
            onError = Color.White,
            errorContainer = dangerContainer,
            onErrorContainer = danger,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = Color.White,
            surfaceDim = ground,
            surfaceContainerLowest = panel,
            surfaceContainerLow = panel,
            surfaceContainer = raised,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFEDEEF1),
        )
    }
}

internal val LocalStudioColors = staticCompositionLocalOf {
    studioColors(
        isDark = true,
        accent = StudioAccents.Blue.onDark,
        fill = StudioAccents.Blue.fillOnDark,
    )
}

/** The Studio look's own values, beside Material's. */
object Studio {
    val colors: StudioColors
        @Composable @ReadOnlyComposable get() = LocalStudioColors.current
}
