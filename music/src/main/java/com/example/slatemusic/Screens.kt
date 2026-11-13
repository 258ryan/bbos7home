package com.example.slatemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TAB_NAMES = listOf("Songs", "Artists", "Albums", "Now Playing")

@Composable
fun MusicApp(ui: UiState, tracks: List<Track>, hasAccess: Boolean, onRequestAccess: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().leather()) {
        val landscape = maxWidth > maxHeight
        SideEffect {
            ui.landscape = landscape
            ui.tabCount = if (landscape) 3 else 4
            if (landscape && ui.tab == 3) ui.tab = ui.lastTab
        }
        val names = TAB_NAMES.take(if (landscape) 3 else 4)
        val (title, subtitle) = headerFor(ui, tracks)
        Column(Modifier.fillMaxSize()) {
            Banner(title, subtitle, compact = landscape)
            FolderStrip(names, ui.tab.coerceIn(0, names.size - 1)) { ui.select(it) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasAccess -> AccessScreen(onRequestAccess)
                    landscape -> Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.4f).fillMaxHeight()) { Library(ui, tracks, landscape) }
                        Box(Modifier.weight(0.6f).fillMaxHeight().background(Color(0x66000000)).stitched()) { NowPlaying(ui, compact = true) }
                    }
                    ui.tab == 3 -> NowPlaying(ui, compact = false)
                    else -> Library(ui, tracks, landscape)
                }
            }
            if (!landscape && ui.tab != 3) MiniPlayer(ui)
            HintBar(ui)
        }
    }
}

private fun headerFor(ui: UiState, tracks: List<Track>): Pair<String, String> {
    val d = ui.detail
    return when {
        ui.tab == 3 -> "Now Playing" to (Player.state.track?.album ?: "")
        d is Detail.Artist -> d.name to "Artist"
        d is Detail.AlbumOf -> d.name to "Album"
        ui.tab == 1 -> "Artists" to "${tracks.map { it.artist }.toSet().size} artists"
        ui.tab == 2 -> "Albums" to "${tracks.map { it.albumId }.toSet().size} albums"
        else -> "Music" to "${tracks.size} songs"
    }
}

@Composable
private fun AccessScreen(onRequestAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        BbIcon("music", 72.dp)
        Spacer(Modifier.height(12.dp))
        Txt("Allow access to your music to see your library.", 16, align = TextAlign.Center, maxLines = 3)
        Spacer(Modifier.height(20.dp))
        Chip("Allow access", on = true, selected = false, onClick = onRequestAccess)
    }
}

// ───────────── library ─────────────

@Composable
private fun Library(ui: UiState, tracks: List<Track>, landscape: Boolean) {
    Column(Modifier.fillMaxSize()) {
        if (ui.searching && ui.tab == 0) SearchBar(ui)
        val d = ui.detail
        when {
            ui.tab == 1 && d is Detail.Artist -> TrackRows(ui, tracks.filter { it.artist == d.name }, landscape)
            ui.tab == 2 && d is Detail.AlbumOf ->
                TrackRows(ui, tracks.filter { it.albumId == d.albumId }.sortedBy { it.trackNo }, landscape)
            ui.tab == 1 -> ArtistRows(ui, tracks)
            ui.tab == 2 -> AlbumRows(ui, tracks)
            else -> {
                val q = ui.query.trim()
                val shown = remember(tracks, q) {
                    if (q.isEmpty()) tracks else tracks.filter {
                        it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                    }
                }
                TrackRows(ui, shown, landscape)
            }
        }
    }
}

@Composable
private fun TrackRows(ui: UiState, shown: List<Track>, landscape: Boolean) {
    val rows = remember(shown, landscape) {
        shown.mapIndexed { i, t ->
            RowData(
                key = t.id, title = t.title, subtitle = t.artist + " — " + t.album, detail = fmt(t.durationMs),
                track = t, icon = null, trackId = t.id,
                onClick = { Player.play(shown, i); if (!landscape) ui.select(3) }
            )
        }
    }
    SlateList(rows, ui, "No songs found")
}

@Composable
private fun ArtistRows(ui: UiState, tracks: List<Track>) {
    val rows = remember(tracks) {
        tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (name, list) ->
            RowData(
                key = name, title = name, subtitle = if (list.size == 1) "1 song" else "${list.size} songs",
                detail = null, track = null, icon = "contacts", trackId = null,
                onClick = { ui.detail = Detail.Artist(name); ui.sel = 0 }
            )
        }
    }
    SlateList(rows, ui, "No artists found")
}

