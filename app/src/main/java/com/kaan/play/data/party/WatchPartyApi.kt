package com.kaan.play.data.party

import com.kaan.play.data.sync.SupabaseCloudSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WatchPartyApi(
    private val cloudSync: SupabaseCloudSync,
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createRoom(
        title: String,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        displayName: String,
    ): Result<WatchPartyRoom> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_tmdb_id", tmdbId)
            put("p_media_type", mediaType)
            season?.let { put("p_season", it) }
            episode?.let { put("p_episode", it) }
            put("p_title", title.trim())
            put("p_display_name", displayName.trim())
        }.toString()

        callRpc("wp_create_room", body).mapCatching { responseText ->
            val list = json.decodeFromString<List<WpCreateResponse>>(responseText)
            val first = list.firstOrNull()
                ?: throw WatchPartyException(WatchPartyError.EmptyResponse)
            WatchPartyRoom(
                id = first.roomId,
                code = first.code,
                hostUserId = cloudSync.account.value?.userId.orEmpty(),
                title = title,
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
            )
        }
    }

    suspend fun joinRoom(
        code: String,
        displayName: String,
    ): Result<Pair<WatchPartyRoom, PartyRole>> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        val body = buildJsonObject {
            put("p_code", cleanCode)
            put("p_display_name", displayName.trim())
        }.toString()

        callRpc("wp_join_room", body).mapCatching { responseText ->
            val list = json.decodeFromString<List<WpJoinResponse>>(responseText)
            val first = list.firstOrNull()
                ?: throw WatchPartyException(WatchPartyError.RoomNotFound)
            val room = WatchPartyRoom(
                id = first.roomId,
                code = first.code,
                hostUserId = "",
                title = first.title,
                mediaType = first.mediaType,
                tmdbId = first.tmdbId,
                season = first.season,
                episode = first.episode,
                expiresAt = first.expiresAt,
            )
            val role = PartyRole.fromString(first.role)
            room to role
        }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_room_id", roomId)
        }.toString()

        callRpc("wp_leave_room", body).map { }
    }

    suspend fun updateContent(
        roomId: String,
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        title: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_room_id", roomId)
            put("p_tmdb_id", tmdbId)
            put("p_media_type", mediaType)
            season?.let { put("p_season", it) }
            episode?.let { put("p_episode", it) }
            put("p_title", title.trim())
        }.toString()

        callRpc("wp_update_content", body).map { }
    }

    private suspend fun callRpc(functionName: String, bodyJson: String): Result<String> {
        val projectUrl = cloudSync.getProjectUrl().trimEnd('/')
        val publishableKey = cloudSync.getPublishableKey()
        if (projectUrl.isBlank() || publishableKey.isBlank()) {
            return Result.failure(WatchPartyException(WatchPartyError.ServerUnavailable))
        }

        val token = cloudSync.getValidAccessToken()
            ?: return Result.failure(WatchPartyException(WatchPartyError.NotAuthenticated))

        return runCatching {
            val url = "$projectUrl/rest/v1/rpc/$functionName"
            val request = Request.Builder()
                .url(url)
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    body
                } else {
                    val error = WatchPartyError.fromServerMessage(body)
                    throw WatchPartyException(error)
                }
            }
        }.recoverCatching { throwable ->
            when (throwable) {
                is WatchPartyException -> throw throwable
                is IOException -> throw WatchPartyException(WatchPartyError.Network, throwable)
                else -> throw WatchPartyException(WatchPartyError.Unknown(throwable.message.orEmpty()), throwable)
            }
        }
    }
}
