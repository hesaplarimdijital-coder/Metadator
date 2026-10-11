/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.editor

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.metadator.core.designsystem.component.ActionMenu
import com.bobbyesp.metadator.core.designsystem.component.LabelChip
import com.bobbyesp.metadator.core.designsystem.component.LoadingScreen
import com.bobbyesp.metadator.core.designsystem.component.LocalPanels
import com.bobbyesp.metadator.core.designsystem.component.MenuAction
import com.bobbyesp.metadator.core.designsystem.component.PlaceholderCard
import com.bobbyesp.metadator.core.designsystem.component.SectionHeader
import com.bobbyesp.metadator.core.designsystem.component.StudioButton
import com.bobbyesp.metadator.core.designsystem.component.StudioButtonStyle
import com.bobbyesp.metadator.core.designsystem.component.StudioDefaults
import com.bobbyesp.metadator.core.designsystem.component.StudioDivider
import com.bobbyesp.metadator.core.designsystem.component.StudioIconButton
import com.bobbyesp.metadator.core.designsystem.component.TonalFieldDefaults
import com.bobbyesp.metadator.core.designsystem.component.TonalTextField
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.component.studioPanel
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.domain.editor.LoadedTrack
import com.bobbyesp.metadator.core.domain.editor.Position
import com.bobbyesp.metadator.core.domain.editor.TagDraft
import com.bobbyesp.metadator.core.ui.R as CoreUiR
import com.bobbyesp.metadator.core.ui.component.fieldLabel
import com.bobbyesp.metadator.core.ui.input.KeyboardShortcuts
import com.bobbyesp.metadator.core.ui.input.Shortcut
import com.bobbyesp.metadator.core.ui.input.shortcutHint
import com.bobbyesp.metadator.feature.editor.components.AttentionNotice
import com.bobbyesp.metadator.feature.editor.components.CoverActions
import com.bobbyesp.metadator.feature.editor.components.CoverModel
import com.bobbyesp.metadator.feature.editor.components.EditorActions
import com.bobbyesp.metadator.feature.editor.components.EditorHeader
import com.bobbyesp.metadator.feature.editor.components.FileInfoCard
import com.bobbyesp.metadator.feature.editor.components.LosslessCheckCard
import com.bobbyesp.metadator.feature.editor.components.PositionField
import com.bobbyesp.metadator.feature.editor.components.TagChipsField
import com.bobbyesp.metadator.feature.editor.components.TagTextField
import com.bobbyesp.metadator.feature.editor.components.kiloHertz
import com.bobbyesp.metadator.lyrics.api.Lrc
import com.bobbyesp.metadator.tags.api.FieldKind
import com.bobbyesp.metadator.tags.api.TagField
import java.util.Locale
import kotlinx.coroutines.launch

/** The fields of each section, in order. */
private val CreditFields =
    listOf(
        TagField.Composer,
        TagField.Lyricist,
        TagField.Conductor,
        TagField.Remixer,
        TagField.Performer,
    )

/** Keys with a place of their own; everything else is under "All tags". */
private val KnownKeys: Set<String> =
    TagField.entries.map { it.key }.toSet() + TagField.TrackTotalKeys + TagField.DiscTotalKeys

/** The essential tags a song needs, in the order the editor has them. */
private val EssentialFields =
    listOf(
        TagField.Artist,
        TagField.Album,
        TagField.AlbumArtist,
        TagField.Date,
        TagField.TrackNumber,
    )

/**
 * The tag editor. Beside the library it is an inspector, as a desktop tool has one: the cover and
 * what the song is at its head, what can be found online, the lossless check, the fields, the file,
 * and at its foot the changes and the way to save them. Filling a window, it is the same, in two
 * columns when there is room; on a phone, one screen with its head centered.
 *
 * @param showClose whether it shares the window with the library, as an inspector
 */
