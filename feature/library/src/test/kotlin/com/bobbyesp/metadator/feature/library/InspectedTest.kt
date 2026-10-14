/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.navigation.BatchEditor
import com.bobbyesp.metadator.core.navigation.Editor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectedTest {
    private val tracks = (1L..FollowedAtMost + 20L).map(::track)

    private fun state(selected: Int, clicked: Long? = null) =
        LibraryState(
            loading = false,
            listing = Listing(tracks = tracks),
            // Taken from the end of the list: the batch editor has them in the order listed.
            selection = tracks.takeLast(selected).reversed().mapTo(LinkedHashSet()) { it.id },
            clicked = clicked?.let(::TrackId),
        )

    @Test
    fun `more songs selected than it follows are not opened, and it says how many`() {
        val inspected = Inspected(state(selected = FollowedAtMost + 1))

        assertEquals(Inspected.Target(key = null, selected = true), inspected.target())
        assertEquals(FollowedAtMost + 1, inspected.unfollowed)
    }

    @Test
    fun `as many as it follows are the batch editor for them, in the order listed`() {
        val inspected = Inspected(state(selected = FollowedAtMost))

        assertEquals(
            Inspected.Target(
                BatchEditor(tracks.takeLast(FollowedAtMost).map { it.id.value }),
                true,
            ),
            inspected.target(),
        )
        assertEquals(0, inspected.unfollowed)
    }

    @Test
    fun `one song selected is its editor, and none the song clicked while it is listed`() {
        assertEquals(Editor(tracks.last().ref.uri), Inspected(state(selected = 1)).target().key)

        val clicked = Inspected(state(selected = 0, clicked = 7))
        assertFalse(clicked.selected)
        assertEquals(Inspected.Target(Editor(tracks[6].ref.uri), false), clicked.target())

        assertNull(Inspected(state(selected = 0, clicked = 999)).target().key)
    }

    @Test
    fun `albums selected are their songs, counted against what it follows`() {
        val first = album("a", tracks.take(60))
        val second = album("b", tracks.drop(60))
        fun selecting(vararg albums: TrackCollection.Album) =
            Inspected(
                LibraryState(
                    loading = false,
                    tab = LibraryTab.Albums,
                    listing = Listing(tracks = tracks, albums = listOf(first, second)),
                    collectionSelection = albums.mapTo(mutableSetOf()) { it.key },
                )
            )

        val one = selecting(first)
        assertTrue(one.selected)
        assertEquals(BatchEditor(first.tracks.map { it.id.value }), one.target().key)

        val both = selecting(first, second)
        assertEquals(tracks.size, both.unfollowed)
        assertNull(both.target().key)
    }

    private fun album(key: String, songs: List<Track>) =
        TrackCollection.Album(key = key, title = key, artist = null, year = null, tracks = songs)

    private fun track(id: Long) =
        Track(
            id = TrackId(id),
            ref = ContentRef("content://media/external/audio/media/$id"),
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            albumId = 1,
            durationMs = 180_000,
            trackNumber = id.toInt(),
            discNumber = 1,
            year = 2026,
            genre = "Rock",
            mimeType = "audio/flac",
            sizeBytes = 1_000_000,
            displayName = "$id.flac",
            folder = "Music/",
            dateAddedEpochSeconds = 0,
            dateModifiedEpochSeconds = 0,
            artworkRef = null,
        )
}
