package com.kaan.play.data.notify

import android.content.Context
import com.kaan.play.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Arka planda tüm profillerin benzersiz favori dizilerini (en çok 40) denetler.
 * Ağ hatası sessizce atlanır; URL/TMDB anahtarı loglanmaz. Süre 25 sn ile sınırlıdır.
 */
object NewEpisodeChecker {
    private const val CHECK_TIMEOUT_MS = 25_000L

    suspend fun run(context: Context, container: AppContainer) {
        val enabled = runCatching { container.settings.newEpisodeNotifications.first() }.getOrDefault(false)
        if (!enabled) return

        val favorites = runCatching {
            container.library.distinctFavoriteSeries(NewEpisodeRules.MAX_SERIES_PER_CHECK)
        }.getOrDefault(emptyList())
        if (favorites.isEmpty()) return

        val beforeMap = runCatching { container.settings.readNotifiedEpisodes() }.getOrDefault(emptyMap())
        val updatedMap = beforeMap.toMutableMap()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val semaphore = Semaphore(3)

        val fetched = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            coroutineScope {
                favorites.map { ref ->
                    async {
                        semaphore.withPermit {
                            val latest = try {
                                container.tmdb.latestAiredEpisode(ref.tmdbId)
                            } catch (_: Throwable) {
                                null
                            }
                            ref to latest
                        }
                    }
                }.awaitAll()
            }
        } ?: return

        fetched.forEach { (ref, latest) ->
            when (val decision = NewEpisodeRules.evaluateSeries(updatedMap[ref.tmdbId], latest, todayIso)) {
                is EpisodeCheckDecision.RecordBaseline -> {
                    updatedMap[ref.tmdbId] = StoredEpisodeState(decision.season, decision.episode)
                }
                is EpisodeCheckDecision.NotifyNewEpisode -> {
                    updatedMap[ref.tmdbId] = StoredEpisodeState(decision.season, decision.episode)
                    val title = latest?.seriesTitle?.ifBlank { ref.title } ?: ref.title
                    NewEpisodeNotifier.notify(
                        context = context,
                        tmdbId = ref.tmdbId,
                        seriesTitle = title,
                        season = decision.season,
                        episode = decision.episode,
                    )
                }
                EpisodeCheckDecision.NoChange -> Unit
            }
        }

        if (updatedMap != beforeMap) {
            runCatching { container.settings.saveNotifiedEpisodes(updatedMap) }
        }
    }
}
