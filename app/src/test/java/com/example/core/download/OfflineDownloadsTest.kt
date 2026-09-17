package com.example.core.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Track
import com.example.playback.isPlayableAudioUri
import com.example.playback.toMediaItem
import com.example.playback.toTrack
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class OfflineDownloadsTest {
    private lateinit var context: Context
    private val track = Track(id = "offline-test", title = "Test recording", artist = "Test artist",
        durationMs = 1000, artworkUrl = "", streamUrl = "https://example.org/recording.mp3")
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        .adapter<List<SongDownload>>(Types.newParameterizedType(List::class.java, SongDownload::class.java))

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, "song-downloads.json").delete()
        File(context.noBackupFilesDir, "song-downloads.json.bak").delete()
        File(context.noBackupFilesDir, "song-downloads.json.new").delete()
        file().delete()
    }

    private fun file() = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), downloadFileName(track.id))
    private fun seed(entry: SongDownload) {
        context.noBackupFilesDir.mkdirs()
        File(context.noBackupFilesDir, "song-downloads.json").writeText(adapter.toJson(listOf(entry)))
    }

    @Test fun completedDownloadSurvivesRepositoryRestartWithoutNetwork() = runBlocking {
        file().apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        seed(SongDownload(track, status = DownloadStatus.COMPLETE, totalBytes = 4))
        repeat(2) {
            DownloadRepository(context).use { repository ->
                assertEquals(Uri.fromFile(file()).toString(), repository.awaitLocalUri(track.id))
                assertEquals(track.title, repository.downloads.value[track.id]?.track?.title)
            }
        }
    }

    @Test fun deletedFileIsNeverReturnedAsPlayableOffline() = runBlocking {
        seed(SongDownload(track, status = DownloadStatus.COMPLETE, totalBytes = 4))
        DownloadRepository(context).use { repository ->
            assertNull(repository.awaitLocalUri(track.id))
            assertEquals(DownloadStatus.FAILED, repository.downloads.value[track.id]?.status)
        }
    }

    @Test fun truncatedFileIsNeverReturnedAsPlayableOffline() = runBlocking {
        file().apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2)) }
        seed(SongDownload(track, status = DownloadStatus.COMPLETE, totalBytes = 4))
        DownloadRepository(context).use { repository ->
            assertNull(repository.awaitLocalUri(track.id))
            assertEquals(DownloadStatus.FAILED, repository.downloads.value[track.id]?.status)
        }
    }

    @Test fun preparationInterruptedByProcessDeathBecomesRetryable() = runBlocking {
        seed(SongDownload(track, status = DownloadStatus.PREPARING))
        DownloadRepository(context).use { repository ->
            assertNull(repository.awaitLocalUri(track.id))
            val restored = repository.downloads.value[track.id]!!
            assertEquals(DownloadStatus.FAILED, restored.status)
            assertFalse(restored.isActive)
        }
    }

    @Test fun localMediaItemRetainsIdentityForOfflineQueueResolution() {
        file().apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val localTrack = track.copy(streamUrl = Uri.fromFile(file()).toString(), isDownloaded = true)
        val item = localTrack.toMediaItem()
        assertTrue(isPlayableAudioUri(localTrack.streamUrl))
        assertEquals("file", item.localConfiguration?.uri?.scheme)
        assertEquals(track.id, item.localConfiguration?.customCacheKey)
        assertEquals(track.id, item.toTrack().id)
        assertTrue(item.toTrack().isDownloaded)
        file().delete()
        assertFalse(isPlayableAudioUri(localTrack.streamUrl))
    }

    @Test fun remoteAndUnresolvedQueueItemsCarryOfflineLookupIdentity() {
        assertEquals(track.id, track.toMediaItem().localConfiguration?.customCacheKey)
        val unresolved = track.copy(streamUrl = "")
        assertEquals("aura", unresolved.toMediaItem().localConfiguration?.uri?.scheme)
        assertEquals(track.id, unresolved.toMediaItem().localConfiguration?.customCacheKey)
    }

    @Test fun serverProvidedTrackIdsCannotEscapeDownloadFolder() {
        listOf("../secret", "/absolute/path", "artist/song?x=1", "a\\b", "🎵").forEach { id ->
            val name = downloadFileName(id)
            assertTrue(name.matches(Regex("[0-9a-f]{64}\\.audio")))
        }
        assertNotEquals(downloadFileName("a/b"), downloadFileName("a_b"))
    }
}
