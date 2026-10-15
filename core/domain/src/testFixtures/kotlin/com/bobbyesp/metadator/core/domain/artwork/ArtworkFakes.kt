/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.artwork

import com.bobbyesp.metadator.core.model.TrackId

/** The checks of covers kept in memory, by song. */
class MemoryCoverCheckStore(initial: List<CoverCheck> = emptyList()) : CoverCheckStore {
    val checks = initial.associateByTo(mutableMapOf()) { it.id }

    override suspend fun load(): List<CoverCheck> = checks.values.toList()

    override suspend fun save(checks: List<CoverCheck>) {
        checks.forEach { this.checks[it.id] = it }
    }

    override suspend fun remove(ids: Collection<TrackId>) {
        ids.forEach(checks::remove)
    }
}
