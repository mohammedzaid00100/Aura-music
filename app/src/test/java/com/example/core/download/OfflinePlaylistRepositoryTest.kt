package com.example.core.download

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class OfflinePlaylistRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun repository(available: Set<String> = setOf("sad", "romantic")) =
        OfflinePlaylistRepository(folder.root) { it in available }

    @Test fun createAddAndRenameSurviveRestart() = runBlocking {
        val first = repository()
        val id = first.create(" Sad songs ")
        first.addTrack(id, "sad")
        first.rename(id, "Quiet evening")
        val restored = repository()
        restored.load()
        assertEquals(listOf(OfflinePlaylist(id, "Quiet evening", listOf("sad"))), restored.playlists.value)
    }

    @Test fun oneSongCanBelongToSeveralPlaylistsWithoutDuplicateMembership() = runBlocking {
        val repo = repository()
        val first = repo.create("Sad", "sad")
        val second = repo.create("Evening")
        repo.addTrack(first, "sad")
        repo.addTrack(second, "sad")
        assertEquals(listOf(listOf("sad"), listOf("sad")), repo.playlists.value.map { it.trackIds })
        assertEquals(listOf("downloaded-playlists.json"), folder.root.listFiles()!!.map { it.name })
    }

    @Test fun concurrentAddsAreNotLost() = runBlocking {
        val repo = repository()
        val id = repo.create("Mixed")
        listOf("sad", "romantic", "sad").map { track -> async { repo.addTrack(id, track) } }.awaitAll()
        assertEquals(setOf("sad", "romantic"), repo.playlists.value.single().trackIds.toSet())
        assertEquals(2, repo.playlists.value.single().trackIds.size)
    }

    @Test fun invalidNamesNeverChangeSavedPlaylists() = runBlocking {
        val repo = repository()
        val id = repo.create("Sad")
        for (name in listOf(" ", "x".repeat(81), " sad ")) {
            assertTrue(runCatching { repo.create(name) }.isFailure)
        }
        assertTrue(runCatching { repo.rename(id, " ") }.isFailure)
        assertEquals(listOf(OfflinePlaylist(id, "Sad")), repo.playlists.value)
        repo.rename(id, "SAD")
        assertEquals("SAD", repo.playlists.value.single().name)
    }

    @Test fun incompleteOrRemovedDownloadsCannotBeAdded() = runBlocking {
        val repo = repository(emptySet())
        val id = repo.create("Sad")
        assertTrue(runCatching { repo.addTrack(id, "sad") }.isFailure)
        assertTrue(runCatching { repo.create("Evening", "sad") }.isFailure)
        assertEquals(listOf(OfflinePlaylist(id, "Sad")), repo.playlists.value)
    }

    @Test fun removingMembershipPreservesOtherPlaylistsAndDownloads() = runBlocking {
        val audio = File(folder.root, "song.audio").apply { writeText("existing downloaded file") }
        val repo = repository()
        val first = repo.create("Sad", "sad")
        val second = repo.create("Evening", "sad")
        repo.removeTrack(first, "sad")
        val restored = repository()
        restored.load()
        assertEquals(emptyList<String>(), restored.playlists.value.first().trackIds)
        assertEquals(listOf("sad"), restored.playlists.value.find { it.id == second }!!.trackIds)
        assertEquals("existing downloaded file", audio.readText())
    }

    @Test fun unreadableIndexIsPreservedAndCanBeRetried() = runBlocking {
        val file = File(folder.root, "downloaded-playlists.json").apply { writeText("broken json") }
        val repo = repository()
        assertTrue(runCatching { repo.load() }.isFailure)
        assertTrue(runCatching { repo.create("Do not overwrite") }.isFailure)
        assertEquals("broken json", file.readText())
        file.writeText("[]")
        repo.load()
        repo.create("Recovered")
        assertEquals("Recovered", repo.playlists.value.single().name)
    }

    @Test fun failedSaveIsNotPublishedAsSuccess() = runBlocking {
        val blocked = File(folder.root, "blocked").apply { writeText("not a directory") }
        val repo = OfflinePlaylistRepository(blocked) { true }
        repo.load()
        assertTrue(runCatching { repo.create("Cannot save") }.isFailure)
        assertTrue(repo.playlists.value.isEmpty())
    }
}
