/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A picture looked at just closely enough to tell it from another: how large it is, and a small
 * grayscale copy of it.
 *
 * @param luma [SIZE] rows of [SIZE] levels of gray, from 0 (black) to 255, the picture squeezed
 *   into a square whatever its shape
 */
class ImageSample(val width: Int, val height: Int, val luma: ByteArray) {
    init {
        require(luma.size == SIZE * SIZE) { "Expected $SIZE x $SIZE levels, got ${luma.size}" }
    }

    val dimensions: ImageDimensions
        get() = ImageDimensions(width, height)

    /**
     * What the picture looks like, in 64 bits that stay nearly the same when it is resized,
     * compressed again or made a little lighter: two copies of one cover from two catalogues come
     * out a few bits apart, two different covers about half of them ([distanceTo]).
     *
     * A perceptual hash: the picture's coarsest 8 by 8 frequencies, each said to be above or below
     * their median. Fine detail, where copies differ, is not in them.
     */
    val fingerprint: Long by lazy {
        val frequencies = DoubleArray(HASH_SIDE * HASH_SIDE)
        for (v in 0 until HASH_SIDE) {
            for (u in 0 until HASH_SIDE) {
                var sum = 0.0
                for (y in 0 until SIZE) {
                    val row = COSINES[v][y]
                    for (x in 0 until SIZE) {
                        sum += (luma[y * SIZE + x].toInt() and 0xFF) * COSINES[u][x] * row
                    }
                }
                frequencies[v * HASH_SIDE + u] = sum
            }
        }
        // The first one is the average brightness, which says nothing of what is drawn.
        val median = frequencies.copyOfRange(1, frequencies.size).sorted().let { it[it.size / 2] }
        var bits = 0L
        for (index in 1 until frequencies.size) {
            if (frequencies[index] > median) bits = bits or (1L shl index)
        }
        bits
    }

    /**
     * How much the grays vary, from 0 for one flat color. A cover that is one flat color is the
     * placeholder a catalogue shows where it has no cover.
     */
    val contrast: Double by lazy {
        var sum = 0.0
        var squares = 0.0
        for (level in luma) {
            val value = level.toInt() and 0xFF
            sum += value
            squares += value * value
        }
        val mean = sum / luma.size
        sqrt((squares / luma.size - mean * mean).coerceAtLeast(0.0))
    }

    /** How many of the 64 bits of the two [fingerprint]s differ. */
    fun distanceTo(other: ImageSample): Int =
        java.lang.Long.bitCount(fingerprint xor other.fingerprint)

    /** Whether the two are copies of one picture, whatever their sizes. */
    fun isSamePictureAs(other: ImageSample): Boolean = distanceTo(other) <= SAME_PICTURE_BITS

    companion object {
        const val SIZE = 32
        private const val HASH_SIDE = 8

        /**
         * Copies of one cover differ by a handful of bits; different covers by thirty or so. Past
         * this, two pictures that share a layout (a white border, a centered logo) start to pass
         * for one.
         */
        const val SAME_PICTURE_BITS = 10

        /** Below this a picture is one color with, at most, a faint mark on it. */
        const val FLAT_CONTRAST = 5.0

        private val COSINES =
            Array(HASH_SIDE) { frequency ->
                DoubleArray(SIZE) { position ->
                    cos((2 * position + 1) * frequency * PI / (2 * SIZE))
                }
            }

        /**
         * The sample of a picture [width] by [height], from a copy of it decoded as [argb] pixels,
         * [columns] to a row. The copy may be smaller than the picture, as a decoder asked to save
         * memory gives it: only its proportions matter.
         *
         * Each level of gray is the average of every pixel that falls in its cell, so that a large
         * copy and a small one of the same picture come out alike.
         */
        fun of(width: Int, height: Int, argb: IntArray, columns: Int): ImageSample {
            require(columns > 0 && argb.isNotEmpty() && argb.size % columns == 0) {
                "Not ${argb.size} pixels in rows of $columns"
            }
            val rows = argb.size / columns
            val sums = LongArray(SIZE * SIZE)
            val counts = IntArray(SIZE * SIZE)
            for (y in 0 until rows) {
                val cellRow = y * SIZE / rows * SIZE
                for (x in 0 until columns) {
                    val cell = cellRow + x * SIZE / columns
                    sums[cell] += gray(argb[y * columns + x])
                    counts[cell]++
                }
            }
            val luma =
                ByteArray(SIZE * SIZE) { cell ->
                    if (counts[cell] > 0) {
                        (sums[cell] / counts[cell]).toByte()
                    } else {
                        // A copy with fewer pixels than cells: the pixel the cell falls on.
                        val x = cell % SIZE * columns / SIZE
                        val y = cell / SIZE * rows / SIZE
                        gray(argb[y * columns + x]).toByte()
                    }
                }
            return ImageSample(width, height, luma)
        }

        /** How bright a pixel looks, from 0 to 255: green counts most, blue least. */
        private fun gray(argb: Int): Int {
            val red = (argb shr 16) and 0xFF
            val green = (argb shr 8) and 0xFF
            val blue = argb and 0xFF
            return (77 * red + 150 * green + 29 * blue) shr 8
        }
    }
}

/**
 * Decodes pictures into [ImageSample]s. The device's decoders are behind this port, so that
 * choosing between covers stays plain Kotlin.
 */
interface ImageSampler {
    /** The sample of the picture in [bytes], or null when it cannot be decoded. */
    suspend fun sample(bytes: ByteArray): ImageSample?
}
