package com.example.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Resource
import com.example.core.design.TrackItem
import com.example.core.model.Track

private data class BrowseCategory(
    val name: String,
    val colors: List<Color>
)

private val BrowseCategories = listOf(
    BrowseCategory("Pop", listOf(Color(0xFF9B5DE5), Color(0xFFF15BB5))),
    BrowseCategory("Hip-Hop", listOf(Color(0xFFF15A24), Color(0xFF8E2DE2))),
    BrowseCategory("Chill", listOf(Color(0xFF00B4D8), Color(0xFF0077B6))),
    BrowseCategory("Focus", listOf(Color(0xFF355C7D), Color(0xFF6C5B7B))),
    BrowseCategory("Workout", listOf(Color(0xFFFF512F), Color(0xFFDD2476))),
    BrowseCategory("Indie", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    BrowseCategory("R&B", listOf(Color(0xFF8E54E9), Color(0xFF4776E6))),
    BrowseCategory("Sleep", listOf(Color(0xFF232526), Color(0xFF414345)))
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 14.dp)
        )

        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .testTag("search_text_field"),
            placeholder = {
                Text(
                    text = "What do you want to listen to?",
                    color = Color(0xFF575B63),
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF111318)
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(
                        onClick = { viewModel.onQueryChange("") },
                        modifier = Modifier.testTag("search_clear_button")
                    ) {
                        Icon(Icons.Default.Clear, "Clear", tint = Color(0xFF111318))
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF4F4F5),
                unfocusedContainerColor = Color(0xFFF4F4F5),
                disabledContainerColor = Color(0xFFF4F4F5),
                focusedTextColor = Color(0xFF111318),
                unfocusedTextColor = Color(0xFF111318),
                cursorColor = Color(0xFF111318),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (query.isBlank()) {
            BrowseContent(onCategoryClick = { viewModel.onQueryChange(it) })
        } else {
            SearchResults(
                query = query,
                state = searchResults,
                currentTrackId = playbackState.currentTrack?.id,
                isPlaying = playbackState.isPlaying,
                onPlay = viewModel::playTrack,
                onToggleFavorite = viewModel::toggleFavorite,
                onRetry = viewModel::retry
            )
        }
    }
}

@Composable
private fun BrowseContent(onCategoryClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                text = "Browse all",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        items(BrowseCategories.chunked(2)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { category ->
                    BrowseCard(
                        category = category,
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(category.name) }
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BrowseCard(
    category: BrowseCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(102.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(category.colors))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("genre_card_${category.name.lowercase()}")
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = "AURA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.38f),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun SearchResults(
    query: String,
    state: Resource<List<Track>>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is Resource.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
        is Resource.Success -> {
            if (state.data.isEmpty()) {
                EmptySearch(query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        Text(
                            text = "Songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                    items(state.data, key = { it.id }) { track ->
                        TrackItem(
                            track = track,
                            isSelected = currentTrackId == track.id,
                            isPlaying = currentTrackId == track.id && isPlaying,
                            onClick = { onPlay(track, state.data) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        is Resource.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tap to retry",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun EmptySearch(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No songs found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Try a different search for “$query”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
