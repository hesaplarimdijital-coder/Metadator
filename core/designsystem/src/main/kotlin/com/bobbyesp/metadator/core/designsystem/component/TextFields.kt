/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.Studio

/**
 * The colors of a field's well. What a field says (it has the focus, it holds something to look at,
 * what is in it is wrong) it says with its edge, and a little with its tone.
 */
@Immutable
class TonalFieldColors(
    val container: Color,
    val focusedContainer: Color,
    val emphasis: Color,
    val error: Color,
    val line: Color,
) {
    /** The well of a field in the given state. An error wins over an emphasis. */
    fun container(focused: Boolean, emphasized: Boolean = false, isError: Boolean = false): Color {
        val base = if (focused) focusedContainer else container
        return when {
            isError -> lerp(base, error, TonalFieldDefaults.ERROR_TINT)
            emphasized -> lerp(base, emphasis, TonalFieldDefaults.EMPHASIS_TINT)
            else -> base
        }
    }

    /** The edge of a field in the given state. */
    fun line(focused: Boolean, emphasized: Boolean = false, isError: Boolean = false): Color =
        when {
            isError -> error
            focused -> emphasis
            emphasized -> emphasis.copy(alpha = 0.55f)
            else -> line
        }
}

object TonalFieldDefaults {
    /**
     * How much of the emphasis color a field takes: a breath of it. After a lookup every field of a
     * song may be emphasized at once, and its edge already says it.
     */
    internal const val EMPHASIS_TINT = 0.06f
    internal const val ERROR_TINT = 0.08f

    val shape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.small

    /** The height of a field of one line. */
    val MinHeight: Dp = 36.dp

    /**
     * For a field on a panel. One on something raised passes the tone that stands out from it: a
     * lower one, where the field reads as a well.
     */
    @Composable
    @ReadOnlyComposable
    fun colors(
        container: Color = Studio.colors.field,
        focusedContainer: Color = container,
    ): TonalFieldColors =
        TonalFieldColors(
            container = container,
            focusedContainer = focusedContainer,
            emphasis = Studio.colors.accent,
            error = Studio.colors.danger,
            line = Studio.colors.fieldLine,
        )

    /** [colors] for a field in a dialog, whose surface is raised above the panels. */
    @Composable
    @ReadOnlyComposable
    fun dialogColors(): TonalFieldColors =
        colors(container = if (Studio.colors.isDark) Studio.colors.panel else Studio.colors.field)

    /** [colors] for a field in a sheet, whose surface is a raised one. */
    @Composable
    @ReadOnlyComposable
    fun sheetColors(): TonalFieldColors =
        colors(container = if (Studio.colors.isDark) Studio.colors.panel else Studio.colors.field)
}

/**
 * The app's text field: its label above it, as a desktop form has them, and a well with a hairline
 * edge. The label is part of the field: pressing it is pressing the field, and it is what the field
 * is called to a screen reader.
 *
 * The field with the focus takes the accent on its edge, with a soft ring around it; an
 * [emphasized] one too, a little quieter, and its label says why with [labelNote].
 *
 * @param label what the field holds, or null where the screen already says it
 * @param emphasized tells the field from its neighbours (the editor: what will be written on
 *   saving)
 * @param labelNote a few words after the label, in the accent ("Changed")
 */
@Composable
fun TonalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    emphasized: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TonalFieldColors = TonalFieldDefaults.colors(),
    shape: Shape = TonalFieldDefaults.shape,
    interactionSource: MutableInteractionSource? = null,
    labelNote: String? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val studio = Studio.colors
    val text = textStyle.copy(color = if (enabled) studio.text else studio.textTertiary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = text,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        interactionSource = interactions,
        cursorBrush = SolidColor(studio.accent),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (label != null) {
                    FieldLabel(label, labelNote, focused || emphasized)
                }
                FieldWell(
                    focused = focused,
                    emphasized = emphasized,
                    isError = isError,
                    colors = colors,
                    shape = shape,
                    singleLine = singleLine,
                    trailing = trailingIcon,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = text,
                            color = studio.textTertiary,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (supportingText != null) {
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) studio.danger else studio.textTertiary,
                    )
                }
            }
        },
    )
}

/** A field's label, and the note after it. */
@Composable
fun FieldLabel(label: String, note: String? = null, highlighted: Boolean = false) {
    val studio = Studio.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (highlighted) studio.textSoft else studio.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (note != null) {
            Text(
                "· $note",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = studio.accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * The well of a field, for whatever is typed in it: a [TonalTextField]'s text, or the chips of a
 * field of several values. Drawn by state, so that the two look alike.
 */
@Composable
fun FieldWell(
    focused: Boolean,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    colors: TonalFieldColors = TonalFieldDefaults.colors(),
    shape: Shape = TonalFieldDefaults.shape,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val container by
        animateColorAsState(
            targetValue = colors.container(focused, emphasized, isError),
            animationSpec = motion.fastEffectsSpec(),
            label = "FieldContainer",
        )
    val line by
        animateColorAsState(
            targetValue = colors.line(focused, emphasized, isError),
            animationSpec = motion.fastEffectsSpec(),
            label = "FieldLine",
        )
    val ringColor = Studio.colors.accentSoft
    val ring = focused || emphasized
    val corner = (shape as? RoundedCornerShape)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TonalFieldDefaults.MinHeight)
                .drawBehind {
                    if (!ring || isError) return@drawBehind
                    // A soft ring outside the edge, as a desktop draws the focus.
                    val spread = 3.dp.toPx()
                    val radius = corner?.topStart?.toPx(size, this) ?: 9.dp.toPx()
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(radius + spread),
                    )
                }
                .background(container, shape)
                .border(1.dp, line, shape)
                .padding(start = 10.dp, end = if (trailing != null) 2.dp else 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 7.dp)) { content() }
        if (trailing != null) {
            Box(Modifier.heightIn(min = TonalFieldDefaults.MinHeight - 2.dp)) { trailing() }
        }
    }
}

/**
 * The search field of a toolbar: a well with a magnifier, the key that comes to it written at its
 * end, and a way to clear it once something is typed.
 *
 * @param shortcut the keys that bring the focus here, shown while nothing is typed
 */
@Composable
fun StudioSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    shortcut: String? = null,
    height: Dp = StudioDefaults.ButtonHeight,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val studio = Studio.colors
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = StudioDefaults.ButtonShape
    val line by
        animateColorAsState(
            targetValue = if (focused) studio.accent else studio.fieldLine,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "SearchLine",
        )
    val style = MaterialTheme.typography.bodyMedium.copy(color = studio.text)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            ),
        enabled = enabled,
        singleLine = true,
        textStyle = style,
        interactionSource = interactions,
        cursorBrush = SolidColor(studio.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        decorationBox = { inner ->
            Row(
                modifier =
                    Modifier.height(height)
                        .background(studio.field, shape)
                        .border(1.dp, line, shape)
                        .padding(start = 11.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = studio.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = style,
                            color = studio.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    StudioIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = clearLabel,
                        onClick = {
                            onValueChange("")
                            focusManager.clearFocus()
                        },
                        size = 26.dp,
                        iconSize = 16.dp,
                    )
                }
                if (shortcut != null && value.isEmpty()) Kbd(shortcut)
                trailing?.invoke(this)
                if (trailing == null && (shortcut == null || value.isNotEmpty())) {
                    Spacer(Modifier.size(2.dp))
                }
            }
        },
    )
}
