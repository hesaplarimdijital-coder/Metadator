/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only thing a feature is given of the back stack. A feature says where it wants to go; it
 * never reads, reorders or replaces the stack.
 */
@Stable
interface Navigator {
    /** Goes to [key]. Asking for the destination already on top does nothing. */
    fun goTo(key: NavKey)

    /**
     * Shows [key] as the detail of the list it was chosen from. On a wide window that list stays
     * beside the detail, and choosing again is changing what the detail shows: [key] takes the
     * place of the destination on top when that is one of its kind and nothing [hold]s it. Going to
     * each instead would pile up a detail for every choice, for Back to walk through. Anything
     * else, as [goTo].
     */
    fun showDetail(key: NavKey)

    /**
     * Says whether [key] holds what [showDetail] must not throw away, as an editor holds unsaved
     * changes. A destination that holds something stays under the next one, where Back finds it.
     */
    fun hold(key: NavKey, held: Boolean)

    /** Leaves the current destination; at the root, leaves the host ([onExit]). */
    fun goBack()

    /** Takes [key] off the stack wherever it sits, for a destination whose subject is gone. */
    fun removeDestination(key: NavKey)
}

@Composable
fun rememberNavigator(backStack: NavBackStack<NavKey>, onExit: () -> Unit = {}): Navigator =
    remember(backStack, onExit) { BackStackNavigator(backStack, onExit) }

internal class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val onExit: () -> Unit,
) : Navigator {

    /**
     * What is held, by its destination. Only the one on top is ever asked about, and it is in
     * composition then, saying what it holds: so this does not need to outlive the host.
     */
    private val held = mutableSetOf<NavKey>()

    override fun goTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    override fun showDetail(key: NavKey) {
        val top = backStack.lastOrNull()
        if (top == key) return
        // The root is a list, never a detail to replace.
        if (top != null && backStack.size > 1 && top::class == key::class && top !in held) {
            backStack.removeAt(backStack.lastIndex)
            // What was chosen may be the one just under it, kept there for what it holds.
            if (backStack.lastOrNull() == key) return
        }
        backStack.add(key)
    }

    override fun hold(key: NavKey, held: Boolean) {
        if (held) this.held += key else this.held -= key
    }

    /** `NavDisplay` needs a non-empty stack, so the root is never popped; the host is left. */
    override fun goBack() {
        if (backStack.size <= 1) onExit() else backStack.removeLastOrNull()?.let(::forget)
    }

    override fun removeDestination(key: NavKey) {
        if (backStack.size == 1 && backStack.single() == key) {
            onExit()
            return
        }
        while (backStack.size > 1 && backStack.contains(key)) backStack.remove(key)
        forget(key)
    }

    /** What a destination held goes with it, unless it is still on the stack further down. */
    private fun forget(key: NavKey) {
        if (key !in backStack) held -= key
    }
}
