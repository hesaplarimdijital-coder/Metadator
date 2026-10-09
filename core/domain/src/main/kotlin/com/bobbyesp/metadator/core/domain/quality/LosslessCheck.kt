/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import kotlin.math.ceil
import kotlin.math.roundToInt

/** What the audio of a lossless file looks like it has been through. */
enum class LosslessVerdict {
    /**
     * No sign of lossy compression: content fills the band the file can hold, or fades into noise.
     */
    Lossless,

    /**
     * The audio shows no lossy compression, but the file claims more resolution than it carries.
     */
    Upscaled,

    /** Not enough to go on, or signs that point both ways. */
    Unclear,

    /** Signs of a lossy source that a few genuine recordings share. */
    ProbablyTranscoded,

    /** The mark a lossy encoder leaves: content cut off at one frequency, and nothing above it. */
    Transcoded,
}

/** Why a file could not be judged. */
enum class UnclearReason {
    /** Too little audio above silence to measure. */
    TooQuiet,

    /** A sample rate so low that a lossy encoder's cut-off would not show. */
    LowSampleRate,

    /** Content ends between where lossy encoders cut and where genuine recordings do. */
    BorderlineCutoff,
}

/**
 * The verdict on one file, and what it rests on.
 *
 * A look at the spectrum cannot prove a file genuine: an encoder at a high enough bitrate keeps the
 * whole band, and leaves nothing to see here. [LosslessVerdict.Lossless] means no sign was found.
 */
data class LosslessReport(
    val format: LosslessFormat,
    val verdict: LosslessVerdict,
    /** How much weight the verdict carries, from 0 to 1. */
    val confidence: Double,
    val unclearReason: UnclearReason?,
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val channels: Int,
    /** Where content stops, when that is below the highest frequency the file can hold. */
    val cutoffHz: Int?,
    /** The bitrate a lossy source with that cut-off most likely had, in kbps. */
    val estimatedSourceKbps: Int?,
    /** Whether the band above 16 kHz comes and goes, as MP3 encoders leave it. */
    val shelfAt16kHz: Boolean,
    /**
     * Whether the sample rate is higher than the content: nothing but silence in the upper band.
     */
    val upsampled: Boolean,
    /**
     * The bits per sample that carry audio, when fewer than the file's: 16-bit audio padded to 24.
     */
    val effectiveBitsPerSample: Int?,
    val spectrum: SpectrumCurve,
    val analyzedSeconds: Double,
)

/**
 * The spectrum of the audio looked at, for a chart: point `i` is the band starting at `i *
 * bandWidthHz`. Levels are in dB below full scale.
 */
class SpectrumCurve(val bandWidthHz: Float, val averageDb: FloatArray, val peakDb: FloatArray) {
    val size: Int
        get() = averageDb.size

    val nyquistHz: Float
        get() = bandWidthHz * size
}

/**
 * Tells audio that has been through a lossy encoder from audio that has not, by its spectrum.
 *
 * Lossy encoders throw away what is above a frequency that depends on their bitrate, and their
 * decoders put nothing back: above it the decoded audio holds only the rounding of its own samples.
 * Recordings do not end like that. Their content fades into the noise of the room and the
 * equipment, or is cut by the filter of the converter, a little under half the sample rate. So what
 * is looked for is a cliff: content right below a frequency, digital silence right above it, in
 * nearly every moment of the song. Where it is says what made it.
 *
 * The thresholds come from files encoded with LAME, FFmpeg's AAC, Vorbis and Opus at their usual
 * bitrates and decoded again, next to the recordings they were made from.
 */
internal object LosslessAnalyzer {
    private const val SEGMENTS = 12

    /** Each stretch of audio gives this many windows, half of them overlapping. */
    private const val WINDOWS_PER_SEGMENT = 32
    private const val MIN_ROWS = 20

    /** Rows this far below the loud ones are silence, and say nothing about the top of the band. */
    private const val ACTIVE_RANGE = 1e-4
    private const val SILENCE = 1e-10

    private const val LOW_EDGE_HZ = 2_000.0
    private const val CLIFF_WINDOW_HZ = 1_300.0

