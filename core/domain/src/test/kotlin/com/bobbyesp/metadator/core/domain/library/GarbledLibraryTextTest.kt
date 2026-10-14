/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.domain.FakeLibrary
import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.domain.FakeTrackTagCache
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.library.api.CachedTrackTags
import com.bobbyesp.metadator.library.api.FileTrackTags
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import java.nio.charset.Charset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the library shows of a song whose text MediaStore decoded with the wrong guess. */
class GarbledLibraryTextTest {
    private val turkishInGbk =
        String("Moğollar".toByteArray(Charset.forName("windows-1254")), Charset.forName("GBK"))

    private fun track(
        title: String = "Bir Sana Bir Bana",
        artist: String = "Moğollar",
        album: String = "1968 - 2000",
        albumArtist: String = "Moğollar",
        modified: Long = 1,
    ) =
        Track(
            id = TrackId(1),
            ref = ContentRef("content://1"),
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            albumId = 1,
            durationMs = 1000,
            trackNumber = 1,
            discNumber = null,
            year = 2000,
            genre = "Anadolu Rock",
            mimeType = "audio/mpeg",
            sizeBytes = 10,
            displayName = "1.mp3",
            folder = "Music/",
            dateAddedEpochSeconds = 1,
            dateModifiedEpochSeconds = modified,
            artworkRef = null,
        )

    private fun file(
        title: String = "Bir Sana Bir Bana",
        artist: String = "Moğollar",
        album: String = "1968 - 2000",
        albumArtist: String = "Moğollar",
    ) =
        TagSnapshot(
            TagMap.of(
                "TITLE" to listOf(title),
                "ARTIST" to listOf(artist),
                "ALBUM" to listOf(album),
                "ALBUMARTIST" to listOf(albumArtist),
            ),
            emptyList(),
        )

    @Test
    fun `a garbled album artist is the file's own`() = runTest {
        val song = track(albumArtist = "MoÄŸollar")
        val files = FakeTagFiles(mapOf(song.ref to file()))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        val (before, after) = library.observeTracks().take(2).toList()

        assertEquals("MoÄŸollar", before.single().albumArtist)
        assertEquals("Moğollar", after.single().albumArtist)
    }

    @Test
    fun `every field the library lists a song by is taken from the file where garbled`() = runTest {
        val song =
            track(
                title = "BÃ¼yÃ¼k AÅŸk",
                artist = turkishInGbk,
                album = "Ã‡iÃ§ek",
                albumArtist = turkishInGbk,
            )
        val files = FakeTagFiles(mapOf(song.ref to file(title = "Büyük Aşk", album = "Çiçek")))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        val completed = library.observeTracks().take(2).toList().last().single()

        assertEquals("Büyük Aşk", completed.title)
        assertEquals("Moğollar", completed.artist)
        assertEquals("Çiçek", completed.album)
        assertEquals("Moğollar", completed.albumArtist)
    }

    @Test
    fun `a wrong guess gives way to the file's text even when that is garbled too`() = runTest {
        // A Windows-1254 tag: MediaStore guessed Chinese, TagLib reads it as Latin-1.
        val song = track(albumArtist = turkishInGbk)
        val files = FakeTagFiles(mapOf(song.ref to file(albumArtist = "Moðollar")))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        val completed = library.observeTracks().take(2).toList().last().single()

        // Garbled still, but repairable: the library's list of garbled songs takes it from here.
        assertEquals("Moðollar", completed.albumArtist)
    }

    @Test
    fun `the file's text is not taken where it looks no better`() = runTest {
        val song = track(album = "MoÄŸollar")
        // The file holds UTF-8 under a Latin-1 mark, which TagLib reads with controls.
        val files = FakeTagFiles(mapOf(song.ref to file(album = "MoÄ\u009Follar")))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        val completed = library.observeTracks().take(2).toList().last().single()

        assertEquals("MoÄŸollar", completed.album)
    }

    @Test
    fun `text that looks right is never read from the file`() = runTest {
        val song = track()
        val files = FakeTagFiles(mapOf(song.ref to file(albumArtist = "Someone else")))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        assertEquals("Moğollar", library.observeTracks().first().single().albumArtist)
        assertTrue(files.reads.isEmpty())
    }

    @Test
    fun `a garbled song is read once, and again only once its file changed`() = runTest {
        val song = track(albumArtist = "MoÄŸollar")
        val files = FakeTagFiles(mapOf(song.ref to file()))
        val source = FakeLibrary(listOf(song))
        val cache = FakeTrackTagCache()

        TagCompletedAudioLibrary(source, files, cache).observeTracks().take(2).toList()
        val restarted = TagCompletedAudioLibrary(source, files, cache)
        assertEquals("Moğollar", restarted.observeTracks().first().single().albumArtist)
        assertEquals(1, files.reads.size)

        source.tracks.value = listOf(song.copy(dateModifiedEpochSeconds = 2))
        restarted.observeTracks().take(2).toList()
        assertEquals(2, files.reads.size)
    }

    @Test
    fun `an entry read before titles were is read again for a garbled title`() = runTest {
        val song = track(title = "BÃ¼yÃ¼k AÅŸk")
        val old =
            CachedTrackTags(
                id = song.id,
                dateModifiedEpochSeconds = song.dateModifiedEpochSeconds,
                sizeBytes = song.sizeBytes,
                tags = FileTrackTags(artist = "Moğollar", album = "1968 - 2000"),
                readerVersion = 1,
            )
        val files = FakeTagFiles(mapOf(song.ref to file(title = "Büyük Aşk")))
        val library =
            TagCompletedAudioLibrary(
                FakeLibrary(listOf(song)),
                files,
                FakeTrackTagCache(listOf(old)),
            )

        val completed = library.observeTracks().take(2).toList().last().single()

        assertEquals("Büyük Aşk", completed.title)
        assertEquals(1, files.reads.size)
    }

    @Test
    fun `a song opened on its own shows the file's text too`() = runTest {
        val song = track(albumArtist = "MoÄŸollar")
        val files = FakeTagFiles(mapOf(song.ref to file()))
        val library =
            TagCompletedAudioLibrary(FakeLibrary(listOf(song)), files, FakeTrackTagCache())

        assertEquals("Moğollar", library.track(song.id)?.albumArtist)
    }
}
