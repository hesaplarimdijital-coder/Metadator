/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.component.ActionMenu
import com.bobbyesp.metadator.core.designsystem.component.FactChip
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioTone
import com.bobbyesp.metadator.core.designsystem.component.Tooltip
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.image.ImageInfo
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import com.bobbyesp.metadator.feature.editor.R

/**
 * The cover and what it is: the picture, and what can be done with it from its menu.
 *
 * @param preview a small copy of a cover on its way, shown in its place while [loading]
 * @param placeholderKey where the library's thumbnail of this cover is cached, to start from
 */
class CoverModel(
    val cover: ByteArray?,
    val preview: String? = null,
    val placeholderKey: String? = null,
    val info: ImageInfo?,
    val changed: Boolean,
    val hasOriginal: Boolean,
    val loading: Boolean,
    val warnSmall: Boolean,
)

/** What the editor's head can be asked to do with the cover and the song. */
class CoverActions(
    val onChange: () -> Unit,
    val onFind: () -> Unit,
    val onRemove: () -> Unit,
    val onRevert: () -> Unit,
    val onPlay: () -> Unit,
)

/**
 * The head of the editor: the cover, the song's name and where it is from, and what its file is. In
 * the inspector the cover is beside the name, as a desktop's inspector has it; on a phone it is
 * above it, centered, as large as a hand can see.
 *
 * Pressing the cover opens what can be done with it; a picture dragged onto it is taken
 * ([imageDropTarget], the caller's).
 *
 * @param details the album, the year and the track, in a line
 * @param facts what the file is, as chips: its format, the cover's size
 * @param trailing what the inspector has at the head's end: its menu, and the way to close it
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorHeader(
    title: String,
    artist: String?,
    details: String?,
    facts: List<String>,
    cover: CoverModel,
    actions: CoverActions,
    isPlaying: Boolean,
    centered: Boolean,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Studio.colors
    val text: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (centered) 22.sp else 19.sp,
                        lineHeight = if (centered) 28.sp else 25.sp,
                    ),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            if (artist != null) {
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (centered) colors.textSecondary else colors.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (details != null) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (facts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp,
                            if (centered) Alignment.CenterHorizontally else Alignment.Start,
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { FactChip(it) }
                    if (cover.warnSmall && cover.info?.isSmall == true) {
                        FactChip(
                            stringResource(R.string.cover_small_warning),
                            tone = StudioTone.Warning,
                        )
                    }
                }
            }
        }
    }
    if (centered) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(
                cover,
                actions,
                isPlaying,
                size = 172.dp,
                corner = 18.dp,
                modifier = coverModifier,
            )
            text(Alignment.CenterHorizontally)
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Cover(
                cover,
                actions,
                isPlaying,
                size = 112.dp,
                corner = 12.dp,
                modifier = coverModifier,
            )
            Box(Modifier.weight(1f).padding(top = 2.dp)) { text(Alignment.Start) }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

/**
 * The cover, which opens what can be done with it: another picture from the device, one found
 * online, none, or the file's own back. A small button on it plays the song.
 */
