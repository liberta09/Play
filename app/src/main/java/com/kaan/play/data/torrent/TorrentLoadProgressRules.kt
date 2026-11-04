package com.kaan.play.data.torrent

/**
 * Android ve libtorrent'ten bağımsız torrent yükleme ekranı ilerleme kuralları (saf sınıf, birim testli).
 *
 * Kurallar (bağlayıcı):
 * 1. Aşama 1 (Metadata alınıyor): 0 ile %30 arası. Metadata gelmiyorsa yüzde sabit kalır.
 * 2. Aşama 2 (Başlangıç tamponu): %30 ile %100 arası (tamponlanan / ilk görüntü için gereken bayt).
 * 3. Monotonluk: İlerleme yüzdesi asla geriye gitmez (max(önceki, yeni)).
 * 4. %100 Yalnızca İlk Karede: İlk kare çizilmeden önce ilerleme en çok %99 olabilir; %100 sadece ilk karede verilir.
 * 5. Hata Yolu: Logo, durum okuma veya hesaplama hatasında sessizce standart görünüme düşülür.
 */
object TorrentLoadProgressRules {

    const val PHASE_1_MAX = 30f
    const val PHASE_2_MAX = 100f
    const val CAP_BEFORE_FIRST_FRAME = 99f
    const val DEFAULT_REQUIRED_BYTES = 2L * 1024L * 1024L // 2 MB ilk görüntü için varsayılan tampon hedefi

    enum class Phase {
        Metadata,
        Buffering,
    }

    data class ProgressState(
        val progress: Float,
        val phase: Phase,
        val isFirstFrame: Boolean = false,
        val fallbackToStandard: Boolean = false,
    )

    data class TorrentLoadSnapshot(
        val hasMetadata: Boolean = false,
        val metadataFraction: Float = 0f,
        val bufferedBytes: Long = 0L,
        val requiredBytes: Long = DEFAULT_REQUIRED_BYTES,
    )

    fun initialState(): ProgressState = ProgressState(
        progress = 0f,
        phase = Phase.Metadata,
        isFirstFrame = false,
        fallbackToStandard = false,
    )

    fun fallbackState(): ProgressState = ProgressState(
        progress = 0f,
        phase = Phase.Metadata,
        isFirstFrame = false,
        fallbackToStandard = true,
    )

    /**
     * Motor durumu ve ilk kare bayrağına göre yükleme ilerleme durumunu hesaplar.
     */
    fun calculateProgress(
        hasMetadata: Boolean,
        metadataFraction: Float = 0f,
        bufferedBytes: Long = 0L,
        requiredBytes: Long = DEFAULT_REQUIRED_BYTES,
        firstFrameRendered: Boolean = false,
        previousProgress: Float = 0f,
    ): ProgressState {
        // Kural: %100 yalnızca ilk karede
        if (firstFrameRendered) {
            return ProgressState(
                progress = PHASE_2_MAX,
                phase = Phase.Buffering,
                isFirstFrame = true,
                fallbackToStandard = false,
            )
        }

        // Kural: Aşama 1 - metadata alınıyor (0 ile %30 arası)
        if (!hasMetadata) {
            val raw = (metadataFraction.coerceIn(0f, 1f)) * PHASE_1_MAX
            val p = maxOf(previousProgress, raw).coerceIn(0f, PHASE_1_MAX)
            return ProgressState(
                progress = p,
                phase = Phase.Metadata,
                isFirstFrame = false,
                fallbackToStandard = false,
            )
        }

        // Kural: Aşama 2 - başlangıç tamponu (%30 ile %100 arası; tamponlanan / ilk görüntü için gereken bayt)
        val req = if (requiredBytes > 0L) requiredBytes else DEFAULT_REQUIRED_BYTES
        val bufferFraction = (bufferedBytes.toDouble() / req.toDouble()).coerceIn(0.0, 1.0).toFloat()
        val raw = PHASE_1_MAX + bufferFraction * (PHASE_2_MAX - PHASE_1_MAX)
        // İlk kare gelene kadar %99 ile sınırlandırılır
        val capped = raw.coerceAtMost(CAP_BEFORE_FIRST_FRAME)
        val p = maxOf(previousProgress, capped).coerceIn(PHASE_1_MAX, CAP_BEFORE_FIRST_FRAME)

        return ProgressState(
            progress = p,
            phase = Phase.Buffering,
            isFirstFrame = false,
            fallbackToStandard = false,
        )
    }

    /**
     * Güvenli hesaplama: herhangi bir istisna veya hata durumunda sessizce standart görünüme düşer.
     */
    fun safeCalculateProgress(
        hasMetadata: Boolean,
        metadataFraction: Float = 0f,
        bufferedBytes: Long = 0L,
        requiredBytes: Long = DEFAULT_REQUIRED_BYTES,
        firstFrameRendered: Boolean = false,
        previousProgress: Float = 0f,
        hasError: Boolean = false,
    ): ProgressState {
        if (hasError) return fallbackState()
        return try {
            calculateProgress(
                hasMetadata = hasMetadata,
                metadataFraction = metadataFraction,
                bufferedBytes = bufferedBytes,
                requiredBytes = requiredBytes,
                firstFrameRendered = firstFrameRendered,
                previousProgress = previousProgress,
            )
        } catch (_: Throwable) {
            fallbackState()
        }
    }

    fun safeCalculateProgress(
        snapshot: TorrentLoadSnapshot,
        firstFrameRendered: Boolean = false,
        previousProgress: Float = 0f,
        hasError: Boolean = false,
    ): ProgressState {
        return safeCalculateProgress(
            hasMetadata = snapshot.hasMetadata,
            metadataFraction = snapshot.metadataFraction,
            bufferedBytes = snapshot.bufferedBytes,
            requiredBytes = snapshot.requiredBytes,
            firstFrameRendered = firstFrameRendered,
            previousProgress = previousProgress,
            hasError = hasError,
        )
    }
}
