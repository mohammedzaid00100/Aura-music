package com.example.core.recommendation

import com.example.core.model.PlaybackState
import com.example.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Converts the existing player state into useful local preference signals.
 * It deliberately ignores large seek jumps so dragging the timeline does not
 * pretend the skipped audio was listened to.
 */
class ListeningHistoryTracker(
    private val repository: ListeningHistoryRepository,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    private var activeTrack: Track? = null
    private var lastPositionMs = 0L
    private var durationMs = 0L
    private var listenedMs = 0L
    private var reachedEnd = false
    private var wasPlaying = false

    fun start(playbackState: StateFlow<PlaybackState>) {
        if (job != null) return

        job = scope.launch {
            playbackState.collect { state ->
                observe(state)
            }
        }
    }

    private suspend fun observe(state: PlaybackState) {
        val track = state.currentTrack

        if (track == null) {
            finishSession()
            return
        }

        val current = activeTrack
        if (current == null) {
            beginSession(track, state)
            return
        }

        if (current.id != track.id) {
            finishSession()
            beginSession(track, state)
            return
        }

        val effectiveDuration = when {
            state.durationMs > 0L -> state.durationMs
            durationMs > 0L -> durationMs
            track.durationMs > 0L -> track.durationMs
            else -> 0L
        }

        // A large backwards jump to the beginning while still on the same ID usually means
        // the song was restarted/replayed. Count it as a new play session.
        if (state.isPlaying && lastPositionMs > 8_000L && state.positionMs < 2_500L) {
            finishSession()
            beginSession(track, state)
            return
        }

        if (wasPlaying) {
            val delta = state.positionMs - lastPositionMs
            // Normal progress ticks are small. Large positive/negative jumps are seeks.
            if (delta in 1L..2_500L) {
                listenedMs += delta
            }
        }

        durationMs = effectiveDuration
        if (effectiveDuration > 0L && state.positionMs >= (effectiveDuration - 4_000L).coerceAtLeast(0L)) {
            reachedEnd = true
        }

        lastPositionMs = state.positionMs.coerceAtLeast(0L)
        wasPlaying = state.isPlaying
    }

    private suspend fun beginSession(track: Track, state: PlaybackState) {
        activeTrack = track
        lastPositionMs = state.positionMs.coerceAtLeast(0L)
        durationMs = when {
            state.durationMs > 0L -> state.durationMs
            track.durationMs > 0L -> track.durationMs
            else -> 0L
        }
        listenedMs = 0L
        reachedEnd = false
        wasPlaying = state.isPlaying
        repository.recordStart(track)
    }

    private suspend fun finishSession() {
        val track = activeTrack ?: return

        repository.recordSessionEnd(
            trackId = track.id,
            listenedMs = listenedMs,
            durationMs = durationMs,
            reachedEnd = reachedEnd
        )

        activeTrack = null
        lastPositionMs = 0L
        durationMs = 0L
        listenedMs = 0L
        reachedEnd = false
        wasPlaying = false
    }
}
