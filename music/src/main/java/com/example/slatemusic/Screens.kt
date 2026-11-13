package com.example.slatemusic

import ai.heretek.pwnapple.components.button.IosButton
import ai.heretek.pwnapple.components.button.IosButtonSize
import ai.heretek.pwnapple.components.button.IosButtonStyle
import ai.heretek.pwnapple.components.indicator.IosProgressBar
import ai.heretek.pwnapple.components.input.IosSlider
import ai.heretek.pwnapple.components.input.IosTextField
import ai.heretek.pwnapple.components.navigation.IosBackButton
import ai.heretek.pwnapple.components.navigation.IosNavBarStyle
import ai.heretek.pwnapple.components.navigation.IosNavigationBar
import ai.heretek.pwnapple.components.table.IosCellAccessory
import ai.heretek.pwnapple.components.table.IosTableCell
import ai.heretek.pwnapple.components.tabs.IosTabBar
import ai.heretek.pwnapple.components.tabs.IosTabItem
import ai.heretek.pwnapple.graphics.darkLinenBackground
import ai.heretek.pwnapple.graphics.glossHighlight
import ai.heretek.pwnapple.graphics.pinstripeBackground
import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────── UI state ─────────────

sealed class Detail {
    data class Artist(val name: String) : Detail()
    data class AlbumOf(val albumId: Long, val name: String) : Detail()
}

class UiState {
    var tab by mutableStateOf(0)           // 0 Songs, 1 Artists, 2 Albums, 3 Now Playing
    var lastTab = 0
    var detail by mutableStateOf<Detail?>(null)
    var query by mutableStateOf("")

    fun select(t: Int) {
        if (tab != 3) lastTab = tab
        tab = t
        detail = null
    }

    fun shiftTab(delta: Int) = select((tab + delta + 4) % 4)

    fun toggleNowPlaying() { if (tab == 3) select(lastTab) else select(3) }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Label(
    text: String, size: Int, color: Color = Color.White, bold: Boolean = false,
    modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, shadow: Boolean = true
) {
    Text(
        text = text, modifier = modifier, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, -2f), 0f) else null
        )
    )
}

// ───────────── root ─────────────

@Composable
fun MusicApp(ui: UiState, tracks: List<Track>, hasAccess: Boolean, onRequestAccess: () -> Unit) {
    BackHandler(enabled = ui.detail != null) { ui.detail = null }
    val tabs = remember {
        listOf(
            IosTabItem("Songs", Icons.Filled.MusicNote),
            IosTabItem("Artists", Icons.Filled.Person),
            IosTabItem("Albums", Icons.Filled.Album),
            IosTabItem("Now Playing", Icons.Filled.PlayArrow)
        )
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !hasAccess -> AccessScreen(onRequestAccess)
                ui.tab == 3 -> NowPlayingScreen()
                ui.tab == 1 -> ArtistsScreen(ui, tracks)
                ui.tab == 2 -> AlbumsScreen(ui, tracks)
                else -> TrackList("Songs", tracks, ui, onBack = null, searchable = true)
            }
        }
        IosTabBar(items = tabs, selectedIndex = ui.tab, onTabSelected = { ui.select(it) })
    }
}

@Composable
private fun AccessScreen(onRequestAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().pinstripeBackground().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Label("Slate Music", 26, Color(0xFF4C566C), bold = true, shadow = false)
        Spacer(Modifier.height(12.dp))
        Label("Allow access to your music to see your library.", 16, Color(0xFF4C566C), shadow = false)
        Spacer(Modifier.height(24.dp))
        IosButton(text = "Allow Access", onClick = onRequestAccess, style = IosButtonStyle.Blue)
    }
}

