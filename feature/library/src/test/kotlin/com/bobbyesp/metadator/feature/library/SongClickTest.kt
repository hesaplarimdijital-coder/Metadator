/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SongClickTest {
    private fun click(
        shift: Boolean = false,
        ctrl: Boolean = false,
        mouse: Boolean,
        selecting: Boolean,
    ) = songClick(shift = shift, ctrl = ctrl, mouse = mouse, selecting = selecting)

    @Test
    fun `a plain click with the mouse opens the song, songs selected or not`() {
        assertEquals(SongClick.Open, click(mouse = true, selecting = false))
        assertEquals(SongClick.Open, click(mouse = true, selecting = true))
    }

    @Test
    fun `a finger's click opens the song, or picks it while songs are selected`() {
        assertEquals(SongClick.Open, click(mouse = false, selecting = false))
        assertEquals(SongClick.Toggle, click(mouse = false, selecting = true))
    }

    @Test
    fun `Ctrl picks the song and Shift takes the range, whatever clicked`() {
        for (mouse in listOf(true, false)) {
            for (selecting in listOf(true, false)) {
                assertEquals(
                    SongClick.Toggle,
                    click(ctrl = true, mouse = mouse, selecting = selecting),
                )
                assertEquals(
                    SongClick.Range,
                    click(shift = true, mouse = mouse, selecting = selecting),
                )
                assertEquals(
                    SongClick.Range,
                    click(shift = true, ctrl = true, mouse = mouse, selecting = selecting),
                )
            }
        }
    }
}
