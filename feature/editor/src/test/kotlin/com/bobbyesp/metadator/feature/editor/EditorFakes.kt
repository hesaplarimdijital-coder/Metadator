/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor

import com.bobbyesp.metadator.core.domain.FakeTagFiles
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.ui.image.ImageInfo
import com.bobbyesp.metadator.core.ui.image.ImageSource
import com.bobbyesp.metadator.core.ui.image.PickedImage
import com.bobbyesp.metadator.lyrics.api.LyricsProvider
import com.bobbyesp.metadator.lyrics.api.LyricsQuery
import com.bobbyesp.metadator.lyrics.api.LyricsResult
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController
import com.bobbyesp.metadator.player.api.RepeatMode
import com.bobbyesp.metadator.tags.api.EmbeddedPicture
import com.bobbyesp.metadator.tags.api.TagMap
import com.bobbyesp.metadator.tags.api.TagWriteResult
import com.bobbyesp.metadator.tags.api.TagWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Pictures picked by URI from [picked]; any picture measured, unless [failure] is set. */
internal class FakeImageSource : ImageSource {
    val picked = mutableMapOf<String, PickedImage>()
    var failure: Throwable? = null

    override suspend fun read(uri: String): PickedImage? = picked[uri]

    override suspend fun inspect(bytes: ByteArray): ImageInfo {
        failure?.let { throw it }
        return ImageInfo(width = 1000, height = 1000, sizeBytes = bytes.size, dominantColor = null)
    }
}

/** A player that plays nothing. */
internal class FakePlayer : PlayerController {
    override val state = MutableStateFlow(PlaybackState())

    override fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean) = Unit

    override fun togglePlayPause() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun skipToNext() = Unit

    override fun skipToPrevious() = Unit

    override fun skipToQueueItem(index: Int) = Unit

    override fun setShuffle(enabled: Boolean) = Unit

    override fun setRepeatMode(mode: RepeatMode) = Unit

    override fun stop() = Unit
}

/** Finds no lyrics, and keeps what it was asked for. */
internal class FakeLyrics : LyricsProvider {
    val queries = mutableListOf<LyricsQuery>()

    override val displayName = "Fake"

    override suspend fun find(query: LyricsQuery): LyricsResult {
        queries += query
        return LyricsResult.NotFound
    }
}

/**
 * Writes to [files]; once [hold] is called, each write waits for [release], as a slow card does.
 */
internal class SlowWriter(private val files: FakeTagFiles) : TagWriter {
    private var gate: CompletableDeferred<Unit>? = null

    fun hold() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
    }

    override suspend fun write(
        ref: ContentRef,
        tags: TagMap,
        pictures: List<EmbeddedPicture>?,
    ): TagWriteResult {
        gate?.await()
        return files.write(ref, tags, pictures)
    }
}
