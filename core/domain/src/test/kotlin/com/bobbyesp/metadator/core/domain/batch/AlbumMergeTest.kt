/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.tags.api.TagMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumMergeTest {
    private fun song(album: String?, albumArtist: String? = null, artist: String = "Daft Punk") =
        TagMap.of(
            buildMap {
                album?.let { put("ALBUM", listOf(it)) }
                albumArtist?.let { put("ALBUMARTIST", listOf(it)) }
                put("ARTIST", listOf(artist))
            }
        )

    @Test
    fun `songs of one album are not merged`() {
        assertNull(
            AlbumMerge.of(
                listOf(
                    song("Discovery", "Daft Punk"),
                    // The library takes the artist where there is no album artist: the same album.
                    song("discovery"),
                    song(null),
                )
            )
        )
    }

    @Test
    fun `albums are told apart by title and album artist, the largest first`() {
        val merge =
            AlbumMerge.of(
                listOf(
                    song("Discovery", "Daft Punk"),
                    song("Discovery (Remastered)", "Daft Punk"),
                    song("Discovery", "Daft Punk"),
                    song("Discovery", artist = "Daft Punk feat. Romanthony"),
                    song(null),
                )
            )!!

        assertEquals(
            listOf(
                BatchAlbum("Discovery", listOf("Daft Punk"), listOf(0, 2)),
                BatchAlbum("Discovery (Remastered)", listOf("Daft Punk"), listOf(1)),
                BatchAlbum("Discovery", listOf("Daft Punk feat. Romanthony"), listOf(3)),
            ),
            merge.albums,
        )
        assertEquals(1, merge.songsWithoutAlbum)
        assertEquals("Discovery", merge.title)
        assertEquals(listOf("Daft Punk"), merge.artists)
        assertEquals(listOf("Discovery", "Discovery (Remastered)"), merge.titleChoices)
        assertEquals(
            listOf(listOf("Daft Punk"), listOf("Daft Punk feat. Romanthony")),
            merge.artistChoices,
        )
        assertTrue(merge.discs.isEmpty())
    }

    @Test
    fun `the discs of one release are numbered, and the title loses its suffix`() {
        val merge =
            AlbumMerge.of(
                listOf(
                    song("Alive 2007 CD1", "Daft Punk"),
                    song("Alive 2007 CD1", "Daft Punk"),
                    song("Alive 2007 (Disc 2)", "Daft Punk"),
                    song("Alive 2007", "Daft Punk"),
                )
            )!!

        assertEquals("Alive 2007", merge.title)
        assertEquals(
            listOf("Alive 2007", "Alive 2007 CD1", "Alive 2007 (Disc 2)"),
            merge.titleChoices,
        )
        // The album without a suffix does not say which disc it is.
        assertEquals(mapOf(0 to 1, 1 to 1, 2 to 2), merge.discs)
    }

    @Test
    fun `titles that differ by more than a disc are not discs`() {
        val merge = AlbumMerge.of(listOf(song("Homework CD1"), song("Discovery CD2")))!!

        assertTrue(merge.discs.isEmpty())
        assertEquals("Homework CD1", merge.title)
    }

    @Test
    fun `disc suffixes are read in the ways albums are named`() {
        mapOf(
                "Album CD1" to DiscSuffix("Album", 1),
                "Album CD 2" to DiscSuffix("Album", 2),
                "Album - CD-3" to DiscSuffix("Album", 3),
                "Album Disc 2" to DiscSuffix("Album", 2),
                "Album (Disc 1)" to DiscSuffix("Album", 1),
                "Album [CD 2]" to DiscSuffix("Album", 2),
                "Album, disk 4" to DiscSuffix("Album", 4),
                "Album (CD 1/2)" to DiscSuffix("Album", 1),
                "Álbum (Disco 2)" to DiscSuffix("Álbum", 2),
            )
            .forEach { (title, expected) -> assertEquals(title, expected, DiscSuffix.of(title)) }
        listOf("CD1", "Discovery", "Disco 2000", "Volume 2", "AlbumCD1", "Album CD0").forEach {
            assertNull(it, DiscSuffix.of(it))
        }
    }
}
