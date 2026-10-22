/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.metadator.core.common.StringProvider
import com.bobbyesp.metadator.core.ui.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI for every screen: intents in, one immutable [state] out.
 * - [effects] are commands for whoever is on screen now (close, navigate) and are dropped when
 *   nobody listens: a command nobody took no longer applies.
 * - [messages] are for the user (snackbars) and wait until somebody shows them: losing one is a
 *   silent failure.
 */
abstract class BaseViewModel<Intent : Any, State : Any, Effect : Any>(initialState: State) :
    ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    protected val currentState: State
        get() = _state.value

    private val _effects =
        MutableSharedFlow<Effect>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onIntent(intent: Intent) = handleIntent(intent)

    protected abstract fun handleIntent(intent: Intent)

    protected fun setState(reducer: State.() -> State) = _state.update { it.reducer() }

    protected fun sendEffect(effect: Effect) {
        _effects.tryEmit(effect)
    }

    protected fun showMessage(message: UiMessage) {
        viewModelScope.launch { _messages.send(message) }
    }

    /**
     * Launches [block]; a failure goes to [onError], or else to [onUnhandledError], instead of
     * crashing the app. Errors are caught too: native code throws whatever it likes, and none of it
     * may crash the app.
     */
    protected fun launch(
        onError: (Throwable) -> Unit = this::onUnhandledError,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onError(error)
        }
    }

    /**
     * A failure no caller of [launch] handled. Swallowed, it left no trace at all: nothing in the
     * log, and nothing on screen for whoever pressed the button. A screen the user acts on says so
     * too, with [unexpectedErrorMessage].
     */
    protected open fun onUnhandledError(error: Throwable) {
        Log.w(javaClass.simpleName, "unhandled", error)
    }

    /** "Something went wrong": what the user is told of a failure nothing else explains. */
    protected fun unexpectedErrorMessage(strings: StringProvider): UiMessage =
        UiMessage(strings.get(R.string.error_unexpected))
}
