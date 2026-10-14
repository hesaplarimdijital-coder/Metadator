/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.core.view.ViewCompat
import com.bobbyesp.metadator.core.designsystem.theme.LocalStudioColors
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Selecting with a rectangle, as a file manager does: the mouse's first button pressed over a list,
 * on a row or beside the rows, and dragged; a rectangle follows the pointer, and every item it
 * touches is selected. Only a mouse draws one. A finger and a stylus scroll the list, and a click
 * that does not move is still the row's, as it was before there were marquees.
 */

/**
 * A rectangle dragged with the mouse over a lazy list: where it began, which items it touches, and
 * whom to tell. Made by [rememberMarqueeSelectionState]; [Modifier.marqueeSelection] on the list
 * draws it and drives it.
 */
@Stable
class MarqueeSelectionState internal constructor(internal val listState: LazyListState) {
    /** Whether a marquee is being dragged now. */
    var isActive: Boolean by mutableStateOf(false)
        private set

    internal var onSelect: (keys: Set<Any>, additive: Boolean) -> Unit = { _, _ -> }
    internal var selectable: (key: Any, contentType: Any?) -> Boolean = { _, _ -> true }
    internal var onStart: () -> Unit = {}
    internal var onEnd: () -> Unit = {}

    // Whether Ctrl was down with the button: what the marquee touches then goes with what was
    // selected, instead of taking its place.
    private var additive = false

    // Where the button went down, held in the content so that it moves with it as the list
    // scrolls: the slot it fell in (MarqueeGeometry.kt), and an item it is measured from, with
    // how far below that item's top it was. In a list with no items it stays where it was in the
    // list's bounds. Across, a list does not scroll.
    private var anchorSlot = 0
    private var anchorItem = NoItem
    private var anchorBelowItem = 0f
    private var pressY = 0f
    internal var anchorX = 0f
        private set

    /** Where the pointer is, in the list's bounds; past them while it drags the list along. */
    internal var pointerX = 0f
        private set

    internal var pointerY = 0f
        private set

    // The items last told, first to last; none when the last is before the first.
    private var first = 0
    private var last = NoItem

    // The key of each item seen since the drag began, by index from keysFrom: one that scrolls out
    // of sight under the marquee is still in it, and the list no longer says what it is. Null for
    // one the list jumped over unseen, Excluded for one that cannot be selected.
    private val keys = ArrayDeque<Any?>()
    private var keysFrom = 0

    /** The button went down at ([x], [y]): where a marquee begins, if it is dragged. */
    internal fun press(x: Float, y: Float, ctrl: Boolean) {
        additive = ctrl
        anchorX = x
        pressY = y
        val layout = listState.layoutInfo
        val items = layout.visibleItemsInfo
        val inList = y + layout.viewportStartOffset
        anchorSlot = slotOf(inList, items)
        // The item it is on, or else the first one below it, or else the last of all.
        val from = items.fastFirstOrNull { inList < it.offset + it.size } ?: items.lastOrNull()
        anchorItem = from?.index ?: NoItem
        anchorBelowItem = if (from == null) 0f else inList - from.offset
    }

    /** The drag went past the slop, with the pointer at ([x], [y]): the marquee begins. */
    internal fun start(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        keys.clear()
        first = 0
        last = NoItem
        isActive = true
        onStart()
        report(force = true)
    }

    internal fun moveTo(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        report()
    }

    /**
     * Tells what the marquee touches now, if that changed since it was last told: after the pointer
     * moved, or the list under it.
     */
    internal fun report(force: Boolean = false) {
        if (!isActive) return
        val layout = listState.layoutInfo
        val items = layout.visibleItemsInfo
        learn(items)
        // Past an edge, the pointer stands for the edge: what lies beyond comes into sight as the
        // list scrolls, and is taken then.
        val bottom = (layout.viewportSize.height - 1).coerceAtLeast(0).toFloat()
        val slot = slotOf(pointerY.coerceIn(0f, bottom) + layout.viewportStartOffset, items)
        var newFirst = firstTouched(anchorSlot, slot)
        var newLast = lastTouched(anchorSlot, slot)
        if (newLast < newFirst) {
            newFirst = 0
            newLast = NoItem
        }
        if (!force && newFirst == first && newLast == last) return
        first = newFirst
        last = newLast
        onSelect(touchedKeys(), additive)
    }

