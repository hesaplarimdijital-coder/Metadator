/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.FactChip
import com.bobbyesp.metadator.core.designsystem.component.StudioDefaults
import com.bobbyesp.metadator.core.designsystem.component.StudioTone
import com.bobbyesp.metadator.core.designsystem.component.colors
import com.bobbyesp.metadator.core.designsystem.theme.Studio
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
 * Whether a lossless file is what it says: the verdict on its audio in a line, why in a sentence,
 * and the spectrum it was read from, for whoever wants to see for themselves. Its edge is the
 * verdict's color.
 *
 * Nothing at all for a file that is not lossless: there is no question to answer.
 */
@Composable
fun LosslessCheckCard(state: LosslessCheckState, modifier: Modifier = Modifier) {
    when (state) {
        LosslessCheckState.None -> Unit
        LosslessCheckState.Checking ->
            CheckCard(StudioTone.Neutral, modifier) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Studio.colors.accent,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        stringResource(R.string.lossless_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Studio.colors.textSecondary,
                    )
                }
            }
        LosslessCheckState.Failed ->
            CheckCard(StudioTone.Neutral, modifier) {
                VerdictLine(
                    icon = Icons.Rounded.ErrorOutline,
                    tone = StudioTone.Neutral,
                    title = stringResource(R.string.lossless_failed),
                    summary = null,
                )
            }
        is LosslessCheckState.Done -> ReportCard(state.report, modifier)
    }
}

/** The card a verdict is said on: a raised surface, edged with the verdict's color. */
@Composable
private fun CheckCard(
    tone: StudioTone,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    val edge =
        if (tone == StudioTone.Neutral) colors.line else tone.colors().content.copy(alpha = 0.24f)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (colors.isDark) colors.raised else colors.field, shape)
                .border(1.dp, edge, shape)
                .padding(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

/** The verdict, in its tone's circle, with why under it. */
@Composable
private fun VerdictLine(icon: ImageVector, tone: StudioTone, title: String, summary: String?) {
    val toneColors = tone.colors()
    Row(
        // Read as one thing: "Lossless. Content fills the band…".
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(30.dp).background(toneColors.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = toneColors.content,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Studio.colors.text,
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Studio.colors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(report: LosslessReport, modifier: Modifier = Modifier) {
    val look = verdictLook(report.verdict)
    CheckCard(look.tone, modifier) {
        VerdictLine(look.icon, look.tone, stringResource(look.title), verdictSummary(report))
        SpectrumChart(report.spectrum, report.cutoffHz, look.tone)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FactChip(
                stringResource(
                    R.string.lossless_format,
                    when (report.format) {
                        LosslessFormat.Flac -> "FLAC"
                        LosslessFormat.Wav -> "WAV"
                    },
                    report.bitsPerSample,
                    kiloHertz(report.sampleRateHz),
                )
            )
            report.estimatedSourceKbps?.let {
                FactChip(stringResource(R.string.lossless_source_bitrate, it), tone = look.tone)
            }
            report.effectiveBitsPerSample?.let {
                FactChip(
                    stringResource(R.string.lossless_bits_used, it, report.bitsPerSample),
                    tone = StudioTone.Warning,
                )
            }
        }
        Text(
            stringResource(R.string.lossless_footnote, report.analyzedSeconds.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = Studio.colors.textTertiary,
        )
    }
}

/**
 * How loud each frequency is, from none at the left to the highest the file can hold at the right:
 * the average as an area in the verdict's color, fading downwards. A lossy source shows as an area
 * that ends in a wall; where it does is marked.
 */
@Composable
private fun SpectrumChart(
    curve: SpectrumCurve,
    cutoffHz: Int?,
    tone: StudioTone,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val fill = if (tone == StudioTone.Neutral) colors.accent else tone.colors().content
    val marker = colors.text.copy(alpha = 0.6f)
    val description = stringResource(R.string.lossless_chart_description)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier.fillMaxWidth()
                .height(ChartHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.sidebar)
                .semantics { contentDescription = description }
        ) {
            val count = curve.size
            if (count < 2) return@Canvas
            // A little air over the loudest average; under the quietest, down to where nothing is
            // told apart any more.
            val top = (curve.averageDb.max() + CHART_MARGIN_DB).coerceAtMost(0f)
            val bottom = (curve.averageDb.min() - CHART_MARGIN_DB).coerceAtLeast(CHART_FLOOR_DB)
            val range = (top - bottom).coerceAtLeast(1f)
            fun x(index: Int) = size.width * (index + 0.5f) / count
            fun y(level: Float) = size.height * ((top - level) / range).coerceIn(0f, 1f)

            val area = Path()
            val outline = Path()
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
            drawPath(
                area,
                Brush.verticalGradient(listOf(fill.copy(alpha = 0.6f), fill.copy(alpha = 0.06f))),
            )
            drawPath(outline, fill, style = Stroke(width = 1.2.dp.toPx()))

            if (cutoffHz != null && curve.nyquistHz > 0f) {
                val at = size.width * (cutoffHz / curve.nyquistHz).coerceIn(0f, 1f)
                drawLine(
                    color = marker,
                    start = Offset(at, 6.dp.toPx()),
                    end = Offset(at, size.height - 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val marks =
                listOf(0f, curve.nyquistHz / 2, curve.nyquistHz).map {
                    stringResource(R.string.lossless_frequency, kiloHertz(it.roundToInt()))
                }
            marks.forEach { mark ->
                Text(
                    mark,
                    style = StudioDefaults.MonoStyle,
                    color = colors.textTertiary,
                )
            }
        }
        if (cutoffHz != null) {
            Text(
                stringResource(R.string.lossless_cutoff, kiloHertz(cutoffHz)),
                style = StudioDefaults.MonoStyle,
                color = colors.textSoft,
            )
        }
    }
}

private class VerdictLook(val title: Int, val icon: ImageVector, val tone: StudioTone)

private fun verdictLook(verdict: LosslessVerdict): VerdictLook =
    when (verdict) {
        LosslessVerdict.Lossless ->
            VerdictLook(R.string.lossless_verdict_lossless, Icons.Rounded.Check, StudioTone.Success)
        LosslessVerdict.Upscaled ->
            VerdictLook(
                R.string.lossless_verdict_upscaled,
                Icons.Rounded.ZoomOutMap,
                StudioTone.Warning,
            )
        LosslessVerdict.Unclear ->
            VerdictLook(
                R.string.lossless_verdict_unclear,
                Icons.AutoMirrored.Rounded.HelpOutline,
                StudioTone.Neutral,
            )
        LosslessVerdict.ProbablyTranscoded ->
            VerdictLook(
                R.string.lossless_verdict_probably_transcoded,
                Icons.Rounded.WarningAmber,
                StudioTone.Danger,
            )
        LosslessVerdict.Transcoded ->
            VerdictLook(
                R.string.lossless_verdict_transcoded,
                Icons.Rounded.GraphicEq,
                StudioTone.Danger,
            )
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

private val ChartHeight = 58.dp
private const val CHART_MARGIN_DB = 6f

/** Under this there is only the rounding of 24-bit samples. */
private const val CHART_FLOOR_DB = -150f
