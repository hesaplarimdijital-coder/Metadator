/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.tags.api.TagMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CoverRequestTest {
    private val release = "8c4a1b6e-0d0a-4f0e-9d5b-2f3a4b5c6d7e"

    private fun song(
        title: String?,
        track: String,
        album: String? = "Discovery",
        vararg more: Pair<String, List<String>>,
    ) =
        TagMap.of(
            mapOf(
                "TITLE" to listOfNotNull(title),
                "ARTIST" to listOf("Daft Punk"),
                "ALBUM" to listOfNotNull(album),
                "ALBUMARTIST" to listOf("Daft Punk"),
                "DATE" to listOf("2001-03-12"),
                "MUSICBRAINZ_ALBUMID" to listOf(release),
                "MUSICBRAINZ_TRACKID" to listOf(track),
            ) + more
        )

    @Test
    fun `one song is asked for by its own tags`() {
        val cover = byteArrayOf(1, 2, 3)
        val request =
            CoverRequest.forSongs(
                songs =
                    listOf(
                        song(
                            "One More Time",
                            "track-1",
                            "Discovery",
                            "ISRC" to listOf("GBDUW0000053"),
                        )
                    ),
                durationMs = 320_000,
                currentCover = cover,
            )

        assertEquals("One More Time", request.title)
        assertEquals("Daft Punk", request.artist)
        assertEquals("Discovery", request.album)
        assertEquals("Daft Punk", request.albumArtist)
        assertEquals(2001, request.year)
        assertEquals(320_000L, request.durationMs)
        assertEquals("GBDUW0000053", request.isrc)
        assertEquals(
            mapOf("MUSICBRAINZ_ALBUMID" to release, "MUSICBRAINZ_TRACKID" to "track-1"),
            request.identifiers,
        )
        assertSame(cover, request.currentCover)
    }

    @Test
    fun `the first of several songs speaks for all of them`() {
        val request =
            CoverRequest.forSongs(
                listOf(song("One More Time", "track-1"), song("Aerodynamic", "track-2"))
            )

        assertEquals("One More Time", request.title)
        assertEquals("Discovery", request.album)
    }

    @Test
    fun `an id counts only when every song has the same`() {
        val request =
            CoverRequest.forSongs(
                listOf(song("One More Time", "track-1"), song("Aerodynamic", "track-2"))
            )

        // The release is theirs; the recording is only the first song's.
        assertEquals(mapOf("MUSICBRAINZ_ALBUMID" to release), request.identifiers)
    }

    @Test
    fun `an id one song lacks is not the id of them all`() {
        val untagged = TagMap.of("TITLE" to listOf("Aerodynamic"), "ALBUM" to listOf("Discovery"))
        val request = CoverRequest.forSongs(listOf(song("One More Time", "track-1"), untagged))

        assertEquals(emptyMap<String, String>(), request.identifiers)
    }

    @Test
    fun `songs of different releases share no release`() {
        val other =
            TagMap.of(
                "TITLE" to listOf("Around the World"),
                "ALBUM" to listOf("Homework"),
                "MUSICBRAINZ_ALBUMID" to listOf("11111111-2222-3333-4444-555555555555"),
            )
        val request = CoverRequest.forSongs(listOf(song("One More Time", "track-1"), other))

        assertEquals(emptyMap<String, String>(), request.identifiers)
    }

    @Test
    fun `a song with no title goes by the name it is given`() {
        val request =
            CoverRequest.forSongs(listOf(song(null, "track-1")), titleFallback = "01.flac")

        assertEquals("01.flac", request.title)
    }

    @Test
    fun `a blank title is no title`() {
        val request =
            CoverRequest.forSongs(listOf(song("  ", "track-1")), titleFallback = "01.flac")

        assertEquals("01.flac", request.title)
    }

    @Test
    fun `what is not a year is no year`() {
        val request =
            CoverRequest.forSongs(
                listOf(TagMap.of("TITLE" to listOf("Untitled"), "DATE" to listOf("unknown")))
            )

        assertNull(request.year)
        assertNull(request.album)
        assertNull(request.artist)
    }

    @Test
    fun `no songs ask for nothing`() {
        val request = CoverRequest.forSongs(emptyList())

        assertEquals("", request.title)
        assertEquals(emptyMap<String, String>(), request.identifiers)
    }
}
