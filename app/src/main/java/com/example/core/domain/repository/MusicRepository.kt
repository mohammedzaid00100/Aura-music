package com.example.core.domain.repository

import com.example.core.common.Resource
import com.example.core.model.Album
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getFeaturedTracks(): Flow<Resource<List<Track>>>
    fun getTrendingTracks(): Flow<Resource<List<Track>>>
    fun getRecommendedPlaylists(): Flow<Resource<List<Playlist>>>
    fun getTopArtists(): Flow<Resource<List<Artist>>>
    suspend fun search(query: String): Resource<List<Track>>
    fun getFavoriteTracks(): Flow<List<Track>>
    suspend fun toggleFavorite(track: Track)
    suspend fun isFavorite(trackId: String): Boolean
    suspend fun getAlbum(albumId: String): Resource<Album>
    suspend fun getArtist(artistId: String): Resource<Artist>
}
