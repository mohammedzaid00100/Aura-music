package com.example.core.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val isCustom: Boolean = true
)
