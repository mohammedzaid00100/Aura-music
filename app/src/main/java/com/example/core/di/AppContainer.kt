package com.example.core.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.core.download.DownloadRepository
import com.example.core.data.repository.LyricsRepositoryImpl
import com.example.core.data.repository.MusicRepositoryImpl
import com.example.core.domain.repository.LyricsRepository
import com.example.core.domain.repository.MusicRepository
import com.example.core.network.MusicSource
import com.example.core.network.innertube.InnertubeMusicSource
import com.example.core.recommendation.ListeningHistoryRepository
import com.example.core.recommendation.ListeningHistoryTracker
import com.example.core.recommendation.LocalRecommendationEngine
import com.example.playback.AuraPlaybackNotificationService
import com.example.playback.PlaybackController
import com.example.playback.PlaybackControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface AppContainer {
    val downloadRepository: DownloadRepository
    val musicSource: MusicSource
    val musicRepository: MusicRepository
    val lyricsRepository: LyricsRepository
    val listeningHistoryRepository: ListeningHistoryRepository
    val recommendationEngine: LocalRecommendationEngine
    val playbackController: PlaybackController
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val downloadRepository by lazy { DownloadRepository(context) }

    override val musicSource: MusicSource by lazy {
        InnertubeMusicSource()
    }

    override val musicRepository: MusicRepository by lazy {
        MusicRepositoryImpl(musicSource)
    }

    override val lyricsRepository: LyricsRepository by lazy {
        LyricsRepositoryImpl()
    }

    override val listeningHistoryRepository by lazy {
        ListeningHistoryRepository(context.applicationContext)
    }

    override val recommendationEngine by lazy {
        LocalRecommendationEngine(
            musicRepository = musicRepository,
            historyRepository = listeningHistoryRepository
        )
    }

    private val listeningHistoryTracker by lazy {
        ListeningHistoryTracker(
            repository = listeningHistoryRepository,
            scope = appScope
        )
    }

    override val playbackController: PlaybackController by lazy {
        PlaybackControllerImpl(context, downloadRepository = downloadRepository).also { controller ->
            listeningHistoryTracker.start(controller.playbackState)

            appScope.launch {
                controller.playbackState
                    .map { it.currentTrack != null }
                    .distinctUntilChanged()
                    .collect { hasActiveTrack ->
                        if (hasActiveTrack) {
                            ContextCompat.startForegroundService(
                                context.applicationContext,
                                Intent(context.applicationContext, AuraPlaybackNotificationService::class.java)
                            )
                        }
                    }
            }
        }
    }
}
