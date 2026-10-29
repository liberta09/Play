package com.kaan.play.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.stream.ranked
import com.kaan.play.data.stream.recommended
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.abs

/**
 * Otomatik sonraki bölümün Android'den bağımsız kuralları (birim testli): kart ne zaman çıkar, hangi kaynak önce denenir,
 * kaynaklar nasıl toplanır.
 */
object AutoNextRules {
    /** Bölümün son bu kadarında "Sonraki bölüm" kartı çıkar. */
    const val WINDOW_MS = 20_000L

    /** Kaynaklar, kart çıkmadan bu kadar önce aranmaya başlar (geri sayım bitince hazır olsun). */
    const val PREFETCH_MS = 45_000L

    /** Bundan kısa videolarda kart penceresi yoktur (yalnızca bölüm bitince geçilir). */
    const val MIN_DURATION_MS = 90_000L

    /** Kart açıkken kullanıcı pencerenin bu kadar dışına geri sararsa kart kapanır. */
    const val SEEK_BACK_MARGIN_MS = 2_000L

    /** Tercih edilen eklenti cevap verdikten sonra diğer eklentileri beklemek için tanınan kısa süre. */
    const val GRACE_MS = 1_500L

    /** Yedekler dahil bir sonraki bölüm için denenecek en çok kaynak. */
    const val MAX_CANDIDATES = 5

    private fun remaining(positionMs: Long, durationMs: Long) = durationMs - positionMs

    fun inPrefetchWindow(positionMs: Long, durationMs: Long): Boolean =
        durationMs >= MIN_DURATION_MS && remaining(positionMs, durationMs) in 0..PREFETCH_MS

    fun inCardWindow(positionMs: Long, durationMs: Long): Boolean =
        durationMs >= MIN_DURATION_MS && remaining(positionMs, durationMs) in 0..WINDOW_MS

    fun leftCardWindow(positionMs: Long, durationMs: Long): Boolean =
        remaining(positionMs, durationMs) > WINDOW_MS + SEEK_BACK_MARGIN_MS
    /**
     * Oynatıcı alt çubuğundaki "Sonraki bölüm" düğmesinin görünüp görünmeyeceğini belirler.
     * Yalnızca dizi bölümlerinde (sezon ve bölüm numarası olan, canlı tv/channel olmayan ve
     * süresi bilinen pozitif akışlar) görünür. Canlı yayında ve filmlerde gizlenir.
     */
    fun canShowNextEpisodeButton(
        type: MediaType?,
        season: Int?,
        episode: Int?,
        addonType: String? = null,
        durationMs: Long = 1L,
    ): Boolean {
        if (addonType?.lowercase() in listOf("tv", "channel")) return false
        if (type != MediaType.Tv) return false
        if (season == null || episode == null) return false
        if (durationMs <= 0L) return false
        return true
    }

    /**
     * Sonraki bölüm için deneme sırası. Önce kullanıcının izlediği eklentinin kaynakları (aynı sunucu, aynı tür, en yakın kalite
     * önde), sonra diğerleri (önerilen önde, sonra kaliteye göre). Aynı adres bir kez. En çok [MAX_CANDIDATES].
     */
    private data class DecoratedAutoNextSame(
        val stream: PlayableStream,
        val dubbedDiff: Int,
        val hostDiff: Int,
        val magnetDiff: Int,
        val resGap: Int,
        val rankIdx: Int,
    )

