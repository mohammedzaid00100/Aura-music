package com.example.core.data.repository

import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Album
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.network.MusicSource
import com.example.core.recommendation.TrackPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class MusicRepositoryImpl(
    private val musicSource: MusicSource,
    private val preferenceRepository: TrackPreferenceRepository
) : MusicRepository {

    override fun getFeaturedTracks(): Flow<Resource<List<Track>>> = flow {
        emit(Resource.Loading)
        when (val result = musicSource.getFeaturedTracks()) {
            is Resource.Success -> {
                val prefs = preferenceRepository.state.value
                val updated = result.data
                    .filterNot { prefs.disliked.containsKey(it.id) }
                    .map { track -> track.copy(isFavorite = prefs.liked.containsKey(track.id)) }
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
                val prefs = preferenceRepository.state.value
                val updated = result.data
                    .filterNot { prefs.disliked.containsKey(it.id) }
                    .map { track -> track.copy(isFavorite = prefs.liked.containsKey(track.id)) }
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
            val liked = preferenceRepository.state.value.liked
            Resource.Success(result.data.map { it.copy(isFavorite = liked.containsKey(it.id)) })
        } else {
            result
        }
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return preferenceRepository.state.map { prefs ->
            prefs.liked.values.map { it.copy(isFavorite = true) }
        }
    }

    override suspend fun toggleFavorite(track: Track) {
        preferenceRepository.toggleLike(track)
    }

    override suspend fun isFavorite(trackId: String): Boolean {
        return preferenceRepository.isLiked(trackId)
    }

    override suspend fun getAlbum(albumId: String): Resource<Album> {
        return musicSource.getAlbum(albumId)
    }

    override suspend fun getArtist(artistId: String): Resource<Artist> {
        return musicSource.getArtist(artistId)
    }
}
