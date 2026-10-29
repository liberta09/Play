package com.kaan.play.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteBackup(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterPath: String?,
    val addedAt: Long,
)

@Serializable
data class HistoryBackup(
    val tmdbId: Int,
    val type: String,
    val season: Int = 0,
    val episode: Int = 0,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val title: String,
    val posterPath: String?,
    val pluginId: String? = null,
    val dataUrl: String? = null,
    val contentUrl: String? = null,
)

@Serializable
data class LocalProfileBackup(
    val profileId: String = "",
    val profileName: String = "",
    val exportedAt: Long = 0L,
    val format: Int = 1,
    val favorites: List<FavoriteBackup> = emptyList(),
    val history: List<HistoryBackup> = emptyList(),
)
