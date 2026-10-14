/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.ClosesOnEscape
import com.bobbyesp.metadator.core.designsystem.component.LabelChip
import com.bobbyesp.metadator.core.designsystem.component.SectionHeader
import com.bobbyesp.metadator.core.designsystem.component.SheetButton
import com.bobbyesp.metadator.core.designsystem.component.SheetMessage
import com.bobbyesp.metadator.core.designsystem.component.SheetQueryField
import com.bobbyesp.metadator.core.designsystem.component.rememberSheetClosing
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.theme.GroupShapes
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.domain.lookup.AlbumProposal
import com.bobbyesp.metadator.core.domain.lookup.AlbumSearchProgress
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.lookup.api.MatchConfidence

/**
 * "Find metadata" for the songs of a batch: the releases the online sources have them on, the one
 * that suits them best first. Choosing one hands it to the batch editor ([onUse]), which fills its
 * fields with it; nothing is written until "Apply to all".
 *
 * @param songCount how many songs the release would be used for, as the sheet says
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumLookupSheet(
    state: AlbumLookupState,
    songCount: Int,
    onQueryChange: (album: String, artist: String) -> Unit,
    onSearch: () -> Unit,
    onUse: (AlbumProposal) -> Unit,
    onDismiss: () -> Unit,
) {
    // No half-open state: the sheet is as tall as what it shows.
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val closing = rememberSheetClosing(sheetState)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ClosesOnEscape(sheetState, onDismiss) {
            AlbumLookupContent(
                state = state,
                songCount = songCount,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onUse = { proposal -> closing { onUse(proposal) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumLookupContent(
    state: AlbumLookupState,
    songCount: Int,
    onQueryChange: (album: String, artist: String) -> Unit,
    onSearch: () -> Unit,
    onUse: (AlbumProposal) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier =
            Modifier.fillMaxWidth()
                // The sheet grows to what it finds rather than jumping to it.
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .heightIn(min = 400.dp)
                .scrollbar(listState),
        state = listState,
        contentPadding =
            PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "query") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    stringResource(R.string.batch_lookup_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.padding(bottom = Spacing.small),
                )
                SheetQueryField(
                    label = stringResource(CoreUiR.string.field_album),
                    value = state.album,
                    onValueChange = { onQueryChange(it, state.artist) },
                )
                SheetQueryField(
                    label = stringResource(CoreUiR.string.field_album_artist),
                    value = state.artist,
                    onValueChange = { onQueryChange(state.album, it) },
                    onSearch = onSearch,
                )
                SheetButton(
                    text = stringResource(R.string.batch_lookup_search),
                    icon = Icons.Rounded.Search,
                    onClick = onSearch,
                    enabled = state.status !is AlbumLookupStatus.Searching,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                )
            }
        }
        when (val status = state.status) {
            AlbumLookupStatus.Idle -> Unit
            is AlbumLookupStatus.Searching ->
                item(key = "searching") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.huge).animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                    ) {
                        ContainedLoadingIndicator()
                        Text(
                            stringResource(R.string.batch_lookup_searching),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            progressText(status.progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            is AlbumLookupStatus.Results -> {
                if (status.proposals.isEmpty()) {
                    item(key = "state") {
                        SheetMessage(
                            Icons.Rounded.SearchOff,
                            stringResource(R.string.batch_lookup_no_results),
                            Modifier.animateItem(),
                        )
                    }
                } else {
                    item(key = "results") {
                        Column(Modifier.animateItem()) {
                            SectionHeader(stringResource(R.string.batch_lookup_results))
                            Text(
                                pluralStringResource(
                                    R.plurals.batch_lookup_how,
                                    songCount,
                                    songCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(
                                        start = Spacing.extraSmall,
                                        end = Spacing.extraSmall,
                                        bottom = Spacing.small,
                                    ),
                            )
                        }
                    }
                }
                itemsIndexed(status.proposals, key = { _, it -> it.id }) { index, proposal ->
                    ProposalItem(
                        proposal = proposal,
                        songCount = songCount,
                        index = index,
                        count = status.proposals.size,
                        modifier = Modifier.animateItem(),
                    ) {
                        onUse(proposal)
                    }
                }
                if (status.failedProviders.isNotEmpty()) {
                    item(key = "partial") {
                        Text(
                            stringResource(
                                R.string.batch_lookup_partial,
                                status.failedProviders.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier.fillMaxWidth().padding(top = Spacing.medium).animateItem(),
                        )
                    }
                }
            }
            AlbumLookupStatus.NoProviders ->
                item(key = "state") {
                    SheetMessage(
                        Icons.Rounded.TravelExplore,
                        stringResource(R.string.batch_lookup_no_sources),
                        Modifier.animateItem(),
                    )
                }
            AlbumLookupStatus.Offline ->
                item(key = "state") {
                    SheetMessage(
                        Icons.Rounded.CloudOff,
                        stringResource(R.string.batch_lookup_offline),
                        Modifier.animateItem(),
                        isError = true,
                    )
                }
            AlbumLookupStatus.RateLimited ->
                item(key = "state") {
                    SheetMessage(
                        Icons.Rounded.HourglassTop,
                        stringResource(R.string.batch_lookup_busy),
                        Modifier.animateItem(),
                    )
                }
            AlbumLookupStatus.Failed ->
                item(key = "state") {
                    SheetMessage(
                        Icons.Rounded.ErrorOutline,
                        stringResource(R.string.batch_lookup_failed),
                        Modifier.animateItem(),
                        isError = true,
                    )
                }
        }
    }
}

/** One release: its cover, what it is, and what speaks for it. Choosing it is pressing it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProposalItem(
    proposal: AlbumProposal,
    songCount: Int,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tracks =
        proposal.trackCount?.let { pluralStringResource(R.plurals.batch_lookup_tracks, it, it) }
    val summary =
        listOfNotNull(
                proposal.albumArtists.joinToString(", ").ifEmpty { null },
                proposal.year,
                tracks,
                proposal.genres.joinToString(", ").ifEmpty { null },
            )
            .joinToString(" · ")
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
                model = proposal.artworkThumbnailUrl ?: proposal.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                if (summary.isNotEmpty()) {
                    Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ProposalLabels(proposal, songCount)
            }
        },
    ) {
        Text(
            proposal.album,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How good a match it is, how many of the songs are on it, and whose it is: said, not pressed. */