@Composable
private fun NowPlayingButton(ui: UiState) {
    if (Player.state.track != null && ui.tab != 3) {
        IosButton(
            text = "Now Playing", onClick = { ui.select(3) },
            style = IosButtonStyle.Blue, size = IosButtonSize.Small,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

// ───────────── lists ─────────────

@Composable
private fun TrackList(
    title: String, tracks: List<Track>, ui: UiState, onBack: (() -> Unit)?,
    searchable: Boolean = false, showArt: Boolean = false
) {
    val q = ui.query.trim()
    val shown = remember(tracks, q, searchable) {
        if (searchable && q.isNotEmpty()) tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        } else tracks
    }
    val current = Player.state.track?.id
    val navIcon: (@Composable () -> Unit)? =
        if (onBack != null) { { IosBackButton(text = "Back", onClick = onBack) } } else null
    Column(Modifier.fillMaxSize().pinstripeBackground()) {
        IosNavigationBar(
            title = title, style = IosNavBarStyle.BlackOpaque,
            navigationIcon = navIcon,
            actions = { NowPlayingButton(ui) }
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (searchable) item {
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    IosTextField(value = ui.query, onValueChange = { ui.query = it }, placeholder = "Search")
                }
            }
            itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                val lead: (@Composable () -> Unit)? =
                    if (showArt) { { AlbumArt(t, Modifier.size(44.dp), px = 128) } } else null
                IosTableCell(
                    title = t.title,
                    subtitle = if (showArt) t.artist else t.artist + " — " + t.album,
                    detailText = fmt(t.durationMs),
                    leadingContent = lead,
                    accessory = if (t.id == current) IosCellAccessory.Checkmark else IosCellAccessory.None,
                    onClick = { Player.play(shown, i); ui.select(3) }
                )
            }
        }
    }
}

@Composable
private fun ArtistsScreen(ui: UiState, tracks: List<Track>) {
    val d = ui.detail
    if (d is Detail.Artist) {
        TrackList(d.name, tracks.filter { it.artist == d.name }, ui, onBack = { ui.detail = null }, showArt = true)
        return
    }
    val groups = remember(tracks) { tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    Column(Modifier.fillMaxSize().pinstripeBackground()) {
        IosNavigationBar(title = "Artists", style = IosNavBarStyle.BlackOpaque, actions = { NowPlayingButton(ui) })
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(groups.entries.toList(), key = { _, e -> e.key }) { _, e ->
                IosTableCell(
                    title = e.key,
                    subtitle = if (e.value.size == 1) "1 song" else "${e.value.size} songs",
                    accessory = IosCellAccessory.DisclosureIndicator,
                    onClick = { ui.detail = Detail.Artist(e.key) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsScreen(ui: UiState, tracks: List<Track>) {
    val d = ui.detail
    if (d is Detail.AlbumOf) {
        TrackList(
            d.name, tracks.filter { it.albumId == d.albumId }.sortedBy { it.trackNo },
            ui, onBack = { ui.detail = null }
        )
        return
    }
    val albums = remember(tracks) {
        tracks.groupBy { it.albumId }.values.map { it.sortedBy { t -> t.trackNo } }
            .sortedBy { it.first().album.lowercase() }
    }
    Column(Modifier.fillMaxSize().pinstripeBackground()) {
        IosNavigationBar(title = "Albums", style = IosNavBarStyle.BlackOpaque, actions = { NowPlayingButton(ui) })
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(albums, key = { _, a -> a.first().albumId }) { _, a ->
                val first = a.first()
                IosTableCell(
                    title = first.album, subtitle = first.artist,
                    leadingContent = { AlbumArt(first, Modifier.size(52.dp), px = 128) },
                    accessory = IosCellAccessory.DisclosureIndicator,
                    onClick = { ui.detail = Detail.AlbumOf(first.albumId, first.album) }
                )
            }
        }
    }
}

// ───────────── Now Playing ─────────────

/** Glossy round chrome button; blue when focused / pressed (works with a gamepad). */
@Composable
private fun ChromeButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val focused by src.collectIsFocusedAsState()
    val lit = pressed || focused
    val sound = PwnappleTheme.sound
    val top = if (lit) Color(0xFF4DA3F5) else Color(0xFF6D7178)
    val bottom = if (lit) Color(0xFF0B5CB8) else Color(0xFF16181B)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, Color(0xFF9AA0A6), CircleShape)
            .glossHighlight(cornerRadius = size / 2, glossAlpha = 0.35f)
            .clickable(interactionSource = src, indication = null) { sound.playButtonClick(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun ArtWithReflection(track: Track, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).border(1.dp, Color(0xFF000000), RoundedCornerShapeSmall)
        ) {
            AlbumArt(track, Modifier.fillMaxSize(), px = 600)
            // top gloss, like the OS 7 / iOS 6 cover art
            Box(
                Modifier.fillMaxWidth().height(size * 0.45f).background(
                    Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x05FFFFFF)))
                )
            )
        }
        Box(Modifier.width(size).height(size * 0.28f)) {
            AlbumArt(
                track,
                Modifier.fillMaxSize().graphicsLayer { scaleY = -1f; alpha = 0.45f },
                px = 600, alignment = Alignment.BottomCenter
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x00000000), Color(0xFF23272B)))
                )
            )
        }
    }
}

