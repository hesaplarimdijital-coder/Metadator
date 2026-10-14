/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.metadator.core.designsystem.component.LabelChip
import com.bobbyesp.metadator.core.designsystem.component.PlaceholderCard
import com.bobbyesp.metadator.core.designsystem.component.RolledIn
import com.bobbyesp.metadator.core.designsystem.component.SectionHeader
import com.bobbyesp.metadator.core.designsystem.component.ThroughFocus
import com.bobbyesp.metadator.core.designsystem.component.scrollbar
import com.bobbyesp.metadator.core.designsystem.theme.Spacing
import com.bobbyesp.metadator.core.designsystem.theme.Studio
import com.bobbyesp.metadator.core.designsystem.theme.defocused
import com.bobbyesp.metadator.core.designsystem.theme.dissolved
import com.bobbyesp.metadator.core.designsystem.theme.resizedTo
import com.bobbyesp.metadator.core.model.Track
import com.bobbyesp.metadator.core.ui.component.ArtworkImage
import com.bobbyesp.metadator.player.api.PlaybackState
import com.bobbyesp.metadator.player.api.PlayerController

/**
 * The player, full screen, as one column: the cover, the song's name, its controls, and under them
 * what plays next. It is the expanded content of [PlayerSheet], which owns how it gets on and off
 * the screen; the modifiers are how the sheet ties the pieces it shares with the bar.
 *
 * @param position where the song is, which only the seek line reads
 * @param settled whether the sheet is at rest, open: what waits for the pieces that travel to have
 *   arrived shows then
 * @param ownContentPresence how much of what only this has (all but the cover, the name and the
 *   play button) is there, from 0 to 1, read while drawing: it comes into focus once the sheet has
 *   opened some way. The surface itself is not part of it, and is always solid.
 */
@Composable
internal fun NowPlayingContent(
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    settled: Boolean,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBarModifier: Modifier = Modifier,
    artworkShape: Shape = MaterialTheme.shapes.extraLarge,
    artworkModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    ownContentPresence: () -> Float = { 1f },
) {
    val track = playback.current
    val colors = Studio.colors
    Surface(modifier = modifier, color = colors.ground, contentColor = colors.text) {
        Box(Modifier.fillMaxSize()) {
            if (track != null) PlayerBackdrop(track, Modifier.matchParentSize())
            Column(
                Modifier.fillMaxSize()
                    // Over the backdrop, which is the surface and stays as it is.
                    .dissolved(ownContentPresence)
                    // The list scrolls under the bar at the bottom; nothing goes under the top.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                PlayerTopRow(
                    onClose = onClose,
                    onEdit = track?.let { { onEdit(it.ref.uri) } },
                    modifier = topBarModifier,
                )
                if (track == null) {
                    Box(Modifier.fillMaxSize().padding(Spacing.extraLarge), Alignment.Center) {
                        PlaceholderCard(
                            title = stringResource(R.string.nothing_playing),
                            description = "",
                            icon = Icons.Rounded.PlayArrow,
                        )
                    }
                } else {
                    Stage(
                        track = track,
                        playback = playback,
                        position = position,
                        player = player,
                        settled = settled,
                        artworkShape = artworkShape,
                        artworkModifier = artworkModifier,
                        titleModifier = titleModifier,
                        artistModifier = artistModifier,
                        playButtonModifier = playButtonModifier,
                    )
                }
            }
        }
    }
}