@Composable
internal fun EditorScreen(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    onFindMetadata: () -> Unit,
    onFindCover: () -> Unit,
    onClose: () -> Unit,
    showClose: Boolean,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = {
        if (state.isDirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }

    // Back with unsaved changes asks first; without them it leaves as usual.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.isDirty,
        onBackCompleted = { confirmDiscard = true },
    )

    val pickCover =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onIntent(EditorIntent.SetCoverFromUri(uri.toString()))
        }
    val changeCover = {
        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val scroll = rememberScrollState()
    EditorShortcuts(
        state = state,
        scroll = scroll,
        onIntent = onIntent,
        onFindMetadata = onFindMetadata,
        onFindCover = onFindCover,
        onChangeCover = changeCover,
        onClose = leave,
    )
    val panels = LocalPanels.current
    val parts =
        EditorParts(
            state = state,
            onIntent = onIntent,
            onFindMetadata = onFindMetadata,
            onFindCover = onFindCover,
            onChangeCover = changeCover,
            onClose = leave,
        )

    Column(
        modifier
            .fillMaxSize()
            .then(if (panels) Modifier.studioPanel() else Modifier.statusBarsPadding())
            // The foot rides above the keyboard, so that Save is in reach while a field is typed
            // in: under it, the keyboard had to be closed first.
            .imePadding()
    ) {
        if (!panels) PhoneTopBar(parts, title = stringResource(R.string.edit_tags))
        val draft = state.draft
        val loaded = state.loaded
        when {
            state.status == EditorStatus.Loading -> LoadingScreen(Modifier.weight(1f))
            state.status != EditorStatus.Ready || draft == null || loaded == null ->
                EditorProblem(
                    state.status,
                    onRetry = { onIntent(EditorIntent.Reload) },
                    onClose = onClose,
                    modifier = Modifier.weight(1f),
                )
            else -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = panels && !showClose && maxWidth >= TwoColumnWidth
                    when {
                        columns -> TwoColumnEditor(parts, draft, loaded, scroll)
                        panels -> InspectorEditor(parts, draft, loaded, scroll, showClose)
                        else -> PhoneEditor(parts, draft, loaded, scroll)
                    }
                }
                EditorFooter(state, onIntent, phone = !panels)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onIntent(EditorIntent.RevertAll)
                        onClose()
                    }
                ) {
                    Text(stringResource(R.string.unsaved_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.unsaved_keep))
                }
            },
        )
    }
}

/** What the pieces of the editor are asked to do, handed down to them. */
private class EditorParts(
    val state: EditorState,
    val onIntent: (EditorIntent) -> Unit,
    val onFindMetadata: () -> Unit,
    val onFindCover: () -> Unit,
    val onChangeCover: () -> Unit,
    val onClose: () -> Unit,
)

/** Wide enough, filling a window, for the cover and the file beside the fields. */
private val TwoColumnWidth = 760.dp

/** The editor beside the library: one column, its head the cover beside the song's name. */
@Composable
private fun InspectorEditor(
    parts: EditorParts,
    draft: TagDraft,
    loaded: LoadedTrack,
    scroll: ScrollState,
    close: Boolean,
) {
    EditorColumn(scroll, Modifier.fillMaxSize(), padding = 16.dp) {
        Head(parts, centered = false, closeable = close)
        EditorActions(
            onFindCover = parts.onFindCover,
            onFindMetadata = parts.onFindMetadata,
            onFindLyrics = { parts.onIntent(EditorIntent.FindLyrics) },
            findingLyrics = parts.state.findingLyrics,
        )
        LosslessCheckCard(parts.state.lossless)
        Fields(parts, draft)
        FileInfoCard(loaded)
    }
}

