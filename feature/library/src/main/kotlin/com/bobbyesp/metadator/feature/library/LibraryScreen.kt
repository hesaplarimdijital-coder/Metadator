/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.library

import android.text.format.Formatter
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.metadator.core.designsystem.component.ActionChip
import com.bobbyesp.metadator.core.designsystem.component.ActionMenu
import com.bobbyesp.metadator.core.designsystem.component.Choice
import com.bobbyesp.metadator.core.designsystem.component.ConnectedChoices
import com.bobbyesp.metadator.core.designsystem.component.GlassSurface
import com.bobbyesp.metadator.core.designsystem.component.LoadingScreen
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.PlaceholderCard
import com.bobbyesp.metadator.core.designsystem.component.PopupMenu
import com.bobbyesp.metadator.core.designsystem.component.PopupMenuGroup
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.StudioMenuDivider
import com.bobbyesp.metadator.core.designsystem.component.StudioMenuItem
import com.bobbyesp.metadator.core.designsystem.component.StudioSearchField
import com.bobbyesp.metadator.core.designsystem.component.ToggleChip
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.library.Storage
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.model.TrackCollection
import com.bobbyesp.metadator.core.model.TrackSort
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.LocalShortcutDispatcher
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.hasHardwareKeyboard
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.NumberFormat

/**
 * The library. On a wide window it is a desktop tool's: a sidebar of places, and a panel with a
 * toolbar, a title over what is listed, and the songs as a table. On a phone it is one screen: a
 * title, the search, the tabs and the filters over a card of songs.
 */
@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    onOpenTrack: (Track) -> Unit,
    onFindCover: (Track) -> Unit,
    onOpenCollection: (TrackCollection) -> Unit,
    onEditTracks: (List<Track>) -> Unit,
    onOpenInNewWindow: (Track) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberAudioPermissionState()
    val granted = permission.status == PermissionStatus.Granted
    LaunchedEffect(permission.status) { if (granted) onIntent(LibraryIntent.Start) }
    BackHandler(enabled = state.selecting) { onIntent(LibraryIntent.ClearSelection) }

    val searchFocus = remember { FocusRequester() }
    LibraryShortcuts(
        state = state,
        granted = granted,
        onIntent = onIntent,
        onSearch = { runCatching { searchFocus.requestFocus() } },
        onEditTracks = onEditTracks,
        onOpenSettings = onOpenSettings,
        onOpenFile = onOpenFile,
    )
    val shortcuts = LocalShortcutDispatcher.current
    val showShortcuts =
        if (shortcuts != null && hasHardwareKeyboard()) {
            { shortcuts.isHelpRequested = true }
        } else null
    val actions =
        LibraryActions(
            onIntent = onIntent,
            onOpenTrack = onOpenTrack,
            onFindCover = onFindCover,
            onOpenCollection = onOpenCollection,
            onEditTracks = onEditTracks,
            onOpenInNewWindow = onOpenInNewWindow,
            onOpenSettings = onOpenSettings,
            onOpenFile = onOpenFile,
            onShowShortcuts = showShortcuts,
        )
    val content: @Composable (wide: Boolean) -> Unit = { wide ->
        when {
            !granted ->
                PermissionNeeded(
                    permanentlyDenied = permission.status == PermissionStatus.PermanentlyDenied,
                    onGrant = permission::request,
                    onOpenFile = onOpenFile,
                )
            state.loading -> LoadingScreen()
            else -> LibraryContent(state, actions, wide)
        }
    }

    if (LocalPanels.current) {
        PanelLibrary(state, actions, granted, searchFocus, modifier) { content(true) }
    } else {
        PhoneLibrary(state, actions, granted, searchFocus, modifier) { content(false) }
    }
}

