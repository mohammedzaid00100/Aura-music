package com.example.core.domain.repository

import com.example.core.common.Resource
import com.example.core.model.Lyrics
import com.example.core.model.Track

interface LyricsRepository {
    suspend fun getLyrics(track: Track): Resource<Lyrics>
}
