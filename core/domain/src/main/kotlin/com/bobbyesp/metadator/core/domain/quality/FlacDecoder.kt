/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.quality

/** What a FLAC file says about its audio in its STREAMINFO block. */
internal class FlacStreamInfo(
    val maxBlockSize: Int,
    val maxFrameSize: Int,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    /** Zero when the encoder did not know. */
    val totalSamples: Long,
    /** The MD5 of the decoded audio, all zeros when the encoder did not write one. */
    val md5: ByteArray,
)

/** One decoded FLAC frame: [blockSize] samples for each channel. */
internal class FlacFrame(val blockSize: Int, val samples: Array<IntArray>, val byteLength: Int)

/** Thrown, without a stack trace, by anything that turns out not to be FLAC. */
internal object NotFlac : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** Reads bit fields most significant bit first, as FLAC writes them. */
internal class BitReader(
    private val data: ByteArray,
    private var next: Int,
    private val limit: Int,
) {
    private var cache = 0L
    private var cached = 0

    /** Where the next whole byte is, once [alignToByte] has been called. */
    val bytePosition: Int
        get() = next - cached / 8

    private fun refill() {
        while (cached <= 56 && next < limit) {
            cache = (cache shl 8) or (data[next++].toLong() and 0xFF)
            cached += 8
        }
    }

    /** The next [bits] bits, at most 32, as an unsigned number. */
    fun read(bits: Int): Int {
        if (bits == 0) return 0
        if (cached < bits) {
            refill()
            if (cached < bits) throw NotFlac
        }
        cached -= bits
        return ((cache ushr cached) and ((1L shl bits) - 1)).toInt()
    }

    fun readSigned(bits: Int): Int {
        val shift = 32 - bits
        return (read(bits) shl shift) shr shift
    }

    /** How many zero bits come before the next one bit, which is read too. */
    fun readUnary(): Int {
        var count = 0
        while (true) {
            if (cached == 0) {
                refill()
                if (cached == 0) throw NotFlac
            }
            val zeros = (cache shl (64 - cached)).countLeadingZeroBits()
            if (zeros < cached) {
                cached -= zeros + 1
                return count + zeros
            }
            count += cached
            cached = 0
        }
    }

    fun readRice(parameter: Int): Int {
        val folded = (readUnary() shl parameter) or read(parameter)
        return (folded ushr 1) xor -(folded and 1)
    }

    fun alignToByte() {
        cached -= cached % 8
    }
}

/**
 * Decodes FLAC frames. Written for looking at the audio, not for playing it: it decodes a frame
 * found anywhere in the file, and trusts nothing it cannot check. A frame counts only when both of
 * its checksums hold, which is also what tells a real frame from two bytes that look like the start
 * of one in the middle of another.
 */
internal object FlacFrames {
    private const val MAX_CHANNELS = 8

    /** Up to 24 bits: a side channel needs one more, and samples are kept in 32. */
    const val MAX_BITS_PER_SAMPLE = 24

    /**
     * The frame starting at [start] of [data], or null when there is none there, or not all of it
     * is before [limit].
     */
    fun decode(data: ByteArray, start: Int, limit: Int, info: FlacStreamInfo): FlacFrame? {
        if (limit - start < 8) return null
        if ((data[start].toInt() and 0xFF) != 0xFF || (data[start + 1].toInt() and 0xFE) != 0xF8) {
            return null
        }
        return try {
            decodeFrame(data, start, limit, info)
        } catch (_: NotFlac) {
            null
        }
    }

    /** Where the next frame may start at or after [from]: the two bytes every frame begins with. */
    fun findSync(data: ByteArray, from: Int, limit: Int): Int {
        var index = from
        while (index < limit - 1) {
            if (data[index] == 0xFF.toByte() && (data[index + 1].toInt() and 0xFE) == 0xF8) {
                return index
            }
            index++
        }
        return -1
    }

