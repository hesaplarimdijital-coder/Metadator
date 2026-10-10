/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bobbyesp.metadator.core.designsystem.component.FactChip
import com.bobbyesp.metadator.core.designsystem.component.Kbd
import com.bobbyesp.metadator.core.designsystem.component.SheetMessage
import com.bobbyesp.metadator.core.designsystem.component.StatusPill
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.StudioTone
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.cover.CoverSuggestion
import com.bobbyesp.metadator.core.ui.R
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.input.hasHardwareKeyboard
import com.bobbyesp.metadator.lookup.api.MatchConfidence

/**
 * "Find a cover" on a wide window, as a desktop's dialog: what is searched with across its head,
 * the best cover large on the left with what speaks for it, the others as a grid on the right, and
 * at its foot how the choice was made. Esc closes it, as it does any dialog.
 */
@Composable
internal fun CoverFinderDialog(
    state: CoverFinderState,
    onQueryChange: (title: String, artist: String, album: String) -> Unit,
    onSearch: () -> Unit,
    onUse: (CoverSuggestion) -> Unit,
    onDismiss: () -> Unit,
    useLabel: String,
) {
    val colors = Studio.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.widthIn(max = 1040.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 780.dp)
                .fillMaxHeight(0.9f)
                .clip(shape)
                .background(colors.panel, shape)
                .border(1.dp, colors.lineStrong, shape)
        ) {
            DialogHead(state, onDismiss)
            QueryRow(state, onQueryChange, onSearch)
            StudioDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DialogBody(state, onUse, useLabel)
            }
            StudioDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.cover_finder_how),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun DialogHead(state: CoverFinderState, onDismiss: () -> Unit) {
    val colors = Studio.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(colors.accentSoft, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.cover_finder_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
            )
            val song = listOf(state.title, state.artist, state.album).filter { it.isNotBlank() }
            if (song.isNotEmpty()) {
                Text(
                    song.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasHardwareKeyboard()) Kbd(stringResource(R.string.key_escape))
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.shortcuts_close),
            onClick = onDismiss,
        )
    }
}

/** What is searched with, in a row, and the search. */
@Composable
private fun QueryRow(
    state: CoverFinderState,
    onQueryChange: (title: String, artist: String, album: String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TonalTextField(
            value = state.title,
            onValueChange = { onQueryChange(it, state.artist, state.album) },
            label = stringResource(R.string.field_title),
            modifier = Modifier.weight(1f),
        )
        TonalTextField(
            value = state.artist,
            onValueChange = { onQueryChange(state.title, it, state.album) },
            label = stringResource(R.string.field_artist),
            modifier = Modifier.weight(1f),
        )
        TonalTextField(
            value = state.album,
            onValueChange = { onQueryChange(state.title, state.artist, it) },
            label = stringResource(R.string.field_album),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        StudioButton(
            text = stringResource(R.string.cover_finder_search),
            onClick = onSearch,
            icon = Icons.Rounded.Search,
            style = StudioButtonStyle.Primary,
            enabled = state.status != CoverFinderStatus.Searching,
        )
    }
}

@Composable
private fun DialogBody(
    state: CoverFinderState,
    onUse: (CoverSuggestion) -> Unit,
    useLabel: String,
) {
    when (val status = state.status) {
        CoverFinderStatus.Idle -> Unit
        CoverFinderStatus.Searching ->
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator(color = Studio.colors.accent, strokeWidth = 3.dp)
                Text(
                    stringResource(R.string.cover_finder_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Studio.colors.textSecondary,
                )
            }
        is CoverFinderStatus.Results -> {
            val best = status.suggestions.firstOrNull()
            if (best == null) {
                Centered {
                    SheetMessage(
                        Icons.Rounded.SearchOff,
                        stringResource(R.string.cover_finder_no_results),
                    )
                }
            } else {
                Results(best, status.suggestions.drop(1), status.failedProviders, onUse, useLabel)
            }
        }
        CoverFinderStatus.NoProviders ->
            Centered {
                SheetMessage(
                    Icons.Rounded.TravelExplore,
                    stringResource(R.string.cover_finder_no_sources),
                )
            }
        CoverFinderStatus.Offline ->
            Centered {
                SheetMessage(
                    Icons.Rounded.CloudOff,
                    stringResource(R.string.cover_finder_offline),
                    isError = true,
                )
            }
        CoverFinderStatus.RateLimited ->
            Centered {
                SheetMessage(Icons.Rounded.HourglassTop, stringResource(R.string.cover_finder_busy))
            }
        CoverFinderStatus.Failed ->
            Centered {
                SheetMessage(
                    Icons.Rounded.ErrorOutline,
                    stringResource(R.string.cover_finder_failed),
                    isError = true,
                )
            }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 420.dp)) { content() }
    }
}

