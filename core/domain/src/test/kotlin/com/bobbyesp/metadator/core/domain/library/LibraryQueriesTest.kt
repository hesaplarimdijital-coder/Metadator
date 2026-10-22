/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.SortOrder
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.model.TrackSort
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueriesTest {
    private fun track(
        id: Long,
        title: String,
        artist: String? = "Artist",
        album: String? = "Album",
        year: Int? = 2000,
        track: Int? = id.toInt(),
        name: String = "$title.mp3",
        folder: String = "Music/$album/",
    ) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://$id"),
            title = title,
            artist = artist,
            album = album,
            albumArtist = artist,
            albumId = 1,
            durationMs = id * 1000,
            trackNumber = track,
            discNumber = null,
            year = year,
            genre = "Rock",
            mimeType = "audio/mpeg",
            sizeBytes = 1,
            displayName = name,
            folder = folder,
            dateAddedEpochSeconds = id,
            dateModifiedEpochSeconds = id,
            artworkRef = null,
        )

    private val tracks =
        listOf(
            track(1, "Come Together", artist = "The Beatles", album = "Abbey Road"),
            track(2, "Halo", artist = "Beyoncé", album = null, year = null),
            track(3, "Ünder Pressure", artist = "Queen", name = "under.flac"),
        )

    @Test
    fun `every search term has to match some field`() {
        assertEquals(
            listOf(1L),
            tracks.filtered(LibraryFilter(search = "beatles abbey")).map { it.id.value },
        )
        assertEquals(
            listOf(2L),
            tracks.filtered(LibraryFilter(search = "beyonce")).map { it.id.value },
        )
        assertEquals(
            emptyList<Long>(),
            tracks.filtered(LibraryFilter(search = "beatles queen")).map { it.id.value },
        )
    }

    @Test
    fun `needs attention finds songs missing essentials`() {
        assertEquals(
            listOf(2L),
            tracks.filtered(LibraryFilter(needsAttention = true)).map { it.id.value },
        )
    }

    @Test
    fun `format filter uses the extension`() {
        assertEquals(
            listOf(3L),
            tracks.filtered(LibraryFilter(formats = setOf("flac"))).map { it.id.value },
        )
    }

    @Test
    fun `sorting ignores accents and puts missing values last`() {
        val byTitle = tracks.sorted(SortOrder(TrackSort.Title), Locale.ENGLISH).map { it.title }
        assertEquals(listOf("Come Together", "Halo", "Ünder Pressure"), byTitle)
        val byAlbum = tracks.sorted(SortOrder(TrackSort.Album, ascending = false), Locale.ENGLISH)
        assertEquals(2L, byAlbum.last().id.value)
    }

    @Test
    fun `albums group by album artist and title`() {
        val albums = tracks.albums()
        assertEquals(listOf("Abbey Road", "Album"), albums.map { it.title })
    }

    @Test
    fun `an album of one title by two artists is two albums, with keys of their own`() {
        // MediaStore gives the songs of one title in a folder one album id, whoever they are by.
        val albums =
            listOf(
                    track(1, "One", artist = "First", album = "Shared").copy(albumArtist = null),
                    track(2, "Two", artist = "Second", album = "Shared").copy(albumArtist = null),
                    track(3, "Three", artist = "first", album = "shared").copy(albumArtist = null),
                )
                .albums()
        assertEquals(listOf(2, 1), albums.map { it.tracks.size })
        assertEquals(albums.size, albums.map { it.key }.distinct().size)
    }

    @Test
    fun `a collection given another key by an edit is found again by its songs`() {
        val shown =
            listOf(track(1, "One", artist = "Old"), track(2, "Two", artist = "Old")).albums()
        val edited =
            listOf(
                    track(1, "One", artist = "New"),
                    track(2, "Two", artist = "New"),
                    track(3, "Three", artist = "Old", album = "Other"),
                )
                .albums()
        val album = shown.single()
        val songs = album.tracks.map { it.id }

        assertEquals(null, edited.firstOrNull { it.key == album.key })
        assertEquals(listOf(1L, 2L), edited.locate(album.key, songs)?.tracks?.map { it.id.value })
        // By its key while it has one, whatever else holds the songs.
        assertEquals("Other", edited.locate(edited[1].key, songs)?.title)
        assertEquals(null, edited.locate(album.key, listOf(TrackId(9))))
        assertEquals(null, edited.locate(album.key, emptyList()))
    }

    @Test
    fun `folders of the same name say where each one is`() {
        val folders =
            listOf(
                    track(1, "One", folder = "Music/Discovery/"),
                    track(2, "Two", folder = "SD card/Music/Discovery/"),
                    track(3, "Three", folder = "SD card/Music/Discovery/"),
                    track(4, "Four", folder = "SD card/"),
                    track(5, "Five", folder = "/"),
                )
                .folders()
        assertEquals(
            listOf(
                Triple("/", "", 1),
                Triple("Discovery", "Music", 1),
                Triple("Discovery", "SD card/Music", 2),
                Triple("SD card", "", 1),
            ),
            folders
                .map { Triple(it.title, it.parent, it.tracks.size) }
                .sortedWith(compareBy({ it.first }, { it.second })),
        )
    }
}
