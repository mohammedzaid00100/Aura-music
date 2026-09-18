package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.AuraApplication
import com.example.MainActivity
import com.example.core.model.Track
import com.example.core.util.ArtworkUtils
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Publishes Aura playback to Android system media controls.
 *
 * Android/HyperOS gets:
 * - real position + total duration for an interactive seek timeline
 * - Previous / Play-Pause / Next / Seek actions
 * - title, artist, album and artwork
 * - the same PlaybackController used by the in-app player
 */
class AuraPlaybackNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlaybackController
    private lateinit var notificationManager: NotificationManager
    private lateinit var systemMediaSession: MediaSession

    private var observeJob: Job? = null
    private var artworkJob: Job? = null
    private var foregroundStarted = false

    private var currentArtworkUrl: String = ""
    private var currentArtwork: Bitmap? = null
    private var lastNotificationKey: String? = null

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

                override fun onSeekTo(pos: Long) {
                    val duration = this@AuraPlaybackNotificationService
                        .controller
                        .playbackState
                        .value
                        .durationMs

                    val target = if (duration > 0L) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
                    this@AuraPlaybackNotificationService.controller.seekTo(target)
                }
            })
            isActive = true
        }

        observeJob = serviceScope.launch {
            controller.playbackState.collectLatest { state ->
                val track = state.currentTrack

                if (track == null) {
                    artworkJob?.cancel()
                    currentArtworkUrl = ""
                    currentArtwork = null
                    lastNotificationKey = null

                    if (foregroundStarted) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        foregroundStarted = false
                    }
                    notificationManager.cancel(NOTIFICATION_ID)

                    // The service exists only for an active playback session. Keeping an
                    // idle START_STICKY service alive lets some OEMs resurrect Aura's
                    // process later and can crash a subsequent launcher start.
                    stopSelf()
                    return@collectLatest
                }

                ensureArtwork(track)

                val durationMs = when {
                    state.durationMs > 0L -> state.durationMs
                    track.durationMs > 0L -> track.durationMs
                    else -> 0L
                }

                publishSystemState(
                    track = track,
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = durationMs,
                    artwork = currentArtwork
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> controller.skipToPrevious()
            ACTION_TOGGLE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.skipToNext()
        }

        // Do not let Android recreate this service after Aura's process is killed.
        // A fresh service is started again automatically when playback becomes active.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        artworkJob?.cancel()
        serviceScope.cancel()

        systemMediaSession.isActive = false
        systemMediaSession.release()

        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        notificationManager.cancel(NOTIFICATION_ID)

        super.onDestroy()
    }

    private fun publishSystemState(
        track: Track,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        artwork: Bitmap?
    ) {
        val boundedPosition = if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }

        updateMediaSession(
            track = track,
            isPlaying = isPlaying,
            positionMs = boundedPosition,
            durationMs = durationMs,
            artwork = artwork
        )

        // The MediaSession drives the live timeline, so the notification itself only
        // needs rebuilding when its visible controls/content changes.
        val notificationKey = buildString {
            append(track.id)
            append('|')
            append(isPlaying)
            append('|')
            append(track.title)
            append('|')
            append(track.artist)
            append('|')
            append(currentArtworkUrl)
            append('|')
            append(artwork != null)
        }

        if (!foregroundStarted || notificationKey != lastNotificationKey) {
            val notification = buildNotification(
                title = track.title,
                artist = track.artist,
                isPlaying = isPlaying,
                artwork = artwork
            )

            if (!foregroundStarted) {
                startForeground(NOTIFICATION_ID, notification)
                foregroundStarted = true
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            lastNotificationKey = notificationKey
        }
    }

    private fun updateMediaSession(
        track: Track,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        artwork: Bitmap?
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
                    if (isPlaying) 1f else 0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album ?: track.title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

        if (currentArtworkUrl.isNotBlank()) {
            metadata.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, currentArtworkUrl)
            metadata.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, currentArtworkUrl)
        }

        if (artwork != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
        }

        systemMediaSession.setMetadata(metadata.build())
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artwork: Bitmap?
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
            .setLargeIcon(artwork)
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

    private fun ensureArtwork(track: Track) {
        val wantedUrl = ArtworkUtils.getHighResArtworkUrl(track.artworkUrl)
        if (wantedUrl == currentArtworkUrl) return

        artworkJob?.cancel()
        currentArtworkUrl = wantedUrl
        currentArtwork = null
        lastNotificationKey = null

        if (wantedUrl.isBlank()) return

        artworkJob = serviceScope.launch(Dispatchers.IO) {
            val loaded = loadArtworkBitmap(wantedUrl)

            withContext(Dispatchers.Main.immediate) {
                if (wantedUrl != currentArtworkUrl) return@withContext

                currentArtwork = loaded

                val state = controller.playbackState.value
                val activeTrack = state.currentTrack
                if (activeTrack != null && ArtworkUtils.getHighResArtworkUrl(activeTrack.artworkUrl) == wantedUrl) {
                    val durationMs = when {
                        state.durationMs > 0L -> state.durationMs
                        activeTrack.durationMs > 0L -> activeTrack.durationMs
                        else -> 0L
                    }

                    publishSystemState(
                        track = activeTrack,
                        isPlaying = state.isPlaying,
                        positionMs = state.positionMs,
                        durationMs = durationMs,
                        artwork = loaded
                    )
                }
            }
        }
    }

    private fun loadArtworkBitmap(url: String): Bitmap? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            try {
                connection.inputStream.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream) ?: return null
                    scaleForNotification(decoded)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleForNotification(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_ARTWORK_SIDE_PX) return bitmap

        val scale = MAX_ARTWORK_SIDE_PX.toFloat() / maxSide.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, width, height, true)
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
        private const val MAX_ARTWORK_SIDE_PX = 512

        private const val ACTION_PREVIOUS = "com.example.aura.action.PREVIOUS"
        private const val ACTION_TOGGLE = "com.example.aura.action.TOGGLE"
        private const val ACTION_NEXT = "com.example.aura.action.NEXT"
    }
}
