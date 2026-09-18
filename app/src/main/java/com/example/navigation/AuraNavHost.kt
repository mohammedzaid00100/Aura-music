package com.example.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.common.Resource
import com.example.core.di.AppContainer
import com.example.core.model.Lyrics
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryViewModel
import com.example.feature.lyrics.LyricsOverlay
import com.example.feature.player.FullPlayerSheet
import com.example.feature.player.MiniPlayer
import com.example.feature.player.QueueSheet
import com.example.feature.search.SearchScreen
import com.example.feature.search.SearchViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun AuraApp(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val playbackState by appContainer.playbackController.playbackState.collectAsStateWithLifecycle()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isLyricsOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    var currentLyrics by remember { mutableStateOf<Lyrics?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                AnimatedVisibility(
                    visible = playbackState.currentTrack != null && !isPlayerExpanded,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onTogglePlayPause = { appContainer.playbackController.togglePlayPause() },
                        onSkipNext = { appContainer.playbackController.skipToNext() },
                        onClick = { isPlayerExpanded = true }
                    )
                }

                FloatingNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.provideFactory(
                            appContainer.musicRepository,
                            appContainer.playbackController,
                            appContainer.listeningHistoryRepository,
                            appContainer.recommendationEngine
                        )
                    )
                    HomeScreen(viewModel = homeViewModel)
                }

                composable(Screen.Search.route) {
                    val searchViewModel: SearchViewModel = viewModel(
                        factory = SearchViewModel.provideFactory(
                            appContainer.musicRepository,
                            appContainer.playbackController
                        )
                    )
                    SearchScreen(viewModel = searchViewModel)
                }

                composable(Screen.Library.route) {
                    val libraryViewModel: LibraryViewModel = viewModel(
                        factory = LibraryViewModel.provideFactory(
                            appContainer.musicRepository,
                            appContainer.playbackController,
                            appContainer.downloadRepository
                        )
                    )
                    LibraryScreen(viewModel = libraryViewModel)
                }

                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.provideFactory()
                    )
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isPlayerExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        FullPlayerSheet(
            playbackState = playbackState,
            onCollapse = { isPlayerExpanded = false },
            onTogglePlayPause = { appContainer.playbackController.togglePlayPause() },
            onSkipNext = { appContainer.playbackController.skipToNext() },
            onSkipPrevious = { appContainer.playbackController.skipToPrevious() },
            onSeekTo = { appContainer.playbackController.seekTo(it) },
            onToggleShuffle = { appContainer.playbackController.toggleShuffle() },
            onToggleRepeat = { appContainer.playbackController.toggleRepeat() },
            onToggleFavorite = {
                playbackState.currentTrack?.let { track ->
                    scope.launch { appContainer.musicRepository.toggleFavorite(track) }
                }
            },
            onOpenLyrics = {
                playbackState.currentTrack?.let { track ->
                    scope.launch {
                        when (val res = appContainer.lyricsRepository.getLyrics(track)) {
                            is Resource.Success -> currentLyrics = res.data
                            else -> currentLyrics = null
                        }
                        isLyricsOpen = true
                    }
                }
            },
            onOpenQueue = { isQueueOpen = true }
        )
    }

    AnimatedVisibility(
        visible = isLyricsOpen && playbackState.currentTrack != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        playbackState.currentTrack?.let { track ->
            LyricsOverlay(
                track = track,
                lyrics = currentLyrics,
                currentPositionMs = playbackState.positionMs,
                onSeekTo = { appContainer.playbackController.seekTo(it) },
                onClose = { isLyricsOpen = false }
            )
        }
    }

    AnimatedVisibility(
        visible = isQueueOpen,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        QueueSheet(
            playbackState = playbackState,
            onTrackSelect = { track ->
                appContainer.playbackController.playTrack(track, playbackState.queue)
            },
            onRemoveFromQueue = { index ->
                appContainer.playbackController.removeFromQueue(index)
            },
            onToggleFavorite = { track ->
                scope.launch { appContainer.musicRepository.toggleFavorite(track) }
            },
            onClose = { isQueueOpen = false }
        )
    }
}
