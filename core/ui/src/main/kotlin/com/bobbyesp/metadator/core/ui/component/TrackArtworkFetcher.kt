/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.component

import android.content.ContentResolver
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.bobbyesp.metadator.core.model.TrackArtwork
import java.io.FileNotFoundException
import java.io.IOException
import okio.buffer
import okio.source

/**
 * Gives Coil a song's own cover ([TrackArtwork]): MediaStore's thumbnail of the picture in its
 * file, asked for at the size it is shown, which MediaStore reads out of the file once and keeps.
 * Before Android 10 there is no such thumbnail, and the album's art, one picture for the whole
 * album, is what MediaStore has.
 *
 * A song whose file holds no picture, and whose folder has none, has no cover: the lists show the
 * note in its place, as the editor shows none, rather than a sibling's as MediaStore's album art
 * would.
 */
class TrackArtworkFetcher(private val artwork: TrackArtwork, private val options: Options) :
    Fetcher {
    override suspend fun fetch(): FetchResult {
        val resolver = options.context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return ownPicture(resolver)
        val albumArt = artwork.albumArt ?: throw FileNotFoundException("No album art")
        val stream =
            resolver.openInputStream(albumArt.uri.toUri())
                ?: throw IOException("Unable to open ${albumArt.uri}")
        return SourceFetchResult(
            source = ImageSource(stream.source().buffer(), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ownPicture(resolver: ContentResolver): FetchResult {
        // What is asked for is the size on screen; a list asks for rows, the editor for a cover.
        val size = options.size
        val width = size.width.pxOrElse { DEFAULT_SIZE }
        val height = size.height.pxOrElse { DEFAULT_SIZE }
        val bitmap =
            resolver.loadThumbnail(artwork.ref.uri.toUri(), android.util.Size(width, height), null)
        // Made for this size: not to be handed to something asking for it larger.
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<TrackArtwork> {
        override fun create(
            data: TrackArtwork,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = TrackArtworkFetcher(data, options)
    }

    private companion object {
        /** Asked for with no size to go by: enough for any row, and for a card. */
        const val DEFAULT_SIZE = 512
    }
}

/**
 * The key a song's cover is cached under: the song and the version of its file, so that the cover
 * it had before a save is not what a list shows after it.
 */
class TrackArtworkKeyer : Keyer<TrackArtwork> {
    override fun key(data: TrackArtwork, options: Options): String =
        "track-artwork:${data.ref.uri}#${data.version}"
}

/**
 * The image loader the app shows its pictures with: Coil's, which knows a song's cover. The
 * application makes it the one `AsyncImage` finds (`SingletonImageLoader.Factory`).
 */
fun artworkImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(TrackArtworkKeyer())
            add(TrackArtworkFetcher.Factory())
        }
        .build()
