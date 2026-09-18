package com.example.core.recommendation

import android.content.Context
import android.util.AtomicFile
import com.example.core.model.Track
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class TrackPreferenceState(
    val liked: Map<String, Track> = emptyMap(),
    val disliked: Map<String, Track> = emptyMap()
)

/**
 * Explicit single-user taste controls stored only on this device.
 * A track can never be both liked and disliked at the same time.
 */
class TrackPreferenceRepository(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TrackPreferenceState> = _state.asStateFlow()

    fun isLiked(trackId: String): Boolean = _state.value.liked.containsKey(trackId)
    fun isDisliked(trackId: String): Boolean = _state.value.disliked.containsKey(trackId)

    suspend fun toggleLike(track: Track) {
        mutex.withLock {
            val current = _state.value
            val liked = current.liked.toMutableMap()
            val disliked = current.disliked.toMutableMap()

            if (liked.containsKey(track.id)) {
                liked.remove(track.id)
            } else {
                liked[track.id] = track.copy(isFavorite = true)
                disliked.remove(track.id)
            }

            publishIfSaved(TrackPreferenceState(liked = liked, disliked = disliked))
        }
    }

    suspend fun toggleDislike(track: Track) {
        mutex.withLock {
            val current = _state.value
            val liked = current.liked.toMutableMap()
            val disliked = current.disliked.toMutableMap()

            if (disliked.containsKey(track.id)) {
                disliked.remove(track.id)
            } else {
                disliked[track.id] = track.copy(isFavorite = false)
                liked.remove(track.id)
            }

            publishIfSaved(TrackPreferenceState(liked = liked, disliked = disliked))
        }
    }

    private fun publishIfSaved(updated: TrackPreferenceState) {
        if (save(updated)) _state.value = updated
    }

    private fun save(state: TrackPreferenceState): Boolean {
        val root = JSONObject().apply {
            put("version", 1)
            put("liked", tracksToJson(state.liked.values))
            put("disliked", tracksToJson(state.disliked.values))
        }

        val output = file.startWrite()
        return try {
            output.write(root.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()
            file.finishWrite(output)
            true
        } catch (_: Exception) {
            file.failWrite(output)
            false
        }
    }

    private fun load(): TrackPreferenceState {
        if (!file.baseFile.exists()) return TrackPreferenceState()
        return try {
            val text = file.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            TrackPreferenceState(
                liked = jsonToTracks(root.optJSONArray("liked")),
                disliked = jsonToTracks(root.optJSONArray("disliked"))
            )
        } catch (_: Exception) {
            TrackPreferenceState()
        }
    }

    private fun tracksToJson(tracks: Collection<Track>): JSONArray = JSONArray().apply {
        tracks.forEach { put(trackToJson(it)) }
    }

    private fun jsonToTracks(array: JSONArray?): Map<String, Track> {
        if (array == null) return emptyMap()
        val result = LinkedHashMap<String, Track>()
        for (index in 0 until array.length()) {
            val track = array.optJSONObject(index)?.let(::jsonToTrack) ?: continue
            result[track.id] = track
        }
        return result
    }

    private fun trackToJson(track: Track) = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("artistId", track.artistId ?: JSONObject.NULL)
        put("album", track.album ?: JSONObject.NULL)
        put("albumId", track.albumId ?: JSONObject.NULL)
        put("durationMs", track.durationMs)
        put("artworkUrl", track.artworkUrl)
        put("streamUrl", track.streamUrl)
        put("sourceId", track.sourceId)
        put("isDownloaded", track.isDownloaded)
    }

    private fun jsonToTrack(json: JSONObject): Track? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = json.optString("title"),
            artist = json.optString("artist"),
            artistId = nullableString(json, "artistId"),
            album = nullableString(json, "album"),
            albumId = nullableString(json, "albumId"),
            durationMs = json.optLong("durationMs", 0L),
            artworkUrl = json.optString("artworkUrl"),
            streamUrl = json.optString("streamUrl"),
            sourceId = json.optString("sourceId", "aura"),
            isFavorite = true,
            isDownloaded = json.optBoolean("isDownloaded", false)
        )
    }

    private fun nullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val FILE_NAME = "aura-track-preferences.json"
    }
}
