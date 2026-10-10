/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.theme.Studio

/*
 * The pieces of the Studio look that Material has no component for: the panels a wide window is
 * laid out in, the buttons of a desktop tool (smaller than Material's, with the key that does the
 * same written on them), and the small labels a table of songs is read by.
 */

/** How something that says how a thing stands is colored: a verdict, a state. */
enum class StudioTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger,
}

/** A tone's text and the tint behind it. */
@Immutable class ToneColors(val content: Color, val container: Color)

@Composable
@ReadOnlyComposable
fun StudioTone.colors(): ToneColors {
    val colors = Studio.colors
    return when (this) {
        StudioTone.Neutral -> ToneColors(colors.textSecondary, colors.textSecondary.copy(0.12f))
        StudioTone.Accent -> ToneColors(colors.accent, colors.accentSoft)
        StudioTone.Success -> ToneColors(colors.success, colors.successSoft)
        StudioTone.Warning -> ToneColors(colors.warning, colors.warningSoft)
        StudioTone.Danger -> ToneColors(colors.danger, colors.dangerSoft)
    }
}

object StudioDefaults {
    /** Between the panels of a wide window, and around them. */
    val PanelGap = 8.dp

    /** The width of the panel at the end of a wide window that shows what is chosen. */
    val InspectorWidth = 380.dp

    val SidebarWidth = 228.dp

    val ButtonHeight = 36.dp
    val RowHeight = 40.dp

    /** The type of keys and of what a file is: a format, a size. */
    val MonoStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        )

    val ButtonShape = RoundedCornerShape(10.dp)
    val ChipShape = RoundedCornerShape(6.dp)
}

/**
 * Whether the window is laid out as panels on a ground, as a tablet's or a desktop's is, rather
 * than being one screen, as a phone's is. The host decides it from the window, and the panels are
 * drawn by the screens: a screen draws its own chrome by it.
 */
val LocalPanels = staticCompositionLocalOf { false }

/**
 * A panel: a surface with a hairline edge, lying on the ground of the window. A wide window is laid
 * out as panels with gaps between them; on a phone the screen itself is the panel.
 */
@Composable
fun Modifier.studioPanel(
    color: Color = Studio.colors.panel,
    shape: Shape = MaterialTheme.shapes.large,
): Modifier = clip(shape).background(color, shape).border(1.dp, Studio.colors.line, shape)

/** What a button is for: the one thing a screen is for, one of several, or a quiet extra. */
enum class StudioButtonStyle {
    Primary,
    Secondary,
    Ghost,
}

/**
 * A desktop tool's button: shorter than Material's, with a hairline edge unless it is the main one,
 * and with the key that does the same written after its text where there is a keyboard.
 *
 * @param shortcut the keys that do the same, as they are written on a key cap ("Ctrl S")
 * @param busy shows that what it started is still running, in place of its icon
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: StudioButtonStyle = StudioButtonStyle.Secondary,
    enabled: Boolean = true,
    shortcut: String? = null,
    iconTint: Color = Color.Unspecified,
    busy: Boolean = false,
) {
    val colors = Studio.colors
    val shape = StudioDefaults.ButtonShape
    val container =
        when (style) {
            StudioButtonStyle.Primary -> colors.accentFill
            StudioButtonStyle.Secondary -> colors.field
            StudioButtonStyle.Ghost -> Color.Transparent
        }
    val content =
        when (style) {
            StudioButtonStyle.Primary -> colors.onAccentFill
            StudioButtonStyle.Secondary -> colors.text
            StudioButtonStyle.Ghost -> colors.textSoft
        }
    val interactions = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier =
                modifier
                    .heightIn(min = StudioDefaults.ButtonHeight)
                    .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
                    .clip(shape)
                    .background(container, shape)
                    .then(
                        if (style == StudioButtonStyle.Secondary) {
                            Modifier.border(1.dp, colors.fieldLine, shape)
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = interactions,
                        indication = ripple(),
                        enabled = enabled && !busy,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(start = if (icon != null || busy) 12.dp else 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = content,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (iconTint.isSpecified) iconTint else content,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight =
                            if (style == StudioButtonStyle.Primary) FontWeight.SemiBold
                            else FontWeight.Medium
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (shortcut != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    shortcut,
                    style = StudioDefaults.MonoStyle,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A button that is only an icon, as a toolbar has them: square, quiet until pointed at, and named
 * by a tooltip.
 *
 * @param selected whether it is the one that is on, of a few (a view of a list)
 */
