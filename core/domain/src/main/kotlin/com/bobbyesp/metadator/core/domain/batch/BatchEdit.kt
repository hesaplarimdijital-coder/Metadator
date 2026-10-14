/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.core.domain.editor.Position
import com.bobbyesp.metadator.core.domain.editor.PositionStyle
import com.bobbyesp.metadator.core.domain.editor.TrackPositions
import com.bobbyesp.metadator.core.domain.save.SaveOutcome
import com.bobbyesp.metadator.core.domain.save.SaveTagChangesUseCase
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.tags.api.ArtworkChange
import com.bobbyesp.metadator.tags.api.BackupId
import com.bobbyesp.metadator.tags.api.TagChanges
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import com.bobbyesp.metadator.tags.api.applying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A file in a batch: where it is, and what it is called (for patterns and the report). */
data class BatchItem(val ref: ContentRef, val displayName: String) {
    val extension: String?
        get() = displayName.substringAfterLast('.', "").lowercase().ifEmpty { null }
}

/** One edit applied to every file of a batch. */
data class BatchEdit(
    /** Set on every file. An empty list removes the field. */
    val fields: Map<String, List<String>> = emptyMap(),
    val artwork: ArtworkChange = ArtworkChange.Unchanged,
    /** Numbers the files in the order given. */
    val numbering: TrackNumbering? = null,
    /** Reads fields from each file's name. Applied before [fields], which win. */
    val fileNamePattern: FileNamePattern? = null,
    /**
     * Track and disc positions for some of the files, each its own: where a lookup found a song on
     * its album, or the disc a merged album's name said. Applied after [fields]; [numbering] still
     * numbers every track.
     */
    val positions: Map<ContentRef, FilePositions> = emptyMap(),
) {
    val isEmpty: Boolean
        get() =
            fields.isEmpty() &&
                artwork == ArtworkChange.Unchanged &&
                numbering == null &&
                fileNamePattern == null &&
                positions.values.all { it.isEmpty }
}

data class TrackNumbering(val startAt: Int = 1, val writeTotal: Boolean = true)

/**
 * A position to set, as numbers. What is null stays as the file has it: an album whose track total
 * is known, but not where a song is on it, sets only the total.
 */
data class PositionEdit(val number: Int? = null, val total: Int? = null) {
    val isEmpty: Boolean
        get() = number == null && total == null
}

/** The track and disc positions to set in one file. */
data class FilePositions(val track: PositionEdit? = null, val disc: PositionEdit? = null) {
    val isEmpty: Boolean
        get() = track?.isEmpty != false && disc?.isEmpty != false

    /** These, with what [other] sets in their place: a lookup after a merge keeps its discs. */
    operator fun plus(other: FilePositions): FilePositions =
        FilePositions(track = other.track ?: track, disc = other.disc ?: disc)
}

/** The changes [edit] makes to the file at [index] of [count], given its current tags. */
fun BatchEdit.changesFor(
    item: BatchItem,
    index: Int,
    count: Int,
    snapshot: TagSnapshot,
): TagChanges {
    val fields = linkedMapOf<String, List<String>>()
    fileNamePattern?.parse(item.displayName)?.let { fields += it }
    fields += this.fields
    positions[item.ref]?.let { position ->
        val style = TrackPositions.detectStyle(snapshot.tags, item.extension)
        // From the tags as edited so far: a total set here keeps a number read from the file name.
        position.track?.let {
            fields += snapshot.tags.applying(fields).set(TagField.TrackNumber, it, style)
        }
        position.disc?.let {
            fields += snapshot.tags.applying(fields).set(TagField.DiscNumber, it, style)
        }
    }
    numbering?.let { numbering ->
        val style = TrackPositions.detectStyle(snapshot.tags, item.extension)
        val current = TrackPositions.read(snapshot.tags, TagField.TrackNumber)
        fields +=
            TrackPositions.write(
                snapshot.tags,
                TagField.TrackNumber,
                Position(
                    number = (numbering.startAt + index).toString(),
                    total =
                        if (numbering.writeTotal) (numbering.startAt + count - 1).toString()
                        else current.total,
                ),
                style,
            )
    }
    return TagChanges(fields = fields, artwork = artwork)
}

/** The values that store [edit] in [field], what it leaves null kept as these tags have it. */
private fun TagMap.set(
    field: TagField,
    edit: PositionEdit,
    style: PositionStyle,
): Map<String, List<String>> {
    if (edit.isEmpty) return emptyMap()
    val current = TrackPositions.read(this, field)
    return TrackPositions.write(
        this,
        field,
        Position(
            number = edit.number?.toString() ?: current.number,
            total = edit.total?.toString() ?: current.total,
        ),
        style,
    )
}

data class BatchItemResult(val item: BatchItem, val outcome: SaveOutcome)

data class BatchProgress(
    val total: Int,
    val results: List<BatchItemResult> = emptyList(),
    val isRunning: Boolean = true,
    val wasCancelled: Boolean = false,
) {
    val done: Int
        get() = results.size

    val fraction: Float
        get() = if (total == 0) 1f else done.toFloat() / total

    val saved: List<BatchItemResult>
        get() = results.filter { it.outcome is SaveOutcome.Saved }

    val failed: List<BatchItemResult>
        get() = results.filter {
            it.outcome !is SaveOutcome.Saved && it.outcome != SaveOutcome.NothingToSave
        }

    val backups: List<BackupId>
        get() = results.mapNotNull { (it.outcome as? SaveOutcome.Saved)?.backupId }

    /**
     * The keys that were written but that some file's format could not keep, with how many files
     * dropped each: saved all the same, and silent unless said.
     */
    val notStored: Map<String, Int>
        get() =
            results
                .flatMap { (it.outcome as? SaveOutcome.Saved)?.notStored.orEmpty() }
                .groupingBy { it }
                .eachCount()
}

/**
 * Runs batch edits in the application's scope, so leaving the screen does not stop one halfway. One
 * batch at a time; its progress is observable until the next one starts.
 */
class BatchRunner(
    private val scope: CoroutineScope,
    private val save: SaveTagChangesUseCase,
) {
    private val _progress = MutableStateFlow<BatchProgress?>(null)
    val progress: StateFlow<BatchProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    /** [finalize] adjusts each file's changes just before writing, e.g. for multi-value mode. */
    fun start(
        items: List<BatchItem>,
        edit: BatchEdit,
        finalize: (TagChanges) -> TagChanges = { it },
    ) {
        if (isRunning) return
        _progress.value = BatchProgress(total = items.size)
        job = scope.launch {
            items.forEachIndexed { index, item ->
                if (!isActive) return@forEachIndexed
                val outcome =
                    save(item.ref) { snapshot ->
                        finalize(edit.changesFor(item, index, items.size, snapshot))
                    }
                _progress.update { it?.copy(results = it.results + BatchItemResult(item, outcome)) }
            }
            _progress.update { it?.copy(isRunning = false) }
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.update { it?.copy(isRunning = false, wasCancelled = true) }
    }

    fun clear() {
        if (!isRunning) _progress.value = null
    }
}
