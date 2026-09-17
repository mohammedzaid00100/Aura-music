package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val featuredTracks: StateFlow<Resource<List<Track>>> = musicRepository.getFeaturedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val trendingTracks: StateFlow<Resource<List<Track>>> = musicRepository.getTrendingTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val recommendedPlaylists: StateFlow<Resource<List<Playlist>>> = musicRepository.getRecommendedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val topArtists: StateFlow<Resource<List<Artist>>> = musicRepository.getTopArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val favoriteTracks: StateFlow<List<Track>> = musicRepository.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    fun selectMood(mood: String) {
        _selectedMood.value = if (_selectedMood.value == mood) null else mood
    }

    val playbackState = playbackController.playbackState

    fun playTrack(track: Track, queue: List<Track>) {
        playbackController.playTrack(track, queue)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    companion object {
        fun provideFactory(
            repository: MusicRepository,
            playbackController: PlaybackController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository, playbackController) as T
            }
        }
    }
}
