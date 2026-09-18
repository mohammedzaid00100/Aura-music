package com.example.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.Resource
import com.example.core.design.TrackItem
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import java.util.Calendar

private val MoodChips = listOf("Chill", "Focus", "Workout", "Party", "Sleep", "Romance", "Feel good")

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val featuredResource by viewModel.featuredTracks.collectAsStateWithLifecycle()
    val personalizedResource by viewModel.personalizedTracks.collectAsStateWithLifecycle()
    val trendingResource by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val playlistsResource by viewModel.recommendedPlaylists.collectAsStateWithLifecycle()
    val artistsResource by viewModel.topArtists.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val likedTracks by viewModel.likedTracks.collectAsStateWithLifecycle()
    val seedTitle by viewModel.recommendationSeedTitle.collectAsStateWithLifecycle()
    val selectedMood by viewModel.selectedMood.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    val featured = (featuredResource as? Resource.Success<List<Track>>)?.data.orEmpty()
    val personalized = (personalizedResource as? Resource.Success<List<Track>>)?.data.orEmpty()

    val recentIds = recentTracks.mapTo(mutableSetOf()) { it.id }
    val madeForYou = personalized.filterNot { it.id in recentIds }.take(10)
    val madeIds = madeForYou.mapTo(mutableSetOf()) { it.id }
    val favorites = likedTracks.filterNot { it.id in recentIds || it.id in madeIds }.take(10)
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val recommended = featured
        .filterNot { it.id in recentIds || it.id in madeIds || it.id in favoriteIds }
        .take(12)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            HomeHeader(onSettingsClick = onNavigateToSettings)
        }

        item {
            QuickAccessGrid(
                likedCount = likedTracks.size,
                recentCount = recentTracks.size,
                onLibraryClick = { onNavigateToLibrary?.invoke() }
            )
        }

        item {
            MoodChipsRow(
                selectedMood = selectedMood,
                onSelectMood = viewModel::selectMood
            )
            Spacer(modifier = Modifier.height(22.dp))
        }

        if (recentTracks.isNotEmpty()) {
            item {
                MusicSection(
                    title = "Recently played",
                    subtitle = "Jump back into your latest listens",
                    tracks = recentTracks,
                    currentTrackId = playbackState.currentTrack?.id,
                    isPlaying = playbackState.isPlaying,
                    onPlay = { track, queue -> viewModel.playTrack(track, queue) }
                )
            }
        }

        if (madeForYou.isNotEmpty()) {
            item {
                MusicSection(
                    title = "Made for you",
                    subtitle = "Shaped by your listening, likes and skips",
                    tracks = madeForYou,
                    currentTrackId = playbackState.currentTrack?.id,
                    isPlaying = playbackState.isPlaying,
                    onPlay = { track, queue -> viewModel.playTrack(track, queue) }
                )
            }
        }

        if (!seedTitle.isNullOrBlank() && personalized.isNotEmpty()) {
            val becauseTracks = personalized
                .filterNot { it.id in recentIds || it.id in madeIds }
                .drop(2)
                .take(8)
            if (becauseTracks.isNotEmpty()) {
                item {
                    MusicSection(
                        title = "Because you listened to $seedTitle",
                        subtitle = "More from the sound you keep coming back to",
                        tracks = becauseTracks,
                        currentTrackId = playbackState.currentTrack?.id,
                        isPlaying = playbackState.isPlaying,
                        onPlay = { track, queue -> viewModel.playTrack(track, queue) }
                    )
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                MusicSection(
                    title = "Your favorites",
                    subtitle = "Songs you explicitly liked",
                    tracks = favorites,
                    currentTrackId = playbackState.currentTrack?.id,
                    isPlaying = playbackState.isPlaying,
                    onPlay = { track, queue -> viewModel.playTrack(track, queue) }
                )
            }
        }

        item {
            when {
                recommended.isNotEmpty() -> MusicSection(
                    title = "Recommended for you",
                    subtitle = "Fresh picks without repeating everything above",
                    tracks = recommended,
                    currentTrackId = playbackState.currentTrack?.id,
                    isPlaying = playbackState.isPlaying,
                    onPlay = { track, queue -> viewModel.playTrack(track, queue) }
                )
                featuredResource is Resource.Loading -> LoadingSection()
            }
        }

        item {
            SectionHeading("Trending now", "Popular tracks worth a listen")
        }
        when (val state = trendingResource) {
            is Resource.Success -> {
                items(state.data.take(6), key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isSelected = playbackState.currentTrack?.id == track.id,
                        isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                        onClick = { viewModel.playTrack(track, state.data) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            }
            is Resource.Loading -> item { LoadingSection() }
            is Resource.Error -> item { ErrorSection(state.message) }
            else -> Unit
        }

        item {
            when (val state = artistsResource) {
                is Resource.Success -> {
                    Spacer(modifier = Modifier.height(22.dp))
                    SectionHeading("Popular artists", "Creators in rotation")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(state.data, key = { it.id }) { artist -> ArtistCard(artist) }
                    }
                }
                else -> Unit
            }
        }

        item {
            when (val state = playlistsResource) {
                is Resource.Success -> {
                    Spacer(modifier = Modifier.height(26.dp))
                    SectionHeading("Curated playlists", "Ready-made listening for the moment")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.data, key = { it.id }) { playlist ->
                            PlaylistCard(playlist) {
                                if (playlist.tracks.isNotEmpty()) {
                                    viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun HomeHeader(onSettingsClick: (() -> Unit)?) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 10.dp, top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "Aura Music",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Aura Music",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onSettingsClick != null) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    likedCount: Int,
    recentCount: Int,
    onLibraryClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessCard(
                title = "Liked Songs",
                subtitle = "$likedCount liked",
                icon = Icons.Default.Favorite,
                modifier = Modifier.weight(1f),
                onClick = onLibraryClick
            )
            QuickAccessCard(
                title = "Downloads",
                subtitle = "Offline library",
                icon = Icons.Default.Download,
                modifier = Modifier.weight(1f),
                onClick = onLibraryClick
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessCard(
                title = "Recently Played",
                subtitle = "$recentCount recent",
                icon = Icons.Default.History,
                modifier = Modifier.weight(1f),
                onClick = onLibraryClick
            )
            QuickAccessCard(
                title = "Made for You",
                subtitle = "Your Aura mix",
                icon = Icons.Default.Waves,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MoodChipsRow(selectedMood: String?, onSelectMood: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MoodChips.forEach { mood ->
            val selected = mood == selectedMood
            val background by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(180),
                label = "mood_background"
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(180),
                label = "mood_foreground"
            )
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelectMood(mood) },
                shape = CircleShape,
                color = background
            ) {
                Text(
                    text = mood,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MusicSection(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (Track, List<Track>) -> Unit
) {
    if (tracks.isEmpty()) return
    Column {
        SectionHeading(title, subtitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                MusicCard(
                    track = track,
                    isPlaying = currentTrackId == track.id && isPlaying,
                    onClick = { onPlay(track, tracks) }
                )
            }
        }
        Spacer(modifier = Modifier.height(26.dp))
    }
}

@Composable
private fun MusicCard(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick)
            .testTag("home_track_${track.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistCard(artist: Artist) {
    Column(
        modifier = Modifier.width(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.artworkUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(166.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
}

@Composable
private fun ErrorSection(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    )
}
