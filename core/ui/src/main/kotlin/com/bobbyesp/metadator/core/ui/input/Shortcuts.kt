/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.input

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A key, alone or with modifiers, and what pressing it does while its screen is showing.
 *
 * @param keyCode one of [KeyEvent]'s key codes
 * @param label what it does, for the list of shortcuts
 * @param repeats whether holding the key down does it again and again, as scrolling should
 */
@Immutable
class Shortcut(
    val keyCode: Int,
    val label: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val enabled: Boolean = true,
    val repeats: Boolean = false,
    val action: () -> Unit,
) {
    /** The modifiers, as [KeyEvent] counts them. */
    val metaState: Int
        get() =
            (if (ctrl) KeyEvent.META_CTRL_ON else 0) or
                (if (shift) KeyEvent.META_SHIFT_ON else 0) or
                (if (alt) KeyEvent.META_ALT_ON else 0)

    internal fun matches(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): Boolean =
        enabled &&
            keyCode == this.keyCode &&
            ctrl == this.ctrl &&
            shift == this.shift &&
            alt == this.alt
}

/** The shortcuts of one screen, under the name the list of shortcuts gives them. */
@Stable
class ShortcutGroup internal constructor(val title: String, source: State<List<Shortcut>>) {
    val shortcuts: List<Shortcut> by source
}

/**
 * Where the keys nobody on screen took end up, to be tried as shortcuts.
 *
 * An activity owns one and hands it every key its views left alone, so a shortcut works whatever
 * has the focus, and when nothing has: Compose only tells a screen about a key while something in
 * it is focused, and after a click with a mouse nothing is. A text field takes what it types and
 * its own shortcuts first, so a shortcut made of a plain key never fires while typing; a dialog or
 * a sheet is a window of its own, and what is under it gets no keys at all.
 *
 * The screens register what they offer ([KeyboardShortcuts]). When two of them want the same key,
 * the one that came on screen last has it: the editor over the library beside it.
 */
@Stable
class ShortcutDispatcher {
    private val registered = mutableStateListOf<ShortcutGroup>()

    /** What can be pressed now, by screen, in the order the screens came. */
    val groups: List<ShortcutGroup>
        get() = registered

    /** Whether the list of shortcuts was asked for, and is to be shown. */
    var isHelpRequested: Boolean by mutableStateOf(false)

    /** Tries [event] as a shortcut. Returns whether it was one. */
    fun dispatch(event: KeyEvent): Boolean {
        // The system's own shortcuts are made with the Meta key, and stay the system's.
        if (event.action != KeyEvent.ACTION_DOWN || event.isMetaPressed) return false
        return dispatch(
            keyCode = event.keyCode,
            ctrl = event.isCtrlPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            isRepeat = event.repeatCount > 0,
        )
    }

    internal fun dispatch(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        isRepeat: Boolean,
    ): Boolean {
        for (index in registered.indices.reversed()) {
            val shortcut =
                registered[index].shortcuts.firstOrNull { it.matches(keyCode, ctrl, shift, alt) }
                    ?: continue
            // A key held down is still that shortcut, done once: it must not fall through to
            // whatever else would take the repeats.
            if (!isRepeat || shortcut.repeats) shortcut.action()
            return true
        }
        return false
    }

    /** The same list for the system's own shortcuts screen (Meta + /). */
    fun systemGroups(): List<KeyboardShortcutGroup> =
        registered
            .filter { it.shortcuts.isNotEmpty() }
            .map { group ->
                KeyboardShortcutGroup(
                    group.title,
                    group.shortcuts.map {
                        KeyboardShortcutInfo(it.label, it.keyCode, it.metaState)
                    },
                )
            }

    internal fun register(group: ShortcutGroup) {
        registered += group
    }

    internal fun unregister(group: ShortcutGroup) {
        registered -= group
    }
}

/** The activity's dispatcher, or null where nobody hands keys over (a preview). */
val LocalShortcutDispatcher = staticCompositionLocalOf<ShortcutDispatcher?> { null }

/**
 * Offers [shortcuts] for as long as this is on screen, listed under [title]. They are read when a
 * key is pressed, so they can close over whatever the screen shows now.
 */
@Composable
fun KeyboardShortcuts(title: String, shortcuts: List<Shortcut>) {
    val dispatcher = LocalShortcutDispatcher.current ?: return
    val current = rememberUpdatedState(shortcuts)
    DisposableEffect(dispatcher, title) {
        val group = ShortcutGroup(title, current)
        dispatcher.register(group)
        onDispose { dispatcher.unregister(group) }
    }
}
