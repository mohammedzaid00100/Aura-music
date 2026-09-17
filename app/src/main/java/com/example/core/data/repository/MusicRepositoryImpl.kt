package com.example.core.data.repository

import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Album
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.network.MusicSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class MusicRepositoryImpl(
    private val musicSource: MusicSource
) : MusicRepository {

    private val favoritesMap = MutableStateFlow<Map<String, Track>>(emptyMap())

    override fun getFeaturedTracks(): Flow<Resource<List<Track>>> = flow {
        emit(Resource.Loading)
        when (val result = musicSource.getFeaturedTracks()) {
            is Resource.Success -> {
                val currentFavorites = favoritesMap.value
                val updated = result.data.map { track ->
                    track.copy(isFavorite = currentFavorites.containsKey(track.id))
                }
                emit(Resource.Success(updated))
            }
            is Resource.Error -> emit(result)
            else -> emit(Resource.Idle)
        }
    }

    override fun getTrendingTracks(): Flow<Resource<List<Track>>> = flow {
        emit(Resource.Loading)
        when (val result = musicSource.getTrendingTracks()) {
            is Resource.Success -> {
                val currentFavorites = favoritesMap.value
                val updated = result.data.map { track ->
                    track.copy(isFavorite = currentFavorites.containsKey(track.id))
                }
                emit(Resource.Success(updated))
            }
            is Resource.Error -> emit(result)
            else -> emit(Resource.Idle)
        }
    }

    override fun getRecommendedPlaylists(): Flow<Resource<List<Playlist>>> = flow {
        emit(Resource.Loading)
        emit(musicSource.getRecommendedPlaylists())
    }

    override fun getTopArtists(): Flow<Resource<List<Artist>>> = flow {
        emit(Resource.Loading)
        emit(musicSource.getTopArtists())
    }

    override suspend fun search(query: String): Resource<List<Track>> {
        val result = musicSource.search(query)
        return if (result is Resource.Success) {
            val currentFavorites = favoritesMap.value
            Resource.Success(result.data.map { it.copy(isFavorite = currentFavorites.containsKey(it.id)) })
        } else {
            result
        }
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return favoritesMap.asStateFlow().map { it.values.toList() }
    }

    override suspend fun toggleFavorite(track: Track) {
        val current = favoritesMap.value.toMutableMap()
        if (current.containsKey(track.id)) {
            current.remove(track.id)
        } else {
            current[track.id] = track.copy(isFavorite = true)
        }
        favoritesMap.value = current
    }

    override suspend fun isFavorite(trackId: String): Boolean {
        return favoritesMap.value.containsKey(trackId)
    }

    override suspend fun getAlbum(albumId: String): Resource<Album> {
        return musicSource.getAlbum(albumId)
    }

    override suspend fun getArtist(artistId: String): Resource<Artist> {
        return musicSource.getArtist(artistId)
    }
}