/** The song and its controls, and the queue under them, as one list that scrolls. */
// Its parts are tied one by one to the other player's, each by a modifier of its own.
@Suppress("ModifierParameter")
@Composable
private fun Stage(
    track: Track,
    playback: PlaybackState,
    position: PlaybackPosition,
    player: PlayerController,
    settled: Boolean,
    artworkShape: Shape,
    artworkModifier: Modifier,
    titleModifier: Modifier,
    artistModifier: Modifier,
    playButtonModifier: Modifier,
) {
    val listState = rememberLazyListState()
    val bottomInset =
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().scrollbar(listState),
        contentPadding =
            PaddingValues(
                start = Spacing.extraLarge,
                end = Spacing.extraLarge,
                top = Spacing.small,
                bottom = Spacing.huge + bottomInset,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "player") {
            Column(
                modifier = Modifier.widthIn(max = StageMaxWidth).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                Cover(track, artworkShape, artworkModifier)
                TrackName(track, settled, titleModifier, artistModifier)
                SeekLine(
                    playback = playback,
                    position = position,
                    onSeek = player::seekTo,
                    modifier = Modifier.fillMaxWidth(),
                    times = SeekTimes.Below,
                    hitHeight = SeekLineDefaults.TouchHitHeight,
                )
                TransportControls(
                    playback = playback,
                    player = player,
                    playButtonModifier = playButtonModifier,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (playback.hasUpNext) {
            item(key = "up-next") {
                SectionHeader(
                    title = stringResource(R.string.up_next),
                    modifier =
                        Modifier.widthIn(max = StageMaxWidth)
                            .fillMaxWidth()
                            .padding(top = Spacing.large),
                )
            }
            upNextRows(playback, player, Modifier.widthIn(max = StageMaxWidth))
        }
    }
}

/** As wide as the stage gets on a tablet held upright: a cover any larger is a poster. */
private val StageMaxWidth = 480.dp

/** The room kept for the album's chip under the name until it rolls in: the chip's own height. */
private val AlbumChipRoom = 26.dp

/**
 * The playing song's cover, in either player. A new song's comes into focus over the old one's,
 * which goes out of focus beneath it and stays solid until it is covered: the cover is never seen
 * through, and never empty.
 *
 * @param modifier how it takes its size, and what ties it to the other player's
 */
@Composable
internal fun TrackArtwork(track: Track, shape: Shape, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = track,
        // Nothing of the container's own: each picture's focus is its transition, and the one
        // leaving has to wait for it.
        transitionSpec = {
            EnterTransition.None togetherWith
                ExitTransition.KeepUntilTransitionsFinished using
                SizeTransform(clip = false)
        },
        contentKey = { it.id },
        // The shape's clip is here and not on each picture: it is what a blur must not get past.
        modifier = modifier.clip(shape),
        label = "TrackArtwork",
    ) { shown ->
        val focus =
            transition.animateFloat(
                transitionSpec = { ThroughFocus.replacing() },
                label = "Focus",
            ) {
                if (it == EnterExitState.Visible) 1f else 0f
            }
        val blur = remember(focus) { { 1f - focus.value } }
        val leaving = transition.targetState == EnterExitState.PostExit
        ArtworkImage(
            model = shown.artwork,
            contentDescription = null,
            modifier =
                Modifier.fillMaxSize().defocused(blur, bounded = true).graphicsLayer {
                    alpha = if (leaving) 1f else focus.value
                },
            shape = RectangleShape,
            cacheKey = shown.artworkCacheKey,
        )
    }
}

/**
 * The song's cover. That it steps back while the music is paused is the sheet's doing, in
 * [sharedModifier]: the bar's cover has to do the same.
 *
 * @param sharedModifier what ties it to the bar's cover, applied once it has its size
 * @param modifier how it takes its size, which is what differs between layouts
 */
// Its parts are tied one by one to the other player's, each by a modifier of its own.
@Suppress("ModifierParameter")
@Composable
internal fun Cover(
    track: Track,
    shape: Shape,
    sharedModifier: Modifier,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f),
) {
    TrackArtwork(track, shape, modifier.then(sharedModifier))
}

/**
 * The song's title and, under it, who it is by, each a line of its own that the sheet ties to the
 * same line of the bar.
 *
 * Each is one line that says what the bar's says, set in a style the bar's is a smaller copy of
 * ([TrackNameDefaults]): scaled to the same height the two are then the same glyphs in the same
 * places, and the line travels whole. A title that wrapped here would be twice the shape of the
 * bar's, so one too long for the line scrolls instead.
 *
 * The album is this player's alone, so it is not part of what travels: it is a chip under the name,
 * which rolls into its place the first time the sheet settles. From then on it is one more thing on
 * this player, and comes and goes with the rest of it as the sheet is dragged. Its place is kept
 * for it meanwhile, so nothing below moves when it turns up.
 *
 * All of it starts at the leading edge, as it does in the bar. The title carries the weight and the
 * artist is set apart from it by its style, lighter in weight and in the color role for what
 * matters less, not by being made see-through.
 *
 * @param settled whether the sheet is at rest, open, which is when the album first shows
 */
