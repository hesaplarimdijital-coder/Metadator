/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.lookup.itunes

import com.bobbyesp.metadator.core.network.HttpClientFactory
import com.bobbyesp.metadator.core.network.isOfflineError
import com.bobbyesp.metadator.core.network.networkCall
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.LookupQuery
import com.bobbyesp.metadator.lookup.api.LookupResult
import com.bobbyesp.metadator.lookup.api.MetadataProvider
import com.bobbyesp.metadator.lookup.api.rank
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.util.Locale

/**
 * The iTunes Store's public search: no key, the catalogue of what is sold and streamed, and covers
 * in any size up to the one the label delivered, which is often the largest to be had anywhere.
 *
 * The catalogue differs from country to country, so the search is made in the store of the device's
 * region, where a song released only there is found.
 *
 * @param country the two letters of the region whose store is searched
 */
class ITunesProvider(
    private val client: HttpClient,
    private val country: () -> String = { Locale.getDefault().country },
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "iTunes"
    override val providesArtwork: Boolean = true

    override suspend fun search(query: LookupQuery): LookupResult {
        if (query.isBlank) return LookupResult.Success(emptyList())
        return networkCall(
            onError = {
                if (isOfflineError(it)) LookupResult.Offline
                else LookupResult.Failed(it.message ?: it::class.simpleName.orEmpty())
            }
        ) {
            val store = country().takeIf { it.length == 2 }
            var response = request(query, store)
            // A region without a store of its own is refused: the default store will do.
            if (response.status == HttpStatusCode.BadRequest && store != null) {
                response = request(query, null)
            }
            when {
                // Past some twenty searches a minute the store turns the client away for a while.
                response.status == HttpStatusCode.Forbidden ||
                    response.status == HttpStatusCode.TooManyRequests -> LookupResult.RateLimited
                !response.status.isSuccess() -> LookupResult.Failed("HTTP ${response.status.value}")
                else -> {
                    // Decoded by hand: the store says its JSON is JavaScript, which the client
                    // will not take for JSON.
                    val search =
                        HttpClientFactory.json.decodeFromString(
                            ITunesSearch.serializer(),
                            response.bodyAsText(),
                        )
                    LookupResult.Success(
                        rank(query, search.results.mapNotNull { it.toCandidate() })
                    )
                }
            }
        }
    }

    private suspend fun request(query: LookupQuery, store: String?): HttpResponse =
        client.get(SEARCH_URL) {
            parameter("term", buildTerm(query))
            parameter("media", "music")
            parameter("entity", "song")
            parameter("limit", RESULT_LIMIT)
            if (store != null) parameter("country", store)
        }

    companion object {
        const val ID = "itunes"
        private const val SEARCH_URL = "https://itunes.apple.com/search"
        private const val RESULT_LIMIT = 15

        /**
         * Sharp on any screen a cover is shown on, and a few hundred kilobytes: a cover is stored
         * again in every song of its album.
         */
        private const val ARTWORK_EDGE = 1500
        private const val THUMBNAIL_EDGE = 250

        /** The size in the name of the picture the store links to, which any other can replace. */
        private val ARTWORK_SIZE = Regex("/\\d+x\\d+(bb|cc)?(?=\\.[A-Za-z]+$)")

        /** The store searches plain words: the artist, then the song. */
        internal fun buildTerm(query: LookupQuery): String =
            listOfNotNull(query.artist?.takeIf { it.isNotBlank() }, query.title)
                .joinToString(" ")
                .trim()

        /**
         * The same picture [edge] pixels wide, or as large as the store has it when that is
         * smaller: the store never enlarges one.
         */
        internal fun artworkUrl(url: String, edge: Int): String =
            url.replace(ARTWORK_SIZE, "/${edge}x${edge}bb")

        internal fun ITunesTrack.toCandidate(): LookupCandidate? {
            if (kind != "song" || trackId == null || trackName.isNullOrBlank()) return null
            return LookupCandidate(
                providerId = ID,
                providerName = "iTunes",
                id = trackId.toString(),
                title = trackName,
                artists = listOfNotNull(artistName),
                album = collectionName,
                albumArtists = listOfNotNull(collectionArtistName ?: artistName),
                // "2008-11-14T08:00:00Z": the day is all a tag keeps.
                date = releaseDate?.take(10),
                trackNumber = trackNumber,
                trackTotal = trackCount,
                discNumber = discNumber,
                discTotal = discCount,
                genres = listOfNotNull(primaryGenreName),
                durationMs = trackTimeMillis,
                artworkUrl = artworkUrl100?.let { artworkUrl(it, ARTWORK_EDGE) },
                artworkThumbnailUrl = artworkUrl100?.let { artworkUrl(it, THUMBNAIL_EDGE) },
            )
        }
    }
}
