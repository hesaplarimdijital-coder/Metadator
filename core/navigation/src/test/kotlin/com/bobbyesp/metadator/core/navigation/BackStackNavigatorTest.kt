/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.model.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackNavigatorTest {
    private val album = Collection(CollectionType.Album, "album:1:a:b", "B")
    private val a = Editor("content://a")
    private val b = Editor("content://b")
    private val c = Editor("content://c")
    private val batch = BatchEditor(listOf("content://a", "content://b"))

    private var exits = 0

    private fun navigator(vararg stack: NavKey): Pair<MutableList<NavKey>, Navigator> {
        val backStack = mutableListOf(*stack)
        return backStack to BackStackNavigator(backStack) { exits++ }
    }

    @Test
    fun `the arrow of a collection leaves it with the song beside it, in one press`() {
        val (stack, navigator) = navigator(Library)
        navigator.goTo(album)
        navigator.showDetail(a)
        navigator.hold(a, false)

        navigator.leave(album)

        assertEquals(listOf(Library), stack)
    }

    @Test
    fun `leaving a collection keeps the editor with unsaved changes, beside the library`() {
        val (stack, navigator) = navigator(Library)
        navigator.goTo(album)
        navigator.showDetail(a)
        navigator.hold(a, true)

        navigator.leave(album)

        assertEquals(listOf(Library, a), stack)
    }

    @Test
    fun `an editor takes the place of the batch editor beside the list`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(batch)
        navigator.hold(batch, false)

        navigator.showDetail(a)

        assertEquals(listOf(Library, a), stack)
    }

    @Test
    fun `an editor that held changes when another was chosen does not stay once it lets go`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(a)
        navigator.hold(a, true)
        navigator.showDetail(b)
        navigator.hold(b, false)
        // Saved meanwhile, as a save already on its way when the next song was chosen.
        navigator.hold(a, false)

        navigator.showDetail(c)

        assertEquals(listOf(Library, c), stack)
    }

    @Test
    fun `a song chosen again while it holds changes comes up, and is never twice on the stack`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(a)
        navigator.hold(a, true)
        navigator.showDetail(b)
        navigator.hold(b, true)

        navigator.showDetail(a)

        assertEquals(listOf(Library, b, a), stack)
    }

    @Test
    fun `a collection opens with nothing beside it but what holds changes`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(a)
        navigator.hold(a, false)
        navigator.goTo(album)
        assertEquals(listOf(Library, album), stack)

        val (kept, other) = navigator(Library)
        other.showDetail(a)
        other.hold(a, true)
        other.goTo(album)
        assertEquals(listOf(Library, a, album), kept)
    }

    @Test
    fun `the selection never comes over a detail that holds changes`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(a)
        navigator.hold(a, true)

        navigator.showSelection(batch)
        navigator.showSelection(null)

        assertEquals(listOf(Library, a), stack)
    }

    @Test
    fun `the selection takes the place of what holds nothing, and nothing selected empties it`() {
        val (stack, navigator) = navigator(Library)
        navigator.showDetail(a)
        navigator.hold(a, false)

        navigator.showSelection(batch)
        assertEquals(listOf(Library, batch), stack)

        navigator.hold(batch, false)
        navigator.showSelection(null)
        assertEquals(listOf(Library), stack)
    }

    @Test
    fun `the song in sight stays when it is the one selected, whatever it was opened for`() {
        val (stack, navigator) = navigator(Library)
        val findingCover = Editor(a.uri, then = EditorAction.FindCover)
        navigator.showDetail(findingCover)
        navigator.hold(findingCover, false)

        navigator.showSelection(a)

        assertEquals(listOf(Library, findingCover), stack)
    }

    @Test
    fun `what came back with the stack is kept until it says it holds nothing`() {
        // As after the process was gone: the editor under the one on top is not composed.
        val (stack, navigator) = navigator(Library, a, b)
        navigator.hold(b, false)

        navigator.showDetail(c)

        assertEquals(listOf(Library, a, c), stack)
    }

    @Test
    fun `the root stays whatever is asked`() {
        val (stack, navigator) = navigator(Library)

        navigator.showSelection(null)
        navigator.goBack()
        navigator.leave(Library)

        assertEquals(listOf(Library), stack)
        assertEquals(2, exits)
    }

    @Test
    fun `back takes one step, the song beside the collection and then the collection`() {
        val (stack, navigator) = navigator(Library)
        navigator.goTo(album)
        navigator.showDetail(a)

        navigator.goBack()
        assertEquals(listOf(Library, album), stack)
        navigator.goBack()
        assertEquals(listOf(Library), stack)
        assertTrue(exits == 0)
    }
}
