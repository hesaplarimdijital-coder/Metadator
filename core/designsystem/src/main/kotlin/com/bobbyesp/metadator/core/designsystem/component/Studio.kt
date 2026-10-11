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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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

    /**
     * The height of an editor's foot, where it says what changed and saves it: what floats over the
     * screen (a message) keeps above it.
     */
    val FooterHeight = 64.dp

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
                    Brush.linearGradient(listOf(Color(0xFF4C9AFF), Color(0xFF3B4CF2))),
                    RoundedCornerShape(size * 0.28f),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            BrandGlyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * The launcher icon's mark, as the launcher shows it: the icon's inner 72 of 108, where a tilted
 * tag has an M and a sparkle cut out of it. The same path as `metamatik_icon_foreground`.
 */
private val BrandGlyph: ImageVector by lazy {
    ImageVector.Builder(
            name = "MetamatikMark",
            defaultWidth = 72.dp,
            defaultHeight = 72.dp,
            viewportWidth = 72f,
            viewportHeight = 72f,
        )
        .addGroup(
            name = "Tilt",
            rotate = -12f,
            pivotX = 54f,
            pivotY = 54f,
            scaleX = 0.86f,
            scaleY = 0.86f,
            translationX = -18f,
            translationY = -18f,
        )
        .addPath(
            pathData = addPathNodes(MarkPath),
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd,
        )
        .clearGroup()
        .build()
}

