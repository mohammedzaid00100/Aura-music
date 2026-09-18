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

    val recentTracks: StateFlow<List<Track>> = listeningHistoryRepository.stats
        .map { stats ->
            stats.values
                .sortedByDescending { it.lastPlayedAt }
                .map { it.track }
                .distinctBy { it.id }
                .take(12)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listeningHistoryRepository.recentTracks())

    /**
     * HomeScreen already renders this flow as "Recommended For You". Once Aura has local
     * listening history we transparently replace generic featured results with the local
     * recommendation engine output. Cold-start users still get the existing featured list.
     */
    val featuredTracks: StateFlow<Resource<List<Track>>> = combine(
        baseFeaturedTracks,
        personalizedTracks
    ) { fallback, personalized ->
        when (personalized) {
            is Resource.Success -> if (personalized.data.isNotEmpty()) personalized else fallback
            else -> fallback
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    /** The existing KEEP LISTENING row now starts with real recent listening history. */
    val favoriteTracks: StateFlow<List<Track>> = combine(
        recentTracks,
        baseFavoriteTracks
    ) { recent, favorites ->
        (recent + favorites).distinctBy { it.id }.take(16)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listeningHistoryRepository.recentTracks())

    val trendingTracks: StateFlow<Resource<List<Track>>> = musicRepository.getTrendingTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val recommendedPlaylists: StateFlow<Resource<List<Playlist>>> = musicRepository.getRecommendedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    val topArtists: StateFlow<Resource<List<Artist>>> = musicRepository.getTopArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    init {
        viewModelScope.launch {
            listeningHistoryRepository.stats.collectLatest { stats ->
                if (stats.isEmpty()) {
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
                    // Keep Home usable with the original featured feed if recommendation refresh fails.
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
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    companion object {
        fun provideFactory(
            repository: MusicRepository,
            playbackController: PlaybackController,
            listeningHistoryRepository: ListeningHistoryRepository,
            recommendationEngine: LocalRecommendationEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    musicRepository = repository,
                    playbackController = playbackController,
                    listeningHistoryRepository = listeningHistoryRepository,
                    recommendationEngine = recommendationEngine
                ) as T
            }
        }
    }
}
