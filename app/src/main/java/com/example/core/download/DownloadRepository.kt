package com.example.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.AtomicFile
import com.example.core.model.Track
import com.example.core.network.saavn.SaavnMediaResolver
import com.example.playback.isDirectAudioUrl
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.MessageDigest

enum class DownloadStatus { PREPARING, QUEUED, DOWNLOADING, PAUSED, COMPLETE, FAILED }

data class SongDownload(
    val track: Track,
    val managerId: Long = -1,
    val status: DownloadStatus = DownloadStatus.PREPARING,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val error: String? = null,
    val requestToken: String = java.util.UUID.randomUUID().toString(),
    val addedAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status in listOf(
        DownloadStatus.PREPARING, DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED
    )
}

/** Android owns the transfers, so leaving Aura does not cancel queued downloads. */
class DownloadRepository(context: Context) : java.io.Closeable {
    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val resolver = SaavnMediaResolver()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val index = AtomicFile(File(context.noBackupFilesDir, "song-downloads.json"))
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter<List<SongDownload>>(Types.newParameterizedType(List::class.java, SongDownload::class.java))
    private val _downloads = MutableStateFlow<Map<String, SongDownload>>(emptyMap())
    val downloads = _downloads.asStateFlow()
    val offlinePlaylists by lazy {
        OfflinePlaylistRepository(context.noBackupFilesDir) { trackId -> localUri(trackId) != null }
    }
    private var indexError: String? = null

    init {
        scope.launch {
            mutex.withLock {
                try {
                    val saved = index.openRead().bufferedReader().use { adapter.fromJson(it.readText()) }.orEmpty()
                    _downloads.value = saved.associateBy { it.track.id }.mapValues { (_, entry) ->
                        if (entry.status == DownloadStatus.PREPARING) entry.copy(
                            status = DownloadStatus.FAILED, error = "Download interrupted. Tap to retry."
                        ) else entry
                    }
                    refreshLocked()
                } catch (_: java.io.FileNotFoundException) {
                    // First launch: no downloads yet.
                } catch (_: Exception) {
                    // Keep the original index if storage is temporarily unavailable or damaged.
                    indexError = "Cannot read saved downloads. Restart Aura and try again."
                } finally {
                    ready.complete(Unit)
                }
            }
            var pollCount = 0
            while (isActive) {
                delay(1000)
                mutex.withLock { runCatching { refreshLocked(checkCompleted = ++pollCount % 30 == 0) } }
            }
        }
    }

    override fun close() { scope.cancel() }

    /** A complete, nonempty file is required; stream URLs are never treated as offline files. */
    fun localUri(trackId: String): String? {
        val entry = _downloads.value[trackId] ?: return null
        if (entry.status != DownloadStatus.COMPLETE) return null
        val file = audioFile(trackId) ?: return null
        return if (file.isFile && file.length() > 0 &&
            (entry.totalBytes <= 0 || file.length() == entry.totalBytes)) Uri.fromFile(file).toString() else null
    }

    suspend fun awaitLocalUri(trackId: String): String? {
        ready.await()
        return localUri(trackId)
    }

