package com.kaan.play.data.party

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PartyRole {
    @SerialName("host") HOST,
    @SerialName("guest") GUEST;

    companion object {
        fun fromString(value: String): PartyRole = when (value.lowercase()) {
            "host" -> HOST
            else -> GUEST
        }
    }
}

@Serializable
data class WatchPartyRoom(
    val id: String,
    val code: String,
    @SerialName("host_user_id") val hostUserId: String,
    val title: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WatchPartyMember(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    val role: PartyRole,
    @SerialName("joined_at") val joinedAt: String? = null,
)

@Serializable
data class PartySyncPacket(
    val seq: Long,
    @SerialName("sent_at") val sentAt: Long,
    @SerialName("is_playing") val isPlaying: Boolean,
    @SerialName("position_ms") val positionMs: Long,
    val speed: Float = 1.0f,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

sealed interface PartyConnectionState {
    data object Disconnected : PartyConnectionState
    data object Connecting : PartyConnectionState
    data object Connected : PartyConnectionState
    data class Error(val message: String) : PartyConnectionState
}

data class PartySession(
    val room: WatchPartyRoom,
    val role: PartyRole,
    val memberCount: Int = 1,
)