    private fun decodeFrame(
        data: ByteArray,
        start: Int,
        limit: Int,
        info: FlacStreamInfo,
    ): FlacFrame {
        val sizeAndRate = data[start + 2].toInt() and 0xFF
        val layout = data[start + 3].toInt() and 0xFF
        if (layout and 1 != 0) throw NotFlac
        val blockSizeCode = sizeAndRate ushr 4
        val sampleRateCode = sizeAndRate and 0x0F
        val channelAssignment = layout ushr 4
        val sampleSizeCode = (layout ushr 1) and 0x07

        // The frame or sample number, coded like UTF-8: its length is in its first byte.
        var position = start + 4
        val first = data[position].toInt() and 0xFF
        val numberLength = if (first < 0x80) 1 else (first shl 24).inv().countLeadingZeroBits()
        if (numberLength > 7 || (numberLength == 1 && first >= 0x80)) throw NotFlac
        if (position + numberLength > limit) throw NotFlac
        for (index in 1 until numberLength) {
            if ((data[position + index].toInt() and 0xC0) != 0x80) throw NotFlac
        }
        position += numberLength

        val blockSize =
            when (blockSizeCode) {
                0 -> throw NotFlac
                1 -> 192
                in 2..5 -> 576 shl (blockSizeCode - 2)
                6 -> {
                    if (position + 1 > limit) throw NotFlac
                    (data[position++].toInt() and 0xFF) + 1
                }
                7 -> {
                    if (position + 2 > limit) throw NotFlac
                    val high = data[position++].toInt() and 0xFF
                    ((high shl 8) or (data[position++].toInt() and 0xFF)) + 1
                }
                else -> 256 shl (blockSizeCode - 8)
            }
        val sampleRate =
            when (sampleRateCode) {
                0 -> info.sampleRate
                12 -> {
                    if (position + 1 > limit) throw NotFlac
                    (data[position++].toInt() and 0xFF) * 1000
                }
                13,
                14 -> {
                    if (position + 2 > limit) throw NotFlac
                    val high = data[position++].toInt() and 0xFF
                    val value = (high shl 8) or (data[position++].toInt() and 0xFF)
                    if (sampleRateCode == 13) value else value * 10
                }
                15 -> throw NotFlac
                else -> SAMPLE_RATES[sampleRateCode]
            }
        val channels =
            when (channelAssignment) {
                in 0..7 -> channelAssignment + 1
                in 8..10 -> 2
                else -> throw NotFlac
            }
        val bitsPerSample =
            when (sampleSizeCode) {
                0 -> info.bitsPerSample
                3 -> throw NotFlac
                else -> SAMPLE_SIZES[sampleSizeCode]
            }
        if (position >= limit || crc8(data, start, position) != (data[position].toInt() and 0xFF)) {
            throw NotFlac
        }
        position++

        // A frame of another stream, or of another part of a stream that changes on the way: not
        // what the file says it holds, so not what is being looked at.
        if (
            channels != info.channels ||
                bitsPerSample != info.bitsPerSample ||
                sampleRate != info.sampleRate ||
                channels > MAX_CHANNELS ||
                bitsPerSample > MAX_BITS_PER_SAMPLE
        ) {
            throw NotFlac
        }

        val reader = BitReader(data, position, limit)
        val samples =
            Array(channels) { channel ->
                val isSide =
                    when (channelAssignment) {
                        8,
                        10 -> channel == 1
                        9 -> channel == 0
                        else -> false
                    }
                IntArray(blockSize).also {
                    decodeSubframe(reader, it, if (isSide) bitsPerSample + 1 else bitsPerSample)
                }
            }
        reader.alignToByte()
        val end = reader.bytePosition
        if (end + 2 > limit) throw NotFlac
        val expected = ((data[end].toInt() and 0xFF) shl 8) or (data[end + 1].toInt() and 0xFF)
        if (crc16(data, start, end) != expected) throw NotFlac

        when (channelAssignment) {
            8 -> {
                val left = samples[0]
                val side = samples[1]
                for (i in 0 until blockSize) side[i] = left[i] - side[i]
            }
            9 -> {
                val side = samples[0]
                val right = samples[1]
                for (i in 0 until blockSize) side[i] += right[i]
            }
            10 -> {
                val mid = samples[0]
                val side = samples[1]
                for (i in 0 until blockSize) {
                    val s = side[i]
                    val m = (mid[i] shl 1) or (s and 1)
                    mid[i] = (m + s) shr 1
                    side[i] = (m - s) shr 1
                }
            }
        }
        return FlacFrame(blockSize, samples, end + 2 - start)
    }