/** The editor filling a wide window: the cover, the check and the file beside the fields. */
@Composable
private fun TwoColumnEditor(
    parts: EditorParts,
    draft: TagDraft,
    loaded: LoadedTrack,
    scroll: ScrollState,
) {
    Row(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Head(parts, centered = true, closeable = false)
            EditorActions(
                onFindCover = parts.onFindCover,
                onFindMetadata = parts.onFindMetadata,
                onFindLyrics = { parts.onIntent(EditorIntent.FindLyrics) },
                findingLyrics = parts.state.findingLyrics,
            )
            LosslessCheckCard(parts.state.lossless)
            FileInfoCard(loaded)
        }
        EditorColumn(
            scroll,
            Modifier.weight(1f).fillMaxHeight().widthIn(max = 720.dp),
            padding = 0.dp,
            end = 20.dp,
        ) {
            Fields(parts, draft)
        }
    }
}

/** The editor on a phone: its head centered over the buttons, the check and the fields. */
@Composable
private fun PhoneEditor(
    parts: EditorParts,
    draft: TagDraft,
    loaded: LoadedTrack,
    scroll: ScrollState,
) {
    EditorColumn(scroll, Modifier.fillMaxSize(), padding = Spacing.screen) {
        Head(parts, centered = true, closeable = false)
        EditorActions(
            onFindCover = parts.onFindCover,
            onFindMetadata = parts.onFindMetadata,
            onFindLyrics = { parts.onIntent(EditorIntent.FindLyrics) },
            findingLyrics = parts.state.findingLyrics,
            stacked = true,
        )
        LosslessCheckCard(parts.state.lossless)
        Fields(parts, draft)
        FileInfoCard(loaded)
    }
}

/** A column that scrolls, and lets go of the focus when what is not a field is pressed. */
@Composable
private fun EditorColumn(
    scroll: ScrollState,
    modifier: Modifier,
    padding: Dp,
    end: Dp = padding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier =
            modifier
                .pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } }
                .scrollbar(scroll)
                .verticalScroll(scroll)
                .padding(start = padding, end = end, top = padding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The editor's head: the cover and what the song is, with the inspector's menu and close. */
@Composable
private fun Head(parts: EditorParts, centered: Boolean, closeable: Boolean) {
    val state = parts.state
    val draft = state.draft ?: return
    val onIntent = parts.onIntent
    val track = state.position(TagField.TrackNumber).number.ifBlank { null }
    val details =
        listOfNotNull(
                draft.tags.first(TagField.Album.key)?.ifBlank { null },
                draft.tags.first(TagField.Date.key)?.ifBlank { null }?.take(4),
                track?.let { stringResource(R.string.track_position, it) },
            )
            .joinToString(" · ")
            .ifEmpty { null }
    EditorHeader(
        title = state.title,
        artist = state.artist,
        details = details,
        facts = fileFacts(state),
        cover =
            CoverModel(
                cover = draft.cover?.data,
                info = state.coverInfo,
                changed = draft.isCoverChanged,
                hasOriginal = draft.original.frontCover != null,
                loading = state.loadingCover,
                warnSmall = state.settings.warnSmallArtwork,
            ),
        actions =
            CoverActions(
                onChange = parts.onChangeCover,
                onFind = parts.onFindCover,
                onRemove = { onIntent(EditorIntent.RemoveCover) },
                onRevert = { onIntent(EditorIntent.RevertCover) },
                onPlay = { onIntent(EditorIntent.Play) },
            ),
        isPlaying = state.isPlayingThis,
        centered = centered,
        // On a desktop a picture is dragged in from the file manager.
        coverModifier =
            Modifier.imageDropTarget { uri -> onIntent(EditorIntent.SetCoverFromUri(uri)) },
        trailing =
            if (closeable) {
                {
                    MoreMenu(parts)
                    StudioIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        onClick = parts.onClose,
                        size = 30.dp,
                    )
                }
            } else null,
    )
}

