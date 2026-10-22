/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.model

import kotlin.coroutines.cancellation.CancellationException

/** A group of songs the library is browsed by. */
sealed interface TrackCollection {
    val key: String
    val title: String
    val tracks: List<Track>

    /** The group's cover: the first of its songs' there is. Null for a group of no songs. */
    val artwork: CollectionArtwork?

    data class Album(
        override val key: String,
        override val title: String,
        val artist: String?,
        val year: Int?,
        override val tracks: List<Track>,
    ) : TrackCollection {
        override val artwork: CollectionArtwork? = CollectionArtwork.of(tracks)
    }

    data class Artist(
        override val key: String,
        override val title: String,
        override val tracks: List<Track>,
    ) : TrackCollection {
        override val artwork: CollectionArtwork? = CollectionArtwork.of(tracks)

        val albumCount: Int
            get() = tracks.mapNotNull { it.album }.distinct().size
    }

    data class Folder(
        override val key: String,
        override val title: String,
        /**
         * Where the folder is: the folder it is in, after the name of its volume when that is not
         * the device's own storage ("SD card/Music"). Empty at the top of the device's storage. Two
         * folders can have the same name, as every album's "CD1" does.
         */
        val parent: String,
        override val tracks: List<Track>,
    ) : TrackCollection {
        override val artwork: CollectionArtwork? = CollectionArtwork.of(tracks)
    }
}

/**
 * A group's cover, as a list shows it: the first of its songs' own covers there is ([songs], in the
 * group's order). The first song's alone stood for the group, and an album whose first song had no
 * picture (a bonus track added later, a song ripped again without it) showed the note where its
 * other songs have a cover.
 *
 * Made once with its group, so that a grid drawing it again is handed the same one.
 *
 * @property songs the songs tried for it, in order: the group's first few ([MOST_TRIED])
 */
data class CollectionArtwork(val songs: List<TrackArtwork>) {
    /**
     * The first cover [load] gives, trying [songs] in order. A song without one makes [load] throw,
     * and the next one is tried; when none has one, what the first of them threw is thrown. A
     * cancellation is not a song without a cover: it stops the search where it is.
     */
    inline fun <T> firstCover(load: (TrackArtwork) -> T): T {
        var none: Exception? = null
        for (song in songs) {
            try {
                return load(song)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (absent: Exception) {
                if (none == null) none = absent
            }
        }
        throw none ?: NoSuchElementException("No song to take a cover from")
    }

    companion object {
        /**
         * How many of a group's songs are tried for its cover. A group none of whose first songs
         * has one most likely has none at all, and trying each of an artist's hundreds of songs as
         * the grid is flung past it would cost more than it ever found.
         */
        const val MOST_TRIED = 6

        fun of(tracks: List<Track>): CollectionArtwork? =
            if (tracks.isEmpty()) null
            else CollectionArtwork(tracks.take(MOST_TRIED).map { it.artwork })
    }
}

enum class CollectionType {
    Album,
    Artist,
    Folder,
}
