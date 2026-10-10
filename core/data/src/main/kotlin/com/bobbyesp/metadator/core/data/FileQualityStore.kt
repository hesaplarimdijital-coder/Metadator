/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.data

import com.bobbyesp.metadator.core.domain.quality.LosslessVerdict
import com.bobbyesp.metadator.core.domain.quality.QualityStore
import com.bobbyesp.metadator.core.domain.quality.StoredVerdict
import java.io.File
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the lossless check's verdicts in a file of the app's own, a line per song: a library's
 * worth is a few hundred kilobytes, read once each time the app starts checking.
 *
 * Each verdict is added at the end, so that one made a second before the app is closed is not lost
 * with the rest; the last line for a song is the one that counts. When most of the file is lines
 * that no longer count, it is written again without them.
 */
class FileQualityStore(private val file: File) : QualityStore {
    private val lock = Mutex()

    override suspend fun load(): List<StoredVerdict> = lock.withLock {
        if (!file.exists()) return emptyList()
        val byUri = LinkedHashMap<String, StoredVerdict>()
        var lines = 0
        try {
            file.forEachLine { line ->
                lines++
                parse(line)?.let { byUri[it.uri] = it }
            }
        } catch (_: IOException) {
            return emptyList()
        }
        if (lines > byUri.size * 2 + COMPACT_SLACK) rewrite(byUri.values)
        byUri.values.toList()
    }

    override suspend fun save(verdict: StoredVerdict) {
        lock.withLock {
            file.parentFile?.mkdirs()
            file.appendText(format(verdict) + "\n")
        }
    }

    private fun rewrite(verdicts: Collection<StoredVerdict>) {
        val fresh = File(file.parentFile, file.name + ".new")
        runCatching {
            fresh.writeText(verdicts.joinToString(separator = "") { format(it) + "\n" })
            if (!fresh.renameTo(file)) fresh.delete()
        }
    }

    private fun format(verdict: StoredVerdict): String =
        listOf(
                verdict.uri,
                verdict.sizeBytes.toString(),
                verdict.modifiedEpochSeconds.toString(),
                verdict.verdict?.name ?: UNREADABLE,
            )
            .joinToString("\t")

    /** A line as [format] wrote it; null for one written by a version that judged otherwise. */
    private fun parse(line: String): StoredVerdict? {
        val parts = line.split('\t')
        if (parts.size != 4) return null
        val verdict =
            if (parts[3] == UNREADABLE) null
            else LosslessVerdict.entries.firstOrNull { it.name == parts[3] } ?: return null
        return StoredVerdict(
            uri = parts[0],
            sizeBytes = parts[1].toLongOrNull() ?: return null,
            modifiedEpochSeconds = parts[2].toLongOrNull() ?: return null,
            verdict = verdict,
        )
    }

    private companion object {
        const val UNREADABLE = "-"

        /** Superseded lines tolerated before the file is written again. */
        const val COMPACT_SLACK = 200
    }
}
