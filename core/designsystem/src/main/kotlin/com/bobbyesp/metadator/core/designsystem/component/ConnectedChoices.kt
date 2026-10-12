/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.theme.Studio

/** One of the options of a [ConnectedChoices]. */
@Immutable class Choice<T>(val value: T, val label: String, val icon: ImageVector)

/**
 * A few exclusive options as a segmented control: a track, and in it the chosen option raised out
 * of it. For what a tab row or a short radio list would otherwise be.
 *
 * @param fill whether the options share the width; otherwise each is as wide as its label, for a
 *   row that scrolls.
 * @param iconOnly shows the options' icons instead of their labels, which they are then named by:
 *   the views of a list, in a toolbar
 */
@Composable
fun <T> ConnectedChoices(
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    iconOnly: Boolean = false,
    height: Dp = if (iconOnly) 26.dp else 30.dp,
) {
    val colors = Studio.colors
    val track = RoundedCornerShape(10.dp)
    val optionShape = RoundedCornerShape(7.dp)
    Row(
        modifier =
            modifier
                .selectableGroup()
                .clip(track)
                .background(if (colors.isDark) colors.field else colors.ground, track)
                .border(1.dp, colors.line, track)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choices.forEach { choice ->
            val checked = choice.value == selected
            val container by
                animateColorAsState(
                    targetValue = if (checked) colors.segment else Color.Transparent,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "ChoiceContainer",
                )
            val option: @Composable (Modifier) -> Unit = { placement ->
                CompositionLocalProvider(
                    LocalContentColor provides if (checked) colors.text else colors.textSecondary
                ) {
                    Row(
                        modifier =
                            placement
                                .heightIn(min = height)
                                .then(
                                    // On a light track the chosen one is lifted, as a key is.
                                    if (checked && !colors.isDark)
                                        Modifier.shadow(1.dp, optionShape)
                                    else Modifier
                                )
                                .clip(optionShape)
                                .background(container, optionShape)
                                .selectable(
                                    selected = checked,
                                    interactionSource = null,
                                    indication = ripple(),
                                    role = Role.RadioButton,
                                    onClick = { onSelect(choice.value) },
                                )
                                .then(
                                    if (iconOnly) {
                                        Modifier.semantics { contentDescription = choice.label }
                                    } else Modifier
                                )
                                // Options sharing the width have the width to give, and labels
                                // of some length in some languages to fit in it.
                                .padding(
                                    horizontal =
                                        when {
                                            iconOnly -> 7.dp
                                            fill -> 6.dp
                                            else -> 12.dp
                                        }
                                ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (iconOnly) {
                            Icon(choice.icon, contentDescription = null, Modifier.size(16.dp))
                        } else {
                            Text(
                                choice.label,
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight =
                                            if (checked) FontWeight.Bold else FontWeight.Medium
                                    ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                // A label too long for its share is set smaller before it is
                                // cut short: "Sanatçı…" named nothing.
                                autoSize =
                                    if (fill) {
                                        TextAutoSize.StepBased(
                                            minFontSize = 11.sp,
                                            maxFontSize = 14.sp,
                                            stepSize = 0.5.sp,
                                        )
                                    } else null,
                            )
                        }
                    }
                }
            }
            // A tooltip names an option shown only by its icon. It wraps the option, so it is
            // the one placed in the row, and an option that shares the width is never one.
            if (iconOnly) Tooltip(choice.label) { option(Modifier) }
            else option(if (fill) Modifier.weight(1f) else Modifier)
        }
    }
}
