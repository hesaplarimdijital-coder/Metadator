/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.component.LoadingScreen
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.StudioSwitch
import com.bobbyesp.metadator.core.designsystem.component.ToggleChip
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.batch.BatchProgress
import com.bobbyesp.metadator.core.domain.batch.FileNamePattern
import com.bobbyesp.metadator.core.domain.batch.TrackNumbering
import com.bobbyesp.metadator.core.domain.save.SaveOutcome
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.core.ui.component.fieldLabel
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import com.bobbyesp.metadator.tags.api.ArtworkChange
import com.bobbyesp.metadator.tags.api.TagField

/**
 * Editing several songs at once. Beside the library it is the inspector, as the editor of one song
 * is, with the selection still in sight: what all of them will get, the cover first, then the
 * fields, then the tools; at its foot, what will be written and the way to write it.
 */
@Composable
internal fun BatchScreen(state: BatchState, onIntent: (BatchIntent) -> Unit, onClose: () -> Unit) {
    val pickCover =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onIntent(BatchIntent.SetCoverFromUri(uri.toString()))
        }
    KeyboardShortcuts(
        title = stringResource(R.string.shortcuts_batch),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_S,
                    stringResource(R.string.batch_apply),
                    ctrl = true,
                    enabled = state.canApply,
                ) {
                    onIntent(BatchIntent.Apply)
                },
                Shortcut(
                    KeyEvent.KEYCODE_K,
                    stringResource(R.string.batch_cover_find),
                    ctrl = true,
                    enabled = !state.loading,
                ) {
                    onIntent(BatchIntent.FindCover)
                },
                Shortcut(
                    KeyEvent.KEYCODE_W,
                    stringResource(R.string.close),
                    ctrl = true,
                    action = onClose,
                ),
            ),
    )
    val panels = LocalPanels.current
    Column(
        Modifier.fillMaxSize()
            .then(if (panels) Modifier.studioPanel() else Modifier.statusBarsPadding())
    ) {
        if (state.loading) {
            BatchHead(state, onClose, Modifier.padding(16.dp))
            LoadingScreen(Modifier.weight(1f))
            return@Column
        }
        val scroll = rememberScrollState()
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .scrollbar(scroll)
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BatchHead(state, onClose)
            CoverChoice(
                cover = state.cover,
                loading = state.loadingCover,
                onFind = { onIntent(BatchIntent.FindCover) },
                onPick = {
                    pickCover.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemove = { onIntent(BatchIntent.RemoveCovers) },
                onKeep = { onIntent(BatchIntent.KeepCovers) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.batch_fields),
                        style =
                            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Studio.colors.textSoft,
                    )
                    Text(
                        stringResource(R.string.batch_fields_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Studio.colors.textSecondary,
                    )
                }
                BatchViewModel.EditableFields.forEach { field ->
                    BatchField(field, state, onIntent)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.batch_tools),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Studio.colors.textSoft,
                )
                Tools(state, onIntent)
            }
        }
        BatchFooter(state, onIntent, phone = !panels)
    }

    state.progress?.let { progress -> ProgressDialog(progress, onIntent) }
}

