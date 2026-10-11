/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.metadator.core.designsystem.component.BrandMark
import com.bobbyesp.metadator.core.designsystem.component.Choice
import com.bobbyesp.metadator.core.designsystem.component.ConnectedChoices
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.NavigationItem
import com.bobbyesp.metadator.core.designsystem.component.RadioItem
import com.bobbyesp.metadator.core.designsystem.component.SectionHeader
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.SwitchItem
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.component.readableWidth
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.GroupShapes
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.StudioAccent
import com.bobbyesp.metadator.core.designsystem.theme.StudioAccents
import com.bobbyesp.metadator.core.designsystem.theme.isDynamicColorSupported
import com.bobbyesp.metadator.core.model.MultiValueMode
import com.bobbyesp.metadator.core.model.ThemeMode
import com.bobbyesp.metadator.core.model.UserSettings
import com.bobbyesp.metadator.core.navigation.About
import com.bobbyesp.metadator.core.navigation.AppearanceSettings
import com.bobbyesp.metadator.core.navigation.EditorSettings
import com.bobbyesp.metadator.core.navigation.LookupSettings
import com.bobbyesp.metadator.core.navigation.Navigator
import com.bobbyesp.metadator.core.navigation.Settings
import com.bobbyesp.metadator.core.ui.viewmodel.LocalSnackbarHostState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val SOURCE_URL = "https://github.com/BobbyESP/Metadator"
private const val ISSUES_URL = "https://github.com/BobbyESP/Metadator/issues"
private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.bobbyesp.metadator"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun EntryProviderScope<NavKey>.settingsSection(navigator: Navigator) {
    entry<Settings> {
        SettingsPage(stringResource(R.string.settings), onBack = navigator::goBack) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    val pages =
                        listOf(
                            Triple(
                                R.string.appearance,
                                R.string.appearance_description,
                                Icons.Rounded.Palette,
                            ) to AppearanceSettings,
                            Triple(
                                R.string.editor,
                                R.string.editor_description,
                                Icons.Rounded.EditNote,
                            ) to EditorSettings,
                            Triple(
                                R.string.lookup,
                                R.string.lookup_description,
                                Icons.Rounded.Public,
                            ) to LookupSettings,
                            Triple(
                                R.string.about,
                                R.string.about_description,
                                Icons.Rounded.Info,
                            ) to About,
                        )
                    pages.forEachIndexed { index, (labels, key) ->
                        NavigationItem(
                            title = stringResource(labels.first),
                            supportingText = stringResource(labels.second),
                            icon = labels.third,
                            onClick = { navigator.goTo(key) },
                            shapes = GroupShapes.listItemShapes(index, pages.size),
                        )
                    }
                }
            }
        }
    }

    entry<AppearanceSettings> {
        val viewModel: SettingsViewModel = koinViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        SettingsPage(stringResource(R.string.appearance), onBack = navigator::goBack) {
            appearance(settings, viewModel::update)
        }
    }

    entry<EditorSettings> {
        val viewModel: SettingsViewModel = koinViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val snackbar = LocalSnackbarHostState.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val cleared = stringResource(R.string.history_cleared)
        SettingsPage(stringResource(R.string.editor), onBack = navigator::goBack) {
            editor(settings, viewModel::update) {
                viewModel.clearHistory { scope.launch { snackbar.showSnackbar(cleared) } }
            }
        }
    }

    entry<LookupSettings> {
        val viewModel: SettingsViewModel = koinViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        SettingsPage(stringResource(R.string.lookup), onBack = navigator::goBack) {
            item { SectionHeader(stringResource(R.string.lookup_sources)) }
            item {
                Text(
                    stringResource(R.string.lookup_sources_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = Spacing.small,
                            end = Spacing.small,
                            bottom = Spacing.medium,
                        ),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    viewModel.providers.forEachIndexed { index, provider ->
                        SwitchItem(
                            title = provider.name,
                            supportingText =
                                stringResource(
                                    when (provider.id) {
                                        UserSettings.PROVIDER_MUSICBRAINZ ->
                                            R.string.provider_musicbrainz_description
                                        UserSettings.PROVIDER_DEEZER ->
                                            R.string.provider_deezer_description
                                        UserSettings.PROVIDER_ITUNES ->
                                            R.string.provider_itunes_description
                                        else -> R.string.provider_other_description
                                    }
                                ),
                            icon = Icons.Rounded.Public,
                            checked = provider.id in settings.enabledProviders,
                            onCheckedChange = { viewModel.toggleProvider(provider.id) },
                            shapes = GroupShapes.listItemShapes(index, viewModel.providers.size),
                        )
                    }
                }
            }
        }
    }

    entry<About> {
        val viewModel: SettingsViewModel = koinViewModel()
        val uriHandler = LocalUriHandler.current
        val info = viewModel.appInfo
        SettingsPage(stringResource(R.string.about), onBack = navigator::goBack) {
            item {
                val context = LocalContext.current
                val appName =
                    remember(context) {
                        context.applicationInfo.loadLabel(context.packageManager).toString()
                    }
                Column(
                    modifier = Modifier.readableWidth().padding(vertical = Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    BrandMark(Modifier.padding(bottom = Spacing.small), size = 88.dp)
                    Text(
                        appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Studio.colors.text,
                    )
                    Text(
                        stringResource(R.string.version, info.versionName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Studio.colors.textSecondary,
                    )
                }
            }
            item {
                val rows = buildList {
                    add(
                        Triple(
                            R.string.source_code,
                            R.string.source_code_description,
                            Icons.Rounded.Code,
                        ) to SOURCE_URL
                    )
                    add(
                        Triple(
                            R.string.report_issue,
                            R.string.report_issue_description,
                            Icons.Rounded.BugReport,
                        ) to ISSUES_URL
                    )
                    if (info.isPlayStoreBuild)
                        add(
                            Triple(R.string.rate, R.string.rate_description, Icons.Rounded.Star) to
                                PLAY_URL
                        )
                }
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    rows.forEachIndexed { index, (labels, url) ->
                        NavigationItem(
                            title = stringResource(labels.first),
                            supportingText = stringResource(labels.second),
                            icon = labels.third,
                            onClick = { uriHandler.openUri(url) },
                            shapes = GroupShapes.listItemShapes(index, rows.size),
                            showChevron = false,
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.credits)) }
            item {
                Text(
                    stringResource(R.string.credits_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.small),
                )
            }
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.huge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.made_by),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A page of settings: its title and the way back over a list at a readable width. On a wide window
 * it is a panel, as the library is.
 */
@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val panels = LocalPanels.current
    Column(
        Modifier.fillMaxSize()
            .then(if (panels) Modifier.studioPanel() else Modifier.statusBarsPadding())
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Studio.colors.text,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = Spacing.small,
                    bottom = Spacing.huge,
                    start = Spacing.screen,
                    end = Spacing.screen,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Box(Modifier.readableWidth()) }
            content()
        }
    }
}

