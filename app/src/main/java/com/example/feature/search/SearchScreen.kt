package com.example.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Resource
import com.example.core.design.TrackItem
import com.example.core.model.Track

data class ExploreCategory(
    val title: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

val ExploreCategories = listOf(
    ExploreCategory("Pop", Icons.Default.MusicNote, listOf(Color(0xFFE91E63), Color(0xFF9C27B0))),
    ExploreCategory("Rock", Icons.Default.Whatshot, listOf(Color(0xFFD32F2F), Color(0xFF7B1FA2))),
    ExploreCategory("Hip-Hop", Icons.Default.Speaker, listOf(Color(0xFFFF9800), Color(0xFFF44336))),
    ExploreCategory("Electronic", Icons.Default.GraphicEq, listOf(Color(0xFF00BCD4), Color(0xFF3F51B5))),
    ExploreCategory("Ambient", Icons.Default.SelfImprovement, listOf(Color(0xFF009688), Color(0xFF4CAF50))),
    ExploreCategory("Lo-Fi", Icons.Default.Headphones, listOf(Color(0xFF673AB7), Color(0xFF3F51B5))),
    ExploreCategory("Jazz", Icons.Default.Radio, listOf(Color(0xFFFFB300), Color(0xFFE65100))),
    ExploreCategory("Classical", Icons.Default.Piano, listOf(Color(0xFF3F51B5), Color(0xFF1A237E))),
    ExploreCategory("Indie", Icons.Default.Album, listOf(Color(0xFF00897B), Color(0xFF004D40))),
    ExploreCategory("Metal", Icons.Default.Star, listOf(Color(0xFF424242), Color(0xFF212121))),
    ExploreCategory("R&B", Icons.Default.Favorite, listOf(Color(0xFFAD1457), Color(0xFF6A1B9A))),
    ExploreCategory("Acoustic", Icons.Default.MusicNote, listOf(Color(0xFF795548), Color(0xFF4E342E))),
    ExploreCategory("Soundtrack", Icons.Default.TheaterComedy, listOf(Color(0xFF5C6BC0), Color(0xFF283593))),
    ExploreCategory("Party", Icons.Default.Whatshot, listOf(Color(0xFFFF4081), Color(0xFFFF6E40))),
    ExploreCategory("Chill", Icons.Default.SelfImprovement, listOf(Color(0xFF26A69A), Color(0xFF00796B))),
    ExploreCategory("Focus", Icons.Default.Headphones, listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))),
    ExploreCategory("Workout", Icons.Default.SportsGymnastics, listOf(Color(0xFFFF5722), Color(0xFFD84315))),
    ExploreCategory("Sleep", Icons.Default.Nightlight, listOf(Color(0xFF311B92), Color(0xFF1A237E)))
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    var activeResultFilter by remember { mutableStateOf("All") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        // 1. Top Search Header Title
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        // 2. Floating Pill Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(12.dp, shape = RoundedCornerShape(percent = 50), spotColor = Color.Black.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_text_field"),
                    placeholder = {
                        Text(
                            text = "Search songs, artists, albums...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onQueryChange("") },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { /* Voice Search */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Body: Search Results or Explore Grid
        if (query.isBlank()) {
            // Explore Categories Grid
            ExploreContent(
                onCategoryClick = { category ->
                    viewModel.onQueryChange(category.title)
                }
            )
        } else {
            // Search Results with Category Filter Tabs (All, Songs, Artists, Playlists)
            SearchResultsContent(
                query = query,
                searchResults = searchResults,
                activeFilter = activeResultFilter,
                onFilterChange = { activeResultFilter = it },
                isPlaying = playbackState.isPlaying,
                currentTrackId = playbackState.currentTrack?.id,
                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onRetry = { viewModel.retry() }
            )
        }
    }
}

@Composable
private fun ExploreContent(
    onCategoryClick: (ExploreCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp)
    ) {
        item {
            Text(
                text = "Explore Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // 2-column grid chunked by 2
        val rows = ExploreCategories.chunked(2)
        items(rows) { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { category ->
                    ExploreCard(
                        category = category,
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExploreCard(
    category: ExploreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .shadow(6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("genre_card_${category.title.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(category.gradientColors))
                .padding(14.dp)
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Subtle angled icon representation matching reference
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .rotate(-15f)
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    searchResults: Resource<List<Track>>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    isPlaying: Boolean,
    currentTrackId: String?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRetry: () -> Unit
) {
    val filters = listOf("All", "Songs", "Artists", "Playlists")

    Column(modifier = Modifier.fillMaxSize()) {
        // Result Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = activeFilter == filter
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFilterChange(filter) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        when (searchResults) {
            is Resource.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is Resource.Success -> {
                val tracks = searchResults.data
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No results found for \"$query\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
                    ) {
                        item {
                            Text(
                                text = "Results for \"$query\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(tracks) { track ->
                            TrackItem(
                                track = track,
                                isSelected = currentTrackId == track.id,
                                isPlaying = currentTrackId == track.id && isPlaying,
                                onClick = { onPlayTrack(track, tracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            is Resource.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = searchResults.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRetry),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
