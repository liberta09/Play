package com.kaan.play.data.party

import kotlin.math.abs

/**
 * Watch Party oynatıcı senkronizasyon mantığı (saf Kotlin, Android bağımlılığı yok).
 */
class WatchPartySyncRules(
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    var lastAppliedSeq: Long = -1L
        private set

    private var nextSeq: Long = 0L

    data class SyncAction(
        val shouldSeek: Boolean,
        val targetPositionMs: Long,
        val isPlaying: Boolean,
        val speed: Float,
    )

    data class ContentTransition(
        val tmdbId: Int,
        val season: Int?,
        val episode: Int?,
    )

    fun shouldApplyPacket(packet: PartySyncPacket): Boolean {
        return packet.seq > lastAppliedSeq
    }

    fun computeLatency(sentAt: Long, nowMs: Long = timeProvider()): Long {
        val raw = nowMs - sentAt
        return raw.coerceIn(0L, MAX_LATENCY_COMPENSATION_MS)
    }

    fun computeTargetPosition(packet: PartySyncPacket, nowMs: Long = timeProvider()): Long {
        val latency = computeLatency(packet.sentAt, nowMs)
        val compensated = if (packet.isPlaying) {
            packet.positionMs + (latency * packet.speed).toLong()
        } else {
            packet.positionMs
        }
        return compensated.coerceAtLeast(0L)
    }

    fun evaluateSync(
        localCurrentPositionMs: Long,
        packet: PartySyncPacket,
        nowMs: Long = timeProvider(),
    ): SyncAction? {
        if (!shouldApplyPacket(packet)) return null

        lastAppliedSeq = packet.seq
        val targetPosition = computeTargetPosition(packet, nowMs)
        val drift = abs(localCurrentPositionMs - targetPosition)
        val shouldSeek = drift > SEEK_DRIFT_THRESHOLD_MS

        return SyncAction(
            shouldSeek = shouldSeek,
            targetPositionMs = targetPosition,
            isPlaying = packet.isPlaying,
            speed = packet.speed,
        )
    }

    fun checkContentTransition(
        currentTmdbId: Int,
        currentSeason: Int?,
        currentEpisode: Int?,
        packet: PartySyncPacket,
    ): ContentTransition? {
        val targetTmdb = packet.tmdbId ?: return null
        val matchesCurrent = targetTmdb == currentTmdbId &&
            packet.season == currentSeason &&
            packet.episode == currentEpisode

        return if (!matchesCurrent) {
            ContentTransition(
                tmdbId = targetTmdb,
                season = packet.season,
                episode = packet.episode,
            )
        } else {
            null
        }
    }

    fun createHostPacket(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float = 1.0f,
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        nowMs: Long = timeProvider(),
    ): PartySyncPacket {
        val seq = ++nextSeq
        return PartySyncPacket(
            seq = seq,
            sentAt = nowMs,
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            speed = speed.coerceIn(0.25f, 4.0f),
            tmdbId = tmdbId,
            season = season,
            episode = episode,
        )
    }

    fun reset() {
        lastAppliedSeq = -1L
        nextSeq = 0L
    }

    companion object {
        const val MAX_LATENCY_COMPENSATION_MS = 3000L
        const val SEEK_DRIFT_THRESHOLD_MS = 2000L
    }
}
