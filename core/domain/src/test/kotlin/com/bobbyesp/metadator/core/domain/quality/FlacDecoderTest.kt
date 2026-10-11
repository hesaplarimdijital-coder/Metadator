/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacDecoderTest {
    private val files =
        listOf("music-16bit-stereo.flac", "music-16bit-fixed.flac", "music-24bit-mono.flac")

    private fun open(bytes: ByteArray): FlacSource =
        checkNotNull(FlacSource.open(MemoryAudioFile(bytes)))

    /** Every frame of [bytes] from [from] on, as a player would decode them. */
    private fun decodeAll(bytes: ByteArray, info: FlacStreamInfo, from: Int = 0): List<FlacFrame> =
        buildList {
            var position = FlacFrames.findSync(bytes, from, bytes.size)
            while (position >= 0) {
                val frame = FlacFrames.decode(bytes, position, bytes.size, info)
                position =
                    if (frame == null) FlacFrames.findSync(bytes, position + 1, bytes.size)
                    else {
                        add(frame)
                        FlacFrames.findSync(bytes, position + frame.byteLength, bytes.size)
                    }
            }
        }

    /** The checksum FLAC keeps of its audio: the samples as a WAV file would store them. */
    private fun md5(frames: List<FlacFrame>, info: FlacStreamInfo): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        val width = (info.bitsPerSample + 7) / 8
        for (frame in frames) {
            val pcm = ByteArray(frame.blockSize * info.channels * width)
            var at = 0
            for (index in 0 until frame.blockSize) {
                for (channel in 0 until info.channels) {
                    val sample = frame.samples[channel][index]
                    for (byte in 0 until width) pcm[at++] = (sample shr (8 * byte)).toByte()
                }
            }
            digest.update(pcm)
        }
        return digest.digest()
    }

    @Test
    fun `what the file says about its audio is read`() {
        val stereo = open(fixture("music-16bit-stereo.flac"))
        assertEquals(44_100, stereo.sampleRate)
        assertEquals(2, stereo.channels)
        assertEquals(16, stereo.bitsPerSample)
        assertEquals(8_820L, stereo.totalFrames)

        val mono = open(fixture("music-24bit-mono.flac"))
        assertEquals(96_000, mono.sampleRate)
        assertEquals(1, mono.channels)
        assertEquals(24, mono.bitsPerSample)
    }

    @Test
    fun `every sample is decoded bit for bit`() {
        for (name in files) {
            val bytes = fixture(name)
            val info = open(bytes).info
            val frames = decodeAll(bytes, info)

            assertEquals(name, info.totalSamples, frames.sumOf { it.blockSize.toLong() })
            assertArrayEquals(name, info.md5, md5(frames, info))
        }
    }

    @Test
    fun `decoding can start anywhere in the file`() {
        val bytes = fixture("music-16bit-fixed.flac")
        val info = open(bytes).info
        val all = decodeAll(bytes, info)
        assertTrue(all.size > 4)

        // From the middle of a frame on: what is left of that frame is skipped, the rest decoded.
        val tail = decodeAll(bytes, info, from = bytes.size / 2)

        assertTrue(tail.isNotEmpty() && tail.size < all.size)
        val expected = all.takeLast(tail.size)
        for ((index, frame) in tail.withIndex()) {
            assertArrayEquals(expected[index].samples[0], frame.samples[0])
            assertArrayEquals(expected[index].samples[1], frame.samples[1])
        }
    }

    @Test
    fun `a stretch of audio is found where it is asked for`() {
        val bytes = fixture("music-16bit-fixed.flac")
        val source = open(bytes)
        val whole = decodeAll(bytes, source.info).flatMap { it.samples[0].asIterable() }

        val start = checkNotNull(source.read(fraction = 0.0, frames = 2_000))
        val end = checkNotNull(source.read(fraction = 1.0, frames = 2_000))

        assertEquals(2, start.size)
        assertTrue(start[0].size >= 2_000 && end[0].size >= 2_000)
        assertEquals(whole.take(start[0].size), start[0].toList())
        // Whole frames, so from the frame that holds the place asked for.
        val found = java.util.Collections.indexOfSubList(whole, end[0].toList())
        assertTrue("Found at $found of ${whole.size}", found > whole.size / 2)
    }

    @Test
    fun `a damaged frame is left out`() {
        val bytes = fixture("music-16bit-fixed.flac")
        val info = open(bytes).info
        val intact = decodeAll(bytes, info)
        val damaged = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }

        val frames = decodeAll(damaged, info)

        assertEquals(intact.size - 1, frames.size)
    }

    @Test
    fun `a cut off frame is not a frame`() {
        val bytes = fixture("music-16bit-stereo.flac")
        val info = open(bytes).info
        // Two bytes that look like the start of a frame can turn up before the first one.
        var first = FlacFrames.findSync(bytes, 0, bytes.size)
        while (FlacFrames.decode(bytes, first, bytes.size, info) == null) {
            first = FlacFrames.findSync(bytes, first + 1, bytes.size)
        }
        val frame = checkNotNull(FlacFrames.decode(bytes, first, bytes.size, info))

        assertNull(FlacFrames.decode(bytes, first, first + frame.byteLength - 1, info))
        assertNotNull(FlacFrames.decode(bytes, first, first + frame.byteLength, info))
    }

    @Test
    fun `what is not FLAC is not opened`() {
        assertNull(FlacSource.open(MemoryAudioFile(ByteArray(0))))
        assertNull(FlacSource.open(MemoryAudioFile("ID3 is not enough".toByteArray())))
        assertNull(FlacSource.open(MemoryAudioFile(ByteArray(4096) { (it * 31).toByte() })))
    }

    @Test
    fun `a tag in front of the audio is skipped`() {
        val bytes = fixture("music-16bit-stereo.flac")
        // An empty ID3v2 tag of 20 bytes, as some taggers write in front of FLAC.
        val tag =
            byteArrayOf(
                'I'.code.toByte(),
                'D'.code.toByte(),
                '3'.code.toByte(),
                4,
                0,
                0,
                0,
                0,
                0,
                10,
            )
        val tagged = tag + ByteArray(10) + bytes

        assertEquals(44_100, open(tagged).sampleRate)
    }
}
