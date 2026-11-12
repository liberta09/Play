package com.kaan.play.data.notify

/** Bir dizinin TMDB'den alınan son yayınlanmış bölümü. */
data class AiredEpisode(
    val season: Int,
    val episode: Int,
    val airDate: String?,
    val seriesTitle: String = "",
) : Comparable<AiredEpisode> {
    override fun compareTo(other: AiredEpisode): Int =
        compareValuesBy(this, other, { it.season }, { it.episode })
}

/** DataStore'da her dizi için son görülen / bildirilen bölüm konumu. */
data class StoredEpisodeState(
    val season: Int,
    val episode: Int,
) {
    operator fun compareTo(other: StoredEpisodeState): Int =
        compareValuesBy(this, other, { it.season }, { it.episode })
}

sealed interface EpisodeCheckDecision {
    /** İlk denetimde mevcut son bölüm sessizce kaydedilir; eski/bilinen bölümde de bildirim atılmaz. */
    data class RecordBaseline(val season: Int, val episode: Int) : EpisodeCheckDecision

    /** Önceki kayda göre gerçekten daha yeni ve bugün/öncesinde yayınlanmış bölüm bulundu. */
    data class NotifyNewEpisode(val season: Int, val episode: Int) : EpisodeCheckDecision

    data object NoChange : EpisodeCheckDecision
}

/** Favori dizilerde yeni bölüm denetimi ve bildirim kuralları (Android bağımsız). */
object NewEpisodeRules {
    const val MAX_SERIES_PER_CHECK = 40
    const val INITIAL_DELAY_MS = 2 * 60_000L
    const val REPEAT_INTERVAL_MS = 12 * 60 * 60_000L

    /** `YYYY-MM-DD` tarihini `YYYYMMDD` tamsayısına çevirir; geçersizse null döner. */
    fun parseIsoDateKey(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.length < 10) return null
        val datePart = text.take(10)
        val parts = datePart.split('-')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y < 1900 || m !in 1..12 || d !in 1..31) return null
        return y * 10_000 + m * 100 + d
    }

    /** [airDate] geçerliyse ve [todayIso] tarihinden ileri değilse true döner. */
    fun isReleasedOnOrBefore(airDate: String?, todayIso: String): Boolean {
        val airKey = parseIsoDateKey(airDate) ?: return false
        val todayKey = parseIsoDateKey(todayIso) ?: return false
        return airKey <= todayKey
    }

    fun evaluateSeries(
        previous: StoredEpisodeState?,
        latest: AiredEpisode?,
        todayIso: String,
    ): EpisodeCheckDecision {
        if (latest == null || latest.season <= 0 || latest.episode <= 0) return EpisodeCheckDecision.NoChange
        if (!isReleasedOnOrBefore(latest.airDate, todayIso)) return EpisodeCheckDecision.NoChange
        if (previous == null) return EpisodeCheckDecision.RecordBaseline(latest.season, latest.episode)
        val latestState = StoredEpisodeState(latest.season, latest.episode)
        return if (latestState > previous) {
            EpisodeCheckDecision.NotifyNewEpisode(latest.season, latest.episode)
        } else {
            EpisodeCheckDecision.NoChange
        }
    }

    fun encodeState(map: Map<Int, StoredEpisodeState>): String =
        map.entries
            .filter { it.key > 0 && it.value.season > 0 && it.value.episode > 0 }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value.season}:${it.value.episode}" }

    fun decodeState(raw: String?): Map<Int, StoredEpisodeState> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<Int, StoredEpisodeState>()
        raw.split(',').forEach { token ->
            val parts = token.trim().split(':')
            if (parts.size == 3) {
                val id = parts[0].toIntOrNull()
                val s = parts[1].toIntOrNull()
                val e = parts[2].toIntOrNull()
                if (id != null && s != null && e != null && id > 0 && s > 0 && e > 0) {
                    result[id] = StoredEpisodeState(s, e)
                }
            }
        }
        return result
    }
}
