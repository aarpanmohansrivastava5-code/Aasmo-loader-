package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface MediaRemoteCommand {
    data class TogglePlayPause(val tabId: Long) : MediaRemoteCommand
    data class SeekBy(val tabId: Long, val seconds: Int) : MediaRemoteCommand
    data class Stop(val tabId: Long) : MediaRemoteCommand
}

object MediaControllerBus {
    private val _commands = MutableSharedFlow<MediaRemoteCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()

    fun emit(cmd: MediaRemoteCommand) {
        _commands.tryEmit(cmd)
    }
}

class MediaPlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private var currentTabId: Long = -1L
    private var currentTitle: String = "Browser Media"
    private var currentSubtitle: String = "Aasmo Loader"
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        mediaSession = MediaSession(this, "AasmoMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (currentTabId != -1L) MediaControllerBus.emit(MediaRemoteCommand.TogglePlayPause(currentTabId))
                }

                override fun onPause() {
                    if (currentTabId != -1L) MediaControllerBus.emit(MediaRemoteCommand.TogglePlayPause(currentTabId))
                }

                override fun onFastForward() {
                    if (currentTabId != -1L) MediaControllerBus.emit(MediaRemoteCommand.SeekBy(currentTabId, 10))
                }

                override fun onRewind() {
                    if (currentTabId != -1L) MediaControllerBus.emit(MediaRemoteCommand.SeekBy(currentTabId, -10))
                }

                override fun onStop() {
                    if (currentTabId != -1L) MediaControllerBus.emit(MediaRemoteCommand.Stop(currentTabId))
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                currentTabId = intent.getLongExtra(EXTRA_TAB_ID, -1L)
                currentTitle = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Browser Audio"
                currentSubtitle = intent.getStringExtra(EXTRA_SUBTITLE)?.takeIf { it.isNotBlank() } ?: "Background Tab"
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                updateSessionAndNotification()
            }
            ACTION_TOGGLE -> {
                if (currentTabId != -1L) {
                    MediaControllerBus.emit(MediaRemoteCommand.TogglePlayPause(currentTabId))
                }
            }
            ACTION_REWIND -> {
                if (currentTabId != -1L) {
                    MediaControllerBus.emit(MediaRemoteCommand.SeekBy(currentTabId, -10))
                }
            }
            ACTION_FORWARD -> {
                if (currentTabId != -1L) {
                    MediaControllerBus.emit(MediaRemoteCommand.SeekBy(currentTabId, 10))
                }
            }
            ACTION_STOP -> {
                if (currentTabId != -1L) {
                    MediaControllerBus.emit(MediaRemoteCommand.Stop(currentTabId))
                }
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateSessionAndNotification() {
        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_STOP
            )
            .setState(stateCode, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (isPlaying) 1f else 0f)
            .build()
        mediaSession.setPlaybackState(state)
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentSubtitle)
                .build()
        )

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rewindPi = servicePendingIntent(ACTION_REWIND, 1)
        val togglePi = servicePendingIntent(ACTION_TOGGLE, 2)
        val forwardPi = servicePendingIntent(ACTION_FORWARD, 3)
        val stopPi = servicePendingIntent(ACTION_STOP, 4)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setContentIntent(launchIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "Rewind",
                    rewindPi,
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    togglePi,
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "Forward",
                    forwardPi,
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPi,
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Media Controls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notification music controller for background browser tabs"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "aasmo_media_playback"
        private const val NOTIFICATION_ID = 4042

        const val ACTION_UPDATE = "com.example.action.MEDIA_UPDATE"
        const val ACTION_TOGGLE = "com.example.action.MEDIA_TOGGLE"
        const val ACTION_REWIND = "com.example.action.MEDIA_REWIND"
        const val ACTION_FORWARD = "com.example.action.MEDIA_FORWARD"
        const val ACTION_STOP = "com.example.action.MEDIA_STOP"

        const val EXTRA_TAB_ID = "extra_tab_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun updateNotification(
            context: Context,
            tabId: Long,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TAB_ID, tabId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopNotification(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }
    }
}
