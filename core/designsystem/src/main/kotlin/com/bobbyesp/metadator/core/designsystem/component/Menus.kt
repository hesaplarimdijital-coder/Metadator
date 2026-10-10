/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.bobbyesp.metadator.core.designsystem.theme.LocalBackdropHaze
import com.bobbyesp.metadator.core.designsystem.theme.MetadatorBlurDefaults
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.blurHalo
import com.bobbyesp.metadator.core.designsystem.theme.frosted
import dev.chrisbanes.haze.HazeState

/**
 * One thing a menu can do.
 *
 * @param shortcut the keys that do the same, written at the end of the item where there is a
 *   keyboard to press them on ("Ctrl K")
 * @param iconTint the icon's own color, for the item a menu is mostly for (finding a cover)
 */
@Immutable
class MenuAction(
    val text: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val shortcut: String? = null,
    val iconTint: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

/** The corners of a menu, as of the app's other floating surfaces. */
val StudioMenuShape = RoundedCornerShape(12.dp)

/**
 * An item of a [PopupMenu], as a desktop's: a line of text with its icon before it, and after it
 * the keys that do the same, or a check for the option that is on.
 *
 * @param checked whether the option it is is on; null for an item that is an action
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    shortcut: String? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
) {
    val colors = Studio.colors
    DropdownMenuItem(
        onClick = onClick,
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(StudioMenuItemHeight),
        leadingIcon =
            icon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint =
                            if (iconTint.isSpecified && enabled) iconTint
                            else LocalContentColor.current,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        trailingContent =
            when {
                checked == true -> {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = colors.accent) }
                }
                shortcut != null -> {
                    { Kbd(shortcut, Modifier.padding(start = 12.dp)) }
                }
                else -> null
            },
        enabled = enabled,
        colors =
            MenuDefaults.itemColors(
                textColor = colors.text,
                leadingIconColor = colors.textSoft,
                trailingIconColor = colors.textTertiary,
                disabledTextColor = colors.textTertiary,
                disabledLeadingIconColor = colors.textTertiary.copy(alpha = 0.6f),
                disabledTrailingIconColor = colors.textTertiary.copy(alpha = 0.6f),
            ),
        contentPadding = PaddingValues(horizontal = 10.dp),
    )
}

/** A hairline between the parts of one group of a [PopupMenu]. */
@Composable
fun StudioMenuDivider() {
    Box(
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Studio.colors.line)
    )
}

/** Tall enough for a finger, short enough that a menu reads as a desktop's. */
private val StudioMenuItemHeight = 38.dp

/**
 * A menu: Material's popup, which places it, opens it and moves through it with the keys, around
 * one or more [PopupMenuGroup]s.
 *
 * Over a screen the shell records ([LocalBackdropHaze]) the menu is frosted, and lifted off the
 * screen by a blur halo where the device can draw one; anywhere else it is Material's own, shadow
 * included.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdropHaze.current
    if (backdrop != null && MetadatorBlurDefaults.isHaloSupported) {
        HaloMenuPopup(expanded, onDismissRequest, backdrop, modifier, content)
    } else {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * A group of a [PopupMenu]: a raised surface with a hairline edge, frosted over the screen where
 * the device can blur it. A menu of two kinds of things has two, with a gap between them; or one,
 * with a [StudioMenuDivider].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = Studio.colors
    val shape = StudioMenuShape
    val shapes = MenuGroupShapes(shape = shape, inactiveShape = shape)
    val border = BorderStroke(1.dp, colors.lineStrong)
    val backdrop = LocalBackdropHaze.current
    if (backdrop == null || !MetadatorBlurDefaults.isBlurSupported) {
        DropdownMenuGroup(
            shapes = shapes,
            containerColor = colors.raisedHigh,
            border = border,
            contentPadding = MenuGroupPadding,
            content = content,
        )
        return
    }
    // The frost goes around the group, because the group's own modifier lands inside its
    // container. The group keeps one shape, hovered or not, so that the frost, clipped to it,
    // always matches.
    Box(
        // What lifts the group is the menu's halo; where there is none (Android 12), Material's
        // shadow, cast from outside the frost, whose clip would cut a shadow of the group's own.
        Modifier.shadow(
                elevation =
                    if (MetadatorBlurDefaults.isHaloSupported) 0.dp
                    else MenuDefaults.ShadowElevation,
                shape = shape,
            )
            .frosted(
                state = backdrop,
                style = MetadatorBlurDefaults.surfaceStyle(colors.raisedHigh),
                shape = shape,
            )
    ) {
        DropdownMenuGroup(
            shapes = shapes,
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            border = border,
            contentPadding = MenuGroupPadding,
            content = content,
        )
    }
}

private val MenuGroupPadding = PaddingValues(6.dp)

/** A menu of plain actions. Choosing one closes the menu first, so it never outlives its screen. */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    PopupMenu(expanded, onDismissRequest, modifier) {
        PopupMenuGroup {
            actions.forEach { action ->
                StudioMenuItem(
                    text = action.text,
                    onClick = {
                        onDismissRequest()
                        action.onClick()
                    },
                    icon = action.icon,
                    iconTint = action.iconTint,
                    shortcut = action.shortcut,
                    enabled = action.enabled,
                )
            }
        }
    }
}

