/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.lookup.api.MatchConfidence
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap

/**
 * What is known of the song a cover is wanted for: its tags, and the cover it has now.
 *
 * @param identifiers the ids of online catalogues the file carries, by tag key
 * @param currentCover the picture in the file, which says what the right cover looks like even when
 *   it is too small to keep
 */
class CoverRequest(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val identifiers: Map<String, String> = emptyMap(),
    val currentCover: ByteArray? = null,
) {
    companion object {
        /**
         * The request for the cover of [songs], each given by its tags: one song, or the songs that
         * are to share a cover.
         *
         * The catalogues are searched by song, so the first one speaks for all of them: its title,
         * artist, album and year are what is asked. Their ids are another matter. An id names the
         * release of every song only when every song carries it, so one that any of them lacks, or
         * has another value for, is left out: the cover it names would be the best match for all of
         * them on the word of one.
         *
         * @param titleFallback what the first song is called, when its tags do not say
         * @param durationMs how long the first song is
         * @param currentCover the cover the songs have now
         */
        fun forSongs(
            songs: List<TagMap>,
            titleFallback: String = "",
            durationMs: Long? = null,
            currentCover: ByteArray? = null,
        ): CoverRequest {
            val first = songs.firstOrNull() ?: TagMap.Empty
            return CoverRequest(
                title = first.first(TagField.Title.key)?.ifBlank { null } ?: titleFallback,
                artist = first.first(TagField.Artist.key),
                album = first.first(TagField.Album.key),
                albumArtist = first.first(TagField.AlbumArtist.key),
                year = first.first(TagField.Date.key)?.take(4)?.toIntOrNull(),
                durationMs = durationMs,
                isrc = first.first(TagField.Isrc.key),
                identifiers =
                    first.keys
                        .filter { key ->
                            key.startsWith(CATALOGUE_ID_PREFIX) &&
                                songs.all { it[key] == first[key] }
                        }
                        .mapNotNull { key -> first.first(key)?.let { key to it } }
                        .toMap(),
                currentCover = currentCover,
            )
        }

        /** MusicBrainz's ids, the ones taggers write to files, all start the same way. */
        private const val CATALOGUE_ID_PREFIX = "MUSICBRAINZ_"
    }
}

/** Something that speaks for a cover, or against it. */
enum class CoverReason(val isWarning: Boolean = false) {
    /** It is the cover of the release the file's own MusicBrainz id names. */
    SameRelease,

    /** It comes with the very recording the file's ISRC names. */
    SameRecording,
    AlbumMatches,
    ArtistMatches,

    /** The song is on it, where the tags name no album to compare. */
    TitleMatches,
    YearMatches,

    /** More than one catalogue has this same picture for the song. */
    SeveralSources,

    /** It is the picture the file already has. */
    SameAsCurrent,

    /** ... and a larger copy of it. */
    LargerThanCurrent,
    HighResolution,

    /** The tags name another album. */
    AlbumDiffers(isWarning = true),

    /** A compilation of several artists, where the tags do not say the song is from one. */
    VariousArtists(isWarning = true),
    LowResolution(isWarning = true),
    NotSquare(isWarning = true),
}

/**
 * A cover proposed for a song, with what makes it a good or a bad choice.
 *
 * @param sources the catalogues that have this picture, the one [url] is from first
 * @param dimensions how large the picture at [url] is, when that could be read
 * @param score how well it suits the song, from 0 to 1
 */
data class CoverSuggestion(
    val url: String,
    val thumbnailUrl: String?,
    val sources: List<String>,
    val album: String?,
    val artists: List<String>,
    val year: String?,
    val dimensions: ImageDimensions?,
    val score: Double,
    val reasons: List<CoverReason>,
) {
    val confidence: MatchConfidence
        get() = MatchConfidence.of(score)
}

/** How far a search has got, for whoever is waiting on it. */
sealed interface CoverSearchProgress {
    /** The catalogues are being asked. */
    data object Asking : CoverSearchProgress

    /** The catalogues named [of] covers, and [seen] of them have been looked at. */
    data class Looking(val seen: Int, val of: Int) : CoverSearchProgress
}

sealed interface CoverSearchOutcome {
    /** The covers found, the one to recommend first. May be empty. */
    data class Results(val suggestions: List<CoverSuggestion>, val failedProviders: List<String>) :
        CoverSearchOutcome

    data object NoProviders : CoverSearchOutcome

    data object Offline : CoverSearchOutcome

    data object RateLimited : CoverSearchOutcome

    data object Failed : CoverSearchOutcome
}
