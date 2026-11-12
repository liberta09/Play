package com.kaan.play.ui.player

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaan.play.data.download.Probe
import com.kaan.play.data.stream.SourceMediaRules
import com.kaan.play.data.match.ContentDurationCheck
import com.kaan.play.data.match.ContentKind
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.player.AutoRetryPolicy
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SlowHostMemory
import com.kaan.play.data.stream.RankContext
import com.kaan.play.data.stream.SourceRanking
import com.kaan.play.data.stream.SourcePref
import com.kaan.play.data.stream.StartDecision
import com.kaan.play.data.stream.StartPolicy
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.StreamOwnership
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamVerdicts
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.stream.WrongContentBlocker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal const val PLAYBACK_DISCOVERY_GRACE_MS = 10_000L

/** Oynatıcının kaynak durumu. */
enum class SourcePhase {
    /** Henüz başlamadı. */
    Idle,

    /** Kaynaklar aranıyor, uygun ilk kaynak (ya da dublajlı kaynak için en çok 3 sn) bekleniyor: "Kaynaklar yükleniyor". */
    Resolving,

    /** Bir kaynak oynatıcıya verildi, ilk kare bekleniyor. */
    Starting,

    /** İlk kare geldi, oynuyor. */
    Playing,

    /** Tüm kaynaklar başarısız (ya da hiç kaynak yok): "Başka kaynak seç". */
    Failed,
}

/** Kullanıcıya kısa süre gösterilen kaynak bildirimi; [detail] ayrıntı satırıdır (hata kodu), ana ileti sade kalır. */
sealed interface SourceNotice {
    /** Elle seçilen kaynak açılmadı, önceki kaynağa dönüldü. */
    data class SwitchFailed(val detail: String) : SourceNotice

    /** Otomatik başlangıçta bozuk kaynak atlandı, sıradakine geçildi. */
    data class Skipped(val detail: String) : SourceNotice

    /** Kaynağın videosu istenen içerikle bağdaşmıyordu (süre uyuşmuyor): atlandı, sıradakine geçildi / önceki kaynağa dönüldü. */
    data object Mismatch : SourceNotice

    /** Kaynak takıldığı için otomatik olarak başka kaynağa geçildi. */
    data object StallSwitched : SourceNotice

    /** Kullanıcının elle seçtiği kaynak yavaşladı (otomatik değiştirilmez). */
    data object ManualSlow : SourceNotice

    /** Oynatma sırasında akış ağ hatasıyla koptu, aynı kaynak kaldığı konumdan bir kez yeniden deneniyor. */
    data class RetryingCurrent(val detail: String) : SourceNotice

    /** Oynatma sırasında kaynak koptu, sıradaki kaynak deneniyor. */
    data class StreamDropped(val detail: String) : SourceNotice

    /** Kullanıcının elle seçtiği kaynak koptu, otomatik olarak başka kaynağa geçildi. */
    data object SourceSwitched : SourceNotice
    data object DubSwitched : SourceNotice

    /** Yanlış içerik veren kaynak bu dizi/film için engellendi ve sıradaki kaynak açılıyor. */
    data object WrongContentBlockedSwitching : SourceNotice

    /** Yanlış içerik veren kaynak engellendi; diğer sağlayıcılardan alternatif bekleniyor. */
    data object WrongContentBlockedWaiting : SourceNotice

    /** Yanlış içerik veren kaynak engellendi ve şu anda başka kaynak kalmadı. */
    data object WrongContentBlockedNone : SourceNotice

    /** Ses senkronu bozuk bildirildi; sıradaki kaynağa geçiliyor. */
    data object AudioSyncSwitching : SourceNotice

    /** Ses senkronu bozuk bildirildi ancak geçilecek başka kaynak bulunamadı. */
    data object AudioSyncNoOtherSource : SourceNotice

    /** Ağ koptu, cihaz çevrimdışı: internet bekleniyor. */
    data object WaitingForInternet : SourceNotice
}

/**
 * Oynatıcının kaynak seçim mantığı (Android'den bağımsız, birim testli; oynatıcıya [load] ile bağlanır).
 *
 * **Otomatik başlangıç** ([autoSource]): kaynaklar arka planda yüklenirken [StartPolicy] uygun ilk kaynağı seçer (Türkçe dublaj önceliği
 * açıkken dublajlı kaynak için en çok 3 sn beklenir; dublajsızsa altyazılıyla başlanır). **Başarısızlık:** kaynak [FIRST_FRAME_TIMEOUT_MS]
 * (torrentte [TORRENT_FIRST_FRAME_TIMEOUT_MS]) içinde ilk kareyi vermez ya da hata verirse sıradaki kaynağa geçilir; hepsi tükenirse
 * [SourcePhase.Failed]. **Elle seçim** ([pick]): kaldığı saniyeden yeni kaynağa geçilir, açılmazsa önceki kaynağa dönülür
 * ([SourceNotice.SwitchFailed]); seçim film/dizi için hatırlanır ([remember]) ve sonraki bölümde önce denenir.
 * **Geç gelen dublaj:** altyazılı başlanmışsa ve sonradan dublajlı kaynak gelirse [dubSuggestion] dolar (oynatma kesilmez).
 * Dosya boyutları ([sizeProbe]: HEAD, torrent üst bilgisi [reportSize]) bulundukça [sizes]'a girer ve sıralamada kullanılır.
 *
 * **İçerik güvencesi:** yalnızca bu içeriğin damgasını taşıyan kaynaklar kabul edilir ([StreamOwnership]; yedek listeye başka içeriğin
 * kaynağı karışsa bile atılır). Oynatıcı süreyi öğrenince [onDuration]: süre TMDB süresiyle bağdaşmıyorsa ([ContentDurationCheck]; ör. 3 saatlik
 * film yerine 33 dakikalık dizi bölümü) kaynak reddedilir, [verdicts]'e yazılır, listeden çıkar ve sıradakine geçilir; kalan kaynak yoksa
 * "kaynak bulunamadı" ekranı gelir.
 */