private const val MarkPath =
    "M32.58,35.00L32.17,35.03L31.76,35.11L31.37,35.26L31.00,35.45L30.66,35.69L30.36,35.98" +
        "L30.11,36.31L19.16,52.31L18.96,52.65L18.80,53.02L18.69,53.40L18.64,53.80L18.64,54.20" +
        "L18.69,54.60L18.80,54.98L18.96,55.35L19.16,55.69L30.11,71.69L30.36,72.02L30.66,72.31" +
        "L31.00,72.55L31.37,72.74L31.76,72.89L32.17,72.97L32.58,73.00L69.93,73.00L70.07,73.00" +
        "L70.54,72.97L70.83,72.94L71.22,72.89L71.51,72.83L71.89,72.73L72.17,72.65L72.54,72.52" +
        "L72.81,72.40L73.17,72.24L73.43,72.10L73.76,71.90L74.01,71.73L74.32,71.50L74.55,71.31" +
        "L74.84,71.05L75.05,70.84L75.31,70.55L75.50,70.32L75.73,70.01L75.90,69.76L76.10,69.43" +
        "L76.24,69.17L76.40,68.81L76.52,68.54L76.65,68.17L76.73,67.89L76.83,67.51L76.89,67.22" +
        "L76.94,66.83L76.97,66.54L77.00,66.07L77.00,65.93L77.00,42.07L77.00,41.93L76.97,41.46" +
        "L76.94,41.17L76.89,40.78L76.83,40.49L76.73,40.11L76.65,39.83L76.52,39.46L76.40,39.19" +
        "L76.24,38.83L76.10,38.57L75.90,38.24L75.73,37.99L75.50,37.68L75.31,37.45L75.05,37.16" +
        "L74.84,36.95L74.55,36.69L74.32,36.50L74.01,36.27L73.76,36.10L73.43,35.90L73.17,35.76" +
        "L72.81,35.60L72.54,35.48L72.17,35.35L71.89,35.27L71.51,35.17L71.22,35.11L70.83,35.06" +
        "L70.54,35.03L70.08,35.00L69.94,35.00ZM31.50,48.60L31.57,49.06L31.65,49.49" +
        "L31.74,49.89L31.83,50.27L31.94,50.63L32.05,50.96L32.18,51.27L32.31,51.55L32.46,51.82" +
        "L32.62,52.07L32.80,52.29L33.00,52.50L33.21,52.70L33.43,52.88L33.68,53.04L33.95,53.19" +
        "L34.23,53.32L34.54,53.45L34.87,53.56L35.23,53.67L35.61,53.76L36.01,53.85L36.44,53.93" +
        "L36.90,54.00L36.44,54.07L36.01,54.15L35.61,54.24L35.23,54.33L34.87,54.44L34.54,54.55" +
        "L34.23,54.68L33.95,54.81L33.68,54.96L33.43,55.12L33.21,55.30L33.00,55.50L32.80,55.71" +
        "L32.62,55.93L32.46,56.18L32.31,56.45L32.18,56.73L32.05,57.04L31.94,57.37L31.83,57.73" +
        "L31.74,58.11L31.65,58.51L31.57,58.94L31.50,59.40L31.43,58.94L31.35,58.51L31.26,58.11" +
        "L31.17,57.73L31.06,57.37L30.95,57.04L30.82,56.73L30.69,56.45L30.54,56.18L30.38,55.93" +
        "L30.20,55.71L30.00,55.50L29.79,55.30L29.57,55.12L29.32,54.96L29.05,54.81L28.77,54.68" +
        "L28.46,54.55L28.13,54.44L27.77,54.33L27.39,54.24L26.99,54.15L26.56,54.07L26.10,54.00" +
        "L26.56,53.93L26.99,53.85L27.39,53.76L27.77,53.67L28.13,53.56L28.46,53.45L28.77,53.32" +
        "L29.05,53.19L29.32,53.04L29.57,52.88L29.79,52.70L30.00,52.50L30.20,52.29L30.38,52.07" +
        "L30.54,51.82L30.69,51.55L30.82,51.27L30.95,50.96L31.06,50.63L31.17,50.27L31.26,49.89" +
        "L31.35,49.49L31.43,49.06ZM48.38,51.53L52.90,59.67L53.10,59.97L53.35,60.24" +
        "L53.63,60.47L53.94,60.65L54.28,60.79L54.64,60.87L55.00,60.90L55.36,60.87L55.72,60.79" +
        "L56.06,60.65L56.37,60.47L56.65,60.24L56.90,59.97L57.10,59.67L61.62,51.53L64.67,64.07" +
        "L64.79,64.43L64.96,64.76L65.18,65.06L65.45,65.33L65.75,65.55L66.09,65.72L66.45,65.84" +
        "L66.82,65.89L67.20,65.89L67.57,65.83L67.93,65.71L68.26,65.54L68.56,65.32L68.83,65.05" +
        "L69.05,64.75L69.22,64.41L69.34,64.05L69.39,63.68L69.39,63.30L69.33,62.93L64.83,44.43" +
        "L64.71,44.07L64.54,43.73L64.31,43.42L64.03,43.15L63.72,42.93L63.38,42.77L63.01,42.65" +
        "L62.63,42.60L62.25,42.61L61.87,42.68L61.51,42.81L61.18,43.00L60.87,43.23L60.61,43.52" +
        "L60.40,43.83L55.00,53.56L49.60,43.83L49.39,43.52L49.13,43.23L48.82,43.00L48.49,42.81" +
        "L48.13,42.68L47.75,42.61L47.37,42.60L46.99,42.65L46.62,42.77L46.28,42.93L45.97,43.15" +
        "L45.69,43.42L45.46,43.73L45.29,44.07L45.17,44.43L40.67,62.93L40.61,63.30L40.61,63.68" +
        "L40.66,64.05L40.78,64.41L40.95,64.75L41.17,65.05L41.44,65.32L41.74,65.54L42.07,65.71" +
        "L42.43,65.83L42.80,65.89L43.18,65.89L43.55,65.84L43.91,65.72L44.25,65.55L44.55,65.33" +
        "L44.82,65.06L45.04,64.76L45.21,64.43L45.33,64.07Z"

/** A hairline between two parts of a panel. */
@Composable
fun StudioDivider(modifier: Modifier = Modifier, color: Color = Studio.colors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

private const val DisabledAlpha = 0.45f