    internal fun end() {
        if (!isActive) return
        isActive = false
        keys.clear()
        onEnd()
    }

    /** Where the button went down, in the list's bounds now: past an edge once scrolled away. */
    internal fun anchorY(layout: LazyListLayoutInfo): Float {
        if (anchorItem == NoItem) return pressY
        val items = layout.visibleItemsInfo
        var aboveSight = true
        for (k in items.indices) {
            val item = items[k]
            if (item.index == anchorItem) {
                return item.offset + anchorBelowItem - layout.viewportStartOffset
            }
            if (item.index < anchorItem) aboveSight = false
        }
        return if (aboveSight) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
    }

    private fun learn(items: List<LazyListItemInfo>) {
        if (items.isEmpty()) return
        if (keys.isEmpty()) keysFrom = items[0].index
        for (k in items.indices) {
            val item = items[k]
            while (item.index < keysFrom) {
                keys.addFirst(null)
                keysFrom--
            }
            while (item.index - keysFrom >= keys.size) keys.addLast(null)
            val at = item.index - keysFrom
            if (keys[at] == null) {
                keys[at] = if (selectable(item.key, item.contentType)) item.key else Excluded
            }
        }
    }

    private fun touchedKeys(): Set<Any> {
        if (last < first) return emptySet()
        val touched = LinkedHashSet<Any>()
        for (index in first..last) {
            val key = keys.getOrNull(index - keysFrom)
            if (key != null && key !== Excluded) touched += key
        }
        return touched
    }
}

/**
 * Remembers a marquee for the lazy list that [listState] drives: a rectangle the mouse drags over
 * it, as in a file manager, selecting every item it touches. Put [Modifier.marqueeSelection] with
 * it on the list, and give the list's items keys: the marquee tells them, not indices.
 *
 * ```
 * val listState = rememberLazyListState()
 * // What was selected as the drag began, for Ctrl to add to. Never read while composing.
 * var before by remember { mutableStateOf(emptySet<Long>()) }
 * val marquee =
 *     rememberMarqueeSelectionState(
 *         listState = listState,
 *         onStart = { before = selection },
 *         onSelect = { keys, additive ->
 *             val touched = keys.mapTo(mutableSetOf()) { it as Long }
 *             select(if (additive) before + touched else touched)
 *         },
 *         selectable = { _, contentType -> contentType == "song" },
 *     )
 * LazyColumn(Modifier.marqueeSelection(marquee), state = listState) {
 *     item(contentType = "header") { Header() }
 *     items(songs, key = { it.id }, contentType = { "song" }) { SongRow(it) }
 * }
 * ```
 *
 * @param listState the state given to the list
 * @param onSelect the keys of the items the marquee touches, in the list's order: each time they
 *   change while it is dragged, and once as it begins (none, when it begins beside every item).
 *   Items scrolled out of sight under it are still among them. `additive` says whether Ctrl was
 *   down as the button went down: the keys are then added to what was selected when the drag began,
 *   and otherwise are all that is selected, as in a file manager.
 * @param selectable whether an item can be selected, from its key and its content type: a header
 *   among the items says no, and is left out of the keys
 * @param onStart called as a drag becomes a marquee, before the first [onSelect]: the moment to
 *   take what is selected, for a marquee with Ctrl to add to
 * @param onEnd called as it ends: the button released, Esc pressed, or the pointer lost. What it
 *   selected stays selected.
 */
@Composable
fun rememberMarqueeSelectionState(
    listState: LazyListState,
    onSelect: (keys: Set<Any>, additive: Boolean) -> Unit,
    selectable: (key: Any, contentType: Any?) -> Boolean = { _, _ -> true },
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
): MarqueeSelectionState {
    val state = remember(listState) { MarqueeSelectionState(listState) }
    // Read when used, not kept from when the marquee was made: a list's callbacks change with what
    // it shows, and a drag must neither start over for that nor act on what was shown before.
    SideEffect {
        state.onSelect = onSelect
        state.selectable = selectable
        state.onStart = onStart
        state.onEnd = onEnd
    }
    return state
}

