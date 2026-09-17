package com.example

import com.example.core.common.Resource
import com.example.core.network.innertube.InnertubeMusicSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InnertubeSearchIntegrationTest {

    private val musicSource = InnertubeMusicSource()

    @Test
    fun testEmptyQueryReturnsEmptyList() = runBlocking {
        val result = musicSource.search("")
        assertTrue("Empty query should return Success", result is Resource.Success)
        val data = (result as Resource.Success).data
        assertTrue("Data should be empty for blank query", data.isEmpty())
    }

    @Test
    fun testRealSearchArijitSinghReturnsActualTracks() = runBlocking {
        val result = musicSource.search("Arijit Singh")
        if (result is Resource.Error) {
            System.err.println("Search error received: ${result.message}")
        }
        assertTrue("Search should succeed but was $result", result is Resource.Success)
        val tracks = (result as Resource.Success).data
        assertFalse("Search results should not be empty", tracks.isEmpty())

        val first = tracks.first()
        assertNotNull("Track ID must be present", first.id)
        assertTrue("Track ID must not be blank", first.id.isNotBlank())
        assertTrue("Track title must not be blank", first.title.isNotBlank())
        assertTrue("Track artist must not be blank", first.artist.isNotBlank())
        assertTrue("Artwork URL must be a valid link", first.artworkUrl.startsWith("http"))
        assertTrue("Duration must be greater than 0", first.durationMs > 0)
    }
}
