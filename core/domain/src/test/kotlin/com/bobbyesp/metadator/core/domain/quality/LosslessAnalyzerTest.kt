/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessAnalyzerTest {
    private fun analyze(
        sampleRate: Int = 44_100,
        bits: Int = 16,
        hiss: Double? = null,
        shape: (Double) -> Double,
    ): LosslessReport =
        checkNotNull(
            LosslessAnalyzer.analyze(
                SyntheticAudio(sampleRate, bits, noise(sampleRate, bits, hiss, shape = shape))
            )
        )

    private fun assertCutoff(expectedHz: Int, report: LosslessReport) {
        val cutoff = checkNotNull(report.cutoffHz) { "No cut-off found" }
        assertTrue("Cut-off at $cutoff Hz", cutoff in expectedHz - 400..expectedHz + 400)
    }

    @Test
    fun `audio that fills the band is lossless`() {
        val report = analyze { 1.0 }

        assertEquals(LosslessVerdict.Lossless, report.verdict)
        assertNull(report.cutoffHz)
        assertNull(report.estimatedSourceKbps)
        assertNull(report.effectiveBitsPerSample)
        assertEquals(16, report.bitsPerSample)
    }

    @Test
    fun `the cut-off of an MP3 at 128 kbps is told`() {
        val report = analyze(shape = cutAt(16_000.0))

        assertEquals(LosslessVerdict.Transcoded, report.verdict)
        assertCutoff(16_000, report)
        assertEquals(128, report.estimatedSourceKbps)
        assertTrue(report.confidence > 0.8)
    }

    @Test
    fun `where it cuts tells the bitrate`() {
        val expected = mapOf(15_000.0 to 96, 17_800.0 to 160, 19_000.0 to 192, 19_650.0 to 256)
        for ((cutoff, kbps) in expected) {
            val report = analyze(shape = cutAt(cutoff))

            assertEquals("Cut at $cutoff Hz", LosslessVerdict.Transcoded, report.verdict)
            assertEquals("Cut at $cutoff Hz", kbps, report.estimatedSourceKbps)
        }
    }

    @Test
    fun `a cut-off at 20 kHz is only probably lossy`() {
        // Where an MP3 at 320 kbps and Opus end, and a few genuine masters too.
        val report = analyze(shape = cutAt(20_400.0))

        assertEquals(LosslessVerdict.ProbablyTranscoded, report.verdict)
        assertEquals(320, report.estimatedSourceKbps)
        assertTrue(report.confidence < 0.75)
    }

    @Test
    fun `the filter of a CD is not a lossy cut-off`() {
        // Converters cut a little under half the sample rate, and that is all a genuine CD lacks.
        val report = analyze(shape = cutAt(21_500.0))

        assertEquals(LosslessVerdict.Lossless, report.verdict)
        assertCutoff(21_500, report)
        assertNull(report.estimatedSourceKbps)
    }

    @Test
    fun `a cut-off at the very top of the band is not one`() {
        val report = analyze(shape = cutAt(21_900.0))

        assertEquals(LosslessVerdict.Lossless, report.verdict)
        assertNull(report.cutoffHz)
    }

    @Test
    fun `a cut-off between the two is left open`() {
        val report = analyze(shape = cutAt(21_000.0))

        assertEquals(LosslessVerdict.Unclear, report.verdict)
        assertEquals(UnclearReason.BorderlineCutoff, report.unclearReason)
    }

    @Test
    fun `music that fades into its own noise is lossless`() {
        // Treble falling by 3 dB per kHz: quiet up there, but never cut.
        val report = analyze { frequency -> 10.0.pow(-frequency / 1_000 * 3 / 20) }

        assertEquals(LosslessVerdict.Lossless, report.verdict)
    }

    @Test
    fun `an old recording that fades into tape hiss is lossless`() {
        // Treble rolling off from 6 kHz into hiss that no frequency is free of: a narrow band, but
        // no cliff, and not the silence a decoder leaves.
        val report =
            analyze(hiss = -70.0) { frequency ->
                10.0.pow(-maxOf(0.0, frequency - 6_000) / 1_000 * 6 / 20)
            }

        assertEquals(LosslessVerdict.Lossless, report.verdict)
        assertNull(report.cutoffHz)
        assertNull(report.estimatedSourceKbps)
    }

    @Test
    fun `a brick wall is a filter even over noise`() {
        // A lossy file that went through an analog stage, or had noise added to pass for genuine:
        // no silence above the cut-off, but no recording ends in a wall either.
        val overNoise = analyze(hiss = -70.0, shape = cutAt(16_000.0))
        val overSilence = analyze(shape = cutAt(16_000.0))

        assertEquals(LosslessVerdict.Transcoded, overNoise.verdict)
        assertCutoff(16_000, overNoise)
        assertTrue(overNoise.confidence < overSilence.confidence)
    }

    @Test
    fun `dither does not hide a lossy cut-off`() {
        // A decoder that dithers its output leaves noise of about one step above the cut-off.
        val report = analyze(hiss = -96.0, shape = cutAt(16_000.0))

        assertEquals(LosslessVerdict.Transcoded, report.verdict)
        assertCutoff(16_000, report)
    }

    @Test
    fun `the band above 16 kHz coming and going is the mark of an MP3`() {
        // Loud up to 16 kHz in every moment; above it only in one moment out of four.
        val full = noise(44_100, 16) { 1.0 }
        val cut = noise(44_100, 16, shape = cutAt(16_000.0))
        val source =
            object : PcmSource by SyntheticAudio(44_100, 16, cut, seconds = 36) {
                override fun read(fraction: Double, frames: Int): Array<IntArray> {
                    val period = if ((fraction * 12).toInt() % 4 == 0) full else cut
                    return SyntheticAudio(44_100, 16, period, seconds = 36).read(fraction, frames)
                }
            }

        val report = checkNotNull(LosslessAnalyzer.analyze(source))

        assertTrue(report.shelfAt16kHz)
        assertEquals(LosslessVerdict.ProbablyTranscoded, report.verdict)
    }

    @Test
    fun `16 bit audio in a 24 bit file is upscaled`() {
        val padded = noise(44_100, 16) { 1.0 }.map { it shl 8 }.toIntArray()

        val report = checkNotNull(LosslessAnalyzer.analyze(SyntheticAudio(44_100, 24, padded)))

        assertEquals(LosslessVerdict.Upscaled, report.verdict)
        assertEquals(16, report.effectiveBitsPerSample)
        assertEquals(24, report.bitsPerSample)
    }

    @Test
    fun `a lossy source is still told in a 24 bit file`() {
        val padded = noise(44_100, 16, shape = cutAt(16_000.0)).map { it shl 8 }.toIntArray()

        val report = checkNotNull(LosslessAnalyzer.analyze(SyntheticAudio(44_100, 24, padded)))

        assertEquals(LosslessVerdict.Transcoded, report.verdict)
        assertEquals(16, report.effectiveBitsPerSample)
    }

    @Test
    fun `CD audio at a high sample rate is upscaled`() {
        val report = analyze(sampleRate = 96_000, bits = 24, shape = cutAt(21_800.0))

        assertEquals(LosslessVerdict.Upscaled, report.verdict)
        assertTrue(report.upsampled)
        assertNull(report.estimatedSourceKbps)
    }

    @Test
    fun `high resolution audio that uses its band is lossless`() {
        val report =
            analyze(sampleRate = 96_000, bits = 24, hiss = -110.0) { frequency ->
                10.0.pow(-frequency / 1_000 * 1.5 / 20)
            }

        assertEquals(LosslessVerdict.Lossless, report.verdict)
        assertTrue(!report.upsampled)
    }

    @Test
    fun `silence cannot be judged`() {
        val report =
            checkNotNull(LosslessAnalyzer.analyze(SyntheticAudio(44_100, 16, IntArray(1 shl 17))))

        assertEquals(LosslessVerdict.Unclear, report.verdict)
        assertEquals(UnclearReason.TooQuiet, report.unclearReason)
    }

    @Test
    fun `a low sample rate cannot be judged`() {
        val report = analyze(sampleRate = 22_050) { 1.0 }

        assertEquals(LosslessVerdict.Unclear, report.verdict)
        assertEquals(UnclearReason.LowSampleRate, report.unclearReason)
    }

    @Test
    fun `the spectrum is kept for the chart`() {
        val report = analyze(shape = cutAt(16_000.0))
        val curve = report.spectrum

        assertEquals(22_050f, curve.nyquistHz, 1f)
        assertEquals(curve.averageDb.size, curve.peakDb.size)
        val below = curve.averageDb[(10_000 / curve.bandWidthHz).toInt()]
        val above = curve.averageDb[(19_000 / curve.bandWidthHz).toInt()]
        assertTrue("$below dB against $above dB", below - above > 50)
        assertNotNull(report.analyzedSeconds)
    }

    @Test
    fun `nothing decodable is no report`() {
        val empty =
            object : PcmSource by SyntheticAudio(44_100, 16, IntArray(16)) {
                override fun read(fraction: Double, frames: Int): Array<IntArray>? = null
            }

        assertNull(LosslessAnalyzer.analyze(empty))
    }
}
