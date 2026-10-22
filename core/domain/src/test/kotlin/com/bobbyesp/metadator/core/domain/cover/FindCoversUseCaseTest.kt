/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.domain.FakeSettings
import com.bobbyesp.metadator.core.domain.lookup.LookupService
import com.bobbyesp.metadator.core.model.UserSettings
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.LookupQuery
import com.bobbyesp.metadator.lookup.api.LookupResult
import com.bobbyesp.metadator.lookup.api.MatchConfidence
import com.bobbyesp.metadator.lookup.api.MetadataProvider
import com.bobbyesp.metadator.lookup.api.ProvidedArtwork
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindCoversUseCaseTest {
    private val downloader = FakeDownloader()
    private val sampler = FakeSampler()
    private val album = Artwork(1)
    private val single = Artwork(2)

    private val request =
        CoverRequest(
            title = "Halo",
            artist = "Beyoncé",
            album = "I Am... Sasha Fierce",
            year = 2008,
        )

    private class Provider(
        override val id: String,
        var result: LookupResult,
        private val direct: (Map<String, String>) -> List<ProvidedArtwork> = { emptyList() },
    ) : MetadataProvider {
        override val displayName = id.replaceFirstChar { it.uppercase() }
        override val providesArtwork = true
        val queries = mutableListOf<LookupQuery>()

        override suspend fun search(query: LookupQuery): LookupResult {
            queries += query
            return result
        }

        override fun artworkFor(identifiers: Map<String, String>) = direct(identifiers)
    }

    private fun candidate(
        provider: String,
        cover: String?,
        album: String = "I Am... Sasha Fierce",
        id: String = cover.orEmpty(),
    ) =
        LookupCandidate(
            providerId = provider,
            providerName = provider.replaceFirstChar { it.uppercase() },
            id = id,
            title = "Halo",
            artists = listOf("Beyoncé"),
            album = album,
            albumArtists = listOf("Beyoncé"),
            date = "2008-11-14",
            artworkUrl = cover?.let { "$it/full" },
            artworkThumbnailUrl = cover?.let { "$it/small" },
        )

    /** Puts a cover online: its picture [edge] pixels wide, and a small copy of what it shows. */
    private fun publish(cover: String, shows: Artwork, edge: Int) {
        downloader.put("$cover/full", edge)
        downloader.put("$cover/small", 250)
        sampler.put("$cover/small", shows.sample(250))
    }

    private suspend fun TestScope.find(
        vararg providers: Provider,
        request: CoverRequest = this@FindCoversUseCaseTest.request,
    ): CoverSearchOutcome {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings =
            FakeSettings(UserSettings(enabledProviders = providers.mapTo(mutableSetOf()) { it.id }))
        return FindCoversUseCase(
            LookupService(providers.toList(), settings),
            downloader,
            sampler,
            AppDispatchers(dispatcher, dispatcher),
        )(request)
    }

    private fun CoverSearchOutcome.suggestions() = (this as CoverSearchOutcome.Results).suggestions

    @Test
    fun `the cover of the album comes before the cover of the single`() = runTest {
        publish("d/album", album, 1000)
        publish("d/single", single, 1000)
        val deezer =
            Provider(
                "deezer",
                LookupResult.Success(
                    listOf(
                        candidate("deezer", "d/single", album = "Halo (Remixes)"),
                        candidate("deezer", "d/album"),
                    )
                ),
            )

        val suggestions = find(deezer).suggestions()

        assertEquals(listOf("d/album/full", "d/single/full"), suggestions.map { it.url })
        assertEquals(MatchConfidence.High, suggestions[0].confidence)
        assertTrue(CoverReason.AlbumDiffers in suggestions[1].reasons)
        assertEquals(ImageDimensions(1000, 1000), suggestions[0].dimensions)
    }

    @Test
    fun `one picture in two catalogues is one suggestion, its largest copy`() = runTest {
        publish("d/album", album, 1000)
        publish("i/album", album, 1500)
        val deezer =
            Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/album"))))
        val itunes =
            Provider("itunes", LookupResult.Success(listOf(candidate("itunes", "i/album"))))

        val suggestion = find(deezer, itunes).suggestions().single()

        assertEquals("i/album/full", suggestion.url)
        assertEquals(listOf("Itunes", "Deezer"), suggestion.sources)
        assertTrue(CoverReason.SeveralSources in suggestion.reasons)
    }

    @Test
    fun `a cover comes once however many songs of its album matched`() = runTest {
        publish("d/album", album, 1000)
        val deezer =
            Provider(
                "deezer",
                LookupResult.Success(
                    listOf(
                        candidate("deezer", "d/album", id = "1"),
                        candidate("deezer", "d/album", id = "2"),
                    )
                ),
            )

        assertEquals(1, find(deezer).suggestions().size)
        assertEquals(1, downloader.downloaded.count { it == "d/album/small" })
    }

    @Test
    fun `a cover the catalogue does not have is left out`() = runTest {
        publish("d/album", album, 1000)
        val musicbrainz =
            Provider(
                "musicbrainz",
                LookupResult.Success(
                    listOf(candidate("musicbrainz", "m/missing"), candidate("musicbrainz", null))
                ),
            )
        val deezer =
            Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/album"))))

        assertEquals(listOf("d/album/full"), find(musicbrainz, deezer).suggestions().map { it.url })
    }

    @Test
    fun `the picture shown in place of a missing cover is left out`() = runTest {
        downloader.put("d/none/full", 1000)
        downloader.put("d/none/small", 250)
        sampler.put("d/none/small", flatSample(250))
        val deezer = Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/none"))))

        assertTrue(find(deezer).suggestions().isEmpty())
    }

    @Test
    fun `a cover that could be measured but not looked at is still offered`() = runTest {
        downloader.put("d/album/full", 1200)
        val deezer =
            Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/album"))))

        val suggestion = find(deezer).suggestions().single()

        assertEquals(ImageDimensions(1200, 1200), suggestion.dimensions)
    }

    @Test
    fun `a cover the file names and the search finds again is offered once, unseen`() = runTest {
        // Measured, not looked at: nothing says the two are one picture but their address.
        downloader.put("d/album/full", 1200)
        val deezer =
            Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/album")))) {
                listOf(ProvidedArtwork("d/album/full", "d/album/small"))
            }

        assertEquals(listOf("d/album/full"), find(deezer).suggestions().map { it.url })
    }

    @Test
    fun `the release the file names comes first, even when the search finds nothing`() = runTest {
        publish("m/release/abc", album, 1200)
        val musicbrainz =
            Provider("musicbrainz", LookupResult.Offline) { ids ->
                listOfNotNull(ids["MUSICBRAINZ_ALBUMID"]).map {
                    ProvidedArtwork("m/release/$it/full", "m/release/$it/small")
                }
            }

        val tagged =
            CoverRequest("Halo", "Beyoncé", identifiers = mapOf("MUSICBRAINZ_ALBUMID" to "abc"))
        val suggestion = find(musicbrainz, request = tagged).suggestions().single()

        assertEquals("m/release/abc/full", suggestion.url)
        assertEquals(CoverReason.SameRelease, suggestion.reasons.first())
        assertTrue(suggestion.score >= 0.98)
    }

    @Test
    fun `a file's cover found again, larger, is said to be`() = runTest {
        publish("d/album", album, 1000)
        publish("d/single", single, 1000)
        val embedded = "embedded".toByteArray()
        sampler.put(embedded, single.sample(300))
        val deezer =
            Provider(
                "deezer",
                LookupResult.Success(
                    listOf(
                        candidate("deezer", "d/album"),
                        candidate("deezer", "d/single", album = "Halo - Single"),
                    )
                ),
            )

        val untagged = CoverRequest("Halo", "Beyoncé", currentCover = embedded)
        val suggestions = find(deezer, request = untagged).suggestions()

        assertEquals("d/single/full", suggestions.first().url)
        assertTrue(CoverReason.SameAsCurrent in suggestions.first().reasons)
        assertTrue(CoverReason.LargerThanCurrent in suggestions.first().reasons)
        assertFalse(CoverReason.SameAsCurrent in suggestions.last().reasons)
    }

    @Test
    fun `a song without a title is searched by its album`() = runTest {
        val deezer = Provider("deezer", LookupResult.Success(emptyList()))

        find(deezer, request = CoverRequest(title = " ", artist = "Beyoncé", album = "Lemonade"))

        assertEquals("Lemonade", deezer.queries.single().title)
    }

    @Test
    fun `only the best matches are downloaded`() = runTest {
        val candidates =
            (1..30).map { index ->
                publish("d/$index", Artwork(100L + index), 1000)
                candidate("deezer", "d/$index", album = "Album number $index")
            }
        val deezer = Provider("deezer", LookupResult.Success(candidates))

        val suggestions = find(deezer).suggestions()

        assertEquals(12, suggestions.size)
        assertEquals(12, downloader.downloaded.size)
    }

    @Test
    fun `no catalogue answering is said as it is`() = runTest {
        assertEquals(CoverSearchOutcome.Offline, find(Provider("deezer", LookupResult.Offline)))
        assertEquals(
            CoverSearchOutcome.RateLimited,
            find(Provider("deezer", LookupResult.RateLimited)),
        )
        assertEquals(CoverSearchOutcome.Failed, find(Provider("deezer", LookupResult.Failed("x"))))
        assertEquals(CoverSearchOutcome.NoProviders, find())
    }

    @Test
    fun `a catalogue that failed is named beside what the others found`() = runTest {
        publish("d/album", album, 1000)
        val deezer =
            Provider("deezer", LookupResult.Success(listOf(candidate("deezer", "d/album"))))
        val itunes = Provider("itunes", LookupResult.Failed("boom"))

        val outcome = find(deezer, itunes) as CoverSearchOutcome.Results

        assertEquals(1, outcome.suggestions.size)
        assertEquals(listOf("Itunes"), outcome.failedProviders)
    }
}
