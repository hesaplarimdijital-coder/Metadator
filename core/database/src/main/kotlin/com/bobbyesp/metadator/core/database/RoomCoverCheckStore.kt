/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.database

import com.bobbyesp.metadator.core.domain.artwork.CoverCheck
import com.bobbyesp.metadator.core.domain.artwork.CoverCheckStore
import com.bobbyesp.metadator.core.model.TrackId

/** The library's cover checks, a row per song: a few hundred kilobytes for a large library. */
class RoomCoverCheckStore(private val dao: CoverChecksDao) : CoverCheckStore {

    override suspend fun load(): List<CoverCheck> =
        dao.all().map { row ->
            CoverCheck(
                id = TrackId(row.trackId),
                dateModifiedEpochSeconds = row.dateModified,
                sizeBytes = row.sizeBytes,
                hasCover = row.hasCover,
            )
        }

    override suspend fun save(checks: List<CoverCheck>) =
        dao.upsert(
            checks.map { check ->
                CoverCheckEntity(
                    trackId = check.id.value,
                    dateModified = check.dateModifiedEpochSeconds,
                    sizeBytes = check.sizeBytes,
                    hasCover = check.hasCover,
                )
            }
        )

    override suspend fun remove(ids: Collection<TrackId>) {
        // SQLite caps the variables of one statement at 999 on older Android versions.
        ids.map { it.value }.chunked(MAX_VARIABLES).forEach { dao.delete(it) }
    }

    private companion object {
        const val MAX_VARIABLES = 900
    }
}
