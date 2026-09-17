package com.example.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.TrackItem
import com.example.core.download.DownloadStatus
import com.example.core.download.OfflinePlaylist
import com.example.core.download.OfflinePlaylistRepository
import com.example.core.download.SongDownload
import com.example.core.model.PlaybackState
import com.example.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DownloadedLibraryState(
    val repository: OfflinePlaylistRepository,
    val playlists: State<List<OfflinePlaylist>>,
    private val scope: CoroutineScope
) {
    var showPlaylists by mutableStateOf(false)
    var selectedPlaylistId by mutableStateOf<String?>(null)
    var pickerTrack by mutableStateOf<Track?>(null)
    var naming by mutableStateOf(false)
    var renameId by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var busy by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not save the playlist. Try again." }
            finally { busy = false }
        }
    }

    fun load() = perform { repository.load(); ready = true }

    fun editName(playlist: OfflinePlaylist? = null) {
        renameId = playlist?.id
        name = playlist?.name.orEmpty()
        error = null
        naming = true
    }
}

@Composable
internal fun rememberDownloadedLibraryState(viewModel: LibraryViewModel): DownloadedLibraryState {
    val repository = viewModel.downloadRepository.offlinePlaylists
    val playlists = repository.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val state = remember(repository) { DownloadedLibraryState(repository, playlists, scope) }
    LaunchedEffect(state) { state.load() }
    BackHandler(enabled = state.selectedPlaylistId != null && !state.naming && state.pickerTrack == null) {
        state.selectedPlaylistId = null
    }
    return state
}

internal fun LazyListScope.downloadedContent(
    state: DownloadedLibraryState,
    entries: List<SongDownload>,
    isAscending: Boolean,
    playback: PlaybackState,
    viewModel: LibraryViewModel
) {
    val completed = entries.filter { it.status == DownloadStatus.COMPLETE }.associateBy { it.track.id }
    val playlists = state.playlists.value
    val selected = playlists.find { it.id == state.selectedPlaylistId }
    item(key = "downloads_heading") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Downloaded (${completed.size})", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.showPlaylists, onClick = {
                    state.showPlaylists = false; state.selectedPlaylistId = null
                }, label = { Text("All songs") }, modifier = Modifier.testTag("downloads_all_songs"))
                FilterChip(selected = state.showPlaylists, onClick = {
                    state.showPlaylists = true; state.selectedPlaylistId = null
                }, label = { Text("Playlists") }, modifier = Modifier.testTag("downloads_playlists"))
            }
            if (state.error != null && !state.naming && state.pickerTrack == null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                if (!state.ready) TextButton(onClick = { state.load() }, enabled = !state.busy) { Text("Try again") }
            }
        }
    }
    if (state.showPlaylists && selected == null) {
        item(key = "downloads_new_playlist") {
            TextButton(onClick = { state.editName() }, enabled = state.ready && !state.busy,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("downloads_create_playlist")) {
                Text("+ New playlist")
            }
            if (!state.ready && state.busy) CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp))
            if (state.ready && playlists.isEmpty()) DownloadedEmpty(
                "No playlists yet", "Create a playlist for your mood, then add songs from All songs."
            )
        }
        val sortedPlaylists = if (isAscending) playlists else playlists.reversed()
        items(sortedPlaylists, key = { "downloaded_playlist_${it.id}" }) { playlist ->
            Row(Modifier.fillMaxWidth().clickable { state.selectedPlaylistId = playlist.id }
                .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${playlist.trackIds.count { it in completed }} songs", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlaylistOptions(playlist, state)
            }
        }
        return
    }
    val sortedEntries = if (selected != null) {
        val members = selected.trackIds.mapNotNull { completed[it] }
        if (isAscending) members else members.reversed()
    } else if (isAscending) entries.reversed() else entries
    val queue = sortedEntries.filter { it.status == DownloadStatus.COMPLETE }.map { it.track.copy(isDownloaded = true) }
    if (selected != null) item(key = "downloaded_playlist_title") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.selectedPlaylistId = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to downloaded playlists")
            }
            Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            PlaylistOptions(selected, state)
        }
    }
    if (sortedEntries.isEmpty()) item(key = "downloads_empty") {
        DownloadedEmpty(if (selected == null) "No downloaded songs yet" else "No downloaded songs in this playlist",
            if (selected == null) "Tap the download arrow on a song to listen offline"
            else "In All songs, tap a song’s three-dot menu and choose Add to playlist.")
    }
    items(sortedEntries, key = { "downloaded_song_${it.track.id}" }) { entry ->
        Column {
            TrackItem(
                track = entry.track.copy(isDownloaded = entry.status == DownloadStatus.COMPLETE),
                isSelected = playback.currentTrack?.id == entry.track.id,
                isPlaying = playback.currentTrack?.id == entry.track.id && playback.isPlaying,
                onClick = {
                    if (entry.status == DownloadStatus.COMPLETE) {
                        val localQueue = queue.mapNotNull { track ->
                            viewModel.downloadRepository.localUri(track.id)?.let { track.copy(streamUrl = it) }
                        }
                        val target = localQueue.find { it.id == entry.track.id }
                        if (target != null) viewModel.playTrack(target, localQueue)
                        else state.error = "This download is unavailable. Download the song again."
                    }
                },
                onToggleFavorite = { viewModel.toggleFavorite(entry.track) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                trailingContent = if (entry.status == DownloadStatus.COMPLETE) ({
                    DownloadedSongOptions(entry.track, selected, state)
                }) else null
            )
            if (entry.status != DownloadStatus.COMPLETE) {
                Text(text = when (entry.status) {
                    DownloadStatus.PREPARING -> "Preparing download…"
                    DownloadStatus.QUEUED -> "Queued"
                    DownloadStatus.PAUSED -> "Waiting for connection or Android download service"
                    DownloadStatus.FAILED -> entry.error ?: "Download failed. Tap to retry."
                    else -> if (entry.totalBytes > 0)
                        "Downloading ${(100.0 * entry.bytesDownloaded / entry.totalBytes).toInt().coerceIn(0, 100)}%"
                        else "Downloading…"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 88.dp, end = 24.dp, bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun PlaylistOptions(playlist: OfflinePlaylist, state: DownloadedLibraryState) {
    var expanded by remember(playlist.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !state.busy) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for ${playlist.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Rename playlist") }, onClick = {
                expanded = false; state.editName(playlist)
            })
        }
    }
}

@Composable
private fun DownloadedSongOptions(track: Track, playlist: OfflinePlaylist?, state: DownloadedLibraryState) {
    var expanded by remember(track.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = state.ready && !state.busy,
            modifier = Modifier.testTag("downloaded_song_options_${track.id}")) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for ${track.title}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                expanded = false; state.error = null; state.pickerTrack = track
            })
            if (playlist != null) DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = {
                expanded = false
                state.perform { state.repository.removeTrack(playlist.id, track.id) }
            })
        }
    }
}