private fun LazyListScope.appearance(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
) {
    item { SectionHeader(stringResource(R.string.theme), Modifier.readableWidth()) }
    item {
        ConnectedChoices(
            choices =
                listOf(
                    Choice(
                        ThemeMode.FollowSystem,
                        stringResource(R.string.theme_system),
                        Icons.Rounded.BrightnessAuto,
                    ),
                    Choice(
                        ThemeMode.Light,
                        stringResource(R.string.theme_light),
                        Icons.Rounded.LightMode,
                    ),
                    Choice(
                        ThemeMode.Dark,
                        stringResource(R.string.theme_dark),
                        Icons.Rounded.DarkMode,
                    ),
                ),
            selected = settings.themeMode,
            onSelect = { mode -> update { it.copy(themeMode = mode) } },
            fill = true,
            modifier = Modifier.readableWidth(),
        )
    }
    item { SectionHeader(stringResource(R.string.accent), Modifier.readableWidth()) }
    item {
        AccentPicker(
            selected =
                if (settings.useDynamicColor && isDynamicColorSupported()) null
                else StudioAccents.of(settings.seedColor),
            onSelect = { accent ->
                update { it.copy(seedColor = accent.id, useDynamicColor = false) }
            },
        )
    }
    item { SectionHeader(stringResource(R.string.colors), Modifier.readableWidth()) }
    item {
        val dynamicAvailable = isDynamicColorSupported()
        Column(
            Modifier.readableWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SwitchItem(
                title = stringResource(R.string.dynamic_color),
                supportingText = stringResource(R.string.dynamic_color_description),
                icon = Icons.Rounded.Wallpaper,
                checked = settings.useDynamicColor && dynamicAvailable,
                enabled = dynamicAvailable,
                onCheckedChange = { checked -> update { it.copy(useDynamicColor = checked) } },
                shapes = GroupShapes.listItemShapes(0, 2),
            )
            SwitchItem(
                title = stringResource(R.string.color_from_cover),
                supportingText = stringResource(R.string.color_from_cover_description),
                icon = Icons.Rounded.Image,
                checked = settings.colorEditorFromArtwork,
                onCheckedChange = { checked ->
                    update { it.copy(colorEditorFromArtwork = checked) }
                },
                shapes = GroupShapes.listItemShapes(1, 2),
            )
        }
    }
}

