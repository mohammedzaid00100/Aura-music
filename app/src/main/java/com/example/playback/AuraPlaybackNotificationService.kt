package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.AuraApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps Aura playback visible in Android's notification shade and routes
 * system media controls back to the same PlaybackController used by the app UI.
 *
 * The service stays silent until Aura has an active track. Once a track exists,
 * it becomes a media-playback foreground service with Previous / Play-Pause / Next
 * controls and remains available while that track is active, including while paused.
 */
class AuraPlaybackNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlaybackController
    private lateinit var notificationManager: NotificationManager
    private lateinit var systemMediaSession: MediaSession
    private var observeJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()

        controller = (application as AuraApplication).container.playbackController
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        systemMediaSession = MediaSession(this, "AuraMusicNotificationSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@AuraPlaybackNotificationService.controller.play()
                override fun onPause() = this@AuraPlaybackNotificationService.controller.pause()
                override fun onSkipToNext() = this@AuraPlaybackNotificationService.controller.skipToNext()
                override fun onSkipToPrevious() = this@AuraPlaybackNotificationService.controller.skipToPrevious()
                override fun onSeekTo(pos: Long) = this@AuraPlaybackNotificationService.controller.seekTo(pos)
            })
            isActive = true
        }

        observeJob = serviceScope.launch {
            controller.playbackState.collectLatest { state ->
                val track = state.currentTrack

                if (track == null) {
                    if (foregroundStarted) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        foregroundStarted = false
                    }
                    notificationManager.cancel(NOTIFICATION_ID)
                    return@collectLatest
                }

                updateMediaSession(
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    title = track.title,
                    artist = track.artist
                )

                val notification = buildNotification(
                    title = track.title,
                    artist = track.artist,
                    isPlaying = state.isPlaying
                )

                if (!foregroundStarted) {
                    startForeground(NOTIFICATION_ID, notification)
                    foregroundStarted = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> controller.skipToPrevious()
            ACTION_TOGGLE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.skipToNext()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()

        systemMediaSession.isActive = false
        systemMediaSession.release()

        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        notificationManager.cancel(NOTIFICATION_ID)

        super.onDestroy()
    }

    private fun updateMediaSession(
        isPlaying: Boolean,
        positionMs: Long,
        title: String,
        artist: String
    ) {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SEEK_TO

        systemMediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs,
                    if (isPlaying) 1f else 0f
                )
                .build()
        )

        systemMediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .build()
        )
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = servicePendingIntent(ACTION_PREVIOUS, 1)
        val toggleIntent = servicePendingIntent(ACTION_TOGGLE, 2)
        val nextIntent = servicePendingIntent(ACTION_NEXT, 3)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { "Aura Music" })
            .setContentText(artist.ifBlank { "Playing" })
            .setContentIntent(openAppIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    previousIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    toggleIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextIntent
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(systemMediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AuraPlaybackNotificationService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls for Aura Music"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "aura_playback"
        private const val NOTIFICATION_ID = 1107

        private const val ACTION_PREVIOUS = "com.example.aura.action.PREVIOUS"
        private const val ACTION_TOGGLE = "com.example.aura.action.TOGGLE"
        private const val ACTION_NEXT = "com.example.aura.action.NEXT"
    }
}
