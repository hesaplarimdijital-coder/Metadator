/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.domain.lookup.AlbumProposal
import com.bobbyesp.metadator.core.domain.lookup.AlbumQuery
import com.bobbyesp.metadator.core.domain.lookup.AlbumSearchOutcome
import com.bobbyesp.metadator.core.domain.lookup.AlbumSearchProgress
import com.bobbyesp.metadator.core.domain.lookup.FindAlbumUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AlbumLookupStatus {
    data object Idle : AlbumLookupStatus

    data class Searching(val progress: AlbumSearchProgress? = null) : AlbumLookupStatus

    /** The releases found, the one that suits the songs best first. */
    data class Results(val proposals: List<AlbumProposal>, val failedProviders: List<String>) :
        AlbumLookupStatus

    data object NoProviders : AlbumLookupStatus

    data object Offline : AlbumLookupStatus

    data object RateLimited : AlbumLookupStatus

    data object Failed : AlbumLookupStatus
}

data class AlbumLookupState(
    val album: String = "",
    val artist: String = "",
    val status: AlbumLookupStatus = AlbumLookupStatus.Idle,
)

/**
 * The batch editor's "Find metadata" sheet: the releases the songs may be from. It proposes and the
 * batch editor applies. Nothing here touches a file: choosing a release fills the batch's fields,
 * and only "Apply to all" writes them.
 */
class AlbumLookupViewModel(private val findAlbum: FindAlbumUseCase) : ViewModel() {
    private val _state = MutableStateFlow(AlbumLookupState())
    val state: StateFlow<AlbumLookupState> = _state.asStateFlow()
    private var search: Job? = null

    /** The songs as the batch has them: what is searched with can be changed, they cannot. */
    private var asked: AlbumQuery? = null

    /**
     * Looks for the releases of [query]'s songs, unless those on screen are already theirs: the
     * sheet opened a second time shows what it found the first.
     */
    fun start(query: AlbumQuery) {
        val status = _state.value.status
        val shown = status is AlbumLookupStatus.Results || status is AlbumLookupStatus.Searching
        if (shown && asked == query) return
        asked = query
        _state.value = AlbumLookupState(album = query.album, artist = query.artist)
        search()
    }

    fun setQuery(album: String, artist: String) {
        _state.update { it.copy(album = album, artist = artist) }
    }

    fun search() {
        val songs = asked?.songs ?: return
        val query = _state.value
        search?.cancel()
        search = viewModelScope.launch {
            _state.update { it.copy(status = AlbumLookupStatus.Searching()) }
            val status =
                try {
                    findAlbum(AlbumQuery(query.album.trim(), query.artist.trim(), songs)) { progress
                            ->
                            _state.update {
                                it.copy(status = AlbumLookupStatus.Searching(progress))
                            }
                        }
                        .toStatus()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    AlbumLookupStatus.Failed
                }
            _state.update { it.copy(status = status) }
        }
    }

    private fun AlbumSearchOutcome.toStatus(): AlbumLookupStatus =
        when (this) {
            is AlbumSearchOutcome.Results -> AlbumLookupStatus.Results(proposals, failedProviders)
            AlbumSearchOutcome.NoProviders -> AlbumLookupStatus.NoProviders
            AlbumSearchOutcome.Offline -> AlbumLookupStatus.Offline
            AlbumSearchOutcome.RateLimited -> AlbumLookupStatus.RateLimited
            AlbumSearchOutcome.Failed -> AlbumLookupStatus.Failed
        }
}
