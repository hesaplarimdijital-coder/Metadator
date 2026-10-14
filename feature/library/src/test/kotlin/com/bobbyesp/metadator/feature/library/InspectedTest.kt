/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
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
        val inspected = Inspected.of(state(selected = FollowedAtMost + 1))

        assertNull(inspected.key)
        assertTrue(inspected.selected)
        assertEquals(FollowedAtMost + 1, inspected.unfollowed)
    }

    @Test
    fun `as many as it follows are the batch editor for them, in the order listed`() {
        val inspected = Inspected.of(state(selected = FollowedAtMost))

        assertEquals(
            BatchEditor(tracks.takeLast(FollowedAtMost).map { it.id.value }),
            inspected.key,
        )
        assertTrue(inspected.selected)
        assertEquals(0, inspected.unfollowed)
    }

    @Test
    fun `one song selected is its editor, and none the song clicked while it is listed`() {
        assertEquals(Editor(tracks.last().ref.uri), Inspected.of(state(selected = 1)).key)

        val clicked = Inspected.of(state(selected = 0, clicked = 7))
        assertEquals(Editor(tracks[6].ref.uri), clicked.key)
        assertFalse(clicked.selected)

        assertNull(Inspected.of(state(selected = 0, clicked = 999)).key)
    }

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
