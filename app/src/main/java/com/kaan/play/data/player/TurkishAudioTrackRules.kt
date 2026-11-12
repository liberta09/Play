package com.kaan.play.data.player

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.RankContext
import com.kaan.play.data.stream.SourceRanking
import com.kaan.play.data.stream.TorrentLinks
import java.text.Normalizer
import java.util.Locale

/** Oynatıcıdaki tek bir ses parçasının platformdan bağımsız özeti. */
data class AudioTrackInfo(
    val index: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean = false,
    val isSupported: Boolean = true,
    val mime: String? = null,
    val channelCount: Int = 0,
)

/** Türkçe ses doğrulama taraması sonucunda oynatıcıda gösterilecek kısa bildirim. */
enum class TurkishAudioNotice {
    /** Ses izi dili doğrulanamadı; sıradaki dublajlı alternatif deneniyor. */
    UnverifiedTryingNextDub,

    /** Türkçe ses izi bulunamadı; en hızlı alternatif kaynakla sürdürülüyor. */
    NotFoundOpeningFastAlternative,

    /** Türkçe iz bulundu ancak cihaz seçemedi; varsayılan sesle devam ediliyor. */
    FoundButSelectionFailed,

    /** Türkçe ses parçası cihazda desteklenmiyor; başka dublajlı kaynak aranıyor. */
    UnsupportedTryingAnother,
}

/** Ses parçaları üzerinde Türkçe iz arama sonucu. */
sealed interface TurkishTrackMatch {
    data class Supported(val track: AudioTrackInfo) : TurkishTrackMatch
    data class Unsupported(val track: AudioTrackInfo) : TurkishTrackMatch
    data object None : TurkishTrackMatch
}

/** [TurkishAudioVerifier.evaluate] adımının döndürdüğü karar. */
sealed interface TurkishVerifyDecision {
    /** Henüz bekleniyor / mevcut kaynakta doğrulama sürüyor. */
    data object KeepWaiting : TurkishVerifyDecision

    /** Desteklenen Türkçe parça var ve seçilmeli. */
    data class SelectTrack(val index: Int) : TurkishVerifyDecision

    /** Desteklenen Türkçe parça başarıyla seçili; doğrulama tamam. */
    data object Verified : TurkishVerifyDecision

    /** Sıradaki dublajlı aday kaynağa aynı saniyeden geç. */
    data class TryNextDubbed(val stream: PlayableStream, val notice: TurkishAudioNotice) : TurkishVerifyDecision

    /** Alternatif hızlı kaynağa (veya çalışan önceki kaynağa) geç. */
    data class FallbackToAlternative(val stream: PlayableStream, val notice: TurkishAudioNotice) : TurkishVerifyDecision

    /** Değiştirilecek başka kaynak yok; mevcut kaynakta varsayılan sesle sürdür. */
    data class ContinueCurrent(val notice: TurkishAudioNotice) : TurkishVerifyDecision
}

/**
 * Türkçe ses izi algılama, aday sıralama ve tarama zaman aşımı kuralları (Android'den bağımsız).
 */
object TurkishAudioTrackRules {

    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
    private val TURKISH_LABEL_TOKENS = Regex("""\b(turkce|turkish|tr|tur|dublaj|dublajli)\b""")
    private val NEGATIVE_MARKERS = Regex("""\b(dublajsiz|turkce\s+altyaz\p{L}*)\b""")

