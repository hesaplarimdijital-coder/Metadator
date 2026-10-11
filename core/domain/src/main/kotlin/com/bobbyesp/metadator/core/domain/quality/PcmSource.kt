/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

import com.bobbyesp.metadator.library.api.OpenedAudioFile

/** The formats whose audio can be looked at: the ones that are read here, without a decoder. */
enum class LosslessFormat {
    Flac,
    Wav,
}

/** The audio of a lossless file, handed out a stretch at a time from anywhere in it. */
internal interface PcmSource {
    val format: LosslessFormat
    val sampleRate: Int
    val channels: Int
    val bitsPerSample: Int

    /** How many sample frames the file holds, or zero when it does not say. */
    val totalFrames: Long

    /**
     * About [frames] sample frames in a row, found [fraction] of the way through the audio (0 is
     * its start, 1 its end), one array per channel. Null when nothing there could be decoded.
     */
    fun read(fraction: Double, frames: Int): Array<IntArray>?
}

/** Reads [length] bytes at [position], fewer only where the file ends first. */
internal fun OpenedAudioFile.readFully(position: Long, buffer: ByteArray, length: Int): Int {
    var total = 0
    while (total < length) {
        val read = read(position + total, buffer, total, length - total)
        if (read <= 0) break
        total += read
    }
    return total
}

/**
 * A FLAC file, decoded where it is asked for rather than from its start: frames are found by their
 * sync code and kept only when their checksums hold, so no seek table is needed.
 */
