/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import kotlin.math.max
import kotlin.math.min

/*
 * The arithmetic of a marquee (MarqueeSelection.kt), kept apart from Compose so that it is tested
 * on the JVM.
 *
 * A lazy list only knows where the items in sight are, and its items need not all be one height,
 * so nothing knows how many pixels down the list a place is. A place is counted as a slot instead:
 * inside item `i` is `2 * i + 1`, the space before it (the list's padding, the spacing between
 * items) is `2 * i`, and below the last of `n` items is `2 * n`. A slot stays true however far the
 * list scrolls. A marquee keeps where it began as one, and touches every item whose slot lies
 * between that one and the pointer's: those in sight, and those scrolled away under it.
 */

/**
 * The slot that [y] falls in, among the [count] items in sight: the `k`th of them is item
 * `index(k)` of the list, from `top(k)` to `top(k) + size(k)`, in the coordinates the list gives
 * its items (`LazyListItemInfo.offset`). They come in order, from the top down.
 */
internal inline fun marqueeSlot(
    y: Float,
    count: Int,
    index: (Int) -> Int,
    top: (Int) -> Int,
    size: (Int) -> Int,
): Int {
    if (count == 0) return 0
    for (k in 0 until count) {
        val start = top(k)
        if (y < start) return 2 * index(k)
        if (y < start + size(k)) return 2 * index(k) + 1
    }
    return 2 * (index(count - 1) + 1)
}

/** The first item a marquee from slot [from] to slot [to] touches, whichever way it was drawn. */
internal fun firstTouched(from: Int, to: Int): Int = min(from, to) / 2

/**
 * The last item a marquee from slot [from] to slot [to] touches, whichever way it was drawn. Before
 * [firstTouched] when it touches none: drawn within one space between two items.
 */
internal fun lastTouched(from: Int, to: Int): Int = (max(from, to) - 1).floorDiv(2)

/**
 * How fast a marquee scrolls a list [height] tall with the pointer [y] down from its top, in pixels
 * a second, toward the end when positive. Not at all while the pointer is further than [zone] from
 * both edges; then faster as it nears an edge and goes past it, up to [maxSpeed] once it is [reach]
 * past. The speed grows with the square of the way in, so that it creeps near the edge, where the
 * eye is on the rows coming in, and races when the pointer is flung far past it. A list too short
 * for two whole zones keeps its middle third still.
 */
internal fun marqueeScrollSpeed(
    y: Float,
    height: Float,
    zone: Float,
    reach: Float,
    maxSpeed: Float,
): Float {
    if (height <= 0f) return 0f
    val inside = min(zone, height / 3f)
    val ramp = inside + reach
    val towardTop = inside - y
    val towardBottom = y - (height - inside)
    return when {
        towardTop > 0f -> -maxSpeed * eased(towardTop / ramp)
        towardBottom > 0f -> maxSpeed * eased(towardBottom / ramp)
        else -> 0f
    }
}

private fun eased(fraction: Float): Float {
    val clamped = min(fraction, 1f)
    return clamped * clamped
}