    /** Metni küçük harfe indirger (`ı -> i`), aksan işaretlerini siler. */
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    }

    /** Ses parçasının dil kodu (`tr`, `tur`, `tr-TR`...) veya etiketi Türkçe mi? */
    fun isTurkishTrack(language: String?, label: String?): Boolean {
        val lang = language?.trim()?.takeIf { it.isNotEmpty() }?.let(::fold)
        if (lang != null) {
            val base = lang.replace('_', '-').substringBefore('-')
            if (base == "tr" || base == "tur" || base == "turkish" || base == "turkce") {
                return true
            }
        }
        val lbl = label?.trim()?.takeIf { it.isNotEmpty() }?.let(::fold) ?: return false
        val cleaned = NEGATIVE_MARKERS.replace(NON_ALNUM.replace(lbl, " "), " ")
        return TURKISH_LABEL_TOKENS.containsMatchIn(cleaned)
    }

    /** Desteklenen Türkçe parça varsa [TurkishTrackMatch.Supported], yalnızca desteklenmeyen varsa [TurkishTrackMatch.Unsupported]. */
    fun findTurkishTrack(tracks: List<AudioTrackInfo>): TurkishTrackMatch {
        val matching = tracks.filter { isTurkishTrack(it.language, it.label) }
        val supported = matching.firstOrNull { it.isSelected && it.isSupported }
            ?: matching.firstOrNull { it.isSupported }
        if (supported != null) return TurkishTrackMatch.Supported(supported)
        val unsupported = matching.firstOrNull()
        return if (unsupported != null) TurkishTrackMatch.Unsupported(unsupported) else TurkishTrackMatch.None
    }

    /** En hızlı açılacak alternatif kaynağı seçer: önce önceki çalışan kaynak, yoksa doğrudan + hafif HTTP adayı. */
    fun pickFastAlternative(
        lastWorking: PlayableStream?,
        candidates: List<PlayableStream>,
        sizes: Map<String, Long> = emptyMap(),
        maxHeight: Int = 1080,
    ): PlayableStream? {
        if (lastWorking != null) return lastWorking
        val ctx = RankContext(maxHeight = maxHeight, preferDub = false, sizes = sizes)
        val nonDubbed = candidates.filter { !it.dubbed && !SourceRanking.isTooHeavy(it, ctx) }
        val pool = nonDubbed.ifEmpty { candidates.filter { !SourceRanking.isTooHeavy(it, ctx) }.ifEmpty { candidates } }
        return pool.minWithOrNull(
            compareBy<PlayableStream>(
                { if (TorrentLinks.isMagnet(it.url)) 1 else 0 },
                { sizes[it.url] ?: it.sizeBytes ?: Long.MAX_VALUE },
                { -(it.resolution ?: 0) },
            ),
        )
    }
}

/**
 * Tek oynatma oturumu için Türkçe ses doğrulama durum makinesi (Android'den bağımsız, sanal saatle test edilebilir).
 */
