/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.save

/**
 * Keeps a batch's backups from the pruning of the saves made beside it, from the batch's start
 * until its result is dismissed: the editor beside the batch editor, or another app's song open in
 * a window, saving meanwhile would otherwise drop the batch's oldest backups, and "Undo all" could
 * not restore them. One for the app, shared by every [SaveTagChangesUseCase] and the batch runner.
 *
 * While it holds, it counts the backups begun, the batch's and every other save's, and a prune
 * keeps at least as many of the newest: all of them. Saves run on other threads than the batch,
 * hence the locks.
 */
class BackupHold {
    private var holding = false
    private var begun = 0

    /** Already holding, it goes on counting: what it held stays held. */
    @Synchronized
    internal fun hold() {
        if (holding) return
        holding = true
        begun = 0
    }

    @Synchronized
    internal fun release() {
        holding = false
        begun = 0
    }

    /** Before each backup: a save cancelled while it backs up may still leave one. */
    @Synchronized
    internal fun backingUp() {
        if (holding) begun++
    }

    /** How many of the newest backups a prune keeps: [keep], and every one begun while holding. */
    @Synchronized internal fun keeping(keep: Int): Int = if (holding) maxOf(keep, begun) else keep
}
