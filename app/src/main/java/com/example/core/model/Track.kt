package com.example.core.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val sourceId: String = "aura",
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false
)
