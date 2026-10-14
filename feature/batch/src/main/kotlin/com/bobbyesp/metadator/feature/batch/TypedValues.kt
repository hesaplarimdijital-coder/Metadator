/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

/**
 * What a field was last typed as, and the values said for it that the screen has not had back yet.
 * They come back a frame or more later, and a frame may come between with the values from before;
 * until the screen holds them, and while it does, the field shows what was typed. Values the screen
 * sets on its own (a reset, a lookup, a merge) are none of these, and are shown as they are.
 */
internal class TypedValues {
    private var text: String? = null
    private val unheard = ArrayDeque<List<String>>()

    fun typed(text: String, values: List<String>) {
        this.text = text
        unheard.addLast(values)
        while (unheard.size > MAX_UNHEARD) unheard.removeFirst()
    }

    /** What was typed, when [values] are what it was taken for or were said on the way to it. */
    fun shownFor(values: List<String>): String? = text?.takeIf { values in unheard }

    /** Once [values] are the screen's, those said before them are not coming back. */
    fun heard(values: List<String>) {
        val index = unheard.indexOf(values)
        if (index < 0) {
            text = null
            unheard.clear()
        } else {
            repeat(index) { unheard.removeFirst() }
        }
    }

    private companion object {
        const val MAX_UNHEARD = 64
    }
}
