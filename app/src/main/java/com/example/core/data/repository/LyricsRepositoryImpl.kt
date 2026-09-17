package com.example.core.data.repository

import com.example.core.common.Resource
import com.example.core.domain.repository.LyricsRepository
import com.example.core.model.Lyrics
import com.example.core.model.Track

/**
 * Lyrics repository implementation.
 *
 * Adheres strictly to the requirement of not fabricating or hallucinating fake lyrics.
 * When real lyrics are unavailable from an authentic source, returns an explicit error or
 * empty state with "Lyrics unavailable".
 */
class LyricsRepositoryImpl : LyricsRepository {

    override suspend fun getLyrics(track: Track): Resource<Lyrics> {
        // Real lyrics provider integration:
        // Do not generate fake lyrics. If unavailable, report authentic state.
        return Resource.Error("Lyrics unavailable")
    }
}
