/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.cover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.LabelChip
import com.bobbyesp.metadator.core.designsystem.component.SectionHeader
import com.bobbyesp.metadator.core.designsystem.component.SheetButton
import com.bobbyesp.metadator.core.designsystem.component.SheetMessage
import com.bobbyesp.metadator.core.designsystem.component.SheetQueryField
import com.bobbyesp.metadator.core.designsystem.theme.GroupShapes
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.domain.cover.CoverReason
import com.bobbyesp.metadator.core.domain.cover.CoverSuggestion
import com.bobbyesp.metadator.core.ui.R
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.lookup.api.MatchConfidence

/**
 * "Find a cover": the covers the online sources have for the song, the one that suits it best first
 * and set apart, with what speaks for it. Choosing one hands it to the screen that opened the sheet
 * ([onUse]); nothing is written until the user saves there.
 *
 * @param useLabel what choosing the best cover is called: a screen that will put it in several
 *   songs says so on the button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverFinderSheet(
    state: CoverFinderState,
    onQueryChange: (title: String, artist: String, album: String) -> Unit,
    onSearch: () -> Unit,
    onUse: (CoverSuggestion) -> Unit,
    onDismiss: () -> Unit,
    useLabel: String = stringResource(R.string.cover_finder_use),
) {
    // No half-open state: the sheet is as tall as what it shows.
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp),
            contentPadding =
                PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "query") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(
                        stringResource(R.string.cover_finder_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.padding(bottom = Spacing.small),
                    )
                    SheetQueryField(
                        label = stringResource(R.string.field_title),
                        value = state.title,
                        onValueChange = { onQueryChange(it, state.artist, state.album) },
                    )
                    SheetQueryField(
                        label = stringResource(R.string.field_artist),
                        value = state.artist,
                        onValueChange = { onQueryChange(state.title, it, state.album) },
                    )
                    SheetQueryField(
                        label = stringResource(R.string.field_album),
                        value = state.album,
                        onValueChange = { onQueryChange(state.title, state.artist, it) },
                        onSearch = onSearch,
                    )
                    SheetButton(
                        text = stringResource(R.string.cover_finder_search),
                        icon = Icons.Rounded.Search,
                        onClick = onSearch,
                        enabled = state.status != CoverFinderStatus.Searching,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                    )
                }
            }
            when (val status = state.status) {
                CoverFinderStatus.Idle -> Unit
                CoverFinderStatus.Searching ->
                    item(key = "searching") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.huge).animateItem(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                        ) {
                            ContainedLoadingIndicator()
                            Text(
                                stringResource(R.string.cover_finder_searching),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                is CoverFinderStatus.Results -> {
                    val best = status.suggestions.firstOrNull()
                    val others = status.suggestions.drop(1)
                    if (best == null) {
                        item(key = "state") {
                            SheetMessage(
                                Icons.Rounded.SearchOff,
                                stringResource(R.string.cover_finder_no_results),
                                Modifier.animateItem(),
                            )
                        }
                    } else {
                        item(key = "best") {
                            BestCover(
                                suggestion = best,
                                useLabel = useLabel,
                                onUse = { onUse(best) },
                                modifier = Modifier.padding(top = Spacing.medium).animateItem(),
                            )
                        }
                        item(key = "how") {
                            Text(
                                stringResource(R.string.cover_finder_how),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(
                                            horizontal = Spacing.small,
                                            vertical = Spacing.small,
                                        )
                                        .animateItem(),
                            )
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "others") {
                            SectionHeader(
                                stringResource(R.string.cover_finder_others),
                                Modifier.animateItem(),
                            )
                        }
                    }
                    itemsIndexed(others, key = { _, it -> it.url }) { index, suggestion ->
                        OtherCover(suggestion, index, others.size, Modifier.animateItem()) {
                            onUse(suggestion)
                        }
                    }
                    if (status.failedProviders.isNotEmpty()) {
                        item(key = "partial") {
                            Text(
                                stringResource(
                                    R.string.cover_finder_partial,
                                    status.failedProviders.joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(top = Spacing.medium)
                                        .animateItem(),
                            )
                        }
                    }
                }
                CoverFinderStatus.NoProviders ->
                    item(key = "state") {
                        SheetMessage(
                            Icons.Rounded.TravelExplore,
                            stringResource(R.string.cover_finder_no_sources),
                            Modifier.animateItem(),
                        )
                    }
                CoverFinderStatus.Offline ->
                    item(key = "state") {
                        SheetMessage(
                            Icons.Rounded.CloudOff,
                            stringResource(R.string.cover_finder_offline),
                            Modifier.animateItem(),
                            isError = true,
                        )
                    }
                CoverFinderStatus.RateLimited ->
                    item(key = "state") {
                        SheetMessage(
                            Icons.Rounded.HourglassTop,
                            stringResource(R.string.cover_finder_busy),
                            Modifier.animateItem(),
                        )
                    }
                CoverFinderStatus.Failed ->
                    item(key = "state") {
                        SheetMessage(
                            Icons.Rounded.ErrorOutline,
                            stringResource(R.string.cover_finder_failed),
                            Modifier.animateItem(),
                            isError = true,
                        )
                    }
            }
        }
    }
}

/**
 * The cover to use, said once and large: the picture, how sure the choice is, what speaks for it
 * and against it, and the one button the sheet is for.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BestCover(
    suggestion: CoverSuggestion,
    useLabel: String,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = suggestion.album ?: stringResource(R.string.cover_finder_no_album)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    model = suggestion.thumbnailUrl ?: suggestion.url,
                    contentDescription = stringResource(R.string.cover_of, album),
                    modifier = Modifier.size(132.dp),
                    shape = MaterialTheme.shapes.large,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                ) {
                    when (suggestion.confidence) {
                        MatchConfidence.High ->
                            LabelChip(
                                stringResource(R.string.cover_finder_best),
                                Icons.Rounded.AutoAwesome,
                            )
                        MatchConfidence.Medium ->
                            LabelChip(
                                stringResource(R.string.cover_match_possible),
                                Icons.AutoMirrored.Rounded.HelpOutline,
                            )
                        MatchConfidence.Low ->
                            LabelChip(
                                stringResource(R.string.cover_match_weak),
                                Icons.Rounded.WarningAmber,
                            )
                    }
                    Text(
                        album,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.extraSmall),
                    )
                    Text(
                        suggestion.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                suggestion.reasons.forEach { reason -> ReasonLine(reason, suggestion) }
            }
            CoverLabels(suggestion, showConfidence = false)
            SheetButton(
                text = useLabel,
                icon = Icons.Rounded.Check,
                onClick = onUse,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One thing for the cover or against it, told apart by its mark as well as by its color. */