    fun order(streams: List<PlayableStream>, current: PlayableStream): List<PlayableStream> {
        val ranked = streams.distinctBy { it.url }.ranked()
        val index = ranked.withIndex().associate { it.value.url to it.index }
        val currentHost = hostOf(current.url)
        val currentMagnet = TorrentLinks.isMagnet(current.url)

        fun resolutionGap(s: PlayableStream): Int {
            val want = current.resolution ?: return 0
            val have = s.resolution ?: return Int.MAX_VALUE
            return abs(have - want)
        }

        val sameRaw = ranked.filter { it.source.addonId == current.source.addonId }
        val same = if (sameRaw.size <= 1) sameRaw else {
            sameRaw.map { s ->
                DecoratedAutoNextSame(
                    stream = s,
                    dubbedDiff = if (s.dubbed != current.dubbed) 1 else 0,
                    hostDiff = if (currentHost != null && hostOf(s.url) != currentHost) 1 else 0,
                    magnetDiff = if (TorrentLinks.isMagnet(s.url) != currentMagnet) 1 else 0,
                    resGap = resolutionGap(s),
                    rankIdx = index.getValue(s.url),
                )
            }.sortedWith(
                compareBy(
                    { it.dubbedDiff },
                    { it.hostDiff },
                    { it.magnetDiff },
                    { it.resGap },
                    { it.rankIdx },
                )
            ).map { it.stream }
        }
        val sameUrls = same.mapTo(HashSet()) { it.url }
        // Başka eklentilerde de izlenen türe (dublajlı/altyazılı) uyanlar öne alınır (sıralama kendi içinde korunur).
        val others = ranked.filter { it.url !in sameUrls }.sortedBy { it.dubbed != current.dubbed }
        val best = others.filter { it.dubbed == current.dubbed }.recommended() ?: others.recommended()
        val orderedOthers = if (best == null) others else listOf(best) + others.filter { it.url != best.url }
        return (same + orderedOthers).take(MAX_CANDIDATES)
    }

    private fun hostOf(url: String): String? = StreamLog.hostOf(url).takeIf { it != "?" }

