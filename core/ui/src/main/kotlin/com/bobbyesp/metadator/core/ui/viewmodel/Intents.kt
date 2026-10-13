/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The way a screen sends [viewModel] its intents: one function for as long as the ViewModel lives.
 * It is closed over by everything on the screen that can be pressed; a new one at each change of
 * the state would have made all of them new, and nothing on the screen could be skipped.
 */
@Composable
fun <I : Any> rememberIntents(viewModel: BaseViewModel<I, *, *>): (I) -> Unit =
    remember(viewModel) { { intent: I -> viewModel.onIntent(intent) } }