    fun download(track: Track) {
        scope.launch {
            ready.await()
            var refreshLink = false
            // Reserve the track before resolving to prevent duplicate taps/transfers.
            val reserved = mutex.withLock {
                val existing = _downloads.value[track.id]
                if (existing?.isActive == true || localUri(track.id) != null) return@withLock null
                refreshLink = existing?.status == DownloadStatus.FAILED
                val entry = SongDownload(track = track.copy(isDownloaded = false),
                    addedAt = existing?.addedAt ?: System.currentTimeMillis())
                try {
                    indexError?.let { throw IOException(it) }
                    existing?.let { removeTransfer(it) }
                    putLocked(entry)
                    entry
                } catch (e: Exception) {
                    publishError(entry, e)
                    null
                }
            } ?: return@launch

            try {
                val directUrl = track.streamUrl.takeUnless {
                    refreshLink && Uri.parse(it).host?.endsWith(".saavncdn.com") == true
                }
                val url = directUrl?.takeIf { isDirectAudioUrl(it) && it.startsWith("https://") }
                    ?: resolver.resolveStreamUrl(track.title, track.artist)
                    ?: throw IOException("Audio unavailable. Try this song again later.")
                if (Uri.parse(url).scheme != "https") throw IOException("A secure download URL is unavailable.")
                mutex.withLock {
                    // A canceled request must never enqueue after its resolver returns.
                    if (_downloads.value[track.id] != reserved) return@withLock
                    val file = audioFile(track.id) ?: throw IOException("Phone storage is unavailable.")
                    if (!file.parentFile!!.exists() && !file.parentFile!!.mkdirs()) {
                        throw IOException("Cannot create the downloads folder.")
                    }
                    if (file.parentFile!!.usableSpace <= 0) throw IOException("Not enough phone storage. Free up space and retry.")
                    val request = DownloadManager.Request(Uri.parse(url))
                        .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .setTitle(track.title)
                        .setDescription(track.artist)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, file.name)
                    val id = manager.enqueue(request)
                    try {
                        putLocked(reserved.copy(managerId = id, status = DownloadStatus.QUEUED))
                    } catch (e: Exception) {
                        manager.remove(id)
                        throw e
                    }
                }
            } catch (e: Exception) {
                mutex.withLock {
                    if (_downloads.value[track.id] == reserved) publishError(reserved, e)
                }
            }
        }
    }

    fun remove(trackId: String) {
        scope.launch {
            ready.await()
            mutex.withLock {
                val entry = _downloads.value[trackId] ?: return@withLock
                try {
                    removeTransfer(entry)
                    commitLocked(_downloads.value - trackId)
                } catch (e: Exception) {
                    publishError(entry, e)
                }
            }
        }
    }

    private fun removeTransfer(entry: SongDownload) {
        if (entry.managerId >= 0) manager.remove(entry.managerId)
        audioFile(entry.track.id)?.let { file ->
            if (file.exists() && !file.delete()) throw IOException("Cannot remove the downloaded file. Try again.")
        }
    }

    private fun audioFile(trackId: String): File? = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?.let { File(it, downloadFileName(trackId)) }

    private fun publishError(entry: SongDownload, error: Exception) {
        val failed = entry.copy(status = DownloadStatus.FAILED,
            error = error.message ?: "Download failed. Check your connection and phone storage.")
        try { putLocked(failed) } catch (_: Exception) {
            _downloads.value = _downloads.value + (entry.track.id to failed)
        }
    }

    private fun putLocked(entry: SongDownload) = commitLocked(_downloads.value + (entry.track.id to entry))

    private fun commitLocked(next: Map<String, SongDownload>) {
        indexError?.let { throw IOException(it) }
        val output = index.startWrite()
        try {
            output.write(adapter.toJson(next.values.toList()).toByteArray(Charsets.UTF_8))
            index.finishWrite(output)
        } catch (e: Exception) {
            index.failWrite(output)
            throw e
        }
        _downloads.value = next
    }

    private fun refreshLocked(checkCompleted: Boolean = true) {
        var durableChange = false
        val next = _downloads.value.mapValues { (_, entry) ->
            val updated = when {
                entry.status == DownloadStatus.COMPLETE && checkCompleted -> {
                    if (localUri(entry.track.id) == null) entry.copy(status = DownloadStatus.FAILED,
                        error = "The downloaded file is missing. Tap to download it again.") else entry
                }
                entry.managerId >= 0 && entry.isActive -> query(entry)
                else -> entry
            }
            if (updated.status != entry.status || updated.error != entry.error) durableChange = true
            updated
        }
        if (durableChange) commitLocked(next) else _downloads.value = next
    }

    private fun query(entry: SongDownload): SongDownload {
        manager.query(DownloadManager.Query().setFilterById(entry.managerId))?.use { cursor ->
            if (!cursor.moveToFirst()) return entry.copy(status = DownloadStatus.FAILED,
                error = "Download was removed. Tap to retry.")
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val state = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETE
                DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                DownloadManager.STATUS_RUNNING -> DownloadStatus.DOWNLOADING
                else -> DownloadStatus.QUEUED
            }
            var error: String? = if (state == DownloadStatus.FAILED) downloadFailureMessage(reason) else null
            var effectiveState = state
            if (state == DownloadStatus.COMPLETE) {
                val file = audioFile(entry.track.id)
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)).orEmpty()
                if (file == null || !file.isFile || file.length() == 0L ||
                    (total > 0 && file.length() != total) || mime.startsWith("text/") || mime.contains("json")) {
                    effectiveState = DownloadStatus.FAILED
                    error = "The server did not return a complete audio file. Tap to retry."
                }
            }
            return entry.copy(status = effectiveState, bytesDownloaded = bytes, totalBytes = total, error = error)
        }
        return entry
    }
}

internal fun downloadFileName(trackId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(trackId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".audio"

internal fun downloadFailureMessage(reason: Int): String = when (reason) {
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough phone storage. Free up space and retry."
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Phone storage is unavailable. Reconnect it and retry."
    DownloadManager.ERROR_CANNOT_RESUME -> "The connection was interrupted. Tap to retry the download."
    401, 403, 404, 410 -> "The audio link expired or is unavailable. Tap to retry."
    else -> "Download failed. Check your connection and phone storage, then retry."
}
