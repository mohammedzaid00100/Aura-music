package com.example.core.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val artworkUrl: String,
    val releaseYear: Int = 2024,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)
