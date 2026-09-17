package com.example.core.download

import android.util.AtomicFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

data class OfflinePlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList()
)

/** Local membership only: audio remains owned by DownloadRepository, with no duplicate files. */
class OfflinePlaylistRepository(
    directory: File,
    private val isDownloaded: (String) -> Boolean
) {
    private val index = AtomicFile(File(directory, "downloaded-playlists.json"))
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter<List<OfflinePlaylist>>(Types.newParameterizedType(List::class.java, OfflinePlaylist::class.java))
    private val mutex = Mutex()
    private var loaded = false
    private val _playlists = MutableStateFlow<List<OfflinePlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { loadLocked() } }

    suspend fun create(name: String, initialTrackId: String? = null): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val cleanName = validateName(name)
            if (initialTrackId != null) requireDownloaded(initialTrackId)
            val playlist = OfflinePlaylist(UUID.randomUUID().toString(), cleanName, listOfNotNull(initialTrackId))
            commitLocked(_playlists.value + playlist)
            playlist.id
        }
    }

    suspend fun rename(playlistId: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            requirePlaylist(playlistId)
            val cleanName = validateName(name, playlistId)
            commitLocked(_playlists.value.map { if (it.id == playlistId) it.copy(name = cleanName) else it })
        }
    }

    suspend fun addTrack(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val playlist = requirePlaylist(playlistId)
            requireDownloaded(trackId)
            if (trackId !in playlist.trackIds) {
                commitLocked(_playlists.value.map {
                    if (it.id == playlistId) it.copy(trackIds = it.trackIds + trackId) else it
                })
            }
        }
    }

    suspend fun removeTrack(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            requirePlaylist(playlistId)
            commitLocked(_playlists.value.map {
                if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
            })
        }
    }

    private fun requirePlaylist(id: String): OfflinePlaylist = _playlists.value.find { it.id == id }
        ?: throw IllegalArgumentException("This playlist is no longer available.")

    private fun requireDownloaded(id: String) {
        require(isDownloaded(id)) { "This song is no longer downloaded. Download it again first." }
    }

    private fun validateName(name: String, exceptId: String? = null): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Enter a playlist name." }
        require(clean.length <= 80) { "Use 80 characters or fewer." }
        require(_playlists.value.none { it.id != exceptId && it.name.equals(clean, ignoreCase = true) }) {
            "A playlist with that name already exists. Choose another name."
        }
        return clean
    }

    private fun loadLocked() {
        if (loaded) return
        val saved = try {
            index.openRead().bufferedReader().use { adapter.fromJson(it.readText()) }
                ?: throw IOException("Cannot read saved playlists.")
        } catch (e: FileNotFoundException) {
            if (index.baseFile.exists()) throw IOException("Cannot read saved playlists. Try again.", e)
            emptyList()
        } catch (e: Exception) {
            // Do not replace an unreadable index with an empty one.
            throw IOException("Cannot read saved playlists. Restart Aura and try again.", e)
        }
        _playlists.value = saved
        loaded = true
    }

    private fun commitLocked(next: List<OfflinePlaylist>) {
        val output = index.startWrite()
        try {
            output.write(adapter.toJson(next).toByteArray(Charsets.UTF_8))
            index.finishWrite(output)
        } catch (e: Exception) {
            index.failWrite(output)
            throw IOException("Cannot save this playlist. Check phone storage and try again.", e)
        }
        _playlists.value = next
    }
}
