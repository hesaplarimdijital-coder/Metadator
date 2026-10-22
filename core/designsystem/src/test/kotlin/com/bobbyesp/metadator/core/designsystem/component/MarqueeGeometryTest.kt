/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeGeometryTest {
    /** The items in sight, from item [first] down: each one's top and height. */
    private class InSight(
        val first: Int,
        val tops: IntArray,
        val sizes: IntArray,
        val indices: IntArray = IntArray(tops.size) { first + it },
    ) {
        fun slot(y: Float): Int =
            marqueeSlot(y, tops.size, { indices[it] }, { tops[it] }, { sizes[it] })

        fun firstInFlow(): Int = firstInFlow(tops.size, { tops[it] }, { sizes[it] })

        fun heldBottom(start: Int = 0): Int =
            heldBottom(tops.size, start, { tops[it] }, { sizes[it] })
    }

    /**
     * A collection scrolled down: its pinned bar (item 1, 80 tall) held at the top, over songs 15
     * to 18 scrolling on under it.
     */
    private val pinned =
        InSight(
            first = 1,
            tops = intArrayOf(0, -10, 30, 70, 110),
            sizes = intArrayOf(80, 40, 40, 40, 40),
            indices = intArrayOf(1, 15, 16, 17, 18),
        )

    /** Rows 40 tall, touching, the first of them at the top of the list. */
    private val rows =
        InSight(first = 0, tops = intArrayOf(0, 40, 80), sizes = intArrayOf(40, 40, 40))

    @Test
    fun `a point inside an item is in that item's slot`() {
        assertEquals(1, rows.slot(10f))
        assertEquals(3, rows.slot(45f))
        assertEquals(5, rows.slot(119f))
    }

    @Test
    fun `the line between two items belongs to the one below`() {
        assertEquals(1, rows.slot(39.9f))
        assertEquals(3, rows.slot(40f))
    }

    @Test
    fun `the spacing between two items is the slot before the lower one`() {
        val spaced =
            InSight(first = 0, tops = intArrayOf(0, 48, 96), sizes = intArrayOf(40, 40, 40))
        assertEquals(2, spaced.slot(44f))
        assertEquals(4, spaced.slot(90f))
    }

    @Test
    fun `the padding above the first item is slot 0`() {
        assertEquals(0, rows.slot(-4f))
    }

    @Test
    fun `below the last item in sight is the slot after it`() {
        assertEquals(6, rows.slot(200f))
    }

    @Test
    fun `a scrolled list counts from the items in sight`() {
        // Item 10 is cut in half by the top edge, and item 11 follows.
        val scrolled = InSight(first = 10, tops = intArrayOf(-20, 20), sizes = intArrayOf(40, 40))
        assertEquals(21, scrolled.slot(0f))
        assertEquals(23, scrolled.slot(25f))
    }

    @Test
    fun `an empty list has nothing but slot 0`() {
        assertEquals(0, InSight(first = 0, tops = intArrayOf(), sizes = intArrayOf()).slot(50f))
    }

    @Test
    fun `every item of a plain list is in its flow`() {
        assertEquals(0, rows.firstInFlow())
        assertEquals(0, rows.heldBottom())
        assertEquals(0, InSight(first = 0, tops = intArrayOf(), sizes = intArrayOf()).firstInFlow())
    }

    @Test
    fun `a sticky item held at the top is out of the flow, down to its bottom`() {
        assertEquals(1, pinned.firstInFlow())
        assertEquals(80, pinned.heldBottom())
    }

    @Test
    fun `a sticky item in its own place is in the flow`() {
        // The collection's header above its pinned bar, then songs 2 and 3, nothing scrolled.
        val atTop =
            InSight(
                first = 0,
                tops = intArrayOf(0, 200, 280, 320),
                sizes = intArrayOf(200, 80, 40, 40),
            )
        assertEquals(0, atTop.firstInFlow())
        assertEquals(0, atTop.heldBottom())
    }

    @Test
    fun `over an item held at the top, the slot is what scrolls under it`() {
        assertEquals(31, pinned.slot(20f))
        assertEquals(33, pinned.slot(50f))
        assertEquals(37, pinned.slot(149f))
        assertEquals(38, pinned.slot(400f))
    }

    @Test
    fun `a marquee within one item touches that item alone`() {
        assertEquals(2, firstTouched(5, 5))
        assertEquals(2, lastTouched(5, 5))
    }

    @Test
    fun `a marquee touches every item between its ends, whichever way it was drawn`() {
        // From inside item 2 to inside item 5.
        assertEquals(2, firstTouched(5, 11))
        assertEquals(5, lastTouched(5, 11))
        assertEquals(2, firstTouched(11, 5))
        assertEquals(5, lastTouched(11, 5))
    }

    @Test
    fun `a marquee drawn within one space between items touches none`() {
        assertTrue(lastTouched(6, 6) < firstTouched(6, 6))
        assertTrue(lastTouched(0, 0) < firstTouched(0, 0))
    }

    @Test
    fun `a marquee from a space takes the items on its side of it only`() {
        // From the space before item 3 down into item 5, and up into item 1.
        assertEquals(3, firstTouched(6, 11))
        assertEquals(5, lastTouched(6, 11))
        assertEquals(1, firstTouched(6, 3))
        assertEquals(2, lastTouched(6, 3))
    }

    @Test
    fun `a marquee from below the last item takes the items up to the pointer`() {
        // Ten items; from below the last into item 7.
        assertEquals(7, firstTouched(20, 15))
        assertEquals(9, lastTouched(20, 15))
    }

    private fun speed(y: Float, height: Float = 600f) =
        marqueeScrollSpeed(y, height, zone = 40f, reach = 80f, maxSpeed = 3000f)

    @Test
    fun `the list holds still while the pointer is away from its edges`() {
        assertEquals(0f, speed(300f), 0f)
        assertEquals(0f, speed(40f), 0f)
        assertEquals(0f, speed(560f), 0f)
    }

    @Test
    fun `near the top edge the list scrolls back, near the bottom one forward`() {
        assertTrue(speed(20f) < 0f)
        assertTrue(speed(580f) > 0f)
        assertEquals(-speed(20f), speed(580f), 0.001f)
    }

    @Test
    fun `the further out the pointer, the faster, up to the fastest`() {
        // At the edge, a third of the way in: a ninth of the fastest.
        assertEquals(-3000f / 9, speed(0f), 0.01f)
        assertTrue(speed(-20f) < speed(0f))
        assertTrue(speed(-40f) < speed(-20f))
        assertEquals(-3000f, speed(-80f), 0.01f)
        assertEquals(-3000f, speed(-500f), 0f)
        assertEquals(3000f, speed(2000f), 0f)
    }

    @Test
    fun `a short list keeps its middle still`() {
        assertEquals(0f, speed(30f, height = 60f), 0f)
        assertTrue(speed(5f, height = 60f) < 0f)
        assertTrue(speed(55f, height = 60f) > 0f)
    }

    @Test
    fun `a list of no height does not scroll`() {
        assertEquals(0f, speed(-50f, height = 0f), 0f)
    }
}
