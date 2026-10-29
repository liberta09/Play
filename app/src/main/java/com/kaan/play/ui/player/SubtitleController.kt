package com.kaan.play.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleDiag
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.TrackLoader
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleOffset
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTimeline
import com.kaan.play.data.subtitle.SubtitleTrack
import com.kaan.play.data.subtitle.translate.ModelPrep
import com.kaan.play.data.subtitle.translate.SubtitleTranslation
import com.kaan.play.data.subtitle.translate.TranslatedSubtitleStore
import com.kaan.play.data.subtitle.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Altyazı katmanının oynatıcıdan ihtiyaç duyduğu şeyler ([PlaybackController] uygular; testlerde sahtesi kullanılır). */
interface SubtitleHost {
    fun positionMs(): Long

    fun embeddedSubtitles(): List<EmbeddedSubtitle>

    fun selectEmbeddedSubtitle(index: Int)

    fun disableEmbeddedSubtitles()
}

/** Altyazı yükleme durumu (yalnızca oynatıcıda küçük bir bildirim olarak görünür). */
enum class SubtitleStatus { Idle, Loading, Failed }

/** Otomatik çeviri durumu (oynatıcıda küçük bir gösterge olarak görünür; [Failed] kısa süre sonra kalkar). */
sealed interface TranslationState {
    data object Idle : TranslationState

    /** Dil modeli ilk kullanımda iniyor (bir kez). */
    data object DownloadingModel : TranslationState

    data class Translating(val percent: Int) : TranslationState

    /** [needsWifi]: model "yalnızca Wi-Fi" ayarı yüzünden indirilemedi. İngilizce altyazı olduğu gibi kalır. */
    data class Failed(val needsWifi: Boolean) : TranslationState
}

/**
 * Oynatıcının altyazı katmanı. Altyazı kaynakları tek listede toplanır: videonun içindekiler, akışın kendi altyazıları ve
 * altyazı addon'ları ([SubtitleProvider]); aynı adres tek kez görünür. Seçim:
 *
 * - Varsayılan: bu içerik için hatırlanan dil, sonra uygulama dili, sonra İngilizce ([SubtitleSelection]). Kullanıcı
 *   kendisi seçene kadar, daha iyi bir dil geç gelirse (addon geç yanıt verirse) seçim ona yükselir, asla geri düşmez.
 *   Kullanıcının seçimi (kapatmak dahil) o film/dizi için hatırlanır ve dizide sonraki bölümlerde uygulanır.
 * - Videonun içindeki parça Media3 ile çizilir. Harici altyazı [SubtitleLoader] ile indirilir (kodlama UTF-8'e çevrilir),
 *   ayrıştırılır ve satırlar burada, oynatma konumuna göre seçilip `SubtitleView`'a verilir. Böylece senkron kaydırması
 *   (±0,1 / ±0,5 sn) oynatmayı hiç kesmeden, anında uygulanır; kaydırma (içerik + altyazı) başına kalıcıdır.
 *
 * Her şey ana iş parçacığında çalışır ([scope] = etkinliğin yaşam döngüsü); ağ/disk işi yükleyicide arka planda yapılır.
 */