/** The songs being edited: a few of their covers stacked, how many, and which. */
@Composable
private fun BatchHead(state: BatchState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val names = state.files.take(2).joinToString(", ") { it.title }
    val more = state.files.size - 2
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 76.dp, height = 60.dp)) {
            repeat(3) { layer ->
                val index = 2 - layer
                Box(
                    Modifier.offset(x = (index * 12).dp, y = ((2 - index) * 6).dp)
                        .size(48.dp)
                        .shadow(2.dp, RoundedCornerShape(9.dp))
                        .background(Studio.colors.raisedHigh, RoundedCornerShape(9.dp))
                        .border(2.dp, Studio.colors.panel, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Studio.colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pluralStringResource(R.plurals.batch_title, state.files.size, state.files.size),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = Studio.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (names.isNotEmpty()) {
                Text(
                    if (more > 0) "$names ${stringResource(R.string.batch_and_more, more)}"
                    else names,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Studio.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
        )
    }
}

@Composable
private fun BatchField(field: TagField, state: BatchState, onIntent: (BatchIntent) -> Unit) {
    val label = fieldLabel(field.key)
    val edited = state.edits[field.key]
    val common = state.common[field.key]
    val shown =
        edited?.joinToString(state.separator)
            ?: (common as? CommonValue.Same)?.values?.joinToString(state.separator).orEmpty()
    TonalTextField(
        value = shown,
        onValueChange = { text ->
            val values =
                if (field.multiValue)
                    text.split(state.separator.trim()).map { it.trim() }.filter { it.isNotEmpty() }
                else listOf(text)
            onIntent(BatchIntent.SetField(field.key, values))
        },
        label = label,
        labelNote = if (edited != null) stringResource(R.string.batch_changed) else null,
        placeholder =
            if (common == CommonValue.Mixed && edited == null)
                stringResource(R.string.batch_multiple_values)
            else null,
        emphasized = edited != null,
        trailingIcon =
            if (edited != null) {
                {
                    StudioIconButton(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.batch_reset_field, label),
                        onClick = { onIntent(BatchIntent.ResetField(field.key)) },
                        size = 30.dp,
                        iconSize = 16.dp,
                    )
                }
            } else null,
        singleLine = field != TagField.Comment,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The cover every song will get, or that they keep their own, and how to change that. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverChoice(
    cover: ArtworkChange,
    loading: Boolean,
    onFind: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (colors.isDark) colors.raised else colors.ground.copy(0.4f), shape)
                .border(1.dp, colors.line, shape)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            when (cover) {
                is ArtworkChange.Replace ->
                    ArtworkImage(
                        cover.picture.data,
                        null,
                        Modifier.size(64.dp),
                        shape = RoundedCornerShape(10.dp),
                    )
                else ->
                    Box(
                        Modifier.size(64.dp)
                            .background(colors.raisedHigh, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (cover == ArtworkChange.Remove) Icons.Rounded.HideImage
                            else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = colors.textTertiary,
                        )
                    }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.accent,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        when {
                            loading -> R.string.batch_cover_downloading
                            cover is ArtworkChange.Replace -> R.string.batch_cover_will_replace
                            cover == ArtworkChange.Remove -> R.string.batch_cover_will_remove
                            else -> R.string.batch_cover_unchanged
                        }
                    ),
                    style =
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (cover == ArtworkChange.Remove) colors.danger else colors.text,
                )
                Text(
                    stringResource(R.string.batch_cover),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StudioButton(
                    text = stringResource(R.string.batch_cover_find),
                    onClick = onFind,
                    icon = Icons.Rounded.AutoAwesome,
                    iconTint = colors.accent,
                    shortcut = shortcutHint(KeyEvent.KEYCODE_K, ctrl = true),
                )
                StudioButton(
                    text = stringResource(R.string.batch_cover_set),
                    onClick = onPick,
                    icon = Icons.Rounded.Image,
                )
                StudioButton(
                    text = stringResource(R.string.batch_cover_remove),
                    onClick = onRemove,
                    icon = Icons.Rounded.HideImage,
                    style = StudioButtonStyle.Ghost,
                )
                if (cover != ArtworkChange.Unchanged) {
                    StudioButton(
                        text = stringResource(R.string.batch_cover_keep),
                        onClick = onKeep,
                        style = StudioButtonStyle.Ghost,
                    )
                }
            }
        }
    }
}

