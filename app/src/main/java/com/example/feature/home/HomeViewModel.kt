package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.recommendation.ListeningHistoryRepository
import com.example.core.recommendation.LocalRecommendationEngine
import com.example.core.recommendation.TrackPreferenceRepository
import com.example.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val trackPreferenceRepository: TrackPreferenceRepository,
    private val recommendationEngine: LocalRecommendationEngine
) : ViewModel() {

    private val baseFeaturedTracks: StateFlow<Resource<List<Track>>> = musicRepository.getFeaturedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    private val baseFavoriteTracks: StateFlow<List<Track>> = musicRepository.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _personalizedTracks = MutableStateFlow<Resource<List<Track>>>(Resource.Idle)
    val personalizedTracks: StateFlow<Resource<List<Track>>> = _personalizedTracks.asStateFlow()

    private val _recommendationSeedTitle = MutableStateFlow<String?>(null)
    val recommendationSeedTitle: StateFlow<String?> = _recommendationSeedTitle.asStateFlow()

    val likedTracks: StateFlow<List<Track>> = trackPreferenceRepository.state
        .map { preferences -> preferences.liked.values.toList().asReversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<Track>> = combine(
        listeningHistoryRepository.stats,
        trackPreferenceRepository.state
    ) { stats, preferences ->
        stats.values
            .filterNot { preferences.disliked.containsKey(it.track.id) }
            .sortedByDescending { it.lastPlayedAt }
            .map { stat -> stat.track.copy(isFavorite = preferences.liked.containsKey(stat.track.id)) }
            .distinctBy { it.id }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredTracks: StateFlow<Resource<List<Track>>> = combine(
        baseFeaturedTracks,
        personalizedTracks,
        trackPreferenceRepository.state
    ) { fallback, personalized, preferences ->
        val chosen = when (personalized) {
            is Resource.Success -> if (personalized.data.isNotEmpty()) personalized else fallback
            else -> fallback
        }

        when (chosen) {
            is Resource.Success -> Resource.Success(
                chosen.data
                    .filterNot { preferences.disliked.containsKey(it.id) }
                    .map { it.copy(isFavorite = preferences.liked.containsKey(it.id)) }
            )
            else -> chosen
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val favoriteTracks: StateFlow<List<Track>> = combine(
        recentTracks,
        baseFavoriteTracks,
        trackPreferenceRepository.state
    ) { recent, favorites, preferences ->
        (recent + favorites)
            .filterNot { preferences.disliked.containsKey(it.id) }
            .map { it.copy(isFavorite = preferences.liked.containsKey(it.id)) }
            .distinctBy { it.id }
            .take(16)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingTracks: StateFlow<Resource<List<Track>>> = combine(
        musicRepository.getTrendingTracks(),
        trackPreferenceRepository.state
    ) { resource, preferences ->
        when (resource) {
            is Resource.Success -> Resource.Success(
                resource.data
                    .filterNot { preferences.disliked.containsKey(it.id) }
                    .map { it.copy(isFavorite = preferences.liked.containsKey(it.id)) }
            )
            else -> resource
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val recommendedPlaylists: StateFlow<Resource<List<Playlist>>> = musicRepository.getRecommendedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val topArtists: StateFlow<Resource<List<Artist>>> = musicRepository.getTopArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                listeningHistoryRepository.stats,
                trackPreferenceRepository.state
            ) { history, preferences -> history to preferences }
                .collectLatest { (history, preferences) ->
                    if (history.isEmpty() && preferences.liked.isEmpty()) {
                        _personalizedTracks.value = Resource.Idle
                        _recommendationSeedTitle.value = null
                        return@collectLatest
                    }

                    _personalizedTracks.value = Resource.Loading
                    try {
                        val result = recommendationEngine.build()
                        _recommendationSeedTitle.value = result.seedTitle
                        _personalizedTracks.value = if (result.tracks.isNotEmpty()) {
                            Resource.Success(result.tracks)
                        } else {
                            Resource.Idle
                        }
                    } catch (e: Exception) {
                        _personalizedTracks.value = Resource.Error(
                            message = e.localizedMessage ?: "Could not refresh recommendations",
                            throwable = e
                        )
                    }
                }
        }
    }

    fun selectMood(mood: String) {
        _selectedMood.value = if (_selectedMood.value == mood) null else mood
    }

    val playbackState = playbackController.playbackState

    fun playTrack(track: Track, queue: List<Track>) {
        playbackController.playTrack(track, queue)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { musicRepository.toggleFavorite(track) }
    }

    companion object {
        fun provideFactory(
            repository: MusicRepository,
            playbackController: PlaybackController,
            listeningHistoryRepository: ListeningHistoryRepository,
            trackPreferenceRepository: TrackPreferenceRepository,
            recommendationEngine: LocalRecommendationEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    musicRepository = repository,
                    playbackController = playbackController,
                    listeningHistoryRepository = listeningHistoryRepository,
                    trackPreferenceRepository = trackPreferenceRepository,
                    recommendationEngine = recommendationEngine
                ) as T
            }
        }
    }
}