/**
 * Lets the mouse select items of this lazy list with a rectangle ([state]): the first button
 * pressed anywhere over the list, on a row or beside the rows, and dragged past the touch slop. The
 * rectangle follows the pointer, in the accent; near the top or bottom edge, and past it, the list
 * scrolls, faster the further out, and the rectangle keeps hold of where it began.
 *
 * Nothing else changes. A finger and a stylus scroll the list as they did, and the wheel does, even
 * during a drag; a click that does not move still reaches the row under it (to open it, or to
 * select it with Ctrl or Shift), and the second button still opens its menu ([ContextMenuArea]).
 * The pointer resting on a row, and a tooltip, are as they were.
 *
 * It goes on the list itself, a `LazyColumn` laid out from the top down, without sticky headers,
 * and with nothing between the two that pads or moves the list: the press, the pointer and the
 * rectangle are all measured in the list's bounds.
 */
fun Modifier.marqueeSelection(state: MarqueeSelectionState): Modifier =
    this then MarqueeSelectionElement(state)

private class MarqueeSelectionElement(private val state: MarqueeSelectionState) :
    ModifierNodeElement<MarqueeSelectionNode>() {
    override fun create() = MarqueeSelectionNode(state)

    override fun update(node: MarqueeSelectionNode) = node.update(state)

    override fun InspectorInfo.inspectableProperties() {
        name = "marqueeSelection"
        properties["state"] = state
    }

    override fun equals(other: Any?): Boolean =
        other is MarqueeSelectionElement && other.state === state

    override fun hashCode(): Int = state.hashCode()
}

/*
 * The rows and the list's own scrolling are below this node, so it sees each event before them,
 * in the initial pass. It takes nothing until the drag goes past the slop: up to then a click is
 * the row's. From then on it consumes every move, which calls off the row's click, and keeps the
 * list from scrolling by it.
 */