private val RoundedCornerShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

@Composable
private fun NowPlayingControls(modifier: Modifier = Modifier) {
    val st = Player.state
    val t = st.track ?: return
    val dur = st.durationMs
    val pos = st.positionMs
    LaunchedEffect(Unit) { Player.refreshVolume() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(t.title, 20, bold = true)
        Spacer(Modifier.height(2.dp))
        Label(t.artist, 16, Color(0xFFB8BEC6))
        Label(t.album, 14, Color(0xFF8E959E))
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(fmt(pos.toLong()), 12, Color(0xFFB8BEC6), modifier = Modifier.width(44.dp))
            IosSlider(
                value = if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { Player.seekTo((it * dur).toInt()) },
                modifier = Modifier.weight(1f)
            )
            Label("-" + fmt((dur - pos).toLong()), 12, Color(0xFFB8BEC6), modifier = Modifier.width(44.dp))
        }
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ChromeButton(Icons.Filled.SkipPrevious, "Previous", 52.dp) { Player.prev() }
            ChromeButton(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play / Pause", 68.dp) { Player.togglePlay() }
            ChromeButton(Icons.Filled.SkipNext, "Next", 52.dp) { Player.next(false) }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IosButton(
                text = if (st.shuffle) "Shuffle: On" else "Shuffle: Off", onClick = { Player.toggleShuffle() },
                style = if (st.shuffle) IosButtonStyle.Blue else IosButtonStyle.Black, size = IosButtonSize.Small
            )
            IosButton(
                text = when (st.repeat) { 1 -> "Repeat: All"; 2 -> "Repeat: One"; else -> "Repeat: Off" },
                onClick = { Player.cycleRepeat() },
                style = if (st.repeat != 0) IosButtonStyle.Blue else IosButtonStyle.Black, size = IosButtonSize.Small
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeMute, null, tint = Color(0xFFB8BEC6), modifier = Modifier.size(20.dp))
            IosSlider(value = Player.volume, onValueChange = { Player.changeVolume(it) }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(Icons.Filled.VolumeUp, null, tint = Color(0xFFB8BEC6), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NowPlayingScreen() {
    val st = Player.state
    val t = st.track
    Column(Modifier.fillMaxSize().darkLinenBackground()) {
        IosNavigationBar(title = "Now Playing", style = IosNavBarStyle.BlackTranslucent)
        if (t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Label("Choose a song from your library", 16, Color(0xFF9AA0A8))
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth > maxHeight) {
                    val art = minOf(maxHeight * 0.62f, maxWidth * 0.38f)
                    Row(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ArtWithReflection(t, art)
                        NowPlayingControls(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    }
                } else {
                    val art = minOf(maxWidth - 48.dp, maxHeight * 0.4f)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtWithReflection(t, art)
                        Spacer(Modifier.height(12.dp))
                        NowPlayingControls(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
