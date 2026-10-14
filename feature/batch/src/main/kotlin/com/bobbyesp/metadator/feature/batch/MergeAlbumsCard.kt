/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioSwitch
import com.bobbyesp.metadator.core.designsystem.component.ToggleChip
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.batch.AlbumMerge
import com.bobbyesp.metadator.core.domain.batch.BatchAlbum
import com.bobbyesp.metadator.core.domain.editor.splitValues
import com.bobbyesp.metadator.core.ui.R as CoreUiR

/**
 * The albums the songs are on, when they are on more than one: one album the library shows as
 * several, spelled two ways, with an album artist on some songs only, or split into its discs. It
 * lists them, and makes them one: a title and an album artist for every song, chosen from theirs or
 * typed, as edits written with the rest.
 *
 * @param merged whether the edits already make the songs one album
 */
@Composable
internal fun MergeAlbumsCard(
    merge: AlbumMerge,
    merged: Boolean,
    separator: String,
    onMerge: (album: String, albumArtists: List<String>, discNumbers: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val motion = MaterialTheme.motionScheme
    val shape = RoundedCornerShape(12.dp)
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (colors.isDark) colors.raised else colors.ground.copy(0.4f), shape)
                .border(1.dp, if (merged) colors.accent else colors.line, shape)
                .clip(shape)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Album,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.batch_albums_title,
                        merge.albums.size,
                        merge.albums.size,
                    ),
                    style =
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                )
                Text(
                    stringResource(
                        if (merged) R.string.batch_merge_pending else R.string.batch_albums_why
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (merged) colors.accent else colors.textSecondary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The inspector shows this for any songs selected across albums: a long list would
            // push the fields down at every selection, so it is folded past a few.
            var every by rememberSaveable(merge) { mutableStateOf(false) }
            val folds = merge.albums.size > MAX_UNFOLDED
            val folded = folds && !every
            (if (folded) merge.albums.take(FOLDED) else merge.albums).forEach { album ->
                AlbumLine(album)
            }
            if (folds) {
                val more = merge.albums.size - FOLDED
                StudioButton(
                    text =
                        if (folded) pluralStringResource(R.plurals.batch_albums_more, more, more)
                        else stringResource(R.string.batch_albums_fewer),
                    onClick = { every = !every },
                    style = StudioButtonStyle.Ghost,
                )
            }
            if (merge.songsWithoutAlbum > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.batch_albums_without,
                        merge.songsWithoutAlbum,
                        merge.songsWithoutAlbum,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
        AnimatedVisibility(
            visible = !choosing,
            enter =
                fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit =
                fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
        ) {
            StudioButton(
                text = stringResource(R.string.batch_merge),
                onClick = { choosing = true },
                icon = Icons.AutoMirrored.Rounded.MergeType,
                iconTint = colors.accent,
            )
        }
        AnimatedVisibility(
            visible = choosing,
            enter =
                fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit =
                fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
        ) {
            MergeChoices(
                merge = merge,
                separator = separator,
                onMerge = { album, artists, discs ->
                    onMerge(album, artists, discs)
                    choosing = false
                },
                onCancel = { choosing = false },
            )
        }
    }
}

/** One of the albums: its title, its artist, and how many of the songs are on it. */
@Composable
private fun AlbumLine(album: BatchAlbum) {
    val colors = Studio.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                album.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.artists.joinToString(", ").ifEmpty {
                    stringResource(R.string.batch_album_no_artist)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            pluralStringResource(R.plurals.batch_album_songs, album.songs.size, album.songs.size),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

/**
 * What the one album will be called and by whom: the album with the most songs' unless another's is
 * chosen, or something else typed; and, for the discs of one release, their numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergeChoices(
    merge: AlbumMerge,
    separator: String,
    onMerge: (album: String, albumArtists: List<String>, discNumbers: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Studio.colors
    var title by rememberSaveable(merge) { mutableStateOf(merge.title) }
    var artist by rememberSaveable(merge) { mutableStateOf(merge.artists.joinToString(separator)) }
    var discs by rememberSaveable(merge) { mutableStateOf(merge.discs.isNotEmpty()) }
    val artists = splitValues(artist, separator)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TonalTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(CoreUiR.string.field_album),
                modifier = Modifier.fillMaxWidth(),
            )
            Choices(merge.titleChoices, selected = { it == title }) { title = it }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TonalTextField(
                value = artist,
                onValueChange = { artist = it },
                label = stringResource(CoreUiR.string.field_album_artist),
                modifier = Modifier.fillMaxWidth(),
            )
            Choices(
                merge.artistChoices.map { it.joinToString(separator) },
                selected = { splitValues(it, separator) == artists },
            ) {
                artist = it
            }
        }
        if (merge.discs.isNotEmpty()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = discs,
                            onValueChange = { discs = it },
                            role = Role.Switch,
                        )
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.batch_merge_discs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    Text(
                        stringResource(R.string.batch_merge_discs_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                StudioSwitch(checked = discs, onCheckedChange = null)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudioButton(
                text = stringResource(R.string.batch_merge_apply),
                onClick = { onMerge(title, artists, discs) },
                icon = Icons.AutoMirrored.Rounded.MergeType,
                style = StudioButtonStyle.Primary,
                enabled = title.isNotBlank() && artists.isNotEmpty(),
            )
            StudioButton(
                text = stringResource(R.string.batch_merge_cancel),
                onClick = onCancel,
                style = StudioButtonStyle.Ghost,
            )
        }
    }
}

/** As many albums as are listed whole; past them, [FOLDED] are, and a way to see the rest. */
private const val MAX_UNFOLDED = 4
private const val FOLDED = 3

/** The values the albums have, one chip each, the one in the field marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(
    values: List<String>,
    selected: (String) -> Boolean,
    onChoose: (String) -> Unit,
) {
    if (values.size < 2) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            ToggleChip(selected = selected(value), onClick = { onChoose(value) }, label = value)
        }
    }
}
