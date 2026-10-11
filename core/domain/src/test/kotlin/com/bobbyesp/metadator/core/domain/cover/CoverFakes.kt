/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.lookup.api.ArtworkDownloader
import com.bobbyesp.metadator.lookup.api.DownloadedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.hypot

/**
 * A picture made up for a test, the same for the same [seed]: a few blocks and discs of gray on a
 * gradient, drawn at whatever size is asked for, as one cover is in every size a catalogue has.
 */
class Artwork(private val seed: Long) {
    private class Shape(
        val x: Double,
        val y: Double,
        val size: Double,
        val gray: Int,
        val round: Boolean,
    )

    private val random = Random(seed)
    private val from = random.nextInt(256)
    private val to = random.nextInt(256)
    private val slant = random.nextDouble()
    private val shapes =
        List(6) {
            Shape(
                x = random.nextDouble(),
                y = random.nextDouble(),
                size = 0.1 + random.nextDouble() * 0.35,
                gray = random.nextInt(256),
                round = random.nextBoolean(),
            )
        }

    /** The picture [edge] pixels wide and tall, as ARGB, each made [lighter] and a little noisy. */
    fun pixels(edge: Int, lighter: Int = 0, noise: Int = 0): IntArray {
        val grain = Random(seed + edge)
        return IntArray(edge * edge) { index ->
            val x = (index % edge + 0.5) / edge
            val y = (index / edge + 0.5) / edge
            val along = x * slant + y * (1 - slant)
            var gray = (from + (to - from) * along).toInt()
            for (shape in shapes) {
                val inside =
                    if (shape.round) hypot(x - shape.x, y - shape.y) < shape.size / 2
                    else x in shape.x..shape.x + shape.size && y in shape.y..shape.y + shape.size
                if (inside) gray = shape.gray
            }
            if (noise > 0) gray += grain.nextInt(2 * noise + 1) - noise
            val level = (gray + lighter).coerceIn(0, 255)
            (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        }
    }

    fun sample(edge: Int, lighter: Int = 0, noise: Int = 0): ImageSample =
        ImageSample.of(edge, edge, pixels(edge, lighter, noise), columns = edge)
}

/** One flat color, as the picture a catalogue shows where it has no cover. */
fun flatSample(edge: Int, gray: Int = 200): ImageSample =
    ImageSample.of(edge, edge, IntArray(edge * edge) { (0xFF shl 24) or (gray * 0x010101) }, edge)

/** The start of a PNG file of that size: all [imageDimensions] needs of one. */
fun pngHeader(width: Int, height: Int): ByteArray =
    ByteArrayOutputStream()
        .apply {
            write(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
            )
            write(byteArrayOf(0x0D, 0x0A, 0x1A, 0x0A))
            write(ByteBuffer.allocate(4).putInt(13).array())
            write("IHDR".toByteArray())
            write(ByteBuffer.allocate(8).putInt(width).putInt(height).array())
            write(byteArrayOf(8, 2, 0, 0, 0))
        }
        .toByteArray()

/**
 * Covers by URL. A picture is stood for by its URL as bytes, which [FakeSampler] turns back into
 * what it shows; its header is a real one, of the size it was given.
 */
class FakeDownloader : ArtworkDownloader {
    private val sizes = mutableMapOf<String, Pair<Int, Int>>()
    val downloaded = mutableListOf<String>()

    /** Puts a picture [width] by [height] at [url]. */
    fun put(url: String, width: Int, height: Int = width) {
        sizes[url] = width to height
    }

    override suspend fun download(url: String): DownloadedImage? {
        downloaded += url
        return if (url in sizes) DownloadedImage(url.toByteArray(), "image/png") else null
    }

    override suspend fun peek(url: String, maxBytes: Int): ByteArray? =
        sizes[url]?.let { (width, height) -> pngHeader(width, height) }
}

/** Says what the picture at each URL shows; one it was told nothing about cannot be decoded. */
class FakeSampler : ImageSampler {
    private val samples = mutableMapOf<String, ImageSample>()

    fun put(url: String, sample: ImageSample) {
        samples[url] = sample
    }

    /** As [put], for the bytes of a file's own cover. */
    fun put(bytes: ByteArray, sample: ImageSample) = put(String(bytes), sample)

    override suspend fun sample(bytes: ByteArray): ImageSample? = samples[String(bytes)]
}
