/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.domain.batch

import com.bobbyesp.metadator.core.common.normalizedForComparison
import com.bobbyesp.metadator.tags.api.TagField
import com.bobbyesp.metadator.tags.api.TagMap
import java.util.Locale

/**
 * An album songs of a batch are on, told apart as the library tells albums apart: by title and by
 * album artist, or artist where a song has no album artist. One album spelled two ways, or with an
 * album artist on some songs only, is two albums there, and two here.
 *
 * @param artists the album artists of its songs, or their artists where they have none
 * @param songs the songs on it, by their place in the batch
 * @param disc the disc its title says it is ("Discovery CD2"), when it says one
 * @param baseTitle the title without that
 */
data class BatchAlbum(
    val title: String,
    val artists: List<String>,
    val songs: List<Int>,
    val disc: Int? = null,
    val baseTitle: String = title,
)

/**
 * The albums the songs of a batch are on, and what making them one would make of them: one title
 * and one album artist for every song, which is what the library groups an album by.
 *
 * @param songsWithoutAlbum songs with no album at all, which a merge puts on the album too
 * @param title the title the merged album takes unless another is chosen: that of the album with
 *   the most songs, without its disc when the albums are the discs of one release
 * @param artists the album artists it takes unless others are chosen, from the same album
 * @param titleChoices the titles there are to choose from, [title] first
 * @param artistChoices the album artists there are to choose from, [artists] first
 * @param discs each song's disc, by its place in the batch, where the albums are the discs of one
 *   release and its album's title says which; empty when they are not
 */
data class AlbumMerge(
    val albums: List<BatchAlbum>,
    val songsWithoutAlbum: Int,
    val title: String,
    val artists: List<String>,
    val titleChoices: List<String>,
    val artistChoices: List<List<String>>,
    val discs: Map<Int, Int>,
) {
    companion object {
        /**
         * What there is to merge in [songs], given by their tags; null when they are on one album.
         */
        fun of(songs: List<TagMap>): AlbumMerge? {
            val albums = albumsOf(songs)
            if (albums.size < 2) return null
            val main = albums.first()
            val discs = discsOf(albums)
            val title = if (discs.isEmpty()) main.title else main.baseTitle
            val artistChoices =
                (listOf(main.artists) + albums.map { it.artists })
                    .filter { it.isNotEmpty() }
                    .distinctBy { artists -> artists.map { it.lowercase(Locale.ROOT) } }
            return AlbumMerge(
                albums = albums,
                songsWithoutAlbum = songs.count { it.album() == null },
                title = title,
                artists = artistChoices.firstOrNull().orEmpty(),
                titleChoices = (listOf(title) + albums.map { it.title }).distinct(),
                artistChoices = artistChoices,
                discs = discs,
            )
        }
    }
}

/**
 * The albums [songs] are on, the one with the most of them first. A song with no album is on none.
 * Each is called what most of its songs call it: the key ignores case, the title shown does not.
 */
fun albumsOf(songs: List<TagMap>): List<BatchAlbum> =
    songs
        .withIndex()
        .filter { it.value.album() != null }
        .groupBy { (_, tags) ->
            tags.albumArtists().joinToString(", ").lowercase(Locale.ROOT) to
                tags.album()!!.lowercase(Locale.ROOT)
        }
        .values
        .map { members ->
            val title = members.mapNotNull { it.value.album() }.mostCommon()
            val disc = DiscSuffix.of(title)
            BatchAlbum(
                title = title,
                artists = members.map { it.value.albumArtists() }.mostCommon(),
                songs = members.map { it.index },
                disc = disc?.number,
                baseTitle = disc?.base ?: title,
            )
        }
        .sortedByDescending { it.songs.size }

/**
 * Each song's disc, where the albums look like the discs of one release: titles that differ only by
 * a disc suffix ("Discovery CD1", "Discovery (Disc 2)"), and at least one that has one. A song
 * whose album's title has no suffix is on a disc nobody said, and is left out.
 */
private fun discsOf(albums: List<BatchAlbum>): Map<Int, Int> {
    if (albums.none { it.disc != null }) return emptyMap()
    val base = albums.first().baseTitle.normalizedForComparison()
    if (base.isEmpty() || albums.any { it.baseTitle.normalizedForComparison() != base }) {
        return emptyMap()
    }
    return albums
        .filter { it.disc != null }
        .flatMap { album -> album.songs.map { song -> song to album.disc!! } }
        .toMap()
}

/** A disc named at the end of an album's title: "CD1", "CD 2", "Disc 2", "(Disc 1)", "[CD 1/2]". */
internal data class DiscSuffix(val base: String, val number: Int) {
    companion object {
        // Escaped for Android's engine (ICU), which takes a bare "]" for the end of a class.
        private val Pattern =
            Regex(
                """^(.*?\S)[\s._,:;-]*[(\[]?\s*\b(?:cd|disc|disk|disco)\s*[-._#]?\s*(\d{1,2})""" +
                    """(?:\s*(?:/|of)\s*\d{1,2})?\s*[)\]]?$""",
                RegexOption.IGNORE_CASE,
            )

        fun of(title: String): DiscSuffix? {
            val match = Pattern.matchEntire(title.trim()) ?: return null
            val number = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return DiscSuffix(match.groupValues[1].trim(), number)
        }
    }
}

private fun TagMap.album(): String? = first(TagField.Album.key)?.trim()?.ifEmpty { null }

private fun TagMap.albumArtists(): List<String> =
    get(TagField.AlbumArtist.key).ifEmpty { get(TagField.Artist.key) }

/** The value given most often, the first of them on a tie. */
private fun <T> List<T>.mostCommon(): T = groupingBy { it }.eachCount().maxBy { it.value }.key
