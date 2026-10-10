/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round

/*
 * What a mouse adds to a screen made for fingers. A pointer rests on things, so a button that is
 * only an icon can say what it does; it has a second button, which every desktop gives a menu to;
 * and it is used with a hand on the keyboard, so Ctrl and Shift change what a click selects.
 */

/**
 * Says what [content] does when the pointer rests on it, or a finger is held on it: for a button
 * that is only an icon, whose name was until now only told to a screen reader.
 *
 * The pointer is an arrow over it, whatever it is in: a text field's I-beam reached the buttons at
 * the field's ends, and made them look like more of the text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier.pointerHoverIcon(PointerIcon.Default),
        content = content,
    )
}

/**
 * Gives [content] a menu for the mouse's secondary button (or a stylus's), opened where the pointer
 * is. [actions] is asked for when the menu opens, so it can depend on what is selected by then.
 *
 * The click is taken before [content] sees it. Left alone it would be a tap like any other, since
 * Android does not tell the buttons of a mouse apart for a tap: a right click on a song would open
 * it.
 */
@Composable
fun ContextMenuArea(
    actions: () -> List<MenuAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val currentActions by rememberUpdatedState(actions)
    var open by remember { mutableStateOf(false) }
    // A list has one of these for each of its rows, and most rows are never clicked this way: the
    // menu is only made once it has been asked for.
    var asked by remember { mutableStateOf(false) }
    // Kept after the menu closes: it is still there, fading, where it was.
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    var shown by remember { mutableStateOf(emptyList<MenuAction>()) }
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                down.consume()
                val offered = currentActions()
                if (offered.isNotEmpty()) {
                    shown = offered
                    anchor = down.position.round()
                    asked = true
                    open = true
                }
                // The rest of the click is nobody's: neither a tap nor a long press of what is
                // under it.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        content()
        if (asked) {
            // A point for the menu to hang from, where the pointer was.
            Box(Modifier.offset { anchor }.size(0.dp)) {
                ActionMenu(expanded = open, onDismissRequest = { open = false }, actions = shown)
            }
        }
    }
}

/** Which modifier keys were down the last time the pointer did something over an element. */
@Stable
class HeldKeys {
    var ctrl: Boolean = false
        internal set

    var shift: Boolean = false
        internal set

    /**
     * Forgets them once a click has been acted on. The next press says them again; a key pressed
     * instead says nothing, and must not be taken for a click with what the last one held.
     */
    fun clear() {
        ctrl = false
        shift = false
    }
}

/**
 * Keeps [keys] up to date with every press, move and release over this element, without taking any
 * of them: what is clicked can then ask what was held as it was.
 */
fun Modifier.trackHeldKeys(keys: HeldKeys): Modifier =
    pointerInput(keys) {
        awaitPointerEventScope {
            while (true) {
                val modifiers = awaitPointerEvent(PointerEventPass.Initial).keyboardModifiers
                keys.ctrl = modifiers.isCtrlPressed
                keys.shift = modifiers.isShiftPressed
            }
        }
    }