/** What the file is, as the head's chips: its format and audio, and the cover's size. */
@Composable
private fun fileFacts(state: EditorState): List<String> {
    val loaded = state.loaded ?: return emptyList()
    val bits = (state.lossless as? LosslessCheckState.Done)?.report?.bitsPerSample
    val audio = loaded.audio
    // One chip each, so that the head wraps them where it is narrow rather than cutting one short.
    return listOfNotNull(
        loaded.fileExtension?.uppercase(Locale.ROOT),
        bits?.let { stringResource(R.string.fact_bits, it) },
        audio?.sampleRateHz?.takeIf { it > 0 }?.let { kiloHertz(it) },
        audio
            ?.bitrateKbps
            ?.takeIf { it > 0 && bits == null }
            ?.let { stringResource(R.string.file_bitrate_value, it) },
        state.coverInfo?.let { stringResource(R.string.cover_size, it.width, it.height) },
    )
}

/** The inspector's menu: what the head has no button for. */
@Composable
private fun MoreMenu(parts: EditorParts) {
    var open by remember { mutableStateOf(false) }
    Box {
        StudioIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            onClick = { open = true },
            size = 30.dp,
        )
        ActionMenu(
            expanded = open,
            onDismissRequest = { open = false },
            actions =
                listOf(
                    MenuAction(
                        stringResource(R.string.revert_all),
                        Icons.AutoMirrored.Rounded.Undo,
                        enabled = parts.state.isDirty,
                        onClick = { parts.onIntent(EditorIntent.RevertAll) },
                    ),
                    MenuAction(
                        stringResource(R.string.lyrics_find),
                        Icons.Outlined.Lyrics,
                        enabled = !parts.state.findingLyrics,
                        shortcut = shortcutHint(KeyEvent.KEYCODE_L, ctrl = true, shift = true),
                        onClick = { parts.onIntent(EditorIntent.FindLyrics) },
                    ),
                ),
        )
    }
}

/** The phone's bar: back, what the screen is, and its menu. */
@Composable
private fun PhoneTopBar(parts: EditorParts, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.close),
            onClick = parts.onClose,
            size = 44.dp,
            iconSize = 22.dp,
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Studio.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (parts.state.status == EditorStatus.Ready) MoreMenu(parts)
    }
}

/**
 * The editor's foot: how many changes there are, and the two things to do with them. Always in
 * sight, however far the fields are scrolled, and above the keyboard while it is open.
 */
@Composable
private fun EditorFooter(state: EditorState, onIntent: (EditorIntent) -> Unit, phone: Boolean) {
    val colors = Studio.colors
    val draft = state.draft
    val count = (draft?.changedKeys?.size ?: 0) + if (draft?.isCoverChanged == true) 1 else 0
    Column(
        Modifier.fillMaxWidth()
            .background(if (phone) colors.ground else colors.panel)
            .then(if (phone) Modifier.navigationBarsPadding() else Modifier)
    ) {
        StudioDivider()
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = StudioDefaults.FooterHeight)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(7.dp)
                        .background(
                            if (count > 0) colors.accent else colors.textTertiary.copy(0.4f),
                            CircleShape,
                        )
                )
                Text(
                    if (count > 0) pluralStringResource(R.plurals.changes_count, count, count)
                    else stringResource(R.string.no_changes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StudioButton(
                text = stringResource(R.string.discard_changes),
                onClick = { onIntent(EditorIntent.RevertAll) },
                enabled = state.isDirty && !state.saving,
            )
            StudioButton(
                text = stringResource(if (state.saving) R.string.saving else R.string.save),
                onClick = { onIntent(EditorIntent.Save) },
                style = StudioButtonStyle.Primary,
                enabled = state.isDirty,
                busy = state.saving,
                shortcut = shortcutHint(KeyEvent.KEYCODE_S, ctrl = true),
            )
        }
    }
}

