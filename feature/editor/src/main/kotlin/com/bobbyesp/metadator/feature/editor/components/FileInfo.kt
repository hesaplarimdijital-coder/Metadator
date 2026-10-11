/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.common.formatDuration
import com.bobbyesp.metadator.core.common.formatFileSize
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.editor.LoadedTrack
import com.bobbyesp.metadator.feature.editor.R
import kotlin.math.roundToInt

/** One thing known about the audio: what it measures, and how much of it there is. */
private class FileStat(val label: String, val value: String)

/**
 * What the file is, read-only: its name, and under it its size, its bitrate and where it is, in a
 * line; pressed, every figure known about its audio, and its folder to copy.
 */
@Composable
fun FileInfoCard(loaded: LoadedTrack, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val name = loaded.track?.displayName ?: loaded.file?.displayName.orEmpty()
    val stats = fileStats(loaded)
    val location = loaded.track?.folder ?: loaded.file?.location
    val colors = Studio.colors
    val shape = RoundedCornerShape(10.dp)
    val summary =
        listOfNotNull(
                (loaded.track?.sizeBytes ?: loaded.file?.sizeBytes)
                    ?.takeIf { it > 0 }
                    ?.let(::formatFileSize),
                loaded.audio
                    ?.bitrateKbps
                    ?.takeIf { it > 0 }
                    ?.let {
                        stringResource(R.string.file_bitrate_value, it)
                    },
                location,
            )
            .joinToString(" · ")
    val expandedLabel =
        stringResource(if (expanded) R.string.lossless_expanded else R.string.lossless_collapsed)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (colors.isDark) colors.raised else colors.field, shape)
                .border(1.dp, colors.line, shape)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        interactionSource = null,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = { expanded = !expanded },
                    )
                    .semantics { stateDescription = expandedLabel }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = expanded,
            enter =
                expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit =
                shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column {
                StudioDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    stats.forEach { stat -> StatRow(stat.label, stat.value) }
                    if (location != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.file_location),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textTertiary,
                            )
                            SelectionContainer {
                                Text(
                                    location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSoft,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        // Read as one thing: "Bitrate, 320 kbps".
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Studio.colors.textTertiary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Studio.colors.text,
            maxLines = 1,
        )
    }
}

/** What is known about the audio, in the order it is asked about; what is not known is left out. */
@Composable
private fun fileStats(loaded: LoadedTrack): List<FileStat> {
    val audio = loaded.audio
    return buildList {
        (audio?.durationMs ?: loaded.track?.durationMs)
            ?.takeIf { it > 0 }
            ?.let { add(FileStat(stringResource(R.string.file_duration), formatDuration(it))) }
        (loaded.track?.sizeBytes ?: loaded.file?.sizeBytes)
            ?.takeIf { it > 0 }
            ?.let { add(FileStat(stringResource(R.string.file_size), formatFileSize(it))) }
        audio
            ?.bitrateKbps
            ?.takeIf { it > 0 }
            ?.let {
                add(
                    FileStat(
                        stringResource(R.string.file_bitrate),
                        stringResource(R.string.file_bitrate_value, it),
                    )
                )
            }
        audio
            ?.sampleRateHz
            ?.takeIf { it > 0 }
            ?.let {
                add(
                    FileStat(
                        stringResource(R.string.file_sample_rate),
                        sampleRate(it),
                    )
                )
            }
        audio
            ?.channels
            ?.takeIf { it > 0 }
            ?.let {
                add(
                    FileStat(
                        stringResource(R.string.file_channels),
                        when (it) {
                            1 -> stringResource(R.string.file_channels_mono)
                            2 -> stringResource(R.string.file_channels_stereo)
                            else -> it.toString()
                        },
                    )
                )
            }
    }
}

/**
 * [hertz] in kHz, to a tenth, as a file's facts say it: "44,1 kHz". The string formats the number
 * in its own language, so the decimal mark is the one of the language the app is shown in.
 */
@Composable
internal fun sampleRate(hertz: Int): String {
    val tenths = (hertz / 100.0).roundToInt()
    return if (tenths % 10 == 0) stringResource(R.string.file_sample_rate_whole, tenths / 10)
    else stringResource(R.string.file_sample_rate_tenths, tenths / 10.0)
}
