package com.kaan.play.data.player

/**
 * Oynatıcı ilk kareyi çizene dek arka planda gösterilecek soluk görselin seçim ve görünürlük kuralları.
 */
object PreplayArtworkRules {

    fun selectPath(
        enabled: Boolean,
        isLive: Boolean,
        backdropPath: String?,
        posterPath: String?,
    ): String? {
        if (!enabled) return null
        val backdrop = backdropPath?.trim()?.takeIf { it.isNotEmpty() }
        val poster = posterPath?.trim()?.takeIf { it.isNotEmpty() }
        return if (isLive) {
            poster ?: backdrop
        } else {
            backdrop ?: poster
        }
    }

    fun shouldShow(
        enabled: Boolean,
        artworkUrl: String?,
        firstFrameRendered: Boolean,
        showTorrentOverlay: Boolean,
        hasError: Boolean,
    ): Boolean = enabled && !artworkUrl.isNullOrBlank() && !firstFrameRendered && !showTorrentOverlay && !hasError
}
