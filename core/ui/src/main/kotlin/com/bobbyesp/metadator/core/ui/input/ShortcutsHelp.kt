/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.input

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.ui.R
import kotlinx.coroutines.launch

/**
 * The shortcuts every screen of an activity has, and the list of all of them when it is asked for.
 * Goes once in the content of each activity that owns a [ShortcutDispatcher].
 */
@Composable
fun ShortcutsHelp() {
    val dispatcher = LocalShortcutDispatcher.current ?: return
    val show = { dispatcher.isHelpRequested = true }
    KeyboardShortcuts(
        title = stringResource(R.string.shortcuts_group_general),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_F1,
                    stringResource(R.string.shortcut_show_shortcuts),
                    action = show,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_SLASH,
                    stringResource(R.string.shortcut_show_shortcuts),
                    ctrl = true,
                    action = show,
                ),
            ),
    )
    if (dispatcher.isHelpRequested) {
        ShortcutsDialog(dispatcher.groups, onDismiss = { dispatcher.isHelpRequested = false })
    }
}

/**
 * Whether a keyboard with real keys is plugged in or paired: a way to the list is worth showing.
 */
@Composable
fun hasHardwareKeyboard(): Boolean =
    LocalConfiguration.current.keyboard == Configuration.KEYBOARD_QWERTY

@Composable
private fun ShortcutsDialog(groups: List<ShortcutGroup>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
        title = { Text(stringResource(R.string.shortcuts_title)) },
        text = {
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val line = with(LocalDensity.current) { LineStep.toPx() }
            // The list is longer than a small window, and whoever asked for it with a key has no
            // wheel to move it with. So it takes the focus, and the keys that move through a page
            // move through it. Tab still goes on to the button, and Esc closes.
            val listFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { listFocus.requestFocus() } }
            Column(
                modifier =
                    Modifier.scrollbar(scroll, alwaysVisible = true)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val page = scroll.viewportSize * PageShare
                            when (event.key) {
                                Key.DirectionDown -> scope.launch { scroll.scrollBy(line) }
                                Key.DirectionUp -> scope.launch { scroll.scrollBy(-line) }
                                Key.PageDown -> scope.launch { scroll.scrollBy(page) }
                                Key.PageUp -> scope.launch { scroll.scrollBy(-page) }
                                Key.MoveHome -> scope.launch { scroll.scrollTo(0) }
                                Key.MoveEnd -> scope.launch { scroll.scrollTo(scroll.maxValue) }
                                else -> return@onKeyEvent false
                            }
                            true
                        }
                        .verticalScroll(scroll)
                        .focusRequester(listFocus)
                        .focusable()
                        // Room for the bar that says where in the list this is.
                        .padding(end = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                // The screen in front first: it is the one whose keys were looked for.
                groups
                    .asReversed()
                    .filter { it.shortcuts.isNotEmpty() }
                    .forEach { group ->
                        GroupTitle(group.title)
                        group.shortcuts
                            // A thing with two shortcuts is listed once, with the first.
                            .distinctBy { it.label }
                            .forEach { shortcut -> ShortcutRow(shortcut.label, shortcut.keys()) }
                    }
                GroupTitle(stringResource(R.string.shortcuts_group_keys))
                ShortcutRow(
                    stringResource(R.string.shortcut_back),
                    listOf(stringResource(R.string.key_escape)),
                )
                ShortcutRow(
                    stringResource(R.string.shortcut_next_control),
                    listOf(stringResource(R.string.key_tab)),
                )
                ShortcutRow(
                    stringResource(R.string.shortcut_activate),
                    listOf(stringResource(R.string.key_enter)),
                )
                GroupTitle(stringResource(R.string.shortcuts_group_mouse))
                ShortcutRow(
                    stringResource(R.string.shortcut_mouse_menu),
                    listOf(stringResource(R.string.mouse_right_click)),
                )
                ShortcutRow(
                    stringResource(R.string.shortcut_mouse_add),
                    listOf(stringResource(R.string.key_ctrl), stringResource(R.string.mouse_click)),
                )
                ShortcutRow(
                    stringResource(R.string.shortcut_mouse_range),
                    listOf(
                        stringResource(R.string.key_shift),
                        stringResource(R.string.mouse_click),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shortcuts_close)) }
        },
    )
}

/** How far an arrow key moves the list: about a row of it. */
private val LineStep = 48.dp

/** How much of what is in sight a page key moves the list by: the rest stays, to read on from. */
private const val PageShare = 0.85f

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.small),
    )
}

@Composable
private fun ShortcutRow(label: String, keys: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            keys.forEachIndexed { index, key ->
                if (index > 0) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KeyCap(key)
            }
        }
    }
}

/** A key as it looks on the keyboard: its legend on a little square of its own. */
@Composable
private fun KeyCap(legend: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            legend,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
        )
    }
}

/** The keys of the shortcut, modifiers first, as their legends. */
@Composable
private fun Shortcut.keys(): List<String> {
    val keys = mutableListOf<String>()
    if (ctrl) keys += stringResource(R.string.key_ctrl)
    if (alt) keys += stringResource(R.string.key_alt)
    if (shift) keys += stringResource(R.string.key_shift)
    keys += keyLegend(keyCode)
    return keys
}

@Composable
private fun keyLegend(keyCode: Int): String =
    when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> stringResource(R.string.key_space)
        KeyEvent.KEYCODE_ENTER -> stringResource(R.string.key_enter)
        KeyEvent.KEYCODE_ESCAPE -> stringResource(R.string.key_escape)
        KeyEvent.KEYCODE_TAB -> stringResource(R.string.key_tab)
        KeyEvent.KEYCODE_FORWARD_DEL -> stringResource(R.string.key_delete)
        KeyEvent.KEYCODE_MOVE_HOME -> stringResource(R.string.key_home)
        KeyEvent.KEYCODE_MOVE_END -> stringResource(R.string.key_end)
        KeyEvent.KEYCODE_PAGE_UP -> stringResource(R.string.key_page_up)
        KeyEvent.KEYCODE_PAGE_DOWN -> stringResource(R.string.key_page_down)
        KeyEvent.KEYCODE_DPAD_LEFT -> "←"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
        KeyEvent.KEYCODE_DPAD_UP -> "↑"
        KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
        KeyEvent.KEYCODE_SLASH -> "/"
        KeyEvent.KEYCODE_COMMA -> ","
        KeyEvent.KEYCODE_PERIOD -> "."
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
            ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
