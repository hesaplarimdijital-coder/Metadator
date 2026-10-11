/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.cover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.domain.cover.CoverRequest
import com.bobbyesp.metadator.core.domain.cover.CoverSearchOutcome
import com.bobbyesp.metadator.core.domain.cover.CoverSuggestion
import com.bobbyesp.metadator.core.domain.cover.FindCoversUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CoverFinderStatus {
    data object Idle : CoverFinderStatus

    data object Searching : CoverFinderStatus

    /** The covers found, the one to recommend first. */
    data class Results(val suggestions: List<CoverSuggestion>, val failedProviders: List<String>) :
        CoverFinderStatus

    data object NoProviders : CoverFinderStatus

    data object Offline : CoverFinderStatus

    data object RateLimited : CoverFinderStatus

    data object Failed : CoverFinderStatus
}

data class CoverFinderState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val status: CoverFinderStatus = CoverFinderStatus.Idle,
)

/**
 * The "Find a cover" sheet, for whichever screen opens it: the editor for one song, the batch
 * editor for the songs of an album. It proposes and the screen applies. Nothing here touches a
 * file: choosing a cover hands it to the screen, and only saving there writes it.
 */
class CoverFinderViewModel(private val findCovers: FindCoversUseCase) : ViewModel() {
    private val _state = MutableStateFlow(CoverFinderState())
    val state: StateFlow<CoverFinderState> = _state.asStateFlow()
    private var search: Job? = null

    /** The song as the screen has it: what is searched with can be changed, the rest cannot. */
    private var song: CoverRequest? = null

    /**
     * Looks for covers for [request], unless the covers on screen are already this song's: the
     * sheet opened a second time shows what it found the first.
     */
    fun start(request: CoverRequest) {
        val status = _state.value.status
        val shown = status is CoverFinderStatus.Results || status == CoverFinderStatus.Searching
        if (shown && song?.isSameSongAs(request) == true) return
        song = request
        _state.value =
            CoverFinderState(
                title = request.title,
                artist = request.artist.orEmpty(),
                album = request.album.orEmpty(),
            )
        search()
    }

    fun setQuery(title: String, artist: String, album: String) {
        _state.update { it.copy(title = title, artist = artist, album = album) }
    }

    fun search() {
        val song = song ?: return
        val query = _state.value
        search?.cancel()
        search = viewModelScope.launch {
            _state.update { it.copy(status = CoverFinderStatus.Searching) }
            val status =
                try {
                    findCovers(
                            CoverRequest(
                                title = query.title.trim(),
                                artist = query.artist.trim().ifEmpty { null },
                                album = query.album.trim().ifEmpty { null },
                                albumArtist = song.albumArtist,
                                year = song.year,
                                durationMs = song.durationMs,
                                isrc = song.isrc,
                                identifiers = song.identifiers,
                                currentCover = song.currentCover,
                            )
                        )
                        .toStatus()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    CoverFinderStatus.Failed
                }
            _state.update { it.copy(status = status) }
        }
    }

    private fun CoverSearchOutcome.toStatus(): CoverFinderStatus =
        when (this) {
            is CoverSearchOutcome.Results -> CoverFinderStatus.Results(suggestions, failedProviders)
            CoverSearchOutcome.NoProviders -> CoverFinderStatus.NoProviders
            CoverSearchOutcome.Offline -> CoverFinderStatus.Offline
            CoverSearchOutcome.RateLimited -> CoverFinderStatus.RateLimited
            CoverSearchOutcome.Failed -> CoverFinderStatus.Failed
        }

    /**
     * The same tags and the same cover: nothing a new search would see differently. The cover is
     * compared by what it holds, since a screen may have read it from the file again.
     */
    private fun CoverRequest.isSameSongAs(other: CoverRequest): Boolean =
        title == other.title &&
            artist == other.artist &&
            album == other.album &&
            year == other.year &&
            identifiers == other.identifiers &&
            currentCover.contentEquals(other.currentCover)
}
