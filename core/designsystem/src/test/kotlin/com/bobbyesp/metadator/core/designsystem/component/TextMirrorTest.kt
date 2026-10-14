/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The field's side of a text field whose screen keeps the text: what is typed is [TextMirror.typed]
 * as the field reports it, and each composition weighs the screen's value as [compose] does.
 */
class TextMirrorTest {

    /** One composition with the screen's [value]: whether the field takes it, as it then does. */
    private fun TextMirror.compose(value: String): Boolean =
        isNews(value).also { if (it) set(value) }

    @Test
    fun `what was typed, said back, is not news`() {
        val mirror = TextMirror("")
        mirror.typed("a")

        assertFalse(mirror.compose("a"))
    }

    @Test
    fun `a value the screen sets on its own is news, once`() {
        val mirror = TextMirror("Halo")

        assertTrue(mirror.compose("Halo (Live)"))
        assertFalse(mirror.compose("Halo (Live)"))
    }

    @Test
    fun `what the screen set is not reported back as typed`() {
        val mirror = TextMirror("a")
        mirror.compose("b")

        // The field reports a text only when it differs from what was said last.
        assertEquals("b", mirror.said)
    }

    @Test
    fun `a screen still catching up is not news`() {
        val mirror = TextMirror("a")
        mirror.typed("ab")
        mirror.typed("abc")

        assertFalse(mirror.compose("a"))
        assertFalse(mirror.compose("ab"))
        assertFalse(mirror.compose("abc"))
    }

    @Test
    fun `an echo of an older text is not news, and the texts before it are not coming any more`() {
        val mirror = TextMirror("")
        mirror.typed("a")
        mirror.typed("ab")
        mirror.typed("abc")

        // The screen skipped "a": it says texts back in order, so "a" now is one it set (an undo).
        assertFalse(mirror.compose("ab"))
        assertTrue(mirror.compose("a"))
    }

    @Test
    fun `once the screen holds the latest text, an older one is its own`() {
        val mirror = TextMirror("")
        mirror.typed("a")
        mirror.typed("ab")

        assertFalse(mirror.compose("ab"))
        assertTrue(mirror.compose("a"))
    }

    @Test
    fun `a text the screen turns down never comes back, and what it sets next is news`() {
        val mirror = TextMirror("12")
        mirror.typed("12x")

        assertFalse(mirror.compose("12"))
        assertTrue(mirror.compose("13"))
    }

    @Test
    fun `the field remembers the last 64 texts it said`() {
        val texts = (1..65).map { "x".repeat(it) }
        fun typedAll() = TextMirror("").apply { texts.forEach(::typed) }

        // The oldest is forgotten: said back now, it is taken for the screen's own.
        assertTrue(typedAll().compose(texts[0]))
        assertFalse(typedAll().compose(texts[1]))
    }
}
