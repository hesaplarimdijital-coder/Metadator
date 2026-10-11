/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageHeaderTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A JPEG up to its frame header, with [notes] bytes of application data before it. */
    private fun jpeg(width: Int, height: Int, frame: Int = 0xC0, notes: Int = 14): ByteArray =
        ByteArrayOutputStream()
            .apply {
                write(bytes(0xFF, 0xD8))
                write(bytes(0xFF, 0xE0, (notes + 2) shr 8, (notes + 2) and 0xFF))
                write(ByteArray(notes))
                // A table, which shares its range of markers with the frame headers.
                write(bytes(0xFF, 0xC4, 0x00, 0x04, 0x00, 0x00))
                write(bytes(0xFF, frame, 0x00, 0x11, 0x08))
                write(bytes(height shr 8, height and 0xFF, width shr 8, width and 0xFF))
                write(bytes(0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01))
            }
            .toByteArray()

    private fun webp(kind: String, vararg data: Int): ByteArray =
        "RIFF".toByteArray() +
            bytes(0, 0, 0, 0) +
            "WEBP".toByteArray() +
            kind.toByteArray() +
            bytes(0, 0, 0, 0) +
            bytes(*data) +
            ByteArray(8)

    @Test
    fun `a PNG says its size first`() {
        assertEquals(ImageDimensions(3000, 2990), imageDimensions(pngHeader(3000, 2990)))
    }

    @Test
    fun `a JPEG says its size in its frame header`() {
        assertEquals(ImageDimensions(1400, 1425), imageDimensions(jpeg(1400, 1425)))
    }

    @Test
    fun `a progressive JPEG has another frame header`() {
        assertEquals(ImageDimensions(600, 600), imageDimensions(jpeg(600, 600, frame = 0xC2)))
    }

    @Test
    fun `what a camera wrote before the frame header is skipped`() {
        assertEquals(ImageDimensions(1000, 1000), imageDimensions(jpeg(1000, 1000, notes = 9000)))
    }

    @Test
    fun `a JPEG cut before its frame header says nothing`() {
        val whole = jpeg(1000, 1000, notes = 9000)

        assertNull(imageDimensions(whole.copyOf(4000)))
        assertNull(imageDimensions(whole.copyOf(whole.size - 12)))
    }

    @Test
    fun `the three kinds of WebP are read`() {
        // 1920 by 1080, each side less one, in three bytes.
        val extended = webp("VP8X", 0x10, 0, 0, 0, 0x7F, 0x07, 0x00, 0x37, 0x04, 0x00)
        // A frame tag, the start code, then each side in two bytes.
        val lossy = webp("VP8 ", 0, 0, 0, 0x9D, 0x01, 0x2A, 0x20, 0x03, 0x58, 0x02)
        // 500 by 400, each less one, in fourteen bits each: 499 | 399 << 14.
        val lossless = webp("VP8L", 0x2F, 0xF3, 0xC1, 0x63, 0x00, 0, 0, 0, 0, 0)

        assertEquals(ImageDimensions(1920, 1080), imageDimensions(extended))
        assertEquals(ImageDimensions(800, 600), imageDimensions(lossy))
        assertEquals(ImageDimensions(500, 400), imageDimensions(lossless))
    }

    @Test
    fun `a GIF says its size after its signature`() {
        val gif = "GIF89a".toByteArray() + bytes(0x40, 0x01, 0xF0, 0x00, 0, 0, 0)

        assertEquals(ImageDimensions(320, 240), imageDimensions(gif))
    }

    @Test
    fun `anything else is not a picture`() {
        assertNull(imageDimensions(ByteArray(0)))
        assertNull(imageDimensions("<html><body>Not found</body></html>".toByteArray()))
        assertNull(imageDimensions(pngHeader(0, 0)))
        assertNull(imageDimensions(pngHeader(300, 300).copyOf(18)))
    }

    @Test
    fun `the short edge is what limits a square cover`() {
        assertEquals(1340, ImageDimensions(1500, 1340).shortEdge)
    }
}
