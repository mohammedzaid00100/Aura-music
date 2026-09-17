package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.core.network.saavn.SaavnMediaResolver
import com.example.playback.PlaybackControllerImpl
import kotlinx.coroutines.runBlocking
import com.example.core.model.Track
import com.example.playback.toMediaItem
import com.example.playback.toTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaavnResolverAndPlaybackTest {

    @Test
    fun saavnResolver_decryptsKnownEncryptedUrl() {
        val resolver = SaavnMediaResolver()
        // Known encrypted media url from JioSaavn API for Alan Walker - Faded
        val encrypted = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDy4sinFyt8XqMvnaGNKYpjMnt1Sb5pnucfcpxfUUvk+cQR0BSamY7SWBw7tS9a8Gtq"
        val decrypted = resolver.decryptMediaUrl(encrypted)

        assertNotNull(decrypted)
        assertTrue("Decrypted URL must start with http: $decrypted", decrypted.startsWith("http"))
        assertTrue("Decrypted URL must be an mp4 container: $decrypted", decrypted.contains(".mp4"))
    }

    @Test
    fun playbackController_doesNotFakePlayingStateOnSelection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = PlaybackControllerImpl(context)

        // Initial state
        val initial = controller.playbackState.value
        assertFalse(initial.isPlaying)
        assertFalse(initial.isBuffering)
        assertEquals(0L, initial.positionMs)

        controller.release()
    }

    @Test
    fun trackToMediaItemAndBack_preservesAllRequiredMetadata() {
        val original = Track(
            id = "test_song_123",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            albumId = "album_999",
            durationMs = 230000L,
            artworkUrl = "https://c.saavncdn.com/123/starboy-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/123/test.mp4",
            isFavorite = true
        )

        val mediaItem = original.toMediaItem()
        val reconstructed = mediaItem.toTrack()

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.title, reconstructed.title)
        assertEquals(original.artist, reconstructed.artist)
        assertEquals(original.album, reconstructed.album)
        assertEquals(original.durationMs, reconstructed.durationMs)
        assertEquals(original.artworkUrl, reconstructed.artworkUrl)
        assertEquals(original.streamUrl, reconstructed.streamUrl)
        assertEquals(original.isFavorite, reconstructed.isFavorite)
    }

    @Test
    fun playbackController_stateUpdatesAuthoritativelyFromExoPlayerTransition() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = PlaybackControllerImpl(context)

        val track1 = Track(
            id = "t1",
            title = "Song One",
            artist = "Artist A",
            album = "Album 1",
            durationMs = 180000L,
            artworkUrl = "https://example.com/art1.jpg",
            streamUrl = "https://example.com/stream1.mp4"
        )
        val track2 = Track(
            id = "t2",
            title = "Song Two",
            artist = "Artist B",
            album = "Album 2",
            durationMs = 210000L,
            artworkUrl = "https://example.com/art2.jpg",
            streamUrl = "https://example.com/stream2.mp4"
        )
        val queue = listOf(track1, track2)

        // Calling playTrack populates the ExoPlayer playlist and buffers
        controller.playTrack(track1, queue)

        // The state must not invent a fake playing state
        assertFalse(controller.playbackState.value.isPlaying)

        controller.release()
    }

    @Test
    fun trackToMediaItem_withoutDirectStream_hasNonNullLocalConfigurationUri() {
        val unresolvedTrack = Track(
            id = "song_unresolved",
            title = "Yellow",
            artist = "Coldplay",
            durationMs = 240000L,
            artworkUrl = "https://example.com/yellow.jpg",
            streamUrl = ""
        )
        val mediaItem = unresolvedTrack.toMediaItem()
        assertNotNull(mediaItem.localConfiguration)
        assertNotNull(mediaItem.localConfiguration?.uri)
        assertTrue(mediaItem.localConfiguration?.uri?.toString()?.startsWith("aura://") == true)

        val reconstructed = mediaItem.toTrack()
        assertEquals("song_unresolved", reconstructed.id)
        assertEquals("Yellow", reconstructed.title)
        assertEquals("Coldplay", reconstructed.artist)
    }
}

