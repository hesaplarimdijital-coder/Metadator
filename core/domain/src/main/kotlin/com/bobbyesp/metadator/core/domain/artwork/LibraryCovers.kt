/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.artwork

import com.bobbyesp.metadator.core.common.AppDispatchers
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.tags.api.TagReadResult
import com.bobbyesp.metadator.tags.api.TagReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Whether a song's file holds a cover, as found for one version of the file: its modification date
 * and size when it was read. [hasCover] is null where that could not be told: a file whose format
 * TagLib reads no tags of, or one that could not be opened.
 */
data class CoverCheck(
    val id: TrackId,
    val dateModifiedEpochSeconds: Long,
    val sizeBytes: Long,
    val hasCover: Boolean?,
)

/**
 * Where cover checks are kept between runs of the app: a library on an SD card takes minutes to
 * read, and each file only needs it once per version.
 */
interface CoverCheckStore {
    suspend fun load(): List<CoverCheck>

    suspend fun save(checks: List<CoverCheck>)

    suspend fun remove(ids: Collection<TrackId>)
}

/** What the check of the library's covers has found, and how far it has got. */
data class LibraryCoverState(
    /** The songs whose files hold no picture, of those checked. */
    val withoutCover: Set<TrackId> = emptySet(),
    /** Songs checked, of [total]: every song of the library the scanner was last given. */
    val checked: Int = 0,
    val total: Int = 0,
) {
    val isRunning: Boolean
        get() = checked < total

    fun lacksCover(track: Track): Boolean = track.id in withoutCover
}

/**
 * Finds the songs whose files hold no cover, in the background, so that the library can list them
 * without a song's row having to fail to show one first. A picture of any kind counts: the app
 * shows the first picture of a file without a front cover as its cover.
 *
 * Each file is read once per version of it, and what was found is kept ([CoverCheckStore]): a cover
 * saved to a song changes its file, which is then read again, before any song not read yet. It
 * works through the library it was last [submit]ted, one file at a time, and starts over from a new
 * one.
 *
 * It is the least urgent work on the files: it waits while [busy] says that work which matters more
 * is reading or writing them (the lossless check of the library, a batch being saved), and yields
 * between two files. The scope it runs in ends it.
 *
 * @param scope where the work runs: the app's, since it outlives any screen
 */
class LibraryCoverScanner(
    private val reader: TagReader,
    private val store: CoverCheckStore,
    private val dispatchers: AppDispatchers,
    private val scope: CoroutineScope,
    private val busy: Flow<Boolean> = flowOf(false),
) {
    private val library = MutableStateFlow<List<Track>?>(null)
    private val _state = MutableStateFlow(LibraryCoverState())
    val state: StateFlow<LibraryCoverState> = _state.asStateFlow()

    /** What is known by song: the checks read from the store, then those made since. */
    private val known = ConcurrentHashMap<TrackId, CoverCheck>()
    private var worker: Job? = null

    /** Takes the library as it is now. Starts the work the first time. */
    fun submit(tracks: List<Track>) {
        library.value = tracks
        synchronized(this) {
            if (worker == null) worker = scope.launch(dispatchers.default) { work() }
        }
    }

    private suspend fun work() {
        val stored = runCatching { withContext(dispatchers.io) { store.load() } }.getOrNull()
        stored?.forEach { known.putIfAbsent(it.id, it) }
        while (true) {
            val tracks = library.value ?: library.first { it != null }!!
            forget(tracks)
            publish(tracks)
            val pending = pending(tracks)
            if (pending.isEmpty()) {
                // All checked: wait for a library that differs.
                library.first { it !== tracks }
                continue
            }
            check(pending, tracks)
        }
    }

    /** The songs to read, those saved since they were first: a cover just added shows at once. */
    private fun pending(tracks: List<Track>): List<Track> {
        val (changed, unseen) =
            tracks.filter { checkOf(it) == null }.partition { known.containsKey(it.id) }
        return changed + unseen
    }

    private suspend fun check(pending: List<Track>, tracks: List<Track>) {
        val unsaved = mutableListOf<CoverCheck>()
        try {
            for (track in pending) {
                // A newer library is worked through from its start instead.
                if (library.value !== tracks) break
                busy.first { !it }
                val (check, lasting) = read(track)
                known[track.id] = check
                if (lasting) unsaved += check
                if (unsaved.size >= SAVE_EVERY) {
                    save(unsaved.toList())
                    unsaved.clear()
                    publish(tracks)
                }
                yield()
            }
        } finally {
            // What was found before the work was cancelled is not lost with it.
            if (unsaved.isNotEmpty()) withContext(NonCancellable) { save(unsaved) }
        }
        publish(tracks)
    }

    /**
     * What [track]'s file says, and whether that lasts: a file that could not be opened is only
     * remembered until the app starts again.
     */
    private suspend fun read(track: Track): Pair<CoverCheck, Boolean> {
        val result =
            try {
                reader.read(track.ref, includePictures = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // One file the reader chokes on must not stop the rest from being checked.
                TagReadResult.Failed(error.message.orEmpty())
            }
        val check =
            CoverCheck(
                id = track.id,
                dateModifiedEpochSeconds = track.dateModifiedEpochSeconds,
                sizeBytes = track.sizeBytes,
                hasCover = (result as? TagReadResult.Success)?.snapshot?.pictures?.isNotEmpty(),
            )
        return check to (result is TagReadResult.Success || result == TagReadResult.Unsupported)
    }

    private suspend fun save(checks: List<CoverCheck>) {
        runCatching { withContext(dispatchers.io) { store.save(checks) } }
    }

    private suspend fun forget(tracks: List<Track>) {
        // An empty library is more often a permission not granted yet than every song gone.
        if (tracks.isEmpty()) return
        val gone = known.keys - tracks.mapTo(HashSet()) { it.id }
        if (gone.isEmpty()) return
        gone.forEach(known::remove)
        runCatching { withContext(dispatchers.io) { store.remove(gone) } }
    }

    /** The check kept for [track], unless its file changed since: null when it needs reading. */
    private fun checkOf(track: Track): CoverCheck? =
        known[track.id]?.takeIf {
            it.dateModifiedEpochSeconds == track.dateModifiedEpochSeconds &&
                it.sizeBytes == track.sizeBytes
        }

    private fun publish(tracks: List<Track>) {
        var checked = 0
        val withoutCover = HashSet<TrackId>()
        for (track in tracks) {
            val check = checkOf(track) ?: continue
            checked++
            if (check.hasCover == false) withoutCover += track.id
        }
        _state.value = LibraryCoverState(withoutCover, checked = checked, total = tracks.size)
    }

    private companion object {
        /** Checks kept together, and shown together: one write and one new state for each. */
        const val SAVE_EVERY = 25
    }
}
