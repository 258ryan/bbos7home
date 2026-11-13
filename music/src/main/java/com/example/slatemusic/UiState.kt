package com.example.slatemusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class Detail {
    data class Artist(val name: String) : Detail()
    data class AlbumOf(val albumId: Long, val name: String) : Detail()
}

/**
 * All navigation state. Selection is tracked here (not via Compose focus) so the highlight is always
 * visible and the gamepad behaves the same everywhere.
 *
 * Portrait: folders = Songs / Artists / Albums / Now Playing.
 * Landscape: the Now Playing panel is always on the right; zone 0 = library list, zone 1 = panel.
 */
class UiState {
    var tab by mutableStateOf(0)
    var lastTab = 0
    var detail by mutableStateOf<Detail?>(null)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var typing by mutableStateOf(false)
    var zone by mutableStateOf(0)
    var sel by mutableStateOf(0)
    var prow by mutableStateOf(1)     // panel row: 0 scrubber, 1 transport, 2 shuffle/repeat, 3 volume
    var pcol by mutableStateOf(1)     // transport column 0..2
    var pcol2 by mutableStateOf(0)    // shuffle / repeat
    var landscape = false
    var tabCount = 4
    var listSize = 0
    var onActivate: (Int) -> Unit = {}

    fun panelActive() = tab == 3 || (landscape && zone == 1)

    fun select(t: Int) {
        if (tab != 3) lastTab = tab
        tab = t
        detail = null
        sel = 0
        zone = 0
        if (t != 0) closeSearch()
    }

    fun shiftTab(delta: Int) = select((tab + delta + tabCount) % tabCount)

    fun toggleNowPlaying() {
        if (landscape) zone = if (zone == 0 && Player.state.track != null) 1 else 0
        else if (tab == 3) select(lastTab) else select(3)
    }

    fun openSearch() { tab = 0; detail = null; sel = 0; zone = 0; searching = true; typing = true }

    fun closeSearch() { searching = false; typing = false; query = "" }

    /** Returns true when B was used for something inside the app (otherwise the app may close). */
    fun back(): Boolean = when {
        typing -> { typing = false; true }
        searching -> { closeSearch(); sel = 0; true }
        detail != null -> { detail = null; sel = 0; true }
        landscape && zone == 1 -> { zone = 0; true }
        tab == 3 -> { select(lastTab); true }
        tab != 0 -> { select(0); true }
        else -> false
    }

    fun dpad(dx: Int, dy: Int) {
        if (typing) { if (dy > 0) typing = false; return }
        if (!panelActive()) {
            if (dy != 0) sel = (sel + dy).coerceIn(0, maxOf(0, listSize - 1))
            else if (dx > 0 && landscape && Player.state.track != null) zone = 1
            return
        }
        if (dy != 0) { prow = (prow + dy).coerceIn(0, 3); return }
        val canLeaveLeft = landscape && tab != 3
        when (prow) {
            0 -> Player.seekBy(dx * 10_000)
            1 -> if (dx < 0 && pcol == 0 && canLeaveLeft) zone = 0 else pcol = (pcol + dx).coerceIn(0, 2)
            2 -> if (dx < 0 && pcol2 == 0 && canLeaveLeft) zone = 0 else pcol2 = (pcol2 + dx).coerceIn(0, 1)
            3 -> Player.bumpVolume(dx)
        }
    }

    fun activate() {
        if (!panelActive()) { onActivate(sel); return }
        when (prow) {
            0 -> Player.togglePlay()
            1 -> when (pcol) { 0 -> Player.prev(); 1 -> Player.togglePlay(); else -> Player.next(false) }
            2 -> if (pcol2 == 0) Player.toggleShuffle() else Player.cycleRepeat()
            3 -> Player.changeVolume(if (Player.volume > 0f) 0f else 0.5f)
        }
    }
}
