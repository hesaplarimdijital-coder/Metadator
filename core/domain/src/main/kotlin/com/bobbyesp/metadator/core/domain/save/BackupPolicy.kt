/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.save

/**
 * How many backups are kept for undo. A save drops the oldest beyond [KEEP] once it has backed up
 * its file. A batch drops them only after its last song, and never one of its own, so "Undo all"
 * can restore every song of a batch of any size; the next save prunes back to [KEEP].
 */
object BackupPolicy {
    const val KEEP = 100
}
