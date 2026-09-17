package com.example.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Track
import com.example.playback.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<Resource<List<Track>>>(Resource.Idle)
    val searchResults: StateFlow<Resource<List<Track>>> = _searchResults.asStateFlow()

    val playbackState = playbackController.playbackState

    private var searchJob: Job? = null

    val searchGenres = listOf("Synthwave", "Chillout", "Cyberpunk", "Lo-Fi", "Ambient", "Electro")

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _searchResults.value = Resource.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(300L) // Debounce
            _searchResults.value = Resource.Loading
            _searchResults.value = musicRepository.search(newQuery)
        }
    }

    fun retry() {
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                _searchResults.value = Resource.Loading
                _searchResults.value = musicRepository.search(currentQuery)
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track>) {
        playbackController.playTrack(track, queue)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
            // Refresh results favorite status if searching
            val current = _searchResults.value
            if (current is Resource.Success) {
                _searchResults.value = Resource.Success(
                    current.data.map {
                        if (it.id == track.id) it.copy(isFavorite = !it.isFavorite) else it
                    }
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: MusicRepository,
            playbackController: PlaybackController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(repository, playbackController) as T
            }
        }
    }
}
