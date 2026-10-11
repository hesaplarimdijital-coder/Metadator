/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

/** How large a picture is, in pixels. */
data class ImageDimensions(val width: Int, val height: Int) {
    /** The side that limits how sharp a square cover can be shown. */
    val shortEdge: Int
        get() = minOf(width, height)
}

/**
 * Reads how large a picture is from the start of its file, without decoding it: a cover's size can
 * then be told from its first kilobytes, before deciding whether the rest is worth downloading.
 *
 * Null when [bytes] is not the start of a JPEG, PNG, WebP or GIF, or stops before saying.
 */
fun imageDimensions(bytes: ByteArray): ImageDimensions? =
    (png(bytes) ?: jpeg(bytes) ?: webp(bytes) ?: gif(bytes))?.takeIf {
        it.width > 0 && it.height > 0
    }

private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { unsigned(it) == prefix[it] }

private fun ByteArray.text(from: Int, length: Int): String? =
    if (size < from + length) null else String(this, from, length, Charsets.ISO_8859_1)

private fun png(bytes: ByteArray): ImageDimensions? {
    if (!bytes.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) || bytes.size < 24) return null
    // The first chunk is always the header: its length and name, then width and height.
    if (bytes.text(12, 4) != "IHDR") return null
    return ImageDimensions(bytes.int32(16), bytes.int32(20))
}

private fun ByteArray.int32(at: Int): Int =
    (unsigned(at) shl 24) or
        (unsigned(at + 1) shl 16) or
        (unsigned(at + 2) shl 8) or
        unsigned(at + 3)

private fun ByteArray.int16(at: Int): Int = (unsigned(at) shl 8) or unsigned(at + 1)

private fun ByteArray.int16LittleEndian(at: Int): Int = unsigned(at) or (unsigned(at + 1) shl 8)

private fun jpeg(bytes: ByteArray): ImageDimensions? {
    if (!bytes.startsWith(0xFF, 0xD8)) return null
    var position = 2
    while (position + 3 < bytes.size) {
        if (bytes.unsigned(position) != 0xFF) return null
        val marker = bytes.unsigned(position + 1)
        when {
            // Padding before a marker.
            marker == 0xFF -> position++
            // Markers that stand alone, with nothing after them.
            marker == 0x01 || marker in 0xD0..0xD8 -> position += 2
            // The end of the picture, or the start of its data: the size was not said.
            marker == 0xD9 || marker == 0xDA -> return null
            // A frame header, whichever way the picture is coded. The three left out share the
            // range but hold tables.
            marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
                if (position + 8 >= bytes.size) return null
                return ImageDimensions(
                    width = bytes.int16(position + 7),
                    height = bytes.int16(position + 5),
                )
            }
            else -> position += 2 + bytes.int16(position + 2)
        }
    }
    return null
}

private fun webp(bytes: ByteArray): ImageDimensions? {
    if (bytes.text(0, 4) != "RIFF" || bytes.text(8, 4) != "WEBP" || bytes.size < 30) return null
    return when (bytes.text(12, 4)) {
        // Extended: the canvas, each side less one, in three bytes.
        "VP8X" ->
            ImageDimensions(
                width = 1 + (bytes.int16LittleEndian(24) or (bytes.unsigned(26) shl 16)),
                height = 1 + (bytes.int16LittleEndian(27) or (bytes.unsigned(29) shl 16)),
            )
        // Lossy: after the frame tag and its start code, each side in fourteen bits.
        "VP8 " ->
            if (!(bytes.unsigned(23) == 0x9D && bytes.unsigned(24) == 0x01)) null
            else
                ImageDimensions(
                    width = bytes.int16LittleEndian(26) and 0x3FFF,
                    height = bytes.int16LittleEndian(28) and 0x3FFF,
                )
        // Lossless: after a signature byte, each side less one in fourteen bits, packed.
        "VP8L" -> {
            if (bytes.unsigned(20) != 0x2F) return null
            val packed =
                bytes.int16LittleEndian(21) or
                    (bytes.unsigned(23) shl 16) or
                    (bytes.unsigned(24) shl 24)
            ImageDimensions(
                width = 1 + (packed and 0x3FFF),
                height = 1 + ((packed ushr 14) and 0x3FFF),
            )
        }
        else -> null
    }
}

private fun gif(bytes: ByteArray): ImageDimensions? {
    val signature = bytes.text(0, 6) ?: return null
    if ((signature != "GIF87a" && signature != "GIF89a") || bytes.size < 10) return null
    return ImageDimensions(bytes.int16LittleEndian(6), bytes.int16LittleEndian(8))
}
