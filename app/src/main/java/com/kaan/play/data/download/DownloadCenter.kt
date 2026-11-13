package com.kaan.play.data.download

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Requirements
import com.kaan.play.data.db.DownloadDao
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.player.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** [DownloadCenter.start] sonucu. */
sealed interface StartResult {
    data class Started(val id: Long) : StartResult

    /** Bu film/bölüm zaten indirilenlerde (sırada, iniyor, duraklatıldı ya da tamam). */
    data class AlreadyDownloaded(val status: DownloadStatus) : StartResult

    /** Kaynak indirilemiyor (süresi dolmuş, medya değil, korumalı, ulaşılamıyor, desteklenmeyen/canlı biçim). */
    data class NotDownloadable(val reason: Probe.Reason) : StartResult

    /** Yeterli boş alan yok; [estimated] true ise gereken alan kaynak boyut bildirmediği için tahmindir. */
    data class NoSpace(val neededBytes: Long, val freeBytes: Long, val estimated: Boolean) : StartResult
}

/**
 * Çevrimdışı izleme (Faz 4/B): Media3 `DownloadManager` + `SimpleCache` ile indirir, Room'daki `downloads` satırlarıyla eşler.
 *
 * - **Veri:** uygulamaya özel harici alanda ([DownloadFiles]); `SimpleCache` **hiçbir şeyi kendiliğinden silmez**
 *   (`NoOpCacheEvictor`): yalnızca kullanıcı silince gider.
 * - **Başlıklar:** akışın `proxyHeaders`'ı indirme isteğinin `data` alanında JSON olarak taşınır; özel [DownloaderFactory]
 *   her indirme için o başlıklarla bir OkHttp veri kaynağı kurar (liste, parça ve anahtar istekleri dahil).
 * - **Ağ:** "Sadece Wi-Fi'de indir" (varsayılan açık) Media3 `Requirements`'ına çevrilir; koşul yokken indirmeler "sırada" bekler.
 * - **Kalıcılık:** Media3 kendi dizininde durumu tutar; açılışta ([start]) satırlarla uzlaştırılır ve bitmemiş indirmeler için
 *   servis yeniden başlatılır.
 *
 * Media3 nesneleri uygulama (ana) iş parçacığında kullanılır; Room yazmaları tek bir sıraya dizilir (durum geçişleri karışmasın).
 */
