/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.PlaceholderCard
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.model.CollectionType
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.navigation.BatchEditor
import com.bobbyesp.metadator.core.navigation.Collection
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.EditorAction
import com.bobbyesp.metadator.core.navigation.Library
import com.bobbyesp.metadator.core.navigation.Navigator
import com.bobbyesp.metadator.core.navigation.Settings
import com.bobbyesp.metadator.core.navigation.pane.LocalPaneContext
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import com.bobbyesp.metadator.core.ui.viewmodel.ShowMessages
import com.bobbyesp.metadator.core.ui.viewmodel.rememberIntents
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.koin.androidx.compose.koinViewModel

/** The library's destinations: the root list and the collections reached from it. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    // What each list has selected beyond what its inspector follows: the placeholder beside the
    // list says it, and is drawn apart from the list's entry.
    val libraryUnfollowed = UnfollowedSelection()
    val collectionUnfollowed = UnfollowedSelection()
    val editTogether = { tracks: List<Track> ->
        navigator.showDetail(BatchEditor(tracks.map { it.id.value }))
    }
    entry<Library>(
        metadata =
            ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSongOpen(libraryUnfollowed) })
    ) {
        val viewModel: LibraryViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ShowMessages(viewModel.messages)
        val openFile = rememberOpenFileLauncher { uri ->
            navigator.showDetail(Editor(uri.toString()))
        }
        val openInNewWindow = rememberNewWindowOpener()
        InspectorFollowsSelection(state, navigator, libraryUnfollowed, editTogether)

        LibraryScreen(
            state = state,
            onIntent = rememberIntents(viewModel),
            onOpenTrack = { track ->
                viewModel.onIntent(LibraryIntent.Opened(track.id))
                navigator.showDetail(Editor(track.ref.uri))
            },
            onFindCover = {
                navigator.showDetail(Editor(it.ref.uri, then = EditorAction.FindCover))
            },
            onOpenCollection = { collection ->
                navigator.goTo(Collection(collection.type, collection.key, collection.title))
            },
            // Beside the library on a wide window, where the selection stays in sight.
            onEditTracks = editTogether,
            onOpenInNewWindow = openInNewWindow,
            onOpenSettings = { navigator.goTo(Settings) },
            onOpenFile = { openFile.launch(arrayOf("audio/*")) },
        )
    }

    entry<Collection>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = { NoSongOpen(collectionUnfollowed) }
            )
    ) { key ->
        // The library narrowed to the collection, keyed by what it shows: its songs are what is
        // selected, taken in a range and played, and it is found again by them once an edit has
        // given it another key.
        val viewModel: LibraryViewModel = koinViewModel(key = "collection:${key.key}")
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(key) { viewModel.onIntent(LibraryIntent.ShowCollection(key.type, key.key)) }
        ShowMessages(viewModel.messages)
        val openInNewWindow = rememberNewWindowOpener()
        val collection = state.collection
        // None of its songs left where they were (moved by another app, say): it goes, rather than
        // stay a screen that loads forever.
        val gone = !state.loading && collection == null
        LaunchedEffect(gone) { if (gone) navigator.removeDestination(key) }
        InspectorFollowsSelection(state, navigator, collectionUnfollowed, editTogether)

        val onIntent = rememberIntents(viewModel)
        val actions =
            remember(onIntent, navigator, openInNewWindow) {
                LibraryActions(
                    onIntent = onIntent,
                    onOpenTrack = { track ->
                        onIntent(LibraryIntent.Opened(track.id))
                        navigator.showDetail(Editor(track.ref.uri))
                    },
                    onFindCover = {
                        navigator.showDetail(Editor(it.ref.uri, then = EditorAction.FindCover))
                    },
                    onOpenCollection = {},
                    onEditTracks = editTogether,
                    onOpenInNewWindow = openInNewWindow,
                    onOpenSettings = {},
                    onOpenFile = {},
                    onShowShortcuts = null,
                )
            }
        CollectionScreen(
            title = collection?.title ?: key.title,
            collection = collection.takeUnless { state.loading },
            state = state,
            actions = actions,
            showBack = true,
            onBack = { navigator.leave(key) },
        )
    }
}

/**
 * Where a list shares the window with its inspector, the inspector shows what is selected in it, as
 * a desktop's file manager does: the songs selected together in the batch editor, one song in its
 * editor, and with nothing selected the song last opened with a click while it is listed, or
 * nothing. Left to the clicks alone, the inspector stayed on the song opened before songs were
 * selected, and an editor of a song no longer listed stayed beside a list of others.
 *
 * Only a change of the selection moves it, not what it is when the list comes back into sight: the
 * inspector then shows what was left there (a file opened, the song playing). A selection that
 * grows, row after row of a range or a drag, is shown once it has stopped for a moment: each step
 * would read every song again. A selection ended shows at once what stands in for it. Whatever has
 * unsaved changes stays, and so does what was opened by hand when the list changes under it
 * ([Navigator.showSelection]). A phone has no inspector: there the selection is edited together
 * from its bar.
 *
 * A selection of more than [FollowedAtMost] songs is not followed: the batch editor reads every
 * song it is given, and Ctrl+A on a large library had it read thousands of files nobody asked for.
 * The placeholder beside the list says how many are selected instead ([unfollowed]), and edits them
 * together on purpose ([editTogether]), as Ctrl+E does.
 */
