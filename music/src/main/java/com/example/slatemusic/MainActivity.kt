package com.example.slatemusic

import ai.heretek.pwnapple.theme.PwnappleTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Slate Music: a skeuomorphic (iOS 6 / BB OS 7 style) music player, usable with a gamepad.
 *
 *   A  select        B  back
 *   X  play / pause  Y  shuffle      Select  repeat
 *   L1 / R1  previous / next track   L2 / R2  previous / next tab   Start  Now Playing
 *   On Now Playing: D-pad left / right = seek 10 s, up / down = volume
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
        setContent {
            PwnappleTheme(soundFeedbackEnabled = true) {
                MusicApp(ui, tracks, hasAccess, ::requestAccess)
            }
        }
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
        val nowPlaying = ui.tab == 3
        when (e.keyCode) {
            // Compose treats DPAD_CENTER / ENTER as "click"; make A do the same.
            KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(
                KeyEvent(
                    e.downTime, e.eventTime, e.action, KeyEvent.KEYCODE_DPAD_CENTER, e.repeatCount,
                    e.metaState, e.deviceId, e.scanCode, e.flags, e.source
                )
            )
            KeyEvent.KEYCODE_BUTTON_B -> { if (up) onBackPressedDispatcher.onBackPressed(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (up) Player.togglePlay(); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (up) Player.toggleShuffle(); return true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { if (up) Player.cycleRepeat(); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (up) Player.prev(); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (up) Player.next(false); return true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { if (up) ui.shiftTab(-1); return true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { if (up) ui.shiftTab(1); return true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (up) ui.toggleNowPlaying(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (nowPlaying) { if (down) Player.seekBy(-10_000); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (nowPlaying) { if (down) Player.seekBy(10_000); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (nowPlaying) { if (down) Player.bumpVolume(1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (nowPlaying) { if (down) Player.bumpVolume(-1); return true }
        }
        return super.dispatchKeyEvent(e)
    }
}
