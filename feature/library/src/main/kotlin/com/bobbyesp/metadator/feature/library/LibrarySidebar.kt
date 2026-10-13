/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.BrandMark
import com.bobbyesp.metadator.core.designsystem.component.Kbd
import com.bobbyesp.metadator.core.designsystem.component.SidebarItem
import com.bobbyesp.metadator.core.designsystem.component.SidebarTitle
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.library.Storage
import com.bobbyesp.metadator.core.ui.input.shortcutHint

/**
 * The library's sidebar on a wide window: the ways of browsing it, the lists of songs it keeps
 * (what needs attention, the fake lossless files), and the volumes the songs are on; settings and
 * the keyboard's shortcuts at the foot. Each place says how many things are there.
 *
 * @param appName the name the app goes by in this build, for the head of the sidebar
 */
@Composable
internal fun LibrarySidebar(
    state: LibraryState,
    appName: String,
    onIntent: (LibraryIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onShowShortcuts: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormat()
    val section = state.section
    val show = { target: LibrarySection -> onIntent(LibraryIntent.ShowSection(target)) }
    val colors = Studio.colors
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .studioPanel(color = colors.sidebar)
                .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark()
                Text(
                    appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SidebarTitle(stringResource(R.string.sidebar_library))
                LibraryTab.entries.forEach { tab ->
                    SidebarItem(
                        label = stringResource(tab.label),
                        icon = tab.sidebarIcon,
                        selected = section == LibrarySection.Browse(tab),
                        count =
                            numbers.format(
                                when (tab) {
                                    LibraryTab.Songs -> state.totalTracks
                                    LibraryTab.Albums -> state.albums.size
                                    LibraryTab.Artists -> state.artists.size
                                    LibraryTab.Folders -> state.folders.size
                                }
                            ),
                        onClick = { show(LibrarySection.Browse(tab)) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SidebarTitle(stringResource(R.string.sidebar_smart_lists))
                SidebarItem(
                    label = stringResource(R.string.needs_attention),
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = colors.warning,
                    selected = section == LibrarySection.NeedsAttention,
                    count = numbers.format(state.needsAttentionCount),
                    onClick = { show(LibrarySection.NeedsAttention) },
                )
                SidebarItem(
                    label = stringResource(R.string.fake_lossless),
                    icon = Icons.Outlined.GraphicEq,
                    iconTint = colors.danger,
                    selected = section == LibrarySection.FakeLossless,
                    // While the library is still being checked, how far it has got.
                    count =
                        if (state.quality.isRunning) {
                            "${numbers.format(state.fakeCount)} · " +
                                "${state.quality.checked * 100 / state.quality.total.coerceAtLeast(1)}%"
                        } else numbers.format(state.fakeCount),
                    onClick = { show(LibrarySection.FakeLossless) },
                )
            }

            if (state.storages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SidebarTitle(stringResource(R.string.sidebar_storage))
                    state.storages.forEach { (storage, count) ->
                        SidebarItem(
                            label = storage.label(),
                            icon =
                                if (storage == Storage.Device) Icons.Outlined.Smartphone
                                else Icons.Outlined.SdCard,
                            selected = section == LibrarySection.OnStorage(storage),
                            count = numbers.format(count),
                            onClick = { show(LibrarySection.OnStorage(storage)) },
                        )
                    }
                }
            }
        }

        StudioDivider(Modifier.padding(vertical = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidebarItem(
                label = stringResource(R.string.settings),
                icon = Icons.Outlined.Tune,
                onClick = onOpenSettings,
                trailing =
                    shortcutHint(KeyEvent.KEYCODE_COMMA, ctrl = true)?.let { keys ->
                        { Kbd(keys) }
                    },
            )
            if (onShowShortcuts != null) {
                SidebarItem(
                    label = stringResource(R.string.keyboard_shortcuts),
                    icon = Icons.Outlined.Keyboard,
                    onClick = onShowShortcuts,
                    trailing = shortcutHint(KeyEvent.KEYCODE_F1)?.let { keys -> { Kbd(keys) } },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** What a volume is called in the sidebar and over its songs. */
@Composable
internal fun Storage.label(): String =
    when (this) {
        Storage.Device -> stringResource(R.string.storage_device)
        is Storage.Removable -> name
    }

private val LibraryTab.sidebarIcon
    get() =
        when (this) {
            LibraryTab.Songs -> Icons.Outlined.LibraryMusic
            LibraryTab.Albums -> Icons.Outlined.Album
            LibraryTab.Artists -> Icons.Outlined.Person
            LibraryTab.Folders -> Icons.Outlined.Folder
        }
