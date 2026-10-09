/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.lookup.itunes

import com.bobbyesp.metadator.core.network.HttpClientFactory
import com.bobbyesp.metadator.lookup.api.LookupQuery
import com.bobbyesp.metadator.lookup.api.LookupResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ITunesProviderTest {
    /** As the store answers: JSON, said to be JavaScript. */
    private val javascript = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8")

    private val halo =
        """{"resultCount":2,"results":[
           {"wrapperType":"track","kind":"song","trackId":296016893,"artistName":"Beyoncé",
            "collectionName":"I Am... Sasha Fierce","trackName":"Halo",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/source/100x100bb.jpg",
            "releaseDate":"2008-11-14T08:00:00Z","discCount":1,"discNumber":1,"trackCount":16,
            "trackNumber":2,"trackTimeMillis":261640,"primaryGenreName":"Pop"},
           {"wrapperType":"track","kind":"music-video","trackId":1,"artistName":"Beyoncé",
            "trackName":"Halo"}]}"""

    @Test
    fun `a song is read from the store's answer`() = runTest {
        val engine = MockEngine { respond(halo, HttpStatusCode.OK, javascript) }
        val provider = ITunesProvider(HttpClientFactory.create("test", engine)) { "US" }

        val result = provider.search(LookupQuery("Halo", "Beyoncé")) as LookupResult.Success
        val song = result.candidates.single()

        assertEquals("Halo", song.title)
        assertEquals(listOf("Beyoncé"), song.artists)
        assertEquals("I Am... Sasha Fierce", song.album)
        assertEquals("2008-11-14", song.date)
        assertEquals(2, song.trackNumber)
        assertEquals(16, song.trackTotal)
        assertEquals(261_640L, song.durationMs)
        assertEquals(listOf("Pop"), song.genres)
    }

    @Test
    fun `the cover is asked for in the size wanted, whatever size the store links to`() = runTest {
        val engine = MockEngine { respond(halo, HttpStatusCode.OK, javascript) }
        val provider = ITunesProvider(HttpClientFactory.create("test", engine)) { "US" }

        val song =
            (provider.search(LookupQuery("Halo")) as LookupResult.Success).candidates.single()

        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/source/1500x1500bb.jpg",
            song.artworkUrl,
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/cd/source/250x250bb.jpg",
            song.artworkThumbnailUrl,
        )
        assertEquals("a/b/600x600bb.png", ITunesProvider.artworkUrl("a/b/60x60bb.png", 600))
        assertEquals(
            "a/100x100/600x600bb.jpg",
            ITunesProvider.artworkUrl("a/100x100/30x30.jpg", 600),
        )
    }

    @Test
    fun `the store of the device's region is searched, by artist and song`() = runTest {
        var asked: Map<String, String?> = emptyMap()
        val engine = MockEngine { request ->
            asked = request.url.parameters.names().associateWith { request.url.parameters[it] }
            respond("""{"results":[]}""", HttpStatusCode.OK, javascript)
        }
        val provider = ITunesProvider(HttpClientFactory.create("test", engine)) { "TR" }

        provider.search(LookupQuery("Şımarık", "Tarkan", "Ölürüm Sana"))

        assertEquals("Tarkan Şımarık", asked["term"])
        assertEquals("TR", asked["country"])
        assertEquals("song", asked["entity"])
    }

    @Test
    fun `a region without a store falls back on the default one`() = runTest {
        val countries = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val country = request.url.parameters["country"]
            countries += country
            if (country != null) respond("", HttpStatusCode.BadRequest)
            else respond(halo, HttpStatusCode.OK, javascript)
        }
        val provider = ITunesProvider(HttpClientFactory.create("test", engine)) { "XX" }

        val result = provider.search(LookupQuery("Halo")) as LookupResult.Success

        assertEquals(listOf("XX", null), countries)
        assertEquals(1, result.candidates.size)
    }

    @Test
    fun `no region at all searches the default store`() = runTest {
        var country: String? = "unset"
        val engine = MockEngine { request ->
            country = request.url.parameters["country"]
            respond(halo, HttpStatusCode.OK, javascript)
        }

        ITunesProvider(HttpClientFactory.create("test", engine)) { "" }.search(LookupQuery("Halo"))

        assertNull(country)
    }

    @Test
    fun `being turned away is rate limiting`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Forbidden) }

        assertEquals(
            LookupResult.RateLimited,
            ITunesProvider(HttpClientFactory.create("t", engine)) { "US" }.search(LookupQuery("x")),
        )
    }

    @Test
    fun `an answer that is not the store's is a failure, not a crash`() = runTest {
        val engine = MockEngine { respond("<html>Maintenance</html>", HttpStatusCode.OK) }

        val result =
            ITunesProvider(HttpClientFactory.create("t", engine)) { "US" }.search(LookupQuery("x"))

        assertEquals(true, result is LookupResult.Failed)
    }
}
