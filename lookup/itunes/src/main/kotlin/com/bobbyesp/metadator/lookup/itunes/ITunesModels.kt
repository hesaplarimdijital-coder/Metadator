/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.lookup.itunes

import kotlinx.serialization.Serializable

@Serializable internal data class ITunesSearch(val results: List<ITunesTrack> = emptyList())

/** One result of a search. Only songs carry every field; other kinds are filtered out. */
@Serializable
internal data class ITunesTrack(
    val kind: String? = null,
    val trackId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val collectionArtistName: String? = null,
    val artworkUrl100: String? = null,
    val releaseDate: String? = null,
    val trackNumber: Int? = null,
    val trackCount: Int? = null,
    val discNumber: Int? = null,
    val discCount: Int? = null,
    val trackTimeMillis: Long? = null,
    val primaryGenreName: String? = null,
)
