package com.example.core.network.innertube

import com.example.core.common.Resource
import com.example.core.model.Album
import com.example.core.model.Artist
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.core.network.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.core.network.saavn.SaavnMediaResolver
import com.example.core.util.ArtworkUtils
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real YouTube Music Innertube provider implementation.
 *
 * Architectural Reference:
 * Modeled after the Echo Music (https://github.com/EchoMusicApp/Echo-Music.git)
 * Innertube module for querying YouTube Music's backend (WEB_REMIX client context).
 *
 * Compatibility and Legal Constraints:
 * - Uses the standard Innertube web JSON interfaces to retrieve legitimate public catalog metadata
 *   including track titles, artists, albums, thumbnails, and durations.
 * - Does not bypass DRM or access controls.
 * - Audio playback stream resolution is isolated and subject to YouTube's current verification policies.
 */
class InnertubeMusicSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
) : MusicSource {

    override val sourceId: String = "innertube_ytm"
    override val sourceName: String = "YouTube Music"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val saavnResolver = SaavnMediaResolver(client)
    private val trackCache = ConcurrentHashMap<String, Track>()

    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val ORIGIN = "https://music.youtube.com"
        private const val REFERER = "https://music.youtube.com/"

        // Song filter param for YouTube Music search
        private const val SEARCH_PARAM_SONGS = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"
    }

    private fun buildClientContext(): JSONObject {
        val clientObj = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20240101.01.00")
            put("hl", "en")
            put("gl", "US")
        }
        return JSONObject().apply {
            put("client", clientObj)
        }
    }

    override suspend fun search(query: String): Resource<List<Track>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext Resource.Success(emptyList())
        }

        try {
            val payload = JSONObject().apply {
                put("context", buildClientContext())
                put("query", query.trim())
                put("params", SEARCH_PARAM_SONGS)
            }

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Search request failed with HTTP ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: return@withContext Resource.Error("Empty response received from music provider")

            val json = JSONObject(responseBody)
            val ytTracks = parseSearchResponse(json).toMutableList()

            // If songs filter returned empty, try fallback search without filter
            if (ytTracks.isEmpty()) {
                val fallbackTracks = searchGeneral(query.trim())
                ytTracks.addAll(fallbackTracks)
            }

            // Also query Saavn for tracks with direct playable streams
            val saavnTracks = saavnResolver.searchSongs(query.trim())

            // Cache all discovered tracks for instant streaming lookup
            saavnTracks.forEach { trackCache[it.id] = it }
            ytTracks.forEach { trackCache[it.id] = it }

            // Combine results prioritizing tracks with direct playable streams
            val combined = (saavnTracks + ytTracks).distinctBy {
                "${it.title.lowercase().trim()}_${it.artist.lowercase().trim()}"
            }

            Resource.Success(combined)
        } catch (e: SocketTimeoutException) {
            Resource.Error("Connection timed out while searching. Please check your internet connection.")
        } catch (e: IOException) {
            Resource.Error("Network error: Unable to reach music provider. Please check your connection.")
        } catch (e: Exception) {
            Resource.Error("Failed to parse music provider response: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun searchGeneral(query: String): List<Track> {
        return try {
            val payload = JSONObject().apply {
                put("context", buildClientContext())
                put("query", query)
            }

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            parseSearchResponse(JSONObject(body))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResponse(json: JSONObject): List<Track> {
        val tracks = mutableListOf<Track>()

        val tabs = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()

        if (tabs.length() == 0) return emptyList()

        val tabContent = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until tabContent.length()) {
            val section = tabContent.optJSONObject(i) ?: continue

            // 1. Direct musicShelfRenderer
            val shelf = section.optJSONObject("musicShelfRenderer")
            if (shelf != null) {
                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseTrackFromResponsiveItem(renderer)?.let { tracks.add(it) }
                }
            }

            // 2. itemSectionRenderer containing musicShelfRenderer
            val itemSection = section.optJSONObject("itemSectionRenderer")
            if (itemSection != null) {
                val innerContents = itemSection.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until innerContents.length()) {
                    val innerShelf = innerContents.optJSONObject(j)?.optJSONObject("musicShelfRenderer") ?: continue
                    val items = innerShelf.optJSONArray("contents") ?: JSONArray()
                    for (k in 0 until items.length()) {
                        val item = items.optJSONObject(k) ?: continue
                        val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        parseTrackFromResponsiveItem(renderer)?.let { tracks.add(it) }
                    }
                }
            }

            // 3. musicCardShelfRenderer (top card result)
            val cardShelf = section.optJSONObject("musicCardShelfRenderer")
            if (cardShelf != null) {
                val cardItems = cardShelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until cardItems.length()) {
                    val item = cardItems.optJSONObject(j) ?: continue
                    val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseTrackFromResponsiveItem(renderer)?.let { tracks.add(it) }
                }
            }
        }

        // Deduplicate tracks by id
        return tracks.distinctBy { it.id }
    }

    private fun parseTrackFromResponsiveItem(renderer: JSONObject): Track? {
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null

        var title = ""
        var artist = "Unknown Artist"
        var artistId: String? = null
        var album: String? = null
        var albumId: String? = null
        var durationMs = 0L

        // Column 0: Title
        if (flexColumns.length() > 0) {
            val col0Runs = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (col0Runs != null && col0Runs.length() > 0) {
                title = col0Runs.optJSONObject(0)?.optString("text") ?: ""
            }
        }

        if (title.isBlank()) return null

        // Column 1: Artist, Album, Duration runs
        if (flexColumns.length() > 1) {
            val col1Runs = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            if (col1Runs != null) {
                val texts = mutableListOf<String>()
                for (i in 0 until col1Runs.length()) {
                    val runObj = col1Runs.optJSONObject(i) ?: continue
                    val text = runObj.optString("text")
                    texts.add(text)

                    // Check for artist navigation endpoint
                    val browseEndpoint = runObj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                    val browseId = browseEndpoint?.optString("browseId")
                    if (browseId != null && browseId.startsWith("UC")) {
                        artistId = browseId
                    } else if (browseId != null && (browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_library_privately_owned_release_detail"))) {
                        albumId = browseId
                    }
                }

                val fullText = texts.joinToString("")
                val parts = fullText.split("•").map { it.trim() }

                if (parts.isNotEmpty()) {
                    artist = parts[0].ifBlank { "Unknown Artist" }
                }

                if (parts.size == 2) {
                    if (parts[1].contains(":")) {
                        durationMs = parseDurationMs(parts[1])
                    } else {
                        album = parts[1]
                    }
                } else if (parts.size >= 3) {
                    album = parts[1]
                    durationMs = parseDurationMs(parts[2])
                }
            }
        }

        // Thumbnails: Get the highest available resolution
        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")

        var artworkUrl = ""
        if (thumbnails != null && thumbnails.length() > 0) {
            val lastThumb = thumbnails.optJSONObject(thumbnails.length() - 1)
            artworkUrl = lastThumb?.optString("url") ?: ""
        }
        val highResArtwork = ArtworkUtils.getHighResArtworkUrl(artworkUrl)

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album ?: title,
            albumId = albumId,
            durationMs = if (durationMs > 0L) durationMs else 210000L,
            artworkUrl = highResArtwork,
            streamUrl = "https://www.youtube.com/watch?v=$videoId",
            isFavorite = false
        )
    }

    private fun parseDurationMs(durationStr: String): Long {
        return try {
            val parts = durationStr.trim().split(":")
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].trim().toLong()
                    val seconds = parts[1].trim().toLong()
                    (minutes * 60 + seconds) * 1000
                }
                3 -> {
                    val hours = parts[0].trim().toLong()
                    val minutes = parts[1].trim().toLong()
                    val seconds = parts[2].trim().toLong()
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun getFeaturedTracks(): Resource<List<Track>> = withContext(Dispatchers.IO) {
        // Retrieve popular trending tracks via search query to ensure reliable track playback entries
        search("Top Global Hits")
    }

    override suspend fun getTrendingTracks(): Resource<List<Track>> = withContext(Dispatchers.IO) {
        search("Trending Music Charts")
    }

    override suspend fun getRecommendedPlaylists(): Resource<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", buildClientContext())
                put("browseId", "FEmusic_home")
            }

            val request = Request.Builder()
                .url("$BASE_URL/browse")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Browse failed with HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext Resource.Error("Empty response")
            val json = JSONObject(body)
            val playlists = parsePlaylistsFromBrowse(json)
            Resource.Success(playlists)
        } catch (e: Exception) {
            Resource.Error("Failed to load curated playlists: ${e.localizedMessage}")
        }
    }

    private fun parsePlaylistsFromBrowse(json: JSONObject): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        val tabs = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()

        if (tabs.length() == 0) return emptyList()

        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val carousel = section.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val items = carousel.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val itemObj = items.optJSONObject(j) ?: continue
                val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer") ?: continue

                val title = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                val subtitle = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                val browseId = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: "pl_$j"

                val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val coverUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                } else ""

                if (title.isNotBlank()) {
                    playlists.add(
                        Playlist(
                            id = browseId,
                            name = title,
                            description = subtitle.ifBlank { "Curated by YouTube Music" },
                            coverUrl = coverUrl,
                            trackCount = 20,
                            tracks = emptyList()
                        )
                    )
                }
            }
        }
        return playlists.distinctBy { it.id }
    }

    override suspend fun getTopArtists(): Resource<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", buildClientContext())
                put("browseId", "FEmusic_charts")
            }

            val request = Request.Builder()
                .url("$BASE_URL/browse")
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Charts request failed with HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext Resource.Error("Empty response")
            val json = JSONObject(body)
            val artists = parseArtistsFromCharts(json)
            Resource.Success(artists)
        } catch (e: Exception) {
            Resource.Error("Failed to load top artists: ${e.localizedMessage}")
        }
    }

    private fun parseArtistsFromCharts(json: JSONObject): List<Artist> {
        val artists = mutableListOf<Artist>()
        val tabs = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return emptyList()

        if (tabs.length() == 0) return emptyList()

        val sections = tabs.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val carousel = section.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val items = carousel.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val itemObj = items.optJSONObject(j) ?: continue
                val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer") ?: continue

                val name = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                val browseId = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: "artist_$j"

                val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val artworkUrl = if (thumbs != null && thumbs.length() > 0) {
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                } else ""

                if (name.isNotBlank()) {
                    artists.add(
                        Artist(
                            id = browseId,
                            name = name,
                            artworkUrl = artworkUrl,
                            monthlyListeners = 0L,
                            bio = "Artist on YouTube Music"
                        )
                    )
                }
            }
        }
        return artists.distinctBy { it.id }
    }

    override suspend fun getStreamUrl(trackId: String): Resource<String> = withContext(Dispatchers.IO) {
        val cached = trackCache[trackId]
        if (cached != null) {
            if (cached.streamUrl.isNotBlank() && !cached.streamUrl.contains("youtube.com/watch")) {
                return@withContext Resource.Success(cached.streamUrl)
            }
            val resolved = saavnResolver.resolveStreamUrl(cached.title, cached.artist)
            if (!resolved.isNullOrBlank()) {
                return@withContext Resource.Success(resolved)
            }
        }
        Resource.Error("Audio stream resolution unavailable for track $trackId")
    }

    override suspend fun getAlbum(albumId: String): Resource<Album> = withContext(Dispatchers.IO) {
        Resource.Error("Album browsing not yet implemented")
    }

    override suspend fun getArtist(artistId: String): Resource<Artist> = withContext(Dispatchers.IO) {
        Resource.Error("Artist details browsing not yet implemented")
    }
}