/** What the library can be asked to do, handed down to the pieces that ask. */
internal class LibraryActions(
    val onIntent: (LibraryIntent) -> Unit,
    val onOpenTrack: (Track) -> Unit,
    /** Opens the song's editor, and its cover finder in it. */
    val onFindCover: (Track) -> Unit,
    val onOpenCollection: (TrackCollection) -> Unit,
    val onEditTracks: (List<Track>) -> Unit,
    val onOpenInNewWindow: (Track) -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenFile: () -> Unit,
    /** Null where there are no keys to press. */
    val onShowShortcuts: (() -> Unit)?,
)

/** The narrowest library that has room for its sidebar beside the songs. */
private val SidebarFrom = 900.dp

/**
 * The library on a wide window: its sidebar when there is room for one, and its panel, whose
 * toolbar holds the search, the filters and the order, and over whose songs a title says what they
 * are.
 */
@Composable
private fun PanelLibrary(
    state: LibraryState,
    actions: LibraryActions,
    granted: Boolean,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appName =
        remember(context) { context.applicationInfo.loadLabel(context.packageManager).toString() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sidebar = maxWidth >= SidebarFrom
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sidebar) {
                LibrarySidebar(
                    state = state,
                    appName = appName,
                    onIntent = actions.onIntent,
                    onOpenSettings = actions.onOpenSettings,
                    onShowShortcuts = actions.onShowShortcuts,
                    modifier = Modifier.width(228.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().studioPanel()) {
                PanelToolbar(
                    state = state,
                    actions = actions,
                    granted = granted,
                    searchFocus = searchFocus,
                    sidebar = sidebar,
                )
                StudioDivider()
                if (!sidebar && granted) {
                    // Without the sidebar, its places are tabs.
                    ConnectedChoices(
                        choices =
                            LibraryTab.entries.map {
                                Choice(it, stringResource(it.label), it.icon)
                            },
                        selected = state.tab,
                        onSelect = {
                            actions.onIntent(LibraryIntent.ShowSection(LibrarySection.Browse(it)))
                        },
                        modifier =
                            Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                    )
                }
                if (granted && !state.loading) {
                    PanelHeader(state, actions)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

/**
 * The panel's toolbar: the search, which Ctrl F comes to, the filters by format (and the lists of
 * songs where there is no sidebar to hold them), and at its end the order and the rest.
 */
@Composable
private fun PanelToolbar(
    state: LibraryState,
    actions: LibraryActions,
    granted: Boolean,
    searchFocus: FocusRequester,
    sidebar: Boolean,
) {
    val onIntent = actions.onIntent
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioSearchField(
            value = state.filter.search,
            onValueChange = { onIntent(LibraryIntent.Search(it)) },
            placeholder = stringResource(R.string.search_library),
            clearLabel = stringResource(R.string.clear_search),
            enabled = granted,
            focusRequester = searchFocus,
            shortcut = shortcutHint(KeyEvent.KEYCODE_F, ctrl = true),
            modifier = Modifier.weight(1f).widthIn(max = 440.dp),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (granted) FilterChips(state, onIntent, smartLists = !sidebar)
        }
        if (granted) SortButton(state, onIntent)
        MoreMenu(state, actions, granted, includePlaces = !sidebar)
    }
}

/**
 * The filters: the lists of songs that ask for something to be done, where nothing else offers
 * them, then the formats. The lists come first: they are what is looked for here, a format is a way
 * to narrow down.
 */
@Composable
private fun FilterChips(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    smartLists: Boolean,
) {
    if (smartLists) {
        if (state.needsAttentionCount > 0 || state.filter.needsAttention) {
            ToggleChip(
                selected = state.filter.needsAttention,
                onClick = { onIntent(LibraryIntent.ToggleNeedsAttention) },
                label = stringResource(R.string.needs_attention_filter, state.needsAttentionCount),
                icon = Icons.Rounded.Warning,
                iconTint = Studio.colors.warning,
            )
        }
        if (state.fakeCount > 0 || state.filter.fakeLossless) {
            ToggleChip(
                selected = state.filter.fakeLossless,
                onClick = { onIntent(LibraryIntent.ToggleFakeLossless) },
                label = stringResource(R.string.fake_lossless_filter, state.fakeCount),
                icon = Icons.Outlined.GraphicEq,
                iconTint = Studio.colors.danger,
            )
        }
    }
    if (state.formats.size > 1) {
        state.formats.forEach { format ->
            ToggleChip(
                selected = format in state.filter.formats,
                onClick = { onIntent(LibraryIntent.ToggleFormat(format)) },
                label = format.uppercase(),
            )
        }
    }
}

/** The order of the songs: what they are sorted by, and which way. */
@Composable
private fun SortButton(state: LibraryState, onIntent: (LibraryIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        StudioButton(
            text = stringResource(R.string.sort_by, stringResource(state.sort.sort.label)),
            onClick = { open = true },
            icon = Icons.AutoMirrored.Rounded.Sort,
            style = StudioButtonStyle.Ghost,
        )
        SortMenu(state, onIntent, open, onDismiss = { open = false })
    }
}

@Composable
private fun SortMenu(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    PopupMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PopupMenuGroup {
            TrackSort.entries.forEach { sort ->
                StudioMenuItem(
                    text = stringResource(sort.label),
                    onClick = {
                        onIntent(LibraryIntent.Sort(sort))
                        onDismiss()
                    },
                    checked = state.sort.sort == sort,
                )
            }
            StudioMenuDivider()
            StudioMenuItem(
                text =
                    stringResource(
                        if (state.sort.ascending) R.string.sort_descending
                        else R.string.sort_ascending
                    ),
                onClick = {
                    onIntent(LibraryIntent.ToggleSortDirection)
                    onDismiss()
                },
                icon =
                    if (state.sort.ascending) Icons.Rounded.ArrowDownward
                    else Icons.Rounded.ArrowUpward,
            )
        }
    }
}

/**
 * The way to everything that is not a song: a file of its own, a rescan, and where the sidebar is
 * not there to hold them, the settings and the keyboard's shortcuts.
 */
@Composable
private fun MoreMenu(
    state: LibraryState,
    actions: LibraryActions,
    granted: Boolean,
    includePlaces: Boolean,
    iconSize: Dp = 32.dp,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        StudioIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            onClick = { open = true },
            size = iconSize,
        )
        ActionMenu(
            expanded = open,
            onDismissRequest = { open = false },
            actions =
                listOfNotNull(
                    MenuAction(
                        stringResource(R.string.open_file),
                        Icons.Rounded.AudioFile,
                        shortcut = shortcutHint(KeyEvent.KEYCODE_O, ctrl = true),
                        onClick = actions.onOpenFile,
                    ),
                    MenuAction(
                            stringResource(R.string.rescan_library),
                            Icons.Rounded.Refresh,
                            shortcut = shortcutHint(KeyEvent.KEYCODE_F5),
                            onClick = { actions.onIntent(LibraryIntent.Refresh) },
                        )
                        .takeIf { granted && !state.loading },
                    MenuAction(
                            stringResource(R.string.settings),
                            Icons.Rounded.Settings,
                            shortcut = shortcutHint(KeyEvent.KEYCODE_COMMA, ctrl = true),
                            onClick = actions.onOpenSettings,
                        )
                        .takeIf { includePlaces },
                    actions.onShowShortcuts
                        ?.let {
                            MenuAction(
                                stringResource(R.string.keyboard_shortcuts),
                                Icons.Rounded.Keyboard,
                                shortcut = shortcutHint(KeyEvent.KEYCODE_F1),
                                onClick = it,
                            )
                        }
                        ?.takeIf { includePlaces },
                ),
        )
    }
}

/**
 * Over the songs: what they are, how many and where, and what can be done with all of them. While
 * songs are selected, what can be done with those instead.
 */
@Composable
private fun PanelHeader(state: LibraryState, actions: LibraryActions) {
    val onIntent = actions.onIntent
    AnimatedContent(
        targetState = state.selecting,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "LibraryHeader",
    ) { selecting ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (selecting) {
                Text(
                    pluralStringResource(
                        R.plurals.selected_songs,
                        state.selection.size,
                        state.selection.size,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = Studio.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StudioButton(
                    text = stringResource(R.string.edit_together),
                    onClick = { actions.onEditTracks(state.selectedTracks) },
                    icon = Icons.Rounded.EditNote,
                    style = StudioButtonStyle.Primary,
                    shortcut = shortcutHint(KeyEvent.KEYCODE_E, ctrl = true),
                )
                StudioButton(
                    text = stringResource(R.string.play_selection),
                    onClick = { onIntent(LibraryIntent.PlaySelection) },
                    icon = Icons.Rounded.PlayArrow,
                )
                StudioButton(
                    text = stringResource(R.string.close_selection),
                    onClick = { onIntent(LibraryIntent.ClearSelection) },
                    style = StudioButtonStyle.Ghost,
                    shortcut = shortcutHint(KeyEvent.KEYCODE_ESCAPE),
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        sectionTitle(state),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                lineHeight = 30.sp,
                            ),
                        color = Studio.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sectionStats(state),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Studio.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.tab == LibraryTab.Songs && state.tracks.isNotEmpty()) {
                    StudioButton(
                        text = stringResource(R.string.play_all),
                        onClick = { onIntent(LibraryIntent.PlayAll(shuffle = false)) },
                        icon = Icons.Rounded.PlayArrow,
                        style = StudioButtonStyle.Primary,
                    )
                    StudioButton(
                        text = stringResource(R.string.shuffle_all),
                        onClick = { onIntent(LibraryIntent.PlayAll(shuffle = true)) },
                        icon = Icons.Rounded.Shuffle,
                    )
                }
            }
        }
    }
}

/** What the songs listed are: the place of the sidebar they are. */
@Composable
internal fun sectionTitle(state: LibraryState): String =
    when (val section = state.section) {
        is LibrarySection.Browse -> stringResource(section.tab.label)
        LibrarySection.NeedsAttention -> stringResource(R.string.needs_attention)
        LibrarySection.FakeLossless -> stringResource(R.string.fake_lossless)
        is LibrarySection.OnStorage -> section.storage.label()
    }

/**
 * How many songs are listed, how much room they take, and how many of them are on a card; while the
 * lossless files are being checked, how far that has got.
 */
@Composable
internal fun sectionStats(state: LibraryState): String {
    val context = LocalContext.current
    val numbers = NumberFormat.getIntegerInstance()
    val parts = mutableListOf<String>()
    when (state.tab) {
        LibraryTab.Songs -> {
            parts +=
                pluralStringResource(
                    R.plurals.song_count_formatted,
                    state.tracks.size,
                    numbers.format(state.tracks.size),
                )
            val bytes = state.tracks.sumOf { it.sizeBytes }
            if (bytes > 0) parts += Formatter.formatShortFileSize(context, bytes)
            if (state.filter.storage == null) {
                state.storages
                    .filter { it.first is Storage.Removable }
                    .forEach { (storage, count) ->
                        parts +=
                            stringResource(
                                R.string.songs_on_storage,
                                numbers.format(count),
                                storage.label(),
                            )
                    }
            }
        }
        LibraryTab.Albums ->
            parts +=
                pluralStringResource(
                    R.plurals.album_count,
                    state.albums.size,
                    numbers.format(state.albums.size),
                )
        LibraryTab.Artists ->
            parts +=
                pluralStringResource(
                    R.plurals.artist_count,
                    state.artists.size,
                    numbers.format(state.artists.size),
                )
        LibraryTab.Folders ->
            parts +=
                pluralStringResource(
                    R.plurals.folder_count,
                    state.folders.size,
                    numbers.format(state.folders.size),
                )
    }
    if (state.quality.isRunning) {
        parts +=
            stringResource(
                R.string.quality_progress,
                numbers.format(state.quality.checked),
                numbers.format(state.quality.total),
            )
    }
    return parts.joinToString(" · ")
}

/**
 * The library on a phone: one screen, with its title, the search, the tabs and the filters over a
 * card of what is listed. Selecting songs turns the head into the selection's, and floats the
 * actions on the selected songs over the list.
 */
@Composable
private fun PhoneLibrary(
    state: LibraryState,
    actions: LibraryActions,
    granted: Boolean,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val onIntent = actions.onIntent
    // The lists, recorded for the selection toolbar that floats over them.
    val haze = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = state.selecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "PhoneLibraryHead",
            ) { selecting ->
                if (selecting) {
                    PhoneSelectionHead(state, onIntent)
                } else {
                    PhoneHead(state, actions, granted)
                }
            }
            if (granted) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StudioSearchField(
                        value = state.filter.search,
                        onValueChange = { onIntent(LibraryIntent.Search(it)) },
                        placeholder = stringResource(R.string.search_library),
                        clearLabel = stringResource(R.string.clear_search),
                        focusRequester = searchFocus,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ConnectedChoices(
                        choices =
                            LibraryTab.entries.map {
                                Choice(it, stringResource(it.label), it.icon)
                            },
                        selected = state.tab,
                        onSelect = {
                            onIntent(LibraryIntent.ShowSection(LibrarySection.Browse(it)))
                        },
                        fill = true,
                        height = 36.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhoneSortChip(state, onIntent)
                    FilterChips(state, onIntent, smartLists = true)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().hazeSource(haze)) { content() }
        }

        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = state.selecting,
            enter =
                slideInVertically(motion.defaultSpatialSpec()) { it } +
                    fadeIn(motion.defaultEffectsSpec()),
            exit =
                slideOutVertically(motion.fastSpatialSpec()) { it } +
                    fadeOut(motion.fastEffectsSpec()),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Above the mini player when one is showing, as far up as the shell keeps
                    // its messages (the player's clearance, which a feature cannot see).
                    .padding(bottom = if (state.playerActive) 96.dp else Spacing.large),
        ) {
            SelectionBar(state, actions, onIntent, glass = haze)
        }
    }
}