    /** No encoder worth the name cuts lower; under it a cliff is just music with little treble. */
    private const val MIN_CUTOFF_HZ = 9_000.0
    private const val TOP_MARGIN_HZ = 300.0

    private const val MIN_STEP_DB = 12.0
    private const val STRONG_STEP_DB = 15.0

    /** Drops this close to each other are one wall, seen from bands next to each other. */
    private const val SAME_STEP_DB = 1.5

    /** A drop this deep is a filter's, even over noise that is not the file's own rounding. */
    private const val DEEP_STEP_DB = 40.0

    /** Above the rounding noise by no more than dither would add. */
    private const val DIGITAL_MARGIN_DB = 12.0

    /** Where the encoders stop: every usual MP3, AAC and Vorbis setting cuts below the first. */
    private const val LOSSY_BELOW_HZ = 19_900.0

    /** MP3 at 320 kbps and Opus end between the two. */
    private const val LIKELY_LOSSY_BELOW_HZ = 20_750.0
    private const val GENUINE_FROM_HZ = 21_200.0
    private const val LOW_BITRATE_BELOW_HZ = 12_000.0

    private const val MIN_NYQUIST_HZ = 16_000.0
    private const val HIGH_RESOLUTION_HZ = 80_000
    private const val UPSAMPLED_FRACTION = 0.62
    private const val SHELF_DB = 10.0

    private const val MAX_CURVE_POINTS = 256

    private class Cliff(val band: Int, val step: Double, val above: Double) {
        fun frequency(bandWidth: Double) = (band + 1) * bandWidth
    }

