package com.example

import com.example.core.util.ArtworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUtilsTest {

    @Test
    fun getHighResArtworkUrl_upgradesJioSaavn150x150To500x500() {
        val original = "https://c.saavncdn.com/123/TrackName-150x150.jpg"
        val upgraded = ArtworkUtils.getHighResArtworkUrl(original)
        assertEquals("https://c.saavncdn.com/123/TrackName-500x500.jpg", upgraded)
    }

    @Test
    fun getHighResArtworkUrl_upgradesJioSaavn50x50To500x500() {
        val original = "https://c.saavncdn.com/456/Artist-50x50.jpg"
        val upgraded = ArtworkUtils.getHighResArtworkUrl(original)
        assertEquals("https://c.saavncdn.com/456/Artist-500x500.jpg", upgraded)
    }

    @Test
    fun getHighResArtworkUrl_upgradesYouTubeMusicDimensions() {
        val original = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        val upgraded = ArtworkUtils.getHighResArtworkUrl(original)
        assertTrue(upgraded.contains("=w800-h800"))
    }

    @Test
    fun getHighResArtworkUrl_upgradesYouTubeVideoThumbnails() {
        val original = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val upgraded = ArtworkUtils.getHighResArtworkUrl(original)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", upgraded)
    }

    @Test
    fun selectHighestResolution_picksAndUpgradesLastCandidate() {
        val thumbnails = listOf(
            "https://c.saavncdn.com/123/Track-50x50.jpg",
            "https://c.saavncdn.com/123/Track-150x150.jpg"
        )
        val selected = ArtworkUtils.selectHighestResolution(thumbnails)
        assertEquals("https://c.saavncdn.com/123/Track-500x500.jpg", selected)
    }
}
