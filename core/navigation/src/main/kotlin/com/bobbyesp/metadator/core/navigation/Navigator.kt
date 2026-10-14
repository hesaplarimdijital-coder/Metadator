/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only thing a feature is given of the back stack. A feature says where it wants to go; it
 * never reads, reorders or replaces the stack.
 */
@Stable
interface Navigator {
    /**
     * Goes to [key]. Asking for the destination already on top does nothing.
     *
     * A list gone to from a list starts with nothing beside it: what the list left showed beside it
     * was chosen there, and beside the new one it was a song from elsewhere, which stayed when the
     * new list's own was chosen and came back on the way out. A detail that [hold]s something
     * stays, in sight beside the new list.
     */
    fun goTo(key: NavKey)

    /**
     * Shows [key] as the detail of the list it was chosen from. On a wide window that list stays
     * beside the detail, and choosing again is changing what the detail shows: [key] takes the
     * place of what is beside the list, an editor and the batch editor alike, except what [hold]s
     * something, which stays under it for Back to find. Going to each instead piled up a detail for
     * every choice, for Back to walk through, and one left under another came back, an old song,
     * once the one over it went. A detail already beside the list comes to the top rather than
     * twice. Anything else, as [goTo].
     */
    fun showDetail(key: NavKey)

    /**
     * Shows beside the list on top what is selected in it: [key], or nothing when it is null. It is
     * [showDetail] for what a list shows on its own as its selection changes, with one difference:
     * nobody chose it, so it never comes over a detail that [hold]s something. Unsaved changes stay
     * in sight until they are saved or thrown away, rather than go under the selection unseen.
     *
     * Asked to show nothing, it takes away only what the list asked for last: its selection, or the
     * song clicked in it. What was opened by hand over that, a file or a song from the player, was
     * nobody's selection, and stays when the list changes under it: a search that left out the song
     * clicked before closed the file with it.
     */
    fun showSelection(key: NavKey?)

    /**
     * Says whether [key] holds what [showDetail] must not throw away, as an editor holds unsaved
     * changes. A destination that holds something stays under the next one, where Back finds it.
     */
    fun hold(key: NavKey, held: Boolean)

    /** Leaves the current destination; at the root, leaves the host ([onExit]). */
    fun goBack()

    /**
     * Leaves the list [key] with the details shown beside it, which were chosen in it: the arrow at
     * the top of a list means the list. Back takes one step, and the song beside the list is the
     * first; as Back, the arrow had to be pressed twice to leave. A detail that holds something
     * stays, beside the list under.
     */
    fun leave(key: NavKey)

    /** Takes [key] off the stack wherever it sits, for a destination whose subject is gone. */
    fun removeDestination(key: NavKey)
}

/**
 * The destination the detail pane shows beside a list, as a list needs to know to mark what it was
 * opened on: the song in the editor. Null when nothing is shown beside the list. The shell, which
 * owns the stack, says it; a feature only reads it.
 */
val LocalShownDetail = compositionLocalOf<NavKey?> { null }

@Composable
fun rememberNavigator(backStack: NavBackStack<NavKey>, onExit: () -> Unit = {}): Navigator =
    remember(backStack, onExit) { BackStackNavigator(backStack, onExit) }
