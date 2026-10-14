/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.lookup

import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.LookupQuery
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/**
 * What the songs of a batch are looked for by: their album and its artist, as the user may have
 * corrected them, and the songs themselves.
 */
data class AlbumQuery(val album: String, val artist: String, val songs: List<AlbumSong>)

/** How far a search for an album has got: the album asked for, then [searched] of [of] songs. */
data class AlbumSearchProgress(val searched: Int, val of: Int)

sealed interface AlbumSearchOutcome {
    /** The releases found, the one that suits the songs best first. May be empty. */
    data class Results(val proposals: List<AlbumProposal>, val failedProviders: List<String>) :
        AlbumSearchOutcome

    data object NoProviders : AlbumSearchOutcome

    data object Offline : AlbumSearchOutcome

    data object RateLimited : AlbumSearchOutcome

    data object Failed : AlbumSearchOutcome
}

/**
 * Finds the releases the songs of a batch may be from ([albumProposals]).
 *
 * The sources are searched song by song, as they are for one song: they know tracks, each with its
 * album, not albums. The album is asked for first under its own name, which finds the songs of an
 * album in a catalogue that searches every word in every field (the iTunes Store), and the title
 * track in the others. Then up to [MAX_SONGS] of the songs, spread over the batch: each finds its
 * own track, which is what tells where it is on the release, and the releases it is on.
 *
 * One search at a time, at least [spacing] apart: MusicBrainz takes one request a second, and the
 * others turn away a client that asks for a whole album at once. What leaves the device is what a
 * search for one song sends: titles, the artist and the album.
 */
class FindAlbumUseCase(
    private val lookup: LookupService,
    private val spacing: Duration = 1.seconds,
) {
    /** @param onProgress told how far the search has got, as it goes */
    suspend operator fun invoke(
        query: AlbumQuery,
        onProgress: (AlbumSearchProgress) -> Unit = {},
    ): AlbumSearchOutcome {
        val album = query.album.trim()
        val artist = query.artist.trim()
        val songs =
            query.songs
                .filter { it.title.isNotBlank() }
                .distinctBy { titleKey(it.title) }
                .spread(MAX_SONGS)
        val searches = buildList {
            if (album.isNotEmpty()) {
                add(LookupQuery(title = album, artist = artist.ifEmpty { null }, album = album))
            }
            songs.forEach { song ->
                add(
                    LookupQuery(
                        title = song.title.trim(),
                        artist = artist.ifEmpty { null },
                        album = album.ifEmpty { null },
                        durationMs = song.durationMs,
                    )
                )
            }
        }
        if (searches.isEmpty()) return AlbumSearchOutcome.Results(emptyList(), emptyList())

        val found = mutableListOf<LookupCandidate>()
        val failed = linkedSetOf<String>()
        var answered = false
        var started: TimeMark? = null
        onProgress(AlbumSearchProgress(0, songs.size))
        for ((index, search) in searches.withIndex()) {
            started?.let { mark ->
                val wait = spacing - mark.elapsedNow()
                if (wait.isPositive()) delay(wait)
            }
            started = TimeSource.Monotonic.markNow()
            when (val outcome = lookup.search(search)) {
                is LookupOutcome.Results -> {
                    answered = true
                    found += outcome.candidates
                    failed += outcome.failedProviders
                }
                LookupOutcome.NoProviders -> return AlbumSearchOutcome.NoProviders
                // Every source failed: the next search would too, or make the wait longer for
                // a source that asked for one. What was found by then is still worth showing.
                else -> if (answered) break else return outcome.asAlbumOutcome()
            }
            val searchedSongs = if (album.isNotEmpty()) index else index + 1
            onProgress(AlbumSearchProgress(searchedSongs, songs.size))
        }
        return AlbumSearchOutcome.Results(
            proposals =
                albumProposals(
                    album = album,
                    artist = artist,
                    songs = query.songs,
                    // A track found by two searches is one track.
                    candidates = found.distinctBy { it.providerId to it.id },
                ),
            failedProviders = failed.toList(),
        )
    }

    private fun LookupOutcome.asAlbumOutcome(): AlbumSearchOutcome =
        when (this) {
            is LookupOutcome.Results -> AlbumSearchOutcome.Results(emptyList(), failedProviders)
            LookupOutcome.NoProviders -> AlbumSearchOutcome.NoProviders
            LookupOutcome.Offline -> AlbumSearchOutcome.Offline
            LookupOutcome.RateLimited -> AlbumSearchOutcome.RateLimited
            LookupOutcome.Failed -> AlbumSearchOutcome.Failed
        }

    companion object {
        /**
         * The songs searched one by one. Each takes a second of MusicBrainz's, and a request of the
         * twenty a minute the iTunes Store allows: past this, a whole album would keep the sheet
         * waiting, and the next search turned away.
         */
        const val MAX_SONGS = 8
    }
}

/** [count] of these, spread evenly from the first to near the last; all when there are fewer. */
private fun <T> List<T>.spread(count: Int): List<T> =
    if (size <= count) this else List(count) { this[it * size / count] }
