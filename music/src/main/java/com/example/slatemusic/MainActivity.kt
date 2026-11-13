package com.example.slatemusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Slate Music: a BB OS 7-style music player that matches Slate Home, driven by a gamepad.
 *
 *   D-pad  move   A  select / play   B  back   X  play / pause   Y  search   Select  shuffle
 *   L1 / R1  previous / next folder   L2 / R2  previous / next track   Start  Now Playing panel
 *   Landscape: D-pad right moves into the Now Playing panel, left (or B) returns to the list.
 */
class MainActivity : ComponentActivity() {
    private val ui = UiState()
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var hasAccess by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checkAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Player.init(this)
        checkAccess()
        if (!hasAccess) requestAccess()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!ui.back()) { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        setContent { MusicApp(ui, tracks, hasAccess, ::requestAccess) }
    }

    override fun onResume() {
        super.onResume()
        Player.refreshVolume()
        if (hasAccess) loadTracks()
    }

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun checkAccess() {
        hasAccess = checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED
        if (hasAccess) loadTracks()
    }

    private fun requestAccess() {
        val p = mutableListOf(audioPermission())
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(p.toTypedArray())
    }

    private fun loadTracks() {
        Thread {
            val l = MusicLibrary.load(this)
            runOnUiThread { if (l != tracks) tracks = l }
        }.start()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val up = e.action == KeyEvent.ACTION_UP
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (up) { if (ui.typing) ui.typing = false else ui.activate() }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> { if (up) onBackPressedDispatcher.onBackPressed(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (up) Player.togglePlay(); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (up) { if (ui.searching) ui.closeSearch() else ui.openSearch() }; return true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { if (up) Player.toggleShuffle(); return true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (up) ui.toggleNowPlaying(); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (up) ui.shiftTab(-1); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (up) ui.shiftTab(1); return true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { if (up) Player.prev(); return true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { if (up) Player.next(false); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val dy = if (e.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else if (e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else 0
                val dx = if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else 0
                // While typing, only "down" (leave the search box) is ours; the rest moves the text cursor.
                if (ui.typing && dy <= 0) return super.dispatchKeyEvent(e)
                if (down) ui.dpad(dx, dy)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }
}
