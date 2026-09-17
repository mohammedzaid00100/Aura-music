package com.example.core.di

import android.content.Context
import com.example.core.download.DownloadRepository
import com.example.core.data.repository.LyricsRepositoryImpl
import com.example.core.data.repository.MusicRepositoryImpl
import com.example.core.domain.repository.LyricsRepository
import com.example.core.domain.repository.MusicRepository
import com.example.core.network.MusicSource
import com.example.core.network.innertube.InnertubeMusicSource
import com.example.playback.PlaybackController
import com.example.playback.PlaybackControllerImpl

interface AppContainer {
    val downloadRepository: DownloadRepository
    val musicSource: MusicSource
    val musicRepository: MusicRepository
    val lyricsRepository: LyricsRepository
    val playbackController: PlaybackController
}

class DefaultAppContainer(private val context: Context) : AppContainer {
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

    override val playbackController: PlaybackController by lazy {
        PlaybackControllerImpl(context, downloadRepository = downloadRepository)
    }
}
