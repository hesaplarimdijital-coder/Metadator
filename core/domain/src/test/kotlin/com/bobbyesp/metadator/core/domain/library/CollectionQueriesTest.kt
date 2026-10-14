/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionQueriesTest {
    private fun track(
        id: Long,
        title: String,
        artist: String? = "Artist",
        albumArtist: String? = artist,
        album: String? = "Album",
        disc: Int? = null,
        number: Int? = id.toInt(),
        folder: String = "Music/$album/",
    ) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://$id"),
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            albumId = 1,
            durationMs = id * 1000,
            trackNumber = number,
            discNumber = disc,
            year = 2000,
            genre = "Rock",
            mimeType = "audio/mpeg",
            sizeBytes = 1,
            displayName = "$title.mp3",
            folder = folder,
            dateAddedEpochSeconds = id,
            dateModifiedEpochSeconds = id,
            artworkRef = null,
        )

    private val library =
        listOf(
            track(1, "Because", artist = "The Beatles", album = "Abbey Road", disc = 1, number = 8),
            track(2, "Come Together", artist = "The Beatles", album = "Abbey Road", number = 1),
            track(3, "Something", artist = "The Beatles", album = "abbey road", number = 2),
            track(4, "Halo", artist = "Beyoncé", album = null, folder = "Music/Singles/"),
            track(5, "Greatest", artist = "Queen", album = "Greatest Hits", disc = 2, number = 1),
            track(6, "Hits", artist = "Queen", album = "Greatest Hits", disc = 1, number = 3),
            track(7, "Other", artist = "ABBA", album = "Greatest Hits"),
            track(8, "Guest", artist = "Freddie", albumArtist = "Queen", album = "Live"),
        )

    @Test
    fun `every album, artist and folder is found by its key as the library lists it`() {
        library.albums().forEach {
            assertEquals(it, library.collection(CollectionType.Album, it.key))
        }
        library.artists().forEach {
            assertEquals(it, library.collection(CollectionType.Artist, it.key))
        }
        library.folders().forEach {
            assertEquals(it, library.collection(CollectionType.Folder, it.key))
        }
    }

    @Test
    fun `an album lists its songs by disc and track`() {
        val queen = library.albums().first { it.artist == "Queen" && it.title == "Greatest Hits" }
        assertEquals(
            listOf(6L, 5L),
            library.collection(CollectionType.Album, queen.key)?.tracks?.map { it.id.value },
        )
    }

    @Test
    fun `an album whose key changed is found again by the songs it showed`() {
        val abbey = library.albums().first { it.title == "Abbey Road" }
        // Its album artist set, from the button that edits them all: its key is another now.
        val edited = library.map {
            if (it.album.equals("abbey road", true)) it.copy(albumArtist = "Beatles") else it
        }
        val found = edited.collection(CollectionType.Album, abbey.key, abbey.tracks.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), found?.tracks?.map { it.id.value })
        assertEquals(edited.albums().first { it.title == "Abbey Road" }.key, found?.key)
    }

    @Test
    fun `a collection none of whose songs is left is not found`() {
        val abbey = library.albums().first { it.title == "Abbey Road" }
        val left = library.filterNot { it.album.equals("abbey road", true) }
        assertNull(left.collection(CollectionType.Album, abbey.key, abbey.tracks.map { it.id }))
    }
}
