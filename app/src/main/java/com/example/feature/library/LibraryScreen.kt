package com.example.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.download.DownloadStatus
import com.example.core.common.Resource
import com.example.core.design.TrackItem
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track

private val LibraryTabs = listOf("Playlists", "Songs", "Albums", "Artists")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedTabIndex by viewModel.selectedTab.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadRepository.downloads.collectAsStateWithLifecycle()
    val downloadedTracks = downloads.values.filter { it.status == DownloadStatus.COMPLETE }
        .sortedByDescending { it.addedAt }.map { it.track.copy(isDownloaded = true) }
    val downloadEntries = downloads.values.sortedByDescending { it.addedAt }

    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val playlistsResource by viewModel.playlists.collectAsStateWithLifecycle()
    val artistsResource by viewModel.artists.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    var activeShortcutSelection by remember { mutableStateOf<String?>(null) }
    // LazyColumn builds its item list later. Capture one selection for this composition
    // so it cannot observe a new shortcut with the previous (possibly null) playlist state.
    val activeShortcut = activeShortcutSelection
    val downloadedState = if (activeShortcut == "Downloaded") rememberDownloadedLibraryState(viewModel) else null
    downloadedState?.let { DownloadedPlaylistDialogs(it) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // 1. Library Top Header with Action Icons
        item {
            LibraryHeader(
                onSearchClick = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings
            )
        }

        // 2. Filter Tabs / Chips (Playlists, Songs, Albums, Artists)
        item {
            LibraryFilterChips(
                tabs = LibraryTabs,
                selectedIndex = selectedTabIndex,
                onSelectTab = { index ->
                    activeShortcutSelection = null
                    viewModel.setTab(index)
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. Sorting Row: Date added • Ascending/Descending
        item {
            SortingRow(
                isAscending = isAscending,
                onToggleSort = { viewModel.toggleSort() }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 4. Large Rounded Shortcut Cards
        item {
            ShortcutCardsSection(
                likedCount = favoriteTracks.size,
                downloadedCount = downloadedTracks.size,
                activeShortcut = activeShortcut,
                onShortcutClick = { shortcut ->
                    activeShortcutSelection = if (activeShortcutSelection == shortcut) null else shortcut
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 5. Main Tab Content
        when {
            activeShortcut == "Downloaded" -> {
                downloadedContent(
                    state = requireNotNull(downloadedState),
                    entries = downloadEntries,
                    isAscending = isAscending,
                    playback = playbackState,
                    viewModel = viewModel
                )
            }
            activeShortcut == "Liked" -> {
                item {
                    SectionHeader(title = "Liked Songs (${favoriteTracks.size})")
                }
                if (favoriteTracks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            message = "No liked tracks yet",
                            subtitle = "Tap the heart on songs to save them to your library"
                        )
                    }
                } else {
                    items(favoriteTracks) { track ->
                        TrackItem(
                            track = track,
                            isSelected = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                            onClick = { viewModel.playTrack(track, favoriteTracks) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            selectedTabIndex == 0 -> {
                // Playlists Tab
                item {
                    SectionHeader(title = "Playlists")
                }
                when (val state = playlistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> {
                        val sortedPlaylists = if (isAscending) state.data.reversed() else state.data
                        items(sortedPlaylists) { playlist ->
                            PlaylistRowItem(
                                playlist = playlist,
                                onClick = {
                                    if (playlist.tracks.isNotEmpty()) {
                                        viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                    }
                                }
                            )
                        }
                    }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
            selectedTabIndex == 1 -> {
                // Songs Tab
                item {
                    SectionHeader(title = "All Songs (${favoriteTracks.size})")
                }
                if (favoriteTracks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            message = "No songs in library",
                            subtitle = "Explore and add tracks to your library collection"
                        )
                    }
                } else {
                    val sortedTracks = if (isAscending) favoriteTracks.reversed() else favoriteTracks
                    items(sortedTracks) { track ->
                        TrackItem(
                            track = track,
                            isSelected = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                            onClick = { viewModel.playTrack(track, sortedTracks) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            selectedTabIndex == 2 -> {
                // Albums Tab
                item {
                    SectionHeader(title = "Albums")
                }
                when (val state = playlistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> {
                        items(state.data) { playlist ->
                            AlbumRowItem(
                                title = playlist.name,
                                artist = "Various Artists",
                                coverUrl = playlist.coverUrl,
                                count = "${playlist.trackCount} tracks",
                                onClick = {
                                    if (playlist.tracks.isNotEmpty()) {
                                        viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                    }
                                }
                            )
                        }
                    }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
            selectedTabIndex == 3 -> {
                // Artists Tab
                item {
                    SectionHeader(title = "Artists")
                }
                when (val state = artistsResource) {
                    is Resource.Loading -> item { LoadingIndicator() }
                    is Resource.Success -> {
                        items(state.data) { artist ->
                            ArtistRowItem(artist = artist)
                        }
                    }
                    is Resource.Error -> item { ErrorMessage(state.message) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* New Playlist */ }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onSearchClick != null) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                label = "lib_chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "lib_chip_text"
            )

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectTab(index) }
                    .testTag("library_tab_$index"),
                shape = RoundedCornerShape(16.dp),
                color = bgColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun SortingRow(
    isAscending: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleSort)
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort order",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isAscending) "Date added • Ascending" else "Date added • Descending",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class LibraryShortcut(
    val title: String,
    val icon: ImageVector,
    val subtitle: String
)

@Composable
private fun ShortcutCardsSection(
    likedCount: Int,
    downloadedCount: Int,
    activeShortcut: String?,
    onShortcutClick: (String) -> Unit
) {
    val shortcuts = listOf(
        LibraryShortcut("Liked", Icons.Default.Favorite, "$likedCount songs"),
        LibraryShortcut("Downloaded", Icons.Default.DownloadDone, "$downloadedCount tracks"),
        LibraryShortcut("Exported", Icons.Default.IosShare, "Ready"),
        LibraryShortcut("Cached", Icons.Default.CloudDone, "Smart cache"),
        LibraryShortcut("My Top 50", Icons.Default.Star, "Ranked"),
        LibraryShortcut("Local", Icons.Default.Folder, "On device")
    )

    // 2-row horizontal scroller or grid
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(shortcuts) { shortcut ->
            val isSelected = activeShortcut == shortcut.title
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }

            Surface(
                modifier = Modifier
                    .width(136.dp)
                    .height(96.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onShortcutClick(shortcut.title) }
                    .testTag("shortcut_${shortcut.title.lowercase().replace(" ", "_")}"),
                shape = RoundedCornerShape(20.dp),
                color = bgColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = shortcut.icon,
                        contentDescription = shortcut.title,
                        tint = if (shortcut.title == "Liked") Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = shortcut.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = shortcut.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun PlaylistRowItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} tracks • ${playlist.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { /* Menu */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
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
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