@Composable
private fun Cover(
    cover: CoverModel,
    actions: CoverActions,
    isPlaying: Boolean,
    size: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size)) {
        Tooltip(stringResource(R.string.cover)) {
            ArtworkImage(
                model = if (cover.loading) cover.preview ?: cover.cover else cover.cover,
                contentDescription = stringResource(R.string.cover),
                placeholderKey = cover.placeholderKey,
                modifier =
                    Modifier.size(size)
                        .shadow(10.dp, shape, ambientColor = Studio.colors.ground)
                        .clip(shape)
                        .clickable(
                            interactionSource = null,
                            indication = ripple(),
                            role = Role.Button,
                            onClick = { menu = true },
                        ),
                shape = shape,
            )
        }
        if (cover.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                color = Studio.colors.accent,
                strokeWidth = 2.5.dp,
            )
        }
        val playLabel = stringResource(if (isPlaying) R.string.pause_song else R.string.play_song)
        Tooltip(playLabel, Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
            Box(
                Modifier.size(30.dp)
                    .clip(CircleShape)
                    .background(Studio.colors.accentFill, CircleShape)
                    .clickable(
                        interactionSource = null,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = actions.onPlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = playLabel,
                    tint = Studio.colors.onAccentFill,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            ActionMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                actions =
                    listOfNotNull(
                        MenuAction(
                            stringResource(R.string.cover_find_online),
                            Icons.Rounded.AutoAwesome,
                            shortcut = shortcutHint(KeyEvent.KEYCODE_K, ctrl = true),
                            iconTint = Studio.colors.accent,
                            onClick = actions.onFind,
                        ),
                        MenuAction(
                            stringResource(R.string.cover_change),
                            Icons.Outlined.Image,
                            shortcut = shortcutHint(KeyEvent.KEYCODE_K, ctrl = true, shift = true),
                            onClick = actions.onChange,
                        ),
                        MenuAction(
                                stringResource(R.string.cover_revert),
                                Icons.AutoMirrored.Rounded.Undo,
                                onClick = actions.onRevert,
                            )
                            .takeIf { cover.changed && cover.hasOriginal },
                        MenuAction(
                                stringResource(R.string.cover_remove),
                                Icons.Rounded.Delete,
                                onClick = actions.onRemove,
                            )
                            .takeIf { cover.cover != null },
                    ),
            )
        }
    }
}

/**
 * What the editor finds online, as three buttons of the same width: a cover, the rest of the tags,
 * the lyrics.
 *
 * @param stacked each button's icon over its name, on a phone: a third of its width is too little
 *   for an icon and a name side by side in every language and at every size of text ("Kapak bul"
 *   was cut short)
 */
@Composable
fun EditorActions(
    onFindCover: () -> Unit,
    onFindMetadata: () -> Unit,
    onFindLyrics: () -> Unit,
    findingLyrics: Boolean,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(if (stacked) 8.dp else 6.dp),
    ) {
        EditorAction(
            text = stringResource(R.string.action_find_cover),
            icon = Icons.Rounded.AutoAwesome,
            onClick = onFindCover,
            stacked = stacked,
            iconTint = Studio.colors.accent,
            modifier = Modifier.weight(1f),
        )
        EditorAction(
            text = stringResource(R.string.action_metadata),
            icon = Icons.Outlined.Sell,
            onClick = onFindMetadata,
            stacked = stacked,
            modifier = Modifier.weight(1f),
        )
        EditorAction(
            text = stringResource(R.string.action_lyrics),
            icon = Icons.Outlined.Lyrics,
            onClick = onFindLyrics,
            stacked = stacked,
            busy = findingLyrics,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    busy: Boolean = false,
) {
    if (!stacked) {
        StudioButton(
            text = text,
            onClick = onClick,
            icon = icon,
            iconTint = iconTint,
            busy = busy,
            modifier = modifier.fillMaxHeight().heightIn(min = 34.dp),
        )
        return
    }
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(shape)
                .background(colors.field, shape)
                .border(1.dp, colors.fieldLine, shape)
                .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = if (iconTint.isSpecified) iconTint else colors.text,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Why the library says this song needs attention: the essential tags it is still missing, before
 * the fields that would fix it.
 */
@Composable
fun AttentionNotice(missing: List<String>, modifier: Modifier = Modifier) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.warningSoft, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.needs_attention_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            Text(
                missing.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * Fields whose text looks garbled, as a tag decoded with the wrong character set does, each with
 * what it most likely says ("Album artist: Moğollar?"), and the one action that takes those as
 * changes to save.
 *
 * @param suggestions each field's name and its text put right
 */
@Composable
fun GarbledTextNotice(
    suggestions: List<Pair<String, String>>,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.warningSoft, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                pluralStringResource(R.plurals.garbled_text_title, suggestions.size),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            suggestions.forEach { (field, text) ->
                Text(
                    stringResource(R.string.garbled_text_suggestion, field, text),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StudioButton(text = stringResource(R.string.garbled_text_repair), onClick = onRepair)
    }
}
