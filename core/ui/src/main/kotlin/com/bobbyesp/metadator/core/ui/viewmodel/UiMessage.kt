/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.viewmodel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Something to tell the user, with at most one action (Undo, Retry). */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val long: Boolean = false,
    val onAction: (() -> Unit)? = null,
)

/** The app's one snackbar host, so messages outlive the screen that raised them. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Shows a ViewModel's messages while the screen is started.
 *
 * A message with an action is as short as one without: "Saved, Undo" kept for ten seconds stood
 * over the next thing to press, and a tap meant for it undid the save. A newer message replaces the
 * one on show rather than waiting behind it, so saving twice says it once, with the latest undo.
 * Material lengthens both for whoever uses accessibility services.
 */
@Composable
fun ShowMessages(messages: Flow<UiMessage>) {
    val host = LocalSnackbarHostState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(messages, host) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages.collectLatest { message ->
                val result =
                    host.showSnackbar(
                        message = message.text,
                        actionLabel = message.actionLabel,
                        withDismissAction = message.actionLabel == null && message.long,
                        duration =
                            if (message.long) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }
}

/**
 * Where the message on show is, so that a press on it (its Undo) can be told from a press
 * elsewhere, which dismisses it. See [dismissesMessages].
 */
@Stable
class MessageArea internal constructor() {
    internal var holder: LayoutCoordinates? = null
    internal var message: LayoutCoordinates? = null

    internal fun contains(position: Offset): Boolean {
        val holder = holder?.takeIf { it.isAttached } ?: return false
        val message = message?.takeIf { it.isAttached } ?: return false
        return holder.localBoundingBoxOf(message).contains(position)
    }
}

@Composable fun rememberMessageArea(): MessageArea = remember { MessageArea() }

/**
 * On what holds the screens and the snackbar host: the first press anywhere but on the message on
 * show dismisses it. It only informs, and stood in the way of what was pressed next until its time
 * was up. The press is not taken: it goes on to whatever it was meant for.
 */
fun Modifier.dismissesMessages(host: SnackbarHostState, area: MessageArea): Modifier {
    val placed = onPlaced { area.holder = it }
    return placed.pointerInput(host, area) {
        awaitEachGesture {
            val press = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val shown = host.currentSnackbarData ?: return@awaitEachGesture
            if (!area.contains(press.position)) shown.dismiss()
        }
    }
}

/** On the snackbar host, last: where its message is. */
fun Modifier.messageArea(area: MessageArea): Modifier = onPlaced { area.message = it }

/** Collects one-off effects while the screen is started. */
@Composable
fun <T> Flow<T>.CollectEffects(vararg keys: Any?, onEffect: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val flow = this
    LaunchedEffect(flow, *keys) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect(onEffect) }
    }
}