class SourceCoordinator(
    private val scope: CoroutineScope,
    private val request: StreamRequest,
    private val streamsFor: (StreamRequest) -> Flow<StreamResult>,
    private val askedFor: suspend (StreamRequest) -> List<StreamSource> = { emptyList() },
    private val preferenceFor: suspend (StreamRequest) -> SourcePref? = { null },
    private val remember: suspend (StreamRequest, PlayableStream) -> Unit = { _, _ -> },
    /** Dosya boyutu (HEAD ile); bulunamazsa null. Hata fırlatmamalıdır. */
    private val sizeProbe: suspend (PlayableStream) -> Long? = { null },
    /** İçeriğin TMDB süresi (film: süre, dizi: bölüm süresi; dk); bilinmiyorsa null. Hata fırlatmamalıdır. */
    private val runtimeFor: suspend (StreamRequest) -> Int? = { null },
    private val verdicts: StreamVerdicts = StreamVerdicts(),
    private val maxHeight: Int = 1080,
    private val preferDub: Boolean = true,
    private val autoSource: Boolean = true,
    private val autoDubSwitch: Boolean = true,
    private val showDubSuggestion: Boolean = autoDubSwitch,
    private val revertOnSelectedFailure: Boolean = true,
    /** Otomatik deneme sayısı sınırı (`null` = Sınırsız). Yalnızca otomatik seçilen kaynak denemelerini sınırlar. */
    private val autoRetryLimit: Int? = null,
    private val loadBlocked: suspend () -> Unit = {},
    private val isSourceBlocked: (StreamSource) -> Boolean = { false },
    private val persistSourceBlock: suspend (StreamSource) -> Unit = {},
    private val persistAudioSyncReport: suspend (StreamSource) -> Unit = {},
    private val isBlacklistedHost: (String) -> Boolean = { false },
    private val onSourceFirstFrame: suspend (PlayableStream) -> Unit = {},
    private val onSourceFailure: suspend (PlayableStream, String) -> Unit = { _, _ -> },
    /** Kaynağı oynatıcıya verir: ([stream], başlangıç konumu ms). */
    private val load: (PlayableStream, Long) -> Unit,
    /** Yanlış içerik engellendiğinde alternatif beklenirken/kalmadığında oynatıcıyı duraklatır. */
    private val pausePlayback: () -> Unit = {},
    private val position: () -> Long = { 0L },
    /** Tekdüze saat (ms); testte sanal zaman verilir. */
    private val clock: () -> Long,
    /** Medya içeriği yoklaması (HTML/sahte kaynak engeli; oynatmayı en çok 2 sn geciktirebilir). */
    private val sourceProbe: (suspend (PlayableStream) -> Probe)? = null,
    private val isOffline: Boolean = false,
    private val isWatchPartyGuest: () -> Boolean = { false },
    private val onAutoDubSwitch: ((previous: PlayableStream, next: PlayableStream) -> Unit)? = null,
    private val rankingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    var phase by mutableStateOf(SourcePhase.Idle)
        private set

    /** Şu an oynatılan (ya da denenen) kaynak. */
    var current by mutableStateOf<PlayableStream?>(null)
        private set

    /** Gelen tüm kaynaklar (geliş sırasıyla, aynı adres bir kez). Panel bunu [ranked]/[SourceRanking.sorted] ile gösterir. */
    private val _streams = mutableStateOf<List<PlayableStream>>(emptyList())
    var streams: List<PlayableStream>
        get() = _streams.value
        private set(value) {
            _streams.value = value
            invalidateRankCache()
        }

    /** Sonradan bulunan dosya boyutları (adres → bayt). */
    private val _sizes = mutableStateOf<Map<String, Long>>(emptyMap())
    var sizes: Map<String, Long>
        get() = _sizes.value
        private set(value) {
            _sizes.value = value
            invalidateRankCache()
        }

    var loading by mutableStateOf(false)
        private set

    /** Sorulan kaynaklar (cevaplardan önce bilinir) ve cevabı gelenler / yanıt vermeyenler. */
    var asked by mutableStateOf<List<StreamSource>>(emptyList())
        private set
    var answered by mutableStateOf<Set<Long>>(emptySet())
        private set
    var failedSources by mutableStateOf<List<StreamSource>>(emptyList())
        private set

    val askedCount: Int
        get() = asked.size

    val answeredCount: Int
        get() = if (asked.isEmpty()) answered.size else asked.count { it.addonId in answered }

    fun resolveElapsedSeconds(): Int {
        val started = resolveElapsedBaseAt ?: return 0
        if (phase != SourcePhase.Resolving) return 0
        return ((clock() - started) / 1_000L).toInt().coerceAtLeast(0)
    }

    var notice by mutableStateOf<SourceNotice?>(null)

    fun retryCurrentStream(startMs: Long) {
        val currentStream = current ?: return
        hasRetriedCurrentStream = true
        revertTo = null
        switching = false
        notice = SourceNotice.RetryingCurrent("Ağ geri geldi")
        lastStartMs = startMs
        firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        begin(currentStream, startMs)
    }

    /** Elle kaynak değişimi sürüyor (dönen yükleme göstergesi). */
    var switching by mutableStateOf(false)
        private set

    /** "Dublajlı kaynak bulundu, geçilsin mi?" önerisi; null: yok. */
    var dubSuggestion by mutableStateOf<PlayableStream?>(null)
        private set

    /** Şu an oynatılan kaynakta Türkçe ses çalıyor mu (etiketsiz ama gömülü/doğrulanmış ses). */
    private val _currentAudioTurkish = mutableStateOf(false)
    val currentAudioTurkish: Boolean get() = _currentAudioTurkish.value

    private val _audioTracksEvaluated = mutableStateOf(false)
    val audioTracksEvaluated: Boolean get() = _audioTracksEvaluated.value

    var firstFrameMs = 0L
        private set

    fun setAudioTracksEvaluated(evaluated: Boolean = true) {
        _audioTracksEvaluated.value = evaluated
        if (phase == SourcePhase.Playing) {
            maybeSuggestDub()
        }
    }

    private val _userAudioChosen = mutableStateOf(false)
    val userAudioChosen: Boolean get() = _userAudioChosen.value

    fun setUserAudioChosen(chosen: Boolean) {
        _userAudioChosen.value = chosen
    }

    var autoDubSwitchCount by mutableStateOf(0)
        private set
    private var lastAutoDubSwitchMs = 0L
    private var scheduledDubSwitchJob: Job? = null

    var midPlaybackSwitches by mutableStateOf(0)
        private set
    var lastWorkingStream: PlayableStream? = null
        private set
    private var hasRetriedCurrentStream = false

    private var preference: SourcePref? = null
        set(value) {
            field = value
            invalidateRankCache()
        }
    private var userPicked = false
    private var autoAttempts = 0
    private var suggestionDismissed = false
    private var finished = false
    private var resolveStartedAt = 0L
    private var resolveElapsedBaseAt: Long? = null
    private var collectJob: Job? = null
    private var firstFrameJob: Job? = null
    private var probeJob: Job? = null
    private var revertTo: PlayableStream? = null
    private var revertPositionMs = 0L
    private var lastStartMs = 0L
    private var activeDubWaitMs: Long = StartPolicy.DUB_WAIT_MS
    private var fallbackDubWaitJob: Job? = null
    private var playbackPriorityCancelledDiscovery = false
    private var discoveryStopJob: Job? = null

    /**
     * Oynarken kaynak değiştirilince (içerik reddi, elle seçim) eski kaynaktan kalan "ilk kare" olayı birkaç ms sonra yeni kaynağa yazılırdı:
     * faz erken "Playing" olur, yeni kaynak açılmazsa yedeğe düşülmezdi. Bu süreden önce gelen ilk kare olayı yok sayılır.
     */
    private var firstFrameNotBefore = 0L
    private val attempted = LinkedHashSet<String>()
    private val sessionBlockedPlugins = HashSet<String>()
    private val requestKey = StreamOwnership.keyOf(request)
    private var expectedRuntime: Int? = null
    private var runtimeJob: Job? = null
    private val durationChecked = HashSet<String>()
    private val queue = ArrayDeque<PlayableStream>()
    private val probed = HashSet<String>()
    private val probeGate = Semaphore(PROBE_PARALLEL)
    private var sizeProbeJob: Job? = null
    private val pendingSizeProbes = LinkedHashMap<String, PlayableStream>()

    val isLive: Boolean
        get() = request.addonType in listOf("tv", "channel")

    val ctx: RankContext
        get() = RankContext(
            maxHeight = maxHeight,
            preferDub = preferDub,
            preference = preference,
            sizes = sizes,
            isSlowHost = { url -> SlowHostMemory.isSlow(url, clock()) },
            isBlacklistedHost = isBlacklistedHost,
            isLive = isLive,
        )

    @Volatile
    private var rankedCache: List<PlayableStream>? = null
    internal var rankComputationCount = 0

    /** Önerilen sıra (başlangıç sırası). */
    fun ranked(): List<PlayableStream> {
        val cached = rankedCache
        if (cached != null) return cached
        val computed = SourceRanking.recommended(streams, ctx)
        rankComputationCount++
        rankedCache = computed
        return computed
    }

    internal fun invalidateRankCache() {
        rankedCache = null
    }

    fun sizeOf(stream: PlayableStream): Long? = SourceRanking.sizeOf(stream, sizes)

    fun cancelSizeProbes() {
        sizeProbeJob?.cancel()
        sizeProbeJob = null
        pendingSizeProbes.clear()
    }

    /** Kanal geçişi/kapanış gibi durumlarda tüm aktif kaynak arama ve bekleyen işleri durdurur. */
    fun cancel() {
        finished = true
        collectJob?.cancel()
        firstFrameJob?.cancel()
        probeJob?.cancel()
        runtimeJob?.cancel()
        scheduledDubSwitchJob?.cancel()
        fallbackDubWaitJob?.cancel()
        discoveryStopJob?.cancel()
        cancelSizeProbes()
    }


    // ---- başlatma ----

    /**
     * [initial] verilmişse (elle seçim ya da sonraki bölümün seçilmiş kaynağı) o oynatılır, [fallbacks] yedek olur; verilmemişse
     * ([autoSource]) kaynaklar aranıp uygun ilki seçilir. [startMs]: kaldığı konum.
     */
    fun start(initial: PlayableStream?, fallbacks: List<PlayableStream>, startMs: Long) {
        cancelSizeProbes()
        autoAttempts = 0
        lastStartMs = startMs
        queue.clear()
        queue.addAll(fallbacks.filter(::acceptable))
        runtimeJob = scope.launch {
            expectedRuntime = try {
                runtimeFor(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        scope.launch {
            try {
                loadBlocked()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            if (sessionBlockedPlugins.isNotEmpty() || fallbacks.any { isBlockedSource(it.source) }) {
                queue.removeAll { isBlockedSource(it.source) }
            }
            preference = try {
                preferenceFor(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            // Başka içeriğin kaynağı verilmişse yok sayılır: kaynaklar bu içerik için baştan aranır.
            fallbackDubWaitJob?.cancel()
            activeDubWaitMs = StartPolicy.DUB_WAIT_MS
            val first = initial?.takeIf(::acceptable)
            if (first != null) {
                begin(first, startMs)
                if (autoSource) ensureSources()
            } else {
                phase = SourcePhase.Resolving
                resolveStartedAt = clock()
                resolveElapsedBaseAt = resolveStartedAt
                ensureSources()
                if (preferDub) {
                    scope.launch {
                        delay(StartPolicy.DUB_WAIT_MS)
                        onChange()
                    }
                }
            }
        }
    }

    /** Kaynak listesini (henüz başlamadıysa) arka planda yüklemeye başlar; panel açılınca da çağrılır. */
    fun ensureSources() {
        if (collectJob?.isActive == true || finished) return
        loading = true
        asked = emptyList()
        answered = emptySet()
        failedSources = emptyList()
        collectJob = scope.launch {
            asked = try {
                askedFor(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            streamsFor(request)
                .catch { t ->
                    Log.w(TAG, "streamsFor hatası: ${t.message}", t)
                }
                .onCompletion { cause ->
                    loading = false
                    if (cause is CancellationException && playbackPriorityCancelledDiscovery) {
                        playbackPriorityCancelledDiscovery = false
                        finished = false
                        collectJob = null
                        discoveryStopJob = null
                        pendingOnChangeJob?.cancel()
                        pendingOnChangeJob = null
                        return@onCompletion
                    }
                    finished = true
                    discoveryStopJob?.cancel()
                    discoveryStopJob = null
                    pendingOnChangeJob?.cancel()
                    pendingOnChangeJob = null
                    onChange()
                }
                .collect { result ->
                    answered = answered + result.source.addonId
                    when (result) {
                        is StreamResult.Found -> {
                            val acc = result.streams.filter(::acceptable)
                            handleStreamResult(acc)
                        }
                        is StreamResult.Failed -> {
                            if (failedSources.none { it.addonId == result.source.addonId }) {
                                failedSources = failedSources + result.source
                            }
                            onChange()
                        }
                    }
                }
        }
    }

    private var lastOnChangeClock: Long = -1L
    private var pendingOnChangeJob: Job? = null

    private fun canStartImmediately(stream: PlayableStream): Boolean {
        if (!autoSource) return false
        if (!preferDub) return true
        if (stream.dubbed) return true
        if (matchesPreference(stream, preference)) return true
        return false
    }

    private fun matchesPreference(stream: PlayableStream, pref: SourcePref?): Boolean =
        pref != null && stream.source.addonId == pref.addonId

    private fun handleStreamResult(acc: List<PlayableStream>) {
        if (acc.isEmpty()) {
            onChange()
            return
        }
        val updated = (streams + acc).distinctBy { it.url }
        if (updated.size == streams.size) {
            onChange()
            return
        }
        streams = updated

        val immediateCandidate = (phase == SourcePhase.Resolving) && acc.any { canStartImmediately(it) }
        val now = clock()
        val timeSinceLast = if (lastOnChangeClock < 0) Long.MAX_VALUE else (now - lastOnChangeClock)

        if (immediateCandidate || timeSinceLast >= BATCH_WINDOW_MS) {
            pendingOnChangeJob?.cancel()
            pendingOnChangeJob = null
            lastOnChangeClock = now
            onChange()
        } else {
            if (pendingOnChangeJob == null || !pendingOnChangeJob!!.isActive) {
                pendingOnChangeJob = scope.launch {
                    delay(BATCH_WINDOW_MS)
                    pendingOnChangeJob = null
                    lastOnChangeClock = clock()
                    val currentStreams = streams
                    val currentCtx = ctx
                    val computed = withContext(rankingDispatcher) {
                        SourceRanking.recommended(currentStreams, currentCtx)
                    }
                    if (rankedCache == null && streams === currentStreams) {
                        rankComputationCount++
                        rankedCache = computed
                    }
                    onChange()
                }
            }
        }
    }

    // ---- olaylar ----

    private fun onChange() {
        probeSizes()
        when (phase) {
            SourcePhase.Resolving -> {
                // Daha önce denenip başarısız olan ya da kara listeye alınan sunucular otomatik başlangıçta atlanır (elle seçim yine çalışır).
                val candidates = ranked().filter { it.url !in attempted && !isBlacklistedHost(it.url) }
                when (val d = StartPolicy(preferDub, activeDubWaitMs).decide(clock() - resolveStartedAt, candidates, ctx, finished)) {
                    is StartDecision.Start -> {
                        fallbackDubWaitJob?.cancel()
                        activeDubWaitMs = StartPolicy.DUB_WAIT_MS
                        if (!beginAuto(d.stream, lastStartMs)) {
                            phase = SourcePhase.Failed
                            switching = false
                        }
                    }
                    StartDecision.NoSources -> {
                        fallbackDubWaitJob?.cancel()
                        activeDubWaitMs = StartPolicy.DUB_WAIT_MS
                        phase = SourcePhase.Failed
                    }
                    is StartDecision.Wait -> Unit
                }
            }
            SourcePhase.Playing -> maybeSuggestDub()
            SourcePhase.Failed, SourcePhase.Idle, SourcePhase.Starting -> Unit
        }
    }

    private fun canAttemptAutoSource(): Boolean =
        AutoRetryPolicy.canTryAnother(autoAttempts, autoRetryLimit)

    private fun beginAuto(stream: PlayableStream, startMs: Long): Boolean {
        if (!canAttemptAutoSource()) return false
        autoAttempts++
        begin(stream, startMs)
        return true
    }

    private fun begin(stream: PlayableStream, startMs: Long) {
        fallbackDubWaitJob?.cancel()
        firstFrameJob?.cancel()
        probeJob?.cancel()
        scheduledDubSwitchJob?.cancel()
        scheduledDubSwitchJob = null
        current = stream
        _currentAudioTurkish.value = false
        _audioTracksEvaluated.value = false
        firstFrameMs = 0L
        attempted += stream.url
        phase = SourcePhase.Starting
        Log.d(TAG, "kaynak başlatılıyor: ${stream.source.addonName} (${StreamLog.hostOf(stream.url)})")

        val probe = sourceProbe
        val isManifest = SourceMediaRules.isStreamingManifest(stream.url, stream.mimeType) ||
            stream.url.lowercase(java.util.Locale.ROOT).contains(".m3u8")
        if (probe == null || TorrentLinks.isMagnet(stream.url) || isManifest) {
            startPlayback(stream, startMs)
            return
        }

        probeJob = scope.launch {
            val isMovie = request.type == MediaType.Movie
            val isLive = request.addonType in listOf("tv", "channel")
            val result = try {
                withTimeoutOrNull(PLAY_PROBE_TIMEOUT_MS) {
                    probe(stream)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

            if (current?.url != stream.url) return@launch

            var eliminate = false
            if (result != null) {
                when (result) {
                    is Probe.Rejected -> {
                        // NotMedia (HTML hata sayfası / JSON) elenmeli; ancak HLS/DASH manifestleri ya da akış bağlantıları
                        // (ör. iframe/embed veya Range reddedenler) erken elenmemeli, doğrudan oynatıcıya devredilmelidir.
                        val isManifest = SourceMediaRules.isStreamingManifest(stream.url, stream.mimeType)
                        if (result.reason == Probe.Reason.NotMedia && !isManifest) {
                            Log.d(TAG, "kaynak yoklamada elendi (${result.reason}): ${stream.source.addonName} (${StreamLog.hostOf(stream.url)})")
                            eliminate = true
                        } else {
                            Log.d(TAG, "kaynak yoklama uyarısı (${result.reason}), oynatıcıya devrediliyor: ${stream.source.addonName}")
                        }
                    }
                    is Probe.Ok -> {
                        result.lengthBytes?.let { len ->
                            if (!isLive && !SourceMediaRules.isAcceptableSize(len, isMovie, stream.url, stream.mimeType)) {
                                Log.d(TAG, "kaynak yoklamada elendi (boyut yetersiz: $len bayt): ${stream.source.addonName}")
                                eliminate = true
                            } else {
                                reportSize(stream.url, len)
                            }
                        }
                    }
                }
            }

            if (eliminate) {
                verdicts.reject(requestKey, stream.url)
                streams = streams.filterNot { it.url == stream.url }
                startFailed("probe-not-media")
                return@launch
            }

            startPlayback(stream, startMs)
        }
    }

    private fun startPlayback(stream: PlayableStream, startMs: Long) {
        if (current?.url != stream.url) return
        load(stream, startMs)
        val timeout = if (TorrentLinks.isMagnet(stream.url)) TORRENT_FIRST_FRAME_TIMEOUT_MS else FIRST_FRAME_TIMEOUT_MS
        firstFrameJob = scope.launch {
            delay(timeout)
            if (phase == SourcePhase.Starting) {
                startFailed("timeout")
            }
        }
    }

    /** Oynatıcı ilk kareyi çizdi: kaynak çalışıyor. */
    fun onFirstFrame() {
        if (clock() < firstFrameNotBefore) {
            Log.d(TAG, "ilk kare olayı yok sayıldı (eski kaynaktan kalma): ${current?.source?.addonName}")
            return
        }
        Log.d(TAG, "ilk kare: ${current?.source?.addonName} (faz=$phase)")
        if (phase != SourcePhase.Starting) return
        firstFrameJob?.cancel()
        probeJob?.cancel()
        cancelSizeProbes()
        phase = SourcePhase.Playing
        firstFrameMs = clock()
        switching = false
        revertTo = null
        val cur = current
        if (cur != null && (!cur.dubbed || !preferDub)) {
            lastWorkingStream = cur
        }
        cur?.let { stream ->
            scope.launch {
                try {
                    onSourceFirstFrame(stream)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                invalidateRankCache()
            }
        }
        stopSourceDiscoveryForPlayback()
        onChange()
    }

    private fun recordFailureAsync(stream: PlayableStream?, detail: String) {
        if (stream == null) return
        scope.launch {
            try {
                onSourceFailure(stream, detail)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            invalidateRankCache()
        }
    }

    /** Türkçe ses izi doğrulandıktan sonra bu dublajlı akışı güvenli geri dönüş adayı olarak işaretler. */
    fun markCurrentVerifiedWorking() {
        val cur = current ?: return
        if (phase == SourcePhase.Playing) {
            lastWorkingStream = cur
        }
    }

    fun dubbedCandidatesExcluding(excludedUrls: Set<String>): List<PlayableStream> =
        ranked().filter { isAutoCandidate(it) && it.dubbed && it.url !in excludedUrls && it.url != current?.url }

    fun alternativeCandidatesExcluding(excludedUrls: Set<String>): List<PlayableStream> =
        (queue + ranked()).distinctBy { it.url }.filter { isAutoCandidate(it) && it.url !in excludedUrls && it.url != current?.url }

    /** Doğrulanmamış dublajlı kaynaktan sıradaki dublajlıya ya da hızlı alternatife kaldığı saniyeden geçer. */
    fun switchAfterUnverifiedDub(next: PlayableStream) {
        if (next.url == current?.url) return
        val revertingToWorking = next.url == lastWorkingStream?.url
        if (!revertingToWorking && !canAttemptAutoSource()) {
            phase = SourcePhase.Failed
            switching = false
            return
        }
        fallbackDubWaitJob?.cancel()
        discoveryStopJob?.cancel()
        dubSuggestion = null
        notice = null
        revertTo = null
        val pos = position().takeIf { it > 0 } ?: lastStartMs
        lastStartMs = pos
        switching = true
        attempted -= next.url
        firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        if (!revertingToWorking) autoAttempts++
        begin(next, pos)
    }

    /**
     * Oynatıcı hata verdi. İlk kare gelmeden önceyse sıradaki kaynağa geçilir / önceki kaynağa dönülür ve true döner (hata ekranı
     * gösterilmez); oynarken gelen hata arayüze bırakılır (false).
     */
    private fun stopSourceDiscoveryForPlayback() {
        val job = collectJob ?: return
        if (!job.isActive) return
        discoveryStopJob?.cancel()
        discoveryStopJob = scope.launch {
            delay(PLAYBACK_DISCOVERY_GRACE_MS)
            if (phase != SourcePhase.Playing || collectJob !== job || !job.isActive) return@launch
            playbackPriorityCancelledDiscovery = true
            Log.d(TAG, "kaynak araması oynatmaya öncelik için durduruluyor (ilk kareden ${PLAYBACK_DISCOVERY_GRACE_MS}ms sonra)")
            job.cancel()
        }
    }

    /** PlaybackController anlık HTTP hatasında akışı tazelerken kısa bilgi bildirimi gösterir. */
    fun onTransientRefresh(detail: String) {
        if (!isLive && phase == SourcePhase.Playing) {
            notice = SourceNotice.RetryingCurrent(detail)
        }
    }

    fun onFailure(detail: String): Boolean {
        Log.d(TAG, "oynatıcı hatası: ${current?.source?.addonName} (faz=$phase) $detail")
        Log.d("PlayMatch", "oynatıcı hatası: ${current?.source?.addonName} ($detail) faz=$phase")

        if (phase == SourcePhase.Playing) {
            val currentStream = current ?: return false
            val queuedList = queue.filter { isAutoCandidate(it) }
            val rankedList = ranked().filter { isAutoCandidate(it) && it.url != currentStream.url }
            val candidates = (queuedList + rankedList).distinctBy { it.url }
            val decision = MidPlaybackRecoveryRules.evaluate(
                phase = phase,
                currentStream = currentStream,
                hasRetriedCurrentStream = hasRetriedCurrentStream,
                switchCount = midPlaybackSwitches,
                candidates = if (canAttemptAutoSource()) candidates else emptyList(),
                isManual = userPicked,
                isOffline = false,
                isLeaving = false,
                isLive = isLive,
                isNetworkError = MidPlaybackRecoveryRules.isNetworkError(detail),
            )
            Log.d("PlayMatch", "oynatirken kurtarma karari: $decision (gecis=$midPlaybackSwitches)")
            when (decision) {
                is MidPlaybackRecoveryRules.Decision.RetrySame -> {
                    hasRetriedCurrentStream = true
                    revertTo = null
                    switching = false
                    notice = SourceNotice.RetryingCurrent(detail)
                    val pos = position().takeIf { it > 0 } ?: lastStartMs
                    lastStartMs = pos
                    firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
                    begin(decision.stream, pos)
                    return true
                }
                is MidPlaybackRecoveryRules.Decision.Switch -> {
                    recordFailureAsync(currentStream, detail)
                    hasRetriedCurrentStream = false
                    midPlaybackSwitches++
                    queue.removeAll { it.url == decision.candidate.url }
                    notice = when (decision.notice) {
                        MidPlaybackRecoveryRules.NoticeType.SOURCE_SWITCHED -> SourceNotice.SourceSwitched
                        MidPlaybackRecoveryRules.NoticeType.STREAM_DROPPED_TRYING_NEXT -> SourceNotice.StreamDropped(detail)
                    }
                    val pos = position().takeIf { it > 0 } ?: lastStartMs
                    lastStartMs = pos
                    switching = true
                    firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
                    autoAttempts++
                    begin(decision.candidate, pos)
                    return true
                }
                MidPlaybackRecoveryRules.Decision.Fail -> {
                    recordFailureAsync(currentStream, detail)
                    phase = SourcePhase.Failed
                    return false
                }
            }
        }

        if (phase != SourcePhase.Starting) return false
        firstFrameJob?.cancel()
        probeJob?.cancel()
        startFailed(detail)
        return true
    }

    private fun startFailed(detail: String, mismatch: Boolean = false) {
        discoveryStopJob?.cancel()
        if (phase == SourcePhase.Playing) {
            Log.d(TAG, "startFailed yok sayıldı (zaten Playing durumunda): $detail")
            return
        }
        val failed = current
        if (failed != null && !mismatch) {
            recordFailureAsync(failed, detail)
        }
        val previous = revertTo
        if (previous != null) {
            // Elle seçilen kaynak açılmadı: önceki kaynağa kaldığı saniyeden dön.
            revertTo = null
            switching = false
            notice = if (mismatch) SourceNotice.Mismatch else SourceNotice.SwitchFailed(detail)
            begin(previous, revertPositionMs)
            return
        }
        if (failed != null) {
            notice = if (mismatch) SourceNotice.Mismatch else SourceNotice.Skipped(detail)
        }
        if (!canAttemptAutoSource()) {
            phase = SourcePhase.Failed
            switching = false
            return
        }
        val queued = nextQueuedCandidate()
        if (queued != null) {
            beginAuto(queued, lastStartMs)
            return
        }
        val next = ranked().firstOrNull { it.url !in attempted && !isBlacklistedHost(it.url) }
        val shouldWaitForDub = preferDub && !mismatch && !finished && (next == null || !next.dubbed)
        if (shouldWaitForDub) {
            phase = SourcePhase.Resolving
            resolveStartedAt = clock()
            if (resolveElapsedBaseAt == null) resolveElapsedBaseAt = resolveStartedAt
            activeDubWaitMs = FALLBACK_DUB_WAIT_MS
            fallbackDubWaitJob?.cancel()
            fallbackDubWaitJob = scope.launch {
                delay(FALLBACK_DUB_WAIT_MS)
                onChange()
            }
            return
        }
        if (next != null) {
            beginAuto(next, lastStartMs)
        } else if (!finished) {
            // Daha kaynak gelebilir: gelince denenir.
            phase = SourcePhase.Resolving
            val now = clock()
            if (resolveElapsedBaseAt == null) resolveElapsedBaseAt = now
            resolveStartedAt = now - StartPolicy.DUB_WAIT_MS
        } else {
            phase = SourcePhase.Failed
            switching = false
        }
    }

    private fun isBlockedSource(source: StreamSource): Boolean =
        WrongContentBlocker.pluginIdentity(source) in sessionBlockedPlugins || isSourceBlocked(source)

    /** Bu içeriğin kaynağı mı (damga) ve oynatma sırasında/engellemeyle reddedilmemiş mi? */
    private fun acceptable(stream: PlayableStream): Boolean {
        if (isBlockedSource(stream.source)) return false
        if (TorrentLinks.isMagnet(stream.url) && !com.kaan.play.data.torrent.TorrentSupport.isBuiltInSupported()) {
            return false
        }
        val size = SourceRanking.sizeOf(stream, sizes)
        val isMovie = request.type == MediaType.Movie
        val isLive = request.addonType in listOf("tv", "channel")
        return StreamOwnership.belongs(stream, request) &&
            !verdicts.isRejected(requestKey, stream.url) &&
            (isLive || SourceMediaRules.isAcceptableSize(size, isMovie, stream.url, stream.mimeType))
    }

    private fun isAutoCandidate(stream: PlayableStream): Boolean =
        acceptable(stream) && stream.url !in attempted && !isBlacklistedHost(stream.url)

    /**
     * Mevcut kaynağın eklentisini bu dizi/film için kalıcı engeller ve sıradaki uygun kaynağa geçer.
     * Canlı TV'de çağrılmaz; alternatif varsa hemen aynı saniyeden açılır.
     */
    fun blockCurrentSourceAndAdvance(): Boolean {
        val stream = current ?: return false
        if (isLive) return false
        val blockedSource = stream.source
        sessionBlockedPlugins += WrongContentBlocker.pluginIdentity(blockedSource)
        scope.launch {
            try {
                persistSourceBlock(blockedSource)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        streams = streams.filterNot { isBlockedSource(it.source) }
        queue.removeAll { isBlockedSource(it.source) }
        if (dubSuggestion?.let { isBlockedSource(it.source) } == true) dubSuggestion = null

        firstFrameJob?.cancel()
        probeJob?.cancel()
        fallbackDubWaitJob?.cancel()
        discoveryStopJob?.cancel()
        revertTo = null

        val pos = position().takeIf { it > 0 } ?: lastStartMs
        lastStartMs = pos
        val queued = nextQueuedCandidate()
        val next = queued ?: ranked().firstOrNull { isAutoCandidate(it) }
        when {
            next != null -> {
                switching = true
                firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
                notice = SourceNotice.WrongContentBlockedSwitching
                begin(next, pos)
                return true
            }
            !finished -> {
                current = null
                switching = false
                phase = SourcePhase.Resolving
                resolveStartedAt = clock()
                resolveElapsedBaseAt = resolveStartedAt
                notice = SourceNotice.WrongContentBlockedWaiting
                pausePlayback()
                ensureSources()
                return false
            }
            else -> {
                current = null
                switching = false
                phase = SourcePhase.Failed
                notice = SourceNotice.WrongContentBlockedNone
                pausePlayback()
                return false
            }
        }
    }

    /**
     * "Ses senkronu bozuk": eklenti için yerel bildirim sayacını artırır (engellemez) ve varsa sıradaki kaynağa
     * kaldığı saniyeden geçer; başka kaynak yoksa mevcut oynatmayı kesmeden kısa bildirim gösterir.
     */
    fun reportAudioSyncAndSwitchNext(): Boolean {
        val stream = current ?: return false
        if (isLive) return false
        scope.launch {
            try {
                persistAudioSyncReport(stream.source)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        val pos = position().takeIf { it > 0 } ?: lastStartMs
        lastStartMs = pos
        val queued = nextQueuedCandidate()
        val next = queued ?: ranked().firstOrNull { isAutoCandidate(it) && it.url != stream.url }
        if (next == null) {
            notice = SourceNotice.AudioSyncNoOtherSource
            return false
        }
        firstFrameJob?.cancel()
        probeJob?.cancel()
        fallbackDubWaitJob?.cancel()
        discoveryStopJob?.cancel()
        revertTo = null
        switching = true
        firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        notice = SourceNotice.AudioSyncSwitching
        begin(next, pos)
        return true
    }

    /**
     * Oynatıcı videonun süresini öğrendi ([durationMs]; canlı/belirsizse çağrılmaz). Süre içeriğin TMDB süresiyle bağdaşmıyorsa
     * kaynak başka bir içerik gösteriyor demektir: reddedilir, listeden çıkar, sıradakine geçilir. Aynı kaynak için bir kez bakılır.
     */
    fun onDuration(durationMs: Long) {
        val stream = current ?: return
        if (phase != SourcePhase.Starting && phase != SourcePhase.Playing) return
        if (!durationChecked.add(stream.url)) return
        if (isLive) {
            if (ContentDurationCheck.isFakeLiveStream(durationMs, isLive)) {
                Log.d(
                    "PlayMatch",
                    "canlı yayında sahte kaynak elendi: kaynak=${stream.source.addonName} (${StreamLog.hostOf(stream.url)}) süre=%.1f dk"
                        .format(java.util.Locale.ROOT, durationMs / 60_000.0),
                )
                verdicts.reject(requestKey, stream.url)
                streams = streams.filterNot { it.url == stream.url }
                firstFrameJob?.cancel()
                phase = SourcePhase.Starting
                firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
                startFailed("fake-live-duration", mismatch = true)
                return
            }
        }
        scope.launch {
            runtimeJob?.join()
            if (current?.url != stream.url || (phase != SourcePhase.Starting && phase != SourcePhase.Playing)) return@launch
            val kind = if (request.type == MediaType.Tv) ContentKind.Tv else ContentKind.Movie
            val verdict = ContentDurationCheck.check(durationMs, expectedRuntime, kind)
            // Tanı (adres yok: kaynak adı + ana makine): süre, beklenen süre, karar.
            Log.d(
                "PlayMatch",
                "süre kontrolü kaynak=${stream.source.addonName} (${StreamLog.hostOf(stream.url)}) süre=%.1f dk beklenen=${expectedRuntime ?: "?"} dk tür=$kind -> $verdict"
                    .format(java.util.Locale.ROOT, durationMs / 60_000.0),
            )
            if (verdict != ContentDurationCheck.Result.Mismatch) return@launch
            verdicts.reject(requestKey, stream.url)
            streams = streams.filterNot { it.url == stream.url }
            firstFrameJob?.cancel()
            phase = SourcePhase.Starting // başarısızlık yolu (sıradaki kaynak / önceki kaynağa dön / hepsi bitti) ortak
            firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
            startFailed("content-mismatch", mismatch = true)
        }
    }

    private fun nextQueuedCandidate(): PlayableStream? {
        while (true) {
            val queued = queue.removeFirstOrNull() ?: break
            if (queued.url !in attempted && !isBlacklistedHost(queued.url)) return queued
        }
        return null
    }

    // ---- kullanıcı eylemleri ----

    /** Panelde "Uygula" ile seçilen kaynak: kaldığı saniyeden geçilir, açılmazsa önceki kaynağa dönülür. */
    fun pick(stream: PlayableStream) {
        if (stream.url == current?.url && phase == SourcePhase.Playing) return
        userPicked = true
        dubSuggestion = null
        notice = null
        discoveryStopJob?.cancel()
        fallbackDubWaitJob?.cancel()
        activeDubWaitMs = StartPolicy.DUB_WAIT_MS
        scope.launch {
            try {
                remember(request, stream)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        preference = SourcePref(stream.source.addonId, stream.source.addonName, null, TorrentLinks.isMagnet(stream.url))
        val previous = current?.takeIf { phase == SourcePhase.Playing || phase == SourcePhase.Starting }
        revertTo = if (revertOnSelectedFailure) previous else null
        revertPositionMs = position().takeIf { it > 0 } ?: lastStartMs
        lastStartMs = revertPositionMs
        switching = previous != null
        // Elle seçim yeniden denenebilsin: daha önce başarısız olduysa da bu sefer dene.
        attempted -= stream.url
        if (previous != null) firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        begin(stream, revertPositionMs)
    }

    fun acceptDubSuggestion() {
        val s = dubSuggestion ?: return
        pick(s)
    }

    fun dismissDubSuggestion() {
        dubSuggestion = null
        suggestionDismissed = true
    }

    fun clearNotice() {
        notice = null
    }

    /** Şu anki kaynakta Türkçe ses çaldığı doğrulanınca/seçilince coordinator bilgilendirilir. */
    fun setCurrentAudioTurkish(isTurkish: Boolean) {
        _currentAudioTurkish.value = isTurkish
        if (isTurkish) {
            dubSuggestion = null
            scheduledDubSwitchJob?.cancel()
            scheduledDubSwitchJob = null
        }
    }

    /** Torrent üst bilgisinden ya da başka yerden bulunan dosya boyutu (adres → bayt). */
    fun reportSize(url: String, bytes: Long) {
        if (isLive) return
        if (bytes <= 0 || sizes[url] == bytes) return
        sizes = sizes + (url to bytes)
        val isMovie = request.type == MediaType.Movie
        val isLive = request.addonType in listOf("tv", "channel")
        val mimeType = streams.firstOrNull { it.url == url }?.mimeType
        if (!isLive && !SourceMediaRules.isAcceptableSize(bytes, isMovie, url, mimeType)) {
            Log.d(TAG, "kaynak boyutu çok küçük (sahte kaynak): $url (${bytes}B)")
            verdicts.reject(requestKey, url)
            streams = streams.filterNot { it.url == url }
            if (current?.url == url && (phase == SourcePhase.Starting || phase == SourcePhase.Playing)) {
                firstFrameJob?.cancel()
                probeJob?.cancel()
                startFailed("size-too-small")
            }
        }
    }

    // ---- iç işler ----

    private fun maybeSuggestDub() {
        if (!autoDubSwitch || !showDubSuggestion || !autoSource || !preferDub || userPicked || userAudioChosen || currentAudioTurkish || notice is SourceNotice.WaitingForInternet) {
            scheduledDubSwitchJob?.cancel()
            scheduledDubSwitchJob = null
            return
        }
        val playing = current ?: return
        if (playing.dubbed) return
        if (isLive || isOffline || isWatchPartyGuest()) return
        if (autoDubSwitchCount >= MAX_AUTO_DUB_SWITCHES) return

        val candidate = ranked().firstOrNull {
            it.dubbed && !SourceRanking.isTooHeavy(it, ctx) && !isBlacklistedHost(it.url) && it.url != playing.url && it.url !in attempted
        } ?: return

        // (a) Mevcut kaynağın ses parçaları en az bir kez değerlendirilmiş olmalı
        if (!audioTracksEvaluated) return

        // (b) İlk kareden sonra en az 1500 ms geçmiş olmalı
        if (firstFrameMs <= 0L) return
        val elapsedSinceFirstFrame = clock() - firstFrameMs
        val remainingFirstFrameDelay = if (elapsedSinceFirstFrame < AUTO_DUB_FIRST_FRAME_DELAY_MS) {
            AUTO_DUB_FIRST_FRAME_DELAY_MS - elapsedSinceFirstFrame
        } else {
            0L
        }

        val elapsedSinceLastSwitch = if (lastAutoDubSwitchMs > 0L) clock() - lastAutoDubSwitchMs else AUTO_DUB_SWITCH_INTERVAL_MS
        val remainingInterval = if (elapsedSinceLastSwitch < AUTO_DUB_SWITCH_INTERVAL_MS) {
            AUTO_DUB_SWITCH_INTERVAL_MS - elapsedSinceLastSwitch
        } else {
            0L
        }

        val delayNeeded = maxOf(remainingFirstFrameDelay, remainingInterval)
        if (delayNeeded > 0L) {
            if (scheduledDubSwitchJob?.isActive != true) {
                scheduledDubSwitchJob = scope.launch {
                    delay(delayNeeded)
                    if (phase == SourcePhase.Playing && audioTracksEvaluated && !currentAudioTurkish) {
                        maybeSuggestDub()
                    }
                }
            }
            return
        }

        scheduledDubSwitchJob?.cancel()
        scheduledDubSwitchJob = null
        applyAutoDubSwitch(playing, candidate)
    }

    fun cancelDubSwitch() {
        scheduledDubSwitchJob?.cancel()
        scheduledDubSwitchJob = null
    }

    private fun applyAutoDubSwitch(old: PlayableStream, candidate: PlayableStream) {
        if (notice is SourceNotice.WaitingForInternet) return
        autoDubSwitchCount++
        lastAutoDubSwitchMs = clock()
        notice = SourceNotice.DubSwitched
        Log.d(TAG, formatAutoDubSwitchLog(old, candidate, autoDubSwitchCount))
        onAutoDubSwitch?.invoke(old, candidate)
        val pos = position().takeIf { it > 0 } ?: lastStartMs
        revertTo = old
        revertPositionMs = pos
        lastStartMs = pos
        switching = true
        firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        begin(candidate, pos)
    }

    private fun probeSizes() {
        if (isLive || phase != SourcePhase.Resolving) {
            cancelSizeProbes()
            return
        }
        val targets = buildList {
            current?.let { add(it) }
            addAll(ranked())
        }.distinctBy { it.url }.take(PROBE_LIMIT)
        for (stream in targets) {
            if (SourceRanking.sizeOf(stream, sizes) != null || probed.contains(stream.url)) continue
            if (!SizeProbing.isProbeable(stream.url, stream.mimeType)) continue
            probed.add(stream.url)
            pendingSizeProbes[stream.url] = stream
        }
        if (sizeProbeJob?.isActive != true && pendingSizeProbes.isNotEmpty()) {
            sizeProbeJob = scope.launch {
                val toProbe = pendingSizeProbes.values.toList()
                pendingSizeProbes.clear()
                for (stream in toProbe) {
                    launch {
                        val bytes = probeGate.withPermit { withTimeoutOrNull(PROBE_TIMEOUT_MS) { sizeProbe(stream) } }
                        if (bytes != null) reportSize(stream.url, bytes)
                    }
                }
            }
        }
    }

    /**
     * Oynatıcı takılma yaşadığında çağrılır; [StallPolicy] kurallarına göre geçiş yapar ya da elle seçimde bildirim çıkarır.
     */
    fun checkStall(
        firstFrameRendered: Boolean,
        isSeeking: Boolean,
        currentStallStartMs: Long?,
        pastStalls: List<StallInterval>,
    ) {
        if (phase != SourcePhase.Playing) return
        val currentStream = current ?: return
        val candidates = ranked().filter { isAutoCandidate(it) && it.url != currentStream.url }
        val decision = StallPolicy.evaluate(
            nowMs = clock(),
            firstFrameRendered = firstFrameRendered,
            isSeeking = isSeeking,
            currentStallStartMs = currentStallStartMs,
            pastStalls = pastStalls,
            currentIsDubbed = currentStream.dubbed,
            isManual = userPicked,
            candidates = candidates,
        )
        when (decision) {
            StallDecision.None -> Unit
            StallDecision.NotifyManualSlow -> {
                if (notice != SourceNotice.ManualSlow) {
                    notice = SourceNotice.ManualSlow
                }
            }
            is StallDecision.Switch -> applyStallSwitch(currentStream, decision.candidate, decision.reason)
        }
    }

    /**
     * Test ve doğrulama için sentetik takılma geçişi tetikler (yalnızca debug).
     */
    fun forceStallForTest(durationMs: Long = 11_000L) {
        val currentStream = current ?: return
        val now = clock()
        val candidates = ranked().filter { isAutoCandidate(it) && it.url != currentStream.url }
        val decision = StallPolicy.evaluate(
            nowMs = now,
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = now - durationMs,
            pastStalls = emptyList(),
            currentIsDubbed = currentStream.dubbed,
            isManual = userPicked,
            candidates = candidates,
        )
        if (decision is StallDecision.Switch) {
            applyStallSwitch(currentStream, decision.candidate, decision.reason)
        }
    }

    private fun applyStallSwitch(old: PlayableStream, newStream: PlayableStream, reason: SwitchReason) {
        SlowHostMemory.markSlow(old.url, clock())
        invalidateRankCache()
        Log.d(
            TAG,
            "takılma geçişi eski=${old.source.addonName} yeni=${newStream.source.addonName} neden=${reason.logValue}",
        )
        notice = SourceNotice.StallSwitched
        val pos = position().takeIf { it > 0 } ?: lastStartMs
        revertTo = old
        revertPositionMs = pos
        lastStartMs = pos
        switching = true
        firstFrameNotBefore = clock() + STALE_FRAME_GUARD_MS
        begin(newStream, pos)
    }

    companion object {
        private const val TAG = "PlayMatch"

        fun formatAutoDubSwitchLog(
            old: PlayableStream,
            candidate: PlayableStream,
            switchCount: Int,
        ): String {
            val oldHost = StreamLog.hostOf(old.url)
            val newHost = StreamLog.hostOf(candidate.url)
            return "otomatik dublaj gecisi: eski=${old.source.addonName} ($oldHost) yeni=${candidate.source.addonName} ($newHost) (gecis=$switchCount)"
        }
        /** Kaynak değişiminden sonra bu süre içinde gelen ilk kare olayı eski kaynağa aittir. */
        const val STALE_FRAME_GUARD_MS = 300L
        const val PLAY_PROBE_TIMEOUT_MS = 1_500L
        const val FIRST_FRAME_TIMEOUT_MS = 10_000L

        /** Torrentte üst bilgi ve ilk parçalar için eş bulmak gerekir: 10 sn çoğu zaman yetmez. */
        const val TORRENT_FIRST_FRAME_TIMEOUT_MS = 40_000L
        const val BATCH_WINDOW_MS = 200L
        const val FALLBACK_DUB_WAIT_MS = 3_500L
        const val PROBE_LIMIT = 24
        const val PROBE_PARALLEL = 6
        const val PROBE_TIMEOUT_MS = 8_000L
        const val MAX_AUTO_DUB_SWITCHES = 3
        const val AUTO_DUB_SWITCH_INTERVAL_MS = 10_000L
        const val AUTO_DUB_FIRST_FRAME_DELAY_MS = 1_500L
    }
}

/** Dosya boyutu aramasının (HEAD) uygunluk kuralları (Android'den bağımsız). */
object SizeProbing {
    /** Düz http(s) dosyası mı? Torrent (üst bilgiden), HLS/DASH (liste, dosya değil) için HEAD anlamsızdır. */
    fun isProbeable(url: String, mimeType: String?): Boolean {
        val lower = url.lowercase()
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return false
        if (".m3u8" in lower || ".mpd" in lower) return false
        if (mimeType != null && (mimeType.contains("mpegurl", true) || mimeType.contains("dash", true))) return false
        return true
    }
}
