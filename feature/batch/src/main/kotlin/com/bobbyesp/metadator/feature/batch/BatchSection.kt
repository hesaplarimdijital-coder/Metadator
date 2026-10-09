/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.batch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.navigation.BatchEditor
import com.bobbyesp.metadator.core.navigation.Navigator
import com.bobbyesp.metadator.core.ui.cover.CoverFinderSheet
import com.bobbyesp.metadator.core.ui.cover.CoverFinderViewModel
import com.bobbyesp.metadator.core.ui.viewmodel.CollectEffects
import com.bobbyesp.metadator.core.ui.viewmodel.ShowMessages
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

fun EntryProviderScope<NavKey>.batchSection(navigator: Navigator) {
    entry<BatchEditor> { key ->
        val id = key.uris.hashCode().toString()
        val viewModel: BatchViewModel = koinViewModel(key = id) { parametersOf(key.uris) }
        val covers: CoverFinderViewModel = koinViewModel(key = "covers:$id")
        val state by viewModel.state.collectAsStateWithLifecycle()
        val coverState by covers.state.collectAsStateWithLifecycle()
        // Not saved: what it showed would be gone with the process, and the songs not yet read.
        var showCovers by remember { mutableStateOf(false) }
        ShowMessages(viewModel.messages)
        viewModel.effects.CollectEffects { effect ->
            when (effect) {
                BatchEffect.Close -> navigator.removeDestination(key)
                is BatchEffect.FindCover -> {
                    covers.start(effect.request)
                    showCovers = true
                }
            }
        }
        BatchScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onClose = { navigator.removeDestination(key) },
        )

        if (showCovers) {
            CoverFinderSheet(
                state = coverState,
                onQueryChange = covers::setQuery,
                onSearch = covers::search,
                onUse = { suggestion ->
                    viewModel.onIntent(BatchIntent.SetCoverFromUrl(suggestion.url))
                    showCovers = false
                },
                onDismiss = { showCovers = false },
                // One tap here decides for every song: the button says for how many.
                useLabel =
                    pluralStringResource(
                        R.plurals.batch_cover_use,
                        state.files.size,
                        state.files.size,
                    ),
            )
        }
    }
}
