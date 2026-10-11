/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.theme.Studio

/** The height of the app's chips: a desktop toolbar's, which a finger still finds. */
private val ChipHeight = 30.dp

/**
 * Something a list is narrowed down by: a pill with a hairline edge that takes the accent once it
 * is on, and ticks under the finger.
 *
 * On, it shows a check where its [icon] was: the color alone would not say it to everyone.
 *
 * @param iconTint the icon's own color, for a filter that is a warning (what needs attention)
 */
@Composable
fun ToggleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
) {
    val haptics = LocalHapticFeedback.current
    val colors = Studio.colors
    val motion = MaterialTheme.motionScheme
    val container by
        animateColorAsState(
            targetValue =
                when {
                    selected -> colors.accentSoft
                    colors.isDark -> Color.Transparent
                    else -> colors.field
                },
            animationSpec = motion.fastEffectsSpec(),
            label = "ChipContainer",
        )
    val edge by
        animateColorAsState(
            targetValue = if (selected) colors.accent.copy(alpha = 0.5f) else colors.lineStrong,
            animationSpec = motion.fastEffectsSpec(),
            label = "ChipEdge",
        )
    val leading = if (selected) Icons.Rounded.Check else icon
    CompositionLocalProvider(
        LocalContentColor provides if (selected) colors.accent else colors.textSoft
    ) {
        Row(
            modifier =
                modifier
                    .height(ChipHeight)
                    .clip(CircleShape)
                    .background(container, CircleShape)
                    .border(1.dp, edge, CircleShape)
                    .toggleable(
                        value = selected,
                        interactionSource = null,
                        indication = ripple(),
                        role = Role.Checkbox,
                        onValueChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onClick()
                        },
                    )
                    .padding(start = if (leading != null) 9.dp else 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(
                    leading,
                    contentDescription = null,
                    tint =
                        if (!selected && iconTint.isSpecified) iconTint
                        else LocalContentColor.current,
                    modifier = Modifier.size(15.dp),
                )
            }
            ChipText(label, selected)
        }
    }
}

/**
 * A chip that does something rather than being chosen: it never looks on, so it is not mistaken for
 * a filter that is.
 *
 * @param opensMenu whether it opens a menu, which an arrow at its end then says
 */
@Composable
fun ActionChip(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    opensMenu: Boolean = false,
) {
    val colors = Studio.colors
    CompositionLocalProvider(LocalContentColor provides colors.textSoft) {
        Row(
            modifier =
                modifier
                    .height(ChipHeight)
                    .clip(CircleShape)
                    .background(
                        if (colors.isDark) Color.Transparent else colors.field,
                        CircleShape,
                    )
                    .border(1.dp, colors.lineStrong, CircleShape)
                    .clickable(
                        interactionSource = null,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(start = 9.dp, end = if (opensMenu) 7.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            ChipText(label, selected = false)
            if (opensMenu) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChipText(label: String, selected: Boolean) {
    Text(
        label,
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A chip that only says something: what a thing is or belongs to, with an icon for the kind of
 * thing. It is not pressed, so it has none of the edge of the chips that are: a quiet pill.
 */
@Composable
fun LabelChip(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = Studio.colors
    Row(
        modifier =
            modifier
                .height(26.dp)
                .background(colors.hover, CircleShape)
                .padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