@OptIn(UnstableApi::class)
class DownloadCenter(
    context: Context,
    private val dao: DownloadDao,
    val files: DownloadFiles,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val subtitleFetcher: DownloadSubtitleFetcher,
    private val scope: CoroutineScope,
    private val serviceClass: Class<out DownloadService>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probe: SourceProbe = SourceProbe(client),
) {
    private val appContext = context.applicationContext
    private val databaseProvider = StandaloneDatabaseProvider(appContext)

    /** Çevrimdışı oynatmanın da okuduğu tek önbellek (dizin başına tek örnek olmalı). */
    val cache: Cache by lazy { SimpleCache(files.cacheDir, NoOpCacheEvictor(), databaseProvider) }

    private val executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS)

    val manager: DownloadManager = DownloadManager(
        appContext,
        DefaultDownloadIndex(databaseProvider),
        HeaderAwareDownloaderFactory(),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL
        // İlk indirmenin ağ koşulu ayarla uyumlu başlasın (küçük bir DataStore okuması; MainActivity de tema için aynısını yapar).
        val wifiOnly = runBlocking(Dispatchers.IO) { settings.downloadWifiOnly.first() }
        requirements = Requirements(DownloadRules.requirementFlags(wifiOnly))
    }

    /** Arayüzün gözlediği liste (Kütüphane > İndirilenler, kaynak listesi simgeleri). */
    val rows: Flow<List<DownloadEntity>> = dao.observeAll()

    private val _waitingForNetwork = MutableStateFlow(false)

    /** Sıradaki indirmeler ağ koşulunu (ör. Wi-Fi) bekliyor mu? */
    val waitingForNetwork: StateFlow<Boolean> = _waitingForNetwork

    private val initialized = MutableStateFlow(false)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var pollJob: Job? = null

    /** Servisin bildirimi için: şu an inen indirmenin başlığı gibi bilgiler. Bellek içi, ana iş parçacığından okunur. */
    @Volatile
    var latestRows: List<DownloadEntity> = emptyList()
        private set

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            _waitingForNetwork.value = DownloadRules.waitingForNetwork(downloadManager.notMetRequirements)
            reconcile()
            initialized.value = true
            resumeServiceIfNeeded()
        }

        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            sync(download)
            if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) startPolling()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = Unit

        override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) {
            _waitingForNetwork.value = DownloadRules.waitingForNetwork(notMetRequirements)
        }

        override fun onWaitingForRequirementsChanged(downloadManager: DownloadManager, waitingForRequirements: Boolean) {
            _waitingForNetwork.value = waitingForRequirements && DownloadRules.waitingForNetwork(downloadManager.notMetRequirements)
        }
    }

    /** Uygulama açılırken bir kez: dinleyicileri bağlar, ayar değişince ağ koşulunu günceller. */
    fun begin() {
        scope.launch { for (job in writes) runCatching { job() } }
        scope.launch { dao.observeAll().collect { latestRows = it } }
        scope.launch(Dispatchers.Main) {
            settings.downloadWifiOnly.collect { wifiOnly ->
                manager.requirements = Requirements(DownloadRules.requirementFlags(wifiOnly))
            }
        }
        manager.addListener(listener)
        if (manager.isInitialized) listener.onInitialized(manager)
    }

    // ---- indirmeyi başlatma ----

    /**
     * [stream] kaynağını indirmeye başlar. Önce kaynak yoklanır (süresi dolmuş/medya olmayan/korumalı kaynak baştan anlaşılır),
     * sonra boş alana bakılır, HLS/DASH için en uygun kalite seçilir. Altyazılar arka planda ayrıca saklanır.
     */
    suspend fun start(target: PlayTarget, stream: PlayableStream): StartResult =
        // Arayüz (kaynak listesi) kapansa da yarım kalmasın: asıl iş uygulama kapsamında sürer.
        scope.async { doStart(target, stream) }.await()

    private suspend fun doStart(target: PlayTarget, stream: PlayableStream): StartResult {
        initialized.first { it }
        val request = target.request
        val season = request.season ?: 0
        val episode = request.episode ?: 0

        val existing = dao.findContent(request.tmdbId, request.type, season, episode)
        if (existing != null) {
            if (existing.status != DownloadStatus.Failed) return StartResult.AlreadyDownloaded(existing.status)
            // Başarısız eski deneme yenisiyle değişir (Media3 yalnızca ana iş parçacığından kullanılır).
            withContext(Dispatchers.Main) { remove(existing.id) }
        }

        val ok = when (val probed = probe.probe(stream.url, stream.headers)) {
            is Probe.Rejected -> return StartResult.NotDownloadable(probed.reason)
            is Probe.Ok -> probed
        }
        val knownBytes = ok.lengthBytes ?: stream.sizeBytes
        when (val space = DownloadRules.checkSpace(knownBytes, files.freeBytes())) {
            DownloadRules.Space.Enough -> Unit
            is DownloadRules.Space.Short -> return StartResult.NoSpace(space.neededBytes, space.freeBytes, space.estimated)
        }

        val headersJson = DownloadCodec.encodeHeaders(stream.headers)
        val built = try {
            buildRequest(stream, ok.kind, headersJson.encodeToByteArray())
        } catch (_: IOException) {
            return StartResult.NotDownloadable(Probe.Reason.NotMedia)
        }

        val row = DownloadEntity(
            tmdbId = request.tmdbId,
            imdbId = request.imdbId,
            type = request.type,
            season = season,
            episode = episode,
            title = target.title,
            posterPath = target.posterPath,
            sourceName = stream.source.addonName,
            sourceUrl = stream.url,
            headersJson = headersJson,
            status = DownloadStatus.Queued,
            totalBytes = knownBytes ?: 0L,
            createdAt = clock(),
        )
        val id = try {
            dao.insert(row)
        } catch (_: SQLiteConstraintException) {
            return StartResult.AlreadyDownloaded(DownloadStatus.Queued) // aynı anda iki dokunuş
        }

        val withId = built.copyWithId(id.toString())
        try {
            DownloadService.sendAddDownload(appContext, serviceClass, withId, /* foreground = */ false)
        } catch (_: IllegalStateException) {
            // Arka plandayken servis başlatılamazsa yönetici doğrudan eklenir (servis sonradan açılışta devralır).
            withContext(Dispatchers.Main) { manager.addDownload(withId) }
        }

        scope.launch { saveSubtitles(id, request, stream) }
        return StartResult.Started(id)
    }

    /** Düz dosya doğrudan istektir; HLS/DASH için hazırlayıcı en uygun (≤ istenen çözünürlük) kaliteyi seçer. */
    private suspend fun buildRequest(stream: PlayableStream, kind: SourceKind, data: ByteArray): DownloadRequest {
        val uri = android.net.Uri.parse(stream.url)
        return when (kind) {
            SourceKind.Progressive -> DownloadRequest.Builder(PENDING_ID, uri).setData(data).build()
            SourceKind.Hls -> prepared(stream, MimeTypes.APPLICATION_M3U8, data)
            SourceKind.Dash -> prepared(stream, MimeTypes.APPLICATION_MPD, data)
        }
    }

    private suspend fun prepared(stream: PlayableStream, mimeType: String, data: ByteArray): DownloadRequest {
        val item = MediaItem.Builder().setUri(stream.url).setMimeType(mimeType).build()
        val source = OkHttpDataSource.Factory(client).setDefaultRequestProperties(stream.headers)
        val cap = (stream.resolution ?: DEFAULT_MAX_HEIGHT).coerceAtLeast(MIN_MAX_HEIGHT)
        return withContext(Dispatchers.Main) {
            withTimeout(PREPARE_TIMEOUT_MS) {
                suspendCancellableCoroutine<DownloadRequest> { cont ->
                    val helper = DownloadHelper.forMediaItem(appContext, item, DefaultRenderersFactory(appContext), source)
                    cont.invokeOnCancellation { helper.release() }
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                            val params = DownloadHelper.getDefaultTrackSelectorParameters(appContext).buildUpon()
                                .setMaxVideoSize(Int.MAX_VALUE, cap)
                                .setForceHighestSupportedBitrate(true)
                                .build()
                            for (period in 0 until helper.periodCount) {
                                helper.clearTrackSelections(period)
                                helper.addTrackSelection(period, params)
                            }
                            val request = helper.getDownloadRequest(PENDING_ID, data)
                            helper.release()
                            if (cont.isActive) cont.resume(request)
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            helper.release()
                            if (cont.isActive) cont.resumeWith(Result.failure(e))
                        }
                    })
                }
            }
        }
    }

    private suspend fun saveSubtitles(id: Long, request: StreamRequest, stream: PlayableStream) {
        val saved = try {
            subtitleFetcher.fetch(id, request, stream)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        enqueue {
            if (dao.find(id) == null) {
                files.deleteSubtitles(id) // indirme bu arada iptal edildi
            } else {
                dao.setSubtitles(id, DownloadCodec.encodeSubtitles(saved))
            }
        }
    }

    // ---- eylemler ----

    fun pause(id: Long) {
        manager.setStopReason(id.toString(), DownloadRules.STOP_REASON_PAUSED)
    }

    /** Duraklatılmış indirmeyi sürdürür; başarısız olanı yeniden dener (aynı istek yeniden eklenir). */
    fun resume(id: Long) {
        scope.launch {
            val download = withContext(Dispatchers.IO) { manager.downloadIndex.getDownload(id.toString()) }
            when {
                download == null -> Unit
                download.state == Download.STATE_FAILED -> addViaService(download.request)
                else -> withContext(Dispatchers.Main) {
                    try {
                        DownloadService.sendSetStopReason(appContext, serviceClass, id.toString(), Download.STOP_REASON_NONE, false)
                    } catch (_: IllegalStateException) {
                        manager.setStopReason(id.toString(), Download.STOP_REASON_NONE)
                    }
                }
            }
        }
    }

    private suspend fun addViaService(request: DownloadRequest) = withContext(Dispatchers.Main) {
        try {
            DownloadService.sendAddDownload(appContext, serviceClass, request, false)
        } catch (_: IllegalStateException) {
            manager.addDownload(request)
        }
    }

    /** Siler (iptal ve silme aynı işlem): video ve altyazılar gider, satır kalkar. */
    fun remove(id: Long) {
        manager.removeDownload(id.toString())
        enqueue {
            files.deleteSubtitles(id)
            dao.delete(id)
        }
    }

    fun removeAll() {
        manager.removeAllDownloads()
        enqueue {
            files.deleteAllSubtitles()
            dao.clear()
        }
    }

    // ---- çevrimdışı oynatma ----

    /** Bu film/bölümün **tamamlanmış** indirmesi; yoksa null. */
    suspend fun completedFor(request: StreamRequest): DownloadEntity? =
        dao.findContent(request.tmdbId, request.type, request.season ?: 0, request.episode ?: 0)
            ?.takeIf { it.status == DownloadStatus.Completed }

    /** Bu dizi/sezonun tüm tamamlanmış indirmeleri. */
    suspend fun completedForSeason(tmdbId: Int, season: Int): List<DownloadEntity> =
        dao.all().filter { it.tmdbId == tmdbId && it.type == com.kaan.play.data.model.MediaType.Tv && it.season == season && it.status == DownloadStatus.Completed }

    /** Oynatıcının çalacağı öğe: kayıtlı istekten (HLS/DASH'te seçilen kaliteyle birlikte) kurulur. */
    suspend fun mediaItem(id: Long): MediaItem? = withContext(Dispatchers.IO) {
        manager.downloadIndex.getDownload(id.toString())
            ?.takeIf { it.state == Download.STATE_COMPLETED }
            ?.request?.toMediaItem()
    }

    // ---- Room eşzamanı ----

    private fun enqueue(block: suspend () -> Unit) {
        writes.trySend(block)
    }

    private fun sync(download: Download) {
        val id = download.request.id.toLongOrNull() ?: return
        val status = DownloadRules.statusOf(download.state)
        val downloaded = download.bytesDownloaded
        val length = download.contentLength
        val percent = download.percentDownloaded
        enqueue {
            val row = dao.find(id) ?: return@enqueue
            val total = DownloadRules.totalBytes(status, downloaded, length, percent, row.totalBytes)
            if (row.status != status || row.downloadedBytes != downloaded || row.totalBytes != total) {
                dao.updateProgress(id, status, downloaded, total)
            }
        }
    }

    /** İnen indirmelerin ilerlemesi olay olarak gelmez; inen varken saniyede bir okunur, kalmayınca durur. */
    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.Main) {
            while (true) {
                delay(POLL_MS)
                val active = manager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
                if (active.isEmpty() && manager.currentDownloads.none { it.state == Download.STATE_QUEUED }) break
                active.forEach(::sync)
            }
        }
    }

    private fun reconcile() {
        enqueue {
            val managed = withContext(Dispatchers.IO) {
                buildList {
                    manager.downloadIndex.getDownloads().use { cursor ->
                        while (cursor.moveToNext()) {
                            val d = cursor.download
                            val status = DownloadRules.statusOf(d.state)
                            add(
                                DownloadRules.Managed(
                                    d.request.id,
                                    status,
                                    d.bytesDownloaded,
                                    DownloadRules.totalBytes(status, d.bytesDownloaded, d.contentLength, d.percentDownloaded, 0L),
                                ),
                            )
                        }
                    }
                }
            }
            for (fix in DownloadRules.reconcile(dao.all(), managed)) {
                when (fix) {
                    is DownloadRules.Fix.UpdateRow -> dao.updateProgress(fix.rowId, fix.status, fix.downloadedBytes, fix.totalBytes)
                    is DownloadRules.Fix.DeleteRow -> {
                        files.deleteSubtitles(fix.rowId)
                        dao.delete(fix.rowId)
                    }
                    is DownloadRules.Fix.RemoveManaged -> withContext(Dispatchers.Main) { manager.removeDownload(fix.id) }
                }
            }
        }
    }

    /** Uygulama açılınca bitmemiş (duraklatılmamış) indirme varsa servisi (ön plan + bildirim) yeniden başlatır. */
    private fun resumeServiceIfNeeded() {
        val pending = manager.currentDownloads.any {
            it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING
        }
        if (!pending) return
        try {
            DownloadService.start(appContext, serviceClass)
        } catch (_: IllegalStateException) {
            // Uygulama arka planda başladıysa (ör. iş zamanlayıcı) servisi zamanlayıcı kendisi başlatır.
        }
        startPolling()
    }

    /** Her indirme kendi başlıklarıyla: liste, parça ve anahtar istekleri aynı `proxyHeaders`'ı taşır. */
    private inner class HeaderAwareDownloaderFactory : DownloaderFactory {
        override fun createDownloader(request: DownloadRequest): Downloader {
            val headers = DownloadCodec.decodeHeaders(request.data.decodeToString())
            val upstream: DataSource.Factory = OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers)
            val cached = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            return DefaultDownloaderFactory(cached, executor).createDownloader(request)
        }
    }

    companion object {
        private const val PENDING_ID = "pending"
        private const val MAX_PARALLEL = 2
        private const val DOWNLOAD_THREADS = 4
        private const val POLL_MS = 1_000L
        private const val PREPARE_TIMEOUT_MS = 20_000L

        /** Kaynak çözünürlük bildirmezse HLS/DASH'te inilecek en yüksek yükseklik. */
        private const val DEFAULT_MAX_HEIGHT = 1080
        private const val MIN_MAX_HEIGHT = 360
    }
}
