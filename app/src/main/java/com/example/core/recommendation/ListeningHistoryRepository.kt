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

data class ListeningStat(
    val track: Track,
    val playCount: Int = 0,
    val completedCount: Int = 0,
    val skipCount: Int = 0,
    val totalListenMs: Long = 0L,
    val lastPlayedAt: Long = 0L
)

/**
 * Private, on-device listening history used only to personalize Aura for this device.
 * The file lives in noBackupFilesDir so it is not uploaded as app backup data.
 */
class ListeningHistoryRepository(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<String, ListeningStat>> = _stats.asStateFlow()

    suspend fun recordStart(track: Track, now: Long = System.currentTimeMillis()) {
        mutex.withLock {
            val current = _stats.value
            val previous = current[track.id]
            val updatedStat = if (previous == null) {
                ListeningStat(track = track, playCount = 1, lastPlayedAt = now)
            } else {
                previous.copy(
                    track = mergeTrack(previous.track, track),
                    playCount = previous.playCount + 1,
                    lastPlayedAt = now
                )
            }

            publishIfSaved(current + (track.id to updatedStat))
        }
    }

    suspend fun recordSessionEnd(
        trackId: String,
        listenedMs: Long,
        durationMs: Long,
        reachedEnd: Boolean
    ) {
        mutex.withLock {
            val current = _stats.value
            val previous = current[trackId] ?: return@withLock

            val safeListened = listenedMs.coerceAtLeast(0L)
            val effectiveDuration = when {
                durationMs > 0L -> durationMs
                previous.track.durationMs > 0L -> previous.track.durationMs
                else -> 0L
            }

            val completionThreshold = if (effectiveDuration > 0L) {
                (effectiveDuration * 0.80).toLong()
            } else {
                Long.MAX_VALUE
            }

            val completed = reachedEnd || safeListened >= completionThreshold
            val skipThreshold = if (effectiveDuration > 0L) {
                minOf(30_000L, (effectiveDuration * 0.25).toLong().coerceAtLeast(8_000L))
            } else {
                15_000L
            }
            val skipped = !completed && safeListened in 1L until skipThreshold

            val updated = previous.copy(
                completedCount = previous.completedCount + if (completed) 1 else 0,
                skipCount = previous.skipCount + if (skipped) 1 else 0,
                totalListenMs = previous.totalListenMs + safeListened
            )

            publishIfSaved(current + (trackId to updated))
        }
    }

    fun recentTracks(limit: Int = 12): List<Track> = stats.value.values
        .sortedByDescending { it.lastPlayedAt }
        .map { it.track }
        .distinctBy { it.id }
        .take(limit)

    private fun publishIfSaved(updated: Map<String, ListeningStat>) {
        if (save(updated)) {
            _stats.value = updated
        }
    }

    private fun save(data: Map<String, ListeningStat>): Boolean {
        val root = JSONObject()
        val entries = JSONArray()

        data.values.forEach { stat ->
            entries.put(
                JSONObject().apply {
                    put("track", trackToJson(stat.track))
                    put("playCount", stat.playCount)
                    put("completedCount", stat.completedCount)
                    put("skipCount", stat.skipCount)
                    put("totalListenMs", stat.totalListenMs)
                    put("lastPlayedAt", stat.lastPlayedAt)
                }
            )
        }
        root.put("version", 1)
        root.put("entries", entries)

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

    private fun load(): Map<String, ListeningStat> {
        if (!file.baseFile.exists()) return emptyMap()

        return try {
            val text = file.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val entries = root.optJSONArray("entries") ?: return emptyMap()
            val result = LinkedHashMap<String, ListeningStat>()

            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val trackJson = item.optJSONObject("track") ?: continue
                val track = jsonToTrack(trackJson) ?: continue
                result[track.id] = ListeningStat(
                    track = track,
                    playCount = item.optInt("playCount", 0),
                    completedCount = item.optInt("completedCount", 0),
                    skipCount = item.optInt("skipCount", 0),
                    totalListenMs = item.optLong("totalListenMs", 0L),
                    lastPlayedAt = item.optLong("lastPlayedAt", 0L)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
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
        put("isFavorite", track.isFavorite)
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
            isFavorite = json.optBoolean("isFavorite", false),
            isDownloaded = json.optBoolean("isDownloaded", false)
        )
    }

    private fun nullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).takeIf { it.isNotBlank() }
    }

    private fun mergeTrack(old: Track, fresh: Track): Track = fresh.copy(
        title = fresh.title.ifBlank { old.title },
        artist = fresh.artist.ifBlank { old.artist },
        artistId = fresh.artistId ?: old.artistId,
        album = fresh.album ?: old.album,
        albumId = fresh.albumId ?: old.albumId,
        durationMs = if (fresh.durationMs > 0L) fresh.durationMs else old.durationMs,
        artworkUrl = fresh.artworkUrl.ifBlank { old.artworkUrl },
        streamUrl = fresh.streamUrl.ifBlank { old.streamUrl },
        sourceId = fresh.sourceId.ifBlank { old.sourceId },
        isFavorite = fresh.isFavorite || old.isFavorite,
        isDownloaded = fresh.isDownloaded || old.isDownloaded
    )

    companion object {
        private const val FILE_NAME = "aura-listening-history.json"
    }
}
