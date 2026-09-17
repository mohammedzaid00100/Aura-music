package com.example.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.core.download.OfflinePlaylistRepository
import com.example.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadedPlaylistDialogsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val track = Track(id = "sad", title = "Test song", artist = "Artist", durationMs = 1000, artworkUrl = "", streamUrl = "")
    private lateinit var state: DownloadedLibraryState

    private fun show(repository: OfflinePlaylistRepository, initialize: (DownloadedLibraryState) -> Unit) {
        compose.setContent {
            val playlists = repository.playlists.collectAsState()
            val scope = rememberCoroutineScope()
            val ui = remember {
                DownloadedLibraryState(repository, playlists, scope).also {
                    it.ready = true
                    initialize(it)
                    state = it
                }
            }
            MaterialTheme { DownloadedPlaylistDialogs(ui) }
        }
    }

    @Test fun createPlaylistFromSongPickerSavesSong() {
        val repo = OfflinePlaylistRepository(folder.root) { true }
        show(repo) { it.pickerTrack = track }
        compose.onNodeWithText("+ New playlist").performClick()
        compose.onNodeWithTag("downloaded_playlist_name").performTextInput("Sad songs")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { repo.playlists.value.isNotEmpty() }
        compose.waitForIdle()
        assertEquals("Sad songs", repo.playlists.value.single().name)
        assertEquals(listOf("sad"), repo.playlists.value.single().trackIds)
    }

    @Test fun chooseExistingPlaylistAndDisableAlreadyAddedSong() {
        val repo = OfflinePlaylistRepository(folder.root) { true }
        val id = runBlocking { repo.create("Evening") }
        show(repo) { it.pickerTrack = track }
        compose.onNodeWithText("Evening").performClick()
        compose.waitUntil(10_000) { repo.playlists.value.single().trackIds == listOf("sad") }
        compose.runOnIdle { state.pickerTrack = track }
        compose.onNodeWithText("Added").assertExists()
        assertEquals(id, repo.playlists.value.single().id)
        assertEquals(1, repo.playlists.value.single().trackIds.size)
    }

    @Test fun renamePreservesPlaylistMembership() {
        val repo = OfflinePlaylistRepository(folder.root) { true }
        runBlocking { repo.create("Sad", "sad") }
        show(repo) { it.editName(repo.playlists.value.single()) }
        compose.onNodeWithTag("downloaded_playlist_name").performTextReplacement("Quiet evening")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { repo.playlists.value.single().name == "Quiet evening" }
        assertEquals(listOf("sad"), repo.playlists.value.single().trackIds)
    }
}
