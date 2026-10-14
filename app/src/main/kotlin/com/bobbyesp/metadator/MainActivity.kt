/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.content.res.Configuration
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : MetadatorActivity() {
    /** Whether this window is a desktop's, for what Back does at the start of the app. */
    private val desktopWindow = DesktopWindow()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the theme is known, so the first frame is already the right one.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !isSettingsLoaded }
        super.onCreate(savedInstanceState)
        desktopWindow.windowed = isInMultiWindowMode
        desktopWindow.touchless = resources.configuration.hasNoTouchscreen
        addOnMultiWindowModeChangedListener { desktopWindow.windowed = it.isInMultiWindowMode }

        setMetadatorContent { settings ->
            ReviewPrompt(successfulSaves = settings.successfulSaves)
            MetadatorApp(onExit = ::finish, isDesktopWindow = desktopWindow::isDesktop)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity stays through being moved to a display without touch, a monitor.
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
