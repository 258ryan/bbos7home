package com.example.slatemusic

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0            // 0 off, 1 all, 2 one
) {
    val track: Track? get() = queue.getOrNull(index)
}

/** Single app-wide player. Compose reads [state] / [volume]; the service mirrors it into a notification. */
object Player {
    var state by mutableStateOf(PlayerState())
        private set
    var volume by mutableStateOf(0f)
        private set

    private var app: Context? = null
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocus = false

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null && state.playing) {
                state = state.copy(positionMs = try { p.currentPosition } catch (_: Exception) { state.positionMs })
                handler.postDelayed(this, 500)
            }
        }
    }

    fun init(ctx: Context) { app = ctx.applicationContext; refreshVolume() }

    private fun audio() = app!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── queue / transport ──

    fun play(queue: List<Track>, index: Int) {
        state = state.copy(queue = queue, index = index)
        startCurrent()
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; pause() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeOnFocus = state.playing; pause() }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) { resumeOnFocus = false; resume() }
                }
            }.build()
        focusRequest = req
        return audio().requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun startCurrent() {
        val ctx = app ?: return
        val t = state.track ?: return
        release()
        val p = MediaPlayer()
        p.setAudioAttributes(attrs())
        p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
        try { p.setDataSource(ctx, t.uri) } catch (_: Exception) { p.release(); next(true); return }
        p.setOnPreparedListener {
            requestFocus()
            it.start()
            state = state.copy(playing = true, durationMs = it.duration, positionMs = 0)
            changed()
        }
        p.setOnCompletionListener { next(true) }
        p.setOnErrorListener { _, _, _ -> next(true); true }
        mp = p
        state = state.copy(playing = false, positionMs = 0, durationMs = t.durationMs.toInt())
        changed()
        p.prepareAsync()
    }

    private fun release() {
        handler.removeCallbacks(ticker)
        mp?.let { try { it.release() } catch (_: Exception) {} }
        mp = null
    }

    fun togglePlay() { if (state.playing) pause() else resume() }

    fun pause() {
        val p = mp ?: return
        try { p.pause() } catch (_: Exception) {}
        state = state.copy(playing = false, positionMs = try { p.currentPosition } catch (_: Exception) { state.positionMs })
        changed()
    }

    fun resume() {
        val p = mp ?: return
        requestFocus()
        try { p.start() } catch (_: Exception) { return }
        state = state.copy(playing = true)
        changed()
    }

    /** [auto] = the track ended by itself (so "repeat one" applies and the end of the queue stops). */
    fun next(auto: Boolean) {
        val s = state
        if (s.queue.isEmpty()) return
        val n = s.queue.size
        val idx = when {
            auto && s.repeat == 2 -> s.index
            s.shuffle && n > 1 -> { var r: Int; do { r = Random.nextInt(n) } while (r == s.index); r }
            s.index + 1 < n -> s.index + 1
            s.repeat == 1 || !auto -> 0
            else -> { release(); state = s.copy(playing = false, positionMs = 0); changed(); return }
        }
        state = s.copy(index = idx)
        startCurrent()
    }

    fun prev() {
        val s = state
        if (s.queue.isEmpty()) return
        if (s.positionMs > 3000) { seekTo(0); return }
        state = s.copy(index = if (s.index - 1 < 0) s.queue.size - 1 else s.index - 1)
        startCurrent()
    }

    fun seekTo(ms: Int) {
        val p = mp ?: return
        val to = ms.coerceIn(0, maxOf(0, state.durationMs))
        try { p.seekTo(to) } catch (_: Exception) {}
        state = state.copy(positionMs = to)
        changed()
    }

    fun seekBy(deltaMs: Int) = seekTo(state.positionMs + deltaMs)

    fun toggleShuffle() { state = state.copy(shuffle = !state.shuffle) }
    fun cycleRepeat() { state = state.copy(repeat = (state.repeat + 1) % 3) }

    // ── volume ──

    fun refreshVolume() {
        if (app == null) return
        val a = audio()
        volume = a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun changeVolume(f: Float) {
        val a = audio()
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f.coerceIn(0f, 1f) * max), 0)
        refreshVolume()
    }

    fun bumpVolume(steps: Int) {
        val a = audio()
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, (cur + steps).coerceIn(0, max), 0)
        refreshVolume()
    }

    // ── bookkeeping ──

    private fun changed() {
        handler.removeCallbacks(ticker)
        if (state.playing) handler.postDelayed(ticker, 500)
        app?.let { PlaybackService.update(it) }
    }
}
