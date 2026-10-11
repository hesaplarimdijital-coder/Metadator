/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bobbyesp.metadator.core.designsystem.component.StatusPill
import com.bobbyesp.metadator.core.designsystem.component.StudioTone
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.quality.LosslessVerdict
import com.bobbyesp.metadator.core.domain.quality.TrackQuality
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.ui.R

/**
 * What the lossless check found, as the library and the editor say it: a few words and the tone
 * they are said in. Null for a file there is nothing to check in.
 *
 * @param format what the file says it is ("FLAC"), for the verdict that says it is not
 */
@Composable
fun qualityLabel(quality: TrackQuality, format: String): Pair<String, StudioTone>? =
    when (quality) {
        TrackQuality.NotApplicable -> null
        TrackQuality.Unchecked -> stringResource(R.string.quality_unchecked) to StudioTone.Neutral
        TrackQuality.Checking -> stringResource(R.string.quality_checking) to StudioTone.Neutral
        TrackQuality.Unreadable -> stringResource(R.string.quality_unreadable) to StudioTone.Neutral
        is TrackQuality.Checked ->
            when (quality.verdict) {
                LosslessVerdict.Lossless ->
                    stringResource(R.string.quality_lossless) to StudioTone.Success
                LosslessVerdict.Upscaled ->
                    stringResource(R.string.quality_upscaled) to StudioTone.Warning
                LosslessVerdict.Unclear ->
                    stringResource(R.string.quality_unclear) to StudioTone.Neutral
                LosslessVerdict.ProbablyTranscoded ->
                    stringResource(R.string.quality_probably_fake) to StudioTone.Danger
                LosslessVerdict.Transcoded ->
                    stringResource(R.string.quality_fake, format) to StudioTone.Danger
            }
    }

/** What a file says it is, as a table writes it: "FLAC", "MP3". */
val Track.formatLabel: String
    get() = fileExtension.uppercase().ifEmpty { mimeType?.substringAfter('/')?.uppercase() ?: "?" }

/**
 * A song's quality in a pill of its tone, or a dash for a lossy file, which a screen reader is told
 * is fine: there is nothing to check in it.
 */
@Composable
fun QualityBadge(quality: TrackQuality, format: String, modifier: Modifier = Modifier) {
    val label = qualityLabel(quality, format)
    if (label == null) {
        val lossy = stringResource(R.string.quality_lossy)
        Text(
            "—",
            modifier = modifier.semantics { contentDescription = lossy },
            style = MaterialTheme.typography.bodyMedium,
            color = Studio.colors.textTertiary,
        )
    } else {
        StatusPill(label.first, label.second, modifier)
    }
}