@Composable
private fun ReasonLine(reason: CoverReason, suggestion: CoverSuggestion) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (reason.isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.Check,
            contentDescription = null,
            tint =
                if (reason.isWarning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(reasonText(reason, suggestion), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun reasonText(reason: CoverReason, suggestion: CoverSuggestion): String =
    when (reason) {
        CoverReason.SameRelease -> stringResource(R.string.cover_reason_same_release)
        CoverReason.SameRecording -> stringResource(R.string.cover_reason_same_recording)
        CoverReason.AlbumMatches -> stringResource(R.string.cover_reason_album)
        CoverReason.ArtistMatches -> stringResource(R.string.cover_reason_artist)
        CoverReason.TitleMatches -> stringResource(R.string.cover_reason_title)
        CoverReason.YearMatches -> stringResource(R.string.cover_reason_year)
        CoverReason.SeveralSources ->
            stringResource(R.string.cover_reason_sources, suggestion.sources.size)
        CoverReason.SameAsCurrent -> stringResource(R.string.cover_reason_same_as_current)
        CoverReason.LargerThanCurrent -> stringResource(R.string.cover_reason_larger)
        CoverReason.HighResolution -> stringResource(R.string.cover_reason_high_resolution)
        CoverReason.AlbumDiffers -> stringResource(R.string.cover_reason_album_differs)
        CoverReason.VariousArtists -> stringResource(R.string.cover_reason_various)
        CoverReason.LowResolution -> stringResource(R.string.cover_reason_low_resolution)
        CoverReason.NotSquare -> stringResource(R.string.cover_reason_not_square)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OtherCover(
    suggestion: CoverSuggestion,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val warning = suggestion.reasons.firstOrNull { it.isWarning }
    SegmentedListItem(
        onClick = onClick,
        shapes = GroupShapes.listItemShapes(index, count),
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        leadingContent = {
            ArtworkImage(
                model = suggestion.thumbnailUrl ?: suggestion.url,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(suggestion.summary(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (warning != null) {
                    Text(
                        reasonText(warning, suggestion),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CoverLabels(suggestion, showConfidence = true)
            }
        },
    ) {
        Text(
            suggestion.album ?: stringResource(R.string.cover_finder_no_album),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun CoverSuggestion.summary(): String =
    listOfNotNull(artists.joinToString(", ").ifEmpty { null }, year).joinToString(" · ")

/** What the picture is, said and not pressed: how good a match, how large, and whose it is. */
@Composable
private fun CoverLabels(suggestion: CoverSuggestion, showConfidence: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        if (showConfidence) {
            val confidence = suggestion.confidence
            LabelChip(
                label =
                    stringResource(
                        when (confidence) {
                            MatchConfidence.High -> R.string.cover_match_good
                            MatchConfidence.Medium -> R.string.cover_match_possible
                            MatchConfidence.Low -> R.string.cover_match_weak
                        }
                    ),
                icon = confidence.icon,
            )
        }
        suggestion.dimensions?.let {
            LabelChip(
                stringResource(R.string.cover_finder_size, it.width, it.height),
                Icons.Rounded.AspectRatio,
            )
        }
        LabelChip(suggestion.sources.joinToString(", "), Icons.Rounded.Public)
    }
}

private val MatchConfidence.icon: ImageVector
    get() =
        when (this) {
            MatchConfidence.High -> Icons.Rounded.Verified
            MatchConfidence.Medium -> Icons.AutoMirrored.Rounded.HelpOutline
            MatchConfidence.Low -> Icons.Rounded.WarningAmber
        }
