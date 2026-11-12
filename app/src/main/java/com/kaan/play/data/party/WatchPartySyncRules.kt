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
    private var lastSeekAtMs: Long = 0L

    data class SyncAction(
        val shouldSeek: Boolean,
        val targetPositionMs: Long,
        val isPlaying: Boolean,
        val speed: Float,
    )

    data class ContentTransition(
        val tmdbId: Int,
        val mediaType: String,
        val season: Int?,
        val episode: Int?,
        val title: String,
    )

    fun shouldApplyPacket(packet: PartyControlPacket): Boolean {
        return packet.seq > lastAppliedSeq
    }

    fun computeLatency(sentAtMs: Long, nowMs: Long = timeProvider()): Long {
        val raw = nowMs - sentAtMs
        return raw.coerceIn(0L, MAX_LATENCY_COMPENSATION_MS)
    }

    fun computeTargetPosition(packet: PartyControlPacket, nowMs: Long = timeProvider()): Long {
        val latency = computeLatency(packet.sentAtMs, nowMs)
        val compensated = if (packet.playing) {
            packet.positionMs + (latency * packet.speed).toLong()
        } else {
            packet.positionMs
        }
        return compensated.coerceAtLeast(0L)
    }

    fun evaluateSync(
        localCurrentPositionMs: Long,
        packet: PartyControlPacket,
        nowMs: Long = timeProvider(),
    ): SyncAction? {
        if (!shouldApplyPacket(packet)) return null

        lastAppliedSeq = packet.seq
        val targetPosition = computeTargetPosition(packet, nowMs)
        val drift = abs(localCurrentPositionMs - targetPosition)
        val shouldSeek = drift > SEEK_DRIFT_THRESHOLD_MS && (nowMs - lastSeekAtMs >= MIN_SEEK_INTERVAL_MS)
        if (shouldSeek) {
            lastSeekAtMs = nowMs
        }

        return SyncAction(
            shouldSeek = shouldSeek,
            targetPositionMs = targetPosition,
            isPlaying = packet.playing,
            speed = packet.speed,
        )
    }

    fun checkContentTransition(
        currentTmdbId: Int,
        currentSeason: Int?,
        currentEpisode: Int?,
        packet: PartyContentPacket,
    ): ContentTransition? {
        val matchesCurrent = packet.tmdbId == currentTmdbId &&
            packet.season == currentSeason &&
            packet.episode == currentEpisode

        return if (!matchesCurrent) {
            ContentTransition(
                tmdbId = packet.tmdbId,
                mediaType = packet.mediaType,
                season = packet.season,
                episode = packet.episode,
                title = packet.title,
            )
        } else {
            null
        }
    }

    fun createControlPacket(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float = 1.0f,
        nowMs: Long = timeProvider(),
    ): PartyControlPacket {
        val seq = ++nextSeq
        return PartyControlPacket(
            seq = seq,
            positionMs = positionMs.coerceAtLeast(0L),
            playing = isPlaying,
            speed = speed.coerceIn(0.25f, 4.0f),
            sentAtMs = nowMs,
        )
    }

    fun reset() {
        lastAppliedSeq = -1L
        nextSeq = 0L
        lastSeekAtMs = 0L
    }

    companion object {
        const val MAX_LATENCY_COMPENSATION_MS = 3000L
        const val SEEK_DRIFT_THRESHOLD_MS = 2000L
        const val MIN_SEEK_INTERVAL_MS = 1000L
    }
}