/**
 * What to do with the songs selected, on a pane of glass over the list: play them, or edit them
 * together, which is what selecting them is mostly for.
 */
@Composable
private fun SelectionBar(
    state: LibraryState,
    actions: LibraryActions,
    onIntent: (LibraryIntent) -> Unit,
    glass: HazeState,
) {
    // The buttons' corners are the bar's, less its padding: the two curves stay parallel.
    GlassSurface(backdrop = glass, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioButton(
                text = stringResource(R.string.play_selection),
                onClick = { onIntent(LibraryIntent.PlaySelection) },
                icon = Icons.Rounded.PlayArrow,
                style = StudioButtonStyle.Ghost,
                modifier = Modifier.heightIn(min = 44.dp),
            )
            StudioButton(
                text = stringResource(R.string.edit_together),
                onClick = { actions.onEditTracks(state.selectedTracks) },
                icon = Icons.Rounded.Edit,
                style = StudioButtonStyle.Primary,
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}

/** The head of the phone's library: its title, what it holds, and the way to the rest. */
@Composable
private fun PhoneHead(state: LibraryState, actions: LibraryActions, granted: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = Spacing.screen, end = 8.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.section is LibrarySection.Browse) stringResource(R.string.library_title)
                else sectionTitle(state),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                    ),
                color = Studio.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (granted && !state.loading) {
                Text(
                    sectionStats(state),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Studio.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoreMenu(state, actions, granted, includePlaces = true, iconSize = 44.dp)
    }
}

/** The head of the phone's library while songs are selected. */
@Composable
private fun PhoneSelectionHead(state: LibraryState, onIntent: (LibraryIntent) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.close_selection),
            onClick = { onIntent(LibraryIntent.ClearSelection) },
            size = 44.dp,
            iconSize = 22.dp,
        )
        Text(
            pluralStringResource(
                R.plurals.selected_count,
                state.selection.size,
                state.selection.size,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = Studio.colors.text,
        )
        StudioIconButton(
            icon = Icons.Rounded.SelectAll,
            contentDescription = stringResource(R.string.select_all),
            onClick = { onIntent(LibraryIntent.SelectAll) },
            size = 44.dp,
            iconSize = 22.dp,
        )
    }
}