@Composable
internal fun DownloadedPlaylistDialogs(state: DownloadedLibraryState) {
    val track = state.pickerTrack
    if (state.naming) {
        AlertDialog(onDismissRequest = { if (!state.busy) { state.naming = false; state.error = null } },
            title = { Text(if (state.renameId == null) "New playlist" else "Rename playlist") },
            text = {
                Column {
                    OutlinedTextField(value = state.name, onValueChange = { state.name = it; state.error = null },
                        label = { Text("Playlist name") }, singleLine = true, enabled = !state.busy,
                        isError = state.name.length > 80 || state.error != null,
                        modifier = Modifier.fillMaxWidth().testTag("downloaded_playlist_name"))
                    Text(state.error ?: "${state.name.length}/80", style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy && state.name.trim().isNotEmpty() && state.name.length <= 80,
                    onClick = {
                        val renameId = state.renameId
                        val name = state.name
                        state.perform {
                            if (renameId == null) state.repository.create(name, track?.id)
                            else state.repository.rename(renameId, name)
                            state.naming = false
                            if (renameId == null) state.pickerTrack = null
                        }
                    }) { Text(if (state.busy) "Saving…" else if (state.renameId == null) "Create" else "Save") }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = { state.naming = false; state.error = null }) { Text("Cancel") }
            })
    } else if (track != null) {
        AlertDialog(onDismissRequest = { if (!state.busy) { state.pickerTrack = null; state.error = null } },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { state.editName() }, enabled = !state.busy) { Text("+ New playlist") }
                    if (state.playlists.value.isEmpty()) Text("Create your first playlist to add this song.")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(state.playlists.value, key = { it.id }) { playlist ->
                            val added = track.id in playlist.trackIds
                            TextButton(enabled = !state.busy && !added, modifier = Modifier.fillMaxWidth(), onClick = {
                                state.perform {
                                    state.repository.addTrack(playlist.id, track.id)
                                    state.pickerTrack = null
                                }
                            }) {
                                Text(playlist.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (added) Text("Added", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.busy) Text("Saving…")
                }
            }, confirmButton = {
                TextButton(enabled = !state.busy, onClick = { state.pickerTrack = null; state.error = null }) { Text("Cancel") }
            })
    }
}

@Composable
private fun DownloadedEmpty(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
