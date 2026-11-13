package com.example.slatemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun Modifier.noIndicationClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

// ───────────── banner, folder strip, hints ─────────────

/** Navy banner: title + subtitle on the left, big thin clock on the right (as on Slate Home). */
@Composable
fun Banner(title: String, subtitle: String, compact: Boolean) {
    val ctx = LocalContext.current
    val time by produceState("") {
        while (true) {
            value = android.text.format.DateFormat.getTimeFormat(ctx).format(Date())
            delay(20_000)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Slate.Banner).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Txt(title, if (compact) 20 else 24, light = true)
                if (subtitle.isNotEmpty()) Txt(subtitle, 12, Slate.Dim)
            }
            Txt(time, if (compact) 26 else 34, light = true)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
    }
}

/** Previous folder (dim) | current folder | next folder (dim). */
@Composable
fun FolderStrip(names: List<String>, index: Int, onSelect: (Int) -> Unit) {
    val n = names.size
    val prev = (index + n - 1) % n
    val next = (index + 1) % n
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt(names[prev], 17, Slate.Dim, modifier = Modifier.weight(1f).noIndicationClick { onSelect(prev) })
            Txt(names[index], 22, align = TextAlign.Center, modifier = Modifier.weight(1.3f))
            Txt(names[next], 17, Slate.Dim, align = TextAlign.End, modifier = Modifier.weight(1f).noIndicationClick { onSelect(next) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
    }
}

@Composable
private fun Hint(btn: String, label: String) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.border(1.dp, Slate.Blue, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Txt(btn, 10, Slate.BlueLight, bold = true)
        }
        Spacer(Modifier.width(4.dp))
        Txt(label, 11, Slate.Dim)
    }
}

@Composable
fun HintBar(ui: UiState) {
    val items = when {
        ui.typing -> listOf("A" to "Done", "B" to "Close", "↓" to "Results")
        ui.panelActive() -> listOf("A" to "Select", "◀ ▶" to "Adjust", "B" to "Back", "X" to "Play / Pause")
        else -> listOf("A" to "Play", "X" to "Pause", "Y" to "Search", "L1/R1" to "Folders", "L2/R2" to "Track")
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xF0050506)).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) { items.forEach { (b, l) -> Hint(b, l) } }
}

// ───────────── list ─────────────

class RowData(
    val key: Any,
    val title: String,
    val subtitle: String?,
    val detail: String?,
    val track: Track?,
    val icon: String?,
    val trackId: Long?,
    val onClick: () -> Unit
)

@Composable
private fun SlateRow(r: RowData, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    val playing = r.trackId != null && Player.state.track?.id == r.trackId
    val bg = when {
        selected && active -> Modifier.background(Slate.Select)
        selected -> Modifier.background(Slate.SelectDim)
        else -> Modifier
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)).then(bg).noIndicationClick(onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            r.track != null -> AlbumArt(r.track, Modifier.size(46.dp), px = 128)
            r.icon != null -> BbIcon(r.icon, 46.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(r.title, 16, bold = true)
            if (r.subtitle != null) Txt(r.subtitle, 13, if (selected && active) Color(0xFFD6ECFF) else Slate.Dim)
        }
        if (r.detail != null) Txt(r.detail, 13, if (selected && active) Color(0xFFD6ECFF) else Slate.Dim)
        if (playing) BbIcon("sound", 24.dp, Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SlateList(rows: List<RowData>, ui: UiState, emptyText: String, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    SideEffect {
        ui.listSize = rows.size
        ui.onActivate = { i -> rows.getOrNull(i)?.onClick?.invoke() }
    }
    LaunchedEffect(ui.sel, rows.size) {
        val vis = state.layoutInfo.visibleItemsInfo
        if (rows.isNotEmpty() && (vis.isEmpty() || ui.sel <= vis.first().index || ui.sel >= vis.last().index)) {
            state.animateScrollToItem((ui.sel - 2).coerceAtLeast(0))
        }
    }
    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt(emptyText, 15, Slate.Dim, align = TextAlign.Center) }
        return
    }
    val active = !ui.panelActive() && !ui.typing
    LazyColumn(modifier.fillMaxSize(), state = state) {
        itemsIndexed(rows, key = { _, r -> r.key }) { i, r ->
            SlateRow(r, selected = i == ui.sel, active = active, onClick = { ui.sel = i; r.onClick() })
        }
    }
}

@Composable
fun SearchBar(ui: UiState) {
    val fr = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(ui.typing) { if (ui.typing) fr.requestFocus() else fm.clearFocus() }
    BasicTextField(
        value = ui.query, onValueChange = { ui.query = it; ui.sel = 0 }, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Slate.BlueLight),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { ui.typing = false }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).focusRequester(fr)
            .clip(RoundedCornerShape(4.dp)).background(Color(0xCC101114))
            .border(2.dp, Slate.Blue, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box { if (ui.query.isEmpty()) Txt("Search songs, artists, albums", 16, Slate.Dim); inner() }
        }
    )
}

// ───────────── controls ─────────────

@Composable
fun ChromeButton(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, selected: Boolean, onClick: () -> Unit) {
    val top = if (selected) Color(0xFF4DA3F5) else Slate.ChromeHi
    val bottom = if (selected) Color(0xFF0B5CB8) else Slate.ChromeLo
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(top, bottom)))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFBFE0FF) else Color(0xFF9AA0A6), CircleShape)
            .noIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        // glossy top half
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.5f).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x05FFFFFF))))
        )
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Chip(text: String, on: Boolean, selected: Boolean, onClick: () -> Unit) {
    val bg = if (on) Slate.Select else Slate.Chrome
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFBFE0FF) else Color(0xFF80868E), RoundedCornerShape(14.dp))
            .noIndicationClick(onClick).padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Txt(text, 13, bold = true) }
}

@Composable
fun SlateSlider(value: Float, selected: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.height(26.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart
    ) {
        val v = value.coerceIn(0f, 1f)
        val shape = RoundedCornerShape(4.dp)
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0xFF2A2C30))))
                .border(1.dp, if (selected) Slate.BlueLight else Color(0xFF50545A), shape)
        )
        Box(Modifier.fillMaxWidth(v).height(8.dp).clip(shape).background(Slate.Select))
        Box(
            Modifier.offset(x = (maxWidth - 20.dp) * v).size(20.dp).clip(CircleShape).background(Slate.Chrome)
                .border(if (selected) 2.dp else 1.dp, if (selected) Slate.BlueLight else Color(0xFF9AA0A6), CircleShape)
        )
    }
}

/** Slim "now playing" strip shown under the portrait lists. */
@Composable
fun MiniPlayer(ui: UiState) {
    val st = Player.state
    val t = st.track ?: return
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1E4378), Color(0xFF0B1A38))))
            .noIndicationClick { ui.select(3) }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(t, Modifier.size(40.dp), px = 128)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Txt(t.title, 15, bold = true)
            Txt(t.artist, 12, Slate.Dim)
        }
        ChromeButton(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play / Pause", 38.dp, false) { Player.togglePlay() }
    }
}
