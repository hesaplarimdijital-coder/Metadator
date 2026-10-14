/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain

import com.bobbyesp.metadator.core.common.StringProvider
import com.bobbyesp.metadator.core.domain.settings.SettingsRepository
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.model.UserSettings
import com.bobbyesp.metadator.library.api.AccessResult
import com.bobbyesp.metadator.library.api.AudioFileInfo
import com.bobbyesp.metadator.library.api.AudioLibrary
import com.bobbyesp.metadator.library.api.CachedTrackTags
import com.bobbyesp.metadator.library.api.FileDescription
import com.bobbyesp.metadator.library.api.MediaIndexer
import com.bobbyesp.metadator.library.api.TrackTagCache
import com.bobbyesp.metadator.library.api.WriteAccess
import com.bobbyesp.metadator.tags.api.AudioProperties
import com.bobbyesp.metadator.tags.api.BackupId
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.TagBackup
import com.bobbyesp.metadator.tags.api.TagBackupStore
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagReadResult
import com.bobbyesp.metadator.tags.api.TagReader
import com.bobbyesp.metadator.tags.api.TagSnapshot
import com.bobbyesp.metadator.tags.api.TagWriteResult
import com.bobbyesp.metadator.tags.api.TagWriter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Files in memory, read and written like TagLib does: a write replaces the whole property map, and
 * a key the format does not support is silently dropped.
 */
class FakeTagFiles(
    initial: Map<ContentRef, TagSnapshot> = emptyMap(),
    private val unsupportedKeys: Set<String> = emptySet(),
) : TagReader, TagWriter {
    val files = initial.toMutableMap()
    var writeResult: TagWriteResult? = null
    var failNextWrites = 0
    val writes = mutableListOf<ContentRef>()
    val reads = mutableListOf<ContentRef>()

    override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult {
        reads += ref
        val snapshot = files[ref] ?: return TagReadResult.NotFound
        return TagReadResult.Success(
            if (includePictures) snapshot else snapshot.copy(pictures = emptyList())
        )
    }

    override suspend fun readAudioProperties(ref: ContentRef) = AudioProperties(1000, 320, 44100, 2)

    override suspend fun write(
        ref: ContentRef,
        tags: TagMap,
        pictures: List<EmbeddedPicture>?,
    ): TagWriteResult {
        writeResult?.let {
            return it
        }
        if (failNextWrites > 0) {
            failNextWrites--
            return TagWriteResult.Failed("disk on fire")
        }
        val current = files[ref] ?: return TagWriteResult.NotFound
        writes += ref
        val stored = TagMap.of(tags.toMap().filterKeys { it !in unsupportedKeys })
        files[ref] = TagSnapshot(stored, pictures ?: current.pictures)
        return TagWriteResult.Written
    }
}

/**
 * Backups in memory. Saving and pruning throw in a cancelled coroutine, as Room's do: they do their
 * work in `withContext`.
 */
class FakeBackupStore : TagBackupStore {
    val backups = mutableMapOf<BackupId, TagBackup>()
    /** What each [prune] was asked to keep, in order. */
    val prunes = mutableListOf<Int>()
    private var nextId = 1L

    override suspend fun save(
        ref: ContentRef,
        snapshot: TagSnapshot,
        createdAtMillis: Long,
    ): BackupId {
        currentCoroutineContext().ensureActive()
        val id = BackupId(nextId++)
        backups[id] = TagBackup(id, ref, snapshot, createdAtMillis)
        return id
    }

    override suspend fun load(id: BackupId) = backups[id]

    override suspend fun history(ref: ContentRef) = backups.values.filter { it.ref == ref }

    override suspend fun delete(id: BackupId) {
        backups.remove(id)
    }

    override suspend fun clear() = backups.clear()

    override suspend fun prune(keep: Int) {
        currentCoroutineContext().ensureActive()
        prunes += keep
        backups.keys.sortedByDescending { it.value }.drop(keep).forEach(backups::remove)
    }
}

class FakeIndexer : MediaIndexer {
    val rescanned = mutableListOf<ContentRef>()

    override suspend fun rescan(refs: List<ContentRef>) {
        rescanned += refs
    }
}

class FakeLibrary(initial: List<Track> = emptyList()) : AudioLibrary {
    val tracks = MutableStateFlow(initial)

    override fun observeTracks(): Flow<List<Track>> = tracks

    override suspend fun track(id: TrackId) = tracks.value.firstOrNull { it.id == id }

    override suspend fun findByRef(ref: ContentRef) = tracks.value.firstOrNull { it.ref == ref }

    override suspend fun refresh() = Unit
}

class FakeTrackTagCache(initial: List<CachedTrackTags> = emptyList()) : TrackTagCache {
    val entries = initial.associateByTo(mutableMapOf()) { it.id }

    override suspend fun all() = entries.values.toList()

    override suspend fun put(entries: List<CachedTrackTags>) {
        entries.forEach { this.entries[it.id] = it }
    }

    override suspend fun remove(ids: Collection<TrackId>) {
        ids.forEach(entries::remove)
    }
}

class FakeSettings(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        settings.update(transform)
    }
}

/** What a file is, as the system says: [files] by their names, the rest unknown. */
class FakeFileInfo(private val files: Map<ContentRef, String> = emptyMap()) : AudioFileInfo {
    override suspend fun describe(ref: ContentRef) =
        files[ref]?.let { FileDescription(it, null, null, null, null) }
}

/**
 * Write access as Android grants it: none at first, then whatever [answer] says once asked. Records
 * each time it was asked, as the system's prompt would show.
 */
class FakeWriteAccess(
    var granted: Boolean = true,
    var answer: AccessResult = AccessResult.Granted,
) : WriteAccess {
    val requests = mutableListOf<List<ContentRef>>()

    override suspend fun canWrite(refs: List<ContentRef>) = granted

    override suspend fun request(refs: List<ContentRef>): AccessResult {
        requests += refs
        if (answer == AccessResult.Granted) granted = true
        return answer
    }
}

/**
 * Strings by their resource ids, for a test to say which one was shown without Android's resources:
 * [get] of an id is the same text every time, and different for another id.
 */
class FakeStrings : StringProvider {
    override fun get(id: Int, vararg args: Any) =
        if (args.isEmpty()) "#$id" else "#$id ${args.joinToString(", ")}"

    override fun plural(id: Int, quantity: Int, vararg args: Any) =
        "#$id[$quantity] ${args.joinToString(", ")}".trimEnd()
}
