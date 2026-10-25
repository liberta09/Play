package com.kaan.play.data.tmdb

import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln1p
import kotlin.math.sqrt

/** Deterministic content-first ordering for the detail page's same-type related titles. */
internal object RelatedMediaRanking {
    private data class Candidate(
        val media: MediaSummary,
        val firstOrder: Int,
        var fromKeywordDiscovery: Boolean = false,
        var fromRecommendations: Boolean = false,
        var fromSimilar: Boolean = false,
    )

    private data class Scored(val media: MediaSummary, val score: Double, val order: Int)

    private val stopWords = setOf(
        "a", "about", "after", "again", "against", "all", "also", "an", "and", "any", "are", "as", "at",
        "be", "because", "been", "before", "being", "between", "both", "but", "by", "can", "could", "did",
        "do", "does", "during", "each", "few", "for", "from", "further", "had", "has", "have", "he", "her",
        "here", "hers", "him", "his", "how", "i", "if", "in", "into", "is", "it", "its", "just", "me",
        "more", "most", "my", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "our",
        "out", "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the", "their",
        "them", "then", "there", "these", "they", "this", "those", "through", "to", "too", "under", "until",
        "up", "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "will", "with",
        "would", "you", "your", "bir", "birkaç", "birçok", "bu", "da", "daha", "de", "diye", "en", "gibi",
        "hem", "her", "için", "ile", "ise", "kadar", "ki", "mı", "mi", "mu", "mü", "ne", "o", "olarak",
        "olan", "oldu", "olduğu", "olmak", "on", "onu", "onun", "ve", "veya", "ya", "yani", "çok", "ama",
        "ancak", "sonra", "önce", "tüm", "kendi", "film", "filmi", "films", "movie", "movies", "series", "dizi",
        "story", "hikaye", "hikâye", "life", "hayat",
    )

    /**
     * Keyword-discovered rows are constrained by source keywords and genres. Similar-only rows need
     * synopsis overlap; TMDB recommendations may additionally use two shared genres as a fallback.
     * Popularity can only break close scores and can never make an unrelated title eligible.
     */
    fun rank(
        type: MediaType,
        sourceId: Int,
        sourceTitle: String,
        sourceOverview: String,
        sourceGenreIds: List<Int>,
        discovered: List<MediaSummary>,
        recommendations: List<MediaSummary>,
        similar: List<MediaSummary>,
        limit: Int = 20,
    ): List<MediaSummary> {
        if (limit <= 0) return emptyList()
        val sourceGenres = sourceGenreIds.filter { it > 0 }.toSet()
        val sourceTokens = tokens("$sourceTitle $sourceOverview")
        val candidates = linkedMapOf<Pair<MediaType, Int>, Candidate>()
        var order = 0

        fun add(items: List<MediaSummary>, mark: (Candidate) -> Unit) {
            items.forEach { item ->
                val candidateOrder = order++
                if (item.type != type || item.id == sourceId || item.title.isBlank()) return@forEach
                val entry = candidates.getOrPut(item.type to item.id) { Candidate(item, candidateOrder) }
                mark(entry)
            }
        }

        add(discovered) { it.fromKeywordDiscovery = true }
        add(recommendations) { it.fromRecommendations = true }
        add(similar) { it.fromSimilar = true }

        return candidates.values.mapNotNull { candidate ->
            val item = candidate.media
            val candidateGenres = item.genreIds.filter { it > 0 }.toSet()
            val sharedGenres = sourceGenres.intersect(candidateGenres).size
            val plotSimilarity = cosine(sourceTokens, tokens("${item.title} ${item.overview}"))
            val hasPlotMatch = plotSimilarity >= MIN_PLOT_SIMILARITY
            val recommendedGenreFallback = candidate.fromRecommendations && sharedGenres >= MIN_SHARED_GENRES
            if (!candidate.fromKeywordDiscovery && !hasPlotMatch && !recommendedGenreFallback) return@mapNotNull null

            val genreUnion = sourceGenres.union(candidateGenres).size
            val genreSimilarity = if (genreUnion == 0) 0.0 else sharedGenres.toDouble() / genreUnion
            val originBonus = (if (candidate.fromKeywordDiscovery) 1.25 else 0.0) +
                (if (candidate.fromRecommendations) 0.35 else 0.0) +
                (if (candidate.fromSimilar) 0.08 else 0.0)
            val ratingTieBreak = (item.rating ?: 0.0).coerceIn(0.0, 10.0) * 0.004
            val popularityTieBreak = ln1p((item.popularity ?: 0.0).coerceAtLeast(0.0)) * 0.003
            Scored(item, originBonus + plotSimilarity * 4.0 + genreSimilarity * 1.2 + ratingTieBreak + popularityTieBreak, candidate.firstOrder)
        }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.order })
            .take(limit)
            .map { it.media }
    }

    private fun tokens(text: String): Set<String> {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
        return WORDS.findAll(normalized)
            .map { it.value }
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in stopWords && it.any(Char::isLetter) }
            .toSet()
    }

    private fun cosine(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size / sqrt(left.size.toDouble() * right.size.toDouble())
    }

    private const val MIN_PLOT_SIMILARITY = 0.08
    private const val MIN_SHARED_GENRES = 2
    private const val MIN_TOKEN_LENGTH = 3
    private val WORDS = Regex("[\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{M}+")
}
