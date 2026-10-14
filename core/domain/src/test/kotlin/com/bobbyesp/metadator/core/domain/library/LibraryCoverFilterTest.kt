/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "No cover" and "Garbled text" filters, and what they make of albums and artists. */
class LibraryCoverFilterTest {
    private val covered = track(1, "Gülpembe", album = "Barış Manço Live", artist = "Barış Manço")
    private val bare = track(2, "Dönence", album = "Barış Manço Live", artist = "Barış Manço")
    private val other = track(3, "Kalk Gidelim", album = "Anadolu Pop", artist = "Cem Karaca")
    private val loose = track(4, "Bir Sana Bir Bana", album = "1968 - 2000", artist = "Moğollar")
    private val garbled = track(5, "Ã‡iÃ§ek", album = "1968 - 2000", artist = "MoÄŸollar")
    private val library = listOf(covered, bare, other, loose, garbled)

    private val withoutCover = setOf(bare.id, loose.id)
    private val lacksCover = { track: Track -> track.id in withoutCover }

    @Test
    fun `no cover lists the songs whose files hold none`() {
        val listed = library.filtered(LibraryFilter(noCover = true), lacksCover = lacksCover)

        assertEquals(listOf(bare, loose), listed)
    }

    @Test
    fun `an album or an artist without a cover is listed whole`() {
        val albums = library.albums().lackingCover(lacksCover)
        val artists = library.artists().lackingCover(lacksCover)

        assertEquals(listOf("1968 - 2000", "Barış Manço Live"), albums.map { it.title })
        // Both of the live album's songs, the one with a cover too.
        assertEquals(2, albums.single { it.title == "Barış Manço Live" }.tracks.size)
        assertEquals(listOf("Barış Manço", "Moğollar"), artists.map { it.title })
    }

    @Test
    fun `albums and artists without a cover are counted once each`() {
        assertEquals(2, library.countLackingCover(CollectionType.Album, lacksCover))
        assertEquals(2, library.countLackingCover(CollectionType.Artist, lacksCover))
        assertEquals(0, library.countLackingCover(CollectionType.Album) { false })
    }

    @Test
    fun `garbled text lists the songs whose text looks garbled`() {
        assertEquals(listOf(garbled), library.filtered(LibraryFilter(garbledText = true)))
        // What a caller already knows is taken as it is.
        assertEquals(
            listOf(covered),
            library.filtered(LibraryFilter(garbledText = true), isGarbled = { it == covered }),
        )
    }

    @Test
    fun `either filter makes the filter active, and the others still apply`() {
        assertTrue(LibraryFilter(noCover = true).isActive)
        assertTrue(LibraryFilter(garbledText = true).isActive)
        assertFalse(LibraryFilter().isActive)
        assertEquals(
            listOf(loose),
            library.filtered(
                LibraryFilter(noCover = true, search = "bana"),
                lacksCover = lacksCover,
            ),
        )
    }

    private fun track(id: Long, title: String, album: String, artist: String) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://media/external/audio/media/$id"),
            title = title,
            artist = artist,
            album = album,
            albumArtist = null,
            albumId = id,
            durationMs = 1_000,
            trackNumber = id.toInt(),
            discNumber = null,
            year = 2000,
            genre = null,
            mimeType = "audio/mpeg",
            sizeBytes = 1_000 + id,
            displayName = "$id.mp3",
            folder = "Music/",
            dateAddedEpochSeconds = 1_700_000_000,
            dateModifiedEpochSeconds = 1_700_000_000 + id,
            artworkRef = null,
        )
}
