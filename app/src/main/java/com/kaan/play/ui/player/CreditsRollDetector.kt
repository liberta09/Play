package com.kaan.play.ui.player

import android.graphics.Bitmap
import android.view.TextureView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bir OCR satırı ve ekran yüksekliğine göre normalize edilmiş dikey konumu. */
data class CreditTextLine(val text: String, val centerY: Float)

/**
 * Tek karede yazı görülmesini jenerik saymaz. Aynı metin satırlarından en az dördünün, yeterli
 * dikey alana yayılmış biçimde, ardışık karelerde tutarlı olarak yukarı kaymasını ister.
 */
class CreditsRollMotion {
    private var previous: List<CreditTextLine> = emptyList()
    private var consecutiveRolls = 0

    fun reset() {
        previous = emptyList()
        consecutiveRolls = 0
    }

    fun observe(lines: List<CreditTextLine>): Boolean {
        val current = lines.mapNotNull { line ->
            val normalized = normalize(line.text)
            if (normalized.length < MIN_TEXT_CHARS || line.centerY !in 0f..1f) null
            else CreditTextLine(normalized, line.centerY)
        }
        if (previous.isEmpty() || current.isEmpty()) {
            previous = current
            consecutiveRolls = 0
            return false
        }

        if (isUpwardRoll(previous, current)) consecutiveRolls++ else consecutiveRolls = 0
        previous = current
        return consecutiveRolls >= REQUIRED_CONSECUTIVE_ROLLS
    }

    private fun isUpwardRoll(before: List<CreditTextLine>, after: List<CreditTextLine>): Boolean {
        val oldByText = before.groupBy(CreditTextLine::text).mapValues { (_, values) -> values.sortedBy(CreditTextLine::centerY) }
        val newByText = after.groupBy(CreditTextLine::text).mapValues { (_, values) -> values.sortedBy(CreditTextLine::centerY) }
        val shifts = ArrayList<Pair<Float, Float>>()
        for ((text, oldLines) in oldByText) {
            val newLines = newByText[text] ?: continue
            oldLines.zip(newLines).forEach { (old, new) ->
                // Positive delta means the text moved toward the top of the image.
                val shift = old.centerY - new.centerY
                if (shift in MIN_SHIFT..MAX_SHIFT) shifts += shift to old.centerY
            }
        }
        if (shifts.size < MIN_MATCHED_LINES) return false
        val yPositions = shifts.map { it.second }
        if ((yPositions.maxOrNull() ?: 0f) - (yPositions.minOrNull() ?: 0f) < MIN_VERTICAL_SPAN) return false

        val sorted = shifts.map { it.first }.sorted()
        val median = sorted[sorted.size / 2]
        val consistent = sorted.count { abs(it - median) <= MAX_SHIFT_DEVIATION }
        return median in MIN_SHIFT..MAX_SHIFT && consistent.toFloat() / sorted.size >= CONSISTENT_SHIFT_FRACTION
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    private companion object {
        const val MIN_TEXT_CHARS = 3
        const val MIN_MATCHED_LINES = 4
        const val MIN_VERTICAL_SPAN = 0.16f
        const val MIN_SHIFT = 0.003f
        const val MAX_SHIFT = 0.14f
        const val MAX_SHIFT_DEVIATION = 0.025f
        const val CONSISTENT_SHIFT_FRACTION = 0.75f
        const val REQUIRED_CONSECUTIVE_ROLLS = 2
    }
}

/**
 * Düşük sıklıkta TextureView karesi alır ve cihaz üzerindeki ML Kit Latin OCR ile metin kaymasını
 * ölçer. Görüntü veya OCR sonucu cihaz dışına çıkarılmaz. Kapalı kaynaklı/DRM yüzeyinde kare yoksa
 * algılama kapalı kalır; açık bölüm jenerik metadata'sı ayrıca güvenli yedek olarak kullanılır.
 */
class CreditsRollDetector : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val motion = CreditsRollMotion()

    suspend fun inspect(textureView: TextureView?): Boolean {
        if (textureView == null) {
            motion.reset()
            return false
        }
        val bitmap = withContext(Dispatchers.Main.immediate) {
            if (!textureView.isAttachedToWindow || !textureView.isAvailable || textureView.width <= 0 || textureView.height <= 0) {
                null
            } else {
                runCatching {
                    val scale = minOf(1f, MAX_BITMAP_WIDTH.toFloat() / textureView.width, MAX_BITMAP_HEIGHT.toFloat() / textureView.height)
                    val width = (textureView.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (textureView.height * scale).roundToInt().coerceAtLeast(1)
                    textureView.getBitmap(width, height)
                }.getOrNull()
            }
        }
        if (bitmap == null) {
            motion.reset()
            return false
        }

        val lines = recognize(bitmap)
        return motion.observe(lines)
    }

    fun reset() = motion.reset()

    override fun close() {
        recognizer.close()
        motion.reset()
    }

    private suspend fun recognize(bitmap: Bitmap): List<CreditTextLine> =
        suspendCancellableCoroutine { continuation ->
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        val lines = runCatching {
                            result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                                val bounds = line.boundingBox ?: return@mapNotNull null
                                CreditTextLine(line.text, bounds.centerY().toFloat() / bitmap.height.coerceAtLeast(1))
                            }
                        }.getOrDefault(emptyList())
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(lines)
                    }
                    .addOnFailureListener {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
            } catch (_: Exception) {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }

    private companion object {
        const val MAX_BITMAP_WIDTH = 960
        const val MAX_BITMAP_HEIGHT = 540
    }
}