internal class FlacSource
private constructor(
    private val file: OpenedAudioFile,
    val info: FlacStreamInfo,
    private val audioStart: Long,
    private val audioEnd: Long,
) : PcmSource {
    override val format = LosslessFormat.Flac
    override val sampleRate: Int
        get() = info.sampleRate

    override val channels: Int
        get() = info.channels

    override val bitsPerSample: Int
        get() = info.bitsPerSample

    override val totalFrames: Long
        get() = info.totalSamples

    override fun read(fraction: Double, frames: Int): Array<IntArray>? {
        val span = audioEnd - audioStart
        if (span <= 0) return null
        val rawFrameBytes = info.channels * ((info.bitsPerSample + 7) / 8)
        val bytesPerFrame =
            if (info.totalSamples > 0) span.toDouble() / info.totalSamples
            else rawFrameBytes * TYPICAL_RATIO
        val largestFrame =
            if (info.maxFrameSize > 0) info.maxFrameSize
            else info.maxBlockSize.coerceAtLeast(DEFAULT_BLOCK) * (rawFrameBytes + 1) + 64
        val wanted = (frames * bytesPerFrame * HEADROOM).toLong() + 2L * largestFrame
        val length = wanted.coerceIn(MIN_READ, MAX_READ).coerceAtMost(span).toInt()
        val start = audioStart + ((span - length) * fraction.coerceIn(0.0, 1.0)).toLong()

        // A file short enough to be read whole is all in the buffer wherever it is asked for, so
        // the place is found by counting samples instead.
        var toSkip =
            if (length.toLong() == span && info.totalSamples > frames) {
                ((info.totalSamples - frames) * fraction.coerceIn(0.0, 1.0)).toLong()
            } else 0L

        val buffer = ByteArray(length)
        val available = file.readFully(start, buffer, length)
        val decoded = ArrayList<FlacFrame>()
        var collected = 0
        var position = FlacFrames.findSync(buffer, 0, available)
        while (position >= 0 && collected < frames) {
            val frame = FlacFrames.decode(buffer, position, available, info)
            position =
                if (frame == null) {
                    FlacFrames.findSync(buffer, position + 1, available)
                } else {
                    if (toSkip >= frame.blockSize) {
                        toSkip -= frame.blockSize
                    } else {
                        toSkip = 0
                        decoded += frame
                        collected += frame.blockSize
                    }
                    FlacFrames.findSync(buffer, position + frame.byteLength, available)
                }
        }
        if (collected == 0) return null
        return Array(info.channels) { channel ->
            val joined = IntArray(collected)
            var offset = 0
            for (frame in decoded) {
                frame.samples[channel].copyInto(joined, offset)
                offset += frame.blockSize
            }
            joined
        }
    }

    companion object {
        /** A frame compresses to somewhat over half its size; this errs on the side of reading. */
        private const val TYPICAL_RATIO = 0.7
        private const val HEADROOM = 1.5
        private const val DEFAULT_BLOCK = 4608
        private const val MIN_READ = 64L * 1024
        private const val MAX_READ = 16L * 1024 * 1024
        private const val STREAMINFO = 0
        private const val STREAMINFO_LENGTH = 34

        /** [file] as FLAC, or null when it is not, or says nothing usable about its audio. */
        fun open(file: OpenedAudioFile): FlacSource? {
            val header = ByteArray(STREAMINFO_LENGTH)
            var position = 0L
            if (file.readFully(position, header, 10) < 10) return null
            // Some taggers put an ID3v2 tag in front of the FLAC marker.
            if (
                header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
            ) {
                val size =
                    ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)
                val footer = if (header[5].toInt() and 0x10 != 0) 10 else 0
                position = 10L + size + footer
                if (file.readFully(position, header, 4) < 4) return null
            }
            if (String(header, 0, 4, Charsets.ISO_8859_1) != "fLaC") return null
            position += 4

            var info: FlacStreamInfo? = null
            while (true) {
                if (file.readFully(position, header, 4) < 4) return null
                val last = header[0].toInt() and 0x80 != 0
                val type = header[0].toInt() and 0x7F
                val length =
                    ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                position += 4
                if (type == STREAMINFO && length >= STREAMINFO_LENGTH && info == null) {
                    if (file.readFully(position, header, STREAMINFO_LENGTH) < STREAMINFO_LENGTH) {
                        return null
                    }
                    info = parseStreamInfo(header)
                }
                position += length
                if (last) break
            }
            val stream = info ?: return null
            if (
                stream.sampleRate <= 0 ||
                    stream.channels !in 1..8 ||
                    stream.bitsPerSample !in 4..FlacFrames.MAX_BITS_PER_SAMPLE
            ) {
                return null
            }
            // Without a length there is no telling where in the audio a position falls.
            if (file.size <= position) return null
            return FlacSource(file, stream, position, file.size)
        }

        private fun parseStreamInfo(block: ByteArray): FlacStreamInfo {
            fun byte(index: Int) = block[index].toInt() and 0xFF
            val packed =
                (byte(10).toLong() shl 56) or
                    (byte(11).toLong() shl 48) or
                    (byte(12).toLong() shl 40) or
                    (byte(13).toLong() shl 32) or
                    (byte(14).toLong() shl 24) or
                    (byte(15).toLong() shl 16) or
                    (byte(16).toLong() shl 8) or
                    byte(17).toLong()
            return FlacStreamInfo(
                maxBlockSize = (byte(2) shl 8) or byte(3),
                maxFrameSize = (byte(7) shl 16) or (byte(8) shl 8) or byte(9),
                sampleRate = (packed ushr 44).toInt(),
                channels = ((packed ushr 41) and 0x7).toInt() + 1,
                bitsPerSample = ((packed ushr 36) and 0x1F).toInt() + 1,
                totalSamples = packed and 0xFFFFFFFFFL,
                md5 = block.copyOfRange(18, 34),
            )
        }
    }
}

