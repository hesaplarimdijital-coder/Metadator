/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorAccentTheme
import com.bobbyesp.metadator.core.domain.cover.CoverRequest
import com.bobbyesp.metadator.core.domain.editor.TagDraft
import com.bobbyesp.metadator.core.model.ContentRef
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.Navigator
import com.bobbyesp.metadator.core.navigation.pane.LocalPaneContext
import com.bobbyesp.metadator.core.ui.cover.CoverFinderSheet
import com.bobbyesp.metadator.core.ui.cover.CoverFinderViewModel
import com.bobbyesp.metadator.core.ui.viewmodel.CollectEffects
import com.bobbyesp.metadator.core.ui.viewmodel.ShowMessages
import com.bobbyesp.metadator.feature.editor.lookup.LookupSheet
import com.bobbyesp.metadator.feature.editor.lookup.LookupViewModel
import com.bobbyesp.metadator.tags.api.TagField
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The editor's destination: beside the library on a wide window, on its own otherwise. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.editorSection(navigator: Navigator) {
    entry<Editor>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        EditorDestination(
            uri = key.uri,
            onClose = { navigator.removeDestination(key) },
            // Another song chosen in the library beside it must not take the place of this one
            // while it has changes nobody saved.
            onUnsavedChanges = { navigator.hold(key, it) },
        )
    }
}

/**
 * The editor for [uri], for any host: the main activity's stack or the external editor's.
 *
 * @param onUnsavedChanges told whether there are changes not yet saved, each time that changes
 */
@Composable
fun EditorDestination(
    uri: String,
    onClose: () -> Unit,
    onUnsavedChanges: (Boolean) -> Unit = {},
) {
    val viewModel: EditorViewModel = koinViewModel(key = uri) { parametersOf(ContentRef(uri)) }
    val lookup: LookupViewModel = koinViewModel(key = "lookup:$uri")
    val covers: CoverFinderViewModel = koinViewModel(key = "covers:$uri")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lookupState by lookup.state.collectAsStateWithLifecycle()
    val coverState by covers.state.collectAsStateWithLifecycle()
    var showLookup by rememberSaveable(uri) { mutableStateOf(false) }
    // Not saved: what it showed would be gone with the process, and the song not yet read again.
    var showCovers by remember(uri) { mutableStateOf(false) }

    val unsaved = state.isDirty
    val currentOnUnsavedChanges by rememberUpdatedState(onUnsavedChanges)
    LaunchedEffect(unsaved) { currentOnUnsavedChanges(unsaved) }

    ShowMessages(viewModel.messages)
    viewModel.effects.CollectEffects { effect ->
        when (effect) {
            EditorEffect.Close -> onClose()
        }
    }

    val accent =
        state.coverInfo
            ?.dominantColor
            ?.takeIf { state.settings.colorEditorFromArtwork }
            ?.let { Color(it) }

    MetadatorAccentTheme(accent = accent) {
        val findMetadata = {
            val draft = state.draft
            if (draft != null) {
                showLookup = true
                if (lookupState.title.isEmpty() && lookupState.artist.isEmpty()) {
                    lookup.start(
                        title =
                            draft.tags.first(TagField.Title.key)
                                ?: state.loaded?.track?.title.orEmpty(),
                        artist = draft.tags[TagField.Artist.key].firstOrNull().orEmpty(),
                        album = draft.tags.first(TagField.Album.key).orEmpty(),
                        durationMs =
                            state.loaded?.audio?.durationMs ?: state.loaded?.track?.durationMs,
                    )
                }
            }
        }
        val findCover = {
            val draft = state.draft
            if (draft != null) {
                showCovers = true
                covers.start(coverRequest(state, draft))
            }
        }
        EditorScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onFindMetadata = findMetadata,
            onFindCover = findCover,
            onClose = onClose,
            showClose = !LocalPaneContext.current.isSolePane,
        )

        if (showLookup) {
            LookupSheet(
                state = lookupState,
                onQueryChange = { title, artist, album -> lookup.setQuery(title, artist, album) },
                onSearch = lookup::search,
                onOpen = { candidate ->
                    state.draft?.let { lookup.open(candidate, it.tags, state.positionStyle) }
                },
                onToggle = lookup::toggle,
                onCheckOnly = lookup::checkOnly,
                onToggleCover = lookup::toggleCover,
                onBackToResults = lookup::closeComparison,
                onApply = { fields, coverUrl ->
                    viewModel.onIntent(EditorIntent.ApplyLookup(fields, coverUrl))
                    lookup.closeComparison()
                    showLookup = false
                },
                onDismiss = { showLookup = false },
            )
        }

        if (showCovers) {
            CoverFinderSheet(
                state = coverState,
                onQueryChange = covers::setQuery,
                onSearch = covers::search,
                onUse = { suggestion ->
                    viewModel.onIntent(EditorIntent.SetCoverFromUrl(suggestion.url))
                    showCovers = false
                },
                onDismiss = { showCovers = false },
            )
        }
    }
}

/** The song as it stands in the editor, unsaved changes included: what a cover is looked for. */
private fun coverRequest(state: EditorState, draft: TagDraft): CoverRequest =
    CoverRequest.forSongs(
        songs = listOf(draft.tags),
        titleFallback = state.loaded?.track?.title.orEmpty(),
        durationMs = state.loaded?.audio?.durationMs ?: state.loaded?.track?.durationMs,
        currentCover = draft.cover?.data,
    )
