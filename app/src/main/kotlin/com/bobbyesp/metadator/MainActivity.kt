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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorTheme
import com.bobbyesp.metadator.core.domain.settings.SettingsRepository
import com.bobbyesp.metadator.core.ui.input.LocalShortcutDispatcher
import com.bobbyesp.metadator.core.ui.input.ShortcutDispatcher
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import com.bobbyesp.metadator.library.mediastore.ActivityResultHost
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by inject()
    private val resultHost: ActivityResultHost by inject()

    /** The splash stays until the theme is known, so the first frame is already the right one. */
    private val settingsLoaded = MutableStateFlow(false)

    /** The keys no view took, for the screens to take as shortcuts. */
    private val shortcuts = ShortcutDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !settingsLoaded.value }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Lends this activity's launchers for permission and write-access prompts.
        resultHost.attach(this)

        setContent {
            val settings by
                settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(settings != null) { if (settings != null) settingsLoaded.value = true }
            val current = settings ?: return@setContent
            ReviewPrompt(successfulSaves = current.successfulSaves)

            val snackbar = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalSnackbarHostState provides snackbar,
                LocalShortcutDispatcher provides shortcuts,
            ) {
                MetadatorTheme(current) { MetadatorApp(onExit = ::finish) }
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
