package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.AuraApplication
import com.example.MainActivity
import com.example.core.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps Aura playback visible in Android's notification shade and routes
 * notification actions to the same PlaybackController used by the app UI.
 */
class AuraPlaybackNotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "aura_playback"
        private const val NOTIFICATION_ID = 1107

        private const val ACTION_PREVIOUS = "com.example.aura.PREVIOUS"
        private const val ACTION_PLAY_PAUSE = "com.example.aura.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.example.aura.NEXT"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playbackController: PlaybackController
        get() = (application as AuraApplication).container.playbackController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        serviceScope.launch {
            playbackController.playbackState.collectLatest { state ->
                startForeground(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> playbackController.skipToPrevious()
            ACTION_PLAY_PAUSE -> playbackController.togglePlayPause()
            ACTION_NEXT -> playbackController.skipToNext()
        }

        startForeground(NOTIFICATION_ID, buildNotification(playbackController.playbackState.value))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls for Aura Music"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: PlaybackState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = actionIntent(ACTION_PREVIOUS, 1)
        val playPauseIntent = actionIntent(ACTION_PLAY_PAUSE, 2)
        val nextIntent = actionIntent(ACTION_NEXT, 3)

        val playPauseIcon = if (state.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (state.isPlaying) "Pause" else "Play"

        val track = state.currentTrack
        val title = track?.title?.takeIf { it.isNotBlank() } ?: "Aura Music"
        val artist = track?.artist?.takeIf { it.isNotBlank() } ?: "Ready to play"

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(track != null)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(playPauseIcon, playPauseLabel, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(mediaStyle)
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AuraPlaybackNotificationService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
