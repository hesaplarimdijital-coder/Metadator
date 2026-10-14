/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.content.res.Configuration
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** Whether this window is a desktop's, for what Back does at the start of the app. */
    private val desktopWindow = DesktopWindow()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !settingsLoaded.value }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Lends this activity's launchers for permission and write-access prompts.
        resultHost.attach(this)
        desktopWindow.windowed = isInMultiWindowMode
        desktopWindow.touchless = resources.configuration.hasNoTouchscreen
        addOnMultiWindowModeChangedListener { desktopWindow.windowed = it.isInMultiWindowMode }

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
                MetadatorTheme(current) {
                    MetadatorApp(onExit = ::finish, isDesktopWindow = desktopWindow::isDesktop)
                }
            }
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        desktopWindow.notePointer(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        desktopWindow.notePointer(ev)
        return super.dispatchTouchEvent(ev)
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
        // And through being moved to a display without touch, a monitor.
        desktopWindow.touchless = newConfig.hasNoTouchscreen
    }
}

private val Configuration.hasNoTouchscreen: Boolean
    get() = touchscreen == Configuration.TOUCHSCREEN_NOTOUCH

/**
 * Whether the app is used as a desktop's window. Back at the start of the app minimizes it: on a
 * phone that is going home, as from any app, but on a desktop it is the window vanishing from under
 * the pointer, at a second press of Esc or of a mouse's back button.
 *
 * DeX says it is DeX only through fields of Samsung's own, read by reflection. These are three
 * things Android tells any app, and any of them makes it a desktop's window:
 * - It is one window among others ([windowed], multi-window mode): DeX, Android's desktop windows,
 *   a Chromebook's and freeform windows all put an app in one. A phone's split screen counts too: a
 *   window placed beside another is not one to take away either.
 * - Its display has no touchscreen ([touchless]): the monitor DeX runs on, most Chromebooks.
 * - What last pointed at it was a mouse, not a finger. Android does not say where a Back came from:
 *   a mouse's back button arrives as the gesture's Back does. A mouse in hand is the nearest thing
 *   to knowing it was the mouse's, on a phone a mouse was plugged into as on a desktop.
 */
internal class DesktopWindow {
    var windowed by mutableStateOf(false)
    var touchless by mutableStateOf(false)
    private var pointedWithMouse by mutableStateOf(false)

    val isDesktop: Boolean
        get() = windowed || touchless || pointedWithMouse

    /** A mouse as it moves over the window or clicks in it; a finger as it touches the screen. */
    fun notePointer(event: MotionEvent) {
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        val finger =
            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        if (mouse && !pointedWithMouse) pointedWithMouse = true
        else if (finger && pointedWithMouse) pointedWithMouse = false
    }
}