@Composable
fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(8.dp)
    val content =
        when {
            tint.isSpecified -> tint
            selected -> colors.text
            else -> colors.textSoft
        }
    Tooltip(contentDescription, modifier) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Box(
                modifier =
                    Modifier.size(size)
                        .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
                        .clip(shape)
                        .background(if (selected) colors.segment else Color.Transparent, shape)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            interactionSource = null,
                            indication = ripple(),
                            onClick = onClick,
                        )
                        .semantics { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** A key as it is written on its cap: "Ctrl", "F", "Esc". */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    val colors = Studio.colors
    val shape = StudioDefaults.ChipShape
    Text(
        text,
        modifier =
            modifier
                .background(colors.hover, shape)
                .border(1.dp, colors.lineStrong, shape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        style = StudioDefaults.MonoStyle,
        color = colors.textTertiary,
        maxLines = 1,
    )
}

/**
 * Says how something stands, in a pill of its tone with a dot before it: a song's verdict in a
 * table, where a color alone would not say it to everyone.
 */
@Composable
fun StatusPill(label: String, tone: StudioTone, modifier: Modifier = Modifier) {
    val colors = tone.colors()
    Row(
        modifier =
            modifier
                .height(22.dp)
                .background(colors.container, CircleShape)
                .padding(start = 8.dp, end = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colors.content, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A fact about a file, as a tool writes it: "FLAC · 16 bit · 44,1 kHz", "1500 × 1500". */
@Composable
fun FactChip(text: String, modifier: Modifier = Modifier, tone: StudioTone? = null) {
    val colors = Studio.colors
    val toneColors = tone?.colors()
    Text(
        text,
        modifier =
            modifier
                .background(toneColors?.container ?: colors.hover, StudioDefaults.ChipShape)
                .padding(horizontal = 7.dp, vertical = 4.dp),
        style = StudioDefaults.MonoStyle,
        color = toneColors?.content ?: colors.textSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The title of a group of a sidebar: "Library", "Smart lists". */
@Composable
fun SidebarTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Studio.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A place a sidebar goes to, with how many things are there. The one shown is tinted with the
 * accent; the others are quieter than the content they lead to.
 *
 * @param iconTint the icon's own color, for a place that is a warning (what needs attention)
 */
@Composable
fun SidebarItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: String? = null,
    iconTint: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Studio.colors
    val shape = MaterialTheme.shapes.small
    val background by
        animateColorAsState(
            targetValue = if (selected) colors.accentSoft else Color.Transparent,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "SidebarItem",
        )
    CompositionLocalProvider(
        LocalContentColor provides if (selected) colors.text else colors.textSoft
    ) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .clip(shape)
                    .background(background, shape)
                    .selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = ripple(),
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint =
                    when {
                        iconTint.isSpecified -> iconTint
                        selected -> colors.accent
                        else -> LocalContentColor.current
                    },
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    count,
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                    color = if (selected) colors.textSoft else colors.textTertiary,
                    maxLines = 1,
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * A desktop's checkbox, small enough for a row of a table. [onClick] is null where something else
 * toggles it, as the row does on a phone.
 */
@Composable
fun StudioCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Studio.colors
    val shape = RoundedCornerShape(4.dp)
    val on = state != ToggleableState.Off
    val fill by
        animateColorAsState(
            targetValue = if (on) colors.accentFill else Color.Transparent,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "CheckboxFill",
        )
    Box(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.triStateToggleable(
                            state = state,
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = 16.dp),
                            enabled = enabled,
                            role = Role.Checkbox,
                            onClick = onClick,
                        )
                    } else Modifier
                )
                .size(16.dp)
                .clip(shape)
                .background(fill, shape)
                .then(
                    if (on) Modifier
                    else Modifier.border(1.5.dp, colors.textTertiary.copy(alpha = 0.7f), shape)
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Icon(
                if (state == ToggleableState.On) Icons.Rounded.Check else Icons.Rounded.Remove,
                contentDescription = null,
                tint = colors.onAccentFill,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * A switch as Samsung's and Apple's are: a pill with a white thumb, smaller than Material's, for a
 * row of a panel. The row is usually what is pressed; the switch shows it.
 */
@Composable
fun StudioSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Studio.colors
    val track by
        animateColorAsState(
            targetValue = if (checked) colors.accentFill else colors.switchOff,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "SwitchTrack",
        )
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 16.dp else 0.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "SwitchThumb",
        )
    Box(
        modifier =
            modifier
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            interactionSource = null,
                            indication = null,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                    } else Modifier
                )
                .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
                .size(width = 40.dp, height = 24.dp)
                .background(track, CircleShape)
                .padding(2.dp)
    ) {
        Box(
            Modifier.offset(x = thumbOffset)
                .size(20.dp)
                .shadow(1.5.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/**
 * The app's mark, as the sidebar and the about page show it: the glyph of the launcher's icon on
 * its gradient, in a rounded square.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF5AA2FF), Color(0xFF1E5FD8))),
                    RoundedCornerShape(size * 0.3f),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            BrandGlyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.86f),
        )
    }
}

/** The launcher icon's glyph: a tag with a sound wave in it. */
private val BrandGlyph: ImageVector by lazy {
    ImageVector.Builder(
            name = "MetadatorGlyph",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f,
        )
        .addPath(
            pathData =
                addPathNodes(
                    "M14.857,18.921L14.857,17.905C14.857,16.222 16.222,14.857 17.905,14.857" +
                        "L30.095,14.857C31.778,14.857 33.143,16.222 33.143,17.905L33.143,18.921" +
                        "M28.063,20.952L28.063,27.048M32.127,21.968L32.127,26.032" +
                        "M24,19.429L24,28.571M19.937,20.952L19.937,27.048" +
                        "M15.873,21.968L15.873,26.032M14.857,29.079L14.857,30.095" +
                        "C14.857,31.778 16.222,33.143 17.905,33.143L30.095,33.143" +
                        "C31.778,33.143 33.143,31.778 33.143,30.095L33.143,29.079"
                ),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** A hairline between two parts of a panel. */
@Composable
fun StudioDivider(modifier: Modifier = Modifier, color: Color = Studio.colors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

private const val DisabledAlpha = 0.45f
