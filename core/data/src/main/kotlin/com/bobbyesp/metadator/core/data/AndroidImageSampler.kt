/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.domain.cover.ImageSample
import com.bobbyesp.metadator.core.domain.cover.ImageSampler
import kotlinx.coroutines.withContext

/** Samples pictures with the device's own decoders: whatever Android shows, it can sample. */
class AndroidImageSampler(private val dispatchers: AppDispatchers) : ImageSampler {

    override suspend fun sample(bytes: ByteArray): ImageSample? =
        withContext(dispatchers.default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val width = bounds.outWidth
                val height = bounds.outHeight
                if (width <= 0 || height <= 0) return@runCatching null

                // Decoded only as large as the sample needs: a 3000 px cover whole is 36 MB.
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = maxOf(1, minOf(width, height) / DECODED_EDGE)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                val bitmap =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@runCatching null
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    ImageSample.of(width, height, pixels, columns = bitmap.width)
                } finally {
                    bitmap.recycle()
                }
            }
                .getOrNull()
        }

    private companion object {
        /** Several pixels for each cell of the sample, so that what is averaged is the picture. */
        const val DECODED_EDGE = ImageSample.SIZE * 4
    }
}
