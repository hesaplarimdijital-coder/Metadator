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
        val key = artwork.cacheKey
        if (Missing.contains(key)) throw FileNotFoundException("No cover in ${artwork.ref.uri}")
        return try {
            fetchFromStore()
        } catch (absent: FileNotFoundException) {
            Missing.add(key)
            throw absent
        }
    }

    private fun fetchFromStore(): FetchResult {
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
 * The songs found to have no cover, by the key of the version of their file that has none. A song
 * with none was asked for again each time its row came back in sight, and MediaStore looked in its
 * file again to say so: a library without covers read its files as it scrolled. A song saved with a
 * cover is another version, under another key, and is asked for again.
 */
private object Missing {
    private const val MOST = 4096
    private val keys =
        object : LinkedHashMap<String, Unit>(MOST, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) =
                size > MOST
        }

    fun contains(key: String): Boolean = synchronized(keys) { keys.containsKey(key) }

    fun add(key: String) {
        synchronized(keys) { keys[key] = Unit }
    }
}

/**
 * The key a song's cover is cached under: the song and the version of its file, so that the cover
 * it had before a save is not what a list shows after it.
 */
class TrackArtworkKeyer : Keyer<TrackArtwork> {
    override fun key(data: TrackArtwork, options: Options): String = data.cacheKey
}

/** The key a song's cover is cached under, for whatever shows the same cover another way. */
val TrackArtwork.cacheKey: String
    get() = "track-artwork:${ref.uri}#$version"

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
