package com.kaan.play.data.party

import com.kaan.play.data.sync.SupabaseCloudSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchPartySessionManager(
    val api: WatchPartyApi,
    val realtimeClient: WatchPartyRealtimeClient,
    val syncRules: WatchPartySyncRules,
    private val cloudSync: SupabaseCloudSync,
    private val scope: CoroutineScope,
) {
    private val _session = MutableStateFlow<PartySession?>(null)
    val session: StateFlow<PartySession?> = _session.asStateFlow()

    private val _roomClosedNotification = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val roomClosedNotification: SharedFlow<Unit> = _roomClosedNotification.asSharedFlow()

    private val _contentTransitionEvents = MutableSharedFlow<PartyContentPacket>(extraBufferCapacity = 8)
    val contentTransitionEvents: SharedFlow<PartyContentPacket> = _contentTransitionEvents.asSharedFlow()

    val currentRoom: WatchPartyRoom? get() = _session.value?.room
    val isHost: Boolean get() = _session.value?.role == PartyRole.HOST
    val isGuest: Boolean get() = _session.value?.role == PartyRole.GUEST
    val isInParty: Boolean get() = _session.value != null

    init {
        scope.launch(Dispatchers.IO) {
            realtimeClient.roomClosedEvents.collect {
                if (_session.value != null) {
                    _session.value = null
                    syncRules.reset()
                    realtimeClient.disconnect()
                    _roomClosedNotification.emit(Unit)
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            realtimeClient.memberCount.collect { count ->
                _session.value = _session.value?.copy(memberCount = count)
            }
        }
        scope.launch(Dispatchers.IO) {
            realtimeClient.members.collect { list ->
                _session.value = _session.value?.copy(members = list)
            }
        }
        scope.launch(Dispatchers.IO) {
            realtimeClient.contentEvents.collect { packet ->
                if (isGuest) {
                    _contentTransitionEvents.emit(packet)
                }
            }
        }
    }

    private fun resolveDisplayName(customName: String?): String {
        val clean = customName?.trim()
        if (!clean.isNullOrEmpty()) return clean
        val account = cloudSync.account.value
        val emailPrefix = account?.email?.substringBefore('@')?.trim()
        if (!emailPrefix.isNullOrEmpty()) return emailPrefix
        return "Kullanıcı"
    }

    suspend fun createParty(
        title: String,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        displayName: String? = null,
    ): Result<WatchPartyRoom> {
        val name = resolveDisplayName(displayName)
        val result = api.createRoom(
            title = title,
            mediaType = mediaType,
            tmdbId = tmdbId,
            season = season,
            episode = episode,
            displayName = name,
        )

        return result.onSuccess { room ->
            syncRules.reset()
            _session.value = PartySession(room = room, role = PartyRole.HOST, memberCount = 1)
            val token = cloudSync.getValidAccessToken().orEmpty()
            val userId = cloudSync.account.value?.userId.orEmpty()
            realtimeClient.connect(
                projectUrl = cloudSync.getProjectUrl(),
                publishableKey = cloudSync.getPublishableKey(),
                token = token,
                userId = userId,
                roomId = room.id,
                displayName = name,
                role = PartyRole.HOST,
            )
        }
    }

    suspend fun joinParty(
        code: String,
        displayName: String? = null,
    ): Result<WatchPartyRoom> {
        val name = resolveDisplayName(displayName)
        val result = api.joinRoom(code, name)

        return result.mapCatching { (room, role) ->
            syncRules.reset()
            _session.value = PartySession(room = room, role = role, memberCount = 1)
            val token = cloudSync.getValidAccessToken().orEmpty()
            val userId = cloudSync.account.value?.userId.orEmpty()
            realtimeClient.connect(
                projectUrl = cloudSync.getProjectUrl(),
                publishableKey = cloudSync.getPublishableKey(),
                token = token,
                userId = userId,
                roomId = room.id,
                displayName = name,
                role = role,
            )
            room
        }
    }

    suspend fun leaveParty(): Result<Unit> {
        val current = _session.value ?: return Result.success(Unit)
        val roomId = current.room.id

        if (current.role == PartyRole.HOST) {
            realtimeClient.broadcastClosed()
        }

        realtimeClient.disconnect()
        _session.value = null
        syncRules.reset()

        return api.leaveRoom(roomId)
    }

    suspend fun updatePartyContent(
        title: String,
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
    ): Result<Unit> {
        val current = _session.value
            ?: return Result.failure(WatchPartyException(WatchPartyError.ServerUnavailable))
        if (current.role != PartyRole.HOST) {
            return Result.failure(WatchPartyException(WatchPartyError.NotHostOrClosed))
        }

        val res = api.updateContent(
            roomId = current.room.id,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
        )

        return res.onSuccess {
            val updatedRoom = current.room.copy(
                title = title,
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
            )
            _session.value = current.copy(room = updatedRoom)
            realtimeClient.broadcastContent(
                PartyContentPacket(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    title = title,
                ),
            )
        }
    }

    fun broadcastControl(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float = 1.0f,
    ) {
        if (!isHost) return
        val packet = syncRules.createControlPacket(
            positionMs = positionMs,
            isPlaying = isPlaying,
            speed = speed,
        )
        realtimeClient.broadcastControl(packet)
    }
}
