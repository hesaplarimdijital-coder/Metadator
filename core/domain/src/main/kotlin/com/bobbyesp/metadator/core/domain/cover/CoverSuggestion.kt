/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.lookup.api.MatchConfidence

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
)

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

sealed interface CoverSearchOutcome {
    /** The covers found, the one to recommend first. May be empty. */
    data class Results(val suggestions: List<CoverSuggestion>, val failedProviders: List<String>) :
        CoverSearchOutcome

    data object NoProviders : CoverSearchOutcome

    data object Offline : CoverSearchOutcome

    data object RateLimited : CoverSearchOutcome

    data object Failed : CoverSearchOutcome
}
