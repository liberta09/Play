package com.kaan.play.data.sync.local

import android.util.Log
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
 * [State.pending] listesinde onay bekler; [autoInstallSources] açıksa onay beklemeden kurulur.
 *
 * Kalıcı eşleşme ([pairs] verilirse; bkz. [LocalSyncPairing]): kodlu bağlantıdan sonra eşleşme kendiliğinden kurulur.
 * Eşleşmiş cihazlar [autoSync] ile kod girilmeden eşitlenir; karşı taraf [startPairedHosting] ile (uygulama ön plandayken)
 * sunucuyu kodsuz açık tutar, kart açılınca aynı sunucuya kod eklenir. [pairs] null ise davranış eskisiyle aynıdır.
 */
class LocalSyncController(
    private val data: LocalSyncData,
    private val nsd: LocalSyncNsd,
    private val scope: CoroutineScope,
    private val client: LocalSyncClient = LocalSyncClient(),
    private val pairs: LocalSyncPairStore? = null,
    /** Bu cihaz eşleşmiş cihazdan gelen "bunu oynat" isteklerini kabul eder ([playRequests]); yalnız [pairs] ile anlamlı. */
    private val acceptRemotePlay: Boolean = false,
    /**
     * Gelen isteği bu cihazda oynatılabilir yapar (ör. karşının eklenti numarasını buradakine çevirir); null: bu içeriğin
     * eklentisi bu cihazda yok, karşı cihaza [CastResult.NoPlugin] döner. Yalnız [acceptRemotePlay] ile anlamlı.
     */
    private val resolveRemotePlay: suspend (RemotePlay) -> com.kaan.play.data.stream.PlayTarget? = { it.target },
    /** Eşitlemede karşıda olup burada olmayan kaynaklar onay sorulmadan kurulur (eşleşmiş cihazlar arası tam eşitleme). */
    private val autoInstallSources: Boolean = false,
    /** Bu cihaz eşleşmiş cihazdan kumanda/klavye isteklerini kabul eder ([remoteInputs]); yalnız [pairs] ile anlamlı. */
    private val acceptRemoteControl: Boolean = false,
    /** Kumandanın durum sorusuna yanıt (TV: oynayan yapım + ses); null: bu cihaz durum vermez. Sunucu iş parçacığında çağrılır. */
    private val remoteStateSource: (() -> RemoteState)? = null,
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

    /** Uygulama ön planda ve eşli sunucu isteniyor (eşleşme sonradan kurulsa da kart kapanınca sunucu açık kalır). */
    @Volatile
    private var pairedHosting = false

    private val hasPairs: Boolean get() = pairs?.pairs?.value?.isNotEmpty() == true

    /** Eşleşmiş cihazlar (arayüz); eşleşme kapalıysa boş. */
    val pairedDevices: StateFlow<List<PairRecord>> = pairs?.pairs ?: MutableStateFlow(emptyList())

    private var autoJob: Job? = null
    private var lastAutoAt = 0L

    private val _playRequests = MutableSharedFlow<RemotePlay>(extraBufferCapacity = 1)

    /**
     * Eşleşmiş cihazdan gelen oynatma istekleri ([acceptRemotePlay]); uygulama ön plandayken sunucu açıktır. İçerik isteği
     * [resolveRemotePlay]'den geçmiştir; akışlı istekte ([RemotePlay.streams]) karşının eklenti numarası silinmiştir.
     */
    val playRequests: SharedFlow<RemotePlay> = _playRequests

    private val _remoteInputs = MutableSharedFlow<RemoteInput>(extraBufferCapacity = 32)

    /** Eşleşmiş cihazdan gelen kumanda tuşları ve yazılar ([acceptRemoteControl]); sırası korunur, uygulama uygular. */
    val remoteInputs: SharedFlow<RemoteInput> = _remoteInputs

    /** Sunucu iş parçacığında; dinleyen yoksa (uygulama uygulamıyor) karşıya [REPLY_UNSUPPORTED]. */
    private val remoteInput: ((String) -> String)? = if (acceptRemoteControl && pairs != null) { body ->
        val input = runCatching { com.kaan.play.data.net.PlayJson.decodeFromString(RemoteInput.serializer(), body) }.getOrNull()
            ?.let { it.copy(text = it.text?.take(RemoteInput.MAX_TEXT)) }
        when {
            input != null && input.state ->
                remoteStateSource?.let { com.kaan.play.data.net.PlayJson.encodeToString(RemoteState.serializer(), it()) } ?: REPLY_UNSUPPORTED
            input == null || (input.key == null && input.text == null) -> REPLY_BAD
            _remoteInputs.subscriptionCount.value == 0 -> REPLY_UNSUPPORTED
            _remoteInputs.tryEmit(input) -> REPLY_OK
            else -> REPLY_BAD
        }
    } else null

    private val _castToTv = MutableStateFlow(false)

    /** Telefondaki "TV'de oynat" anahtarı (süreç boyunca): açıkken detaydan başlatılan oynatma eşleşmiş cihaza gider. */
    val castToTv: StateFlow<Boolean> = _castToTv

    fun setCastToTv(on: Boolean) {
        _castToTv.value = on && hasPairs
    }

    /** Sunucu iş parçacığında: istek çözülür, oynatma başlatılır, karşıya sonuç döner ([REPLY_OK] / [REPLY_NO_PLUGIN]). */
    private val remotePlay: ((String) -> String)? = if (acceptRemotePlay && pairs != null) { body ->
        val req = runCatching { com.kaan.play.data.net.PlayJson.decodeFromString(RemotePlay.serializer(), body) }.getOrNull()
        when {
            req == null -> {
                Log.w(TAG, "remote play: bad request")
                REPLY_BAD
            }
            req.streams.isNotEmpty() -> {
                Log.i(TAG, "remote play request (${req.streams.size} direct streams)")
                val request = req.target.request.copy(pluginId = null) // karşının numarası burada başka eklenti olabilir
                _playRequests.tryEmit(req.copy(target = req.target.copy(request = request)))
                REPLY_OK
            }
            else -> {
                val resolved = try {
                    runBlocking { resolveRemotePlay(req) }
                } catch (e: Exception) {
                    Log.w(TAG, "remote play resolve failed: ${e.javaClass.simpleName}")
                    null
                }
                if (resolved == null) {
                    Log.i(TAG, "remote play request: no plugin here")
                    REPLY_NO_PLUGIN
                } else {
                    Log.i(TAG, "remote play request")
                    _playRequests.tryEmit(RemotePlay(resolved))
                    REPLY_OK
                }
            }
        }
    } else null

    /**
     * [target]'ı eşleşmiş cihazda (en son eşitlenen önce) oynatır: önce son adres, yanıt yoksa ağda arama. [streams] doluysa
     * içerik değil bu akışlar gönderilir (karşıda eklenti yoksa). Karşıda eklenti yoksa [CastResult.NoPlugin].
     * [pluginName]: hedefin eklentisinin adı ([RemotePlay.pluginName]).
     */
    suspend fun playOnPeer(
        target: com.kaan.play.data.stream.PlayTarget,
        streams: List<com.kaan.play.data.stream.PlayableStream> = emptyList(),
        pluginName: String? = null,
    ): CastResult {
        val body = com.kaan.play.data.net.PlayJson.encodeToString(RemotePlay.serializer(), RemotePlay(target, streams, pluginName))
        val (pair, reply) = sendToPeer("remote play", body) { pair, host, b -> client.playPaired(host, LocalSyncProtocol.PORT, pair, b) }
            ?: return CastResult.Unreachable
        return if (reply.contains("\"$ERROR_NO_PLUGIN\"")) CastResult.NoPlugin(pair.peerName) else CastResult.Sent(pair.peerName)
    }

    /** Kumanda tuşu ya da yazı eşleşmiş cihaza ([playOnPeer] gibi: son adres, yoksa ağda arama). */
    suspend fun remoteToPeer(input: RemoteInput): RemoteSendResult {
        val body = com.kaan.play.data.net.PlayJson.encodeToString(RemoteInput.serializer(), input)
        val (_, reply) = sendToPeer("remote input", body) { pair, host, b -> client.remotePaired(host, LocalSyncProtocol.PORT, pair, b) }
            ?: return RemoteSendResult.Unreachable
        return if (reply.contains("\"$ERROR_UNSUPPORTED\"")) RemoteSendResult.Unsupported else RemoteSendResult.Sent
    }

    /**
     * Eşleşmiş cihazın durumu (kumanda ekranı saniyede bir sorar). Ağda arama YAPMAZ: yalnız eşlerin son adresi denenir;
     * ulaşılamazsa ya da karşı durum vermiyorsa (eski sürüm) null.
     */
    suspend fun remoteStateFromPeer(): RemoteState? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val store = pairs ?: return@withContext null
        val body = com.kaan.play.data.net.PlayJson.encodeToString(RemoteInput.serializer(), RemoteInput(state = true))
        for (pair in store.pairs.value.sortedByDescending { it.lastSyncAt }) {
            val host = pair.lastHost ?: continue
            val reply = try {
                client.remotePaired(host, LocalSyncProtocol.PORT, pair, body)
            } catch (e: LocalSyncException) {
                continue
            } catch (e: IOException) {
                continue
            }
            return@withContext runCatching { com.kaan.play.data.net.PlayJson.decodeFromString(RemoteState.serializer(), reply) }
                .getOrNull()?.takeIf { it.v >= 1 }
        }
        null
    }

    /** [body]'yi eşleşmiş cihaza (en son eşitlenen önce) gönderir: önce son adres, yanıt yoksa ağda arama. Eş + yanıt ya da null. */
    private suspend fun sendToPeer(
        label: String,
        body: String,
        send: suspend (pair: PairRecord, host: String, body: String) -> String,
    ): Pair<PairRecord, String>? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val store = pairs ?: return@withContext null
        val list = store.pairs.value.sortedByDescending { it.lastSyncAt }
        suspend fun tryAt(pair: PairRecord, host: String): Pair<PairRecord, String>? = try {
            val reply = send(pair, host, body)
            if (pair.lastHost != host) store.update(pair.pairId) { it.copy(lastHost = host) }
            pair to reply
        } catch (e: LocalSyncException) {
            Log.w(TAG, "$label failed: ${e.kind}")
            null
        } catch (e: IOException) {
            Log.w(TAG, "$label failed: ${e.javaClass.simpleName}")
            null
        }
        for (pair in list) pair.lastHost?.let { host -> tryAt(pair, host)?.let { return@withContext it } }
        val found = discover().mapNotNull { it.hostAddress }.distinct()
        for (pair in list) for (host in found.filter { it != pair.lastHost }) tryAt(pair, host)?.let { return@withContext it }
        Log.i(TAG, "$label: no paired device reachable")
        null
    }

    /** Kart açıldı: yeni kod, sunucu + UDP dinleyici + NSD kaydı; 3 dk sonra kendiliğinden kapanır. */
    fun open() {
        wanted = true
        scope.launch(Dispatchers.IO) { openNow() }
    }

    @Synchronized
    private fun openNow() {
        if (!wanted) return
        val code = LocalSyncCode.generate()
        val running = server?.takeIf { it.isRunning }
        if (running != null) {
            if (!pairedHosting) return // kart zaten açık
            running.setCode(code) // eşli sunucu açık: yalnız kod eklenir
            _state.value = State(code = code, pending = _state.value.pending) // otomatik eşitlemenin onay bekleyen kaynakları kalır
            startTtl()
            return
        }
        if (!startServer(code)) return
        _state.value = State(code = code, pending = _state.value.pending)
        startTtl()
    }

    private fun startTtl() {
        ttlJob?.cancel()
        ttlJob = scope.launch {
            delay(LocalSyncProtocol.CODE_TTL_MS)
            close()
            _state.value = State(expired = true, pending = _state.value.pending)
        }
    }

    /** Sunucu + UDP dinleyici + NSD kaydı; [code] null ise yalnız eşleşmiş cihazları kabul eder. */
    @Synchronized
    private fun startServer(code: String?): Boolean {
        val srv = LocalSyncServer(code, handler, log = { Log.i(TAG, it) }, pairs = pairs, remotePlay = remotePlay, remoteInput = remoteInput)
        try {
            srv.start()
        } catch (e: IOException) {
            Log.w(TAG, "sync server start failed: ${e.javaClass.simpleName}")
            _state.update { it.copy(code = null, phase = Phase.Failed, error = Error.PortBusy) }
            return false
        }
        srv.onCodeLocked = {
            ttlJob?.cancel()
            _state.update { it.copy(code = null, phase = Phase.Failed, error = Error.Locked) }
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
        return true
    }

    /**
     * Uygulama ön plana geldi: eşleşmiş cihaz varsa sunucu kodsuz açılır (karşı cihaz kod girmeden eşitler, ileride
     * "TV'de aç" ve kumanda da bu kanaldan gelir). Eşleşme kapalıysa ya da eşleşme yoksa bir şey yapılmaz.
     */
    fun startPairedHosting() {
        if (pairs == null) return
        scope.launch(Dispatchers.IO) {
            synchronized(this@LocalSyncController) {
                pairedHosting = true
                if (hasPairs && server?.isRunning != true) startServer(code = null)
            }
        }
    }

    /** Uygulama arka plana geçti: eşli sunucu da durur. */
    @Synchronized
    fun stopPairedHosting() {
        pairedHosting = false
        if (!wanted) stopHosting()
    }

    /** Kart kapandı / uygulama arka plana geçti / süre doldu: kod geçersiz; eşli sunucu istendiyse kodsuz açık kalır. */
    @Synchronized
    fun close() {
        wanted = false
        ttlJob?.cancel()
        ttlJob = null
        connectJob?.cancel()
        connectJob = null
        if (pairedHosting && hasPairs && server?.isRunning == true) server?.setCode(null) else stopHosting()
        _state.value = State()
    }

    /** Eşleşmeyi bu cihazda kaldırır (karşı taraf bir sonraki denemede 401 alır ve kendi kaydını siler). */
    fun unpair(pairId: String) {
        pairs?.remove(pairId)
    }

    /**
     * Eşleşmiş cihazlarla sessiz eşitleme (açılış, ön plana dönüş, oynatma bitişi). [MIN_AUTO_INTERVAL_MS]'den sık
     * çalışmaz, kodlu bağlantı sürerken çalışmaz; hata arayüzü bozmaz, bir sonraki tetikte yeniden denenir.
     */
    fun autoSync() {
        val store = pairs ?: return
        if (store.pairs.value.isEmpty() || autoJob?.isActive == true || connectJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastAutoAt < MIN_AUTO_INTERVAL_MS) return
        lastAutoAt = now
        autoJob = scope.launch(Dispatchers.IO) {
            for (pair in store.pairs.value.sortedByDescending { it.lastSyncAt }) {
                try {
                    syncPaired(store, pair)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "auto sync failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /** Önce son adres denenir; UDP bulma (birkaç saniye) yalnız orada yanıt yoksa yapılır. */
    private suspend fun syncPaired(store: LocalSyncPairStore, pair: PairRecord) {
        pair.lastHost?.let { if (syncPairedAt(store, pair, it)) return }
        for (host in discover().mapNotNull { it.hostAddress }.filter { it != pair.lastHost }.distinct()) {
            if (syncPairedAt(store, pair, host)) return
        }
    }

    /** true: iş bitti (eşitlendi ya da eşleşme kaldırılmış); false: bu adreste yok, sıradakini dene. */
    private suspend fun syncPairedAt(store: LocalSyncPairStore, pair: PairRecord, host: String): Boolean {
        val local = data.export(includePins = true)
        val remoteJson = try {
            client.exchangePaired(host, LocalSyncProtocol.PORT, pair, LocalSyncMerge.encode(local))
        } catch (e: LocalSyncException) {
            // Son adreste 401: karşı taraf eşleşmeyi kaldırmış; başka adreste 401 başka bir Play cihazıdır.
            if (e.kind == LocalSyncException.Kind.WrongCode && host == pair.lastHost) {
                Log.i(TAG, "auto sync: pairing removed on the other device")
                store.remove(pair.pairId)
                return true
            }
            return false
        }
        val plan = LocalSyncMerge.plan(local, LocalSyncMerge.decodeTransfer(remoteJson), remoteIsAuthority = true, localActiveProfileId = data.activeProfileId())
        data.apply(plan, preserveProfileSecrets = false, repairPlugins = true, installPlugins = autoInstallSources)
        store.update(pair.pairId) { it.copy(lastHost = host, lastSyncAt = System.currentTimeMillis()) }
        Log.i(TAG, "auto sync done (${plan.missingSources.size} sources missing)")
        if (plan.missingSources.isNotEmpty()) {
            if (autoInstallSources) installSources(plan.missingSources) else _state.update { it.copy(pending = plan.missingSources) }
        }
        return true
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
                val local = data.export(includePins = true)
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
                    rememberPeer(host.hostAddress!!, code)
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

    /** Kodlu eşitlemeden sonra kalıcı eşleşme (karşı sürüm bilmiyorsa sessizce atlanır; kod 3 dk geçerli). */
    private suspend fun rememberPeer(host: String, code: String) {
        val store = pairs ?: return
        try {
            store.paired(client.pair(host, LocalSyncProtocol.PORT, code, store.identity))
            Log.i(TAG, "paired with peer")
        } catch (e: LocalSyncException) {
            Log.i(TAG, "pairing skipped: ${e.kind}")
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
        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority, localActiveProfileId = data.activeProfileId())
        data.apply(plan, preserveProfileSecrets = false, repairPlugins = true, installPlugins = autoInstallSources)
        val counts = data.counts()
        val pending = if (autoInstallSources) emptyList() else plan.missingSources
        _state.update { it.copy(phase = Phase.Done, result = counts, pending = pending, installFailed = 0) }
        if (autoInstallSources && plan.missingSources.isNotEmpty()) installSources(plan.missingSources)
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
            val local = data.export(includePins = true)
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
        scope.launch { installSources(items) }
    }

    private suspend fun installSources(items: List<SourceTransferItem>) {
        _state.update { it.copy(installing = true) }
        var failed = 0
        for (item in items) {
            if (!data.install(item)) failed++
        }
        // Yeni kurulan eklentinin kayıtları bir sonraki eşitlemeyi beklemeden ona taşınır.
        if (failed < items.size) data.repairHistoryPlugins()
        val counts = data.counts()
        Log.i(TAG, "sources installed: ${items.size - failed}/${items.size}")
        _state.update { s ->
            s.copy(installing = false, pending = emptyList(), installFailed = failed, result = counts)
        }
    }

    fun dismissPending() = _state.update { it.copy(pending = emptyList()) }

    private companion object {
        const val TAG = "PlaySync"
        const val NSD_TIMEOUT_MS = 4_000L
        const val MIN_AUTO_INTERVAL_MS = 30_000L
        const val ERROR_NO_PLUGIN = "no_plugin"
        const val REPLY_OK = """{"ok":true}"""
        const val REPLY_NO_PLUGIN = """{"error":"$ERROR_NO_PLUGIN"}"""
        const val REPLY_BAD = """{"error":"bad_request"}"""
        const val ERROR_UNSUPPORTED = "unsupported"
        const val REPLY_UNSUPPORTED = """{"error":"$ERROR_UNSUPPORTED"}"""
    }
}
