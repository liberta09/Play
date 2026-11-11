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
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createRoom(
        title: String,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
    ): Result<WatchPartyRoom> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_title", title)
            put("p_media_type", mediaType)
            put("p_tmdb_id", tmdbId)
            season?.let { put("p_season", it) }
            episode?.let { put("p_episode", it) }
        }.toString()

        callRpc("wp_create_room", body).mapCatching { responseText ->
            json.decodeFromString<WatchPartyRoom>(responseText)
        }
    }

    suspend fun joinRoom(code: String): Result<WatchPartyRoom> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_code", code.trim().uppercase())
        }.toString()

        callRpc("wp_join_room", body).mapCatching { responseText ->
            json.decodeFromString<WatchPartyRoom>(responseText)
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
        title: String,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_room_id", roomId)
            put("p_title", title)
            put("p_media_type", mediaType)
            put("p_tmdb_id", tmdbId)
            season?.let { put("p_season", it) }
            episode?.let { put("p_episode", it) }
        }.toString()

        callRpc("wp_update_content", body).map { }
    }

    private suspend fun callRpc(functionName: String, bodyJson: String): Result<String> {
        val projectUrl = cloudSync.getProjectUrl().trimEnd('/')
        val publishableKey = cloudSync.getPublishableKey()
        if (projectUrl.isBlank() || publishableKey.isBlank()) {
            return Result.failure(IllegalStateException("Watch Party sunucusu henüz aktif değil."))
        }

        val token = cloudSync.getValidAccessToken()
            ?: return Result.failure(IllegalStateException("Watch Party için Bulut Hesabı girişi gerekir."))

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
                    val errorMsg = mapServerError(response.code, body)
                    throw IOException(errorMsg)
                }
            }
        }
    }

    private fun mapServerError(code: Int, body: String): String {
        return when {
            code == 404 || body.contains("PGRST202") || body.contains("Could not find the function") ->
                "Watch Party sunucusu henüz aktif değil."
            body.contains("ROOM_NOT_FOUND") ->
                "Oda bulunamadı. Lütfen kodu kontrol edin."
            body.contains("ROOM_INACTIVE") ->
                "Oda artık aktif değil veya kapatılmış."
            body.contains("NOT_AUTHENTICATED") ->
                "Watch Party için Bulut Hesabı girişi gerekir."
            body.contains("NOT_AUTHORIZED") ->
                "Bu işlem için oda sahibi yetkisi gerekir."
            else -> "Sunucu hatası ($code)"
        }
    }
}
