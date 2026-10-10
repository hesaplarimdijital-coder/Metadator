/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.Studio

/**
 * What a screen shows instead of content: empty, needs a permission, or failed. Not a card of its
 * own: it is the panel's content, centered, with its icon in a tinted square and the one thing to
 * do about it as the panel's main button.
 */
@Composable
fun PlaceholderCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val tone = (if (isError) StudioTone.Danger else StudioTone.Accent).colors()
    Column(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).background(tone.container, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Studio.colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Studio.colors.textSecondary,
            )
        }
        val hasAction = actionText != null && onAction != null
        val hasSecondary = secondaryActionText != null && onSecondaryAction != null
        if (hasAction || hasSecondary) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondaryActionText != null && onSecondaryAction != null) {
                    StudioButton(
                        text = secondaryActionText,
                        onClick = onSecondaryAction,
                        style = StudioButtonStyle.Secondary,
                    )
                }
                if (actionText != null && onAction != null) {
                    StudioButton(
                        text = actionText,
                        onClick = onAction,
                        icon = actionIcon,
                        style = StudioButtonStyle.Primary,
                    )
                }
            }
        }
    }
}
