/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.theme.Spacing

/*
 * What the sheets that search online are made of: the metadata lookup and the cover finder ask
 * the same way, and say the same way that nothing came back.
 */

/**
 * One of the fields a search is made with. The last one searches from the keyboard ([onSearch]);
 * the others move on to the next.
 */
@Composable
fun SheetQueryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    TonalTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions =
            KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Next),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = TonalFieldDefaults.sheetColors(),
    )
}

/**
 * The one thing to do at each stage, as Material's medium button: tall and as wide as the sheet, so
 * that it is found without being looked for.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
    ) {
        Icon(icon, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(text, style = ButtonDefaults.textStyleFor(height))
    }
}

/** Why there are no results, under the shape the app's empty screens use. */
@Composable
fun SheetMessage(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        ShapedIcon(
            icon = icon,
            size = 88.dp,
            containerColor = if (isError) colors.errorContainer else colors.primaryContainer,
            contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
