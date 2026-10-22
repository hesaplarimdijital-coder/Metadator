/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.model

import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CollectionArtworkTest {
    private fun track(id: Long) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://media/$id"),
            title = "Song $id",
            artist = "Metallica",
            album = "...And Justice for All",
            albumArtist = "Metallica",
            albumId = 1,
            durationMs = 1000,
            trackNumber = id.toInt(),
            discNumber = null,
            year = 1988,
            genre = null,
            mimeType = "audio/flac",
            sizeBytes = 1,
            displayName = "$id.flac",
            folder = "Music/Metallica/",
            dateAddedEpochSeconds = id,
            dateModifiedEpochSeconds = id,
            artworkRef = null,
        )

    private fun album(vararg ids: Long) =
        TrackCollection.Album(
            key = "album:8:metallica:...and justice for all",
            title = "...And Justice for All",
            artist = "Metallica",
            year = 1988,
            tracks = ids.map { track(it) },
        )

    /** Covers as a fetcher finds them: only the songs in [withCover] have one. */
    private fun coverOf(vararg withCover: Long): (TrackArtwork) -> String = { song ->
        val id = song.ref.uri.substringAfterLast('/').toLong()
        if (id in withCover) "cover of $id" else throw FileNotFoundException("No cover in $id")
    }

    @Test
    fun `an album whose first song has no cover shows the next one's`() {
        val artwork = album(1, 2, 3).artwork!!

        assertEquals("cover of 2", artwork.firstCover(coverOf(2, 3)))
    }

    @Test
    fun `the songs are tried in the album's order`() {
        val tried = mutableListOf<Long>()
        album(1, 2, 3, 4).artwork!!.firstCover { song ->
            tried += song.ref.uri.substringAfterLast('/').toLong()
            coverOf(3)(song)
        }

        assertEquals(listOf(1L, 2L, 3L), tried)
    }

    @Test
    fun `only the first few songs are tried`() {
        val artwork = album(1, 2, 3, 4, 5, 6, 7, 8).artwork!!

        assertEquals(
            (1L..CollectionArtwork.MOST_TRIED).map { track(it).artwork },
            artwork.songs,
        )
        assertThrows(FileNotFoundException::class.java) { artwork.firstCover(coverOf(7, 8)) }
    }

    @Test
    fun `a group none of whose songs has a cover has none, as its first song says`() {
        val thrown =
            assertThrows(FileNotFoundException::class.java) {
                album(1, 2).artwork!!.firstCover(coverOf())
            }

        assertEquals("No cover in 1", thrown.message)
    }

    @Test
    fun `a cancelled search stops where it is`() {
        val tried = mutableListOf<TrackArtwork>()
        val cancelled = CancellationException("Scrolled away")

        val thrown =
            assertThrows(CancellationException::class.java) {
                album(1, 2, 3).artwork!!.firstCover { song ->
                    tried += song
                    throw cancelled
                }
            }

        assertSame(cancelled, thrown)
        assertEquals(1, tried.size)
    }

    @Test
    fun `a group of no songs has no cover`() {
        assertNull(album().artwork)
    }

    @Test
    fun `a group's cover is made once, and is the same however often it is asked for`() {
        val album = album(1, 2)

        assertSame(album.artwork, album.artwork)
        assertEquals(album(1, 2).artwork, album.artwork)
    }
}
