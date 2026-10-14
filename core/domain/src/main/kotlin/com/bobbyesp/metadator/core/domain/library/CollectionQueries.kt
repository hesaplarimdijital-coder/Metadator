/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId

/**
 * The key of the album, artist or folder of [type] this song is listed under, as [albums],
 * [artists] and [folders] make it; null when it is under none, as a song without an album's name is
 * in no album.
 */
fun Track.collectionKey(type: CollectionType): String? =
    when (type) {
        CollectionType.Album ->
            album
                ?.takeIf { it.isNotBlank() }
                ?.let { title ->
                    val artist = (albumArtist ?: artist).orEmpty().lowercase()
                    "album:${artist.length}:$artist:${title.lowercase()}"
                }
        CollectionType.Artist ->
            (albumArtist ?: artist)?.takeIf { it.isNotBlank() }?.let { "artist:${it.trim()}" }
        CollectionType.Folder -> "folder:${folder.orEmpty()}"
    }

/**
 * The album, artist or folder known by [key], made of its own songs and nothing else: a
 * collection's screen needs those, not the whole library grouped every way to find them. Its songs
 * are in the order the library's own lists give them, an album's by disc and track.
 *
 * When no song has [key] any more, it is the one most of [shown] are in now, as [locate] finds it:
 * an edit can give a collection another key (another artist for an album without an album artist,
 * another title), and a screen that was showing it goes on showing those songs.
 */
fun List<Track>.collection(
    type: CollectionType,
    key: String,
    shown: Collection<TrackId> = emptyList(),
): TrackCollection? {
    val keys = map { it.collectionKey(type) }
    val found =
        key.takeIf { it in keys }
            ?: shown.toSet().let { wanted ->
                indices
                    .filter { this[it].id in wanted }
                    .mapNotNull { keys[it] }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            }
            ?: return null
    val songs = filterIndexed { index, _ -> keys[index] == found }
    // Made as the library's lists make it, from its songs alone: the same title, cover and order.
    val made: List<TrackCollection> =
        when (type) {
            CollectionType.Album -> songs.albums()
            CollectionType.Artist -> songs.artists()
            CollectionType.Folder -> songs.folders()
        }
    return made.firstOrNull { it.key == found }
}