/** What the editor does from the keyboard, whatever has the focus. */
@Composable
private fun EditorShortcuts(
    state: EditorState,
    scroll: ScrollState,
    onIntent: (EditorIntent) -> Unit,
    onFindMetadata: () -> Unit,
    onFindCover: () -> Unit,
    onChangeCover: () -> Unit,
    onClose: () -> Unit,
) {
    val ready = state.status == EditorStatus.Ready
    val scope = rememberCoroutineScope()
    val page = { scroll.viewportSize * PAGE_SHARE }
    KeyboardShortcuts(
        title = stringResource(R.string.shortcuts_editor),
        shortcuts =
            listOf(
                Shortcut(
                    KeyEvent.KEYCODE_S,
                    stringResource(R.string.save),
                    ctrl = true,
                    enabled = ready,
                ) {
                    onIntent(EditorIntent.Save)
                },
                Shortcut(
                    KeyEvent.KEYCODE_L,
                    stringResource(R.string.lookup_find),
                    ctrl = true,
                    enabled = ready,
                    action = onFindMetadata,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_L,
                    stringResource(R.string.lyrics_find),
                    ctrl = true,
                    shift = true,
                    enabled = ready && !state.findingLyrics,
                ) {
                    onIntent(EditorIntent.FindLyrics)
                },
                Shortcut(
                    KeyEvent.KEYCODE_K,
                    stringResource(R.string.cover_find_online),
                    ctrl = true,
                    enabled = ready,
                    action = onFindCover,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_K,
                    stringResource(R.string.cover_change),
                    ctrl = true,
                    shift = true,
                    enabled = ready,
                    action = onChangeCover,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_P,
                    stringResource(R.string.play_song),
                    ctrl = true,
                    enabled = ready,
                ) {
                    onIntent(EditorIntent.Play)
                },
                Shortcut(
                    KeyEvent.KEYCODE_W,
                    stringResource(R.string.close),
                    ctrl = true,
                    action = onClose,
                ),
                Shortcut(
                    KeyEvent.KEYCODE_PAGE_DOWN,
                    stringResource(CoreUiR.string.shortcut_page_down),
                    enabled = ready,
                    repeats = true,
                ) {
                    scope.launch { scroll.animateScrollBy(page()) }
                },
                Shortcut(
                    KeyEvent.KEYCODE_PAGE_UP,
                    stringResource(CoreUiR.string.shortcut_page_up),
                    enabled = ready,
                    repeats = true,
                ) {
                    scope.launch { scroll.animateScrollBy(-page()) }
                },
            ),
    )
}

/** Most of what is in view: a field or two stay, to keep the place. */
private const val PAGE_SHARE = 0.85f

/**
 * Takes a picture dropped on this element, as one dragged out of a file manager on a desktop is,
 * and hands [onDropped] where it is. While one is held over it, an outline says it can be let go.
 */
@Composable
private fun Modifier.imageDropTarget(onDropped: (uri: String) -> Unit): Modifier {
    val activity = LocalActivity.current
    val currentOnDropped by rememberUpdatedState(onDropped)
    var hovered by remember { mutableStateOf(false) }
    val target =
        remember(activity) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    hovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    hovered = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    hovered = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val drag = event.toAndroidDragEvent()
                    // The picture is the other app's: this is what allows reading it. It lasts
                    // as long as the activity, which is longer than reading it takes.
                    activity?.requestDragAndDropPermissions(drag)
                    val clip = drag.clipData ?: return false
                    val uri =
                        (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).uri }
                            ?: return false
                    currentOnDropped(uri.toString())
                    return true
                }
            }
        }
    return dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().any { it.startsWith("image/") }
            },
            target = target,
        )
        .then(
            if (hovered) {
                Modifier.border(
                    width = 2.dp,
                    color = Studio.colors.accent,
                    shape = MaterialTheme.shapes.medium,
                )
            } else Modifier
        )
}