class TurkishAudioVerifier(
    private val scanTimeoutMs: Long = DEFAULT_SCAN_MS,
) {
    var activeStreamUrl: String? = null
        private set
    var scanning: Boolean = false
        private set
    var abortedByUser: Boolean = false
        private set

    private var scanTimerStartedAtMs: Long? = null
    private var selectionAttemptedForIndex: Int? = null
    private var selectionAttemptedAtMs: Long? = null
    private var requireVerifiedRevertStream: PlayableStream? = null
    private val verifiedUrls = HashSet<String>()
    private val failedDubUrls = HashSet<String>()

    /** Yeni bir akış denenmeye başlandığında çağrılır. */
    fun begin(
        stream: PlayableStream,
        shouldVerify: Boolean,
        revertIfUnverified: PlayableStream? = null,
    ) {
        activeStreamUrl = stream.url
        scanTimerStartedAtMs = null
        selectionAttemptedForIndex = null
        selectionAttemptedAtMs = null
        requireVerifiedRevertStream = revertIfUnverified
        scanning = shouldVerify && !abortedByUser && stream.url !in verifiedUrls && stream.url !in failedDubUrls
    }

    /** Kullanıcı "Türkçe ses aramasını bırak..." dediğinde veya sesi elle değiştirdiğinde çağrılır. */
    fun stopByUser() {
        abortedByUser = true
        scanning = false
        scanTimerStartedAtMs = null
        requireVerifiedRevertStream = null
    }

    /** Yeni bir dublaj kaynağı elle öneriden tetiklenirse (kullanıcı aramayı manuel durdurmamışsa) sıfırlar. */
    fun resetForExplicitDubSwitch() {
        abortedByUser = false
    }

    fun hasFailedVerification(url: String): Boolean = url in failedDubUrls

    fun evaluate(
        stream: PlayableStream?,
        tracks: List<AudioTrackInfo>,
        playbackStarted: Boolean,
        nowMs: Long,
        remainingDubbedCandidates: List<PlayableStream>,
        remainingAlternativeCandidates: List<PlayableStream>,
        lastWorking: PlayableStream?,
        sizes: Map<String, Long> = emptyMap(),
        maxHeight: Int = 1080,
    ): TurkishVerifyDecision {
        if (stream == null || !scanning || abortedByUser || stream.url != activeStreamUrl) {
            return TurkishVerifyDecision.KeepWaiting
        }

        if (playbackStarted && scanTimerStartedAtMs == null) {
            scanTimerStartedAtMs = nowMs
        }

        when (val match = TurkishAudioTrackRules.findTurkishTrack(tracks)) {
            is TurkishTrackMatch.Supported -> {
                val track = match.track
                if (track.isSelected) {
                    verifiedUrls += stream.url
                    scanning = false
                    requireVerifiedRevertStream = null
                    return TurkishVerifyDecision.Verified
                }
                if (selectionAttemptedForIndex != track.index) {
                    selectionAttemptedForIndex = track.index
                    selectionAttemptedAtMs = nowMs
                    return TurkishVerifyDecision.SelectTrack(track.index)
                }
                val selectElapsed = nowMs - (selectionAttemptedAtMs ?: nowMs)
                if (selectElapsed < SELECT_SETTLE_MS) {
                    return TurkishVerifyDecision.KeepWaiting
                }
                // Cihaz seçimi uygulayamadı
                return failCurrentStream(
                    stream = stream,
                    noticeNextDub = TurkishAudioNotice.FoundButSelectionFailed,
                    noticeFallback = TurkishAudioNotice.FoundButSelectionFailed,
                    noticeStay = TurkishAudioNotice.FoundButSelectionFailed,
                    remainingDubbedCandidates = remainingDubbedCandidates,
                    remainingAlternativeCandidates = remainingAlternativeCandidates,
                    lastWorking = lastWorking,
                    sizes = sizes,
                    maxHeight = maxHeight,
                )
            }
            is TurkishTrackMatch.Unsupported -> {
                // Parçalarda Türkçe var ama cihaz çözemiyor: beklemeden sıradaki adaya geç.
                return failCurrentStream(
                    stream = stream,
                    noticeNextDub = TurkishAudioNotice.UnsupportedTryingAnother,
                    noticeFallback = TurkishAudioNotice.NotFoundOpeningFastAlternative,
                    noticeStay = TurkishAudioNotice.FoundButSelectionFailed,
                    remainingDubbedCandidates = remainingDubbedCandidates,
                    remainingAlternativeCandidates = remainingAlternativeCandidates,
                    lastWorking = lastWorking,
                    sizes = sizes,
                    maxHeight = maxHeight,
                )
            }
            TurkishTrackMatch.None -> {
                val timerStart = scanTimerStartedAtMs ?: return TurkishVerifyDecision.KeepWaiting
                if (nowMs - timerStart < scanTimeoutMs) {
                    return TurkishVerifyDecision.KeepWaiting
                }
                return failCurrentStream(
                    stream = stream,
                    noticeNextDub = TurkishAudioNotice.UnverifiedTryingNextDub,
                    noticeFallback = TurkishAudioNotice.NotFoundOpeningFastAlternative,
                    noticeStay = TurkishAudioNotice.NotFoundOpeningFastAlternative,
                    remainingDubbedCandidates = remainingDubbedCandidates,
                    remainingAlternativeCandidates = remainingAlternativeCandidates,
                    lastWorking = lastWorking,
                    sizes = sizes,
                    maxHeight = maxHeight,
                )
            }
        }
    }

    private fun failCurrentStream(
        stream: PlayableStream,
        noticeNextDub: TurkishAudioNotice,
        noticeFallback: TurkishAudioNotice,
        noticeStay: TurkishAudioNotice,
        remainingDubbedCandidates: List<PlayableStream>,
        remainingAlternativeCandidates: List<PlayableStream>,
        lastWorking: PlayableStream?,
        sizes: Map<String, Long>,
        maxHeight: Int,
    ): TurkishVerifyDecision {
        failedDubUrls += stream.url
        scanning = false

        val revertStream = requireVerifiedRevertStream?.takeIf { it.url != stream.url }
        requireVerifiedRevertStream = null
        if (revertStream != null) {
            return TurkishVerifyDecision.FallbackToAlternative(revertStream, noticeFallback)
        }

        val nextDub = remainingDubbedCandidates.firstOrNull { it.url != stream.url && it.url !in failedDubUrls }
        if (nextDub != null) {
            return TurkishVerifyDecision.TryNextDubbed(nextDub, noticeNextDub)
        }

        val alt = TurkishAudioTrackRules.pickFastAlternative(
            lastWorking = lastWorking?.takeIf { it.url != stream.url && it.url !in failedDubUrls },
            candidates = remainingAlternativeCandidates.filter { it.url != stream.url && it.url !in failedDubUrls },
            sizes = sizes,
            maxHeight = maxHeight,
        )
        return if (alt != null) {
            TurkishVerifyDecision.FallbackToAlternative(alt, noticeFallback)
        } else {
            TurkishVerifyDecision.ContinueCurrent(noticeStay)
        }
    }

    companion object {
        const val DEFAULT_SCAN_MS = 7_000L
        const val SELECT_SETTLE_MS = 1_500L
    }
}