// Its parts are tied one by one to the other player's, each by a modifier of its own.
@Suppress("ModifierParameter")
@Composable
internal fun TrackName(
    track: Track,
    settled: Boolean,
    titleModifier: Modifier,
    artistModifier: Modifier,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        TrackLine(track, titleModifier) { shown ->
            Text(
                shown.title,
                style = TrackNameDefaults.titleStyle,
                color = Studio.colors.text,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
        TrackLine(track, artistModifier, follows = true) { shown ->
            Text(
                shown.artist ?: stringResource(R.string.unknown_artist),
                style = TrackNameDefaults.artistStyle,
                color = TrackNameDefaults.artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Once it has turned up it stays: a finger that starts to pull the sheet down must not send
        // it away, any more than it does the controls.
        var albumArrived by remember { mutableStateOf(false) }
        LaunchedEffect(settled) { if (settled) albumArrived = true }
        val album = track.album
        if (album != null) {
            // The chip's place, kept while it is not there. Not a window it is clipped to: a pill
            // cut by a rectangle on its way in shows the rectangle.
            Box(
                modifier = Modifier.padding(top = Spacing.small).height(AlbumChipRoom),
                contentAlignment = Alignment.CenterStart,
            ) {
                RolledIn(album.takeIf { albumArrived }) { shown ->
                    LabelChip(label = shown, icon = Icons.Rounded.Album)
                }
            }
        }
    }
}

/**
 * One line about the playing song. A new song's comes in from the side through focus, a short way
 * and sharpening as it stops, while the old one's leaves the other way: the same as the album's
 * chip turning up, on its side.
 *
 * Nothing clips it. Slid the whole of its width inside its own box, the text was cut by the box's
 * edges as it went; a short way and out of focus, it needs no box to come out of.
 *
 * @param follows whether this is the second line of a pair, which then starts a moment after the
 *   first so that the two do not move as one block
 */
@Composable
internal fun TrackLine(
    track: Track,
    modifier: Modifier = Modifier,
    follows: Boolean = false,
    line: @Composable (Track) -> Unit,
) {
    val delay = if (follows) FOLLOW_MS else 0
    val shift = with(LocalDensity.current) { Spacing.extraLarge.roundToPx() }
    AnimatedContent(
        targetState = track,
        transitionSpec = {
            slideInHorizontally(ThroughFocus.arriving(delay)) { shift } togetherWith
                slideOutHorizontally(ThroughFocus.leaving(delay)) { -shift } using
                SizeTransform(clip = false)
        },
        contentKey = { it.id },
        modifier = modifier,
        label = "TrackLine",
    ) { shown ->
        val focus =
            transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) ThroughFocus.arriving(delay)
                    else ThroughFocus.leaving(delay)
                },
                label = "Focus",
            ) {
                if (it == EnterExitState.Visible) 1f else 0f
            }
        val presence = remember(focus) { { focus.value } }
        Box(Modifier.dissolved(presence)) { line(shown) }
    }
}

/** How much later the second line of a pair starts than the first, going and coming. */
private const val FOLLOW_MS = 140

/**
 * How the song's name is set in the full player, and in the bar: the same styles at the size of the
 * bar's roles in the type scale.
 */
internal object TrackNameDefaults {
    /** A headline, semibold: the title is what the eye should land on in either player. */
    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall

    val artistStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge

    /** The role for text that matters less, on whichever surface: a color, not an opacity. */
    val artistColor: Color
        @Composable get() = Studio.colors.textSecondary

    val barTitleStyle: TextStyle
        @Composable get() = titleStyle.resizedTo(MaterialTheme.typography.titleSmall.fontSize)

    val barArtistStyle: TextStyle
        @Composable get() = artistStyle.resizedTo(MaterialTheme.typography.bodySmall.fontSize)
}