@OptIn(FlowPreview::class)
@Composable
private fun InspectorFollowsSelection(
    state: LibraryState,
    navigator: Navigator,
    unfollowed: UnfollowedSelection,
    editTogether: (List<Track>) -> Unit,
) {
    if (LocalPaneContext.current.isSolePane) return
    val inspected =
        remember(state.listing, state.selection, state.clicked, state.collectionSelection) {
            Inspected.of(state)
        }
    val latest = rememberUpdatedState(inspected)
    LaunchedEffect(navigator) {
        snapshotFlow { latest.value }
            .drop(1)
            .debounce { if (it.selected) SelectionSettles else Duration.ZERO }
            .collect { navigator.showSelection(it.key) }
    }
    val current = rememberUpdatedState(state)
    val owner = remember { Any() }
    SideEffect {
        unfollowed.show(owner, inspected.unfollowed) {
            current.value.selectedSongs.takeIf { it.isNotEmpty() }?.let(editTogether)
        }
    }
    DisposableEffect(unfollowed, owner) { onDispose { unfollowed.clear(owner) } }
}

/**
 * What the inspector shows of a list, and whether that is its selection; [unfollowed] is how many
 * songs are selected when they are too many to show.
 */
internal data class Inspected(val key: NavKey?, val selected: Boolean, val unfollowed: Int = 0) {
    companion object {
        fun of(state: LibraryState): Inspected {
            val selected = state.selectedSongs
            return when {
                selected.size > FollowedAtMost -> Inspected(null, true, unfollowed = selected.size)
                selected.size > 1 -> Inspected(BatchEditor(selected.map { it.id.value }), true)
                selected.size == 1 -> Inspected(Editor(selected.single().ref.uri), true)
                else ->
                    Inspected(
                        state.clicked
                            ?.let { id -> state.tracks.firstOrNull { it.id == id } }
                            ?.let { Editor(it.ref.uri) },
                        selected = false,
                    )
            }
        }
    }
}

/**
 * The songs selected: albums, artists or folders selected are their songs, as Ctrl+E edits them.
 */
private val LibraryState.selectedSongs: List<Track>
    get() = if (selectingCollections) selectedCollectionTracks else selectedTracks

/** How long a selection rests before the inspector reads it. */
private val SelectionSettles = 300.milliseconds

/**
 * The most songs the inspector follows a selection of. The batch editor reads each file it is
 * given: a hundred is a few seconds on an SD card, and more than a desktop's eye takes in at once.
 */
internal const val FollowedAtMost = 100

/**
 * A selection too large for the inspector to follow, as the list on top has it: what the
 * placeholder beside the list says instead. List-detail draws that placeholder apart from the
 * list's entry, which knows the selection; the entry writes it here, and takes it back as it goes.
 * The library has one and collections another, and a list that is not the last to write leaves it
 * be.
 */
@Stable
private class UnfollowedSelection {
    /** How many songs are selected, while they are more than the inspector follows; else 0. */
    var count by mutableIntStateOf(0)
        private set

    private var owner: Any? = null
    private var edit: () -> Unit = {}

    fun show(owner: Any, count: Int, edit: () -> Unit) {
        this.owner = owner
        this.count = count
        this.edit = edit
    }

    fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        count = 0
        edit = {}
    }

    /** Opens the batch editor for the songs selected, as Ctrl+E does. */
    fun editTogether() = edit()
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
 * what will be there, or how many songs are selected when they are too many for it to follow
 * ([unfollowed]), with the way to edit them together.
 */
@Composable
private fun NoSongOpen(unfollowed: UnfollowedSelection) {
    Box(
        Modifier.fillMaxSize()
            .then(if (LocalPanels.current) Modifier.studioPanel() else Modifier)
            .padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        val count = unfollowed.count
        if (count > 0) {
            PlaceholderCard(
                title = pluralStringResource(R.plurals.selected_songs, count, count),
                description = stringResource(R.string.selection_unfollowed_description),
                icon = Icons.Rounded.SelectAll,
                actionText = stringResource(R.string.edit_together),
                actionIcon = Icons.Rounded.EditNote,
                onAction = unfollowed::editTogether,
                actionShortcut = shortcutHint(KeyEvent.KEYCODE_E, ctrl = true),
            )
        } else {
            PlaceholderCard(
                title = stringResource(R.string.no_song_open_title),
                description = stringResource(R.string.no_song_open_description),
                icon = Icons.Rounded.EditNote,
            )
        }
    }
}
