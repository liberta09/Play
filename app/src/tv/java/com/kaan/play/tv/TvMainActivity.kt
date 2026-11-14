package com.kaan.play.tv

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kaan.play.PlayApp
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.player.PlaySession
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// AppCompatActivity: Play'in "Dil" seçimi (AppLanguage → AppCompatDelegate.setApplicationLocales) TV ekranlarına da
// uygulansın; API 33 altında seçim AppCompat'in etkin ekranı varken kaydedilir ve açılışta okunur.
class TvMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = PlayLauncher(this, lifecycleScope)
        setContent { TvApp(onPlay = launcher) }
        // Telefondan "TV'de oynat": içerik isteğinin eklentisi bu TV'ninkine çevrilmiş gelir (TvPlayApp.resolveRemotePlay);
        // TV'de eklenti yoksa telefon kaynağı kendisi bulup doğrudan akış gönderir.
        // Sunucu yalnız uygulama ön plandayken açık; oynatıcı açıkken gelen istek yeni oynatıcıyı onun üstünde açar.
        lifecycleScope.launch {
            val container = (application as PlayApp).container
            container.localSync.playRequests.collect { req ->
                if (req.streams.isEmpty()) {
                    Log.i("PlayTv", "telefondan oynatma isteği")
                    launcher.play(req.target)
                } else {
                    Log.i("PlayTv", "telefondan doğrudan akış (${req.streams.size} kaynak)")
                    launcher.playStreams(req.target, req.streams)
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) { adoptProfilelessHistory() }
        // Önceki sürümlerin Wi-Fi eşitlemesinden yanlış eklenti numarasıyla kalmış geçmiş kayıtları (Devam et yanlış eklentiyi
        // açıyordu) eşitleme beklemeden onarılır. Ana sayfa eklentileri yükledikten sonra çalışır ki yeniden yükleme olmasın.
        lifecycleScope.launch(Dispatchers.IO) {
            // Eski (eklenti numaralı) içerik kimlikleri hemen kalıcı kimliğe taşınır: eklenti yüklemez.
            (application as PlayApp).container.localSyncData.migrateContentIds()
            kotlinx.coroutines.delay(60_000)
            val fixed = (application as PlayApp).container.localSyncData.repairHistoryPlugins()
            if (fixed > 0) Log.i("PlayTv", "geçmişte $fixed kaydın eklentisi onarıldı")
        }
    }

    /**
     * TV'de profil seçme ekranı yok: profil oluşmadan izlenenler Play'in profilsiz kapsamına ("legacy-profile") yazılıyordu.
     * Wi-Fi eşitlemesi telefondan profiller getirince etkin profil değişiyor ve bu geçmiş "Devam et"ten kayboluyordu
     * (profilsiz kayıtlar "legacy-profile"da kalıp etkin profil "default" oluyordu). Açılışta etkin profil kesinleştirilir ve
     * profilsiz kayıtlar Play'in profil taşıma yoluyla (ProfileDataMigrator) ona taşınır; taşınacak kayıt yoksa bir şey değişmez.
     */
    private suspend fun adoptProfilelessHistory() = runCatching {
        val container = (application as PlayApp).container
        val active = container.localProfile.ensureId()
        if (active == LEGACY_PROFILE) return@runCatching
        container.localProfile.recordPendingRemaps(mapOf(LEGACY_PROFILE to active))
        container.localSyncData.consolidateLocalDefaultProfiles()
    }.onFailure { Log.w("PlayTv", "profilsiz geçmiş taşınamadı: ${it.javaClass.simpleName}") }

    private companion object {
        const val LEGACY_PROFILE = "legacy-profile"
    }
}

/**
 * Oynatmayı Play'in oynatıcısında başlatır. Kaynak listesi beklenmez: oynatıcı uygun ilk kaynağı kendisi bulur.
 * [startAtMs] verilmezse kayıtlı konumdan devam edilir ("Her zaman kaldığım yerden devam et" kapalıysa önce sorulur).
 */
class PlayLauncher(private val context: Context, private val scope: CoroutineScope) {
    private val container get() = (context.applicationContext as PlayApp).container

    /** [then]: başlatıldıktan sonra (oynatıcıdaki bölüm panelinden açılınca eski oynatıcı kapanır). */
    fun play(target: PlayTarget, startAtMs: Long? = null, then: () -> Unit = {}) {
        scope.launch {
            val saved = if (startAtMs != null) null else container.library.progress(target)
                ?.takeIf { WatchRules.isResumable(it.positionMs, it.durationMs) }?.positionMs
            // "Her zaman kaldığım yerden devam et" kapalıysa (Play ayarı) kayıtlı konum varken başlangıç ekranında sorulur.
            val ask = saved != null && !container.settings.alwaysResume.first()
            val start = startAtMs ?: saved ?: 0L
            // Önce TV'nin hızlı başlangıç ekranı (kaynak yoklama), oradan TV oynatıcısı.
            context.startActivity(
                PlaySession(target, stream = null, startPositionMs = start, autoResumed = start > 0 && !ask, autoSource = true)
                    .toIntent(context).setClass(context, TvPlayStartActivity::class.java)
                    .putExtra(TvPlayStartActivity.EXTRA_ASK_RESUME, ask),
            )
            then()
        }
    }

