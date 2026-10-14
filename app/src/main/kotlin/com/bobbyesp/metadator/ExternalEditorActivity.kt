/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.StudioDefaults
import com.bobbyesp.metadator.core.ui.input.ShortcutsHelp
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import com.bobbyesp.metadator.core.ui.viewmodel.dismissesMessages
import com.bobbyesp.metadator.core.ui.viewmodel.messageArea
import com.bobbyesp.metadator.core.ui.viewmodel.rememberMessageArea
import com.bobbyesp.metadator.feature.editor.EditorDestination

/**
 * "Open with Metadator" from a file manager or a player. It runs in its own task with just the
 * editor, needs no library permission, and closing it returns to the app that opened it.
 */
class ExternalEditorActivity : MetadatorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.audioUri()
        if (uri == null) {
            finish()
            return
        }

        setMetadatorContent {
            val snackbar = LocalSnackbarHostState.current
            val messageArea = rememberMessageArea()
            // A window of its own on a desktop: as wide as one, it is a panel on the ground, as
            // the main window's editor is.
            val window = LocalWindowInfo.current.containerSize
            val panels =
                with(LocalDensity.current) { window.width.toDp() } >=
                    StudioDefaults.PanelWindowWidth
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().dismissesMessages(snackbar, messageArea)) {
                    ShortcutsHelp()
                    CompositionLocalProvider(LocalPanels provides panels) {
                        Box(
                            if (panels) {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(StudioDefaults.PanelGap)
                            } else Modifier
                        ) {
                            EditorDestination(uri = uri.toString(), onClose = ::finish)
                        }
                    }
                    SnackbarHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter)
                            // Over the keyboard, as the editor's foot is.
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.ime)
                            )
                            .padding(bottom = 96.dp)
                            .messageArea(messageArea),
                    )
                }
            }
        }
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
