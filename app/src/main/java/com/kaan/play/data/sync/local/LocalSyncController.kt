package com.kaan.play.data.sync.local

import android.util.Log
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.InetAddress

/**
 * Wi-Fi senkronizasyonu kartının durumu ve işleri. **Uygulama açılışında hiçbir şey başlatılmaz:** sunucu, UDP dinleyici
 * ve NSD kaydı yalnızca kart açılınca ([open]) başlar; kart kapanınca, uygulama arka plana geçince ([close]) ya da
 * [LocalSyncProtocol.CODE_TTL_MS] sonra durur ve kod geçersiz olur. 5 yanlış denemede sunucu kendini kapatır.
 *
 * Kodu gösteren cihaz sunucudur; kodu yazıp "Bağlan"a basan cihaz istemcidir. İkisi de karşının anlık görüntüsünü alıp
 * [LocalSyncMerge] ile birleştirir (ortak kaynakların durumlarında kodu gösteren cihaz esas). Eksik kaynaklar
 * [State.pending] listesinde onay bekler.
 */
class LocalSyncController(
    private val data: LocalSyncData,
    private val nsd: LocalSyncNsd,
    private val scope: CoroutineScope,
    private val client: LocalSyncClient = LocalSyncClient(),
) {
    enum class Phase { Idle, Searching, Connecting, Syncing, Done, Failed }

    enum class Error { InvalidCode, NotFound, WrongCode, Unreachable, Locked, PortBusy, Failed }

    data class State(
        /** Bu cihazın tek kullanımlık kodu (kart açıkken); null ise sunucu kapalı. */
        val code: String? = null,
        val phase: Phase = Phase.Idle,
        val error: Error? = null,
        val result: LocalSyncData.Counts? = null,
        /** Karşıda olup burada olmayan kaynaklar: "Şunlar eklensin mi?" */
        val pending: List<SourceTransferItem> = emptyList(),
        val installing: Boolean = false,
        /** Kurulamayan kaynak sayısı (son onaydan). */
        val installFailed: Int = 0,
        /** Kodun süresi doldu (kart açık kalabilir; "Yeni kod" ile yeniden açılır). */
        val expired: Boolean = false,
    ) {
        val busy: Boolean get() = phase == Phase.Searching || phase == Phase.Connecting || phase == Phase.Syncing
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var server: LocalSyncServer? = null
    private var responder: LocalSyncDiscovery.Responder? = null
    private var ttlJob: Job? = null
    private var connectJob: Job? = null

    /** Kart açık mı (açılış IO'da sürerken kart kapanırsa sunucu hiç başlamaz). */
    @Volatile
    private var wanted = false

    /** Kart açıldı: yeni kod, sunucu + UDP dinleyici + NSD kaydı; 3 dk sonra kendiliğinden kapanır. */
    fun open() {
        wanted = true
        scope.launch(Dispatchers.IO) { openNow() }
    }

    @Synchronized
    private fun openNow() {
        if (!wanted || server?.isRunning == true) return
        val code = LocalSyncCode.generate()
        val srv = LocalSyncServer(code, handler, log = { Log.i(TAG, it) })
        try {
            srv.start()
        } catch (e: IOException) {
            Log.w(TAG, "sync server start failed: ${e.javaClass.simpleName}")
            _state.update { it.copy(code = null, phase = Phase.Failed, error = Error.PortBusy) }
            return
        }
        srv.onClosed = { reason ->
            if (reason != LocalSyncServer.CloseReason.Stopped) {
                stopHosting()
                _state.update {
                    it.copy(code = null, phase = Phase.Failed, error = if (reason == LocalSyncServer.CloseReason.TooManyAttempts) Error.Locked else Error.Failed)
                }
            }
        }
        server = srv
        nsd.acquireMulticast()
        responder = LocalSyncDiscovery.Responder().also { r ->
            try {
                r.start()
            } catch (e: IOException) {
                Log.w(TAG, "sync responder start failed: ${e.javaClass.simpleName}")
            }
        }
        nsd.register(LocalSyncProtocol.PORT)
        _state.value = State(code = code)
        ttlJob = scope.launch {
            delay(LocalSyncProtocol.CODE_TTL_MS)
            close()
            _state.value = State(expired = true)
        }
    }

    /** Kart kapandı / uygulama arka plana geçti / süre doldu: her şey durur, kod geçersiz. */
    @Synchronized
    fun close() {
        wanted = false
        ttlJob?.cancel()
        ttlJob = null
        connectJob?.cancel()
        connectJob = null
        stopHosting()
        _state.value = State()
    }

    @Synchronized
    private fun stopHosting() {
        server?.let { it.onClosed = null; it.stop() }
        server = null
        responder?.stop()
        responder = null
        nsd.unregister()
        nsd.releaseMulticast()
    }

    /** Karşı cihazın kodu girildi. [manualHost] yalnızca debug derlemede (emülatör denemesi). */
    fun connect(codeInput: String, manualHost: String? = null) {
        val code = LocalSyncCode.normalize(codeInput)
        if (code == null) {
            _state.update { it.copy(phase = Phase.Failed, error = Error.InvalidCode) }
            return
        }
        if (connectJob?.isActive == true) return
        connectJob = scope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(phase = Phase.Searching, error = null, result = null) }
                val hosts = manualHost?.takeIf { it.isNotBlank() }?.let { listOfNotNull(LocalNetwork.parseIpv4(it)) } ?: discover()
                if (hosts.isEmpty()) {
                    _state.update { it.copy(phase = Phase.Failed, error = Error.NotFound) }
                    return@launch
                }
                _state.update { it.copy(phase = Phase.Connecting) }
                val local = data.export()
                var lastError = Error.Unreachable
                for (host in hosts) {
                    val remoteJson = try {
                        client.exchange(host.hostAddress!!, LocalSyncProtocol.PORT, code, LocalSyncMerge.encode(local))
                    } catch (e: LocalSyncException) {
                        Log.w(TAG, "sync connect failed: ${e.kind}")
                        if (e.kind == LocalSyncException.Kind.WrongCode) {
                            lastError = Error.WrongCode
                            break
                        }
                        continue
                    }
                    val remote = LocalSyncMerge.decodeTransfer(remoteJson)
                    merge(local, remote, remoteIsAuthority = true)
                    return@launch
                }
                _state.update { it.copy(phase = Phase.Failed, error = lastError) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(phase = Phase.Failed, error = Error.Failed) }
            }
        }
    }

    /** UDP yayın (MulticastLock alınmış); bulunamazsa NSD `_playsync._tcp`. Bu cihazın kendi sunucusu atlanır. */
    private suspend fun discover(): List<InetAddress> {
        nsd.acquireMulticast()
        try {
            val (own, broadcasts) = LocalSyncDiscovery.localInterfaces()
            val udp = LocalSyncDiscovery.discover(broadcasts, ownAddresses = own)
            if (udp.isNotEmpty()) return udp
            return nsd.discover(NSD_TIMEOUT_MS, own)
        } finally {
            if (server == null) nsd.releaseMulticast()
        }
    }

    private suspend fun merge(local: LocalProfileTransfer, remote: LocalProfileTransfer, remoteIsAuthority: Boolean) {
        _state.update { it.copy(phase = Phase.Syncing, error = null) }
        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority)
        data.apply(plan)
        val counts = data.counts()
        _state.update { it.copy(phase = Phase.Done, result = counts, pending = plan.missingSources, installFailed = 0) }
    }

    /** Sunucu tarafı: iş parçacığında engelleyerek çağrılır. */
    private val handler = object : LocalSyncServer.Handler {
        override fun profiles(): String = runBlocking {
            val list = data.export().profiles
            buildJsonObject {
                put("profiles", buildJsonArray { list.forEach { p -> add(buildJsonObject { put("id", p.id); put("name", p.name) }) } })
            }.toString()
        }

        override fun exchange(remoteJson: String): String = runBlocking {
            val remote = LocalSyncMerge.decodeTransfer(remoteJson)
            val local = data.export()
            // Yanıt (birleştirmeden önceki görüntü) hemen gider; bu cihaz kendi tarafını arka planda yazar.
            scope.launch {
                try {
                    merge(local, remote, remoteIsAuthority = false)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "sync apply failed: ${e.javaClass.simpleName}")
                    _state.update { it.copy(phase = Phase.Failed, error = Error.Failed) }
                }
            }
            LocalSyncMerge.encode(local)
        }
    }

    /** "Şunlar eklensin mi?": yalnızca onaylananlar bu cihazda kurulur. */
    fun confirm(items: List<SourceTransferItem>) {
        if (items.isEmpty()) {
            dismissPending()
            return
        }
        scope.launch {
            _state.update { it.copy(installing = true) }
            var failed = 0
            for (item in items) {
                if (!data.install(item)) failed++
            }
            val counts = data.counts()
            _state.update { s ->
                s.copy(installing = false, pending = emptyList(), installFailed = failed, result = counts)
            }
        }
    }

    fun dismissPending() = _state.update { it.copy(pending = emptyList()) }

    private companion object {
        const val TAG = "PlaySync"
        const val NSD_TIMEOUT_MS = 4_000L
    }
}
