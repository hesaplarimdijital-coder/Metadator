/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.cover

import com.bobbyesp.metadator.lookup.api.LookupCandidate
import com.bobbyesp.metadator.lookup.api.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverScoringTest {
    private val request =
        CoverRequest(
            title = "Halo",
            artist = "Beyoncé",
            album = "I Am... Sasha Fierce",
            year = 2008,
            durationMs = 261_000,
        )

    private fun candidate(
        title: String = "Halo",
        artist: String = "Beyoncé",
        album: String? = "I Am... Sasha Fierce",
        albumArtist: String = artist,
        date: String? = "2008-11-14",
        isrc: String? = null,
    ) =
        LookupCandidate(
            providerId = "deezer",
            providerName = "Deezer",
            id = "1",
            title = title,
            artists = listOf(artist),
            album = album,
            albumArtists = listOf(albumArtist),
            date = date,
            isrc = isrc,
            durationMs = 261_000,
        )

    private fun score(
        request: CoverRequest = this.request,
        candidate: LookupCandidate = candidate(),
        edge: Int? = 1000,
        height: Int? = edge,
        isDirect: Boolean = false,
        sample: ImageSample? = null,
        sourceCount: Int = 1,
        current: ImageSample? = null,
    ) =
        CoverScoring.score(
            request,
            CoverEvidence(
                candidate = candidate,
                isDirect = isDirect,
                dimensions =
                    if (edge != null && height != null) ImageDimensions(edge, height) else null,
                sample = sample,
                sourceCount = sourceCount,
                current = current,
            ),
        )

    @Test
    fun `the cover of the album the tags name is a confident choice`() {
        val scored = score()

        assertEquals(MatchConfidence.High, MatchConfidence.of(scored.score))
        assertTrue(CoverReason.AlbumMatches in scored.reasons)
        assertTrue(CoverReason.ArtistMatches in scored.reasons)
        assertTrue(CoverReason.YearMatches in scored.reasons)
        assertTrue(CoverReason.HighResolution in scored.reasons)
        assertTrue(scored.reasons.none { it.isWarning })
    }

    @Test
    fun `the cover of another album of the song is not`() {
        val single = score(candidate = candidate(album = "Halo (Remixes)", date = "2009-01-20"))
        val album = score()

        assertTrue(single.score < album.score - 0.1)
        assertTrue(CoverReason.AlbumDiffers in single.reasons)
        assertFalse(CoverReason.AlbumMatches in single.reasons)
    }

    @Test
    fun `what a catalogue adds to an album's name does not make it another album`() {
        val deluxe = score(candidate = candidate(album = "I Am... Sasha Fierce (Deluxe Edition)"))
        val single =
            score(
                request = CoverRequest("Halo", "Beyoncé", "Halo"),
                candidate = candidate(album = "Halo - Single"),
            )

        assertTrue(CoverReason.AlbumMatches in deluxe.reasons)
        assertTrue(CoverReason.AlbumMatches in single.reasons)
    }

    @Test
    fun `without an album in the tags the song is what is matched`() {
        val untagged = CoverRequest(title = "Halo", artist = "Beyoncé")

        val right = score(request = untagged)
        val wrong =
            score(request = untagged, candidate = candidate(title = "Hello", artist = "Adele"))

        assertTrue(CoverReason.TitleMatches in right.reasons)
        assertEquals(MatchConfidence.High, MatchConfidence.of(right.score))
        assertTrue(wrong.score < 0.6)
    }

    @Test
    fun `the release the file's own id names is the one`() {
        // Whatever else is known: no year, no size.
        val scored = score(candidate = candidate(date = null), edge = null, isDirect = true)

        assertTrue(scored.score >= 0.98)
        assertEquals(CoverReason.SameRelease, scored.reasons.first())
    }

    @Test
    fun `the same recording is the same song whatever it is called`() {
        val tagged =
            CoverRequest(
                "Halo (Album Version)",
                "Beyonce Knowles",
                "I Am... Sasha Fierce",
                isrc = "US-SM1-08-04556",
            )

        val scored = score(request = tagged, candidate = candidate(isrc = "USSM10804556"))

        assertTrue(CoverReason.SameRecording in scored.reasons)
        assertEquals(MatchConfidence.High, MatchConfidence.of(scored.score))
    }

    @Test
    fun `a small picture is never the confident choice`() {
        val scored = score(edge = 300)

        assertTrue(CoverReason.LowResolution in scored.reasons)
        assertEquals(MatchConfidence.Medium, MatchConfidence.of(scored.score))
    }

    @Test
    fun `a larger picture is better, up to what a cover needs`() {
        assertTrue(score(edge = 1000).score > score(edge = 600).score)
        assertTrue(score(edge = 600).score > score(edge = 400).score)
        assertEquals(score(edge = 1400).score, score(edge = 3000).score, 0.0001)
    }

    @Test
    fun `a picture that is not square is said to be`() {
        val scan = score(edge = 1500, height = 1200)

        assertTrue(CoverReason.NotSquare in scan.reasons)
        assertTrue(scan.score < score(edge = 1200).score)
        assertFalse(CoverReason.NotSquare in score(edge = 1425, height = 1417).reasons)
    }

    @Test
    fun `catalogues agreeing on a picture count for it`() {
        val alone = score()
        val two = score(sourceCount = 2)
        val three = score(sourceCount = 3)

        assertTrue(two.score > alone.score)
        assertTrue(three.score > two.score)
        assertTrue(CoverReason.SeveralSources in two.reasons)
        assertFalse(CoverReason.SeveralSources in alone.reasons)
    }

    @Test
    fun `the picture the file already has is the right one, and a larger copy is worth having`() {
        val cover = Artwork(7)
        val untagged = CoverRequest(title = "Halo", artist = "Beyoncé", album = "Greatest Hits")

        val same =
            score(request = untagged, sample = cover.sample(250), current = cover.sample(300))
        val other =
            score(request = untagged, sample = Artwork(8).sample(250), current = cover.sample(300))

        assertTrue(CoverReason.SameAsCurrent in same.reasons)
        assertTrue(CoverReason.LargerThanCurrent in same.reasons)
        assertTrue(same.score >= 0.9)
        assertFalse(CoverReason.SameAsCurrent in other.reasons)
        assertTrue(other.score < same.score)
    }

    @Test
    fun `the same picture no larger than the file's is not said to be larger`() {
        val cover = Artwork(7)

        val scored = score(edge = 600, sample = cover.sample(250), current = cover.sample(600))

        assertTrue(CoverReason.SameAsCurrent in scored.reasons)
        assertFalse(CoverReason.LargerThanCurrent in scored.reasons)
    }

    @Test
    fun `a compilation is not where a song without an album is from`() {
        val untagged = CoverRequest(title = "Halo", artist = "Beyoncé")
        val compilation =
            candidate(album = "Now That's What I Call Music! 72", albumArtist = "Various Artists")

        val scored = score(request = untagged, candidate = compilation)

        assertTrue(CoverReason.VariousArtists in scored.reasons)
        assertTrue(scored.score < score(request = untagged).score - 0.05)
    }

    @Test
    fun `a compilation the tags name is what is wanted`() {
        val tagged = CoverRequest("Halo", "Beyoncé", "Now 72", albumArtist = "Various Artists")
        val compilation = candidate(album = "Now 72", albumArtist = "Various Artists")

        assertFalse(
            CoverReason.VariousArtists in score(request = tagged, candidate = compilation).reasons
        )
    }

    @Test
    fun `a year off by one is a reissue, not another album`() {
        val reissue = score(candidate = candidate(date = "2009"))
        val unrelated = score(candidate = candidate(date = "1994"))

        assertFalse(CoverReason.YearMatches in reissue.reasons)
        assertTrue(reissue.score > unrelated.score)
    }

    @Test
    fun `what could not be measured counts neither way`() {
        val unmeasured = score(edge = null)

        assertTrue(unmeasured.score < score(edge = 1000).score)
        assertTrue(unmeasured.score > score(edge = 300).score - 0.2)
        assertTrue(unmeasured.reasons.none { it == CoverReason.HighResolution || it.isWarning })
    }

    @Test
    fun `the text match alone orders what is worth looking at`() {
        val right = CoverScoring.textMatch(request, candidate())
        val wrong = CoverScoring.textMatch(request, candidate(album = "Dangerously in Love"))

        assertEquals(1.0, right, 0.0001)
        assertTrue(wrong < 0.8)
    }
}
