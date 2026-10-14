/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The [Navigator] over a back stack: the library at its root, a collection gone to from it, what is
 * shown beside each, and the screens gone to from those. Only what is beside the list on top is
 * ever replaced or dropped, and never the root: `NavDisplay` needs a stack that is not empty.
 */
internal class BackStackNavigator(
    private val backStack: MutableList<NavKey>,
    private val onExit: () -> Unit,
) : Navigator {

    /**
     * What is held, by its destination. A destination says it while it is composed, which is
     * whenever it is on top; it stays held under another until it says otherwise or goes.
     */
    private val held = mutableSetOf<NavKey>()

    /**
     * What was on the stack before this navigator was made, as when the stack comes back after the
     * process was gone, and has not said yet whether it holds anything. A detail under another is
     * not composed to say it, and its unsaved changes may have been kept through that: it is kept
     * too, until it is composed again and says.
     */
    private val unheard = backStack.toMutableSet()

    private val NavKey.isKept: Boolean
        get() = this in held || this in unheard

    override fun goTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        if (key.isList) dropDetails()
        backStack.add(key)
    }

    override fun showDetail(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        if (!key.isDetail) {
            goTo(key)
            return
        }
        dropDetails()
        if (backStack.lastOrNull() == key) return
        // Kept beside the list for what it holds: it comes up, rather than be on the stack twice.
        val kept = backStack.lastIndexOf(key)
        if (kept > 0) backStack.removeAt(kept)
        backStack.add(key)
    }

    override fun showSelection(key: NavKey?) {
        val top = backStack.lastOrNull() ?: return
        if (top.isDetail && top.isKept) return
        // The song in sight already stays, whatever it was opened to do there, as find its cover.
        if (top is Editor && key is Editor && top.uri == key.uri) return
        if (key == null) dropDetails() else showDetail(key)
    }

    override fun hold(key: NavKey, held: Boolean) {
        unheard -= key
        if (held) this.held += key else this.held -= key
    }

    override fun goBack() {
        if (backStack.size <= 1) onExit() else backStack.removeLastOrNull()?.let(::forget)
    }

    override fun leave(key: NavKey) {
        val at = backStack.lastIndexOf(key)
        if (at < 0) return
        if (at == 0) {
            onExit()
            return
        }
        for (index in backStack.lastIndex downTo at + 1) {
            val beside = backStack[index]
            if (beside.isDetail && !beside.isKept) {
                backStack.removeAt(index)
                forget(beside)
            }
        }
        backStack.removeAt(at)
        forget(key)
    }

    override fun removeDestination(key: NavKey) {
        if (backStack.size == 1 && backStack.single() == key) {
            onExit()
            return
        }
        while (backStack.size > 1 && backStack.contains(key)) backStack.remove(key)
        forget(key)
    }

    /**
     * Takes away what is beside the list on top and holds nothing. What holds something stays where
     * it is, for Back or a choice to find; the list, and the root, always stay.
     */
    private fun dropDetails() {
        var index = backStack.lastIndex
        while (index > 0 && backStack[index].isDetail) {
            val key = backStack[index]
            if (!key.isKept) {
                backStack.removeAt(index)
                forget(key)
            }
            index--
        }
    }

    /** What a destination held goes with it, unless it is still on the stack further down. */
    private fun forget(key: NavKey) {
        if (key !in backStack) {
            held -= key
            unheard -= key
        }
    }
}
