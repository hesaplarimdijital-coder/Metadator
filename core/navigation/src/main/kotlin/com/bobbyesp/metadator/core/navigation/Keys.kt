/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.model.CollectionType
import kotlinx.serialization.Serializable

/*
 * Every destination of the app. They live together because features may not depend on each
 * other: a feature reaches another only through these keys. Keys carry ids, never models, so a
 * back stack restored after process death reloads fresh data.
 */

/** The library: songs, albums, artists and folders. The root of the stack. */
@Serializable data object Library : NavKey

/** The songs of an album, an artist or a folder. */
@Serializable
data class Collection(val type: CollectionType, val key: String, val title: String) : NavKey

/**
 * The tag editor for one file, by its `content://` URI. [then] is what it does once it has read the
 * file, when it was opened for that: a song's menu in the library finds its cover.
 */
@Serializable data class Editor(val uri: String, val then: EditorAction? = null) : NavKey

/** What the editor can be opened to do, beyond showing the song's tags. */
@Serializable
enum class EditorAction {
    FindCover
}

/** Editing several files at once. */
@Serializable data class BatchEditor(val uris: List<String>) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object AppearanceSettings : NavKey

@Serializable data object EditorSettings : NavKey

@Serializable data object LookupSettings : NavKey

@Serializable data object About : NavKey

/*
 * What the entries tell list-detail with their metadata, the navigator needs to keep the stack:
 * which destinations list songs and which are shown beside a list, in a wide window's inspector.
 */

/** Whether this lists songs, beside which a wide window shows what is chosen in it. */
internal val NavKey.isList: Boolean
    get() = this is Library || this is Collection

/** Whether this is shown beside a list: one song's editor, or the batch editor. */
internal val NavKey.isDetail: Boolean
    get() = this is Editor || this is BatchEditor
