/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.library.api

import com.bobbyesp.metadator.core.model.ContentRef

/** Opens audio files for native code, which reads and writes through a file descriptor. */
interface AudioFileOpener {
    fun open(ref: ContentRef, mode: AccessMode): OpenResult
}

enum class AccessMode {
    Read,
    ReadWrite,
}

sealed interface OpenResult {
    data class Opened(val file: OpenedAudioFile) : OpenResult

    data object NotFound : OpenResult

    /** Writing needs the user's permission first ([WriteAccess]). */
    data object AccessDenied : OpenResult

    data class Failed(val message: String) : OpenResult
}

/** An open file. Close it when done; the descriptors it hands out are the caller's to close. */
interface OpenedAudioFile : AutoCloseable {
    /**
     * A new descriptor for the same file, detached from this object: native code takes ownership
     * and closes it.
     */
    fun detachDuplicateFd(): Int

    /** How long the file is, in bytes, or -1 when whoever serves it does not say. */
    val size: Long

    /**
     * Reads up to [length] bytes found at [position] of the file into [buffer], from [offset] on,
     * for code that parses the audio itself. Returns how many were read, and 0 or less at the end
     * of the file.
     *
     * @throws java.io.IOException when the file cannot be read at a position of one's choosing, as
     *   one served through a pipe cannot
     */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * Getting the user's permission to change files the app did not create. The prompt depends on the
 * Android version: a runtime permission up to 9, a per-file prompt on 10, and one prompt for any
 * number of files from 11.
 */
interface WriteAccess {
    /** Whether [refs] can be written now, without asking. */
    suspend fun canWrite(refs: List<ContentRef>): Boolean

    /** Asks for whatever is missing. Suspends while the system prompt is on screen. */
    suspend fun request(refs: List<ContentRef>): AccessResult
}

enum class AccessResult {
    Granted,
    Denied,
    /** No screen could show the prompt (the app is in the background). */
    Unavailable,
}

/** Makes the system index files again after they were written, so every app sees the new tags. */
interface MediaIndexer {
    suspend fun rescan(refs: List<ContentRef>)
}
