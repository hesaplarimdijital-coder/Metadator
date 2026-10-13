/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.editor

import com.bobbyesp.metadator.tags.api.ArtworkChange
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.TagChanges
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagSnapshot
import com.bobbyesp.metadator.tags.api.diff

/**
 * The editor's working copy of a file's tags: what was read, and what the user has made of it. The
 * changes to save are always the difference between the two, never the whole form.
 */
data class TagDraft(
    val original: TagSnapshot,
    val tags: TagMap = original.tags,
    val artwork: ArtworkChange = ArtworkChange.Unchanged,
) {
    val changes: TagChanges
        get() = TagChanges(fields = diff(original.tags, tags), artwork = artwork)

    val isDirty: Boolean
        get() = !changes.isEmpty

    val changedKeys: Set<String>
        get() = changes.changedKeys

    /** The cover as it will be after saving. */
    val cover: EmbeddedPicture?
        get() =
            when (val change = artwork) {
                ArtworkChange.Unchanged -> original.frontCover
                ArtworkChange.Remove -> null
                is ArtworkChange.Replace -> change.picture
            }

    val isCoverChanged: Boolean
        get() = artwork != ArtworkChange.Unchanged

    fun isChanged(key: String): Boolean = original.tags[key] != tags[key]

    fun set(key: String, values: List<String>): TagDraft = copy(tags = tags.with(key, values))

    fun setAll(values: Map<String, List<String>>): TagDraft =
        copy(tags = values.entries.fold(tags) { map, (key, value) -> map.with(key, value) })

    fun revert(key: String): TagDraft = copy(tags = tags.with(key, original.tags[key]))

    /**
     * The picture the file already has is no change: the cover finder puts it first when a source
     * has it too, and counting it left a change that saving had nothing to write for.
     */
    fun replaceCover(picture: EmbeddedPicture): TagDraft =
        copy(
            artwork =
                if (picture.isSameImage(original.frontCover)) ArtworkChange.Unchanged
                else ArtworkChange.Replace(picture)
        )

    fun removeCover(): TagDraft =
        copy(
            artwork =
                if (original.frontCover == null) ArtworkChange.Unchanged else ArtworkChange.Remove
        )

    fun revertCover(): TagDraft = copy(artwork = ArtworkChange.Unchanged)

    fun revertAll(): TagDraft = TagDraft(original)
}

/** The same picture, whatever its type or description says: what a viewer would see. */
private fun EmbeddedPicture.isSameImage(other: EmbeddedPicture?): Boolean =
    other != null && mimeType == other.mimeType && data.contentEquals(other.data)
