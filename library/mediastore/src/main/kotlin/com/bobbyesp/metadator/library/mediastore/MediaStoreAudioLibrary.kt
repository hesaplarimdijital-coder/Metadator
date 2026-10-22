/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.library.mediastore

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.library.api.AudioLibrary
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The device's songs, from MediaStore: those on its own storage and on an SD card or a USB drive,
 * which MediaStore lists together.
 */
class MediaStoreAudioLibrary(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : AudioLibrary {

    private val resolver: ContentResolver
        get() = context.contentResolver

    private val volumes = StorageVolumes(context)

    @OptIn(FlowPreview::class)
    override fun observeTracks(): Flow<List<Track>> = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        resolver.registerContentObserver(AudioCollection, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        // A save or a scan changes many rows at once: one query for the burst is enough.
        .debounce(DEBOUNCE_MS.milliseconds)
        .conflate()
        .map { queryTracks(selection = IS_MUSIC, args = null) }
        .flowOn(dispatchers.io)

    override suspend fun track(id: TrackId): Track? =
        withContext(dispatchers.io) {
            queryTracks("${MediaStore.Audio.Media._ID} = ?", arrayOf(id.value.toString()))
                .firstOrNull()
        }

    override suspend fun findByRef(ref: ContentRef): Track? =
        withContext(dispatchers.io) {
            val id = mediaIdOf(context, ref.uri.toUri()) ?: return@withContext null
            track(TrackId(id))
        }

    /**
     * Has the media scanner look again at the device's music and download folders, and at the whole
     * of every SD card and USB drive: music on a card is often kept anywhere on it, not in a folder
     * named for it. The scanner only reads again what changed.
     */
    override suspend fun refresh() {
        val folders =
            (listOf(Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOWNLOADS).map {
                    Environment.getExternalStoragePublicDirectory(it).absolutePath
                } + volumes.removableRoots())
                .toTypedArray()
        withTimeoutOrNull(SCAN_TIMEOUT_MS.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                // Counted down on the scanner's thread, not on this one.
                val pending = AtomicInteger(folders.size)
                MediaScannerConnection.scanFile(context, folders, null) { _, _ ->
                    if (pending.decrementAndGet() == 0 && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    private fun queryTracks(selection: String, args: Array<String>?): List<Track> {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            } else {
                add(DATA_COLUMN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.DISC_NUMBER)
            }
        }
        // Before Android 10 a song's folder is worked out from its path and the volumes' roots.
        val roots =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) volumes.mountedRoots()
            else emptyList()
        return resolver
            .query(AudioCollection, projection.toTypedArray(), selection, args, null)
            ?.use { cursor ->
                val columns = Columns(cursor)
                buildList { while (cursor.moveToNext()) add(cursor.toTrack(columns, roots)) }
            }
            .orEmpty()
    }

    /**
     * Where each column is in a cursor of [queryTracks], found once for all its rows: finding one
     * by its name searches the names, and a library of ten thousand songs did that some 180,000
     * times on every change. The columns of Android 11 (the album artist, the genre, the disc) are
     * only asked for there, and the path only before Android 10: elsewhere a column is -1, and read
     * as nothing.
     */
    @SuppressLint("InlinedApi")
    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
        val duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val track = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val disc = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
        val year = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val genre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
        val mimeType = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val size = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val displayName = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val dateAdded = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val dateModified = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
        val relativePath = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
        val volumeName = cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
        val data = cursor.getColumnIndex(DATA_COLUMN)
    }

    private fun Cursor.toTrack(columns: Columns, roots: List<String>): Track {
        val id = long(columns.id)!!
        val albumId = long(columns.albumId) ?: 0
        // MediaStore packs the disc into the thousands: 2005 is disc 2, track 5.
        val packedTrack = int(columns.track)
        val discColumn = string(columns.disc)?.substringBefore('/')?.toIntOrNull()
        val displayName = string(columns.displayName).orEmpty()
        return Track(
            id = TrackId(id),
            ref = ContentRef(ContentUris.withAppendedId(AudioCollection, id).toString()),
            title = string(columns.title)?.ifBlank { null } ?: displayName.substringBeforeLast('.'),
            artist = string(columns.artist).known(),
            album = string(columns.album).known(),
            albumArtist = string(columns.albumArtist).known(),
            albumId = albumId,
            durationMs = long(columns.duration) ?: 0,
            trackNumber = packedTrack?.rem(1000)?.takeIf { it > 0 },
            discNumber = discColumn ?: packedTrack?.div(1000)?.takeIf { it > 0 },
            year = int(columns.year)?.takeIf { it > 0 },
            genre = string(columns.genre).known(),
            mimeType = string(columns.mimeType),
            sizeBytes = long(columns.size) ?: 0,
            displayName = displayName,
            folder =
                volumes.folderOf(
                    volumeName = string(columns.volumeName),
                    relativePath = string(columns.relativePath),
                ) ?: string(columns.data)?.let { volumes.folderOfPath(it, roots) },
            dateAddedEpochSeconds = long(columns.dateAdded) ?: 0,
            dateModifiedEpochSeconds = long(columns.dateModified) ?: 0,
            artworkRef =
                ContentRef(ContentUris.withAppendedId(AlbumArtCollection, albumId).toString()),
            volume =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    volumes.volumeOf(string(columns.volumeName))
                } else {
                    string(columns.data)?.let { volumes.volumeOfPath(it, roots) }
                },
        )
    }

    /** [column], where the cursor has it and holds a value there. */
    private fun Cursor.valueAt(column: Int): Int? = column.takeIf { it >= 0 && !isNull(it) }

    private fun Cursor.string(column: Int): String? = valueAt(column)?.let { getString(it) }

    private fun Cursor.long(column: Int): Long? = valueAt(column)?.let { getLong(it) }

    private fun Cursor.int(column: Int): Int? = valueAt(column)?.let { getInt(it) }

    /** MediaStore spells "no value" as `<unknown>`. */
    private fun String?.known(): String? =
        this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }

    companion object {
        val AudioCollection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val AlbumArtCollection: Uri = "content://media/external/audio/albumart".toUri()
        /** The file path column: deprecated, and only read where nothing replaces it (API < 29). */
        internal const val DATA_COLUMN = "_data"
        private const val IS_MUSIC = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        private const val DEBOUNCE_MS = 300L
        private const val SCAN_TIMEOUT_MS = 30_000L
    }
}

/**
 * The MediaStore id of [uri]: its own for a MediaStore URI, and the one of the same file for a
 * document another app handed over, when the system can tell (Android 10+).
 */
internal fun mediaIdOf(context: Context, uri: Uri): Long? {
    if (uri.authority == MediaStore.AUTHORITY) {
        return runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it >= 0 }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val mediaUri =
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() ?: return null
        return runCatching { ContentUris.parseId(mediaUri) }.getOrNull()
    }
    return null
}

internal fun isMediaStoreUri(uri: Uri): Boolean = uri.authority == MediaStore.AUTHORITY
