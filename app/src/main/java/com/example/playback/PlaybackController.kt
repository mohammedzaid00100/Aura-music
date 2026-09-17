package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.example.core.download.DownloadRepository
import com.example.core.model.PlaybackState
import com.example.core.model.RepeatMode
import com.example.core.model.Track
import com.example.core.network.saavn.SaavnMediaResolver
import com.example.core.util.ArtworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>

    fun playTrack(track: Track, queue: List<Track> = listOf(track))
    fun togglePlayPause()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun toggleShuffle()
    fun toggleRepeat()
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int)
    fun release()
}

class PlaybackControllerImpl(
    private val context: Context,
    private val mediaResolver: SaavnMediaResolver = SaavnMediaResolver(),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val downloadRepository: DownloadRepository? = null
) : PlaybackController {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressJob: Job? = null
    private var streamResolutionJob: Job? = null
    private var mediaSession: MediaSession? = null

    // Single source of truth ExoPlayer instance
    private val player: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpDataSourceFactory),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    // A queued HTTP item may have finished downloading after the queue was built.
                    // Resolve at the data source too, including automatic next/repeat/shuffle transitions.
                    val trackId = dataSpec.key ?: if (uri.scheme == "aura") uri.lastPathSegment else null
                    val local = trackId?.let { id -> runBlocking { downloadRepository?.awaitLocalUri(id) } }
                    if (local != null) return dataSpec.buildUpon().setUri(Uri.parse(local)).build()
                    if (uri.scheme == "aura") {
                        val title = uri.getQueryParameter("title").orEmpty()
                        val artist = uri.getQueryParameter("artist").orEmpty()
                        val streamUrl = uri.getQueryParameter("stream_url").orEmpty()
                        if (isPlayableAudioUri(streamUrl)) {
                            return dataSpec.buildUpon().setUri(Uri.parse(streamUrl)).build()
                        }
                        val resolved = runBlocking {
                            mediaResolver.resolveStreamUrl(title, artist)
                        }
                        if (!resolved.isNullOrBlank()) {
                            return dataSpec.buildUpon().setUri(Uri.parse(resolved)).build()
                        }
                    }
                    return dataSpec
                }
            }
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val hasWakeLockPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context.applicationContext,
            android.Manifest.permission.WAKE_LOCK
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val wakeMode = if (hasWakeLockPermission) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE

        val exo = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(wakeMode)
            .build()

        exo.addListener(playerListener)

        try {
            mediaSession = MediaSession.Builder(context.applicationContext, exo)
                .setId("AuraMusicSession")
                .build()
        } catch (e: Exception) {
            android.util.Log.w("PlaybackController", "Could not initialize MediaSession: ${e.message}")
        }

        exo
    }

    private val playerListener = object : Player.Listener {
        /**
         * The authoritative callback for track changes.
         * The UI ONLY updates currentTrack when ExoPlayer itself transitions to a new MediaItem.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) {
                _playbackState.update {
                    it.copy(
                        currentTrack = null,
                        queueIndex = -1,
                        durationMs = 0L,
                        positionMs = 0L,
                        isPlaying = false,
                        isBuffering = false
                    )
                }
                return
            }

            val track = mediaItem.toTrack()
            val currentIndex = player.currentMediaItemIndex
            val realDuration = if (player.duration > 0) player.duration else track.durationMs

            val actualQueue = (0 until player.mediaItemCount).map { idx ->
                player.getMediaItemAt(idx).toTrack()
            }

            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    queue = if (actualQueue.isNotEmpty()) actualQueue else it.queue,
                    queueIndex = currentIndex,
                    durationMs = realDuration,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    errorMessage = null
                )
            }

            // Proactively resolve stream for the next item in ExoPlayer queue for gapless transitions
            preloadNextStreamIfNeeded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressLoop()
            } else {
                progressJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playbackState.update { it.copy(isBuffering = true, errorMessage = null) }
                }
                Player.STATE_READY -> {
                    val realDuration = player.duration.coerceAtLeast(0L)
                    val activeTrack = player.currentMediaItem?.toTrack() ?: _playbackState.value.currentTrack
                    _playbackState.update {
                        it.copy(
                            currentTrack = activeTrack,
                            isBuffering = false,
                            durationMs = if (realDuration > 0) realDuration else it.durationMs,
                            errorMessage = null
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
                    progressJob?.cancel()
                    handleQueueEnded()
                }
                Player.STATE_IDLE -> {
                    _playbackState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMsg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed"
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Audio stream file not found"
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "Audio decoding failed"
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Cleartext stream not permitted"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Audio stream server returned error"
                else -> error.localizedMessage ?: "Playback error: ${error.errorCodeName}"
            }
            android.util.Log.e("PlaybackController", "ExoPlayer error: $errorMsg (code ${error.errorCode})", error)
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = errorMsg
                )
            }
            progressJob?.cancel()
        }
    }

    override fun playTrack(track: Track, queue: List<Track>) {
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(track)
        val targetIndex = currentQueue.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }

        streamResolutionJob?.cancel()
        progressJob?.cancel()

        // Show buffering without manually changing currentTrack (currentTrack is updated onMediaItemTransition)
        _playbackState.update {
            it.copy(
                isBuffering = true,
                errorMessage = null
            )
        }

        streamResolutionJob = coroutineScope.launch {
            try {
                // If the exact same playlist is already populated in ExoPlayer, just seek to targetIndex
                if (isSamePlaylistInPlayer(currentQueue)) {
                    ensurePlayableAndSeek(targetIndex)
                    return@launch
                }

                // Resolve target track stream first so playback begins immediately
                val playableStreamUrl = resolvePlayableStream(track)
                if (playableStreamUrl.isNullOrBlank()) {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playable audio stream unavailable for \"${track.title}\""
                        )
                    }
                    return@launch
                }

                // Build full MediaItems for all tracks in queue
                val mediaItems = currentQueue.mapIndexed { idx, item ->
                    if (idx == targetIndex) {
                        item.toMediaItem(playableStreamUrl)
                    } else if (isPlayableAudioUri(item.streamUrl)) {
                        item.toMediaItem(item.streamUrl)
                    } else {
                        item.toMediaItem(null)
                    }
                }

                player.stop()
                player.clearMediaItems()
                player.setMediaItems(mediaItems, targetIndex, 0L)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                android.util.Log.e("PlaybackController", "Error preparing media: ${e.message}", e)
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "Failed to play stream: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun isSamePlaylistInPlayer(queue: List<Track>): Boolean {
        if (player.mediaItemCount != queue.size || queue.isEmpty()) return false
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId != queue[i].id) {
                return false
            }
        }
        return true
    }

    private suspend fun ensurePlayableAndSeek(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        val mediaItem = player.getMediaItemAt(index)
        val uri = mediaItem.localConfiguration?.uri?.toString()

        if (uri.isNullOrBlank() || !isPlayableAudioUri(uri)) {
            val track = mediaItem.toTrack()
            val resolved = resolvePlayableStream(track)
            if (!resolved.isNullOrBlank()) {
                player.replaceMediaItem(index, track.toMediaItem(resolved))
            } else {
                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = "Playable stream unavailable for \"${track.title}\""
                    )
                }
                return
            }
        }

        player.seekToDefaultPosition(index)
        player.play()
    }

    private suspend fun resolvePlayableStream(track: Track): String? {
        downloadRepository?.awaitLocalUri(track.id)?.let { return it }
        if (isPlayableAudioUri(track.streamUrl)) {
            return track.streamUrl
        }

        val resolved = mediaResolver.resolveStreamUrl(track.title, track.artist)
        if (!resolved.isNullOrBlank()) {
            return resolved
        }

        return null
    }

    override fun togglePlayPause() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun play() {
        if (player.mediaItemCount == 0) return
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, _playbackState.value.durationMs)
        player.seekTo(bounded)
        _playbackState.update { it.copy(positionMs = bounded) }
    }

    override fun skipToNext() {
        val mediaItemCount = player.mediaItemCount
        if (mediaItemCount == 0) return

        val currentIndex = player.currentMediaItemIndex
        val nextIndex = if (player.shuffleModeEnabled) {
            val candidates = (0 until mediaItemCount).filter { it != currentIndex }
            if (candidates.isNotEmpty()) candidates.random() else currentIndex
        } else if (currentIndex + 1 < mediaItemCount) {
            currentIndex + 1
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            0
        } else {
            return
        }

        streamResolutionJob?.cancel()

        val nextMediaItem = player.getMediaItemAt(nextIndex)
        val uri = nextMediaItem.localConfiguration?.uri?.toString()

        if (!uri.isNullOrBlank() && isPlayableAudioUri(uri)) {
            player.seekToDefaultPosition(nextIndex)
            player.play()
        } else {
            _playbackState.update { it.copy(isBuffering = true) }
            streamResolutionJob = coroutineScope.launch {
                val track = nextMediaItem.toTrack()
                val resolvedUrl = resolvePlayableStream(track)
                if (!resolvedUrl.isNullOrBlank()) {
                    if (nextIndex < player.mediaItemCount && player.getMediaItemAt(nextIndex).mediaId == track.id) {
                        player.replaceMediaItem(nextIndex, track.toMediaItem(resolvedUrl))
                    }
                    player.seekToDefaultPosition(nextIndex)
                    player.play()
                } else {
                    _playbackState.update {
                        it.copy(
                            isBuffering = false,
                            errorMessage = "Playable audio stream unavailable for \"${track.title}\""
                        )
                    }
                }
            }
        }
    }

    override fun skipToPrevious() {
        val mediaItemCount = player.mediaItemCount
        if (mediaItemCount == 0) return

        if (player.currentPosition > 3000L) {
            seekTo(0L)
            play()
            return
        }

        val currentIndex = player.currentMediaItemIndex
        val prevIndex = if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            mediaItemCount - 1
        } else {
            0
        }

        streamResolutionJob?.cancel()

        val prevMediaItem = player.getMediaItemAt(prevIndex)
        val uri = prevMediaItem.localConfiguration?.uri?.toString()

        if (!uri.isNullOrBlank() && isPlayableAudioUri(uri)) {
            player.seekToDefaultPosition(prevIndex)
            player.play()
        } else {
            _playbackState.update { it.copy(isBuffering = true) }
            streamResolutionJob = coroutineScope.launch {
                val track = prevMediaItem.toTrack()
                val resolvedUrl = resolvePlayableStream(track)
                if (!resolvedUrl.isNullOrBlank()) {
                    if (prevIndex < player.mediaItemCount && player.getMediaItemAt(prevIndex).mediaId == track.id) {
                        player.replaceMediaItem(prevIndex, track.toMediaItem(resolvedUrl))
                    }
                    player.seekToDefaultPosition(prevIndex)
                    player.play()
                } else {
                    _playbackState.update {
                        it.copy(
                            isBuffering = false,
                            errorMessage = "Playable audio stream unavailable for \"${track.title}\""
                        )
                    }
                }
            }
        }
    }

    override fun toggleShuffle() {
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(shuffleMode = newShuffle) }
    }

    override fun toggleRepeat() {
        val (nextMode, exoMode) = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL to Player.REPEAT_MODE_ALL
            RepeatMode.ALL -> RepeatMode.ONE to Player.REPEAT_MODE_ONE
            RepeatMode.ONE -> RepeatMode.OFF to Player.REPEAT_MODE_OFF
        }
        player.repeatMode = exoMode
        _playbackState.update { it.copy(repeatMode = nextMode) }
    }

    override fun addToQueue(track: Track) {
        val mediaItem = track.toMediaItem()
        player.addMediaItem(mediaItem)
        val updatedQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
        _playbackState.update { it.copy(queue = updatedQueue) }

        if (!isPlayableAudioUri(track.streamUrl)) {
            coroutineScope.launch {
                val resolved = resolvePlayableStream(track)
                if (!resolved.isNullOrBlank()) {
                    val index = player.mediaItemCount - 1
                    if (index >= 0 && player.getMediaItemAt(index).mediaId == track.id) {
                        player.replaceMediaItem(index, track.toMediaItem(resolved))
                    }
                }
            }
        }
    }

    override fun removeFromQueue(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            val updatedQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
            val currentIdx = player.currentMediaItemIndex
            _playbackState.update {
                it.copy(queue = updatedQueue, queueIndex = currentIdx)
            }
        }
    }

    override fun release() {
        progressJob?.cancel()
        streamResolutionJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player.removeListener(playerListener)
        player.release()
    }

    private fun preloadNextStreamIfNeeded() {
        val count = player.mediaItemCount
        if (count <= 1) return

        val currentIndex = player.currentMediaItemIndex
        val nextIndex = if (currentIndex + 1 < count) {
            currentIndex + 1
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            0
        } else {
            return
        }

        val nextMediaItem = player.getMediaItemAt(nextIndex)
        val uri = nextMediaItem.localConfiguration?.uri?.toString()
        if (uri.isNullOrBlank() || !isPlayableAudioUri(uri)) {
            coroutineScope.launch {
                val track = nextMediaItem.toTrack()
                val resolvedUrl = resolvePlayableStream(track)
                if (!resolvedUrl.isNullOrBlank()) {
                    if (nextIndex < player.mediaItemCount && player.getMediaItemAt(nextIndex).mediaId == track.id) {
                        player.replaceMediaItem(nextIndex, track.toMediaItem(resolvedUrl))
                    }
                }
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && player.isPlaying) {
                val currentPos = player.currentPosition.coerceAtLeast(0L)
                val realDuration = player.duration.let { if (it > 0) it else _playbackState.value.durationMs }
                _playbackState.update {
                    it.copy(
                        positionMs = currentPos,
                        durationMs = realDuration,
                        isPlaying = player.isPlaying
                    )
                }
                delay(250L)
            }
        }
    }

    private fun handleQueueEnded() {
        if (player.repeatMode == Player.REPEAT_MODE_OFF) {
            seekTo(0L)
            _playbackState.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }
}

fun isPlayableAudioUri(url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme == "file") return uri.path?.let { java.io.File(it).isFile } == true
    if (uri.scheme == "content") return true
    return isDirectAudioUrl(url)
}

fun isDirectAudioUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            (lower.contains(".mp4") || lower.contains(".mp3") || lower.contains(".m4a") ||
                    lower.contains(".aac") || lower.contains(".opus") || lower.contains("saavncdn.com")) &&
            !lower.contains("youtube.com/watch")
}

/**
 * Serialization extension: Converts Track domain model to Media3 MediaItem with complete metadata.
 */
fun Track.toMediaItem(playableUri: String? = null): MediaItem {
    val effectiveUri: String = when {
        !playableUri.isNullOrBlank() -> playableUri
        isPlayableAudioUri(streamUrl) -> streamUrl
        else -> "aura://track/${Uri.encode(id)}?title=${Uri.encode(title)}&artist=${Uri.encode(artist)}&stream_url=${Uri.encode(streamUrl)}"
    }
    val highResArt = ArtworkUtils.getHighResArtworkUrl(artworkUrl)
    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album ?: title)
        .setArtworkUri(if (highResArt.isNotBlank()) Uri.parse(highResArt) else null)
        .setExtras(Bundle().apply {
            putString("track_id", id)
            putString("title", title)
            putString("artist", artist)
            artistId?.let { putString("artist_id", it) }
            album?.let { putString("album", it) }
            albumId?.let { putString("album_id", it) }
            putLong("duration_ms", durationMs)
            putString("artwork_url", artworkUrl)
            putString("stream_url", streamUrl)
            putString("source_id", sourceId)
            putBoolean("is_favorite", isFavorite)
            putBoolean("is_downloaded", isDownloaded)
        })
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setCustomCacheKey(id)
        .setMediaMetadata(mediaMetadata)
        .setUri(Uri.parse(effectiveUri))
        .build()
}

