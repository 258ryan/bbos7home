package com.example.slatemusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
            Modifier.fillMaxWidth().glassBar().windowInsetsPadding(WindowInsets.statusBars)
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
            Modifier.fillMaxWidth().metalBand().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt(names[prev], 17, Slate.Dim, modifier = Modifier.weight(1f).noIndicationClick { onSelect(prev) })
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 3 } + fadeOut(tween(120)))
                },
                modifier = Modifier.weight(1.3f), label = "folder"
            ) { i -> Txt(names[i], 22, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            Txt(names[next], 17, Slate.Dim, align = TextAlign.End, modifier = Modifier.weight(1f).noIndicationClick { onSelect(next) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
    }
}

@Composable
private fun Hint(btn: String, label: String) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.chrome(11.dp, dark = true).padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 4.dp)) {
            Txt(btn, 10, Color.White, bold = true)
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
        Modifier.fillMaxWidth().leather(0.97f).windowInsetsPadding(WindowInsets.navigationBars)
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
    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(150), label = "sel")
    val lift by animateFloatAsState(if (selected && active) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "lift")
    val bg = if (sel > 0.01f) Modifier.gel(8.dp, glow = active, dim = !active, alpha = sel) else Modifier.bevelLines()
    val hi = selected && active
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = 1f + 0.015f * lift; scaleY = 1f + 0.015f * lift }
            .then(bg).noIndicationClick(onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            r.track != null -> AlbumArt(r.track, Modifier.size(46.dp).dropShadow(3.dp, 4.dp, 2.dp), px = 128)
            r.icon != null -> BbIcon(r.icon, 46.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(r.title, 16, bold = true)
            if (r.subtitle != null) Txt(r.subtitle, 13, if (hi) Color(0xFFD6ECFF) else Slate.Dim)
        }
        if (r.detail != null) Txt(r.detail, 13, if (hi) Color(0xFFD6ECFF) else Slate.Dim)
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
            .recessed(5.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box { if (ui.query.isEmpty()) Txt("Search songs, artists, albums", 16, Slate.Dim); inner() }
        }
    )
}

// ───────────── controls ─────────────

@Composable
fun ChromeButton(icon: Painter, description: String, size: Dp, selected: Boolean, pulse: Int = 0, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val base by animateFloatAsState(
        when { pressed -> 0.92f; selected -> 1.1f; else -> 1f },
        spring(dampingRatio = 0.5f, stiffness = 500f), label = "btn"
    )
    val squash = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (selected && pulse > 0) { squash.animateTo(0.9f, tween(60)); squash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
    }
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = base * squash.value; scaleY = base * squash.value }
            .then(if (selected) Modifier.gel(size / 2, glow = true) else Modifier.chrome(size / 2))
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Chip(text: String, on: Boolean, selected: Boolean, onClick: () -> Unit) {
    val look = if (on) Modifier.gel(14.dp) else Modifier.chrome(14.dp, dark = true)
    Box(
        Modifier.then(look)
            .then(if (selected) Modifier.border(2.dp, Color(0xFFBFE0FF), RoundedCornerShape(14.dp)) else Modifier)
            .noIndicationClick(onClick).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 9.dp)
    ) { Txt(text, 13, bold = true) }
}

@Composable
fun SlateSlider(value: Float, selected: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.height(30.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart
    ) {
        val v = value.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(10.dp).recessed(5.dp))
        Box(Modifier.fillMaxWidth(v).height(10.dp).padding(1.dp).gel(4.dp))
        Box(
            Modifier.offset(x = (maxWidth - 28.dp) * v).size(28.dp).chromeKnob()
                .then(if (selected) Modifier.border(2.dp, Slate.BlueLight, CircleShape) else Modifier)
        )
    }
}

/** Slim "now playing" strip shown under the portrait lists. */
@Composable
fun MiniPlayer(ui: UiState) {
    val st = Player.state
    val t = st.track ?: return
    Row(
        Modifier.fillMaxWidth().glassBar().noIndicationClick { ui.select(3) }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(t, Modifier.size(40.dp), px = 128)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Txt(t.title, 15, bold = true)
            Txt(t.artist, 12, Slate.Dim)
        }
        ChromeButton(painterResource(if (st.playing) R.drawable.ic_pause else R.drawable.ic_play), "Play / Pause", 40.dp, false) { Player.togglePlay() }
    }
}
