/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.core.common.normalizedForComparison
import com.bobbyesp.metadator.core.common.similarity
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import kotlin.math.abs

/**
 * Everything gathered about one cover a catalogue proposed.
 *
 * @param candidate the song the catalogue matched, whose album the cover is of
 * @param isDirect whether the catalogue named it from an id of its own in the file, not a search
 * @param dimensions how large the whole picture is
 * @param sample what its small copy looks like
 * @param sourceCount how many catalogues have this same picture for the song
 * @param current the cover the file has now
 */
internal class CoverEvidence(
    val candidate: LookupCandidate,
    val isDirect: Boolean = false,
    val dimensions: ImageDimensions? = null,
    val sample: ImageSample? = null,
    val sourceCount: Int = 1,
    val current: ImageSample? = null,
)

internal class ScoredCover(val score: Double, val reasons: List<CoverReason>)

/**
 * Says how well a cover suits a song, and why.
 *
 * The right cover is the cover of the release the song is from, so most of the score is how well
 * what the catalogue says of the release agrees with the tags. Text alone cannot tell two editions
 * of one album apart, nor a real cover from a scan somebody uploaded, so the rest comes from the
 * picture: its size, its shape, and whether other catalogues, or the file itself, have the same
 * one.
 */
internal object CoverScoring {
    private const val TEXT_WEIGHT = 0.70
    private const val YEAR_WEIGHT = 0.08
    private const val SIZE_WEIGHT = 0.10
    private const val SOURCES_WEIGHT = 0.07
    private const val SHAPE_WEIGHT = 0.05

    /** What stands in for a measure that could not be taken: neither for nor against. */
    private const val UNKNOWN = 0.5

    private const val MATCH = 0.85
    private const val MISMATCH = 0.5

    /** Sharp at any size a cover is shown at; past it a cover only gets heavier. */
    private const val LARGE_EDGE = 1000
    private const val SMALL_EDGE = 500
    private const val SQUARE_RATIO = 0.9

    /** A copy this much larger than the file's cover is worth changing it for. */
    private const val LARGER_BY = 1.2

    private const val COMPILATION_PENALTY = 0.10
    private const val SAME_PICTURE_SCORE = 0.90
    private const val SAME_RELEASE_SCORE = 0.98

    /** A small picture is never the confident choice, however well its album matches. */
    private const val SMALL_PICTURE_CAP = 0.84

    fun score(request: CoverRequest, evidence: CoverEvidence): ScoredCover {
        val candidate = evidence.candidate
        val reasons = mutableListOf<CoverReason>()

        val sameRecording =
            request.isrc.isrc().let { it != null && it == candidate.isrc.isrc() } &&
                !evidence.isDirect
        var title = if (request.title.isBlank()) null else titleSimilarity(request, candidate)
        var artist = artistSimilarity(request, candidate)
        val album = albumSimilarity(request.album, candidate.album)
        if (sameRecording) {
            title = 1.0
            artist = 1.0
        }
        val duration = durationCloseness(request.durationMs, candidate.durationMs)
        // With an album to compare it weighs most: it is the album's cover that is wanted. With
        // none, the song is all there is to find the album by.
        val text =
            if (album != null) {
                weighted(0.25 to title, 0.30 to artist, 0.35 to album, 0.10 to duration)
            } else {
                weighted(0.50 to title, 0.40 to artist, 0.10 to duration)
            }

        if (evidence.isDirect) reasons += CoverReason.SameRelease
        if (sameRecording) reasons += CoverReason.SameRecording
        if (album != null && album >= MATCH) reasons += CoverReason.AlbumMatches
        if (artist != null && artist >= MATCH) reasons += CoverReason.ArtistMatches
        if (album == null && title != null && title >= MATCH) reasons += CoverReason.TitleMatches

        val year = yearCloseness(request.year, candidate.year?.toIntOrNull())
        if (year == 1.0) reasons += CoverReason.YearMatches

        val sources =
            when {
                evidence.sourceCount >= 3 -> 1.0
                evidence.sourceCount == 2 -> 0.6
                else -> 0.0
            }
        if (evidence.sourceCount >= 2) reasons += CoverReason.SeveralSources

        val current = evidence.current
        val sameAsCurrent = current != null && evidence.sample?.isSamePictureAs(current) == true
        if (sameAsCurrent) {
            reasons += CoverReason.SameAsCurrent
            val edge = evidence.dimensions?.shortEdge
            if (
                edge != null && current != null && edge >= current.dimensions.shortEdge * LARGER_BY
            ) {
                reasons += CoverReason.LargerThanCurrent
            }
        }

        val edge = evidence.dimensions?.shortEdge
        if (edge != null && edge >= LARGE_EDGE) reasons += CoverReason.HighResolution

        // Against it, after everything for it.
        val albumDiffers = album != null && album < MISMATCH && !evidence.isDirect
        if (albumDiffers) reasons += CoverReason.AlbumDiffers
        val compilation =
            candidate.albumArtists.any { it.isVariousArtists() } &&
                request.albumArtist?.isVariousArtists() != true &&
                !evidence.isDirect &&
                (album == null || album < MATCH)
        if (compilation) reasons += CoverReason.VariousArtists
        val small = edge != null && edge < SMALL_EDGE
        if (small) reasons += CoverReason.LowResolution
        val shape = evidence.dimensions?.let { squareness(it) }
        if (shape != null && shape < SQUARE_RATIO) reasons += CoverReason.NotSquare

        var score =
            TEXT_WEIGHT * text +
                YEAR_WEIGHT * (year ?: UNKNOWN) +
                SIZE_WEIGHT * (edge?.let(::sizeScore) ?: UNKNOWN) +
                SOURCES_WEIGHT * sources +
                SHAPE_WEIGHT * (shape?.let { if (it >= SQUARE_RATIO) 1.0 else 0.0 } ?: UNKNOWN)
        if (compilation) score -= COMPILATION_PENALTY
        // The file's own cover says what the right picture is better than its tags do.
        if (sameAsCurrent && text >= MISMATCH) score = maxOf(score, SAME_PICTURE_SCORE)
        if (evidence.isDirect) score = maxOf(score, SAME_RELEASE_SCORE)
        if (small) score = minOf(score, SMALL_PICTURE_CAP)
        return ScoredCover(score.coerceIn(0.0, 1.0), reasons)
    }

