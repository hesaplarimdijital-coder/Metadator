/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.StudioDefaults
import com.bobbyesp.metadator.core.designsystem.theme.LocalBackdropHaze
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.outOfFocus
import com.bobbyesp.metadator.core.navigation.BatchEditor
import com.bobbyesp.metadator.core.navigation.Collection
import com.bobbyesp.metadator.core.navigation.Editor
import com.bobbyesp.metadator.core.navigation.Library
import com.bobbyesp.metadator.core.navigation.LocalShownDetail
import com.bobbyesp.metadator.core.navigation.MetadatorNavDisplay
import com.bobbyesp.metadator.core.navigation.overlay.rememberOverlaySceneStrategy
import com.bobbyesp.metadator.core.navigation.pane.sharingTheWindow
import com.bobbyesp.metadator.core.navigation.rememberNavigator
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.ShortcutsHelp
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import com.bobbyesp.metadator.feature.batch.batchSection
import com.bobbyesp.metadator.feature.editor.editorSection
import com.bobbyesp.metadator.feature.library.librarySection
import com.bobbyesp.metadator.feature.player.PlayerLayout
import com.bobbyesp.metadator.feature.player.PlayerSheet
import com.bobbyesp.metadator.feature.player.PlayerSheetDefaults
import com.bobbyesp.metadator.feature.player.rememberPlayerSheetExpansion
import com.bobbyesp.metadator.feature.settings.settingsSection
import com.bobbyesp.metadator.player.api.PlayerController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject

/**
 * The app shell: one back stack, one display. Strategies decide the layout: overlays first, then
 * list-detail, which puts the editor beside the library on a wide window. The shell owns what
 * floats over every screen: the snackbar and the player, which is a bar that opens to full screen.
 *
 * A window wide enough is laid out as panels on a ground, as a desktop tool is: the library's, the
 * inspector beside it, which the editor is, and the player's bar docked under both. The screens
 * draw their panels; the shell keeps the gaps between them and around them.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MetadatorApp(onExit: () -> Unit) {
    val backStack = rememberNavBackStack(Library)
    val navigator = rememberNavigator(backStack, onExit)
    val overlay = rememberOverlaySceneStrategy<NavKey>()
    // The player is not a destination, so it is not told how much room it has as one is: the shell
    // measures the window for it, and for the panels.
    val window = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { window.width.toDp() }
    val windowHeight = with(LocalDensity.current) { window.height.toDp() }
    val panels = windowWidth >= PanelWindowWidth
    // The inspector keeps its width, whatever the window's: the library beside it takes the rest.
    val expansion =
        rememberPaneExpansionState(
            anchors =
                listOf(
                    PaneExpansionAnchor.Offset.fromEnd(
                        StudioDefaults.InspectorWidth + StudioDefaults.PanelGap / 2
                    )
                ),
            initialAnchoredIndex = 0,
        )
    val listDetail =
        rememberListDetailSceneStrategy<NavKey>(
            directive = paneDirective(panels),
            paneExpansionState = expansion,
        )
    val strategies =
        remember(overlay, listDetail) { listOf(overlay, listDetail.sharingTheWindow()) }
    // Remembered: entries are equal only while their content is the same instance, and a back
    // gesture let go is finished only if the scene it was showing still equals the one it ends on.
    val entries =
        remember(navigator) {
            entryProvider<NavKey> {
                librarySection(navigator)
                editorSection(navigator)
                batchSection(navigator)
                settingsSection(navigator)
            }
        }
    val snackbarHostState = LocalSnackbarHostState.current

    // The player belongs to browsing; the editors have their own controls and need the room. On a
    // wide window the editors are an inspector beside the library, and the bar is docked under
    // both.
    val top = backStack.lastOrNull()
    val browsing = top == Library || top is Collection
    val showPlayer = browsing || (panels && (top is Editor || top is BatchEditor))
    val player = koinInject<PlayerController>()
    val playback by player.state.collectAsStateWithLifecycle()
    // Before the screens: a screen that wants one of these keys for itself comes later, and has
    // it.
    ShortcutsHelp()
    KeyboardShortcuts(
        title = stringResource(CoreUiR.string.shortcuts_group_playback),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_SPACE,
                    stringResource(CoreUiR.string.shortcut_play_pause),
                    enabled = playback.isActive,
                    action = player::togglePlayPause,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    stringResource(CoreUiR.string.shortcut_next_song),
                    ctrl = true,
                    enabled = playback.isActive,
                    action = player::skipToNext,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    stringResource(CoreUiR.string.shortcut_previous_song),
                    ctrl = true,
                    enabled = playback.isActive,
                    action = player::skipToPrevious,
                ),
            ),
    )
    val docked = panels && showPlayer && playback.isActive
    val editing = top is Editor || top is BatchEditor
    val snackbarClearance by
        animateDpAsState(
            targetValue =
                when {
                    // An editor's foot holds its Save button: a message goes above it, never over
                    // it. In a panel, the foot is above the panels' gap and the docked bar.
                    editing && panels ->
                        StudioDefaults.PanelGap +
                            (if (docked) PlayerSheetDefaults.DockedClearance else 0.dp) +
                            StudioDefaults.FooterHeight +
                            Spacing.small
                    editing -> StudioDefaults.FooterHeight + Spacing.small
                    docked -> PlayerSheetDefaults.DockedClearance
                    showPlayer && playback.isActive -> PlayerSheetDefaults.CollapsedClearance
                    else -> Spacing.medium
                },
            label = "SnackbarClearance",
        )
    // Room under the panels for the docked bar, which opens and closes them as it comes and goes.
    val dockClearance by
        animateDpAsState(
            targetValue = if (docked) PlayerSheetDefaults.DockedClearance else 0.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "DockClearance",
        )

    val playerLayout = PlayerLayout.forWindow(width = windowWidth, height = windowHeight)
    val playerExpansion = rememberPlayerSheetExpansion()
    // The screens, recorded for what floats over all of them to blur: their menus, and the bar of
    // the player, which is their sibling here.
    val backdrop = rememberHazeState()
    // Nothing once the player covers the screen: it is opaque, and what is behind it would be
    // blurred again on every frame for nobody to see.
    val behindPlayer =
        remember(playerExpansion) { { playerExpansion.fraction.let { if (it < 1f) it else 0f } } }
    val shownDetail = top?.takeIf { it is Editor || it is BatchEditor }
    Surface(color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(
            LocalPanels provides panels,
            LocalShownDetail provides shownDetail,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Everything the player opens over goes out of focus as it does.
                Box(
                    Modifier.fillMaxSize().outOfFocus(behindPlayer, MaterialTheme.colorScheme.scrim)
                ) {
                    CompositionLocalProvider(LocalBackdropHaze provides backdrop) {
                        MetadatorNavDisplay(
                            backStack = backStack,
                            navigator = navigator,
                            sceneStrategies = strategies,
                            entryProvider = entries,
                            modifier =
                                Modifier.hazeSource(backdrop)
                                    .then(
                                        if (panels) {
                                            // The panels keep clear of the system's bars
                                            // themselves, with a gap all around: the screens in
                                            // them have no bars to keep clear of.
                                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                                                .padding(StudioDefaults.PanelGap)
                                                .padding(bottom = dockClearance)
                                        } else Modifier
                                    ),
                        )
                    }
                    SnackbarHost(
                        snackbarHostState,
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = snackbarClearance),
                    )
                }
                // Last, so that opened it covers everything, the snackbar included.
                PlayerSheet(
                    visible = showPlayer,
                    onEdit = { uri -> navigator.showDetail(Editor(uri)) },
                    layout = playerLayout,
                    docked = panels,
                    expansion = playerExpansion,
                    backdrop = backdrop,
                )
            }
        }
    }
}

/** The narrowest window laid out as panels; narrower, the screen is one, as a phone's. */
private val PanelWindowWidth = 600.dp

/**
 * The window's own directive, but the scaffold does not move the focus to the pane it shows. It
 * would whenever the layout turns into list-detail, as on a rotation, and the first thing to take
 * it in the library is the search field: the keyboard came up over a library nobody was searching.
 *
 * Between panels, the gap of panels.
 *
 * Built field by field: `copy` has no parameter for it, and sets it back to true.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun paneDirective(panels: Boolean): PaneScaffoldDirective {
    val default = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    return remember(default, panels) {
        PaneScaffoldDirective(
            maxHorizontalPartitions = default.maxHorizontalPartitions,
            horizontalPartitionSpacerSize =
                if (panels) StudioDefaults.PanelGap else default.horizontalPartitionSpacerSize,
            maxVerticalPartitions = default.maxVerticalPartitions,
            verticalPartitionSpacerSize =
                if (panels) StudioDefaults.PanelGap else default.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = default.defaultPanePreferredWidth,
            defaultPanePreferredHeight = default.defaultPanePreferredHeight,
            excludedBounds = default.excludedBounds,
            shouldAutoFocusCurrentDestination = false,
        )
    }
}