/** Every editable section, as groups of fields under their titles. */
@Composable
private fun Fields(parts: EditorParts, draft: TagDraft) {
    val state = parts.state
    val onIntent = parts.onIntent
    val separator = state.settings.multiValueSeparator
    val missing = EssentialFields.filter { field -> draft.tags[field.key].all { it.isBlank() } }
    if (missing.isNotEmpty()) AttentionNotice(missing.map { fieldLabel(it.key) })

    Section(stringResource(R.string.section_main)) {
        FieldInput(TagField.Title, draft, separator, onIntent)
        FieldInput(TagField.Artist, draft, separator, onIntent)
        FieldInput(TagField.Album, draft, separator, onIntent)
        FieldInput(TagField.AlbumArtist, draft, separator, onIntent)
        SideBySide(
            { FieldInput(TagField.Date, draft, separator, onIntent) },
            { FieldInput(TagField.Genre, draft, separator, onIntent) },
        )
        SideBySide(
            { PositionInput(TagField.TrackNumber, state, draft, onIntent) },
            { PositionInput(TagField.DiscNumber, state, draft, onIntent) },
        )
    }

    Section(stringResource(R.string.section_credits)) {
        CreditFields.forEach { FieldInput(it, draft, separator, onIntent) }
    }

    Section(stringResource(R.string.section_more)) {
        FieldInput(TagField.Comment, draft, separator, onIntent)
        SideBySide(
            { FieldInput(TagField.Bpm, draft, separator, onIntent) },
            { FieldInput(TagField.Isrc, draft, separator, onIntent) },
        )
        FieldInput(TagField.Copyright, draft, separator, onIntent)
        SideBySide(
            { FieldInput(TagField.Label, draft, separator, onIntent) },
            { FieldInput(TagField.Grouping, draft, separator, onIntent) },
        )
    }

    val lyrics = draft.tags.first(TagField.Lyrics.key).orEmpty()
    Section(
        stringResource(R.string.section_lyrics),
        trailing = {
            if (Lrc.isSynced(lyrics)) {
                // Said, not pressed: a label, where a chip would ask to be tapped.
                LabelChip(stringResource(R.string.lyrics_synced), Icons.Rounded.Schedule)
            }
        },
    ) {
        TagTextField(
            label = fieldLabel(TagField.Lyrics.key),
            value = lyrics,
            onValueChange = { onIntent(EditorIntent.SetField(TagField.Lyrics.key, listOf(it))) },
            changed = draft.isChanged(TagField.Lyrics.key),
            onRevert = { onIntent(EditorIntent.Revert(listOf(TagField.Lyrics.key))) },
            singleLine = false,
            minLines = 4,
            maxLines = 14,
        )
    }

    val otherKeys =
        (draft.tags.keys + draft.original.tags.keys).filter { it !in KnownKeys }.distinct().sorted()
    var adding by rememberSaveable { mutableStateOf(false) }
    Section(
        stringResource(R.string.section_all_tags),
        trailing = {
            StudioButton(
                text = stringResource(R.string.add_field),
                onClick = { adding = true },
                icon = Icons.Rounded.Add,
                style = StudioButtonStyle.Ghost,
            )
        },
    ) {
        Text(
            stringResource(R.string.all_tags_description),
            style = MaterialTheme.typography.bodySmall,
            color = Studio.colors.textTertiary,
        )
        otherKeys.forEach { key ->
            TagChipsField(
                label = key,
                values = draft.tags[key],
                onValuesChange = { onIntent(EditorIntent.SetField(key, it)) },
                changed = draft.isChanged(key),
                onRevert = { onIntent(EditorIntent.Revert(listOf(key))) },
                separator = separator,
            )
        }
    }
    if (adding) {
        AddFieldDialog(
            onAdd = { key, value ->
                adding = false
                onIntent(EditorIntent.SetField(key, listOf(value)))
            },
            onDismiss = { adding = false },
        )
    }
}

/** A titled group of fields. */
@Composable
private fun Section(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, trailing = trailing)
        content()
    }
}

/** Two fields side by side, sharing the width. */
@Composable
private fun SideBySide(start: @Composable () -> Unit, end: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { start() }
        Box(Modifier.weight(1f)) { end() }
    }
}

