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
 * Listening history and scoring stay on-device. The existing MusicRepository is only
 * used to search for candidate songs around the strongest local seeds; the full history
 * is never uploaded anywhere.
 */
class LocalRecommendationEngine(
    private val musicRepository: MusicRepository,
    private val historyRepository: ListeningHistoryRepository
) {
    suspend fun build(limit: Int = 24): PersonalizedRecommendations {
        val history = historyRepository.stats.value
        if (history.isEmpty()) return PersonalizedRecommendations(emptyList(), null)

        val now = System.currentTimeMillis()
        val seeds = history.values
            .sortedByDescending { RecommendationScorer.preferenceScore(it, now) }
            .take(MAX_SEEDS)

        if (seeds.isEmpty()) return PersonalizedRecommendations(emptyList(), null)

        val candidates = LinkedHashMap<String, Candidate>()

        seeds.forEachIndexed { seedIndex, seed ->
            val seedWeight = RecommendationScorer.preferenceScore(seed, now)
            val queries = buildQueries(seed.track)

            queries.forEach { query ->
                when (val result = musicRepository.search(query)) {
                    is Resource.Success -> {
                        result.data.take(SEARCH_RESULTS_PER_QUERY).forEachIndexed { rank, track ->
                            if (track.id == seed.track.id) return@forEachIndexed

                            val score = RecommendationScorer.candidateScore(
                                candidate = track,
                                seed = seed,
                                seedWeight = seedWeight,
                                searchRank = rank,
                                existingStat = history[track.id],
                                seedIndex = seedIndex
                            )

                            val previous = candidates[track.id]
                            if (previous == null) {
                                candidates[track.id] = Candidate(track, score)
                            } else {
                                // Multiple strong seeds agreeing on the same song is a useful signal.
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

        val sorted = candidates.values.sortedByDescending { it.score }
        val selected = diversify(sorted, limit)

        return PersonalizedRecommendations(
            tracks = selected,
            seedTitle = seeds.firstOrNull()?.track?.title
        )
    }

    private fun buildQueries(track: Track): List<String> {
        val queries = linkedSetOf<String>()

        if (track.artist.isNotBlank()) {
            queries += track.artist.trim()
        }
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

        // Most results should be highly familiar/relevant, but prevent one artist from
        // swallowing the entire Home page.
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
        private const val MAX_SEEDS = 5
        private const val MAX_QUERIES_PER_SEED = 3
        private const val SEARCH_RESULTS_PER_QUERY = 12
        private const val MAX_TRACKS_PER_ARTIST = 4
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
            (if (stat.track.isFavorite) 30.0 else 0.0) +
                (if (stat.track.isDownloaded) 18.0 else 0.0)

        return playSignal + completionSignal + listeningSignal + recency + explicitSignal - skipPenalty
    }

    fun candidateScore(
        candidate: Track,
        seed: ListeningStat,
        seedWeight: Double,
        searchRank: Int,
        existingStat: ListeningStat?,
        seedIndex: Int
    ): Double {
        var score = 0.0

        val seedArtist = seed.track.artist.trim().lowercase()
        val candidateArtist = candidate.artist.trim().lowercase()
        if (seedArtist.isNotBlank() && seedArtist == candidateArtist) score += 52.0

        val seedAlbum = seed.track.album?.trim()?.lowercase()
        val candidateAlbum = candidate.album?.trim()?.lowercase()
        if (!seedAlbum.isNullOrBlank() && seedAlbum == candidateAlbum) score += 18.0

        score += titleSimilarity(candidate.title, seed.track.title) * 20.0
        score += max(0, 24 - searchRank) * 2.0

        // Stronger seeds influence more, but cap them so one obsession doesn't fully dominate.
        score += min(seedWeight, 350.0) * 0.18
        score -= seedIndex * 4.0

        if (existingStat != null) {
            // Familiar tracks are allowed, but repeated positive behavior helps more than mere history.
            score += min(preferenceScore(existingStat), 220.0) * 0.16
            score -= min(existingStat.skipCount, 6) * 8.0
        } else {
            // Small discovery bonus keeps the list from becoming only songs already played.
            score += 12.0
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

    private fun tokenize(value: String): Set<String> = value
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 2 }
        .toSet()
}
