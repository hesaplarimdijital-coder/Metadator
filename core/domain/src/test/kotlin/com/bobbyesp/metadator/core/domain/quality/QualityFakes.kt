/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.library.api.AccessMode
import com.bobbyesp.metadator.library.api.AudioFileOpener
import com.bobbyesp.metadator.library.api.OpenResult
import com.bobbyesp.metadator.library.api.OpenedAudioFile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A file held in memory, read at any position as one on disk is. */
class MemoryAudioFile(private val bytes: ByteArray) : OpenedAudioFile {
    override val size: Long = bytes.size.toLong()

    override fun detachDuplicateFd(): Int = error("No descriptor in a test")

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length, bytes.size - position.toInt())
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override fun close() = Unit
}

class FakeFileOpener(private val files: Map<ContentRef, ByteArray>) : AudioFileOpener {
    override fun open(ref: ContentRef, mode: AccessMode): OpenResult =
        files[ref]?.let { OpenResult.Opened(MemoryAudioFile(it)) } ?: OpenResult.NotFound
}

/** One of the FLAC files of `src/test/resources/quality`. */
fun fixture(name: String): ByteArray =
    checkNotNull(MemoryAudioFile::class.java.getResourceAsStream("/quality/$name")) {
            "Missing test resource $name"
        }
        .use { it.readBytes() }

/**
 * Audio made for a test: [period] played over and over for [seconds], in one channel. A period of
 * [noise] loops without a click, so any stretch of it has the spectrum it was made with.
 */
internal class SyntheticAudio(
    override val sampleRate: Int,
    override val bitsPerSample: Int,
    private val period: IntArray,
    seconds: Int = 9,
) : PcmSource {
    override val format = LosslessFormat.Flac
    override val channels = 1
    override val totalFrames = sampleRate.toLong() * seconds

    override fun read(fraction: Double, frames: Int): Array<IntArray> {
        val first = ((totalFrames - frames) * fraction).toLong()
        return arrayOf(IntArray(frames) { period[((first + it) % period.size).toInt()] })
    }
}

/**
 * A period of noise whose spectrum is [shape]: the gain at each frequency, in Hz. Where the gain is
 * zero there is nothing at all, as above the cut-off of a lossy encoder, and rounding the samples
 * to [bits] is then the only thing left there.
 *
 * @param hiss white noise added on top, in dB below full scale: the noise of a room or a tape,
 *   which no frequency is free of
 */
internal fun noise(
    sampleRate: Int,
    bits: Int,
    hiss: Double? = null,
    seed: Long = 7,
    shape: (Double) -> Double,
): IntArray {
    val size = 1 shl 17
    val real = DoubleArray(size)
    val imaginary = DoubleArray(size)
    val random = java.util.Random(seed)
    for (bin in 1 until size / 2) {
        val gain = shape(bin.toDouble() * sampleRate / size)
        val phase = random.nextDouble() * 2 * PI
        real[bin] = gain * cos(phase)
        real[size - bin] = real[bin]
        // The conjugate of the spectrum: transformed forward, it gives the signal.
        imaginary[bin] = -gain * sin(phase)
        imaginary[size - bin] = -imaginary[bin]
    }
    Fft(size).transform(real, imaginary)
    val fullScale = (1L shl (bits - 1)).toDouble()
    val gain = LEVEL * fullScale / sqrt(real.sumOf { it * it } / size)
    val hissAmplitude = hiss?.let { Math.pow(10.0, it / 20) * fullScale } ?: 0.0
    return IntArray(size) { index ->
        (real[index] * gain + random.nextGaussian() * hissAmplitude)
            .roundToInt()
            .coerceIn(-fullScale.toInt(), fullScale.toInt() - 1)
    }
}

/** Everything up to [hz], and nothing above: what a lossy encoder's low-pass filter leaves. */
internal fun cutAt(hz: Double): (Double) -> Double = { frequency ->
    when {
        frequency <= hz - TRANSITION_HZ -> 1.0
        frequency >= hz -> 0.0
        else -> (hz - frequency) / TRANSITION_HZ
    }
}

/** About 20 dB below full scale: loud music. */
private const val LEVEL = 0.1
private const val TRANSITION_HZ = 300.0
