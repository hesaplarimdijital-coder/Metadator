/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorTheme
import com.bobbyesp.metadator.core.domain.settings.SettingsRepository
import com.bobbyesp.metadator.core.ui.input.LocalShortcutDispatcher
import com.bobbyesp.metadator.core.ui.input.ShortcutDispatcher
import com.bobbyesp.metadator.core.ui.input.ShortcutsHelp
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import com.bobbyesp.metadator.feature.editor.EditorDestination
import com.bobbyesp.metadator.library.mediastore.ActivityResultHost
import org.koin.android.ext.android.inject

/**
 * "Open with Metadator" from a file manager or a player. It runs in its own task with just the
 * editor, needs no library permission, and closing it returns to the app that opened it.
 */
class ExternalEditorActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by inject()
    private val resultHost: ActivityResultHost by inject()

    /** The keys no view took, for the editor to take as shortcuts. */
    private val shortcuts = ShortcutDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.audioUri()
        if (uri == null) {
            finish()
            return
        }
        resultHost.attach(this)

        setContent {
            val loaded by
                settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: return@setContent
            val snackbar = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalSnackbarHostState provides snackbar,
                LocalShortcutDispatcher provides shortcuts,
            ) {
                MetadatorTheme(settings) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize()) {
                            ShortcutsHelp()
                            EditorDestination(uri = uri.toString(), onClose = ::finish)
                            SnackbarHost(
                                snackbar,
                                Modifier.align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 96.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        (event != null && shortcuts.dispatch(event)) || super.onKeyDown(keyCode, event)

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
        // As in the main activity: the bars' icons follow day and night, which no longer
        // recreate the activity.
        enableEdgeToEdge()
    }

    private fun Intent.audioUri(): Uri? =
        when (action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            else -> data
        }
}