    /**
     * Telefonun bulduğu doğrudan akışlar (bu TV'de içeriğin eklentisi yok): kaynak araması yapılmadan TV oynatıcısında,
     * sırayla denenir. Eklenti olmadığı için geçmişe yazılmaz ve sonraki bölüme geçilmez.
     */
    fun playStreams(target: PlayTarget, streams: List<com.kaan.play.data.stream.PlayableStream>) {
        val session = PlaySession(target, stream = streams.first(), startPositionMs = 0L, fallbacks = streams.drop(1), noHistory = true)
        context.startActivity(session.toIntent(context).setClass(context, TvPlayerActivity::class.java))
    }

    /**
     * Canlı TV kanalı (Play'deki PlayRoot.playLiveChannel ile aynı hedef): TMDB içeriği değildir, eklentinin kanal kimliğiyle
     * canlı akış aranır; ilerleme/geçmiş yazılmaz (Play addonType tv/channel'ı kaydetmez).
     */
    fun playLive(item: CatalogItem, then: () -> Unit = {}) {
        val target = PlayTarget(
            request = StreamRequest(
                tmdbId = 0, imdbId = null, type = MediaType.Tv,
                pluginId = item.providerId,
                addonType = item.addonType?.takeIf { it == "tv" || it == "channel" } ?: "tv",
                addonContentId = item.rawUrl ?: item.id,
                dataUrl = item.rawUrl?.takeIf { item.providerId.startsWith("cs:") },
                title = item.title,
            ),
            title = item.title, posterPath = null,
        )
        play(target, startAtMs = 0L, then = then)
    }

    /**
     * "İzlemeye devam et" kartı (Play'deki PlayRoot.continueWatching'in eklenti yolu): yarım kalan bölüm kaldığı yerden,
     * biten bölümün sıradakini baştan oynatır. Sıradaki bulunamazsa [openDetail] ile detay açılır.
     */
    fun resume(item: ContinueItem, openDetail: (DetailRef) -> Unit) {
        val e = item.entry
        if (e.pluginId == null && e.tmdbId > 0) return resumeTmdb(item)
        val pluginId = e.pluginId ?: return
        val contentUrl = e.contentUrl ?: return
        scope.launch {
            when (item) {
                is ContinueItem.Resume -> {
                    val dataUrl = e.dataUrl ?: return@launch openDetail(e.toRef())
                    play(e.toTarget(dataUrl, e.season.takeIf { e.type == MediaType.Tv }, e.episode.takeIf { e.type == MediaType.Tv }), e.positionMs)
                }
                is ContinueItem.Next -> {
                    val next = runCatching { container.csNextEpisodes.next(pluginId, contentUrl, e.dataUrl, e.season, e.episode) }.getOrNull()
                        ?: return@launch openDetail(e.toRef())
                    play(e.toTarget(next.dataUrl, next.season, next.episode), 0L)
                }
            }
        }
    }

    /** Kütüphane > Geçmiş satırı (Play'deki playHistory): yarım kaldıysa kayıtlı konumdan, izlendiyse baştan. */
    fun playHistory(e: WatchHistoryEntity, openDetail: (DetailRef) -> Unit) {
        val start = if (WatchRules.isResumable(e.positionMs, e.durationMs)) e.positionMs else 0L
        resume(ContinueItem.Resume(e.copy(positionMs = start)), openDetail)
    }

    /** Kaydın detay sayfası. */
    fun openDetail(e: WatchHistoryEntity, onOpen: (DetailRef) -> Unit) = onOpen(e.toRef())

    /** TMDB film/dizi kaydı (Play'deki playEntry'nin TMDB yolu): IMDb kimliği detaydan alınır, yayını eklentiler bulur. */
    private fun resumeTmdb(item: ContinueItem) {
        val e = item.entry
        val (season, episode, start) = when (item) {
            is ContinueItem.Resume -> Triple(e.season.takeIf { e.type == MediaType.Tv }, e.episode.takeIf { e.type == MediaType.Tv }, e.positionMs)
            is ContinueItem.Next -> Triple(item.season, item.episode, 0L)
        }
        scope.launch { playTmdb(e, season, episode, start) }
    }

    private suspend fun playTmdb(e: WatchHistoryEntity, season: Int?, episode: Int?, start: Long) {
        val imdb = runCatching { container.tmdb.detail(e.type, e.tmdbId).imdbId }.getOrNull()
        play(
            PlayTarget(StreamRequest(tmdbId = e.tmdbId, imdbId = imdb, type = e.type, season = season, episode = episode, title = e.title), e.title, e.posterPath),
            start,
        )
    }

    private fun WatchHistoryEntity.toTarget(dataUrl: String, season: Int?, episode: Int?) = PlayTarget(
        request = StreamRequest(
            tmdbId = tmdbId, imdbId = null, type = type, season = season, episode = episode,
            pluginId = pluginId, dataUrl = dataUrl, contentUrl = contentUrl,
        ),
        title = title, posterPath = posterPath,
    )
}

/** Devam et kaydının detay sayfası: eklentide eklenti + içerik adresi, TMDB'de tür + kimlik. */
internal fun WatchHistoryEntity.toRef() =
    if (pluginId == null && tmdbId > 0) DetailRef(TMDB_PROVIDER, "${type.tmdbPath}:$tmdbId", true, title, TmdbImages.url(posterPath, TmdbImages.Size.Poster))
    else DetailRef(pluginId.orEmpty(), contentUrl.orEmpty(), false, title, posterPath)

/** TV'de açılabilen kayıt: CloudStream eklentisinden (içerik adresi bilinen) ya da TMDB film/dizisi. */
internal fun ContinueItem.openableOnTv() = entry.openableOnTv()

internal fun WatchHistoryEntity.openableOnTv() =
    (pluginId?.startsWith("cs:") == true && contentUrl != null) || (pluginId == null && tmdbId > 0)
