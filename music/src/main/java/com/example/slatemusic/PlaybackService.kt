package com.example.slatemusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

/** Keeps playback alive in the background and shows a media notification / lock-screen controls. */
class PlaybackService : Service() {

    companion object {
        @Volatile private var instance: PlaybackService? = null
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
        private const val ACT_TOGGLE = "toggle"
        private const val ACT_NEXT = "next"
        private const val ACT_PREV = "prev"
        private const val ACT_STOP = "stop"

        fun update(ctx: Context) {
            val s = instance
            if (s != null) s.refresh(false)
            else try { ctx.startForegroundService(Intent(ctx, PlaybackService::class.java)) } catch (_: Exception) {}
        }
    }

    private lateinit var session: MediaSession
    private lateinit var nm: NotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "SlateMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Player.resume()
                override fun onPause() = Player.pause()
                override fun onSkipToNext() = Player.next(false)
                override fun onSkipToPrevious() = Player.prev()
                override fun onSeekTo(pos: Long) = Player.seekTo(pos.toInt())
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> Player.togglePlay()
            ACT_NEXT -> Player.next(false)
            ACT_PREV -> Player.prev()
            ACT_STOP -> {
                Player.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        refresh(true)
        return START_NOT_STICKY
    }

    private fun pending(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(icon: Int, title: String, act: String) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending(act)).build()

    private fun refresh(forceForeground: Boolean) {
        val st = Player.state
        val t = st.track

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t?.title ?: "")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t?.artist ?: "")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t?.album ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, st.durationMs.toLong())
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                )
                .setState(if (st.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, st.positionMs.toLong(), 1f)
                .build()
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(t?.title ?: "Slate Music")
            .setContentText(t?.artist ?: "")
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(st.playing)
            .addAction(action(R.drawable.ic_prev, "Previous", ACT_PREV))
            .addAction(
                action(
                    if (st.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (st.playing) "Pause" else "Play", ACT_TOGGLE
                )
            )
            .addAction(action(R.drawable.ic_next, "Next", ACT_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()

        if (st.playing || forceForeground) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIF_ID, n)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            nm.notify(NOTIF_ID, n)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        session.release()
        super.onDestroy()
    }
}