/** A WAV file with plain PCM in it, integer or floating point. */
internal class WavSource
private constructor(
    private val file: OpenedAudioFile,
    override val sampleRate: Int,
    override val channels: Int,
    private val storedBits: Int,
    private val isFloat: Boolean,
    private val blockAlign: Int,
    private val dataStart: Long,
    override val totalFrames: Long,
) : PcmSource {
    override val format = LosslessFormat.Wav

    /** Floating point is read as 24 bits, which is all the precision it has. */
    override val bitsPerSample: Int = if (isFloat) FLOAT_BITS else storedBits

    override fun read(fraction: Double, frames: Int): Array<IntArray>? {
        val count = minOf(frames.toLong(), totalFrames).toInt()
        if (count <= 0) return null
        val first = ((totalFrames - count) * fraction.coerceIn(0.0, 1.0)).toLong()
        val buffer = ByteArray(count * blockAlign)
        val read = file.readFully(dataStart + first * blockAlign, buffer, buffer.size) / blockAlign
        if (read <= 0) return null
        val bytes = storedBits / 8
        return Array(channels) { channel ->
            IntArray(read) { frame -> sample(buffer, frame * blockAlign + channel * bytes, bytes) }
        }
    }

    private fun sample(buffer: ByteArray, at: Int, bytes: Int): Int {
        fun unsigned(index: Int) = buffer[at + index].toInt() and 0xFF
        return when {
            bytes == 1 -> unsigned(0) - 128
            bytes == 2 -> (buffer[at + 1].toInt() shl 8) or unsigned(0)
            bytes == 3 -> (buffer[at + 2].toInt() shl 16) or (unsigned(1) shl 8) or unsigned(0)
            isFloat -> {
                val bits =
                    (unsigned(3) shl 24) or
                        (unsigned(2) shl 16) or
                        (unsigned(1) shl 8) or
                        unsigned(0)
                val scaled = Float.fromBits(bits) * FLOAT_SCALE
                if (scaled.isNaN()) 0
                else scaled.toInt().coerceIn(-FLOAT_SCALE.toInt(), FLOAT_SCALE.toInt() - 1)
            }
            else ->
                (buffer[at + 3].toInt() shl 24) or
                    (unsigned(2) shl 16) or
                    (unsigned(1) shl 8) or
                    unsigned(0)
        }
    }

    companion object {
        private const val FLOAT_BITS = 24
        private const val FLOAT_SCALE = 8_388_608f
        private const val FORMAT_PCM = 1
        private const val FORMAT_FLOAT = 3
        private const val FORMAT_EXTENSIBLE = 0xFFFE

        /** [file] as WAV, or null when it is not, or holds something other than plain PCM. */
        fun open(file: OpenedAudioFile): WavSource? {
            val header = ByteArray(40)
            if (file.readFully(0, header, 12) < 12) return null
            if (tag(header, 0) != "RIFF" || tag(header, 8) != "WAVE") return null

            var position = 12L
            var format: IntArray? = null
            while (file.readFully(position, header, 8) == 8) {
                val id = tag(header, 0)
                val size = int32(header, 4).toLong() and 0xFFFFFFFFL
                position += 8
                when (id) {
                    "fmt " -> {
                        val length = minOf(size, 40L).toInt()
                        if (length < 16 || file.readFully(position, header, length) < length) {
                            return null
                        }
                        var kind = int16(header, 0)
                        if (kind == FORMAT_EXTENSIBLE && length >= 26) kind = int16(header, 24)
                        format =
                            intArrayOf(
                                kind,
                                int16(header, 2),
                                int32(header, 4),
                                int16(header, 12),
                                int16(header, 14),
                            )
                    }
                    "data" -> {
                        val (kind, channels, sampleRate, blockAlign, bits) = format ?: return null
                        val isFloat = kind == FORMAT_FLOAT && bits == 32
                        val isPcm = kind == FORMAT_PCM && bits in intArrayOf(8, 16, 24, 32)
                        if (!isFloat && !isPcm) return null
                        if (
                            channels !in 1..8 ||
                                sampleRate <= 0 ||
                                blockAlign != channels * bits / 8
                        ) {
                            return null
                        }
                        // A file written as a stream says zero, or all ones, for its length.
                        val inFile = if (file.size > position) file.size - position else size
                        val bytes = if (size == 0L || size > inFile) inFile else size
                        return WavSource(
                            file,
                            sampleRate,
                            channels,
                            bits,
                            isFloat,
                            blockAlign,
                            position,
                            bytes / blockAlign,
                        )
                    }
                }
                // Chunks start on even bytes.
                position += size + (size and 1)
            }
            return null
        }

        private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.ISO_8859_1)

        private fun int16(bytes: ByteArray, at: Int) =
            (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

        private fun int32(bytes: ByteArray, at: Int) =
            int16(bytes, at) or (int16(bytes, at + 2) shl 16)
    }
}