class SubtitleController(
    private val scope: CoroutineScope,
    private val playback: SubtitleHost,
    private val request: StreamRequest,
    private val loader: TrackLoader,
    private val memory: SubtitleMemory,
    private val provider: SubtitleProvider,
    /** Tercih edilen altyazı dili, iki harfli (Ayarlar > Oynatıcı; varsayılan `tr`). */
    private val preferredLanguage: suspend () -> String,
    /** Etkinlik kapansa da tamamlanması gereken yazmalar (kalıcı hafıza) için uygulama kapsamı. */
    private val persist: CoroutineScope,
    /** Türkçe yoksa İngilizceyi cihazda Türkçeye çeviren katman; null: çeviri yok (indirilmiş kopya, testler). */
    private val translation: TranslationEngine? = null,
    private val translationStore: TranslatedSubtitleStore? = null,
    /** Önbellek dosyalarının okunup yazıldığı dispatcher (testlerde sanal zamanlı olanı verilir). */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val computation: CoroutineDispatcher = io,
) {

    /** Menüde gösterilen tüm seçenekler (aynı adresler ayıklanmış). */
    var items by mutableStateOf<List<SubtitleItem>>(emptyList())
        private set

    /** Açık olan altyazı; null: kapalı. */
    var selected by mutableStateOf<SubtitleItem?>(null)
        private set

    var status by mutableStateOf(SubtitleStatus.Idle)
        private set

    /** Açık harici altyazının senkron kaydırması (ms); + geç, − erken. */
    var offsetMs by mutableLongStateOf(0L)
        private set

    /** Altyazı addon'larından cevap bekleniyor mu? */
    var searching by mutableStateOf(true)
        private set

    /** Menü sırası için dil tercihi: tercih edilen dil, sonra İngilizce (otomatik seçimden bağımsız, hatırlanan dil girmez). */
    var menuLanguages by mutableStateOf<List<String>>(emptyList())
        private set

    /** Otomatik çeviri durumu. */
    var translationState by mutableStateOf<TranslationState>(TranslationState.Idle)
        private set

    /** Cevap vermeyen altyazı addon'ları. */
    var failedSources by mutableStateOf<List<StreamSource>>(emptyList())
        private set

    private var embedded: List<SubtitleItem.Embedded> = emptyList()
    private var streamTracks: List<SubtitleTrack> = emptyList()
    private var addonTracks: List<SubtitleTrack> = emptyList()
    private var localTracks: List<SubtitleTrack> = emptyList()
    private var translatedTracks: List<SubtitleTrack> = emptyList()
    private var translateStarted = false
    private var translateJob: Job? = null
    private val badUrls = HashSet<String>()

    private var preferred: List<String> = emptyList()
    private var ready = false
    private var userChose = false
    private var autoRank: Int? = null

    /** Altyazıyı bu içerik için kullanıcı daha önce bir dille seçti mi (kayıtlı dil; "kapalı" değil)? Seçtiyse dublajlı kaynakta da uygulanır. */
    private var rememberedExplicit = false

    /** Dublajlı kaynakta (Türkçe ses) otomatik altyazı seçilmez; kullanıcının elle seçimine dokunulmaz. */
    private var autoSuppressed = false

    private var searchJob: Job? = null

    /** Kullanıcı bu oturumda altyazıyı elle seçti/kapattı mı? (Otomatik seçim ve kaynak değişimi bunu bozmaz.) */
    val userSelected: Boolean get() = userChose

    private var timeline: SubtitleTimeline? = null
    private var shown: List<SubtitleCue> = emptyList()
    private var sink: ((List<SubtitleCue>) -> Unit)? = null
    private var loadJob: Job? = null
    private var saveOffsetJob: Job? = null

    /** Altyazı katmanını çizen görünüm (oynatıcı ekranı bağlar). */
    fun attach(cueSink: ((List<SubtitleCue>) -> Unit)?) {
        sink = cueSink
        shown = emptyList()
        cueSink?.invoke(emptyList())
        refresh()
    }

    /** Hatırlanan dili okur, altyazı addon'larını sorar, satır zamanlayıcısını başlatır. Bir kez çağrılır. */
    fun start() {
        scope.launch {
            val remembered = memory.language(request)
            rememberedExplicit = remembered != null && remembered != SubtitleSelection.REMEMBERED_OFF
            val language = preferredLanguage()
            menuLanguages = listOf(language, "en").distinct()
            preferred = SubtitleSelection.preferredLanguages(remembered, language)
            ready = true
            autoSelect()
            maybeTranslate()
        }
        collectProvider()
        scope.launch {
            while (isActive) {
                refresh()
                delay(TICK_MS)
            }
        }
    }

    private fun collectProvider() {
        searchJob?.cancel()
        searching = true
        searchJob = scope.launch {
            provider.subtitles(request)
                .onCompletion {
                    searching = false
                    maybeTranslate()
                }
                .collect { result ->
                    when (result) {
                        is SubtitleResult.Found -> {
                            val reqEpisode = request.episode
                            val reqSeason = request.season
                            val updatedTracks = if (reqSeason != null || reqEpisode != null) {
                                result.tracks.map {
                                    if (it.season == null && it.episode == null) it.copy(season = reqSeason, episode = reqEpisode) else it
                                }
                            } else {
                                result.tracks
                            }
                            addonTracks = addonTracks + updatedTracks
                            rebuild()
                        }
                        is SubtitleResult.Failed -> failedSources = failedSources + result.source
                    }
                }
        }
    }

    /**
     * "İnternetten yükle": sağlayıcılara yeniden sorar (önceki arama bittiyse; sonuçlar film bazında önbellekte olduğundan ağa
     * yalnızca hata/boş sonuçta gidilir). Zaten arıyorsa bir şey yapmaz; sonuçlar [items]'a (tür [SubtitleKind.Online]) girer.
     */
    fun searchOnline() {
        if (searching) return
        addonTracks = emptyList()
        failedSources = emptyList()
        rebuild()
        collectProvider()
    }

    /** Çevrimiçi aramadan gelen parçalar (SubDL önce, sonra OpenSubtitles). */
    fun onlineItems(): List<SubtitleItem.External> =
        items.filterIsInstance<SubtitleItem.External>().filter { it.track.kind == SubtitleKind.Online }

    /**
     * "İlk kullanılabiliri yükle": [language] için çevrimiçi sonuçları sırayla indirip ayrıştırarak denemeyi; ilk çalışanı döner
     * (indirilmiş olduğundan sonradan seçmek ağa gitmez). Hiçbiri çalışmazsa null.
     */
    suspend fun firstUsableOnline(language: String): SubtitleItem.External? = withContext(io) {
        for (item in onlineItems().filter { it.language == language }) {
            if (item.track.url in badUrls) continue
            if (loader.load(item.track) is SubtitleLoad.Loaded) return@withContext item
            badUrls += item.track.url
        }
        return@withContext null
    }

    /**
     * Dublajlı kaynakta (Türkçe ses var) otomatik altyazı seçilmez; açık olan otomatik seçim kapatılır. Kullanıcının elle seçtiğine
     * ve daha önce bu içerik için seçtiği dile dokunulmaz.
     */
    fun suppressAuto() {
        autoSuppressed = true
        if (!rememberedExplicit && !userChose && selected != null) apply(null)
    }

    /** Altyazılı kaynağa dönülünce otomatik seçim yeniden açılır. */
    fun resumeAuto() {
        if (!autoSuppressed) return
        autoSuppressed = false
        autoSelect()
    }

    /** Akış (yeniden) yüklendi: akışın kendi altyazıları listeye girer. */
    fun setStream(stream: PlayableStream) {
        val reqEpisode = request.episode
        val reqSeason = request.season
        streamTracks = stream.subtitles.map {
            SubtitleTrack(it.url, SubtitleLanguages.normalize(it.language), stream.source.addonName, headers = it.headers, season = reqSeason, episode = reqEpisode)
        }
        rebuild()
    }

    /** Videonun içindeki parçalar değişti (Media3 parça listesi güncellendi). */
    fun onTracksChanged() {
        val next = playback.embeddedSubtitles().map {
            SubtitleItem.Embedded(it.index, SubtitleLanguages.normalize(it.language), it.label?.takeIf(String::isNotBlank))
        }
        if (next == embedded) return
        embedded = next
        rebuild()
    }

    /**
     * Kullanıcı bir seçenek seçti ya da altyazıyı kapattı ([item] null). Dil hatırlanır (dizide sonraki bölümlerde uygulanır) ve
     * otomatik seçim bir daha karışmaz. İndirilen altyazının dili, altyazı gerçekten yüklenince hatırlanır: yüklenemeyen bir seçim
     * dil tercihi sayılmaz.
     */
    fun choose(item: SubtitleItem?) {
        userChose = true
        val alreadyLoaded = item != null && item == selected && status == SubtitleStatus.Idle && (item is SubtitleItem.Embedded || timeline != null)
        if (!alreadyLoaded) apply(item)
        when (item) {
            null -> remember(SubtitleSelection.REMEMBERED_OFF)
            is SubtitleItem.Embedded -> remember(item.language)
            is SubtitleItem.External -> if (alreadyLoaded) remember(item.language)
        }
    }

    /**
     * Kullanıcı dosyadan altyazı seçti ([uri]: `content://`, [name]: görünen dosya adı). Listeye eklenir ve hemen açılır;
     * dil dosya adından (`film.tr.srt`) tahmin edilir, bulunamazsa "Diğer" grubunda durur.
     */
    fun addLocal(uri: String, name: String) {
        val track = SubtitleTrack(uri, languageFromFileName(name), name, SubtitleKind.Local)
        if (localTracks.none { it.url == uri }) localTracks = localTracks + track
        rebuild()
        choose(SubtitleItem.External(track))
    }

    private fun remember(language: String?) {
        persist.launch { memory.rememberLanguage(request, language) }
    }

    /** Elle senkron: [deltaMs] kadar kaydırır (sınırlı), anında uygular, kalıcı saklar. */
    fun adjustOffset(deltaMs: Long) = setOffset(SubtitleOffset.step(offsetMs, deltaMs))

    fun resetOffset() = setOffset(0L)

    /** Etkinlik durunca bekleyen kaydırma yazmasını hemen tamamlar. */
    fun flush() {
        val job = saveOffsetJob ?: return
        saveOffsetJob = null
        job.cancel()
        saveOffset()
    }

    fun clearStatus() {
        if (status == SubtitleStatus.Failed) status = SubtitleStatus.Idle
    }

    // ---- iç işler ----

    private fun rebuild() {
        // Sıra: videonun içindekiler, akışın, kullanıcının dosyası, addon'lar, en sonda çevrimiçi arama (aynı dilde öndeki seçilir).
        val (online, fromAddons) = addonTracks.partition { it.kind == SubtitleKind.Online }
        // Çevrimiçi sonuçlarda sağlayıcı sırası sabit: önce SubDL, sonra OpenSubtitles (cevap geliş sırası değil).
        val onlineOrdered = online.sortedBy { onlineRank(it.origin) }
        val all = embedded + (streamTracks + localTracks + fromAddons + onlineOrdered + translatedTracks).map(SubtitleItem::External)
        items = SubtitleSelection.dedupeAndDisambiguate(all)
        // Açık videonun-içi parça artık yoksa (kaynak değişti), seçim düşer ve otomatik seçim yeniden değerlendirilir.
        val current = selected
        if (current is SubtitleItem.Embedded && current !in items) {
            selected = null
            userChose = false
            autoRank = null
        }
        // Eski akışın kendi altyazısı (ör. içeriğe uymadığı için elenen kaynağınki) yeni kaynağın videosunda kalmaz: senkron tutmaz.
        if (current is SubtitleItem.External && current.track.kind == SubtitleKind.Source && current !in items && !userChose) {
            apply(null)
            autoRank = null
        }
        autoSelect()
        maybeTranslate()
    }

    // ---- otomatik çeviri ----

    /**
     * Hiçbir kaynakta (videonun içi dahil) Türkçe yoksa ve İngilizce harici altyazı varsa, İngilizceyi arka planda Türkçeye çevirir.
     * Altyazı aramaları bitmeden başlamaz (geç gelen gerçek Türkçe çeviriyi gereksiz kılar); bu sırada İngilizce zaten gösterilir.
     * Gerçek Türkçe gelirse süren çeviri bırakılır. Kullanıcının elle seçimine ve dublajlı kaynaktaki bastırmaya dokunmaz. Oturum
     * başına bir deneme: başarısızsa İngilizce kalır, tekrar tekrar denenmez.
     */
    private fun maybeTranslate() {
        val engine = translation ?: return
        val store = translationStore ?: return
        if (!ready) return
        if (SubtitleTranslation.hasRealTarget(items)) {
            if (translateJob?.isActive == true) {
                translateJob?.cancel()
                translationState = TranslationState.Idle
            }
            return
        }
        if (translateStarted || searching || userChose || (autoSuppressed && !rememberedExplicit)) return
        val sources = SubtitleTranslation.candidates(items.filter { it !is SubtitleItem.External || it.track.url !in badUrls }, preferred)
        if (sources.isEmpty()) return
        translateStarted = true
        translateJob = scope.launch { runTranslation(engine, store, sources) }
    }

    private suspend fun runTranslation(engine: TranslationEngine, store: TranslatedSubtitleStore, sources: List<SubtitleTrack>) {
        try {
            if (!engine.enabled()) return
            val cached = withContext(io) { sources.firstNotNullOfOrNull { store.cached(request, it) } }
            if (cached != null) return addTranslated(cached)
            if (!engine.isModelReady()) {
                translationState = TranslationState.DownloadingModel
                when (engine.prepareModel()) {
                    ModelPrep.Ready -> Unit
                    ModelPrep.NeedsWifi -> return failTranslation(needsWifi = true)
                    ModelPrep.Failed -> return failTranslation(needsWifi = false)
                }
            }
            translationState = TranslationState.Translating(0)
            for (source in sources) {
                val loaded = loader.load(source) as? SubtitleLoad.Loaded ?: continue
                val startedAt = System.currentTimeMillis()
                val cues = engine.translate(loaded.cues) { percent ->
                    // Çeviri iş parçacıklarından gelir; durum yalnızca burada (ana iş parçacığı) yazılır.
                    scope.launch { if (translationState is TranslationState.Translating) translationState = TranslationState.Translating(percent) }
                }
                SubtitleDiag.d("çeviri bitti satır=${cues.size} süre=${System.currentTimeMillis() - startedAt} ms")
                val track = store.trackFor(request, source)
                if (!withContext(io) { store.write(track, cues) }) return failTranslation(needsWifi = false)
                return addTranslated(track)
            }
            translationState = TranslationState.Idle // İngilizce altyazı zaten yüklenemedi; ayrı bir çeviri mesajı gerekmez.
        } catch (e: CancellationException) {
            translationState = TranslationState.Idle
            throw e
        } catch (e: Exception) {
            failTranslation(needsWifi = false)
        }
    }

    private fun addTranslated(track: SubtitleTrack) {
        translationState = TranslationState.Idle
        translatedTracks = translatedTracks + track
        rebuild()
    }

    private fun failTranslation(needsWifi: Boolean) {
        translationState = TranslationState.Failed(needsWifi)
    }

    fun clearTranslationNotice() {
        if (translationState is TranslationState.Failed) translationState = TranslationState.Idle
    }

    private fun autoSelect() {
        if (!ready || userChose || (autoSuppressed && !rememberedExplicit)) return
        val candidates = items.filter { it !is SubtitleItem.External || it.track.url !in badUrls }
        val pick = SubtitleSelection.pickDefault(candidates, preferred) ?: return
        // Sıra: önce dil, sonra (aynı dilde) kaynak türü; çevrimiçide SubDL, OpenSubtitles'tan önce. OpenSubtitles önce cevap verse de
        // sonradan gelen SubDL sonucu (aynı dilde) otomatik seçimi yükseltir.
        val rank = (SubtitleSelection.rank(pick.language, preferred) ?: return) * RANK_STEP + sourceRank(pick)
        val best = autoRank
        if (selected != null && best != null && rank >= best) return
        autoRank = rank
        apply(pick)
    }

    /** Hafızaya yazmadan seçimi uygular. */
    private fun apply(item: SubtitleItem?) {
        loadJob?.cancel()
        timeline = null
        offsetMs = 0L
        selected = item
        status = SubtitleStatus.Idle
        when (item) {
            null -> {
                playback.disableEmbeddedSubtitles()
                refresh()
            }
            is SubtitleItem.Embedded -> {
                playback.selectEmbeddedSubtitle(item.index)
                refresh()
            }
            is SubtitleItem.External -> {
                playback.disableEmbeddedSubtitles()
                status = SubtitleStatus.Loading
                refresh()
                loadJob = scope.launch { loadExternal(item) }
            }
        }
    }

    private suspend fun loadExternal(item: SubtitleItem.External) {
        val savedOffset = withContext(io) { memory.offset(request, item.track.url) }
        when (val result = loader.load(item.track)) {
            is SubtitleLoad.Loaded -> {
                if (selected != item) return
                val newTimeline = withContext(computation) { SubtitleTimeline(result.cues) }
                if (selected != item) return
                timeline = newTimeline
                offsetMs = savedOffset
                status = SubtitleStatus.Idle
                if (userChose) remember(item.language)
                refresh()
            }
            is SubtitleLoad.Failed -> {
                if (selected != item) return
                badUrls += item.track.url
                selected = null
                autoRank = null
                status = SubtitleStatus.Failed
                refresh()
                // Kullanıcı seçmediyse sıradaki aday denenir; seçtiyse hata bildirimi yeter.
                autoSelect()
            }
        }
    }

    private fun setOffset(value: Long) {
        val track = (selected as? SubtitleItem.External)?.track ?: return
        if (timeline == null || value == offsetMs) return
        offsetMs = value
        refresh()
        saveOffsetJob?.cancel()
        saveOffsetJob = persist.launch {
            delay(SAVE_DEBOUNCE_MS)
            memory.saveOffset(request, track.url, value)
        }
    }

    private fun saveOffset() {
        val track = (selected as? SubtitleItem.External)?.track ?: return
        val value = offsetMs
        persist.launch { memory.saveOffset(request, track.url, value) }
    }

    /** Şimdiki konumda görünmesi gereken satırları hesaplar; değiştiyse görünüme verir. */
    private fun refresh() {
        val sink = sink ?: return
        val active = timeline?.activeAt(playback.positionMs(), offsetMs).orEmpty()
        if (active == shown) return
        shown = active
        sink(active)
    }

    companion object {
        private const val TICK_MS = 50L

        private const val RANK_STEP = 10

        /** Aynı dilde kaynak sırası: videonun içindekiler/akış/addon 0, çevrimiçi 1 + sağlayıcı sırası, otomatik çeviri en sonda. */
        private fun sourceRank(item: SubtitleItem): Int = when {
            item !is SubtitleItem.External -> 0
            item.track.kind == SubtitleKind.Online -> 1 + onlineRank(item.track.origin)
            // Gerçek Türkçe (her kaynaktan) her zaman çeviriden önce gelir.
            item.track.kind == SubtitleKind.Translated -> TRANSLATED_RANK
            else -> 0
        }

        private const val TRANSLATED_RANK = 5

        /** Çevrimiçi sağlayıcı önceliği: SubDL, OpenSubtitles, diğerleri. */
        internal fun onlineRank(origin: String): Int = when (origin) {
            "SubDL" -> 0
            "OpenSubtitles" -> 1
            else -> 2
        }

        /** `film.tr.srt` / `Film.Turkish.srt` -> `tr`; uzantıdan önceki parça dil değilse null. */
        fun languageFromFileName(name: String): String? {
            val stem = name.substringBeforeLast('.')
            val last = stem.split('.', '_', '-', ' ').lastOrNull { it.isNotBlank() } ?: return null
            if (last.length < 2 || last.any { !it.isLetter() }) return null
            return SubtitleLanguages.normalize(last)
        }
        private const val SAVE_DEBOUNCE_MS = 300L
    }
}
