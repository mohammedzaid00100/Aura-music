package com.example.feature.library

import android.content.Context
import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.download.DownloadRepository
import com.example.core.download.DownloadStatus
import com.example.core.download.SongDownload
import com.example.core.download.downloadFileName
import com.example.core.model.*
import com.example.playback.PlaybackController
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadedScreenRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private var downloads: DownloadRepository? = null
    private val song = Track(id = "regression-song", title = "Offline recording", artist = "Test artist",
        durationMs = 1000, artworkUrl = "", streamUrl = "")
    private val player = TestPlayer()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        listOf("song-downloads.json", "downloaded-playlists.json").forEach { name ->
            listOf("", ".bak", ".new").forEach { suffix -> File(context.noBackupFilesDir, name + suffix).delete() }
        }
    }

    @After fun close() { downloads?.close() }

    private fun show(withSong: Boolean = false) {
        if (withSong) {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), downloadFileName(song.id))
            file.parentFile!!.mkdirs()
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                .adapter<List<SongDownload>>(Types.newParameterizedType(List::class.java, SongDownload::class.java))
            File(context.noBackupFilesDir, "song-downloads.json").writeText(adapter.toJson(listOf(
                SongDownload(song, status = DownloadStatus.COMPLETE, totalBytes = 4)
            )))
        }
        val repository = DownloadRepository(context).also { downloads = it }
        runBlocking { repository.awaitLocalUri(song.id) }
        val vm = LibraryViewModel(TestMusic(), player, repository)
        compose.setContent { MaterialTheme { LibraryScreen(viewModel = vm) } }
    }

    @Test fun openingDownloadedFromLibraryDoesNotCrash() {
        show()
        compose.onNodeWithTag("shortcut_downloaded").performClick()
        compose.onNodeWithTag("downloads_all_songs").assertExists()
        compose.onNodeWithTag("downloads_playlists").performClick()
        compose.onNodeWithTag("downloads_create_playlist").assertExists()
    }

    @Test fun switchAwayAndReopenDownloadedRepeatedly() {
        show()
        repeat(4) {
            compose.onNodeWithTag("shortcut_downloaded").performClick()
            compose.onNodeWithTag("downloads_all_songs").assertExists()
            compose.onNodeWithTag("shortcut_liked").performClick()
            compose.onNodeWithTag("downloads_all_songs").assertDoesNotExist()
        }
        compose.onNodeWithTag("shortcut_downloaded").performClick()
        compose.onNodeWithTag("library_tab_1").performClick()
        compose.onNodeWithTag("downloads_all_songs").assertDoesNotExist()
        compose.onNodeWithTag("shortcut_downloaded").performClick()
        compose.onNodeWithTag("downloads_all_songs").assertExists()
    }

    @Test fun downloadedSongCanBeAddedAndOpenedInPlaylistFromFullScreen() {
        show(withSong = true)
        compose.onNodeWithTag("shortcut_downloaded").performClick()
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("downloaded_song_options_${song.id}"))
        compose.waitUntil(10_000) { downloads!!.offlinePlaylists.playlists.value.isEmpty() }
        compose.onNodeWithTag("downloaded_song_options_${song.id}").performClick()
        compose.onNodeWithText("Add to playlist").performClick()
        compose.onNodeWithText("+ New playlist").performClick()
        compose.onNodeWithTag("downloaded_playlist_name").performTextInput("My mood")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(10_000) { downloads!!.offlinePlaylists.playlists.value.isNotEmpty() }
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("downloads_playlists"))
        compose.onNodeWithTag("downloads_playlists").performClick()
        compose.onNodeWithText("My mood").performClick()
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("track_item_${song.id}"))
        compose.onNodeWithTag("track_item_${song.id}").performClick()
        assertEquals(listOf(song.id), player.playbackState.value.queue.map { it.id })
        assertEquals("file", android.net.Uri.parse(player.playbackState.value.currentTrack!!.streamUrl).scheme)
    }

    private class TestMusic : MusicRepository {
        override fun getFeaturedTracks() = flowOf(Resource.Success(emptyList<Track>()))
        override fun getTrendingTracks() = getFeaturedTracks()
        override fun getRecommendedPlaylists() = flowOf(Resource.Success(emptyList<Playlist>()))
        override fun getTopArtists() = flowOf(Resource.Success(emptyList<Artist>()))
        override suspend fun search(query: String) = Resource.Success(emptyList<Track>())
        override fun getFavoriteTracks() = flowOf(emptyList<Track>())
        override suspend fun toggleFavorite(track: Track) = Unit
        override suspend fun isFavorite(trackId: String) = false
        override suspend fun getAlbum(albumId: String): Resource<Album> = Resource.Error("Not used")
        override suspend fun getArtist(artistId: String): Resource<Artist> = Resource.Error("Not used")
    }

    private class TestPlayer : PlaybackController {
        override val playbackState = MutableStateFlow(PlaybackState())
        override fun playTrack(track: Track, queue: List<Track>) {
            playbackState.value = PlaybackState(currentTrack = track, queue = queue, isPlaying = true)
        }
        override fun togglePlayPause() = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun skipToNext() = Unit
        override fun skipToPrevious() = Unit
        override fun toggleShuffle() = Unit
        override fun toggleRepeat() = Unit
        override fun addToQueue(track: Track) = Unit
        override fun removeFromQueue(index: Int) = Unit
        override fun release() = Unit
    }
}
