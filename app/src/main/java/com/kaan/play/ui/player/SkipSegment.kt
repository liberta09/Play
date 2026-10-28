package com.kaan.play.ui.player

import androidx.media3.common.Metadata
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SkipCategory
import java.util.Locale

/** Oynatıcıda tek dokunuşla atlanabilen bölüm türleri. */
enum class SkipSegmentKind {
    Recap,
    Intro,
    Outro,
    Preview,
}

data class SkipSegment(
    val kind: SkipSegmentKind,
    val startMs: Long,
    val endMs: Long,
    /** True only when the source explicitly supplied end_ms=null (end of media). */
    val openEndedEnd: Boolean = false,
) {
    val key: String get() = "${kind.name}:$startMs:$endMs:$openEndedEnd"

    val category: SkipCategory
        get() = when (kind) {
            SkipSegmentKind.Intro -> SkipCategory.Intro
            SkipSegmentKind.Recap -> SkipCategory.Recap
            SkipSegmentKind.Outro -> SkipCategory.Credits
            SkipSegmentKind.Preview -> SkipCategory.Preview
        }

    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/** Bir medya kapsayıcısından veya akış sağlayıcısından gelen bölüm başlığı. */
data class SkipMarker(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val openEndedEnd: Boolean = false,
)

/**
 * Intro/özet/outro aralıklarını açık bölüm işaretlerinden (kaynak chapter metadata'sı veya eşleşen
 * topluluk timestamp kaydı) çıkarır. Süreye bakarak yer tahmini yapılmaz: hatalı bir tahmin kullanıcıyı
 * içeriğin ortasında bırakabileceği için işaret bulunmayan kaynaklarda atlama düğmesi gösterilmez.
 * Hatalı/ölçek birimi uyuşmayan zamanlar kırpılmaz; güvenli aralığın dışındaysa tamamen reddedilir.
 */
object SkipSegmentDetector {
    private const val MIN_SEGMENT_MS = 3 * 1_000L
    private const val MAX_RECAP_MS = 3 * 60 * 1_000L
    private const val MAX_INTRO_MS = 4 * 60 * 1_000L
    private const val MAX_OUTRO_MS = 8 * 60 * 1_000L
    private const val MAX_PREVIEW_MS = 6 * 60 * 1_000L
    private const val MAX_OPENING_ZONE_MS = 8 * 60 * 1_000L
    private const val MAX_CLOSING_ZONE_MS = 12 * 60 * 1_000L

    fun detect(
        type: MediaType?,
        durationMs: Long,
        markers: List<SkipMarker> = emptyList(),
        addonType: String? = null,
    ): List<SkipSegment> {
        if (type !in setOf(MediaType.Tv, MediaType.Movie) ||
            addonType?.lowercase(Locale.ROOT) in setOf("tv", "channel") ||
            durationMs <= 0L
        ) {
            return emptyList()
        }

        val openingLimit = minOf(MAX_OPENING_ZONE_MS, durationMs / 3L)
        val closingLimit = maxOf(durationMs * 2L / 3L, durationMs - MAX_CLOSING_ZONE_MS)

        return markers.mapNotNull { marker ->
            // Bölüm zamanları video zaman çizelgesinde ms olmalıdır. Birim/süre uyuşmazlığını duration'a
            // kırpmak, tüm videoyu atlanabilir pencere gibi gösterebilirdi; geçersiz aralıkları reddet.
            val start = marker.startMs
            val end = marker.endMs
            if (start < 0L || end <= start || end > durationMs || end - start < MIN_SEGMENT_MS) {
                return@mapNotNull null
            }

            val kind = kindFor(marker.title) ?: return@mapNotNull null
            val length = end - start
            val plausible = when (kind) {
                SkipSegmentKind.Recap -> length <= MAX_RECAP_MS && start <= openingLimit
                SkipSegmentKind.Intro -> length <= MAX_INTRO_MS && start <= openingLimit
                SkipSegmentKind.Outro -> length <= MAX_OUTRO_MS && start >= closingLimit
                SkipSegmentKind.Preview -> length <= MAX_PREVIEW_MS && start >= closingLimit
            }
            if (plausible) SkipSegment(kind, start, end, marker.openEndedEnd) else null
        }
            .distinctBy { it.key }
            .sortedBy { it.startMs }
    }

    /** Media3'ün ID3/metadata bölüm kayıtlarını, sürüm sınıflarına doğrudan bağlanmadan okur. */
    fun markersFromMetadata(metadata: Metadata): List<SkipMarker> {
        val markers = ArrayList<SkipMarker>()
        for (index in 0 until metadata.length()) {
            val entry = metadata.get(index)
            if (!entry.javaClass.simpleName.contains("chapter", ignoreCase = true)) continue
            val start = readNumber(entry, "startTimeMs", "startTime") ?: continue
            val end = readNumber(entry, "endTimeMs", "endTime") ?: continue
            val title = readText(entry).takeIf { it.isNotBlank() } ?: continue
            markers += SkipMarker(title, start, end)
        }
        return markers
    }

    /**
     * [resumePositionMs] devam konumudur. Başlangıçta oynatıcı seek'i henüz uygulamadan 0 raporlasa bile,
     * daha önce bitmiş olan intro/özet düğmesi geçici olarak görünmez.
     */
    fun active(
        segments: List<SkipSegment>,
        positionMs: Long,
        dismissed: Set<String> = emptySet(),
        resumePositionMs: Long = 0L,
    ): SkipSegment? = activeSegments(segments, positionMs, dismissed, resumePositionMs).firstOrNull()

    fun activeSegments(
        segments: List<SkipSegment>,
        positionMs: Long,
        dismissed: Set<String> = emptySet(),
        resumePositionMs: Long = 0L,
    ): List<SkipSegment> = segments.filter {
        it.key !in dismissed && it.endMs > resumePositionMs.coerceAtLeast(0L) && it.contains(positionMs)
    }.sortedWith(compareBy<SkipSegment> { it.startMs }.thenBy { it.kind.ordinal })

    /** Returns one eligible segment; callers record its key before seeking to prevent rewind loops. */
    fun autoSkipCandidate(
        active: List<SkipSegment>,
        enabled: Boolean,
        playing: Boolean,
        attemptedThisSession: Set<String>,
    ): SkipSegment? = if (!enabled || !playing) null else active.firstOrNull { it.key !in attemptedThisSession }

    private fun kindFor(raw: String): SkipSegmentKind? {
        val title = normalize(raw).replace(Regex("[^a-z0-9]+"), " ").trim()
        val words = title.split(' ').filter(String::isNotBlank).toSet()
        fun phrase(value: String) = " $title ".contains(" $value ")

        return when {
            "recap" in words || "summary" in words || phrase("previously on") || phrase("previous on") ||
                phrase("daha once") || phrase("onceki bolum") || "ozet" in words -> SkipSegmentKind.Recap
            "outro" in words || phrase("end credits") || phrase("closing credits") || phrase("final credits") ||
                phrase("son jenerik") || phrase("kapanis jenerigi") -> SkipSegmentKind.Outro
            "intro" in words || phrase("opening credits") || phrase("opening theme") || phrase("opening sequence") ||
                phrase("title sequence") || phrase("acilis jenerigi") || phrase("baslangic jenerigi") -> SkipSegmentKind.Intro
            "preview" in words || "previews" in words || "next episode preview" in title -> SkipSegmentKind.Preview
            else -> null
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
        .replace('ş', 's')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ö', 'o')
        .replace('ç', 'c')

    private fun readNumber(instance: Any, vararg names: String): Long? {
        for (name in names) {
            val fieldValue = generateSequence(instance.javaClass) { it.superclass }
                .mapNotNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
                .firstNotNullOfOrNull { field ->
                    runCatching {
                        field.isAccessible = true
                        (field.get(instance) as? Number)?.toLong()
                    }.getOrNull()
                }
            if (fieldValue != null) return fieldValue

            val getter = "get" + name.replaceFirstChar { it.uppercase(Locale.ROOT) }
            val methodValue = runCatching {
                (instance.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                    ?.invoke(instance) as? Number)?.toLong()
            }.getOrNull()
            if (methodValue != null) return methodValue
        }
        return null
    }

    /** ChapterFrame başlığını ve alt ID3 metin karelerini okur; nesne toString()'ini başlık sanmaz. */
    private fun readText(instance: Any): String {
        val values = buildList {
            listOf("title", "value", "values", "chapterId", "elementId", "id", "description", "text")
                .forEach { name -> addAll(readTextMember(instance, name)) }
            val children = readMember(instance, "subFrames")
            when (children) {
                is Array<*> -> children.filterNotNull().forEach { child ->
                    listOf("title", "value", "values", "description", "text").forEach { name ->
                        addAll(readTextMember(child, name))
                    }
                }
                is Iterable<*> -> children.filterNotNull().forEach { child ->
                    listOf("title", "value", "values", "description", "text").forEach { name ->
                        addAll(readTextMember(child, name))
                    }
                }
            }
        }
        return values.filter(String::isNotBlank).distinct().joinToString(" ")
    }

    private fun readTextMember(instance: Any, name: String): List<String> = textValues(readMember(instance, name))

    private fun textValues(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is Array<*> -> value.flatMap(::textValues)
        is Iterable<*> -> value.flatMap(::textValues)
        else -> emptyList()
    }

    private fun readMember(instance: Any, name: String): Any? {
        val fieldValue = generateSequence(instance.javaClass) { it.superclass }
            .mapNotNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
            .firstNotNullOfOrNull { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(instance)
                }.getOrNull()
            }
        if (fieldValue != null) return fieldValue
        val getter = "get" + name.replaceFirstChar { it.uppercase(Locale.ROOT) }
        return runCatching {
            instance.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }?.invoke(instance)
        }.getOrNull()
    }
}
