/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.model

/** A song in the device's library, as MediaStore indexes it. */
@JvmInline value class TrackId(val value: Long)

/**
 * What the library knows about a song. The tags in the file are the truth; this is MediaStore's
 * copy, which can lag behind until the file is rescanned, with the fields MediaStore has no value
 * for read from the file.
 */
data class Track(
    val id: TrackId,
    val ref: ContentRef,
    val title: String,
    /** Null when MediaStore has no artist (it reports `<unknown>`). */
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val displayName: String,
    /** The folder, relative to the storage volume (`Music/Album/`), when MediaStore knows it. */
    val folder: String?,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    /**
     * MediaStore's album art: one picture for the album, from whichever of its songs it read first.
     * Only for display, and where a song's own picture cannot be had ([artwork]).
     */
    val artworkRef: ContentRef?,
    /**
     * What the volume the file is on is called, when it is not the device's own storage: an SD
     * card, a USB drive. Null on the device's own.
     */
    val volume: String? = null,
) {
    /** The fields neither MediaStore nor the file has a value for. */
    val missingFields: Set<TrackField>
        get() = buildSet {
            if (artist.isNullOrBlank()) add(TrackField.Artist)
            if (album.isNullOrBlank()) add(TrackField.Album)
            if (albumArtist.isNullOrBlank()) add(TrackField.AlbumArtist)
            if (year == null) add(TrackField.Year)
            if (trackNumber == null) add(TrackField.TrackNumber)
            if (genre.isNullOrBlank()) add(TrackField.Genre)
        }

    /*
     * Worked out once for each song, not at each letter typed in the library's search, which counts
     * and filters every song by them. A song never changes; a copy of it works them out again.
     */

    val needsAttention: Boolean = missingFields.any { it in TrackField.Essential }

    val fileExtension: String =
        displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    /** The song's own cover, as the lists show it. */
    val artwork: TrackArtwork
        get() = TrackArtwork(ref, artworkRef, dateModifiedEpochSeconds)
}

/**
 * A song's cover for a list: the picture in its own file, which MediaStore reads out of it and
 * keeps a thumbnail of, or where it cannot be had (Android 9 and before) the album's.
 *
 * [version] is when the file was last written. A cover saved to the file is a new picture under a
 * new key, where the one it replaced was cached under the old: without it the lists kept showing
 * the cover a song had before it was saved, for as long as the app ran.
 */
data class TrackArtwork(val ref: ContentRef, val albumArt: ContentRef?, val version: Long)

/** Library-level fields, for sorting, filtering and health checks. */
enum class TrackField {
    Title,
    Artist,
    Album,
    AlbumArtist,
    Year,
    TrackNumber,
    Genre;

    companion object {
        /** Missing any of these makes a song show up under "Needs attention". */
        val Essential = setOf(Artist, Album, AlbumArtist, Year, TrackNumber)
    }
}
