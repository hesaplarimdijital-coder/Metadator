/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material's scrollbar for what scrolls up and down: how much of it is in sight and which part, at
 * its end edge. It shows while the list moves and fades once it rests, as lists do on Android.
 * [alwaysVisible] keeps it in sight where it is the only sign that there is more: a list cut short
 * by a small window looks like a list that ends there.
 *
 * It is a sign, not a handle: the wheel, the keys and a finger do the moving.
 *
 * On a lazy list it goes on the list itself. On a column that scrolls it goes before
 * `verticalScroll` in the chain, or it would be scrolled away with the content.
 */
// Both are new; which of them was still marked experimental in this version is not worth a build.
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun Modifier.scrollbar(state: ScrollableState, alwaysVisible: Boolean = false): Modifier {
    // What cannot say where it is gets no bar.
    val indicator = state.scrollIndicatorState ?: return this
    return nonInteractiveScrollbar(
        state = indicator,
        orientation = Orientation.Vertical,
        isFadeEnabled = !alwaysVisible,
    )
}
