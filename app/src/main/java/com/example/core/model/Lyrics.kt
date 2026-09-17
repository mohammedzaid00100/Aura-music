package com.example.core.model

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class Lyrics(
    val trackId: String,
    val isSynced: Boolean = false,
    val plainLyrics: String = "",
    val lines: List<LyricsLine> = emptyList()
)