@Composable
private fun PositionInput(
    field: TagField,
    state: EditorState,
    draft: TagDraft,
    onIntent: (EditorIntent) -> Unit,
) {
    val position = state.position(field)
    val keys =
        listOf(field.key) +
            if (field == TagField.TrackNumber) TagField.TrackTotalKeys else TagField.DiscTotalKeys
    PositionField(
        label = fieldLabel(field.key),
        number = position.number,
        total = position.total,
        onChange = { number, total ->
            onIntent(EditorIntent.SetPosition(field, Position(number, total)))
        },
        changed = keys.any(draft::isChanged),
        onRevert = { onIntent(EditorIntent.Revert(keys)) },
    )
}

@Composable
private fun FieldInput(
    field: TagField,
    draft: TagDraft,
    separator: String,
    onIntent: (EditorIntent) -> Unit,
) {
    val label = fieldLabel(field.key)
    val changed = draft.isChanged(field.key)
    val revert = { onIntent(EditorIntent.Revert(listOf(field.key))) }
    if (field.multiValue) {
        TagChipsField(
            label = label,
            values = draft.tags[field.key],
            onValuesChange = { onIntent(EditorIntent.SetField(field.key, it)) },
            changed = changed,
            onRevert = revert,
            separator = separator,
        )
    } else {
        TagTextField(
            label = label,
            value = draft.tags.first(field.key).orEmpty(),
            onValueChange = { onIntent(EditorIntent.SetField(field.key, listOf(it))) },
            changed = changed,
            onRevert = revert,
            singleLine = field.kind != FieldKind.LongText,
            maxLines = if (field.kind == FieldKind.LongText) 6 else 1,
            placeholder =
                if (field.kind == FieldKind.Date) stringResource(CoreUiR.string.field_date_hint)
                else null,
            keyboardType =
                if (field.kind == FieldKind.Number) KeyboardType.Number else KeyboardType.Text,
            capitalization =
                if (field == TagField.Isrc) KeyboardCapitalization.Characters
                else KeyboardCapitalization.Sentences,
        )
    }
}

@Composable
private fun AddFieldDialog(onAdd: (key: String, value: String) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    val normalized = key.trim().uppercase().replace(' ', '_')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_field)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                TonalTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = stringResource(R.string.add_field_name),
                    placeholder = stringResource(R.string.add_field_name_hint),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next,
                        ),
                    colors = TonalFieldDefaults.dialogColors(),
                )
                TonalTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = stringResource(R.string.add_field_value),
                    colors = TonalFieldDefaults.dialogColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(normalized, value.trim()) },
                enabled = normalized.isNotEmpty() && value.isNotBlank(),
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EditorProblem(
    status: EditorStatus,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(Spacing.extraLarge), contentAlignment = Alignment.Center) {
        when (status) {
            EditorStatus.NotFound ->
                PlaceholderCard(
                    title = stringResource(R.string.file_gone_title),
                    description = stringResource(R.string.file_gone_description),
                    icon = Icons.Rounded.SearchOff,
                    isError = true,
                    actionText = stringResource(R.string.go_back),
                    onAction = onClose,
                )
            EditorStatus.AccessDenied ->
                PlaceholderCard(
                    title = stringResource(R.string.access_denied_title),
                    description = stringResource(R.string.access_denied_description),
                    icon = Icons.Rounded.Error,
                    isError = true,
                    actionText = stringResource(R.string.go_back),
                    onAction = onClose,
                )
            EditorStatus.Unsupported ->
                PlaceholderCard(
                    title = stringResource(R.string.unsupported_title),
                    description = stringResource(R.string.unsupported_description),
                    icon = Icons.Rounded.Error,
                    isError = true,
                    actionText = stringResource(R.string.go_back),
                    onAction = onClose,
                )
            is EditorStatus.Failed ->
                PlaceholderCard(
                    title = stringResource(R.string.failed_title),
                    description = status.message,
                    icon = Icons.Rounded.Error,
                    isError = true,
                    actionText = stringResource(R.string.retry),
                    onAction = onRetry,
                    secondaryActionText = stringResource(R.string.go_back),
                    onSecondaryAction = onClose,
                )
            else -> Unit
        }
    }
}
