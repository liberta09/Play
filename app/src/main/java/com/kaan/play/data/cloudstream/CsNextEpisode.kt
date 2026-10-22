package com.kaan.play.data.cloudstream

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** CloudStream dizisinde bir bölüm: sezon/bölüm numarası eklentide boş olabilir, [data] oynatma anahtarıdır. */
data class CsEpisodeRef(val season: Int?, val episode: Int?, val data: String)

/** Sıradaki bölüm: oynatma için sezon, bölüm ve eklentinin bölüm verisi (`dataUrl`). */
data class CsNextEpisode(val season: Int, val episode: Int, val dataUrl: String)

/**
 * CloudStream dizilerinde sıradaki bölümü eklentinin KENDİ bölüm listesinden bulur (TMDB kimliği yoktur).
 * Oynatıcının otomatik sonraki bölümü ve ana sayfa "İzlemeye devam et" aynı yolu kullanır.
 */
class CsNextEpisodeFinder(private val csRepos: CsRepository) {
    private val cache = HashMap<String, CsNextEpisode?>()

    suspend fun next(pluginId: String, contentUrl: String, dataUrl: String?, season: Int?, episode: Int?): CsNextEpisode? {
        val key = listOf(pluginId, contentUrl, dataUrl, season, episode).joinToString("|")
        synchronized(cache) { if (key in cache) return cache[key] }
        if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.d("CsNextEpisode", "next(): s=$season e=$episode")
        val episodes = load(pluginId, contentUrl)
        if (episodes == null) {
            if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.w("CsNextEpisode", "bölüm listesi alınamadı")
            return null
        }
        val result = pickNext(episodes, dataUrl, season, episode)
        if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.d("CsNextEpisode", "sıradaki: ${result?.season}/${result?.episode} (${episodes.size} bölüm)")
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun load(pluginId: String, contentUrl: String): List<CsEpisodeRef>? = withContext(Dispatchers.IO) {
        val repoId = pluginId.removePrefix("cs:").removePrefix("cs_").toLongOrNull()
        val plugin = csRepos.enabledPlugins().firstOrNull {
            it.id == repoId || "cs:${it.id}" == pluginId || it.pluginId == pluginId
        }
        if (plugin == null) {
            if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.w("CsNextEpisode", "eklenti bulunamadı")
            return@withContext null
        }
        val api = csRepos.loadApis(plugin.id).apisOrEmpty().firstOrNull()
        if (api == null) {
            if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.w("CsNextEpisode", "eklenti API yok")
            return@withContext null
        }
        val response = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            try {
                api.load(contentUrl)
            } catch (t: Throwable) {
                if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.w("CsNextEpisode", "load hatası: ${t.javaClass.simpleName}")
                null
            }
        }
        if (response == null) {
            if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.w("CsNextEpisode", "load boş/zaman aşımı")
            return@withContext null
        }
        val list = when (response) {
            is TvSeriesLoadResponse -> response.episodes
            is AnimeLoadResponse -> response.episodes.values.flatten()
            else -> emptyList()
        }.map { CsEpisodeRef(it.season, it.episode, it.data) }
        if (com.kaan.play.BuildConfig.DEBUG) android.util.Log.d("CsNextEpisode", "${list.size} bölüm")
        list
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L

        /** Android'den bağımsız seçim (birim testli): önce [dataUrl] eşleşmesi, sonra sezon/bölüm numarası. */
        fun pickNext(episodes: List<CsEpisodeRef>, dataUrl: String?, season: Int?, episode: Int?): CsNextEpisode? {
            if (episodes.isEmpty()) return null
            val s = season ?: 1
            val e = episode ?: 1
            var idx = if (!dataUrl.isNullOrBlank()) episodes.indexOfFirst { it.data == dataUrl } else -1
            if (idx == -1) {
                idx = episodes.indices.firstOrNull { i ->
                    (episodes[i].season ?: 1) == s && (episodes[i].episode ?: (i + 1)) == e
                } ?: -1
            }
            val next = if (idx != -1) {
                episodes.getOrNull(idx + 1) ?: return null
            } else {
                episodes.filter {
                    val es = it.season ?: 1
                    val ee = it.episode ?: 0
                    es > s || (es == s && ee > e)
                }.minWithOrNull(compareBy({ it.season ?: 1 }, { it.episode ?: 0 })) ?: return null
            }
            return CsNextEpisode(next.season ?: s, next.episode ?: (e + 1), next.data)
        }
    }
}