@Composable
private fun ProposalLabels(proposal: AlbumProposal, songCount: Int) {
    val confidence = proposal.confidence
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        LabelChip(
            label =
                stringResource(
                    when (confidence) {
                        MatchConfidence.High -> R.string.batch_lookup_match_good
                        MatchConfidence.Medium -> R.string.batch_lookup_match_possible
                        MatchConfidence.Low -> R.string.batch_lookup_match_weak
                    }
                ),
            icon =
                when (confidence) {
                    MatchConfidence.High -> Icons.Rounded.Verified
                    MatchConfidence.Medium -> Icons.AutoMirrored.Rounded.HelpOutline
                    MatchConfidence.Low -> Icons.Rounded.WarningAmber
                },
        )
        if (songCount > 0) {
            LabelChip(
                pluralStringResource(
                    R.plurals.batch_lookup_songs_found,
                    songCount,
                    proposal.songsFound,
                    songCount,
                ),
                Icons.Rounded.MusicNote,
            )
        }
        LabelChip(proposal.sources.joinToString(", "), Icons.Rounded.Public)
    }
}

/** How far the search has got: the album asked for, then song after song. */
@Composable
private fun progressText(progress: AlbumSearchProgress?): String =
    if (progress == null || progress.searched == 0) {
        stringResource(R.string.batch_lookup_asking)
    } else {
        pluralStringResource(
            R.plurals.batch_lookup_progress,
            progress.of,
            progress.searched,
            progress.of,
        )
    }
