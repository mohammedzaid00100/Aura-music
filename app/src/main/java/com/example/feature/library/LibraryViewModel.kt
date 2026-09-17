package com.example.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.download.DownloadRepository
import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
    val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val favoriteTracks: StateFlow<List<Track>> = musicRepository.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<Resource<List<Playlist>>> = musicRepository.getRecommendedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val artists = musicRepository.getTopArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    private val _isAscending = MutableStateFlow(false)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    fun toggleSort() {
        _isAscending.value = !_isAscending.value
    }

    val playbackState = playbackController.playbackState

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

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
            playbackController: PlaybackController,
            downloadRepository: DownloadRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(repository, playbackController, downloadRepository) as T
            }
        }
    }
}
