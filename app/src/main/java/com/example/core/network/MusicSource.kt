package com.example.core.network

import com.example.core.common.Resource
import com.example.core.model.Album
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track

interface MusicSource {
    val sourceId: String
    val sourceName: String

    suspend fun getFeaturedTracks(): Resource<List<Track>>
    suspend fun getTrendingTracks(): Resource<List<Track>>
    suspend fun getRecommendedPlaylists(): Resource<List<Playlist>>
    suspend fun getTopArtists(): Resource<List<Artist>>
    suspend fun search(query: String): Resource<List<Track>>
    suspend fun getStreamUrl(trackId: String): Resource<String>
    suspend fun getAlbum(albumId: String): Resource<Album>
    suspend fun getArtist(artistId: String): Resource<Artist>
}