    /**
     * The report on [source], or null when none of its audio could be decoded.
     *
     * @param checkpoint called between stretches of audio, for a caller that may want to stop
     */
    fun analyze(source: PcmSource, checkpoint: () -> Unit = {}): LosslessReport? {
        val frames = SpectralFrames.forFormat(source.sampleRate, source.channels)
        val segmentLength = frames.fftSize / 2 * (WINDOWS_PER_SEGMENT + 1)
        val segments =
            if (source.totalFrames <= 0) SEGMENTS
            else ceil(source.totalFrames.toDouble() / segmentLength).toInt().coerceIn(1, SEGMENTS)
        val fullScale = (1L shl (source.bitsPerSample - 1)).toDouble()
        var usedBits = 0
        for (segment in 0 until segments) {
            checkpoint()
            val audio = source.read((segment + 0.5) / segments, segmentLength) ?: continue
            for (channel in audio) for (sample in channel) usedBits = usedBits or sample
            frames.add(audio, fullScale)
        }
        if (frames.frameCount == 0) return null

        val bandWidth = frames.bandWidthHz
        val bands = frames.bandCount
        val nyquist = source.sampleRate / 2.0
        val active = activeRows(frames, bandWidth)
        // Silence has a spectrum too, for the chart; it only cannot be judged.
        val measured = if (active.isEmpty()) IntArray(frames.frameCount) { it } else active
        val mean = DoubleArray(bands)
        val median = DoubleArray(bands)
        val high = DoubleArray(bands)
        val peak = DoubleArray(bands)
        val column = FloatArray(measured.size)
        for (band in 0 until bands) {
            var sum = 0.0
            for ((index, row) in measured.withIndex()) {
                val value = frames.row(row)[band]
                column[index] = value
                sum += value
            }
            column.sort()
            mean[band] = decibels(sum / measured.size)
            median[band] = decibels(percentile(column, 0.5))
            high[band] = decibels(percentile(column, 0.9))
            peak[band] = decibels(column[column.lastIndex].toDouble())
        }

        // Bits every sample has at zero carry nothing: 16-bit audio in a 24-bit file.
        val effectiveBits =
            usedBits
                .countTrailingZeroBits()
                .takeIf { usedBits != 0 && it > 0 }
                ?.let { source.bitsPerSample - it }
        // The noise that rounding samples to the bits they use leaves in a band: what a band holds
        // when it holds nothing.
        val step = 1.0 / (1L shl ((effectiveBits ?: source.bitsPerSample) - 1))
        val rounding = decibels(step * step / 12 * 6 / frames.fftSize * frames.channelsAdded)

        val lowEdge = ceil(LOW_EDGE_HZ / bandWidth).toInt()
        val window = (CLIFF_WINDOW_HZ / bandWidth).roundToInt().coerceAtLeast(2)
        val lowest = maxOf(lowEdge + window, ceil(MIN_CUTOFF_HZ / bandWidth).toInt())
        var top: Cliff? = null
        var bottom: Cliff? = null
        var current: Cliff? = null
        var deepest = 0.0
        val below = DoubleArray(window)
        fun close() {
            val cliff = current ?: return
            current = null
            if (cliff.frequency(bandWidth) >= nyquist - TOP_MARGIN_HZ) return
            if (top == null) top = cliff
            bottom = cliff
        }
        for (band in bands - 2 downTo lowest) {
            var above = high[band + 1]
            for (i in band + 2..minOf(bands - 1, band + window)) above = maxOf(above, high[i])
            mean.copyInto(below, 0, band - window, band)
            below.sort()
            val drop = (below[(window - 1) / 2] + below[window / 2]) / 2 - above
            val isCliff =
                drop >= MIN_STEP_DB &&
                    (above <= rounding + DIGITAL_MARGIN_DB || drop >= DEEP_STEP_DB)
            if (!isCliff) {
                close()
            } else {
                deepest = if (current == null) drop else maxOf(deepest, drop)
                // The bands right above a wall see all of it as well, so the lowest band that
                // does is where the wall stands.
                if (drop >= deepest - SAME_STEP_DB) current = Cliff(band, deepest, above)
            }
        }
        close()

        val cutoff = bottom
        val cutoffHz = cutoff?.frequency(bandWidth)
        val shelf = hasShelfAt16k(median, bandWidth, nyquist, rounding)
        val upsampled =
            source.sampleRate >= HIGH_RESOLUTION_HZ &&
                top.let { it != null && it.frequency(bandWidth) <= nyquist * UPSAMPLED_FRACTION }
        val padded = effectiveBits != null && effectiveBits <= 16 && source.bitsPerSample > 16

        var reason: UnclearReason? = null
        val verdict: LosslessVerdict
        val confidence: Double
        when {
            active.size < MIN_ROWS -> {
                verdict = LosslessVerdict.Unclear
                reason = UnclearReason.TooQuiet
                confidence = 0.0
            }
            nyquist < MIN_NYQUIST_HZ -> {
                verdict = LosslessVerdict.Unclear
                reason = UnclearReason.LowSampleRate
                confidence = 0.0
            }
            cutoff != null && cutoffHz != null && cutoffHz < LOSSY_BELOW_HZ -> {
                val depth = ((cutoff.step - STRONG_STEP_DB) / 25).coerceIn(0.0, 1.0)
                val digital = cutoff.above <= rounding + DIGITAL_MARGIN_DB
                if (cutoff.step >= STRONG_STEP_DB && cutoffHz >= LOW_BITRATE_BELOW_HZ) {
                    verdict = LosslessVerdict.Transcoded
                    confidence = 0.7 + 0.25 * depth - if (digital) 0.0 else 0.1
                } else {
                    verdict = LosslessVerdict.ProbablyTranscoded
                    confidence = 0.5
                }
            }
            cutoff != null && cutoffHz != null && cutoffHz < LIKELY_LOSSY_BELOW_HZ -> {
                verdict = LosslessVerdict.ProbablyTranscoded
                confidence = 0.55 + 0.15 * ((cutoff.step - STRONG_STEP_DB) / 25).coerceIn(0.0, 1.0)
            }
            cutoff != null && cutoffHz != null && cutoffHz < GENUINE_FROM_HZ -> {
                verdict = LosslessVerdict.Unclear
                reason = UnclearReason.BorderlineCutoff
                confidence = 0.0
            }
            shelf -> {
                verdict = LosslessVerdict.ProbablyTranscoded
                confidence = 0.6
            }
            upsampled || padded -> {
                verdict = LosslessVerdict.Upscaled
                confidence = if (padded) 0.95 else 0.85
            }
            else -> {
                verdict = LosslessVerdict.Lossless
                confidence = 0.8
            }
        }
        val lossy =
            verdict == LosslessVerdict.Transcoded || verdict == LosslessVerdict.ProbablyTranscoded
        return LosslessReport(
            format = source.format,
            verdict = verdict,
            confidence = confidence,
            unclearReason = reason,
            sampleRateHz = source.sampleRate,
            bitsPerSample = source.bitsPerSample,
            channels = source.channels,
            cutoffHz = cutoffHz?.roundToInt(),
            estimatedSourceKbps =
                cutoffHz?.takeIf { lossy && it < LIKELY_LOSSY_BELOW_HZ }?.let(::bitrateFor),
            shelfAt16kHz = shelf,
            upsampled = upsampled,
            effectiveBitsPerSample = effectiveBits,
            spectrum = curve(mean, peak, bandWidth),
            analyzedSeconds =
                frames.frameCount.toDouble() * (frames.fftSize / 2) / source.sampleRate,
        )
    }

