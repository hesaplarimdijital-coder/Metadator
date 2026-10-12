/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.LocalBackdropHaze
import com.bobbyesp.metadator.core.designsystem.theme.Studio

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
 * one or more [PopupMenuGroup]s, which are what it looks like.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

/**
 * A group of a [PopupMenu]: a pane of glass over the screen it opens over, as the bars floating
 * over a list are, with nothing drawn around it: what is beneath shows through it, blurred, and its
 * edge catches the light. A menu of two kinds of things has two, with a gap between them; or one,
 * with a [StudioMenuDivider].
 *
 * Where nothing of the screen is recorded ([LocalBackdropHaze] null: a preview, a window of its
 * own) it is the raised tone, solid, with a hairline edge.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = Studio.colors
    val shape = StudioMenuShape
    val shapes = MenuGroupShapes(shape = shape, inactiveShape = shape)
    val backdrop = LocalBackdropHaze.current
    if (backdrop == null) {
        DropdownMenuGroup(
            shapes = shapes,
            containerColor = colors.raisedHigh,
            border = BorderStroke(1.dp, colors.lineStrong),
            contentPadding = MenuGroupPadding,
            content = content,
        )
        return
    }
    // The glass goes around the group, because the group's own modifier lands inside its
    // container. The group keeps one shape, hovered or not, so that the glass, clipped to it,
    // always matches.
    GlassSurface(backdrop = backdrop, shape = shape) {
        DropdownMenuGroup(
            shapes = shapes,
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            contentPadding = MenuGroupPadding,
            content = content,
        )
    }
}

private val MenuGroupPadding = PaddingValues(6.dp)

/**
 * What a menu shows as it closes: what it showed open. Choosing an item changes the state the menu
 * is built from (removing the cover takes "Remove cover" out of it, sorting moves the check), and a
 * menu that followed the change changed as it faded, in the user's eye. Built from what this gives,
 * a menu keeps its items until it is gone.
 */
@Composable
fun <T> heldWhileClosing(expanded: Boolean, value: T): T {
    val held = remember { Held(value) }
    if (expanded) held.value = value
    return held.value
}

private class Held<T>(var value: T)

/** A menu of plain actions. Choosing one closes the menu first, so it never outlives its screen. */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val shown = heldWhileClosing(expanded, actions)
    PopupMenu(expanded, onDismissRequest, modifier) {
        PopupMenuGroup {
            shown.forEach { action ->
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
