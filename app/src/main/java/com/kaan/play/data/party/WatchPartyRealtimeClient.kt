package com.kaan.play.data.party

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime (Phoenix Channels V1, vsn=1.0.0) WebSocket istemcisi.
 *
 * Güvenlik ve Gizlilik:
 * Loglarda (PlayParty etiketi altında) token, oda kimliği, oda kodu veya kullanıcı adı ASLA yer almaz.
 * Yalnızca olay türü loglanır.
 */
class WatchPartyRealtimeClient(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var activeRoomId: String? = null
    private var supabaseUrl: String = ""
    private var apiKey: String = ""
    private var userToken: String? = null
    private var currentUserId: String = ""
    private var currentDisplayName: String = ""
    private var currentRole: PartyRole = PartyRole.GUEST

    private val refCounter = AtomicLong(1)
    private var currentJoinRef: String = "1"
    private var isManuallyClosed = false

    private val _connectionState = MutableStateFlow<PartyConnectionState>(PartyConnectionState.Disconnected)
    val connectionState: StateFlow<PartyConnectionState> = _connectionState.asStateFlow()

    private val _controlEvents = MutableSharedFlow<PartyControlPacket>(extraBufferCapacity = 64)
    val controlEvents: SharedFlow<PartyControlPacket> = _controlEvents.asSharedFlow()

    private val _contentEvents = MutableSharedFlow<PartyContentPacket>(extraBufferCapacity = 16)
    val contentEvents: SharedFlow<PartyContentPacket> = _contentEvents.asSharedFlow()

    private val _roomClosedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val roomClosedEvents: SharedFlow<Unit> = _roomClosedEvents.asSharedFlow()

    private val _latestControl = MutableStateFlow<PartyControlPacket?>(null)
    val latestControl: StateFlow<PartyControlPacket?> = _latestControl.asStateFlow()

    private val _memberCount = MutableStateFlow(1)
    val memberCount: StateFlow<Int> = _memberCount.asStateFlow()

    private val _members = MutableStateFlow<List<PresenceMember>>(emptyList())
    val members: StateFlow<List<PresenceMember>> = _members.asStateFlow()

    private val presenceMap = ConcurrentHashMap<String, PresenceMember>()

    fun connect(
        projectUrl: String,
        publishableKey: String,
        token: String,
        userId: String,
        roomId: String,
        displayName: String,
        role: PartyRole,
    ) {
        if (projectUrl.isBlank() || publishableKey.isBlank()) {
            _connectionState.value = PartyConnectionState.Error(WatchPartyError.ServerUnavailable)
            return
        }

        activeRoomId = roomId
        supabaseUrl = projectUrl
        apiKey = publishableKey
        userToken = token
        currentUserId = userId
        currentDisplayName = displayName
        currentRole = role
        isManuallyClosed = false
        presenceMap.clear()
        _memberCount.value = 1
        _members.value = emptyList()

        initiateConnection(initialDelayMs = 0L)
    }

    private fun initiateConnection(initialDelayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            if (initialDelayMs > 0) delay(initialDelayMs)
            if (isManuallyClosed) return@launch

            _connectionState.value = PartyConnectionState.Connecting

            val wsUrl = buildWsUrl(supabaseUrl, apiKey)
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    val roomId = activeRoomId ?: return
                    val topic = "realtime:wp:$roomId"
                    val joinRef = refCounter.getAndIncrement().toString()
                    currentJoinRef = joinRef

                    val token = userToken.orEmpty()
                    val joinFrame = PhoenixFrame.join(
                        topic = topic,
                        ref = joinRef,
                        jwt = token,
                        userId = currentUserId,
                        joinRef = joinRef,
                    )
                    ws.send(joinFrame.serialize())
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingMessage(ws, text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    stopHeartbeat()
                    if (!isManuallyClosed) {
                        _connectionState.value = PartyConnectionState.Disconnected
                        scheduleReconnect(1_000L)
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    stopHeartbeat()
                    if (!isManuallyClosed) {
                        Log.d(TAG, "olay=hata")
                        _connectionState.value = PartyConnectionState.Error(WatchPartyError.Network)
                        scheduleReconnect(2_000L)
                    }
                }
            }

            webSocket = client.newWebSocket(request, listener)
        }
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        val frame = PhoenixFrame.parse(text) ?: return

        when (frame.event) {
            "phx_reply" -> {
                if (frame.ref == currentJoinRef) {
                    val status = frame.payload.jsonObject["status"]?.jsonPrimitive?.content
                    if (status == "error") {
                        Log.d(TAG, "olay=yetki_reddi")
                        isManuallyClosed = true
                        reconnectJob?.cancel()
                        stopHeartbeat()
                        ws.close(1000, "Auth error")
                        _connectionState.value = PartyConnectionState.Error(WatchPartyError.ConnectionFailed)
                    } else if (status == "ok") {
                        Log.d(TAG, "olay=baglanti_kuruldu")
                        _connectionState.value = PartyConnectionState.Connected
                        startHeartbeat()

                        // Kendi varlığını (presence) bildir
                        val roomId = activeRoomId
                        if (roomId != null) {
                            val topic = "realtime:wp:$roomId"
                            val trackRef = refCounter.getAndIncrement().toString()
                            val trackFrame = PhoenixFrame.presenceTrack(
                                topic = topic,
                                ref = trackRef,
                                userId = currentUserId,
                                displayName = currentDisplayName,
                                role = currentRole.name.lowercase(),
                            )
                            ws.send(trackFrame.serialize())
                        }
                    }
                }
            }
            "presence_state" -> {
                parsePresenceState(frame.payload)
            }
            "presence_diff" -> {
                parsePresenceDiff(frame.payload)
            }
            "broadcast" -> {
                val broadcast = PhoenixFrame.extractBroadcast(frame) ?: return
                val (eventName, data) = broadcast
                when (eventName) {
                    "control" -> {
                        runCatching {
                            val packet = json.decodeFromJsonElement<PartyControlPacket>(data)
                            Log.d(TAG, "olay=control")
                            _latestControl.value = packet
                            scope.launch { _controlEvents.emit(packet) }
                        }
                    }
                    "content" -> {
                        runCatching {
                            val packet = json.decodeFromJsonElement<PartyContentPacket>(data)
                            Log.d(TAG, "olay=content")
                            scope.launch { _contentEvents.emit(packet) }
                        }
                    }
                    "closed" -> {
                        Log.d(TAG, "olay=closed")
                        scope.launch { _roomClosedEvents.emit(Unit) }
                    }
                }
            }
        }
    }

    private fun parsePresenceState(payload: kotlinx.serialization.json.JsonElement) {
        runCatching {
            val root = payload.jsonObject
            presenceMap.clear()
            for ((key, value) in root) {
                val metas = value.jsonObject["metas"]?.jsonArray ?: continue
                val firstMeta = metas.firstOrNull()?.jsonObject ?: continue
                val name = firstMeta["display_name"]?.jsonPrimitive?.content ?: "Üye"
                val roleStr = firstMeta["role"]?.jsonPrimitive?.content ?: "guest"
                presenceMap[key] = PresenceMember(key, name, PartyRole.fromString(roleStr))
            }
            syncPresenceList()
        }
    }

    private fun parsePresenceDiff(payload: kotlinx.serialization.json.JsonElement) {
        runCatching {
            val root = payload.jsonObject
            val joins = root["joins"]?.jsonObject ?: buildJsonObject {}
            val leaves = root["leaves"]?.jsonObject ?: buildJsonObject {}

            for ((key, value) in joins) {
                val metas = value.jsonObject["metas"]?.jsonArray ?: continue
                val firstMeta = metas.firstOrNull()?.jsonObject ?: continue
                val name = firstMeta["display_name"]?.jsonPrimitive?.content ?: "Üye"
                val roleStr = firstMeta["role"]?.jsonPrimitive?.content ?: "guest"
                presenceMap[key] = PresenceMember(key, name, PartyRole.fromString(roleStr))
            }

            for ((key, _) in leaves) {
                presenceMap.remove(key)
            }
            syncPresenceList()
        }
    }

    private fun syncPresenceList() {
        val list = presenceMap.values.toList()
        _members.value = list
        _memberCount.value = list.size.coerceAtLeast(1)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val ref = refCounter.getAndIncrement().toString()
                val frame = PhoenixFrame.heartbeat(ref)
                val sent = webSocket?.send(frame.serialize()) == true
                if (sent) {
                    Log.d(TAG, "olay=kalp_atisi")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (isManuallyClosed) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            val nextBackoff = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            initiateConnection(nextBackoff)
        }
    }

    fun broadcastControl(packet: PartyControlPacket) {
        if (currentRole != PartyRole.HOST) return
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val payload = json.encodeToJsonElement(packet)
        val frame = PhoenixFrame.broadcast(topic = topic, ref = ref, event = "control", data = payload)
        ws.send(frame.serialize())
    }

    fun broadcastContent(packet: PartyContentPacket) {
        if (currentRole != PartyRole.HOST) return
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val payload = json.encodeToJsonElement(packet)
        val frame = PhoenixFrame.broadcast(topic = topic, ref = ref, event = "content", data = payload)
        ws.send(frame.serialize())
    }

    fun broadcastClosed() {
        if (currentRole != PartyRole.HOST) return
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val payload = buildJsonObject {}
        val frame = PhoenixFrame.broadcast(topic = topic, ref = ref, event = "closed", data = payload)
        ws.send(frame.serialize())
    }

    fun updateAccessToken(newToken: String) {
        userToken = newToken
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val frame = PhoenixFrame.accessToken(topic = topic, ref = ref, jwt = newToken)
        ws.send(frame.serialize())
    }

    fun disconnect() {
        isManuallyClosed = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        val roomId = activeRoomId
        if (roomId != null && webSocket != null) {
            val topic = "realtime:wp:$roomId"
            val ref = refCounter.getAndIncrement().toString()
            val leaveFrame = PhoenixFrame.leave(topic = topic, ref = ref)
            runCatching { webSocket?.send(leaveFrame.serialize()) }
        }

        webSocket?.close(1000, "User left")
        webSocket = null
        activeRoomId = null
        _latestControl.value = null
        presenceMap.clear()
        _memberCount.value = 1
        _members.value = emptyList()
        _connectionState.value = PartyConnectionState.Disconnected
        Log.d(TAG, "olay=ayrildi")
    }

    companion object {
        private const val TAG = "PlayParty"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val MAX_BACKOFF_MS = 16_000L

        fun buildWsUrl(projectUrl: String, apiKey: String): String {
            val cleaned = projectUrl.trimEnd('/')
            val wsBase = when {
                cleaned.startsWith("https://") -> "wss://" + cleaned.removePrefix("https://")
                cleaned.startsWith("http://") -> "ws://" + cleaned.removePrefix("http://")
                else -> "wss://$cleaned"
            }
            return "$wsBase/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
        }
    }
}