    /** The rows with music in them: within [ACTIVE_RANGE] of the loud ones, low rumble left out. */
    private fun activeRows(frames: SpectralFrames, bandWidth: Double): IntArray {
        val firstBand = ceil(200 / bandWidth).toInt()
        val energy =
            FloatArray(frames.frameCount) { index ->
                val row = frames.row(index)
                var sum = 0f
                for (band in firstBand until row.size) sum += row[band]
                sum
            }
        val threshold = maxOf(percentile(energy.sortedArray(), 0.95) * ACTIVE_RANGE, SILENCE)
        return (0 until frames.frameCount).filter { energy[it] > threshold }.toIntArray()
    }

    /**
     * The value [fraction] of the way through [sorted], between two of them where it falls there.
     */
    private fun percentile(sorted: FloatArray, fraction: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val position = fraction * (sorted.size - 1)
        val index = position.toInt()
        val next = minOf(index + 1, sorted.lastIndex)
        return sorted[index] + (sorted[next] - sorted[index]) * (position - index)
    }

    /**
     * MP3 has no way to say how loud its band above 16 kHz is, so encoders drop it whenever bits
     * are short: in most moments it is silent, in the others it is there. The level most moments
     * have then falls off a step at 16 kHz that no instrument plays.
     */
    private fun hasShelfAt16k(
        median: DoubleArray,
        bandWidth: Double,
        nyquist: Double,
        rounding: Double,
    ): Boolean {
        if (nyquist <= 18_000) return false
        fun level(from: Double, to: Double): Double {
            val first = ceil(from / bandWidth - 0.5).toInt()
            val last = ceil(to / bandWidth - 0.5).toInt() - 1
            if (last < first) return Double.NaN
            val values = median.copyOfRange(first, last + 1).also { it.sort() }
            return (values[(values.size - 1) / 2] + values[values.size / 2]) / 2
        }
        val under = level(15_300.0, 15_900.0)
        val over = level(16_200.0, 16_800.0)
        return under - over >= SHELF_DB && over <= rounding + DIGITAL_MARGIN_DB
    }

    /** The bitrate whose usual cut-off is nearest under [cutoffHz]. */
    private fun bitrateFor(cutoffHz: Double): Int =
        when {
            cutoffHz < 11_800 -> 64
            cutoffHz < 15_900 -> 96
            cutoffHz < 17_300 -> 128
            cutoffHz < 18_300 -> 160
            cutoffHz < 19_400 -> 192
            cutoffHz < LOSSY_BELOW_HZ -> 256
            else -> 320
        }

    private fun curve(mean: DoubleArray, peak: DoubleArray, bandWidth: Double): SpectrumCurve {
        val merge = ceil(mean.size.toDouble() / MAX_CURVE_POINTS).toInt().coerceAtLeast(1)
        val points = mean.size / merge
        return SpectrumCurve(
            bandWidthHz = (bandWidth * merge).toFloat(),
            averageDb =
                FloatArray(points) { point ->
                    var sum = 0.0
                    for (band in point * merge until (point + 1) * merge) sum += mean[band]
                    (sum / merge).toFloat()
                },
            peakDb =
                FloatArray(points) { point ->
                    var highest = peak[point * merge]
                    for (band in point * merge until (point + 1) * merge) {
                        highest = maxOf(highest, peak[band])
                    }
                    highest.toFloat()
                },
        )
    }
}
