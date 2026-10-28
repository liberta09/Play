package com.kaan.play.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.kaan.play.data.settings.SkipPlaybackSettings
import com.kaan.play.data.skip.SkipSegmentProvider
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * İntro/özet/jenerik/önizleme atlama (Kullanıcı kararı 2026-10-28; ortak geliştiricinin 1.1.47 sürümündeki TheIntroDB v4
 * özelliğinden uyarlandı). Kurallar CLAUDE.md "İntro/özet/jenerik atlama".
 *
 * - Süre öğrenilince (canlı yayında değil) TheIntroDB asenkron sorulur; oynatma **asla beklemez**. Kaynak değişirse
 *   ([onSourceChanged]) eski sorgunun sonucu atılır.
 * - Kaynağın chapter işaretleri aynı türde geçerliyse önceliklidir; dış zamanlar yalnızca eksik türleri tamamlar.
 * - Otomatik atlama (varsayılan kapalı) bir segmenti oynatıcı oturumu başına en çok bir kez geçer; atlanan segment o kaynak
 *   için kapanır, geri sarınca döngü olmaz. Devam konumundan başlarken daha önce bitmiş intro/özet geçici olarak gösterilmez.
 */
class SkipController(
    private val scope: CoroutineScope,
    private val request: () -> StreamRequest,
    private val provider: SkipSegmentProvider,
    settings: Flow<SkipPlaybackSettings>,
    private val seekTo: (Long) -> Unit,
    startPositionMs: Long = 0L,
) {
    /** Ayarlar > Oynatıcı'daki atlama ayarları (düğmeler, otomatik atlama, türler). */
    var settings by mutableStateOf(SkipPlaybackSettings.Default)
        private set

    /** Şu an içinde bulunulan, düğmesi gösterilebilecek segmentler (otomatik atlanan hariç). */
    var active by mutableStateOf<List<SkipSegment>>(emptyList())
        private set

    /** Son [tick] konumu; düğmenin ilk 20 sn kontroller gizliyken de görünmesi için. */
    var positionMs by mutableLongStateOf(0L)
        private set

    /** Süre ve işaretlerden doğrulanmış tüm segmentler. */
    var segments: List<SkipSegment> = emptyList()
        private set

    private val markers = mutableListOf<SkipMarker>()
    private val externalMarkers = mutableListOf<SkipMarker>()
    private val dismissed = mutableSetOf<String>()
    private val autoSkipped = mutableSetOf<String>()
    private var knownDurationMs = 0L
    private var generation = 0
    private var resumePositionMs = startPositionMs

    init {
        scope.launch { settings.collect { this@SkipController.settings = it } }
    }

    /** Oynatıcıdan süre (hazır olunca; canlı yayında değil) ve bölüm işaretleri (zamanlı ve kapsayıcıdaki ID3 chapter) alınır. */
    fun attach(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_READY || player.isCurrentMediaItemLive) return
                val duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0) onDurationKnown(duration)
            }

            override fun onMetadata(metadata: Metadata) = onMetadataMarkers(metadata)

            override fun onTracksChanged(tracks: Tracks) {
                for (group in tracks.groups) {
                    for (index in 0 until group.length) group.getTrackFormat(index).metadata?.let(::onMetadataMarkers)
                }
            }
        })
    }

    /** Yeni kaynak yüklendi: işaretler, segmentler ve kapatılanlar sıfırlanır; süren TheIntroDB sorgusunun sonucu atılır. */
    fun onSourceChanged(startMs: Long) {
        generation++
        if (resumePositionMs > 0L) resumePositionMs = startMs
        knownDurationMs = 0L
        markers.clear()
        externalMarkers.clear()
        segments = emptyList()
        active = emptyList()
        dismissed.clear()
    }

    fun onDurationKnown(durationMs: Long) {
        knownDurationMs = durationMs
        rebuild()
        val current = request()
        val expected = generation
        scope.launch {
            val incoming = provider.timestamps(current, durationMs)
                .map { SkipMarker(it.type.markerTitle, it.startMs, it.endMs, it.openEndedEnd) }
            if (expected != generation || knownDurationMs != durationMs || incoming.isEmpty()) return@launch
            externalMarkers.clear()
            externalMarkers += incoming
            rebuild()
        }
    }

    fun onMetadataMarkers(metadata: Metadata) {
        val incoming = SkipSegmentDetector.markersFromMetadata(metadata)
        if (incoming.isEmpty()) return
        markers.removeAll { old -> incoming.any { it.title == old.title && it.startMs == old.startMs && it.endMs == old.endMs } }
        markers += incoming
        rebuild()
    }

    /** Yarım saniyede bir (oynatıcı görünürken): etkin segmentler ve isteğe bağlı otomatik atlama. */
    fun tick(position: Long, playing: Boolean) {
        if (resumePositionMs > 0L && position >= (resumePositionMs - RESUME_SEEK_TOLERANCE_MS).coerceAtLeast(0L)) {
            resumePositionMs = 0L
        }
        val current = SkipSegmentDetector.activeSegments(
            segments = segments.filter { settings.isEnabled(it.category) },
            positionMs = position,
            dismissed = dismissed,
            resumePositionMs = resumePositionMs,
        )
        val autoSkip = SkipSegmentDetector.autoSkipCandidate(
            active = current,
            enabled = settings.autoSkip,
            playing = playing,
            attemptedThisSession = autoSkipped,
        )
        if (autoSkip != null) {
            autoSkipped += autoSkip.key
            skip(autoSkip)
        }
        val display = if (autoSkip == null) current else current.filterNot { it.key == autoSkip.key }
        if (active.map { it.key } != display.map { it.key }) active = display
        // Segment yokken konum yazılmaz (0'da kalır): ekran her yarım saniyede bir yeniden çizilmesin.
        positionMs = if (display.isEmpty()) 0L else position
    }

    /**
     * Düğme (ya da otomatik atlama): segmentin sonuna sarar. Açık uçlu jenerik (`end_ms` yok) videonun sonudur; bölüm bitince
     * otomatik sonraki bölüm akışı her zamanki gibi devralır.
     */
    fun skip(segment: SkipSegment) {
        dismissed += segment.key
        active = active.filterNot { it.key == segment.key }
        seekTo(segment.endMs)
    }

    /** Kumandada Geri: düğme(ler) bu segment için kapanır, oynatıcı kapanmaz. */
    fun dismiss(segments: List<SkipSegment>) {
        dismissed += segments.map { it.key }
        active = active.filterNot { it.key in dismissed }
    }

    /** "Baştan başla": devam konumu koruması kalkar, intro yeniden önerilebilir. */
    fun restartFromBeginning() {
        resumePositionMs = 0L
    }

    private fun rebuild() {
        val r = request()
        val local = SkipSegmentDetector.detect(r.type, knownDurationMs, markers, r.addonType)
        val localKinds = local.mapTo(mutableSetOf()) { it.kind }
        val external = SkipSegmentDetector.detect(r.type, knownDurationMs, externalMarkers, r.addonType)
            .filter { it.kind !in localKinds }
        segments = (local + external)
            .distinctBy { it.key }
            .filter { it.key !in dismissed }
    }

    private companion object {
        const val RESUME_SEEK_TOLERANCE_MS = 10_000L
    }
}
