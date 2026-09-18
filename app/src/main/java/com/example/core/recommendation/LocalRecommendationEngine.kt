package com.example.core.recommendation

import com.example.core.common.Resource
import com.example.core.domain.repository.MusicRepository
import com.example.core.model.Track
import kotlin.math.max
import kotlin.math.min


data class PersonalizedRecommendations(
    val tracks: List<Track>,
    val seedTitle: String?
)

/**
 * One-user recommendation engine.
 *
 * Listening history and explicit like/dislike preferences stay on-device. The existing
 * MusicRepository is only used to search for candidate songs around strong local seeds.
 */
class LocalRecommendationEngine(
    private val musicRepository: MusicRepository,
    private val historyRepository: ListeningHistoryRepository,
    private val preferenceRepository: TrackPreferenceRepository
) {
    suspend fun build(limit: Int = 24): PersonalizedRecommendations {
        val history = historyRepository.stats.value
        val preferences = preferenceRepository.state.value

        if (history.isEmpty() && preferences.liked.isEmpty()) {
            return PersonalizedRecommendations(emptyList(), null)
        }

        val now = System.currentTimeMillis()
        val dislikedIds = preferences.disliked.keys

        val seedMap = LinkedHashMap<String, ListeningStat>()

        history.values
            .filterNot { dislikedIds.contains(it.track.id) }
            .forEach { stat ->
                seedMap[stat.track.id] = stat.copy(
                    track = stat.track.copy(isFavorite = preferences.liked.containsKey(stat.track.id))
                )
            }

        // A direct Like is intentionally strong enough to become a recommendation seed even
        // before the user has replayed the song many times.
        preferences.liked.values.forEach { likedTrack ->
            val existing = seedMap[likedTrack.id]
            seedMap[likedTrack.id] = if (existing != null) {
                existing.copy(track = existing.track.copy(isFavorite = true))
            } else {
                ListeningStat(
                    track = likedTrack.copy(isFavorite = true),
                    playCount = 1,
                    completedCount = 1,
                    lastPlayedAt = now
                )
            }
        }

        val seeds = seedMap.values
            .sortedByDescending { stat ->
                RecommendationScorer.preferenceScore(stat, now) +
                    if (preferences.liked.containsKey(stat.track.id)) EXPLICIT_LIKE_SEED_BONUS else 0.0
            }
            .take(MAX_SEEDS)

        if (seeds.isEmpty()) return PersonalizedRecommendations(emptyList(), null)

        val candidates = LinkedHashMap<String, Candidate>()
        val dislikedTracks = preferences.disliked.values.toList()

        seeds.forEachIndexed { seedIndex, seed ->
            val isExplicitlyLikedSeed = preferences.liked.containsKey(seed.track.id)
            val seedWeight = RecommendationScorer.preferenceScore(seed, now) +
                if (isExplicitlyLikedSeed) EXPLICIT_LIKE_SEED_BONUS else 0.0

            buildQueries(seed.track).forEach { query ->
                when (val result = musicRepository.search(query)) {
                    is Resource.Success -> {
                        result.data.take(SEARCH_RESULTS_PER_QUERY).forEachIndexed { rank, rawTrack ->
                            if (rawTrack.id == seed.track.id || dislikedIds.contains(rawTrack.id)) {
                                return@forEachIndexed
                            }

                            val track = rawTrack.copy(
                                isFavorite = preferences.liked.containsKey(rawTrack.id)
                            )

                            val score = RecommendationScorer.candidateScore(
                                candidate = track,
                                seed = seed,
                                seedWeight = seedWeight,
                                searchRank = rank,
                                existingStat = history[track.id],
                                seedIndex = seedIndex,
                                isExplicitlyLiked = preferences.liked.containsKey(track.id),
                                dislikedTracks = dislikedTracks
                            )

                            val previous = candidates[track.id]
                            if (previous == null) {
                                candidates[track.id] = Candidate(track, score)
                            } else {
                                // Agreement across several positive seeds is a strong signal.
                                candidates[track.id] = previous.copy(
                                    score = max(previous.score, score) + min(previous.score, score) * 0.18
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        val sorted = candidates.values
            .filter { it.score > MIN_RECOMMENDATION_SCORE }
            .sortedByDescending { it.score }
        val selected = diversify(sorted, limit)

        return PersonalizedRecommendations(
            tracks = selected,
            seedTitle = seeds.firstOrNull()?.track?.title
        )
    }

    private fun buildQueries(track: Track): List<String> {
        val queries = linkedSetOf<String>()

        if (track.artist.isNotBlank()) queries += track.artist.trim()
        if (track.title.isNotBlank() && track.artist.isNotBlank()) {
            queries += "${track.title.trim()} ${track.artist.trim()}"
        }
        track.album?.takeIf { it.isNotBlank() }?.let { album ->
            queries += "${track.artist.trim()} ${album.trim()}".trim()
        }

        return queries.take(MAX_QUERIES_PER_SEED)
    }

    private fun diversify(sorted: List<Candidate>, limit: Int): List<Track> {
        if (sorted.isEmpty()) return emptyList()

        val result = ArrayList<Track>(limit)
        val artistCounts = HashMap<String, Int>()

        sorted.forEach { candidate ->
            if (result.size >= limit) return@forEach
            val artistKey = candidate.track.artist.trim().lowercase()
            val count = artistCounts[artistKey] ?: 0
            if (artistKey.isBlank() || count < MAX_TRACKS_PER_ARTIST) {
                result += candidate.track
                if (artistKey.isNotBlank()) artistCounts[artistKey] = count + 1
            }
        }

        if (result.size < limit) {
            sorted.asSequence()
                .map { it.track }
                .filter { track -> result.none { it.id == track.id } }
                .take(limit - result.size)
                .forEach(result::add)
        }

        return result
    }

    private data class Candidate(val track: Track, val score: Double)

    companion object {
        private const val MAX_SEEDS = 6
        private const val MAX_QUERIES_PER_SEED = 3
        private const val SEARCH_RESULTS_PER_QUERY = 12
        private const val MAX_TRACKS_PER_ARTIST = 4
        private const val EXPLICIT_LIKE_SEED_BONUS = 180.0
        private const val MIN_RECOMMENDATION_SCORE = -40.0
    }
}

/** Pure scoring logic kept separate so it can be regression-tested without Android. */
object RecommendationScorer {
    fun preferenceScore(stat: ListeningStat, now: Long = System.currentTimeMillis()): Double {
        val ageMs = (now - stat.lastPlayedAt).coerceAtLeast(0L)
        val ageDays = ageMs.toDouble() / 86_400_000.0
        val recency = 80.0 / (1.0 + ageDays / 3.0)

        val playSignal = min(stat.playCount, 25) * 10.0
        val completionSignal = min(stat.completedCount, 20) * 14.0
        val skipPenalty = min(stat.skipCount, 20) * 13.0

        val duration = stat.track.durationMs.coerceAtLeast(1L)
        val equivalentFullPlays = stat.totalListenMs.toDouble() / duration.toDouble()
        val listeningSignal = min(equivalentFullPlays, 12.0) * 7.0

        val explicitSignal =
            (if (stat.track.isFavorite) 80.0 else 0.0) +
                (if (stat.track.isDownloaded) 18.0 else 0.0)

        return playSignal + completionSignal + listeningSignal + recency + explicitSignal - skipPenalty
    }

    fun candidateScore(
        candidate: Track,
        seed: ListeningStat,
        seedWeight: Double,
        searchRank: Int,
        existingStat: ListeningStat?,
        seedIndex: Int,
        isExplicitlyLiked: Boolean,
        dislikedTracks: List<Track>
    ): Double {
        var score = 0.0

        val seedArtist = normalized(seed.track.artist)
        val candidateArtist = normalized(candidate.artist)
        if (seedArtist.isNotBlank() && seedArtist == candidateArtist) score += 52.0

        val seedAlbum = seed.track.album?.let(::normalized)
        val candidateAlbum = candidate.album?.let(::normalized)
        if (!seedAlbum.isNullOrBlank() && seedAlbum == candidateAlbum) score += 18.0

        score += titleSimilarity(candidate.title, seed.track.title) * 20.0
        score += max(0, 24 - searchRank) * 2.0

        score += min(seedWeight, 500.0) * 0.20
        score -= seedIndex * 4.0

        if (isExplicitlyLiked) score += 160.0

        if (existingStat != null) {
            score += min(preferenceScore(existingStat), 260.0) * 0.16
            score -= min(existingStat.skipCount, 6) * 8.0
        } else {
            score += 12.0
        }

        // Explicit dislikes are stronger than passive skips. The exact track is already
        // filtered out; these penalties reduce close relatives without banning a whole genre.
        dislikedTracks.forEach { disliked ->
            if (candidateArtist.isNotBlank() && candidateArtist == normalized(disliked.artist)) {
                score -= 48.0
            }
            val dislikedAlbum = disliked.album?.let(::normalized)
            if (!candidateAlbum.isNullOrBlank() && !dislikedAlbum.isNullOrBlank() && candidateAlbum == dislikedAlbum) {
                score -= 28.0
            }
            score -= titleSimilarity(candidate.title, disliked.title) * 24.0
        }

        return score
    }

    private fun titleSimilarity(a: String, b: String): Double {
        val aTokens = tokenize(a)
        val bTokens = tokenize(b)
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
        val intersection = aTokens.intersect(bTokens).size.toDouble()
        val union = aTokens.union(bTokens).size.toDouble().coerceAtLeast(1.0)
        return intersection / union
    }

    private fun normalized(value: String): String = value.trim().lowercase()

    private fun tokenize(value: String): Set<String> = value
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 2 }
        .toSet()
}