/** The order of the songs, as the first of the phone's chips. */
@Composable
private fun PhoneSortChip(state: LibraryState, onIntent: (LibraryIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionChip(
            onClick = { open = true },
            label = stringResource(state.sort.sort.label),
            icon = Icons.AutoMirrored.Rounded.Sort,
            opensMenu = true,
        )
        SortMenu(state, onIntent, open, onDismiss = { open = false })
    }
}

@Composable
private fun PermissionNeeded(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(Spacing.extraLarge), contentAlignment = Alignment.Center) {
        PlaceholderCard(
            title = stringResource(R.string.permission_title),
            description =
                stringResource(
                    if (permanentlyDenied) R.string.permission_denied_description
                    else R.string.permission_description
                ),
            icon = Icons.Rounded.LibraryMusic,
            actionText =
                stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings
                    else R.string.permission_grant
                ),
            onAction = onGrant,
            secondaryActionText = stringResource(R.string.open_file),
            onSecondaryAction = onOpenFile,
        )
    }
}

/**
 * What is listed: the songs, the albums, the artists or the folders, or why there is nothing to
 * list. Pulled down, it rescans.
 *
 * @param wide whether it is in a panel of a wide window, where the songs can be a table
 */
@Composable
private fun LibraryContent(state: LibraryState, actions: LibraryActions, wide: Boolean) {
    val onIntent = actions.onIntent
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { onIntent(LibraryIntent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.totalTracks == 0 ->
                CenteredPlaceholder {
                    PlaceholderCard(
                        title = stringResource(R.string.empty_library_title),
                        description = stringResource(R.string.empty_library_description),
                        icon = Icons.Rounded.LibraryMusic,
                        actionText = stringResource(R.string.rescan_library),
                        actionIcon = Icons.Rounded.Refresh,
                        onAction = { onIntent(LibraryIntent.Refresh) },
                    )
                }
            state.tracks.isEmpty() && state.filter.fakeLossless ->
                CenteredPlaceholder {
                    PlaceholderCard(
                        title = stringResource(R.string.no_fake_title),
                        description =
                            if (state.quality.isRunning) {
                                stringResource(
                                    R.string.no_fake_yet_description,
                                    state.quality.checked,
                                    state.quality.total,
                                )
                            } else stringResource(R.string.no_fake_description),
                        icon = Icons.Rounded.TaskAlt,
                    )
                }
            state.tracks.isEmpty() ->
                CenteredPlaceholder {
                    PlaceholderCard(
                        title = stringResource(R.string.no_results_title),
                        description = stringResource(R.string.no_results_description),
                        icon = Icons.Rounded.SearchOff,
                        actionText = stringResource(R.string.clear_filters),
                        onAction = {
                            onIntent(LibraryIntent.Search(""))
                            onIntent(LibraryIntent.ClearFilters)
                        },
                    )
                }
            else ->
                AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = {
                        // The new tab comes from the side its place is on.
                        val travel = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInVertically(spatial) { travel * it / 24 } + fadeIn(effects))
                            .togetherWith(fadeOut(effects))
                    },
                    label = "LibraryTab",
                ) { tab ->
                    when (tab) {
                        LibraryTab.Songs ->
                            if (wide) SongsTable(state, actions) else SongCards(state, actions)
                        LibraryTab.Albums -> AlbumGrid(state.albums, actions.onOpenCollection, wide)
                        LibraryTab.Artists ->
                            CollectionList(state.artists, actions.onOpenCollection, wide)
                        LibraryTab.Folders ->
                            CollectionList(state.folders, actions.onOpenCollection, wide)
                    }
                }
        }
    }
}

