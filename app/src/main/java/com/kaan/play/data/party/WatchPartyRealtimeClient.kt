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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime (Phoenix Channels V2) WebSocket istemcisi.
 *
 * Güvenlik ve Gizlilik:
 * Loglarda adres, token, parola, oda kodu/UUID, kullanıcı adı/UUID ASLA yer almaz.
 * Yalnızca genel durum ve olay isimleri (PlayParty etiketiyle) loglanır.
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

    private val refCounter = AtomicLong(1)
    private var isManuallyClosed = false

    private val _connectionState = MutableStateFlow<PartyConnectionState>(PartyConnectionState.Disconnected)
    val connectionState: StateFlow<PartyConnectionState> = _connectionState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<PartySyncPacket>(extraBufferCapacity = 64)
    val syncEvents: SharedFlow<PartySyncPacket> = _syncEvents.asSharedFlow()

    private val _latestPacket = MutableStateFlow<PartySyncPacket?>(null)
    val latestPacket: StateFlow<PartySyncPacket?> = _latestPacket.asStateFlow()

    private val _roomClosedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val roomClosedEvents: SharedFlow<Unit> = _roomClosedEvents.asSharedFlow()

    fun connect(
        projectUrl: String,
        publishableKey: String,
        token: String?,
        roomId: String,
    ) {
        if (projectUrl.isBlank() || publishableKey.isBlank()) {
            _connectionState.value = PartyConnectionState.Error("Yapılandırma eksik")
            return
        }

        activeRoomId = roomId
        supabaseUrl = projectUrl
        apiKey = publishableKey
        userToken = token
        isManuallyClosed = false

        initiateConnection(backoffMs = 1000L)
    }

    private fun initiateConnection(backoffMs: Long) {
        val roomId = activeRoomId ?: return
        if (isManuallyClosed) return

        _connectionState.value = PartyConnectionState.Connecting
        Log.d(TAG, "durum=bağlanıyor")

        val wsUrl = buildWsUrl(supabaseUrl, apiKey, userToken)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "durum=bağlandı")
                _connectionState.value = PartyConnectionState.Connected
                joinRoomChannel(roomId)
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "hata=ağ")
                stopHeartbeat()
                _connectionState.value = PartyConnectionState.Error("Ağ hatası")
                scheduleReconnect(backoffMs)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "durum=ayrıldı")
                stopHeartbeat()
                if (!isManuallyClosed) {
                    scheduleReconnect(backoffMs)
                } else {
                    _connectionState.value = PartyConnectionState.Disconnected
                }
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        val frame = PhoenixFrame.parse(text) ?: return

        when (frame.event) {
            "phx_reply" -> {
                // Katılma veya komut yanıtı
            }
            "phx_close" -> {
                Log.d(TAG, "olay=oda_kapandı")
                scope.launch { _roomClosedEvents.emit(Unit) }
            }
            "broadcast" -> {
                val broadcast = PhoenixFrame.extractBroadcast(frame) ?: return
                val (eventName, data) = broadcast
                when (eventName) {
                    "sync" -> {
                        runCatching {
                            val packet = json.decodeFromJsonElement<PartySyncPacket>(data)
                            Log.d(TAG, "olay=sync")
                            _latestPacket.value = packet
                            scope.launch { _syncEvents.emit(packet) }
                        }
                    }
                    "room_closed" -> {
                        Log.d(TAG, "olay=oda_kapandı")
                        scope.launch { _roomClosedEvents.emit(Unit) }
                    }
                }
            }
        }
    }

    private fun joinRoomChannel(roomId: String) {
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val joinFrame = PhoenixFrame.join(topic = topic, ref = ref)
        webSocket?.send(joinFrame.serialize())
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
                    Log.d(TAG, "kalp_atışı")
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

    fun broadcastSync(packet: PartySyncPacket) {
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val payload = json.encodeToJsonElement(packet)
        val frame = PhoenixFrame.broadcast(topic = topic, ref = ref, event = "sync", data = payload)
        ws.send(frame.serialize())
    }

    fun broadcastRoomClosed() {
        val roomId = activeRoomId ?: return
        val ws = webSocket ?: return
        val topic = "realtime:wp:$roomId"
        val ref = refCounter.getAndIncrement().toString()
        val payload = json.encodeToJsonElement(mapOf("closed" to true))
        val frame = PhoenixFrame.broadcast(topic = topic, ref = ref, event = "room_closed", data = payload)
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
        _latestPacket.value = null
        _connectionState.value = PartyConnectionState.Disconnected
        Log.d(TAG, "durum=ayrıldı")
    }

    companion object {
        private const val TAG = "PlayParty"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val MAX_BACKOFF_MS = 16_000L

        fun buildWsUrl(projectUrl: String, apiKey: String, token: String?): String {
            val cleaned = projectUrl.trimEnd('/')
            val wsBase = when {
                cleaned.startsWith("https://") -> "wss://" + cleaned.removePrefix("https://")
                cleaned.startsWith("http://") -> "ws://" + cleaned.removePrefix("http://")
                else -> "wss://$cleaned"
            }
            val authParam = if (!token.isNullOrBlank()) "&token=$token" else ""
            return "$wsBase/realtime/v1/websocket?apikey=$apiKey&vsn=2.0.0$authParam"
        }
    }
}