/**
 * The accents the app comes in, as swatches with their names under them. None is chosen while the
 * phone's own palette is used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: StudioAccent?, onSelect: (StudioAccent) -> Unit) {
    val dark = Studio.colors.isDark
    FlowRow(
        modifier = Modifier.readableWidth().padding(horizontal = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        StudioAccents.All.forEach { accent ->
            val chosen = accent == selected
            val swatch = if (dark) accent.fillOnDark else accent.fillOnLight
            val name = stringResource(accent.label)
            Column(
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = chosen,
                            onClick = { onSelect(accent) },
                            role = Role.RadioButton,
                        )
                        .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(40.dp)
                        .then(
                            if (chosen) {
                                Modifier.border(2.dp, Studio.colors.text, CircleShape).padding(4.dp)
                            } else Modifier
                        )
                        .background(swatch, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) Studio.colors.text else Studio.colors.textSecondary,
                )
            }
        }
    }
}

/** What an accent is called, for its swatch and for a screen reader. */
private val StudioAccent.label: Int
    get() =
        when (this) {
            StudioAccents.Violet -> R.string.accent_violet
            StudioAccents.Teal -> R.string.accent_teal
            StudioAccents.Orange -> R.string.accent_orange
            StudioAccents.Pink -> R.string.accent_pink
            StudioAccents.Graphite -> R.string.accent_graphite
            else -> R.string.accent_blue
        }

private fun LazyListScope.editor(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
    onClearHistory: () -> Unit,
) {
    item { SectionHeader(stringResource(R.string.multi_values), Modifier.readableWidth()) }
    item {
        Column(
            Modifier.readableWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            RadioItem(
                title = stringResource(R.string.multi_separate),
                supportingText = stringResource(R.string.multi_separate_description),
                selected = settings.multiValueMode == MultiValueMode.Separate,
                onClick = { update { it.copy(multiValueMode = MultiValueMode.Separate) } },
                shapes = GroupShapes.listItemShapes(0, 2),
            )
            RadioItem(
                title = stringResource(R.string.multi_joined),
                supportingText = stringResource(R.string.multi_joined_description),
                selected = settings.multiValueMode == MultiValueMode.Joined,
                onClick = { update { it.copy(multiValueMode = MultiValueMode.Joined) } },
                shapes = GroupShapes.listItemShapes(1, 2),
            )
        }
    }
    item {
        TonalTextField(
            value = settings.multiValueSeparator,
            onValueChange = { value ->
                if (value.isNotEmpty() && value.length <= 5)
                    update { it.copy(multiValueSeparator = value) }
            },
            label = stringResource(R.string.separator),
            supportingText = stringResource(R.string.separator_description),
            inputTransformation = SeparatorInput,
            modifier = Modifier.readableWidth().padding(top = Spacing.medium),
        )
    }
    item { Box(Modifier.padding(top = Spacing.small)) }
    item {
        SwitchItem(
            title = stringResource(R.string.warn_small_cover),
            supportingText = stringResource(R.string.warn_small_cover_description),
            icon = Icons.Rounded.Image,
            checked = settings.warnSmallArtwork,
            onCheckedChange = { checked -> update { it.copy(warnSmallArtwork = checked) } },
            modifier = Modifier.readableWidth(),
        )
    }
    item { SectionHeader(stringResource(R.string.history), Modifier.readableWidth()) }
    item {
        NavigationItem(
            title = stringResource(R.string.clear_history),
            supportingText = stringResource(R.string.clear_history_description),
            icon = Icons.Rounded.DeleteSweep,
            onClick = onClearHistory,
            showChevron = false,
            modifier = Modifier.readableWidth(),
        )
    }
}

/**
 * What the separator field takes: one to five characters. Turned down as it is typed, so that the
 * field never shows a separator the settings did not take.
 */
private val SeparatorInput = InputTransformation { if (length !in 1..5) revertAllChanges() }
