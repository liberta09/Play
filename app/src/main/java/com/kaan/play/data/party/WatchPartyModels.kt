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
    @SerialName("host_user_id") val hostUserId: String = "",
    val title: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
) {
    val isOpen: Boolean get() = closedAt == null
}

@Serializable
data class WatchPartyMember(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val role: PartyRole,
    @SerialName("joined_at") val joinedAt: String? = null,
)

@Serializable
data class WpCreateResponse(
    @SerialName("room_id") val roomId: String,
    val code: String,
)

@Serializable
data class WpJoinResponse(
    @SerialName("room_id") val roomId: String,
    val code: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("media_type") val mediaType: String,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val role: String,
)

@Serializable
data class PartyControlPacket(
    val seq: Long,
    @SerialName("positionMs") val positionMs: Long,
    val playing: Boolean,
    val speed: Float = 1.0f,
    @SerialName("sentAtMs") val sentAtMs: Long,
)

@Serializable
data class PartyContentPacket(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    val season: Int? = null,
    val episode: Int? = null,
    val title: String,
)

@Serializable
data class PresenceMember(
    val userId: String,
    val displayName: String,
    val role: PartyRole,
)

sealed interface WatchPartyError {
    data object NotAuthenticated : WatchPartyError
    data object InvalidInput : WatchPartyError
    data object RoomNotFound : WatchPartyError
    data object RoomFull : WatchPartyError
    data object NotHostOrClosed : WatchPartyError
    data object CodeGenerationFailed : WatchPartyError
    data object ServerUnavailable : WatchPartyError
    data object ConnectionFailed : WatchPartyError
    data object Network : WatchPartyError
    data object EmptyResponse : WatchPartyError
    data class Unknown(val message: String) : WatchPartyError

    companion object {
        fun fromServerMessage(message: String): WatchPartyError = when {
            message.contains("not_authenticated", ignoreCase = true) -> NotAuthenticated
            message.contains("invalid_input", ignoreCase = true) -> InvalidInput
            message.contains("room_not_found", ignoreCase = true) -> RoomNotFound
            message.contains("room_full", ignoreCase = true) -> RoomFull
            message.contains("not_host_or_closed", ignoreCase = true) -> NotHostOrClosed
            message.contains("code_generation_failed", ignoreCase = true) -> CodeGenerationFailed
            message.contains("PGRST202", ignoreCase = true) ||
            message.contains("Could not find the function", ignoreCase = true) ||
            message.contains("server_unavailable", ignoreCase = true) -> ServerUnavailable
            else -> Unknown(message)
        }
    }
}

class WatchPartyException(val error: WatchPartyError, cause: Throwable? = null) :
    Exception(error.toString(), cause)

sealed interface PartyConnectionState {
    data object Disconnected : PartyConnectionState
    data object Connecting : PartyConnectionState
    data object Connected : PartyConnectionState
    data class Error(val error: WatchPartyError) : PartyConnectionState
}

data class PartySession(
    val room: WatchPartyRoom,
    val role: PartyRole,
    val memberCount: Int = 1,
    val members: List<PresenceMember> = emptyList(),
) {
    val isHost: Boolean get() = role == PartyRole.HOST
}
