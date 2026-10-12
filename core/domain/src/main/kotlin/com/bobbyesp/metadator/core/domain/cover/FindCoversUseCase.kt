/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.domain.lookup.LookupOutcome
import com.bobbyesp.metadator.core.domain.lookup.LookupService
import com.bobbyesp.metadator.lookup.api.ArtworkDownloader
import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.LookupQuery
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds covers for a song and says which one to use.
 *
 * Every enabled catalogue is asked for the song. Of what they answer, the covers whose release best
 * agrees with the tags are looked at: a small copy of each is downloaded, to see what it shows, and
 * the first bytes of the picture itself, to read how large it is. Copies of one picture from
 * several catalogues become one suggestion, the largest copy of it, and count for it: two
 * catalogues do not agree on a wrong cover by chance. Then each is scored ([CoverScoring]) and the
 * best comes first.
 *
 * All of it happens on the device. What leaves it is what a search already sent: the song's title,
 * artist and album.
 */
class FindCoversUseCase(
    private val lookup: LookupService,
    private val downloader: ArtworkDownloader,
    private val sampler: ImageSampler,
    private val dispatchers: AppDispatchers,
) {
    /** @param onProgress told how far the search has got, as it goes, on no particular thread */
    suspend operator fun invoke(
        request: CoverRequest,
        onProgress: (CoverSearchProgress) -> Unit = {},
    ): CoverSearchOutcome =
        withContext(dispatchers.default) {
            onProgress(CoverSearchProgress.Asking)
            val direct =
                lookup.artworkFor(request.identifiers).map { (provider, artwork) ->
                    Proposed(
                        // The file says which release it is, so what is known of the release is
                        // what the file says.
                        LookupCandidate(
                            providerId = provider.id,
                            providerName = provider.displayName,
                            id = artwork.url,
                            title = request.title,
                            artists = listOfNotNull(request.artist),
                            album = request.album,
                            albumArtists = listOfNotNull(request.albumArtist),
                            date = request.year?.toString(),
                            artworkUrl = artwork.url,
                            artworkThumbnailUrl = artwork.thumbnailUrl,
                        ),
                        isDirect = true,
                    )
                }

            // A song with no title is still on an album that often has a song named after it.
            val query =
                LookupQuery(
                    title = request.title.trim().ifEmpty { request.album?.trim().orEmpty() },
                    artist = request.artist?.trim()?.ifEmpty { null },
                    album = request.album?.trim()?.ifEmpty { null },
                    durationMs = request.durationMs,
                )
            val outcome = lookup.search(query)
            val found =
                when (outcome) {
                    is LookupOutcome.Results -> outcome.candidates
                    // A cover named by the file's own id needs no search to have worked.
                    else ->
                        if (direct.isEmpty()) return@withContext outcome.asCoverOutcome()
                        else emptyList()
                }

            val searched =
                found
                    .filter { it.artworkUrl != null }
                    .groupBy { it.artworkUrl }
                    .values
                    // One cover comes with every song of its album that matched: the best of
                    // them speaks for it.
                    .map { sameCover ->
                        sameCover.maxBy { CoverScoring.textMatch(request, it) }
                    }
                    .sortedByDescending { CoverScoring.textMatch(request, it) }
                    .take(MAX_LOOKED_AT)
                    .map { Proposed(it, isDirect = false) }

            val current = request.currentCover?.let { sampler.sample(it) }
            val seen = lookAt(direct + searched, onProgress)
            val suggestions =
                samePictures(seen)
                    .map { copies -> suggestion(request, copies, current) }
                    .sortedByDescending { it.score }
            CoverSearchOutcome.Results(
                suggestions = suggestions,
                failedProviders = (outcome as? LookupOutcome.Results)?.failedProviders.orEmpty(),
            )
        }

    private class Proposed(val candidate: LookupCandidate, val isDirect: Boolean)

    private class Seen(
        val proposed: Proposed,
        val dimensions: ImageDimensions?,
        val sample: ImageSample?,
    )

    /**
     * Downloads what is needed to judge each cover, a few at a time. A cover of which nothing could
     * be had is left out: a catalogue names covers it does not have. So is one that is a flat
     * color, which is what some show in place of a missing one.
     */
    private suspend fun lookAt(
        proposed: List<Proposed>,
        onProgress: (CoverSearchProgress) -> Unit,
    ): List<Seen> = coroutineScope {
        val gate = Semaphore(PARALLEL_DOWNLOADS)
        val seen = AtomicInteger()
        onProgress(CoverSearchProgress.Looking(0, proposed.size))
        proposed
            .map { cover ->
                async {
                    gate.withPermit {
                        val url = cover.candidate.artworkUrl ?: return@withPermit null
                        val thumbnail = cover.candidate.artworkThumbnailUrl ?: url
                        val sample = async {
                            withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
                                downloader.download(thumbnail)?.let { sampler.sample(it.bytes) }
                            }
                        }
                        val dimensions = async {
                            withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
                                downloader.peek(url, HEADER_BYTES)?.let(::imageDimensions)
                            }
                        }
                        Seen(cover, dimensions.await(), sample.await()).also {
                            onProgress(
                                CoverSearchProgress.Looking(seen.incrementAndGet(), proposed.size)
                            )
                        }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .filter { it.sample != null || it.dimensions != null }
            .filter { seen ->
                seen.sample.let { it == null || it.contrast >= ImageSample.FLAT_CONTRAST }
            }
    }

    /**
     * Groups the copies of one picture, in the order their first copy came. A cover that could not
     * be looked at is a group of its own: nothing says what it is a copy of.
     */
    private fun samePictures(seen: List<Seen>): List<List<Seen>> {
        val groups = mutableListOf<MutableList<Seen>>()
        for (cover in seen) {
            val sample = cover.sample
            val group =
                if (sample == null) null
                else groups.firstOrNull { it.first().sample?.isSamePictureAs(sample) == true }
            if (group != null) group += cover else groups += mutableListOf(cover)
        }
        return groups
    }

    /** One suggestion for one picture: its best copy, backed by every catalogue that has it. */
    private fun suggestion(
        request: CoverRequest,
        copies: List<Seen>,
        current: ImageSample?,
    ): CoverSuggestion {
        val sourceCount = copies.distinctBy { it.proposed.candidate.providerId }.size
        val scored = copies.map { copy ->
            copy to
                CoverScoring.score(
                    request,
                    CoverEvidence(
                        candidate = copy.proposed.candidate,
                        isDirect = copy.proposed.isDirect,
                        dimensions = copy.dimensions,
                        sample = copy.sample,
                        sourceCount = sourceCount,
                        current = current,
                    ),
                )
        }
        val (best, verdict) = scored.maxBy { it.second.score }
        val candidate = best.proposed.candidate
        return CoverSuggestion(
            url = checkNotNull(candidate.artworkUrl),
            thumbnailUrl = candidate.artworkThumbnailUrl,
            sources =
                (listOf(candidate.providerName) + copies.map { it.proposed.candidate.providerName })
                    .distinct(),
            album = candidate.album,
            artists = candidate.albumArtists.ifEmpty { candidate.artists },
            year = candidate.year,
            dimensions = best.dimensions,
            score = verdict.score,
            reasons = verdict.reasons,
        )
    }

    private fun LookupOutcome.asCoverOutcome(): CoverSearchOutcome =
        when (this) {
            is LookupOutcome.Results -> CoverSearchOutcome.Results(emptyList(), failedProviders)
            LookupOutcome.NoProviders -> CoverSearchOutcome.NoProviders
            LookupOutcome.Offline -> CoverSearchOutcome.Offline
            LookupOutcome.RateLimited -> CoverSearchOutcome.RateLimited
            LookupOutcome.Failed -> CoverSearchOutcome.Failed
        }

    private companion object {
        /** The covers downloaded to be compared: past the best dozen matches, none is the one. */
        const val MAX_LOOKED_AT = 12
        const val PARALLEL_DOWNLOADS = 6

        /** Where a JPEG says its size, even after the notes a camera or an editor leaves first. */
        const val HEADER_BYTES = 64 * 1024

        /** Past this a catalogue is not answering, and the rest should not wait for it. */
        val DOWNLOAD_TIMEOUT = 8.seconds
    }
}
