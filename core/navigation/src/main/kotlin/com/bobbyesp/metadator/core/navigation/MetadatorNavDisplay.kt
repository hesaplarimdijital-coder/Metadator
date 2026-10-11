/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.metadator.core.navigation.motion.LocalNavSharedTransitionScope
import com.bobbyesp.metadator.core.navigation.motion.NavigationMotion
import com.bobbyesp.metadator.core.navigation.motion.rememberBehindTheGestureNavEntryDecorator
import com.bobbyesp.metadator.core.navigation.motion.rememberGroundNavEntryDecorator

/**
 * How every host renders its back stack: the main activity and the external editor. They differ in
 * their destinations and strategies, never in how entries keep state or move.
 *
 * Saveable state goes before the ViewModel store: the library needs that order for a
 * `SavedStateHandle` to survive process death.
 */
@Composable
fun MetadatorNavDisplay(
    backStack: NavBackStack<NavKey>,
    navigator: Navigator,
    sceneStrategies: List<SceneStrategy<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val shift = with(LocalDensity.current) { ScreenShift.roundToPx() }
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = navigator::goBack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                        rememberGroundNavEntryDecorator(),
                        rememberBehindTheGestureNavEntryDecorator(),
                    ),
                sceneStrategies = sceneStrategies,
                entryProvider = entryProvider,
                transitionSpec = { with(NavigationMotion) { forward(motion, shift) } },
                popTransitionSpec = { with(NavigationMotion) { backward(motion, shift) } },
                predictivePopTransitionSpec = { NavigationMotion.predictiveBack(it) },
            )
        }
    }
}

/**
 * How far a screen moves as it comes or goes: enough to say which way, little enough to be quick.
 */
private val ScreenShift = 32.dp