    /**
     * How well the catalogue's entry agrees with the tags on what a search is made with: enough to
     * choose which covers are worth a closer look, before anything is downloaded.
     */
    fun textMatch(request: CoverRequest, candidate: LookupCandidate): Double {
        val title = if (request.title.isBlank()) null else titleSimilarity(request, candidate)
        return weighted(
            0.30 to title,
            0.30 to artistSimilarity(request, candidate),
            0.40 to albumSimilarity(request.album, candidate.album),
        )
    }

    /** The mean of the values that are known, each with its weight; 0 when none is. */
    private fun weighted(vararg parts: Pair<Double, Double?>): Double {
        var total = 0.0
        var weights = 0.0
        for ((weight, value) in parts) {
            if (value == null) continue
            total += weight * value
            weights += weight
        }
        return if (weights == 0.0) 0.0 else total / weights
    }

    private fun titleSimilarity(request: CoverRequest, candidate: LookupCandidate): Double =
        maxOf(
            similarity(request.title, candidate.title),
            similarity(request.title.withoutNotes(), candidate.title.withoutNotes()),
        )

    private fun artistSimilarity(request: CoverRequest, candidate: LookupCandidate): Double? {
        val wanted = listOfNotNull(request.artist, request.albumArtist).filter { it.isNotBlank() }
        val offered =
            (candidate.artists +
                    candidate.albumArtists +
                    candidate.artists.joinToString(" ") +
                    candidate.albumArtists.joinToString(" "))
                .filter { it.isNotBlank() }
        if (wanted.isEmpty() || offered.isEmpty()) return null
        return wanted.maxOf { name -> offered.maxOf { similarity(name, it) } }
    }

    private fun albumSimilarity(wanted: String?, offered: String?): Double? {
        if (wanted.isNullOrBlank() || offered.isNullOrBlank()) return null
        return maxOf(
            similarity(wanted, offered),
            similarity(wanted.withoutNotes(), offered.withoutNotes()),
        )
    }

    /** Within 2 s is the same recording; past 20 s it almost certainly is not. */
    private fun durationCloseness(wanted: Long?, offered: Long?): Double? {
        if (wanted == null || offered == null || wanted <= 0 || offered <= 0) return null
        val seconds = abs(wanted - offered) / 1000.0
        return (1.0 - (seconds - 2.0).coerceAtLeast(0.0) / 18.0).coerceIn(0.0, 1.0)
    }

    /** Reissues carry the year they came out, so a near miss is not a miss. */
    private fun yearCloseness(wanted: Int?, offered: Int?): Double? {
        if (wanted == null || offered == null) return null
        return when (abs(wanted - offered)) {
            0 -> 1.0
            1 -> 0.7
            2,
            3 -> 0.4
            else -> 0.1
        }
    }

    private fun sizeScore(shortEdge: Int): Double =
        when {
            shortEdge >= 1400 -> 1.0
            shortEdge >= LARGE_EDGE -> 0.85 + 0.15 * (shortEdge - LARGE_EDGE) / 400
            shortEdge >= SMALL_EDGE -> 0.5 + 0.35 * (shortEdge - SMALL_EDGE) / 500
            else -> 0.5 * shortEdge / SMALL_EDGE
        }

    private fun squareness(dimensions: ImageDimensions): Double =
        dimensions.shortEdge.toDouble() / maxOf(dimensions.width, dimensions.height)

    /** An ISRC as it compares: its twelve letters and digits, however it was written. */
    private fun String?.isrc(): String? =
        this?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.length == 12 }

    /**
     * Without what catalogues add to a name to tell editions apart: "(Deluxe Edition)",
     * "[Remastered 2011]", " - Single". Never down to nothing: a name that is only a note is kept.
     */
    private fun String.withoutNotes(): String =
        replace(BRACKETED, " ").replace(RELEASE_KIND, "").trim().ifEmpty { this }

    private fun String.isVariousArtists(): Boolean = normalizedForComparison() in VARIOUS_ARTISTS

    private val BRACKETED = Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]")
    private val RELEASE_KIND = Regex("\\s+-\\s+(single|ep)\\s*$", RegexOption.IGNORE_CASE)
    private val VARIOUS_ARTISTS =
        setOf(
            "various artists",
            "various",
            "va",
            "varios artistas",
            "varios",
            "cesitli sanatcilar",
            // The same with the dotless i, which is a letter of its own and loses no accent.
            "cesitli sanatc\u0131lar",
            "verschiedene interpreten",
            "artistes divers",
            "artisti vari",
        )
}
