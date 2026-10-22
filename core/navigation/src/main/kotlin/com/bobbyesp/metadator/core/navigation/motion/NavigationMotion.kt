/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.navigation.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent

/** Every transition between destinations, in one place. Screens contribute nothing. */
object NavigationMotion {
    private const val BEHIND_SCALE = 0.94f
    private const val HELD_SCALE = 0.9f
    private const val GESTURE_MS = 250
    private val Peek = CubicBezierEasing(0f, 0f, 0f, 1f)
    private val Leave = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * What is left of the screen behind while another covers it: dimmed, not gone, so that the
     * strip of it still uncovered as the other slides over it is the same screen stepping back.
     */
    private const val BEHIND_ALPHA = 0.6f

    /**
     * Forward on a wide window: the new screen fades up from a little way towards the end, as the
     * old one fades away a little way towards the start. A short shift through a fade, as a desktop
     * tool changes what it shows: a whole screen sliding over another is a phone's way, and between
     * panels on a ground it put two screens in sight at once.
     *
     * @param shift how far each screen moves, in pixels: a set distance, not a share of a window
     *   that may be as wide as a monitor
     */
    fun AnimatedContentTransitionScope<*>.forward(
        motion: MotionScheme,
        shift: Int,
    ): ContentTransform {
        val travel = motion.defaultSpatialSpec<IntOffset>().toThePixel()
        val arriving =
            slideIntoContainer(SlideDirection.Start, travel) { shift } +
                fadeIn(motion.defaultEffectsSpec())
        val leaving =
            slideOutOfContainer(SlideDirection.Start, travel) { shift } +
                fadeOut(motion.fastEffectsSpec())
        return arriving togetherWith leaving
    }

    /** Backward on a wide window: [forward] the other way round. */
    fun AnimatedContentTransitionScope<*>.backward(
        motion: MotionScheme,
        shift: Int,
    ): ContentTransform {
        val travel = motion.defaultSpatialSpec<IntOffset>().toThePixel()
        val arriving =
            slideIntoContainer(SlideDirection.End, travel) { shift } +
                fadeIn(motion.defaultEffectsSpec())
        val leaving =
            slideOutOfContainer(SlideDirection.End, travel) { shift } +
                fadeOut(motion.fastEffectsSpec())
        return arriving togetherWith leaving
    }

    /**
     * Forward on a phone: the new screen comes in over the old one from the end of the window, and
     * the old one steps back under it, a little smaller and dimmed, the way the back gesture finds
     * it. A screen that only faded in was a flash: it was over before it read as a move.
     */
    fun AnimatedContentTransitionScope<*>.push(motion: MotionScheme): ContentTransform {
        val arriving = slideIntoContainer(SlideDirection.Start, motion.screenTravel()) { it }
        val leaving =
            scaleOut(motion.defaultSpatialSpec(), targetScale = BEHIND_SCALE) +
                fadeOut(motion.defaultEffectsSpec(), targetAlpha = BEHIND_ALPHA)
        return arriving togetherWith leaving
    }

    /**
     * Backward on a phone: [push] the other way round, and the same leaving as the back gesture's,
     * so that a tap on Back and a swipe end alike.
     */
    fun AnimatedContentTransitionScope<*>.pop(motion: MotionScheme): ContentTransform {
        val arriving =
            scaleIn(motion.defaultSpatialSpec(), initialScale = BEHIND_SCALE) +
                fadeIn(motion.defaultEffectsSpec(), initialAlpha = BEHIND_ALPHA)
        val leaving = slideOutOfContainer(SlideDirection.End, motion.screenTravel()) { it }
        return arriving togetherWith leaving
    }

    /**
     * The back gesture: the screen shrinks under the finger, showing the one behind, then leaves
     * the way the finger goes.
     *
     * Tweens of one length, not the theme's springs: the display seeks this transition by the
     * gesture's progress, and each spring would run through its own time under the same finger.
     */
    fun predictiveBack(@NavigationEvent.SwipeEdge swipeEdge: Int): ContentTransform {
        val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        return (scaleIn(tween(GESTURE_MS, easing = Peek), initialScale = BEHIND_SCALE) +
            fadeIn(tween(GESTURE_MS / 3, easing = LinearEasing))) togetherWith
            (scaleOut(tween(GESTURE_MS, easing = Peek), targetScale = HELD_SCALE) +
                slideOutHorizontally(tween(GESTURE_MS, easing = Leave)) { direction * it })
    }

    /**
     * The theme's spatial spring, for a whole screen: at the pixel, and with no bounce. The spring
     * is made to overshoot a little, which a button's corner wears well and a screen does not: the
     * width of the window past its place and back is a screen that lands and rebounds.
     */
    private fun MotionScheme.screenTravel(): FiniteAnimationSpec<IntOffset> {
        val spec = defaultSpatialSpec<IntOffset>()
        val stiffness = (spec as? SpringSpec)?.stiffness ?: Spring.StiffnessMediumLow
        return spring(Spring.DampingRatioNoBouncy, stiffness, IntOffset.VisibilityThreshold)
    }

    /**
     * A spring with no threshold settles to a hundredth of a pixel, and the transition, with both
     * screens composed, runs on long after anything moves.
     */
    private fun FiniteAnimationSpec<IntOffset>.toThePixel(): FiniteAnimationSpec<IntOffset> =
        if (this is SpringSpec) spring(dampingRatio, stiffness, IntOffset.VisibilityThreshold)
        else this
}

val LocalNavSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Whether the destination has finished arriving, to start work that would stutter its motion. */
@Composable
fun isDestinationSettled(): Boolean {
    if (LocalNavSharedTransitionScope.current == null) return true
    return !LocalNavAnimatedContentScope.current.transition.isRunning
}

/** Shares an element (a cover) between two destinations; a no-op outside a navigation display. */
@Composable
fun Modifier.sharedElementAcrossDestinations(key: Any): Modifier {
    val scope = LocalNavSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(scope) {
        this@sharedElementAcrossDestinations.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
