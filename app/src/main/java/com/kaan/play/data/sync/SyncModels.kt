package com.kaan.play.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class CloudAccountState(
    val email: String,
    val userId: String,
)

enum class CloudRegistrationResult {
    SIGNED_IN,
    EMAIL_CONFIRMATION_REQUIRED,
}

class CloudWriteConflict : Exception()

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("user") val user: AuthUser? = null,
    @SerialName("msg") val msg: String? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class CloudSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

data class RemoteCloudSnapshot(
    val transferJson: String,
    val updatedAt: String,
)

data class LocalSyncProgress(
    val percent: Int = 0,
    val message: String = "",
    val complete: Boolean = false,
    val failed: Boolean = false,
)
