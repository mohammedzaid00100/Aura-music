package com.example.core.network.saavn

import com.example.core.model.Track
import com.example.core.util.ArtworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * High-fidelity streaming media resolver utilizing JioSaavn's public CDN services.
 *
 * Provides real playable PCM audio streams (160kbps AAC in MP4 container)
 * and high-resolution 500x500 album artwork.
 */
class SaavnMediaResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val BASE_SEARCH_URL =
            "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=5&p=1"
        private const val DES_KEY = "38346591"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Decrypts JioSaavn DES-ECB encrypted media URLs into direct playable AAC/MP4 stream URLs.
     */
    fun decryptMediaUrl(encryptedUrl: String): String {
        return try {
            val key = DES_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(key, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val decodedBytes = try {
                java.util.Base64.getDecoder().decode(encryptedUrl.trim())
            } catch (t: Throwable) {
                android.util.Base64.decode(encryptedUrl.trim(), android.util.Base64.DEFAULT)
            }

            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8)

            // Upgrade bitrate to 160kbps for pristine audio fidelity
            rawUrl.replace("_96.mp4", "_160.mp4")
                .replace("_48.mp4", "_160.mp4")
                .replace("_12.mp4", "_160.mp4")
        } catch (e: Exception) {
            android.util.Log.e("SaavnMediaResolver", "Decryption failed: ${e.message}")
            ""
        }
    }

    /**
     * Resolves a direct playable stream URL for a given track title and artist.
     */
    suspend fun resolveStreamUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(title)
        val cleanArtist = cleanArtistName(artist)
        val query = "$cleanTitle $cleanArtist".trim()

        if (query.isBlank()) return@withContext null

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_SEARCH_URL&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext null

            if (results.length() == 0) {
                // Try searching title alone if title+artist had no results
                if (cleanTitle.isNotBlank() && cleanTitle != query) {
                    return@withContext resolveStreamUrl(cleanTitle, "")
                }
                return@withContext null
            }

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val moreInfo = item.optJSONObject("more_info") ?: continue
                val encryptedUrl = moreInfo.optString("encrypted_media_url")
                if (encryptedUrl.isNotBlank()) {
                    val decrypted = decryptMediaUrl(encryptedUrl)
                    if (decrypted.isNotBlank() && decrypted.startsWith("http")) {
                        return@withContext decrypted
                    }
                }
            }

            null
        } catch (e: Exception) {
            android.util.Log.w("SaavnMediaResolver", "Failed to resolve stream: ${e.message}")
            null
        }
    }

    /**
     * Searches tracks and returns them with pre-decrypted direct playable streams and 500x500 artwork.
     */
    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE_SEARCH_URL&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext emptyList()

            val tracks = mutableListOf<Track>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = unescapeHtml(item.optString("title"))
                val artist = unescapeHtml(item.optString("subtitle"))
                val rawImage = item.optString("image")
                val artworkUrl = ArtworkUtils.getHighResArtworkUrl(rawImage)

                val moreInfo = item.optJSONObject("more_info")
                val durationSec = moreInfo?.optString("duration")?.toLongOrNull() ?: 210L
                val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url") ?: ""

                val streamUrl = if (encryptedMediaUrl.isNotBlank()) {
                    decryptMediaUrl(encryptedMediaUrl)
                } else ""

                if (id.isNotBlank() && title.isNotBlank() && streamUrl.isNotBlank()) {
                    tracks.add(
                        Track(
                            id = "saavn_$id",
                            title = title,
                            artist = if (artist.isNotBlank()) artist else "Unknown Artist",
                            album = title,
                            durationMs = durationSec * 1000L,
                            artworkUrl = artworkUrl,
                            streamUrl = streamUrl,
                            sourceId = "saavn",
                            isFavorite = false
                        )
                    )
                }
            }
            tracks
        } catch (e: Exception) {
            android.util.Log.e("SaavnMediaResolver", "Error searching Saavn: ${e.message}")
            emptyList()
        }
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(?i)official\\s+video|official\\s+audio|lyrics|lyric\\s+video"), "")
            .replace(Regex("[-–—|].*$"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex(",.*$"), "")
            .replace(Regex("(?i)\\bft\\.?\\b.*$"), "")
            .replace(Regex("(?i)\\bfeat\\.?\\b.*$"), "")
            .trim()
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
