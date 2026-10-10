/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.FieldLabel
import com.bobbyesp.metadator.core.designsystem.component.FieldWell
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.feature.editor.R

/** The trailing "undo this change" button every changed field gets. */
@Composable
private fun RevertButton(label: String, onRevert: () -> Unit) {
    StudioIconButton(
        icon = Icons.AutoMirrored.Rounded.Undo,
        contentDescription = stringResource(R.string.revert_field, label),
        onClick = onRevert,
        size = 30.dp,
        iconSize = 16.dp,
    )
}

/**
 * A field with one value. A changed field takes the accent on its edge, says so after its label,
 * and gets an undo button: what will be written is visible at a glance before saving.
 */
@Composable
fun TagTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    changed: Boolean,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    TonalTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        labelNote = if (changed) stringResource(R.string.changed) else null,
        modifier = modifier.fillMaxWidth().changedState(changed),
        placeholder = placeholder,
        emphasized = changed,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        trailingIcon = if (changed) ({ RevertButton(label, onRevert) }) else null,
        keyboardOptions =
            KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
    )
}

/**
 * Says that a field is changed to who cannot see its edge. Not a line of text under the field: that
 * moved everything below it down on the first key typed.
 */
@Composable
private fun Modifier.changedState(changed: Boolean): Modifier {
    if (!changed) return this
    val description = stringResource(R.string.changed)
    return semantics { stateDescription = description }
}

/**
 * A field with several values (artists, genres): one chip per value, so "Tyler, The Creator" is one
 * artist and not two. Typing and pressing enter adds a chip; tapping a chip edits it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipsField(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    changed: Boolean,
    onRevert: () -> Unit,
    separator: String,
    modifier: Modifier = Modifier,
) {
    val input = rememberTextFieldState()
    var focused by rememberSaveable(label) { mutableStateOf(false) }
    val commit = {
        val added = splitTyped(input.text.toString(), separator)
        if (added.isNotEmpty()) onValuesChange(values + added)
        input.clearText()
    }
    // Typing the separator ends a value, as a comma ends an email address.
    val latestCommit by rememberUpdatedState(commit)
    LaunchedEffect(input, separator) {
        val end = separator.trim()
        if (end.isEmpty()) return@LaunchedEffect
        snapshotFlow { input.text.toString() }
            .collect { typed ->
                if (typed.endsWith(end)) latestCommit()
            }
    }
    val inputEmpty by remember(input) { derivedStateOf { input.text.isEmpty() } }
    val focusRequester = remember { FocusRequester() }
    val colors = Studio.colors

    Column(
        modifier = modifier.fillMaxWidth().changedState(changed),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FieldLabel(
            label,
            if (changed) stringResource(R.string.changed) else null,
            focused || changed,
        )
        FieldWell(
            focused = focused,
            emphasized = changed,
            singleLine = false,
            modifier =
                Modifier.pointerInput(focusRequester) {
                    detectTapGestures { focusRequester.requestFocus() }
                },
            trailing = if (changed) ({ RevertButton(label, onRevert) }) else null,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                values.forEachIndexed { index, value ->
                    ValueChip(
                        value = value,
                        onEdit = {
                            // Editing a chip moves it back into the input.
                            commit()
                            input.setTextAndPlaceCursorAtEnd(value)
                            onValuesChange(values.filterIndexed { i, _ -> i != index })
                        },
                        onRemove = { onValuesChange(values.filterIndexed { i, _ -> i != index }) },
                    )
                }
                BasicTextField(
                    state = input,
                    modifier =
                        Modifier.widthIn(min = 90.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (focused && !it.isFocused) commit()
                                focused = it.isFocused
                            },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                    onKeyboardAction = { commit() },
                    decorator = { inner ->
                        Box(
                            Modifier.heightIn(min = 22.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (inputEmpty) {
                                Text(
                                    stringResource(
                                        if (values.isEmpty()) R.string.add_value_hint
                                        else R.string.add_value
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

/** One of the values of a field of several: pressed to edit it, with a cross to take it away. */
@Composable
private fun ValueChip(value: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier =
            Modifier.height(26.dp)
                .clip(shape)
                .background(colors.segment, shape)
                .pointerInput(onEdit) { detectTapGestures { onEdit() } }
                .padding(start = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.remove_value, value),
            onClick = onRemove,
            size = 22.dp,
            iconSize = 13.dp,
        )
    }
}

private fun splitTyped(text: String, separator: String): List<String> {
    val trimmed = separator.trim()
    val parts = if (trimmed.isEmpty()) listOf(text) else text.split(trimmed)
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}

/** A position and its total, side by side under one label: "Track 3 / 12". */
@Composable
fun PositionField(
    label: String,
    number: String,
    total: String,
    onChange: (number: String, total: String) -> Unit,
    changed: Boolean,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().changedState(changed),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FieldLabel(label, if (changed) stringResource(R.string.changed) else null, changed)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionCell(
                value = number,
                onValueChange = { onChange(it, total) },
                description = label,
                changed = changed,
                modifier = Modifier.weight(1f),
            )
            Text(
                "/",
                color = Studio.colors.textTertiary,
                style = MaterialTheme.typography.bodyLarge,
            )
            PositionCell(
                value = total,
                onValueChange = { onChange(number, it) },
                description = stringResource(CoreUiR.string.field_of),
                changed = changed,
                modifier = Modifier.weight(1f),
                trailing = if (changed) ({ RevertButton(label, onRevert) }) else null,
            )
        }
    }
}

@Composable
private fun PositionCell(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    changed: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    TonalTextField(
        value = value,
        onValueChange = onValueChange,
        label = null,
        placeholder = description,
        modifier = modifier.semantics { contentDescription = description },
        emphasized = changed,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        trailingIcon = trailing,
    )
}
