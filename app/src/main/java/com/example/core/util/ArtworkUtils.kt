package com.example.core.util

import java.util.regex.Pattern

/**
 * Utility for resolving and upgrading music artwork URLs to the highest available resolution.
 *
 * Implements the quality priority:
 * HIGH-RES ORIGINAL -> LARGE THUMBNAIL (800x800 / 500x500) -> MEDIUM -> SMALL
 *
 * Handles:
 * - JioSaavn CDN (rewriting 150x150.jpg / 50x50.jpg to 500x500.jpg)
 * - YouTube Music CDN (rewriting =wXX-hXX to =w800-h800 and =sXX to =s800)
 * - YouTube static video thumbnails (rewriting hqdefault.jpg to maxresdefault.jpg)
 */
object ArtworkUtils {

    private val YTM_DIMENSION_PATTERN = Pattern.compile("=w\\d+-h\\d+[^/]*")
    private val YTM_S_PATTERN = Pattern.compile("=s\\d+[^/]*")

    /**
     * Resolves the highest resolution version of an artwork URL.
     */
    fun getHighResArtworkUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var cleaned = url.trim()

        // 1. JioSaavn CDN artwork
        if (cleaned.contains("saavncdn.com")) {
            cleaned = cleaned
                .replace("150x150.jpg", "500x500.jpg")
                .replace("50x50.jpg", "500x500.jpg")
                .replace("150x150.png", "500x500.png")
                .replace("50x50.png", "500x500.png")
            return cleaned
        }

        // 2. YouTube Music / Google UserContent =w...-h...
        val dimMatcher = YTM_DIMENSION_PATTERN.matcher(cleaned)
        if (dimMatcher.find()) {
            return dimMatcher.replaceAll("=w800-h800-l90-rj")
        }

        // 3. YouTube Music / Google UserContent =s...
        if (cleaned.contains("=s") && !cleaned.contains("=s800")) {
            val sMatcher = YTM_S_PATTERN.matcher(cleaned)
            if (sMatcher.find()) {
                return sMatcher.replaceAll("=s800-c-k-c0x00ffffff-no-rj")
            }
        }

        // 4. YouTube Static Video Thumbnails
        if (cleaned.contains("i.ytimg.com") && cleaned.contains("hqdefault.jpg")) {
            return cleaned.replace("hqdefault.jpg", "maxresdefault.jpg")
        }

        return cleaned
    }

    /**
     * Selects the highest resolution thumbnail from a list of URLs or dimensions.
     */
    fun selectHighestResolution(thumbnailUrls: List<String>): String {
        if (thumbnailUrls.isEmpty()) return ""
        // Start with the last item which is typically the largest in Innertube responses
        val candidate = thumbnailUrls.lastOrNull { it.isNotBlank() } ?: thumbnailUrls.first()
        return getHighResArtworkUrl(candidate)
    }
}
