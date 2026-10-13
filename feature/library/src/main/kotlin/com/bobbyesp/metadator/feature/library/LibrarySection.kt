/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.PlaceholderCard
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.domain.library.locate
import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackId
import com.bobbyesp.metadator.core.navigation.BatchEditor
import com.bobbyesp.metadator.core.navigation.Collection
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.EditorAction
import com.bobbyesp.metadator.core.navigation.Library
import com.bobbyesp.metadator.core.navigation.Navigator
import com.bobbyesp.metadator.core.navigation.Settings
import com.bobbyesp.metadator.core.ui.viewmodel.ShowMessages
import com.bobbyesp.metadator.core.ui.viewmodel.rememberIntents
import com.bobbyesp.metadator.player.api.PlayerController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** The library's destinations: the root list and the collections reached from it. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Library>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSongOpen() })
    ) {
        val viewModel: LibraryViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ShowMessages(viewModel.messages)
        val openFile = rememberOpenFileLauncher { uri ->
            navigator.showDetail(Editor(uri.toString()))
        }
        val openInNewWindow = rememberNewWindowOpener()

        LibraryScreen(
            state = state,
            onIntent = rememberIntents(viewModel),
            onOpenTrack = { navigator.showDetail(Editor(it.ref.uri)) },
            onFindCover = {
                navigator.showDetail(Editor(it.ref.uri, then = EditorAction.FindCover))
            },
            onOpenCollection = { collection ->
                navigator.goTo(Collection(collection.type, collection.key, collection.title))
            },
            // Beside the library on a wide window, where the selection stays in sight.
            onEditTracks = { tracks ->
                navigator.showDetail(BatchEditor(tracks.map { it.ref.uri }))
            },
            onOpenInNewWindow = openInNewWindow,
            onOpenSettings = { navigator.goTo(Settings) },
            onOpenFile = { openFile.launch(arrayOf("audio/*")) },
        )
    }

    entry<Collection>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSongOpen() })
    ) { key ->
        // The library's ViewModel lives in the Library entry; a collection reads the same data
        // through its own, keyed by what it shows.
        val viewModel: LibraryViewModel = koinViewModel(key = "collection:${key.key}")
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.onIntent(LibraryIntent.Start) }
        val player: PlayerController = koinInject()
        // What plays and whether it does, not where it is: that changes twice a second.
        val playback = player.state.collectAsStateWithLifecycle()
        val playingTrack by remember { derivedStateOf { playback.value.current } }
        val isPlaybackRunning by remember { derivedStateOf { playback.value.isPlaying } }
        val openInNewWindow = rememberNewWindowOpener()

        // Found again by the songs it showed once an edit has given it another key: the artist of
        // an album without an album artist changed, say, from the button that edits them all.
        val shown = remember { ShownSongs() }
        val collection =
            remember(state, key) {
                when (key.type) {
                    CollectionType.Album -> state.albums
                    CollectionType.Artist -> state.artists
                    CollectionType.Folder -> state.folders
                }.locate(key.key, shown.ids)
            }
        SideEffect { collection?.let { found -> shown.ids = found.tracks.map { it.id } } }
        // None of its songs left where they were (moved by another app, say): it goes, rather than
        // stay a screen that loads forever.
        val gone = !state.loading && collection == null
        LaunchedEffect(gone) { if (gone) navigator.removeDestination(key) }

        CollectionScreen(
            title = collection?.title ?: key.title,
            collection = collection.takeUnless { state.loading },
            quality = state.quality,
            playingTrack = playingTrack,
            isPlaybackRunning = isPlaybackRunning,
            showBack = true,
            onBack = navigator::goBack,
            onOpenTrack = { navigator.showDetail(Editor(it.ref.uri)) },
            onOpenInNewWindow = openInNewWindow,
            onPlay = { tracks, index, shuffle -> player.play(tracks, index, shuffle) },
            onEditAll = { tracks: List<Track> ->
                navigator.showDetail(BatchEditor(tracks.map { it.ref.uri }))
            },
        )
    }
}

/**
 * The songs a collection's screen showed last. Nothing is drawn from them, so they are not state.
 */
private class ShownSongs {
    var ids: List<TrackId> = emptyList()
}

internal val TrackCollection.type: CollectionType
    get() =
        when (this) {
            is TrackCollection.Album -> CollectionType.Album
            is TrackCollection.Artist -> CollectionType.Artist
            is TrackCollection.Folder -> CollectionType.Folder
        }

/**
 * The system's file picker for one audio file. The grant is made persistable, so reopening the
 * editor after the app restarts can still read (and, if the provider allows, write) the file.
 */
@Composable
private fun rememberOpenFileLauncher(onPicked: (Uri) -> Unit) =
    LocalContext.current.let { context ->
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val resolver = context.contentResolver
            // Read and write when the provider grants both, read alone when it only grants that.
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
                .onFailure {
                    runCatching {
                        resolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            onPicked(uri)
        }
    }

/**
 * Opens a song's editor in a window of its own, beside this one where windows can be: two songs
 * side by side on a desktop. It is the editor other apps open with "Edit with", asked for by this
 * app; a new one for each song, so that a second song does not take the first one's window.
 */
@Composable
private fun rememberNewWindowOpener(): (Track) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { track ->
            val intent =
                Intent(Intent.ACTION_EDIT)
                    .setDataAndType(track.ref.uri.toUri(), "audio/*")
                    .setPackage(context.packageName)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                    )
            // Nothing to fall back to: the song still opens here with a click.
            runCatching { context.startActivity(intent) }
        }
    }
}

/**
 * What a wide window shows beside the library while no song is open: the inspector's panel, saying
 * what will be there.
 */
@Composable
private fun NoSongOpen() {
    Box(
        Modifier.fillMaxSize()
            .then(if (LocalPanels.current) Modifier.studioPanel() else Modifier)
            .padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        PlaceholderCard(
            title = stringResource(R.string.no_song_open_title),
            description = stringResource(R.string.no_song_open_description),
            icon = Icons.Rounded.EditNote,
        )
    }
}