    /**
     * Kaynakları toplar. Tercih edilen eklenti ([preferredAddonId]; null: herhangi biri) kaynak verince ya da hepsi bitince döner;
     * tercih edilen cevap verdikten sonra diğerleri için [GRACE_MS] beklenir (yedek listesi dolsun). O ana kadar gelenler döner.
     */
    suspend fun collect(results: Flow<StreamResult>, preferredAddonId: Long?): List<PlayableStream> = coroutineScope {
        val all = ArrayList<PlayableStream>()
        val ready = CompletableDeferred<Unit>()
        val collector = launch {
            try {
                results.collect { result ->
                    if (result is StreamResult.Found) {
                        synchronized(all) { all += result.streams }
                        if (result.streams.isNotEmpty() && (preferredAddonId == null || result.source.addonId == preferredAddonId)) {
                            ready.complete(Unit)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sağlayıcı hataları kaynak başına Failed olarak gelir; beklenmedik bir hata toplananlarla devam edilir.
            } finally {
                ready.complete(Unit)
            }
        }
        ready.await()
        if (collector.isActive) withTimeoutOrNull(GRACE_MS) { collector.join() }
        collector.cancel()
        synchronized(all) { all.toList() }
    }
}

/** Oynatıcının bir sonraki adımda açacağı şey. */
sealed interface NextPlayback {
    val target: PlayTarget

    /** İnternetten: [streams] deneme sırasıdır (ilki esas kaynak, kalanlar yedek). */
    data class Online(override val target: PlayTarget, val streams: List<PlayableStream>) : NextPlayback

    /** İndirilmiş kopyadan (çevrimdışı izleme). */
    data class Offline(override val target: PlayTarget, val download: DownloadEntity) : NextPlayback
}

/** Kartın hangi aşamada olduğu. */
enum class AutoNextPhase {
    /** Kart yok. */
    Hidden,

    /** "Sonraki bölüm: S1 B4 · 10 sn". */
    Countdown,

    /** Süre doldu (ya da "Şimdi oynat"); kaynak hazırlanıyor. */
    Loading,

    /** Hiçbir kaynak bulunamadı: "Başka kaynak seç". */
    NoSource,
}

/** Ayarlardan okunan değerler. */
data class AutoNextConfig(val enabled: Boolean, val seconds: Int)

/**
 * Dizide "sonraki bölüm" kartının durum makinesi. Etkinlik yarım saniyede bir [tick] çağırır; geri sayım yalnızca video
 * oynarken işler. Yalnızca dizilerde çalışır; filmde, ayar kapalıyken, son bölümde ve kullanıcı iptal ettikten sonra hiçbir şey olmaz.
 *
 * Sonraki bölüm ([findNext]) açılışta bir kez bulunur (ağ hatasında kart penceresine girerken bir kez daha denenir); kaynaklar
 * ([prepare]) bölümün bitmesine ~45 sn kala aranmaya başlar. Hazırlanamazsa ([prepare] null) kart "Başka kaynak seç" der.
 * Hiçbir kaynak siyah ekranda bırakılmaz: süre dolana dek mevcut bölüm oynamaya devam eder.
 */
class AutoNextController(
    private val scope: CoroutineScope,
    private val target: PlayTarget,
    private val config: suspend () -> AutoNextConfig,
    /** Sonraki (sezon, bölüm); yoksa null (son bölüm); ağ hatasında fırlatır. */
    private val findNext: suspend () -> Pair<Int, Int>?,
    /** Sonraki bölümü oynatmaya hazırlar (kaynakları bulur); hiçbiri yoksa null. */
    private val prepare: suspend (season: Int, episode: Int) -> NextPlayback?,
    private val onAdvance: (NextPlayback) -> Unit,
    private val onNoNext: (() -> Unit)? = null,
) {
    var phase by mutableStateOf(AutoNextPhase.Hidden)
        private set

    /** Kartta gösterilen sonraki bölüm. */
    var season by mutableIntStateOf(0)
        private set
    var episode by mutableIntStateOf(0)
        private set

    var secondsLeft by mutableIntStateOf(0)
        private set

    /** "Başka kaynak seç" için kaynak listesi açık mı. */
    var picking by mutableStateOf(false)
        private set

    /** Ayar okunduktan sonra, PlayerScreen'in manuel geçiş ikonunu güvenle gösterebilmesi için. */
    var manualNextEnabled by mutableStateOf(false)
        private set

    private var settings = AutoNextConfig(enabled = false, seconds = 10)
    private var started = false
    private var next: Pair<Int, Int>? = null
    private var lookups = 0
    private var lookup: Job? = null
    private var lookupDone = false
    private var cancelled = false
    private var advancing = false
    private var prepared: Deferred<NextPlayback?>? = null
    private var advanceJob: Job? = null
    private var countdownLeftMs = 0L
    private var lastTickMs = -1L

    private val isSeries: Boolean
        get() = AutoNextRules.canShowNextEpisodeButton(target.request.type, target.request.season, target.request.episode, target.request.addonType, 1L)

    /** Ayarı okur ve sonraki bölümü arar. Bir kez çağrılır. */
    fun start() {
        if (started || !isSeries) return
        started = true
        scope.launch {
            settings = config()
            manualNextEnabled = settings.enabled
            if (settings.enabled) lookUp()
        }
    }

    private fun lookUp() {
        if (lookupDone || lookup?.isActive == true || lookups >= MAX_LOOKUPS) return
        lookups++
        lookup = scope.launch {
            try {
                next = findNext()
                lookupDone = true // bulundu ya da (null) son bölüm: bir daha sorulmaz
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ağ hatası: kart penceresine girerken bir kez daha denenir
            }
        }
    }

    /** Kart gösterilebilir ve geçiş yapılabilir durumda mı. */
    private fun armed(): Boolean = isSeries && settings.enabled && !cancelled && next != null

    /** Zaman ilerledi: pencereye giriş/çıkış, geri sayım, kaynak ön hazırlığı. [nowMs]: tekdüze saat. */
    fun tick(nowMs: Long, positionMs: Long, durationMs: Long, playing: Boolean, ended: Boolean) {
        val dt = if (lastTickMs < 0) 0L else (nowMs - lastTickMs).coerceIn(0L, MAX_TICK_MS)
        lastTickMs = nowMs
        if (!isSeries || !settings.enabled || cancelled) return
        if (next == null) {
            if (AutoNextRules.inPrefetchWindow(positionMs, durationMs)) lookUp()
            return
        }
        when (phase) {
            AutoNextPhase.Hidden -> {
                if (AutoNextRules.inPrefetchWindow(positionMs, durationMs)) prefetch()
                if (ended) {
                    advance()
                } else if (AutoNextRules.inCardWindow(positionMs, durationMs)) {
                    show()
                }
            }
            AutoNextPhase.Countdown -> when {
                ended -> advance()
                AutoNextRules.leftCardWindow(positionMs, durationMs) -> phase = AutoNextPhase.Hidden // geri sarıldı
                playing -> {
                    countdownLeftMs -= dt
                    secondsLeft = secondsOf(countdownLeftMs)
                    if (countdownLeftMs <= 0) advance()
                }
            }
            AutoNextPhase.Loading, AutoNextPhase.NoSource -> Unit
        }
    }

    private fun show() {
        val (s, e) = next ?: return
        season = s
        episode = e
        countdownLeftMs = settings.seconds * 1000L
        secondsLeft = settings.seconds
        phase = AutoNextPhase.Countdown
    }

    /** Video bitti. @return true: geçişi biz üstlendik (etkinlik kapanmasın); false: eskisi gibi kapan. */
    fun onEnded(): Boolean {
        if (phase == AutoNextPhase.Loading || phase == AutoNextPhase.NoSource) return true
        if (!armed()) return false
        advance()
        return true
    }

    /** "Şimdi oynat" (geri sayımı beklemeden). */
    fun playNow() {
        if (phase == AutoNextPhase.Countdown) advance()
    }

    /**
     * Kullanıcı oynatıcıdaki "Sonraki bölüm" düğmesine bastığında doğrudan sonraki bölüme geçer.
     * Kart penceresini veya geri sayımı beklemez.
     */
    fun skipToNext() {
        if (advancing || !isSeries) return
        cancelled = false
        scope.launch {
            if (next == null && !lookupDone) {
                lookup?.join()
            }
            if (next == null && !lookupDone) {
                try {
                    next = findNext()
                    lookupDone = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    phase = AutoNextPhase.NoSource
                    return@launch
                }
            }
            val (s, e) = next ?: run {
                onNoNext?.invoke()
                return@launch
            }
            season = s
            episode = e
            phase = AutoNextPhase.Loading
            advancing = true
            val pending = prefetch()
            advanceJob = scope.launch {
                val result = pending.await()
                if (cancelled) return@launch
                if (result == null) {
                    phase = AutoNextPhase.NoSource
                    advancing = false
                    prepared = null
                } else {
                    onAdvance(result)
                }
            }
        }
    }

    /**
     * Jenerik yazıları akmaya başladığında manuel geçiş düğmesi için sonraki bölümü bulur ve
     * kaynak aramasını başlatır. Otomatik geri sayım ayarından bağımsızdır.
     */
    suspend fun prepareManualNext(): Boolean {
        if (!isSeries) return false
        if (next == null && !lookupDone) {
            if (lookup?.isActive != true) lookUp()
            lookup?.join()
        }
        // İlk arama ağ hatasıyla bittiyse, ikinci ve son deneme hakkını burada kullan.
        if (next == null && !lookupDone && lookups < MAX_LOOKUPS) {
            lookUp()
            lookup?.join()
        }
        if (next == null) return false
        prefetch()
        return true
    }

    /** "İptal": bu bölüm için kart bir daha çıkmaz, geçiş yapılmaz. */
    fun cancel() {
        cancelled = true
        phase = AutoNextPhase.Hidden
        picking = false
        advancing = false
        advanceJob?.cancel()
        prepared?.cancel()
        prepared = null
    }

    /** "Başka kaynak seç": kaynak listesi açılır. */
    fun startPicking() {
        if (phase == AutoNextPhase.NoSource) picking = true
    }

    fun dismissPicking() {
        picking = false
    }

    /** Kaynak listesinden seçilen akışla sonraki bölüm açılır. */
    fun picked(stream: PlayableStream) {
        picking = false
        val request = target.request.copy(season = season, episode = episode, refresh = false)
        onAdvance(NextPlayback.Online(target.copy(request = request), listOf(stream)))
    }

    /** Sonraki bölümün hedefi (kaynak listesi için). */
    fun nextTarget(): PlayTarget =
        target.copy(request = target.request.copy(season = season, episode = episode, refresh = false))

    private fun prefetch(): Deferred<NextPlayback?> {
        prepared?.let { return it }
        val (s, e) = next ?: return CompletableDeferred(null)
        return scope.async {
            try {
                prepare(s, e)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                null
            }
        }.also { prepared = it }
    }

    private fun advance() {
        if (advancing || !armed()) return
        advancing = true
        if (phase != AutoNextPhase.Loading) {
            val (s, e) = next ?: return
            season = s
            episode = e
            phase = AutoNextPhase.Loading
        }
        val pending = prefetch()
        advanceJob = scope.launch {
            val result = pending.await()
            if (cancelled) return@launch
            if (result == null) {
                phase = AutoNextPhase.NoSource
                advancing = false
                prepared = null // "Başka kaynak seç" sonrası yeniden denenebilsin
            } else {
                onAdvance(result)
            }
        }
    }

    private fun secondsOf(ms: Long): Int = ((ms + 999) / 1000).toInt().coerceAtLeast(0)

    private companion object {
        const val MAX_LOOKUPS = 2
        const val MAX_TICK_MS = 1_000L
    }
}