/**
 * Material's menu popup, lifted off what it opens over by a blur halo instead of a shadow: the
 * content around the menu goes out of focus, most at its edge, and is sharp again a little further
 * out.
 *
 * A popup's window ends where its content does, so the halo has to take room inside it. That room
 * is made up for in two places:
 * - the menu is still placed where Material would place it, as if the room were not there;
 * - a tap on the halo closes the menu, as a tap anywhere outside it would.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaloMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: HazeState,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spread = MetadatorBlurDefaults.MenuHaloSpread
    val marginPx =
        with(LocalDensity.current) { MetadatorBlurDefaults.haloMargin(spread).roundToPx() }
    val menuPosition =
        MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below)
    val positionProvider =
        remember(menuPosition, marginPx) { HaloMarginPositionProvider(menuPosition, marginPx) }
    val dismiss by rememberUpdatedState(onDismissRequest)

    // The halo comes into focus on its own, not inside Material's open animation: in there it
    // would be scaled by the menu's spring, overshoot included, and cut to the menu's bounds while
    // the menu fades. The scheme's effects specs never overshoot.
    val haloStrength = remember { Animatable(0f) }
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(expanded) {
        if (expanded) {
            haloStrength.snapTo(0f)
            haloStrength.animateTo(1f, enterSpec)
        } else {
            haloStrength.animateTo(0f, exitSpec)
        }
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // Material applies this outside its own scale and fade, which only the menu goes through.
        modifier =
            modifier
                .pointerInput(marginPx) {
                    detectTapGestures { tap ->
                        val menu =
                            IntRect(
                                left = marginPx,
                                top = marginPx,
                                right = size.width - marginPx,
                                bottom = size.height - marginPx,
                            )
                        if (!menu.contains(IntOffset(tap.x.toInt(), tap.y.toInt()))) dismiss()
                    }
                }
                .blurHalo(
                    state = backdrop,
                    // However many groups, they read as one menu: the halo follows its outer
                    // corners.
                    shape = StudioMenuShape,
                    spread = spread,
                    strength = haloStrength.value,
                    reserveSpace = true,
                ),
        popupPositionProvider = positionProvider,
        properties = HaloMenuProperties,
        content = content,
    )
}

/**
 * Places the menu where [menu] would, as if the popup were only the menu, then moves the popup out
 * by [margin] so the halo around it lands around it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class HaloMarginPositionProvider(
    private val menu: DropdownMenuPopupPositionProvider,
    private val margin: Int,
) : DropdownMenuPopupPositionProvider {

    override val transformOrigin
        get() = menu.transformOrigin

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val menuSize =
            IntSize(
                (popupContentSize.width - 2 * margin).coerceAtLeast(0),
                (popupContentSize.height - 2 * margin).coerceAtLeast(0),
            )
        val position = menu.calculatePosition(anchorBounds, windowSize, layoutDirection, menuSize)
        return IntOffset(position.x - margin, position.y - margin)
    }
}

/**
 * Material's menu properties, unclipped: the menu itself is kept on screen by the position above,
 * and a window kept on screen as a whole would push the menu off its place to fit a halo that may
 * run past the screen's edge.
 */
private val HaloMenuProperties =
    PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = false,
        usePlatformDefaultWidth = false,
    )
