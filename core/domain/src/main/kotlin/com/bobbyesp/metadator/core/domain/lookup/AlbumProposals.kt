/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.lookup

import com.bobbyesp.metadator.core.common.normalizedForComparison
import com.bobbyesp.metadator.core.common.similarity
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.MatchConfidence
import kotlin.math.abs

/**
 * A song of a batch, as finding its album sees it: what it is called, how long it is, and where its
 * file says it is on its album, which tells two songs of one name apart.
 */
data class AlbumSong(
    val title: String,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

/**
 * A release the songs may be from: one album by one artist, with what the sources that have it
 * agree it is, and the tracks of it the search came across.
 *
 * @param id what tells it from the other proposals: its title and artist, as compared
 * @param trackCount how many tracks the whole release has, where the sources say
 * @param sources the catalogues that have it, by name
 * @param tracks its tracks the search found, one candidate per source and track
 * @param songsFound how many of the songs are among [tracks], by title
 * @param score how well it suits the songs, from 0 to 1
 */
data class AlbumProposal(
    val id: String,
    val album: String,
    val albumArtists: List<String>,
    val date: String?,
    val genres: List<String>,
    val trackCount: Int?,
    val discTotal: Int?,
    val artworkUrl: String?,
    val artworkThumbnailUrl: String?,
    val sources: List<String>,
    val tracks: List<LookupCandidate>,
    val songsFound: Int,
    val score: Double,
) {
    val year: String?
        get() = date?.take(4)

    val confidence: MatchConfidence
        get() = MatchConfidence.of(score)
}

/**
 * Where a song is on a release, as far as that is sure. What is null the file keeps.
 *
 * @param track its track number, only for a song found on the release
 */
data class ReleasePosition(
    val track: Int? = null,
    val trackTotal: Int? = null,
    val disc: Int? = null,
    val discTotal: Int? = null,
)

/**
 * The releases [candidates] are from, the one that suits [songs] best first.
 *
 * The sources answer track by track, each track with its album. Tracks of one title by one artist
 * are taken as one release, whatever source named them, and each source has one say in what is
 * known of it: the value most of them give wins, and a tie goes to the better match, which comes
 * first. A release is ranked by how well its title and artist match [album] and [artist], how many
 * of the songs are on it, how close its number of tracks is to theirs, how many sources have it,
 * and whether it has a cover.
 *
 * @param candidates what the searches found, the best matches of each first
 */
fun albumProposals(
    album: String,
    artist: String,
    songs: List<AlbumSong>,
    candidates: List<LookupCandidate>,
): List<AlbumProposal> {
    val sourceCount = candidates.distinctBy { it.providerId }.size
    val songKeys = songs.map { titleKey(it.title) }.filter { it.isNotEmpty() }
    return candidates
        .filter { !it.album.isNullOrBlank() }
        .groupBy { releaseKey(it) }
        .map { (id, tracks) -> proposal(id, tracks) }
        .map { proposal ->
            val titles = proposal.tracks.mapTo(HashSet()) { titleKey(it.title) }
            val found = songKeys.count { it in titles }
            proposal.copy(
                songsFound = found,
                score = score(proposal, album, artist, songs.size, found, sourceCount),
            )
        }
        .sortedWith(
            compareByDescending<AlbumProposal> { it.score }.thenByDescending { it.songsFound }
        )
        .take(MAX_PROPOSALS)
}

/**
 * Where each of [songs] is on this release: its track and disc for a song found on it for sure,
 * only the track total for the others where that is known, and null where nothing is.
 *
 * A song is found when one of the release's tracks has its title and is the same recording by its
 * length, or, where a length is missing, sits where the file already says it does. Sources that
 * disagree on where it is, as two editions can, say nothing: a number is never guessed.
 */
fun AlbumProposal.positionsFor(songs: List<AlbumSong>): List<ReleasePosition?> {
    val byTitle = tracks.filter { it.trackNumber != null }.groupBy { titleKey(it.title) }
    return songs.map { song ->
        val key = titleKey(song.title)
        val sameTitle = if (key.isEmpty()) null else byTitle[key]
        sameTitle?.let { foundAt(song, it) }
            ?: trackTotalFor(song)?.let { ReleasePosition(trackTotal = it) }
    }
}

/** The one place [song] is at among [sameTitle], or null where there is none or no single one. */
private fun foundAt(song: AlbumSong, sameTitle: List<LookupCandidate>): ReleasePosition? {
    val supported = sameTitle.filter { song.isAt(it) }
    if (supported.isEmpty()) return null
    val byNumber =
        supported
            .groupBy { it.trackNumber!! }
            .mapValues { (_, at) -> at.distinctBy { it.providerId }.size }
    val most = byNumber.values.max()
    val number = byNumber.filterValues { it == most }.keys.singleOrNull() ?: return null
    val at = supported.filter { it.trackNumber == number }
    // What the sources disagree on is left as the file has it: two editions of a release can put
    // the same track on another disc, or on a disc of another length.
    return ReleasePosition(
        track = number,
        trackTotal = at.agreed(tieIsNone = true) { it.trackTotal },
        disc = at.agreed(tieIsNone = true) { it.discNumber },
        discTotal = at.agreed(tieIsNone = true) { it.discTotal },
    )
}

/**
 * Whether [track] is this song: by length where both are known, as the ranking of candidates judges
 * a recording; by where the file says it is otherwise.
 */
private fun AlbumSong.isAt(track: LookupCandidate): Boolean {
    val length = durationMs?.takeIf { it > 0 }
    val theirs = track.durationMs?.takeIf { it > 0 }
    if (length != null && theirs != null) return abs(length - theirs) <= SAME_LENGTH_MS
    return trackNumber != null &&
        trackNumber == track.trackNumber &&
        (discNumber == null || track.discNumber == null || discNumber == track.discNumber)
}

/**
 * The track total of the disc [song] is on, for a song not found on the release: known where the
 * file says its disc, or where the release has one disc only.
 */
private fun AlbumProposal.trackTotalFor(song: AlbumSong): Int? {
    val disc = song.discNumber ?: 1.takeIf { discTotal == 1 } ?: return null
    return tracks.filter { (it.discNumber ?: 1) == disc }.agreed(tieIsNone = true) { it.trackTotal }
}

private fun proposal(id: String, tracks: List<LookupCandidate>): AlbumProposal {
    val year = tracks.agreed { candidate -> candidate.year?.takeIf { it.length == 4 } }
    val discTotal = tracks.agreed(tieIsNone = true) { it.discTotal }
    val cover = tracks.firstOrNull { it.artworkUrl != null }
    return AlbumProposal(
        id = id,
        album = tracks.agreed { it.album }!!,
        albumArtists =
            tracks.agreed(sameAs = ::listKey) { candidate ->
                candidate.albumArtists.ifEmpty { candidate.artists }.ifEmpty { null }
            } ?: emptyList(),
        date = year?.let { dateIn(it, tracks) },
        genres = tracks.agreed(sameAs = ::listKey) { it.genres.ifEmpty { null } } ?: emptyList(),
        trackCount = trackCount(tracks, discTotal),
        discTotal = discTotal,
        artworkUrl = cover?.artworkUrl,
        artworkThumbnailUrl = cover?.artworkThumbnailUrl,
        sources = tracks.map { it.providerName }.distinct(),
        tracks = tracks,
        songsFound = 0,
        score = 0.0,
    )
}

/**
 * The date of [year] the sources agree on, the most precise one where they agree equally: one says
 * "2001", another "2001-03-12".
 */
private fun dateIn(year: String, tracks: List<LookupCandidate>): String {
    val dates = tracks.filter { it.date?.startsWith(year) == true }
    val bySource =
        dates
            .groupBy { it.providerId }
            .values
            .map { own -> own.mapNotNull { it.date }.mostCommon() }
    return bySource
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
        .key
}

/**
 * How many tracks the release has: the track total of its one disc, or of each of its discs added
 * up where every disc's is known.
 */
private fun trackCount(tracks: List<LookupCandidate>, discTotal: Int?): Int? {
    val discs = discTotal ?: tracks.mapNotNull { it.discNumber }.maxOrNull() ?: 1
    val totals =
        (1..discs).map { disc ->
            tracks.filter { (it.discNumber ?: 1) == disc }.agreed { it.trackTotal } ?: return null
        }
    return totals.sum()
}

private fun score(
    proposal: AlbumProposal,
    album: String,
    artist: String,
    songCount: Int,
    songsFound: Int,
    sourceCount: Int,
): Double {
    var total = 0.0
    var weights = 0.0

    fun add(weight: Double, value: Double) {
        total += weight * value
        weights += weight
    }

    if (album.isNotBlank()) add(0.3, similarity(album, proposal.album))
    if (artist.isNotBlank()) {
        val names = proposal.albumArtists + proposal.albumArtists.joinToString(" ")
        add(0.2, names.maxOfOrNull { similarity(artist, it) } ?: 0.0)
    }
    if (songCount > 0) {
        // The surest sign: a release with the songs on it is theirs.
        add(0.2, songsFound.toDouble() / songCount)
        // Unknown counts for half: a release is not worse for a source that does not say.
        val tracks = proposal.trackCount
        add(
            0.15,
            if (tracks == null) 0.5
            else 1.0 - abs(tracks - songCount).toDouble() / maxOf(tracks, songCount),
        )
    }
    if (sourceCount > 0) add(0.1, proposal.sources.size.toDouble() / sourceCount)
    add(0.05, if (proposal.artworkUrl != null) 1.0 else 0.0)
    return if (weights == 0.0) 0.0 else total / weights
}

/**
 * The value most sources give, each counting once, for the value it gives most itself. A tie goes
 * to the value of the better match, which comes first; or to none, where [tieIsNone] says that a
 * value two sources disagree on is not known.
 */
private fun <T : Any> List<LookupCandidate>.agreed(
    tieIsNone: Boolean = false,
    sameAs: (T) -> Any = { it },
    value: (LookupCandidate) -> T?,
): T? {
    val bySource = groupBy {
        it.providerId
    }
        .values
        .mapNotNull { own -> own.mapNotNull(value).takeIf { it.isNotEmpty() } }
        .map { values -> values.groupBy(sameAs).values.maxBy { it.size }.first() }
    if (bySource.isEmpty()) return null
    val counted = bySource.groupBy(sameAs).values
    val most = counted.maxOf { it.size }
    val best = counted.filter { it.size == most }
    if (tieIsNone && best.size > 1) return null
    return best.first().first()
}

/** The value given most often, the first of them on a tie. */
private fun <T> List<T>.mostCommon(): T = groupingBy { it }.eachCount().maxBy { it.value }.key

/**
 * What tells one release from another: its title and the first artist it is credited to, as they
 * compare. A source that credits a featured artist on one track ("A, B & C") still names A first.
 */
private fun releaseKey(candidate: LookupCandidate): String {
    val artist =
        candidate.albumArtists
            .ifEmpty { candidate.artists }
            .firstOrNull()
            ?.let { name ->
                name.split(Credits, limit = 2).first().ifBlank { name }
            }
    return candidate.album.orEmpty().normalizedForComparison() +
        "\n" +
        artist.orEmpty().normalizedForComparison()
}

private fun listKey(values: List<String>): Any = values.map { it.normalizedForComparison() }

/**
 * A title as it compares with another of the same song: without the featured artists or the
 * remaster note one source adds and another does not.
 */
internal fun titleKey(title: String): String =
    title
        .replace(BracketedNote, "")
        .replace(TrailingFeaturing, "")
        .replace(TrailingRemaster, "")
        .normalizedForComparison()

private const val MAX_PROPOSALS = 10

/** Within this, two lengths are one recording, cut or counted a little differently. */
private const val SAME_LENGTH_MS = 3_000L

// Escaped for Android's engine (ICU), which takes a bare "]" for the end of a class.
private val Credits =
    Regex(
        """\s*(?:,|&|/|;|\bfeat\.?|\bft\.|\bfeaturing\b|\bwith\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE,
    )
private val BracketedNote =
    Regex(
        """\s*[(\[]\s*(?:(?:feat\.?|ft\.|featuring|with)\s[^)\]]*|[^)\]]*remaster[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )
private val TrailingFeaturing =
    Regex("""\s+(?:feat\.?|ft\.|featuring)\s.*$""", RegexOption.IGNORE_CASE)
private val TrailingRemaster = Regex("""\s+-\s+[^-]*remaster.*$""", RegexOption.IGNORE_CASE)