    private fun decodeSubframe(reader: BitReader, out: IntArray, frameBits: Int) {
        if (reader.read(1) != 0) throw NotFlac
        val type = reader.read(6)
        // Bits every sample of the block has at zero are left out, and counted here.
        val wasted = if (reader.read(1) == 1) reader.readUnary() + 1 else 0
        val bits = frameBits - wasted
        if (bits <= 0) throw NotFlac
        when {
            type == 0 -> out.fill(reader.readSigned(bits))
            type == 1 -> for (i in out.indices) out[i] = reader.readSigned(bits)
            type in 8..12 -> decodeFixed(reader, out, type - 8, bits)
            type >= 32 -> decodeLpc(reader, out, type - 31, bits)
            else -> throw NotFlac
        }
        if (wasted > 0) for (i in out.indices) out[i] = out[i] shl wasted
    }

    private fun decodeFixed(reader: BitReader, out: IntArray, order: Int, bits: Int) {
        if (order > out.size) throw NotFlac
        for (i in 0 until order) out[i] = reader.readSigned(bits)
        decodeResidual(reader, out, order)
        when (order) {
            1 -> for (i in 1 until out.size) out[i] += out[i - 1]
            2 -> for (i in 2 until out.size) out[i] += 2 * out[i - 1] - out[i - 2]
            3 ->
                for (i in 3 until out.size) {
                    out[i] += 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3]
                }
            4 ->
                for (i in 4 until out.size) {
                    out[i] += 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4]
                }
        }
    }

    private fun decodeLpc(reader: BitReader, out: IntArray, order: Int, bits: Int) {
        if (order > out.size) throw NotFlac
        for (i in 0 until order) out[i] = reader.readSigned(bits)
        val precision = reader.read(4) + 1
        if (precision == 16) throw NotFlac
        val shift = reader.readSigned(5)
        if (shift < 0) throw NotFlac
        val coefficients = IntArray(order) { reader.readSigned(precision) }
        decodeResidual(reader, out, order)
        for (i in order until out.size) {
            var sum = 0L
            for (j in 0 until order) sum += coefficients[j].toLong() * out[i - 1 - j]
            out[i] += (sum shr shift).toInt()
        }
    }

    /** The residual of a block: what is left of each sample once the predictor has had its say. */
    private fun decodeResidual(reader: BitReader, out: IntArray, predictorOrder: Int) {
        val method = reader.read(2)
        if (method > 1) throw NotFlac
        val parameterBits = if (method == 0) 4 else 5
        val escape = (1 shl parameterBits) - 1
        val partitionOrder = reader.read(4)
        val partitionSize = out.size ushr partitionOrder
        if (partitionSize shl partitionOrder != out.size || partitionSize < predictorOrder) {
            throw NotFlac
        }
        var index = predictorOrder
        for (partition in 0 until (1 shl partitionOrder)) {
            val end = (partition + 1) * partitionSize
            val parameter = reader.read(parameterBits)
            if (parameter == escape) {
                val bits = reader.read(5)
                while (index < end) out[index++] = if (bits == 0) 0 else reader.readSigned(bits)
            } else {
                while (index < end) out[index++] = reader.readRice(parameter)
            }
        }
    }

    private val SAMPLE_RATES =
        intArrayOf(0, 88200, 176400, 192000, 8000, 16000, 22050, 24000, 32000, 44100, 48000, 96000)
    private val SAMPLE_SIZES = intArrayOf(0, 8, 12, 0, 16, 20, 24, 32)

    private val CRC8_TABLE =
        IntArray(256) { index ->
            var crc = index
            repeat(8) { crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1 }
            crc and 0xFF
        }
    private val CRC16_TABLE =
        IntArray(256) { index ->
            var crc = index shl 8
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x8005 else crc shl 1 }
            crc and 0xFFFF
        }

    private fun crc8(data: ByteArray, from: Int, to: Int): Int {
        var crc = 0
        for (i in from until to) crc = CRC8_TABLE[crc xor (data[i].toInt() and 0xFF)]
        return crc
    }

    private fun crc16(data: ByteArray, from: Int, to: Int): Int {
        var crc = 0
        for (i in from until to) {
            crc =
                ((crc shl 8) and 0xFFFF) xor
                    CRC16_TABLE[(crc ushr 8) xor (data[i].toInt() and 0xFF)]
        }
        return crc
    }
}