/**
 * Deserialization extension: Reconstructs Track domain model from Media3 MediaItem.
 */
fun MediaItem.toTrack(): Track {
    val extras = mediaMetadata.extras
    val trackId = mediaId.ifBlank { extras?.getString("track_id") ?: "" }
    val title = mediaMetadata.title?.toString() ?: extras?.getString("title") ?: ""
    val artist = mediaMetadata.artist?.toString() ?: extras?.getString("artist") ?: ""
    val artistId = extras?.getString("artist_id")
    val album = mediaMetadata.albumTitle?.toString() ?: extras?.getString("album") ?: title
    val albumId = extras?.getString("album_id")
    val durationMs = extras?.getLong("duration_ms", 0L) ?: 0L
    val artworkUrl = extras?.getString("artwork_url") ?: mediaMetadata.artworkUri?.toString() ?: ""
    val rawUri = localConfiguration?.uri?.toString() ?: extras?.getString("stream_url") ?: ""
    val streamUrl = if (rawUri.startsWith("aura://")) {
        localConfiguration?.uri?.getQueryParameter("stream_url")?.takeIf { it.isNotBlank() }
            ?: extras?.getString("stream_url")
            ?: ""
    } else {
        rawUri
    }
    val sourceId = extras?.getString("source_id") ?: "aura"
    val isFavorite = extras?.getBoolean("is_favorite", false) ?: false
    val isDownloaded = extras?.getBoolean("is_downloaded", false) ?: false

    return Track(
        id = trackId,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        sourceId = sourceId,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded
    )
}