@Composable
private fun AlbumRows(ui: UiState, tracks: List<Track>) {
    val rows = remember(tracks) {
        tracks.groupBy { it.albumId }.values.map { it.sortedBy { t -> t.trackNo } }
            .sortedBy { it.first().album.lowercase() }.map { list ->
                val first = list.first()
                RowData(
                    key = first.albumId, title = first.album, subtitle = first.artist,
                    detail = "${list.size}", track = first, icon = null, trackId = null,
                    onClick = { ui.detail = Detail.AlbumOf(first.albumId, first.album); ui.sel = 0 }
                )
            }
    }
    SlateList(rows, ui, "No albums found")
}

// ───────────── Now Playing ─────────────

@Composable
private fun ArtWithReflection(track: Track, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).shadow(10.dp, RoundedCornerShape(3.dp)).border(1.dp, Color.Black, RoundedCornerShape(3.dp))) {
            AlbumArt(track, Modifier.fillMaxSize(), px = 600)
            Box(
                Modifier.fillMaxWidth().height(size * 0.45f)
                    .background(Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x05FFFFFF))))
            )
        }
        Box(Modifier.width(size).height(size * 0.28f)) {
            AlbumArt(
                track, Modifier.fillMaxSize().graphicsLayer { scaleY = -1f; alpha = 0.45f },
                px = 600, alignment = Alignment.BottomCenter
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xFF17191D)))))
        }
    }
}

@Composable
private fun Controls(ui: UiState, compact: Boolean, modifier: Modifier = Modifier) {
    val st = Player.state
    val t = st.track ?: return
    val active = ui.panelActive()
    val dur = st.durationMs
    val pos = st.positionMs
    val big = if (compact) 56.dp else 68.dp
    val small = if (compact) 44.dp else 52.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(t.title, if (compact) 18 else 20, bold = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Txt(t.artist, 15, Color(0xFFB8BEC6), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Txt(t.album, 13, Slate.Dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(fmt(pos.toLong()), 12, Color(0xFFB8BEC6), modifier = Modifier.width(42.dp))
            SlateSlider(
                value = if (dur > 0) pos.toFloat() / dur else 0f, selected = active && ui.prow == 0,
                onChange = { Player.seekTo((it * dur).toInt()) }, modifier = Modifier.weight(1f)
            )
            Txt("-" + fmt((dur - pos).toLong()), 12, Color(0xFFB8BEC6), align = TextAlign.End, modifier = Modifier.width(46.dp))
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 22.dp)) {
            ChromeButton(Icons.Filled.SkipPrevious, "Previous", small, active && ui.prow == 1 && ui.pcol == 0) { Player.prev() }
            ChromeButton(
                if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play / Pause", big,
                active && ui.prow == 1 && ui.pcol == 1
            ) { Player.togglePlay() }
            ChromeButton(Icons.Filled.SkipNext, "Next", small, active && ui.prow == 1 && ui.pcol == 2) { Player.next(false) }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(if (st.shuffle) "Shuffle: On" else "Shuffle: Off", st.shuffle, active && ui.prow == 2 && ui.pcol2 == 0) { Player.toggleShuffle() }
            Chip(
                when (st.repeat) { 1 -> "Repeat: All"; 2 -> "Repeat: One"; else -> "Repeat: Off" },
                st.repeat != 0, active && ui.prow == 2 && ui.pcol2 == 1
            ) { Player.cycleRepeat() }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BbIcon("sound", 22.dp)
            SlateSlider(
                value = Player.volume, selected = active && ui.prow == 3,
                onChange = { Player.changeVolume(it) }, modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun NowPlaying(ui: UiState, compact: Boolean) {
    val t = Player.state.track
    LaunchedEffect(Unit) { Player.refreshVolume() }
    if (t == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BbIcon("music", 64.dp)
                Spacer(Modifier.height(8.dp))
                Txt("Choose a song to start playing", 15, Slate.Dim)
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (compact) {
            // Landscape panel: art on the left, controls on the right, no reflection (height is tight).
            val art = minOf(maxHeight - 16.dp, maxWidth * 0.34f)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AlbumArtFrame(t, art)
                Controls(ui, compact = true, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            val art = minOf(maxWidth - 48.dp, maxHeight * 0.36f)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArtWithReflection(t, art)
                Spacer(Modifier.height(8.dp))
                Controls(ui, compact = false, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AlbumArtFrame(track: Track, size: Dp) {
    Box(Modifier.size(size).shadow(10.dp, RoundedCornerShape(3.dp)).border(1.dp, Color.Black, RoundedCornerShape(3.dp))) {
        AlbumArt(track, Modifier.fillMaxSize(), px = 600)
        Box(
            Modifier.fillMaxWidth().height(size * 0.45f)
                .background(Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x05FFFFFF))))
        )
    }
}