private class MarqueeSelectionNode(private var state: MarqueeSelectionState) :
    DelegatingNode(), DrawModifierNode, ObserverModifierNode, CompositionLocalConsumerModifierNode {

    private val input = delegate(SuspendingPointerInputModifierNode { awaitMarquees() })
    private var scrolling: Job? = null
    // The view Esc is listened for on, while a marquee is dragged.
    private var listening: View? = null
    private var stroke: Stroke? = null

    // Esc ends a marquee as a release does. It comes here after every view on screen has left it,
    // and before the activity's shortcuts and Android's Back: during a drag with the mouse no view
    // has the focus to take it with.
    private val escape = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
        val ends =
            event.keyCode == KeyEvent.KEYCODE_ESCAPE &&
                event.action == KeyEvent.ACTION_DOWN &&
                state.isActive
        if (ends) finish()
        ends
    }

    fun update(state: MarqueeSelectionState) {
        if (state === this.state) return
        finish()
        this.state = state
        input.resetPointerInputHandler()
    }

    private suspend fun PointerInputScope.awaitMarquees() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val buttons = currentEvent.buttons
            val primaryAlone =
                buttons.isPrimaryPressed &&
                    !buttons.isSecondaryPressed &&
                    !buttons.isTertiaryPressed
            if (down.type != PointerType.Mouse || !primaryAlone) return@awaitEachGesture
            state.press(
                down.position.x,
                down.position.y,
                currentEvent.keyboardModifiers.isCtrlPressed,
            )
            val slop = viewConfiguration.touchSlop
            var dragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                    // Up once no button is held: the pointer lost comes as a release too.
                    val released = !change.pressed
                    // The wheel scrolls the list under a marquee, as under a pointer at rest.
                    val moved =
                        event.type == PointerEventType.Move || event.type == PointerEventType.Press
                    if (!released && !moved) continue
                    if (!dragging) {
                        if (released) break
                        if ((change.position - down.position).getDistance() <= slop) continue
                        dragging = true
                        begin(change.position)
                    }
                    // The press is the marquee's up to the release, after Esc too: nothing under
                    // it is to take the rest of it for a gesture of its own.
                    change.consume()
                    if (released) break
                    if (state.isActive) moveTo(change.position)
                }
            } finally {
                if (dragging) finish()
            }
        }
    }

    private fun begin(position: Offset) {
        state.start(position.x, position.y)
        follow()
        listening =
            currentValueOf(LocalView).also { ViewCompat.addOnUnhandledKeyEventListener(it, escape) }
        invalidateDraw()
        scrollIfNeeded()
    }

    private fun moveTo(position: Offset) {
        state.moveTo(position.x, position.y)
        invalidateDraw()
        scrollIfNeeded()
    }

    /** Ends the marquee as a release does: what it selected stays selected. */
    private fun finish() {
        scrolling?.cancel()
        scrolling = null
        listening?.let { ViewCompat.removeOnUnhandledKeyEventListener(it, escape) }
        listening = null
        if (isAttached) observeReads {}
        state.end()
        invalidateDraw()
    }

    override fun onDetach() {
        finish()
    }

    // What the marquee touches changes as the list moves under it, scrolled by the marquee itself
    // or by the wheel, while the pointer stays where it is.
    private fun follow() = observeReads { state.listState.layoutInfo }

    override fun onObservedReadsChanged() {
        if (!state.isActive) return
        follow()
        state.report()
    }

    /** Starts the list scrolling, if the pointer is near enough an edge and it is not already. */
    private fun scrollIfNeeded() {
        if (scrolling?.isActive == true || scrollSpeed() == 0f) return
        scrolling = coroutineScope.launch {
            var then = withFrameNanos { it }
            while (true) {
                val speed = scrollSpeed()
                if (speed == 0f) break
                val now = withFrameNanos { it }
                state.listState.dispatchRawDelta(speed * (now - then) / 1_000_000_000f)
                then = now
            }
        }
    }

    /** How fast the list is to scroll now, for where the pointer is; not past either end. */
    private fun scrollSpeed(): Float {
        val state = state
        if (!isAttached || !state.isActive) return 0f
        val list = state.listState
        val speed =
            with(requireDensity()) {
                marqueeScrollSpeed(
                    y = state.pointerY,
                    height = list.layoutInfo.viewportSize.height.toFloat(),
                    zone = ScrollZone.toPx(),
                    reach = ScrollReach.toPx(),
                    maxSpeed = MaxScrollSpeed.toPx(),
                )
            }
        return when {
            speed < 0f && !list.canScrollBackward -> 0f
            speed > 0f && !list.canScrollForward -> 0f
            else -> speed
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val state = state
        if (!state.isActive) return
        val width = BorderWidth.toPx()
        // Past an edge by no more than the border, so that the side out of sight is not drawn.
        val anchorY =
            state.anchorY(state.listState.layoutInfo).coerceIn(-width, size.height + width)
        val pointerX = state.pointerX.coerceIn(-width, size.width + width)
        val pointerY = state.pointerY.coerceIn(-width, size.height + width)
        val left = min(state.anchorX, pointerX)
        val top = min(anchorY, pointerY)
        val right = max(state.anchorX, pointerX)
        val bottom = max(anchorY, pointerY)
        val colors = currentValueOf(LocalStudioColors)
        clipRect {
            drawRect(colors.accentSoft, Offset(left, top), Size(right - left, bottom - top))
            drawRect(
                color = colors.accent,
                topLeft = Offset(left + width / 2, top + width / 2),
                size = Size(max(right - left - width, 0f), max(bottom - top - width, 0f)),
                style = strokeOf(width),
            )
        }
    }

    private fun strokeOf(width: Float): Stroke =
        stroke?.takeIf { it.width == width } ?: Stroke(width).also { stroke = it }
}

private fun slotOf(y: Float, items: List<LazyListItemInfo>): Int =
    marqueeSlot(y, items.size, { items[it].index }, { items[it].offset }, { items[it].size })

private const val NoItem = -1

/** What an item that cannot be selected is kept as, among the keys seen during a drag. */
private object Excluded

private val BorderWidth = 1.dp

/** How near an edge the pointer starts the list scrolling: about a row of the table. */
private val ScrollZone = 40.dp

/** How far past the edge the list keeps scrolling faster. */
private val ScrollReach = 80.dp

/** The fastest the list scrolls, a second: some seventy rows of the table. */
private val MaxScrollSpeed = 3000.dp
