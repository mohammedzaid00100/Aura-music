package com.example.core.model

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String,
    val monthlyListeners: Long = 0,
    val bio: String? = null,
    val topTracks: List<Track> = emptyList()
)
