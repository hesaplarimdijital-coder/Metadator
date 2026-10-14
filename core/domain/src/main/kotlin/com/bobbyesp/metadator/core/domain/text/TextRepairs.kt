/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.text

import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackField
import com.bobbyesp.metadator.tags.api.FieldKind
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap

/** The keys whose text is looked at for garbling: every field of text the editor names. */
val TextFieldKeys: List<String> =
    TagField.entries
        .filter { it.kind == FieldKind.Text || it.kind == FieldKind.LongText }
        .map { it.key }

/** Which of the fields a song is listed by (title, artist, album, album artist) look garbled. */
val Track.garbledFields: Set<TrackField>
    get() = buildSet {
        if (title.looksGarbled()) add(TrackField.Title)
        if (artist.looksGarbled()) add(TrackField.Artist)
        if (album.looksGarbled()) add(TrackField.Album)
        if (albumArtist.looksGarbled()) add(TrackField.AlbumArtist)
    }

/** Whether any field a song is listed by looks garbled, repairable or not. */
val Track.hasGarbledText: Boolean
    get() =
        title.looksGarbled() ||
            artist.looksGarbled() ||
            album.looksGarbled() ||
            albumArtist.looksGarbled()

private fun String?.looksGarbled(): Boolean = this != null && GarbledText.inspect(this) != null

/**
 * What to write where these tags hold text garbled in a way the text itself can undo: by key, every
 * value of the key, each garbled one repaired. Empty when there is nothing to repair. The editor
 * offers it as changes to save, and it is what a batch writes to many files at once.
 */
fun TagMap.textRepairs(keys: Collection<String> = TextFieldKeys): Map<String, List<String>> =
    buildMap {
        for (key in keys) {
            val values = this@textRepairs[key]
            val repaired = values.map { GarbledText.repair(it) ?: it }
            if (repaired != values) put(key, repaired)
        }
    }
