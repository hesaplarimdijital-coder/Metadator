/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.library

import com.bobbyesp.metadator.core.common.normalizedForComparison
import com.bobbyesp.metadator.core.domain.text.hasGarbledText
import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.SortOrder
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.model.TrackSort
import java.text.Collator
import java.util.Locale

/** What the library is narrowed to. */
data class LibraryFilter(
    val search: String = "",
    val needsAttention: Boolean = false,
    /** File extensions, lower case. Empty means every format. */
    val formats: Set<String> = emptySet(),
    /** Only the lossless files whose audio was found to have come from a lossy one. */
    val fakeLossless: Boolean = false,
    /** Only the songs on one volume; null for all of them. */
    val storage: Storage? = null,
    /**
     * Only the songs whose files hold no cover; albums, artists and folders whole, when any of
     * their songs has none ([lackingCover]).
     */
    val noCover: Boolean = false,
    /** Only the songs whose title, artist, album or album artist looks garbled. */
    val garbledText: Boolean = false,
) {
    val isActive: Boolean
        get() =
            search.isNotBlank() ||
                needsAttention ||
                formats.isNotEmpty() ||
                fakeLossless ||
                storage != null ||
                noCover ||
                garbledText
}

/** A volume songs are on: the device's own storage, or one that can be taken out. */
sealed interface Storage {
    data object Device : Storage

    /** An SD card or a USB drive, by what it is called ([Track.volume]). */
    data class Removable(val name: String) : Storage
}

/** The volume this song is on. */
val Track.storage: Storage
    get() = volume?.let { Storage.Removable(it) } ?: Storage.Device

/** How many songs each volume holds: the device's own first, then the others by name. */
fun List<Track>.countByStorage(): List<Pair<Storage, Int>> {
    val counts = groupingBy { it.storage }.eachCount()
    val device = counts[Storage.Device]
    val removable =
        counts
            .filterKeys { it is Storage.Removable }
            .toList()
            .sortedBy {
                (it.first as Storage.Removable).name
            }
    return listOfNotNull(device?.let { Storage.Device to it }) + removable
}

/**
 * The songs [filter] lets through. [isFake] says whether a song's audio was found to have come from
 * a lossy file, and [lacksCover] whether its file holds no cover: what the library knows of those
 * is not in the song itself. [isGarbled] is there for a caller that knows it already.
 */
fun List<Track>.filtered(
    filter: LibraryFilter,
    isFake: (Track) -> Boolean = { false },
    lacksCover: (Track) -> Boolean = { false },
    isGarbled: (Track) -> Boolean = { it.hasGarbledText },
): List<Track> {
    val terms = filter.search.normalizedForComparison().split(' ').filter { it.isNotEmpty() }
    return filter { track ->
        (!filter.needsAttention || track.needsAttention) &&
            (filter.formats.isEmpty() || track.fileExtension in filter.formats) &&
            (filter.storage == null || track.storage == filter.storage) &&
            (!filter.fakeLossless || isFake(track)) &&
            (!filter.noCover || lacksCover(track)) &&
            (!filter.garbledText || isGarbled(track)) &&
            (terms.isEmpty() || track.matches(terms))
    }
}

/**
 * The albums, artists or folders with a song without a cover, each whole: under "No cover" a
 * collection is listed with all of its songs, since a cover is found for an album, not for those of
 * its songs that lack one.
 */
fun <C : TrackCollection> List<C>.lackingCover(lacksCover: (Track) -> Boolean): List<C> = filter {
    it.tracks.any(lacksCover)
}

/** How many albums, artists or folders of [type] have a song without a cover. */
fun List<Track>.countLackingCover(type: CollectionType, lacksCover: (Track) -> Boolean): Int =
    asSequence().filter(lacksCover).mapNotNullTo(HashSet()) { it.collectionKey(type) }.size

/** Every term must appear in one of the fields: "beatles abbey" finds Come Together. */
private fun Track.matches(terms: List<String>): Boolean {
    val haystack =
        listOfNotNull(title, artist, album, albumArtist, displayName, genre)
            .joinToString(" ")
            .normalizedForComparison()
    return terms.all { it in haystack }
}