/** The best cover beside the others. */
@Composable
private fun Results(
    best: CoverSuggestion,
    others: List<CoverSuggestion>,
    failedProviders: List<String>,
    onUse: (CoverSuggestion) -> Unit,
    useLabel: String,
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(380.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BestCoverPanel(best, onUse = { onUse(best) }, useLabel = useLabel)
            if (failedProviders.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.cover_finder_partial,
                        failedProviders.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Studio.colors.textTertiary,
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Studio.colors.line))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.cover_finder_others),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = Studio.colors.text,
                )
            }
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f).scrollbar(gridState),
                state = gridState,
                contentPadding = PaddingValues(14.dp),
            ) {
                items(others, key = { it.url }) { suggestion ->
                    OtherCoverTile(suggestion) { onUse(suggestion) }
                }
            }
        }
    }
}

/** The cover to use: large, with how sure the choice is and what speaks for it. */
@Composable
private fun BestCoverPanel(suggestion: CoverSuggestion, onUse: () -> Unit, useLabel: String) {
    val colors = Studio.colors
    val album = suggestion.album ?: stringResource(R.string.cover_finder_no_album)
    Box {
        ArtworkImage(
            model = suggestion.thumbnailUrl ?: suggestion.url,
            contentDescription = stringResource(R.string.cover_of, album),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(14.dp),
        )
        Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
            val (label, tone) = suggestion.confidence.label()
            StatusPill(
                label,
                tone,
                Modifier.background(colors.panel.copy(alpha = 0.6f), RoundedCornerShape(50)),
            )
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                album,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(suggestion.summary(), suggestion.sources.joinToString(", "))
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        suggestion.dimensions?.let {
            FactChip(stringResource(R.string.cover_finder_size, it.width, it.height))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        suggestion.reasons.forEach { reason ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (reason.isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (reason.isWarning) colors.warning else colors.success,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    reasonText(reason, suggestion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSoft,
                )
            }
        }
    }
    StudioButton(
        text = useLabel,
        onClick = onUse,
        icon = Icons.Rounded.Check,
        style = StudioButtonStyle.Primary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
    )
}

/** Another cover: its picture, how large and from where, and how good a match. */
@Composable
private fun OtherCoverTile(suggestion: CoverSuggestion, onClick: () -> Unit) {
    val colors = Studio.colors
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    val album = suggestion.album ?: stringResource(R.string.cover_finder_no_album)
    val warning = suggestion.reasons.firstOrNull { it.isWarning }
    Column(
        modifier =
            Modifier.padding(6.dp)
                .clip(shape)
                .background(if (hovered) colors.hover else Color.Transparent, shape)
                .clickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkImage(
            model = suggestion.thumbnailUrl ?: suggestion.url,
            contentDescription = stringResource(R.string.cover_of, album),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            suggestion.dimensions?.let {
                Text(
                    stringResource(R.string.cover_finder_size, it.width, it.height),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Text(
                suggestion.sources.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val (label, tone) = suggestion.confidence.label()
        StatusPill(label, tone)
        if (warning != null) {
            Text(
                reasonText(warning, suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = colors.warning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MatchConfidence.label(): Pair<String, StudioTone> =
    when (this) {
        MatchConfidence.High -> stringResource(R.string.cover_finder_best) to StudioTone.Success
        MatchConfidence.Medium ->
            stringResource(R.string.cover_match_possible) to StudioTone.Warning
        MatchConfidence.Low -> stringResource(R.string.cover_match_weak) to StudioTone.Danger
    }
