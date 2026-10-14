/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.lookup

import com.bobbyesp.metadator.core.domain.FakeSettings
import com.bobbyesp.metadator.core.model.UserSettings
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.LookupQuery
import com.bobbyesp.metadator.lookup.api.LookupResult
import com.bobbyesp.metadator.lookup.api.MetadataProvider
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumLookupTest {

    private fun track(
        source: String,
        title: String,
        number: Int?,
        album: String = "Discovery",
        artist: String = "Daft Punk",
        total: Int? = null,
        disc: Int? = 1,
        discTotal: Int? = null,
        lengthMs: Long? = null,
        date: String? = null,
        genres: List<String> = emptyList(),
        cover: String? = null,
    ) =
        LookupCandidate(
            providerId = source,
            providerName = source.replaceFirstChar { it.uppercaseChar() },
            id = "$source/$album/$title",
            title = title,
            artists = listOf(artist),
            album = album,
            albumArtists = listOf(artist),
            date = date,
            trackNumber = number,
            trackTotal = total,
            discNumber = disc,
            discTotal = discTotal,
            genres = genres,
            durationMs = lengthMs,
            artworkUrl = cover,
            artworkThumbnailUrl = cover?.let { "$it/small" },
        )

    private val songs =
        listOf(
            AlbumSong("One More Time", durationMs = 320_000),
            AlbumSong("Aerodynamic", durationMs = 212_000),
            AlbumSong("Digital Love", durationMs = 301_000),
            AlbumSong("Harder, Better, Faster, Stronger", trackNumber = 4),
        )

    private val discovery =
        listOf(
            track(
                "musicbrainz",
                "One More Time",
                1,
                total = 14,
                lengthMs = 320_400,
                date = "2001-03-07",
                genres = listOf("House", "French House"),
            ),
            track(
                "itunes",
                "One More Time",
                1,
                total = 14,
                discTotal = 1,
                lengthMs = 320_000,
                date = "2001-03-12",
                genres = listOf("Electronic"),
                cover = "itunes/cover",
            ),
            track(
                "deezer",
                "One More Time",
                1,
                lengthMs = 320_000,
                date = "2001-03-12",
                cover = "deezer/cover",
            ),
            track(
                "itunes",
                "Aerodynamic",
                2,
                total = 14,
                discTotal = 1,
                lengthMs = 212_000,
                date = "2001-03-12",
                genres = listOf("Electronic"),
            ),
            // A featured artist credited on the track still makes it the same album.
            track(
                "itunes",
                "Digital Love",
                3,
                artist = "Daft Punk, Romanthony & Todd Edwards",
                total = 14,
                discTotal = 1,
                lengthMs = 301_000,
                date = "2001-03-12",
                genres = listOf("Electronic"),
            ),
            track("deezer", "Harder, Better, Faster, Stronger", 4),
            track(
                "itunes",
                "Harder, Better, Faster, Stronger",
                4,
                total = 14,
                discTotal = 1,
                date = "2001-03-12",
            ),
        )

    private val compilation =
        listOf(
            track(
                "musicbrainz",
                "One More Time",
                7,
                album = "Now Dance 2001",
                artist = "Various Artists",
                total = 20,
                lengthMs = 320_400,
                date = "2001",
            ),
            track(
                "deezer",
                "One More Time",
                7,
                album = "Now Dance 2001",
                artist = "Various Artists",
                lengthMs = 320_000,
                date = "2001",
            ),
        )

    @Test
    fun `the tracks of one album make one proposal, with what its sources agree on`() {
        val proposals = albumProposals("Discovery", "Daft Punk", songs, compilation + discovery)

        val first = proposals.first()
        assertEquals("Discovery", first.album)
        assertEquals(listOf("Daft Punk"), first.albumArtists)
        // Two sources say the 12th, one the 7th: the year is agreed first, then the day.
        assertEquals("2001-03-12", first.date)
        assertEquals(listOf("Musicbrainz", "Itunes", "Deezer"), first.sources)
        assertEquals(14, first.trackCount)
        assertEquals(4, first.songsFound)
        assertEquals("itunes/cover/small", first.artworkThumbnailUrl)
        assertEquals("Now Dance 2001", proposals[1].album)
        assertTrue(first.score > proposals[1].score)
    }

    @Test
    fun `a tie between sources goes to the better match`() {
        val proposal = albumProposals("Discovery", "Daft Punk", songs, discovery).single()

        // MusicBrainz's genres and the iTunes Store's are one source each: the first one wins.
        assertEquals(listOf("House", "French House"), proposal.genres)
    }

    @Test
    fun `the release with the songs on it ranks above one with the same name`() {
        val single =
            listOf(
                track("deezer", "One More Time", 1, album = "One More Time", total = 2),
                track("musicbrainz", "One More Time", 1, album = "One More Time", total = 2),
            )
        val proposals = albumProposals("", "Daft Punk", songs, single + discovery)

        assertEquals("Discovery", proposals.first().album)
    }

    @Test
    fun `a song is found by its title and its length, or where it says it is`() {
        val proposal = albumProposals("Discovery", "Daft Punk", songs, discovery).single()

        val positions = proposal.positionsFor(songs)

        assertEquals(ReleasePosition(1, 14, 1, 1), positions[0])
        assertEquals(ReleasePosition(2, 14, 1, 1), positions[1])
        assertEquals(ReleasePosition(3, 14, 1, 1), positions[2])
        // No length to compare: its file says track 4, and so do the sources.
        assertEquals(ReleasePosition(4, 14, 1, 1), positions[3])
    }

    @Test
    fun `a song of another length is another recording, and keeps its number`() {
        val proposal = albumProposals("Discovery", "Daft Punk", songs, discovery).single()

        val live = AlbumSong("One More Time", durationMs = 410_000, trackNumber = 1)
        val unknown = AlbumSong("Too Long")

        // Only the total is known, the release having one disc.
        assertEquals(
            listOf(ReleasePosition(trackTotal = 14), ReleasePosition(trackTotal = 14)),
            proposal.positionsFor(listOf(live, unknown)),
        )
    }

    @Test
    fun `nothing is guessed where the sources disagree`() {
        val editions =
            listOf(
                track("musicbrainz", "Digital Love", 3, total = 14, lengthMs = 301_000),
                track("deezer", "Digital Love", 4, lengthMs = 301_000),
            )
        val proposal = albumProposals("Discovery", "Daft Punk", songs, editions).single()

        // Nor is a total where the release may have more discs than one.
        assertEquals(
            listOf(null),
            proposal.positionsFor(listOf(AlbumSong("Digital Love", durationMs = 301_000))),
        )
    }

    @Test
    fun `a song with no length or number is not found, however its title matches`() {
        val proposal = albumProposals("Discovery", "Daft Punk", songs, discovery).single()

        assertEquals(
            ReleasePosition(trackTotal = 14),
            proposal.positionsFor(listOf(AlbumSong("Aerodynamic"))).single(),
        )
    }

    @Test
    fun `titles compare without featured artists and remaster notes`() {
        assertEquals(titleKey("Get Lucky"), titleKey("Get Lucky (feat. Pharrell Williams)"))
        assertEquals(titleKey("Get Lucky"), titleKey("Get Lucky feat. Pharrell Williams"))
        assertEquals(titleKey("Paranoid Android"), titleKey("Paranoid Android - 2009 Remaster"))
        assertEquals(titleKey("Karma Police"), titleKey("Karma Police [Remastered]"))
        assertTrue(titleKey("One More Time") != titleKey("One More Time (Live)"))
    }

    @Test
    fun `a track on another disc of a release is told from the same title on the first`() {
        val tracks =
            listOf(
                track("itunes", "Intro", 1, disc = 1, total = 10, discTotal = 2, lengthMs = 60_000),
                track("itunes", "Intro", 1, disc = 2, total = 9, discTotal = 2, lengthMs = 95_000),
            )
        val proposal =
            albumProposals(
                    "Alive",
                    "Daft Punk",
                    emptyList(),
                    tracks.map { it.copy(album = "Alive") },
                )
                .single()

        assertEquals(19, proposal.trackCount)
        assertEquals(
            listOf(ReleasePosition(1, 9, 2, 2)),
            proposal.positionsFor(listOf(AlbumSong("Intro", durationMs = 95_500))),
        )
    }

    private class Source(
        override val id: String,
        val answer: (LookupQuery) -> LookupResult,
    ) : MetadataProvider {
        val asked = mutableListOf<LookupQuery>()
        override val displayName = id
        override val providesArtwork = true

        override suspend fun search(query: LookupQuery): LookupResult {
            asked += query
            return answer(query)
        }
    }

    private fun service(vararg sources: Source) =
        LookupService(
            sources.toList(),
            FakeSettings(UserSettings(enabledProviders = sources.mapTo(mutableSetOf()) { it.id })),
        )

    @Test
    fun `the album is asked for first, then songs spread over the batch`() = runTest {
        val itunes =
            Source("itunes") { query ->
                LookupResult.Success(
                    discovery.filter {
                        it.providerId == "itunes" && query.title in listOf("Discovery", it.title)
                    }
                )
            }
        val many = (1..20).map { AlbumSong("Song $it") }
        val progress = mutableListOf<AlbumSearchProgress>()

        FindAlbumUseCase(service(itunes), Duration.ZERO)(
            AlbumQuery("Discovery", "Daft Punk", many)
        ) {
            progress += it
        }

        val asked = itunes.asked.map { it.title }
        assertEquals("Discovery", asked.first())
        assertEquals(1 + FindAlbumUseCase.MAX_SONGS, asked.size)
        assertEquals(listOf("Song 1", "Song 3", "Song 6"), asked.drop(1).take(3))
        assertTrue(itunes.asked.all { it.album == "Discovery" && it.artist == "Daft Punk" })
        assertEquals(AlbumSearchProgress(0, 8), progress.first())
        assertEquals(AlbumSearchProgress(8, 8), progress.last())
    }

    @Test
    fun `what every search finds makes the proposals, each track once`() = runTest {
        val itunes =
            Source("itunes") { query ->
                LookupResult.Success(
                    discovery.filter {
                        it.providerId == "itunes" &&
                            (query.title == "Discovery" || it.title == query.title)
                    }
                )
            }

        val outcome =
            FindAlbumUseCase(service(itunes), Duration.ZERO)(
                AlbumQuery("Discovery", "Daft Punk", songs)
            )
                as AlbumSearchOutcome.Results

        val proposal = outcome.proposals.single()
        assertEquals(4, proposal.tracks.size)
        assertEquals(4, proposal.songsFound)
    }

    @Test
    fun `offline from the start is offline, and nothing more is asked`() = runTest {
        val offline = Source("deezer") { LookupResult.Offline }

        val outcome =
            FindAlbumUseCase(service(offline), Duration.ZERO)(
                AlbumQuery("Discovery", "Daft Punk", songs)
            )

        assertEquals(AlbumSearchOutcome.Offline, outcome)
        assertEquals(1, offline.asked.size)
    }

    @Test
    fun `sources turned off are said`() = runTest {
        val outcome =
            FindAlbumUseCase(LookupService(emptyList(), FakeSettings()), Duration.ZERO)(
                AlbumQuery("Discovery", "Daft Punk", songs)
            )

        assertEquals(AlbumSearchOutcome.NoProviders, outcome)
    }

    @Test
    fun `a source that stops answering keeps what was found before`() = runTest {
        var calls = 0
        val busy =
            Source("itunes") { query ->
                calls++
                if (calls > 2) LookupResult.RateLimited
                else
                    LookupResult.Success(
                        discovery.filter { it.providerId == "itunes" && it.title == query.title }
                    )
            }

        val outcome =
            FindAlbumUseCase(service(busy), Duration.ZERO)(AlbumQuery("", "Daft Punk", songs))
                as AlbumSearchOutcome.Results

        assertEquals(3, busy.asked.size)
        assertEquals(2, outcome.proposals.single().songsFound)
    }

    @Test
    fun `a batch with nothing to search by finds nothing`() = runTest {
        val source = Source("itunes") { LookupResult.Success(emptyList()) }

        val outcome =
            FindAlbumUseCase(service(source), Duration.ZERO)(
                AlbumQuery(" ", "", listOf(AlbumSong("")))
            )

        assertEquals(AlbumSearchOutcome.Results(emptyList(), emptyList()), outcome)
        assertTrue(source.asked.isEmpty())
        assertNull((outcome as AlbumSearchOutcome.Results).proposals.firstOrNull())
    }
}
