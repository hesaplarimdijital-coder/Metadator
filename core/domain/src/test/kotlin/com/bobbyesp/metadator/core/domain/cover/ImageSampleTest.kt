/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSampleTest {
    @Test
    fun `copies of one picture in two sizes are the same picture`() {
        for (seed in 1L..20L) {
            val cover = Artwork(seed)
            val distance = cover.sample(250).distanceTo(cover.sample(1200))

            assertTrue("Picture $seed: $distance bits apart", distance <= 4)
        }
    }

    @Test
    fun `a copy compressed again and made lighter is still the same picture`() {
        for (seed in 1L..20L) {
            val cover = Artwork(seed)
            val original = cover.sample(500)
            val copy = cover.sample(300, lighter = 18, noise = 6)

            assertTrue(
                "Picture $seed: ${original.distanceTo(copy)} bits apart",
                original.isSamePictureAs(copy),
            )
        }
    }

    @Test
    fun `different pictures are different`() {
        var closest = 64
        for (first in 1L..12L) {
            for (second in first + 1..12L) {
                val distance = Artwork(first).sample(250).distanceTo(Artwork(second).sample(250))
                closest = minOf(closest, distance)
                assertFalse(
                    "Pictures $first and $second: $distance bits apart",
                    Artwork(first).sample(250).isSamePictureAs(Artwork(second).sample(250)),
                )
            }
        }
        assertTrue("The closest two are $closest bits apart", closest > 14)
    }

    @Test
    fun `a picture smaller than the sample is still sampled`() {
        val cover = Artwork(3)

        val tiny = cover.sample(20)

        assertEquals(20, tiny.width)
        assertTrue(tiny.isSamePictureAs(cover.sample(400)))
    }

    @Test
    fun `a picture that is not square is squeezed into the sample`() {
        val wide =
            IntArray(64 * 16) { if (it % 64 < 32) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

        val sample = ImageSample.of(640, 160, wide, columns = 64)

        assertEquals(ImageDimensions(640, 160), sample.dimensions)
        assertEquals(0, sample.luma[0].toInt() and 0xFF)
        assertEquals(255, sample.luma[ImageSample.SIZE - 1].toInt() and 0xFF)
    }

    @Test
    fun `one flat color has no contrast`() {
        assertEquals(0.0, flatSample(250).contrast, 0.001)
        assertTrue(Artwork(1).sample(250).contrast > ImageSample.FLAT_CONTRAST)
    }

    @Test
    fun `gray is how bright a color looks`() {
        val green = ImageSample.of(8, 8, IntArray(64) { 0xFF00FF00.toInt() }, columns = 8)
        val blue = ImageSample.of(8, 8, IntArray(64) { 0xFF0000FF.toInt() }, columns = 8)

        assertTrue((green.luma[0].toInt() and 0xFF) > 4 * (blue.luma[0].toInt() and 0xFF))
    }
}
