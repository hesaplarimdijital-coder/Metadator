/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import kotlinx.coroutines.launch

/*
 * What the sheets that search online are made of: the metadata lookup and the cover finder ask
 * the same way, say the same way that nothing came back, and close the same way.
 */

/**
 * Closes the sheet [sheetState] drives with Esc, as a dialog closes, sliding it away first as a tap
 * beside it does. Material's sheet does not: its window takes Esc for a cancel it ignores.
 *
 * The key only reaches what has the focus or holds it, and on a desktop the sheet is opened from
 * the keyboard as often as not, with nothing in it clicked. So [content] takes the focus as the
 * sheet opens, and again when what had it goes, as a field does when a sheet moves on to its next
 * step. A field still takes it when clicked or tabbed to, and Esc still comes here first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosesOnEscape(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val focus = remember { FocusRequester() }
    // Whether the focus is here or in something inside, as last told.
    val holdsFocus = remember { mutableStateOf(false) }
    // Once the sheet is up, and not before: until then its window may not take the focus. Not if
    // a field was clicked meanwhile, either: that is where the focus is wanted.
    LaunchedEffect(sheetState, focus) {
        snapshotFlow { sheetState.isVisible }
            .collect { visible ->
                if (visible && !holdsFocus.value) runCatching { focus.requestFocus() }
            }
    }
    Box(
        modifier
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Escape) return@onPreviewKeyEvent false
                // Both halves are taken, so that the window gets neither.
                if (event.type == KeyEventType.KeyUp) {
                    scope
                        .launch { sheetState.hide() }
                        .invokeOnCompletion { if (!sheetState.isVisible) currentOnDismiss() }
                }
                true
            }
            .onFocusChanged { state ->
                val lost = holdsFocus.value && !state.hasFocus
                holdsFocus.value = state.hasFocus
                // Taken back later, not now: when it is the sheet that goes, its scope is gone by
                // then and nothing is asked of what is leaving.
                if (lost && sheetState.isVisible) {
                    scope.launch { runCatching { focus.requestFocus() } }
                }
            }
            .focusRequester(focus)
            .focusable()
    ) {
        content()
    }
}

/**
 * One of the fields a search is made with. The last one searches from the keyboard ([onSearch]);
 * the others move on to the next.
 */
@Composable
fun SheetQueryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    TonalTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions =
            KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Next),
        onKeyboardAction = onSearch?.let { search -> KeyboardActionHandler { search() } },
        colors = TonalFieldDefaults.sheetColors(),
    )
}

/**
 * The one thing to do at each stage: the main button, as wide as the sheet, so that it is found
 * without being looked for.
 */
@Composable
fun SheetButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    StudioButton(
        text = text,
        onClick = onClick,
        icon = icon,
        style = StudioButtonStyle.Primary,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
    )
}

/** Why there are no results, under a tinted square as the app's empty screens have. */
@Composable
fun SheetMessage(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val tone = (if (isError) StudioTone.Danger else StudioTone.Accent).colors()
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Box(
            Modifier.size(56.dp).background(tone.container, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Studio.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
