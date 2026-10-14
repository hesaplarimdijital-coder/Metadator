/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import com.bobbyesp.metadator.core.domain.editor.splitValues
import org.junit.Assert.assertEquals
import org.junit.Test

class TypedValuesTest {

    /**
     * A field of several values as the batch editor drives it: what is typed is split into values
     * and said to the ViewModel, whose values the screen has a frame or more later.
     */
    private class Field {
        private val typed = TypedValues()

        /** The values the screen holds. */
        var values: List<String> = emptyList()

        /** Typed [text]: the values it is taken for, which the screen does not hold yet. */
        fun type(text: String): List<String> =
            splitValues(text, SEPARATOR).also { typed.typed(text, it) }

        /** What the field shows in a frame drawn with the values the screen holds. */
        fun frame(): String {
            val shown = typed.shownFor(values) ?: values.joinToString(SEPARATOR)
            typed.heard(values)
            return shown
        }

        /** Typed [text], and a frame drawn once the screen has its values. */
        fun typeAndShow(text: String): String {
            values = type(text)
            return frame()
        }
    }

    @Test
    fun `a separator typed after a value stays, and the next value goes after it`() {
        val field = Field()

        assertEquals("Daft Punk", field.typeAndShow("Daft Punk"))
        // The values are still "Daft Punk" alone: joined again, the separator was gone.
        assertEquals("Daft Punk;", field.typeAndShow("Daft Punk;"))
        assertEquals("Daft Punk; ", field.typeAndShow("Daft Punk; "))
        assertEquals("Daft Punk; P", field.typeAndShow("Daft Punk; P"))
        assertEquals(listOf("Daft Punk", "P"), field.values)
    }

    @Test
    fun `a second value deleted back to the separator keeps the separator`() {
        val field = Field()
        field.typeAndShow("Daft Punk; Pharrell")

        field.type("Daft Punk; ")
        // A frame comes first with the values from before.
        assertEquals("Daft Punk; ", field.frame())
        field.values = listOf("Daft Punk")
        assertEquals("Daft Punk; ", field.frame())
    }

    @Test
    fun `a separator typed without its space, or a space at a value's end, is shown as typed`() {
        val field = Field()

        // Joined again, "Daft Punk;Pharrell" was rewritten, and the cursor thrown to the end.
        assertEquals("Daft Punk;Pharrell", field.typeAndShow("Daft Punk;Pharrell"))
        assertEquals(listOf("Daft Punk", "Pharrell"), field.values)
        assertEquals("Daft Punk;Pharrell ", field.typeAndShow("Daft Punk;Pharrell "))
        assertEquals("Daft Punk;Pharrell W", field.typeAndShow("Daft Punk;Pharrell W"))
    }

    @Test
    fun `values set on the screen's own while typed ones are on their way are shown as they are`() {
        val field = Field()
        field.typeAndShow("Daft Punk")
        field.type("Daft Punk; Pharrell")

        // Reset to the songs' own before the typed values came back.
        field.values = listOf("Justice")
        assertEquals("Justice", field.frame())
        // What was typed is forgotten with them: values set later, a lookup's, are shown as they
        // are, not as the text once typed on the way to them.
        field.values = listOf("Daft Punk")
        assertEquals("Daft Punk", field.frame())
        assertEquals("Daft Punk; ", field.typeAndShow("Daft Punk; "))
    }

    private companion object {
        const val SEPARATOR = "; "
    }
}
