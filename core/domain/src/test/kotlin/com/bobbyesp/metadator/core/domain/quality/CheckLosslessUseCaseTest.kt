/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.model.ContentRef
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckLosslessUseCaseTest {
    private val flac = ContentRef("content://media/external/audio/media/1")
    private val wav = ContentRef("content://media/external/audio/media/2")
    private val mp3 = ContentRef("content://media/external/audio/media/3")
    private val gone = ContentRef("content://media/external/audio/media/4")

    private val opener =
        FakeFileOpener(
            mapOf(
                flac to fixture("music-16bit-stereo.flac"),
                wav to wav(noise(44_100, 16, shape = cutAt(16_000.0)), sampleRate = 44_100),
                mp3 to ByteArray(8_192) { (it * 7).toByte() },
            )
        )

    private suspend fun TestScope.check(ref: ContentRef): LosslessCheckResult {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CheckLosslessUseCase(opener, AppDispatchers(dispatcher, dispatcher))(ref)
    }

    @Test
    fun `a FLAC file is decoded and judged`() = runTest {
        val report = (check(flac) as LosslessCheckResult.Checked).report

        assertEquals(LosslessFormat.Flac, report.format)
        assertEquals(44_100, report.sampleRateHz)
        assertEquals(2, report.channels)
        assertTrue(report.analyzedSeconds > 0)
    }

    @Test
    fun `a WAV file is read and judged`() = runTest {
        val report = (check(wav) as LosslessCheckResult.Checked).report

        assertEquals(LosslessFormat.Wav, report.format)
        assertEquals(LosslessVerdict.Transcoded, report.verdict)
        assertEquals(128, report.estimatedSourceKbps)
    }

    @Test
    fun `a lossy file is not what is checked`() = runTest {
        assertEquals(LosslessCheckResult.NotApplicable, check(mp3))
    }

    @Test
    fun `a file that is gone is unreadable`() = runTest {
        assertEquals(LosslessCheckResult.Unreadable, check(gone))
    }

    @Test
    fun `a WAV file holds what was written to it`() {
        val samples = intArrayOf(0, 1, -1, 32_767, -32_768, 1_234)
        val source =
            checkNotNull(WavSource.open(MemoryAudioFile(wav(samples, sampleRate = 48_000))))

        assertEquals(48_000, source.sampleRate)
        assertEquals(16, source.bitsPerSample)
        assertEquals(samples.size.toLong(), source.totalFrames)
        assertEquals(samples.toList(), checkNotNull(source.read(0.0, samples.size))[0].toList())
    }

    @Test
    fun `a WAV file of something other than PCM is not opened`() {
        val bytes = wav(intArrayOf(1, 2, 3), sampleRate = 44_100)
        // The format tag of MP3 in a WAV container.
        bytes[20] = 0x55

        assertNull(WavSource.open(MemoryAudioFile(bytes)))
    }

    /** A mono, 16-bit WAV file holding [samples]. */
    private fun wav(samples: IntArray, sampleRate: Int): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it.toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        header.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.capacity())
        return ByteArrayOutputStream()
            .apply {
                write(header.array())
                write(data.array())
            }
            .toByteArray()
    }
}