fun List<Track>.sorted(order: SortOrder, locale: Locale = Locale.getDefault()): List<Track> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val text =
        Comparator<String?> { a, b ->
            when {
                a.isNullOrBlank() && b.isNullOrBlank() -> 0
                // Missing values last, whichever the direction, so they never crowd the top.
                a.isNullOrBlank() -> if (order.ascending) 1 else -1
                b.isNullOrBlank() -> if (order.ascending) -1 else 1
                else -> collator.compare(a, b)
            }
        }
    val comparator: Comparator<Track> =
        when (order.sort) {
            TrackSort.Title -> compareBy(text) { it.title }
            TrackSort.Artist ->
                compareBy<Track, String?>(text) { it.artist }
                    .thenBy(text) { it.album }
                    .thenBy { it.discNumber ?: 0 }
                    .thenBy { it.trackNumber ?: 0 }
            TrackSort.Album ->
                compareBy<Track, String?>(text) { it.album }
                    .thenBy { it.discNumber ?: 0 }
                    .thenBy { it.trackNumber ?: 0 }
            TrackSort.DateAdded -> compareBy { it.dateAddedEpochSeconds }
            TrackSort.DateModified -> compareBy { it.dateModifiedEpochSeconds }
            TrackSort.Duration -> compareBy { it.durationMs }
        }
    return sortedWith(if (order.ascending) comparator else comparator.reversed())
}

/**
 * Albums by album artist and title, so two albums called "Greatest Hits" stay apart.
 *
 * Each is known by those two, which are what make it the album it is. MediaStore's id of an album
 * is not: it is one for the songs of a title in a folder, whoever they are by, and two albums under
 * one id had one key, which a list cannot show twice. A library with a compilation without album
 * artists closed as soon as its albums were all in sight, as on a wide window.
 */
fun List<Track>.albums(): List<TrackCollection.Album> = filter {
    !it.album.isNullOrBlank()
}
    .groupBy { (it.albumArtist ?: it.artist).orEmpty().lowercase() to it.album!!.lowercase() }
    .map { (identity, tracks) ->
        val (artist, album) = identity
        val ordered = tracks.sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
        val first = ordered.first()
        TrackCollection.Album(
            // The artist's length first: no artist and title can then be read as another pair.
            key = "album:${artist.length}:$artist:$album",
            title = first.album!!,
            artist = first.albumArtist ?: first.artist,
            year = ordered.firstNotNullOfOrNull { it.year },
            tracks = ordered,
        )
    }
    .sortedWith(compareBy(Collator.getInstance()) { it.title })

/** Artists by album artist when there is one, so features do not split an album's artist. */
fun List<Track>.artists(): List<TrackCollection.Artist> = filter {
    !(it.albumArtist ?: it.artist).isNullOrBlank()
}
    .groupBy { (it.albumArtist ?: it.artist)!!.trim() }
    .map { (name, tracks) ->
        TrackCollection.Artist(
            key = "artist:$name",
            title = name,
            tracks = tracks.sorted(SortOrder(TrackSort.Album)),
        )
    }
    .sortedWith(compareBy(Collator.getInstance()) { it.title })

/**
 * The collection known by [key] or, when none is any more, the one most of [songs] are in now. An
 * edit can give a collection another key (another artist for an album without an album artist,
 * another title, another name), and a screen that was showing it goes on showing those songs.
 */
fun <C : TrackCollection> List<C>.locate(key: String, songs: Collection<TrackId>): C? =
    firstOrNull { it.key == key }
        ?: songs.toSet().let { wanted ->
            map { it to it.tracks.count { track -> track.id in wanted } }
                .filter { (_, shared) -> shared > 0 }
                .maxByOrNull { (_, shared) -> shared }
                ?.first
        }

fun List<Track>.folders(): List<TrackCollection.Folder> = groupBy {
    it.folder.orEmpty()
}
    .map { (folder, tracks) ->
        val path = folder.trimEnd('/')
        TrackCollection.Folder(
            key = "folder:$folder",
            title = path.substringAfterLast('/').ifEmpty { folder.ifEmpty { "/" } },
            parent = path.substringBeforeLast('/', ""),
            tracks = tracks.sorted(SortOrder(TrackSort.Title)),
        )
    }
    .sortedWith(compareBy(Collator.getInstance()) { it.title })
