/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.LabelChip
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.domain.quality.LosslessFormat
import com.bobbyesp.metadator.core.domain.quality.LosslessReport
import com.bobbyesp.metadator.core.domain.quality.LosslessVerdict
import com.bobbyesp.metadator.core.domain.quality.SpectrumCurve
import com.bobbyesp.metadator.core.domain.quality.UnclearReason
import com.bobbyesp.metadator.feature.editor.LosslessCheckState
import com.bobbyesp.metadator.feature.editor.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Whether a lossless file is what it says: the verdict on its audio in a line, and under it what
 * the verdict rests on, for whoever wants to see for themselves.
 *
 * Nothing at all for a file that is not lossless: there is no question to answer.
 *
 * @param initiallyExpanded whether what the verdict rests on starts in sight, where there is room
 */
@Composable
fun LosslessCheckCard(
    state: LosslessCheckState,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    when (state) {
        LosslessCheckState.None -> Unit
        LosslessCheckState.Checking -> CheckInProgress(modifier)
        LosslessCheckState.Failed ->
            CheckNotice(
                icon = Icons.Rounded.ErrorOutline,
                text = stringResource(R.string.lossless_failed),
                modifier = modifier,
            )
        is LosslessCheckState.Done -> ReportCard(state.report, initiallyExpanded, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckInProgress(modifier: Modifier = Modifier) {
    CheckSurface(modifier) {
        Row(
            modifier = Modifier.padding(Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(BadgeSize))
            Text(
                stringResource(R.string.lossless_checking),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckNotice(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    CheckSurface(modifier) {
        Row(
            modifier = Modifier.padding(Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerdictBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportCard(
    report: LosslessReport,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val look = verdictLook(report.verdict)
    val motion = MaterialTheme.motionScheme
    val expandedLabel =
        stringResource(if (expanded) R.string.lossless_expanded else R.string.lossless_collapsed)
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth().semantics { stateDescription = expandedLabel },
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Spacing.large)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerdictBadge(look.icon, look.containerColor, look.contentColor)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                ) {
                    Text(
                        stringResource(look.title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        verdictSummary(report),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter =
                    expandVertically(motion.defaultSpatialSpec()) +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    shrinkVertically(motion.defaultSpatialSpec()) +
                        fadeOut(motion.fastEffectsSpec()),
            ) {
                ReportDetails(report, Modifier.padding(top = Spacing.large))
            }
        }
    }
}

/** What the verdict rests on: the figures, the spectrum they were read from, and their limits. */
@Composable
private fun ReportDetails(report: LosslessReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            LabelChip(
                stringResource(
                    R.string.lossless_format,
                    when (report.format) {
                        LosslessFormat.Flac -> "FLAC"
                        LosslessFormat.Wav -> "WAV"
                    },
                    report.bitsPerSample,
                    kiloHertz(report.sampleRateHz),
                ),
                Icons.Rounded.GraphicEq,
            )
            report.cutoffHz?.let {
                LabelChip(
                    stringResource(R.string.lossless_cutoff, kiloHertz(it)),
                    Icons.Rounded.ContentCut,
                )
            }
            report.estimatedSourceKbps?.let {
                LabelChip(stringResource(R.string.lossless_source_bitrate, it), Icons.Rounded.Speed)
            }
            report.effectiveBitsPerSample?.let {
                LabelChip(
                    stringResource(R.string.lossless_bits_used, it, report.bitsPerSample),
                    Icons.Rounded.Memory,
                )
            }
        }
        SpectrumChart(report.spectrum, report.cutoffHz)
        Text(
            stringResource(R.string.lossless_footnote, report.analyzedSeconds.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How loud each frequency is, from none at the left to the highest the file can hold at the right:
 * the average as an area, the loudest moments as a line over it. A lossy source shows as an area
 * that ends in a wall; where it does is marked.
 */
@Composable
private fun SpectrumChart(curve: SpectrumCurve, cutoffHz: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val average = colors.primary
    val peaks = colors.onSurfaceVariant
    val marker = colors.error
    val description = stringResource(R.string.lossless_chart_description)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Canvas(
            Modifier.fillMaxWidth()
                .height(ChartHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerHighest)
                .semantics { contentDescription = description }
        ) {
            val count = curve.size
            if (count < 2) return@Canvas
            // A little air over the loudest peak; under the quietest average, down to where
            // nothing is told apart any more.
            val top = (curve.peakDb.max() + CHART_MARGIN_DB).coerceAtMost(0f)
            val bottom = (curve.averageDb.min() - CHART_MARGIN_DB).coerceAtLeast(CHART_FLOOR_DB)
            val range = (top - bottom).coerceAtLeast(1f)
            fun x(index: Int) = size.width * (index + 0.5f) / count
            fun y(level: Float) = size.height * ((top - level) / range).coerceIn(0f, 1f)

            val outline = Path()
            val area = Path()
            area.moveTo(0f, size.height)
            area.lineTo(0f, y(curve.averageDb[0]))
            outline.moveTo(0f, y(curve.averageDb[0]))
            for (index in 0 until count) {
                area.lineTo(x(index), y(curve.averageDb[index]))
                outline.lineTo(x(index), y(curve.averageDb[index]))
            }
            area.lineTo(size.width, y(curve.averageDb[count - 1]))
            area.lineTo(size.width, size.height)
            area.close()
            drawPath(area, average.copy(alpha = AREA_ALPHA))
            drawPath(outline, average, style = Stroke(width = 1.5.dp.toPx()))

            val loudest = Path()
            loudest.moveTo(0f, y(curve.peakDb[0]))
            for (index in 0 until count) loudest.lineTo(x(index), y(curve.peakDb[index]))
            drawPath(loudest, peaks.copy(alpha = PEAKS_ALPHA), style = Stroke(width = 1.dp.toPx()))

            if (cutoffHz != null && curve.nyquistHz > 0f) {
                val at = size.width * (cutoffHz / curve.nyquistHz).coerceIn(0f, 1f)
                drawLine(
                    color = marker,
                    start = Offset(at, 0f),
                    end = Offset(at, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0f, curve.nyquistHz / 2, curve.nyquistHz).forEach { frequency ->
                Text(
                    stringResource(R.string.lossless_frequency, kiloHertz(frequency.roundToInt())),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

/** The verdict at a glance: still, where the app's turning shapes say that nothing is there. */
@Composable
private fun VerdictBadge(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Surface(
        modifier = Modifier.size(BadgeSize),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

private class VerdictLook(
    val title: Int,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun verdictLook(verdict: LosslessVerdict): VerdictLook {
    val colors = MaterialTheme.colorScheme
    return when (verdict) {
        LosslessVerdict.Lossless ->
            VerdictLook(
                R.string.lossless_verdict_lossless,
                Icons.Rounded.Verified,
                colors.primaryContainer,
                colors.onPrimaryContainer,
            )
        LosslessVerdict.Upscaled ->
            VerdictLook(
                R.string.lossless_verdict_upscaled,
                Icons.Rounded.ZoomOutMap,
                colors.tertiaryContainer,
                colors.onTertiaryContainer,
            )
        LosslessVerdict.Unclear ->
            VerdictLook(
                R.string.lossless_verdict_unclear,
                Icons.AutoMirrored.Rounded.HelpOutline,
                colors.surfaceContainerHighest,
                colors.onSurfaceVariant,
            )
        LosslessVerdict.ProbablyTranscoded ->
            VerdictLook(
                R.string.lossless_verdict_probably_transcoded,
                Icons.Rounded.WarningAmber,
                colors.tertiaryContainer,
                colors.onTertiaryContainer,
            )
        LosslessVerdict.Transcoded ->
            VerdictLook(
                R.string.lossless_verdict_transcoded,
                Icons.Rounded.Warning,
                colors.errorContainer,
                colors.onErrorContainer,
            )
    }
}

/** Why the verdict is what it is, in a sentence. */
@Composable
private fun verdictSummary(report: LosslessReport): String {
    val cutoff = report.cutoffHz?.let(::kiloHertz)
    val bitrate = report.estimatedSourceKbps
    return when (report.verdict) {
        LosslessVerdict.Lossless ->
            if (cutoff == null) {
                stringResource(
                    R.string.lossless_summary_full_band,
                    kiloHertz(report.sampleRateHz / 2),
                )
            } else {
                stringResource(R.string.lossless_summary_natural_end, cutoff)
            }
        LosslessVerdict.Upscaled ->
            if (report.upsampled && cutoff != null) {
                stringResource(
                    R.string.lossless_summary_upsampled,
                    kiloHertz(report.sampleRateHz),
                    cutoff,
                )
            } else {
                stringResource(
                    R.string.lossless_summary_padded,
                    report.bitsPerSample,
                    report.effectiveBitsPerSample ?: report.bitsPerSample,
                )
            }
        LosslessVerdict.Unclear ->
            when (report.unclearReason) {
                UnclearReason.LowSampleRate -> stringResource(R.string.lossless_summary_low_rate)
                UnclearReason.BorderlineCutoff ->
                    stringResource(R.string.lossless_summary_borderline, cutoff.orEmpty())
                UnclearReason.TooQuiet,
                null -> stringResource(R.string.lossless_summary_too_quiet)
            }
        LosslessVerdict.ProbablyTranscoded ->
            when {
                cutoff != null && bitrate != null ->
                    stringResource(R.string.lossless_summary_probably, cutoff, bitrate)
                else -> stringResource(R.string.lossless_summary_shelf)
            }
        LosslessVerdict.Transcoded ->
            if (cutoff != null && bitrate != null) {
                stringResource(R.string.lossless_summary_transcoded, cutoff, bitrate)
            } else {
                stringResource(R.string.lossless_summary_shelf)
            }
    }
}

/** [hertz] in kHz as it is said: "44.1", "48", "22.05" being close enough to "22.1". */
private fun kiloHertz(hertz: Int): String {
    val tenths = (hertz / 100.0).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString()
    else String.format(Locale.getDefault(), "%.1f", tenths / 10.0)
}

private val BadgeSize = 48.dp
private val ChartHeight = 132.dp
private const val CHART_MARGIN_DB = 6f

/** Under this there is only the rounding of 24-bit samples. */
private const val CHART_FLOOR_DB = -150f
private const val AREA_ALPHA = 0.28f
private const val PEAKS_ALPHA = 0.7f
