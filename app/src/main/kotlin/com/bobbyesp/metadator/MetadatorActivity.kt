/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorTheme
import com.bobbyesp.metadator.core.domain.settings.SettingsRepository
import com.bobbyesp.metadator.core.model.UserSettings
import com.bobbyesp.metadator.core.ui.input.LocalShortcutDispatcher
import com.bobbyesp.metadator.core.ui.input.ShortcutDispatcher
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import com.bobbyesp.metadator.library.mediastore.ActivityResultHost
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject

/**
 * What the app's two activities share: the settings read before anything is composed, so the first
 * frame is in their theme; the keys no view took, for the screens to take as shortcuts; and the
 * system bars drawn behind the app, with their icons following day and night.
 */
abstract class MetadatorActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by inject()
    private val resultHost: ActivityResultHost by inject()

    /** The keys no view took, for the screens to take as shortcuts. */
    private val shortcuts = ShortcutDispatcher()

    private val settingsLoaded = MutableStateFlow(false)

    /** Whether the settings were read: nothing is composed before. */
    protected val isSettingsLoaded: Boolean
        get() = settingsLoaded.value

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /**
     * Lends this activity's launchers to the prompts for permission and write access, then composes
     * [content] once the settings are read, in their theme, with the snackbar's state and the
     * shortcuts provided. For [onCreate].
     */
    protected fun setMetadatorContent(content: @Composable (UserSettings) -> Unit) {
        resultHost.attach(this)
        setContent {
            val settings by
                settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(settings != null) { if (settings != null) settingsLoaded.value = true }
            val current = settings ?: return@setContent
            val snackbar = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalSnackbarHostState provides snackbar,
                LocalShortcutDispatcher provides shortcuts,
            ) {
                MetadatorTheme(current) { content(current) }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        (event != null && shortcuts.dispatch(event)) || super.onKeyDown(keyCode, event)

    /** For the system's list of shortcuts, which a keyboard opens with Meta and the slash. */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data?.addAll(shortcuts.systemGroups())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity stays through a change between day and night (see the manifest), so
        // nothing else gives the system bars the icons that go with the new one.
        enableEdgeToEdge()
    }
}
