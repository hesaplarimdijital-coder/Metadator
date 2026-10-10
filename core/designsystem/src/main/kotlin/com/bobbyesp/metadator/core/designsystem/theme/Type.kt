/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The phone's own typeface, Samsung's on a Galaxy, in a desktop tool's sizes: smaller body text
 * than Material's, so that a table holds a library and a form a song, and heavier titles, which
 * carry the hierarchy. Figures line up (tabular), as durations, years and kilohertz in a column
 * must.
 */
private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )

private val DisplayLarge = style(48f, 56f, FontWeight.Bold, -0.02f)
private val DisplayMedium = style(40f, 48f, FontWeight.Bold, -0.02f)
private val DisplaySmall = style(34f, 42f, FontWeight.Bold, -0.015f)
private val HeadlineLarge = style(28f, 34f, FontWeight.Bold, -0.015f)
private val HeadlineMedium = style(24f, 30f, FontWeight.Bold, -0.01f)
private val HeadlineSmall = style(21f, 28f, FontWeight.SemiBold, -0.01f)
private val TitleLarge = style(19f, 26f, FontWeight.Bold, -0.005f)
private val TitleMedium = style(15f, 22f, FontWeight.SemiBold)
private val TitleSmall = style(13.5f, 20f, FontWeight.SemiBold)
private val BodyLarge = style(15f, 22f, FontWeight.Normal)
private val BodyMedium = style(13.5f, 20f, FontWeight.Normal)
private val BodySmall = style(12.5f, 18f, FontWeight.Normal)
private val LabelLarge = style(13.5f, 18f, FontWeight.SemiBold)
private val LabelMedium = style(12f, 16f, FontWeight.SemiBold)
private val LabelSmall = style(11f, 14f, FontWeight.Medium)

private fun TextStyle.heavier(): TextStyle =
    copy(
        fontWeight =
            when (fontWeight) {
                FontWeight.Normal -> FontWeight.Medium
                FontWeight.Medium -> FontWeight.SemiBold
                else -> FontWeight.Bold
            }
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val MetadatorTypography: Typography =
    Typography(
        displayLarge = DisplayLarge,
        displayMedium = DisplayMedium,
        displaySmall = DisplaySmall,
        headlineLarge = HeadlineLarge,
        headlineMedium = HeadlineMedium,
        headlineSmall = HeadlineSmall,
        titleLarge = TitleLarge,
        titleMedium = TitleMedium,
        titleSmall = TitleSmall,
        bodyLarge = BodyLarge,
        bodyMedium = BodyMedium,
        bodySmall = BodySmall,
        labelLarge = LabelLarge,
        labelMedium = LabelMedium,
        labelSmall = LabelSmall,
        displayLargeEmphasized = DisplayLarge.heavier(),
        displayMediumEmphasized = DisplayMedium.heavier(),
        displaySmallEmphasized = DisplaySmall.heavier(),
        headlineLargeEmphasized = HeadlineLarge.heavier(),
        headlineMediumEmphasized = HeadlineMedium.heavier(),
        headlineSmallEmphasized = HeadlineSmall.heavier(),
        titleLargeEmphasized = TitleLarge.heavier(),
        titleMediumEmphasized = TitleMedium.heavier(),
        titleSmallEmphasized = TitleSmall.heavier(),
        bodyLargeEmphasized = BodyLarge.heavier(),
        bodyMediumEmphasized = BodyMedium.heavier(),
        bodySmallEmphasized = BodySmall.heavier(),
        labelLargeEmphasized = LabelLarge.heavier(),
        labelMediumEmphasized = LabelMedium.heavier(),
        labelSmallEmphasized = LabelSmall.heavier(),
    )

/**
 * Corners of a desktop tool: tighter than Material's, and the same few everywhere. A panel is
 * [Shapes.large], a card in it [Shapes.medium], a field or a button [Shapes.small].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val MetadatorShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(9.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(18.dp),
        largeIncreased = RoundedCornerShape(16.dp),
        extraLargeIncreased = RoundedCornerShape(22.dp),
        extraExtraLarge = RoundedCornerShape(28.dp),
    )

/**
 * This style at another size, with every length in it scaled by as much: text set in one is then
 * the text set in the other, only larger or smaller. For a text that grows from one place to
 * another, whose two ends must match glyph for glyph on the way; two roles of the type scale do
 * not, since each has a weight, a line height and a tracking of its own.
 */
fun TextStyle.resizedTo(fontSize: TextUnit): TextStyle {
    val scale = fontSize.value / this.fontSize.value
    return copy(
        fontSize = fontSize,
        // Lengths in em already follow the font size.
        lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight,
        letterSpacing = if (letterSpacing.isSp) letterSpacing * scale else letterSpacing,
    )
}
