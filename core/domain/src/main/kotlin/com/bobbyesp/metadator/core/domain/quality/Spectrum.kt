/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/** A radix-2 fast Fourier transform of one fixed [size], which must be a power of two. */
internal class Fft(val size: Int) {
    private val cosines = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sines = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    private val reversed =
        IntArray(size).also { table ->
            val bits = size.countTrailingZeroBits()
            for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
        }

    /** Transforms [real] and [imaginary] in place. */
    fun transform(real: DoubleArray, imaginary: DoubleArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val r = real[i]
                real[i] = real[j]
                real[j] = r
                val m = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = m
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val stride = size / length
            var start = 0
            while (start < size) {
                var k = 0
                for (i in start until start + half) {
                    val j = i + half
                    val wr = cosines[k]
                    val wi = -sines[k]
                    val xr = real[j] * wr - imaginary[j] * wi
                    val xi = real[j] * wi + imaginary[j] * wr
                    real[j] = real[i] - xr
                    imaginary[j] = imaginary[i] - xi
                    real[i] += xr
                    imaginary[i] += xi
                    k += stride
                }
                start += length
            }
            length = length shl 1
        }
    }
}

/**
 * How the audio's energy is spread over frequency, and how that changes from one moment to the
 * next: one row of band levels per stretch of about a tenth of a second.
 *
 * Levels are powers relative to a sine wave that fills the file's range (0 dB), added over the
 * channels looked at. Bands are all [bandWidthHz] wide; band `b` covers from `b * bandWidthHz` up.
 */
internal class SpectralFrames(
    val sampleRate: Int,
    val fftSize: Int,
    val bandCount: Int,
    val channelsAdded: Int,
) {
    val bandWidthHz: Double = BINS_PER_BAND.toDouble() * sampleRate / fftSize
    private val rows = ArrayList<FloatArray>()

    val frameCount: Int
        get() = rows.size

    fun row(index: Int): FloatArray = rows[index]

    private val fft = Fft(fftSize)
    private val window = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2 * PI * it / fftSize) }
    private val scale: Double = window.sum().let { 4.0 / (it * it) }
    private val real = DoubleArray(fftSize)
    private val imaginary = DoubleArray(fftSize)

    /**
     * Adds what [channels] hold, a stretch of audio in a row: half-overlapping windows of it, each
     * one row. [fullScale] is the largest value a sample can take.
     */
    fun add(channels: Array<IntArray>, fullScale: Double) {
        val length = channels[0].size
        val hop = fftSize / 2
        var start = 0
        while (start + fftSize <= length) {
            val row = FloatArray(bandCount)
            for (channel in 0 until channelsAdded) {
                val samples = channels[channel]
                for (i in 0 until fftSize) {
                    real[i] = samples[start + i] / fullScale * window[i]
                    imaginary[i] = 0.0
                }
                fft.transform(real, imaginary)
                for (band in 0 until bandCount) {
                    var power = 0.0
                    val first = band * BINS_PER_BAND
                    for (bin in first until first + BINS_PER_BAND) {
                        power += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                    }
                    row[band] += (power / BINS_PER_BAND * scale).toFloat()
                }
            }
            rows += row
            start += hop
        }
    }

    companion object {
        const val BINS_PER_BAND = 16

        /** Fine enough to tell where a filter cuts to within a couple of hundred hertz. */
        private const val MAX_BIN_HZ = 12.0
        private const val MIN_FFT = 4096

        fun forFormat(sampleRate: Int, channels: Int): SpectralFrames {
            var size = MIN_FFT
            while (sampleRate.toDouble() / size > MAX_BIN_HZ) size *= 2
            return SpectralFrames(
                sampleRate = sampleRate,
                fftSize = size,
                bandCount = size / 2 / BINS_PER_BAND,
                channelsAdded = minOf(channels, 2),
            )
        }
    }
}

internal fun decibels(power: Double): Double = 10 * log10(maxOf(power, 1e-20))
