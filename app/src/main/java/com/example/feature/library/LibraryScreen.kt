package com.example.feature.library

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.Resource
import com.example.core.design.TrackItem
import com.example.core.download.DownloadStatus
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track

private val LibraryTabs = listOf("Playlists", "Liked", "Downloaded", "Recent", "Albums", "Artists")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedTabIndex by viewModel.selectedTab.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val playlistsResource by viewModel.playlists.collectAsStateWithLifecycle()
    val artistsResource by viewModel.artists.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadRepository.downloads.collectAsStateWithLifecycle()

    val downloadEntries = downloads.values.sortedByDescending { it.addedAt }
    val downloadedCount = downloads.values.count { it.status == DownloadStatus.COMPLETE }

    // Snapshot the selected tab for this composition. LazyColumn builds its item list later,
    // so it must not observe a newer tab value paired with an older nullable Downloaded state.
    val activeTab = selectedTabIndex
    val downloadedState = if (activeTab == 2) rememberDownloadedLibraryState(viewModel) else null
    downloadedState?.let { DownloadedPlaylistDialogs(it) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            LibraryHeader(
                onSearchClick = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings
            )
        }

        item {
            LibrarySummary(
                likedCount = favoriteTracks.size,
                downloadedCount = downloadedCount,
                recentCount = recentTracks.size
            )
        }

        item {
            LibraryFilterChips(
                tabs = LibraryTabs,
                selectedIndex = activeTab,
                onSelectTab = viewModel::setTab
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SortingRow(
                isAscending = isAscending,
                onToggleSort = viewModel::toggleSort
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (activeTab) {
            0 -> {
                item { SectionHeader("Playlists") }
                when (val state = playlistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> {
                        val list = if (isAscending) state.data.reversed() else state.data
                        items(list, key = { it.id }) { playlist ->
                            PlaylistRowItem(playlist) {
                                if (playlist.tracks.isNotEmpty()) {
                                    viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                }
                            }
                        }
                    }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
            1 -> {
                item { SectionHeader("Liked Songs (${favoriteTracks.size})") }
                if (favoriteTracks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            message = "Your liked songs will appear here",
                            subtitle = "Use the Like button in the player to shape Aura around your taste."
                        )
                    }
                } else {
                    val list = if (isAscending) favoriteTracks.reversed() else favoriteTracks
                    items(list, key = { it.id }) { track ->
                        TrackItem(
                            track = track.copy(isFavorite = true),
                            isSelected = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                            onClick = { viewModel.playTrack(track, list) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            2 -> {
                downloadedState?.let { state ->
                    downloadedContent(
                        state = state,
                        entries = downloadEntries,
                        isAscending = isAscending,
                        playback = playbackState,
                        viewModel = viewModel
                    )
                }
            }
            3 -> {
                item { SectionHeader("Recently Played") }
                if (recentTracks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            message = "Nothing played yet",
                            subtitle = "Your latest listening history will show up here."
                        )
                    }
                } else {
                    val list = if (isAscending) recentTracks.reversed() else recentTracks
                    items(list, key = { it.id }) { track ->
                        TrackItem(
                            track = track,
                            isSelected = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                            onClick = { viewModel.playTrack(track, list) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            4 -> {
                item { SectionHeader("Albums") }
                when (val state = playlistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> {
                        items(state.data, key = { "album_${it.id}" }) { playlist ->
                            AlbumRowItem(
                                title = playlist.name,
                                artist = "Various Artists",
                                coverUrl = playlist.coverUrl,
                                count = "${playlist.trackCount} tracks"
                            ) {
                                if (playlist.tracks.isNotEmpty()) {
                                    viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                }
                            }
                        }
                    }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
            5 -> {
                item { SectionHeader("Artists") }
                when (val state = artistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> items(state.data, key = { it.id }) { ArtistRowItem(it) }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    onSearchClick: (() -> Unit)?,
    onSettingsClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row {
            if (onSearchClick != null) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, "Search library", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun LibrarySummary(likedCount: Int, downloadedCount: Int, recentCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard("Liked", likedCount.toString(), Icons.Default.Favorite, Modifier.weight(1f))
        SummaryCard("Offline", downloadedCount.toString(), Icons.Default.DownloadDone, Modifier.weight(1f))
        SummaryCard("Recent", recentCount.toString(), Icons.Default.Folder, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(
    label: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryFilterChips(
    tabs: List<String>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            val background by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(180),
                label = "library_chip_bg"
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(180),
                label = "library_chip_fg"
            )
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelectTab(index) }
                    .testTag("library_tab_$index"),
                shape = CircleShape,
                color = background
            ) {
                Text(
                    text = title,
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
private fun SortingRow(isAscending: Boolean, onToggleSort: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleSort)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = if (isAscending) "Oldest first" else "Recently added",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun PlaylistRowItem(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist • ${playlist.trackCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumRowItem(
    title: String,
    artist: String,
    coverUrl: String,
    count: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$artist • $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistRowItem(artist: Artist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.artworkUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(message: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
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
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    )
}