/** A tool as a card: what it does, and its switch; once on, what it is set to. */
@Composable
private fun ToolCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit = {},
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, colors.lineStrong, shape).clip(shape)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .toggleable(
                        value = checked,
                        onValueChange = onCheckedChange,
                        role = Role.Switch,
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            StudioSwitch(checked = checked, onCheckedChange = null)
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(state: BatchState, onIntent: (BatchIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolCard(
            title = stringResource(R.string.batch_numbering),
            description = stringResource(R.string.batch_numbering_description, state.files.size),
            checked = state.numbering != null,
            onCheckedChange = {
                onIntent(BatchIntent.SetNumbering(if (it) TrackNumbering() else null))
            },
        ) {
            state.numbering?.let { numbering ->
                TonalTextField(
                    value = numbering.startAt.toString(),
                    onValueChange = { text ->
                        text
                            .toIntOrNull()
                            ?.takeIf { it in 0..999 }
                            ?.let {
                                onIntent(BatchIntent.SetNumbering(numbering.copy(startAt = it)))
                            }
                    },
                    label = stringResource(R.string.batch_numbering_start),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    inputTransformation = NumberingStartInput,
                    modifier =
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).width(140.dp),
                )
            }
        }
        ToolCard(
            title = stringResource(R.string.batch_pattern),
            description = stringResource(R.string.batch_pattern_description),
            checked = state.usePattern,
            onCheckedChange = { onIntent(BatchIntent.UsePattern(it)) },
        ) {
            if (state.usePattern) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val valid = FileNamePattern(state.pattern).isValid
                    TonalTextField(
                        value = state.pattern,
                        onValueChange = { onIntent(BatchIntent.SetPattern(it)) },
                        label = stringResource(R.string.batch_pattern_field),
                        isError = !valid,
                        supportingText =
                            if (!valid) stringResource(R.string.batch_pattern_invalid) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FileNamePattern.Suggestions.forEach { suggestion ->
                            ToggleChip(
                                selected = suggestion == state.pattern,
                                onClick = { onIntent(BatchIntent.SetPattern(suggestion)) },
                                label = suggestion,
                            )
                        }
                    }
                    state.patternPreview.forEach { (name, parsed) ->
                        // Labels first: a composable cannot be called from joinToString's lambda.
                        val described =
                            parsed?.entries?.map { (key, values) ->
                                fieldLabel(key) to values.joinToString()
                            }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelLarge,
                                color = Studio.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                described?.joinToString(" · ") { (label, value) ->
                                    "$label: $value"
                                } ?: stringResource(R.string.batch_pattern_no_match),
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (parsed == null) Studio.colors.danger
                                    else Studio.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What will be written, and to how many songs; and the button that writes it. */
@Composable
private fun BatchFooter(state: BatchState, onIntent: (BatchIntent) -> Unit, phone: Boolean) {
    val colors = Studio.colors
    Column(
        Modifier.fillMaxWidth()
            .background(if (phone) colors.ground else colors.panel)
            .then(if (phone) Modifier.navigationBarsPadding() else Modifier)
    ) {
        StudioDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.canApply) {
                    pluralStringResource(
                        R.plurals.batch_will_write,
                        state.files.size,
                        state.files.size,
                    )
                } else stringResource(R.string.batch_nothing_yet),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StudioButton(
                text = stringResource(R.string.batch_apply),
                onClick = { onIntent(BatchIntent.Apply) },
                style = StudioButtonStyle.Primary,
                enabled = state.canApply,
                shortcut = shortcutHint(KeyEvent.KEYCODE_S, ctrl = true),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressDialog(
    progress: BatchProgress,
    onIntent: (BatchIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon =
            if (!progress.isRunning) {
                { Icon(Icons.Rounded.CheckCircle, null) }
            } else null,
        title = {
            Text(
                if (progress.isRunning)
                    stringResource(R.string.batch_running, progress.done + 1, progress.total)
                else stringResource(R.string.batch_done_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                if (progress.isRunning) {
                    LinearWavyProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        pluralStringResource(
                            R.plurals.batch_saved,
                            progress.saved.size,
                            progress.saved.size,
                        )
                    )
                    if (progress.wasCancelled) Text(stringResource(R.string.batch_cancelled))
                    if (progress.failed.isNotEmpty()) {
                        Text(
                            pluralStringResource(
                                R.plurals.batch_failed,
                                progress.failed.size,
                                progress.failed.size,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                        progress.failed.take(5).forEach { result ->
                            Text(
                                "${result.item.displayName}: ${stringResource(reason(result.outcome))}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress.isRunning) {
                TextButton(onClick = { onIntent(BatchIntent.Cancel) }) {
                    Text(stringResource(R.string.batch_cancel))
                }
            } else {
                TextButton(onClick = { onIntent(BatchIntent.Dismiss) }) {
                    Text(stringResource(R.string.batch_finish))
                }
            }
        },
        dismissButton =
            if (!progress.isRunning && progress.backups.isNotEmpty()) {
                {
                    TextButton(onClick = { onIntent(BatchIntent.UndoAll) }) {
                        Text(stringResource(R.string.batch_undo_all))
                    }
                }
            } else null,
    )
}

private fun reason(outcome: SaveOutcome): Int =
    when (outcome) {
        SaveOutcome.NeedsAccess -> R.string.batch_reason_access
        SaveOutcome.FileGone -> R.string.batch_reason_gone
        SaveOutcome.Unsupported -> R.string.batch_reason_unsupported
        else -> R.string.batch_reason_failed
    }

/** What the first track number takes: up to three digits, turned down as they are typed. */
private val NumberingStartInput = InputTransformation {
    if (length > 3 || !asCharSequence().all(Char::isDigit)) revertAllChanges()
}