@Composable
private fun CenteredPlaceholder(content: @Composable () -> Unit) {
    // Scrollable, so pull-to-refresh still works on an empty library.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item { content() }
    }
}

/** What the library does from the keyboard, whatever has the focus. */
@Composable
private fun LibraryShortcuts(
    state: LibraryState,
    granted: Boolean,
    onIntent: (LibraryIntent) -> Unit,
    onSearch: () -> Unit,
    onEditTracks: (List<Track>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val onSongs = granted && state.tab == LibraryTab.Songs && state.tracks.isNotEmpty()
    val editSelection = { onEditTracks(state.selectedTracks) }
    val rescan = { onIntent(LibraryIntent.Refresh) }
    KeyboardShortcuts(
        title = stringResource(R.string.shortcuts_library),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_F,
                    stringResource(R.string.shortcut_search),
                    ctrl = true,
                    enabled = granted,
                    action = onSearch,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_A,
                    stringResource(R.string.select_all),
                    ctrl = true,
                    enabled = onSongs,
                ) {
                    onIntent(LibraryIntent.SelectAll)
                },
                Shortcut(
                    KeyEvent.KEYCODE_E,
                    stringResource(R.string.edit_together),
                    ctrl = true,
                    enabled = state.selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ENTER,
                    stringResource(R.string.edit_together),
                    enabled = state.selecting,
                    action = editSelection,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_ESCAPE,
                    stringResource(R.string.close_selection),
                    enabled = state.selecting,
                ) {
                    onIntent(LibraryIntent.ClearSelection)
                },
                Shortcut(KeyEvent.KEYCODE_O, stringResource(R.string.open_file), ctrl = true) {
                    onOpenFile()
                },
                Shortcut(
                    KeyEvent.KEYCODE_R,
                    stringResource(R.string.rescan_library),
                    ctrl = true,
                    enabled = granted,
                    action = rescan,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_F5,
                    stringResource(R.string.rescan_library),
                    enabled = granted,
                    action = rescan,
                ),
                Shortcut(KeyEvent.KEYCODE_COMMA, stringResource(R.string.settings), ctrl = true) {
                    onOpenSettings()
                },
            ) +
                LibraryTab.entries.mapIndexed { index, tab ->
                    Shortcut(
                        KeyEvent.KEYCODE_1 + index,
                        stringResource(tab.label),
                        ctrl = true,
                        enabled = granted,
                    ) {
                        onIntent(LibraryIntent.ShowSection(LibrarySection.Browse(tab)))
                    }
                },
    )
}

internal val LibraryTab.label: Int
    get() =
        when (this) {
            LibraryTab.Songs -> R.string.tab_songs
            LibraryTab.Albums -> R.string.tab_albums
            LibraryTab.Artists -> R.string.tab_artists
            LibraryTab.Folders -> R.string.tab_folders
        }

internal val LibraryTab.icon
    get() =
        when (this) {
            LibraryTab.Songs -> Icons.Rounded.LibraryMusic
            LibraryTab.Albums -> Icons.Rounded.Album
            LibraryTab.Artists -> Icons.Rounded.Person
            LibraryTab.Folders -> Icons.Rounded.FolderOpen
        }

internal val TrackSort.label: Int
    get() =
        when (this) {
            TrackSort.Title -> R.string.sort_title
            TrackSort.Artist -> R.string.sort_artist
            TrackSort.Album -> R.string.sort_album
            TrackSort.DateAdded -> R.string.sort_date_added
            TrackSort.DateModified -> R.string.sort_date_modified
            TrackSort.Duration -> R.string.sort_duration
        }
