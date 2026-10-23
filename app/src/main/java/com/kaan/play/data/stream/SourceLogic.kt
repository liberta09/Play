package com.kaan.play.data.stream

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/**
 * Kaynağın Türkçe dublajlı olup olmadığını ad/başlık/açıklama/dosya adındaki işaretlerden anlar. Belirsizse **altyazılı** sayılır.
 *
 * Metin önce katlanır: küçük harf, Türkçe karakterler ve aksanlar atılır (`Türkçe` → `turkce`, `Dublajlı` → `dublajli`), böylece
 * büyük/küçük harf ve Türkçe karakter farkı sonucu değiştirmez. Aranan işaretler (katlanmış hâlleriyle):
 * `TR Dublaj`, `Türkçe Dublaj`, `Dublajlı`, `Dublaj`, `TR Dub`, `Turkish Dubbed`, `Dual Audio TR`, `TR-EN`.
 * "Dublajsız" bir dublaj işareti değildir.
 */
object DubDetector {

    private val MARKERS: List<Regex> = listOf(
        Regex("""\btr[\s._-]*dublaj"""),
        Regex("""\bturkce[\s._-]*dublaj"""),
        Regex("""dublajli"""),
        Regex("""\bdublaj"""),
        Regex("""\btr[\s._-]*dub\b"""),
        Regex("""\bturkish[\s._-]*dubbed\b"""),
        Regex("""\bdual[\s._-]*audio[\s._-]*tr\b"""),
        Regex("""\btr-en\b"""),
    )

    private val NEGATIVE = Regex("""\bdublajsiz""")

    fun isDubbed(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val folded = fold(text)
        val withoutNegative = NEGATIVE.replace(folded, " ")
        return MARKERS.any { it.containsMatchIn(withoutNegative) }
    }

    /** Küçük harf + aksan/Türkçe karakter atma (`İ`, `ı`, `ş`, `ğ`, `ç`, `ö`, `ü` → ASCII karşılığı). */
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    }
}

/** Boyutu arayüzde gösterilecek biçime çevirir: 1 GB ve üstü "7,7 GB" (1 ondalık), altı "700 MB". */
object SizeText {
    private const val GB = 1_000_000_000.0
    private const val MB = 1_000_000.0

    /** [bytes] <= 0 ya da null: gösterilecek bir şey yok (null döner; "0 GB" yazılmaz). */
    fun format(bytes: Long?, locale: Locale): String? {
        if (bytes == null || bytes <= 0) return null
        return if (bytes >= GB) {
            String.format(locale, "%.1f GB", bytes / GB)
        } else {
            val mb = Math.round(bytes / MB).coerceAtLeast(1)
            String.format(locale, "%d MB", mb)
        }
    }

    /** "1920×872" (genişlik ya da yükseklik bilinmiyorsa null). */
    fun dimensions(width: Int, height: Int): String? = if (width > 0 && height > 0) "$width×$height" else null
}

/** Kullanıcının bir film/dizi için elle seçtiği kaynak: sonraki açılışta ve dizinin sonraki bölümünde önce denenir. */
@Serializable
data class SourcePref(val addonId: Long, val addonName: String, val host: String? = null, val torrent: Boolean = false)

/** Panel süzgeci: Tümü / Dublajlı / Altyazılı. */
enum class AudioFilter { All, Dubbed, Subbed }

/** Panel sıralaması. */
enum class SourceSort { Recommended, SizeDesc, ResolutionDesc }

/** Sıralama ve seçim bağlamı. */
data class RankContext(
    /** Cihazın MediaCodecList ile çözebildiği en yüksek video yüksekliği. */
    val maxHeight: Int = 1080,
    val preferDub: Boolean = true,
    val preference: SourcePref? = null,
    /** Dosya boyutları (adres → bayt): eklentinin verdiğine ek olarak sonradan bulunanlar (HEAD, torrent üst bilgisi). */
    val sizes: Map<String, Long> = emptyMap(),
    val isSlowHost: (String) -> Boolean = { false },
)

/**
 * Kaynak sıralaması (Android'den bağımsız, birim testli).
 *
 * **Başlangıç sırası** ([recommended]): 1) cihazın çözemeyeceği çözünürlük (örn. 4K) ya da çok büyük dosya (~15 GB üstü) en sona;
 * 2) Türkçe dublaj önceliği açıkken dublajlılar altyazılıların önüne; 3) kullanıcının önceki seçimi kendi grubunda öne;
 * 4) doğrudan HTTP/HLS torrentten önce; 5) 1080p'ye kadar en yüksek çözünürlük (sonra 1080p üstü, sonra bilinmeyen);
 * 6) aynı çözünürlükte küçük dosya (daha çabuk açılır; boyutu bilinmeyen sonda).
 */
object SourceRanking {

    /** Bundan büyük dosyalar başlangıçta sona gider (kullanıcı sonradan panelden seçebilir). */
    const val HUGE_BYTES = 15_000_000_000L

    fun sizeOf(stream: PlayableStream, sizes: Map<String, Long>): Long? = sizes[stream.url] ?: stream.sizeBytes

    fun isTooHeavy(stream: PlayableStream, ctx: RankContext): Boolean {
        val tooSharp = stream.resolution?.let { it > ctx.maxHeight } ?: false
        val tooBig = sizeOf(stream, ctx.sizes)?.let { it > HUGE_BYTES } ?: false
        return tooSharp || tooBig
    }

    private fun resolutionTier(stream: PlayableStream): Int {
        val r = stream.resolution ?: return 2
        return if (r <= 1080) 0 else 1
    }

    fun matchesPreference(stream: PlayableStream, pref: SourcePref?): Boolean =
        pref != null && stream.source.addonId == pref.addonId

    fun recommended(streams: List<PlayableStream>, ctx: RankContext): List<PlayableStream> {
        val comparator = compareBy<PlayableStream>(
            { if (isTooHeavy(it, ctx)) 1 else 0 },
            { if (ctx.preferDub && !it.dubbed) 1 else 0 },
            { if (ctx.isSlowHost(it.url)) 1 else 0 },
            { if (matchesPreference(it, ctx.preference)) 0 else 1 },
            { if (TorrentLinks.isMagnet(it.url)) 1 else 0 },
            { resolutionTier(it) },
            { -(it.resolution ?: 0) },
            { sizeOf(it, ctx.sizes) ?: Long.MAX_VALUE },
        )
        return streams.distinctBy { it.url }.sortedWith(comparator)
    }

    fun sorted(streams: List<PlayableStream>, sort: SourceSort, ctx: RankContext): List<PlayableStream> = when (sort) {
        SourceSort.Recommended -> recommended(streams, ctx)
        SourceSort.SizeDesc -> streams.distinctBy { it.url }.sortedWith(
            compareByDescending<PlayableStream> { sizeOf(it, ctx.sizes) ?: -1L }.thenByDescending { it.resolution ?: 0 },
        )
        SourceSort.ResolutionDesc -> streams.distinctBy { it.url }.sortedWith(
            compareByDescending<PlayableStream> { it.resolution ?: 0 }.thenByDescending { sizeOf(it, ctx.sizes) ?: -1L },
        )
    }

    fun filtered(streams: List<PlayableStream>, filter: AudioFilter): List<PlayableStream> = when (filter) {
        AudioFilter.All -> streams
        AudioFilter.Dubbed -> streams.filter { it.dubbed }
        AudioFilter.Subbed -> streams.filter { !it.dubbed }
    }
}

/** [StartPolicy.decide] sonucu. */
sealed interface StartDecision {
    /** Henüz başlama: [untilMs] (başlangıçtan itibaren) dublajlı kaynak beklenir; null: yeni kaynak gelmesini bekle. */
    data class Wait(val untilMs: Long?) : StartDecision

    data class Start(val stream: PlayableStream) : StartDecision

    /** Tüm kaynaklar cevap verdi, oynatılacak bir şey yok. */
    data object NoSources : StartDecision
}

/**
 * Otomatik başlangıç kararı. Türkçe dublaj önceliği açıkken dublajlı (ve çok ağır olmayan) kaynak gelir gelmez o başlar; yoksa en çok
 * [dubWaitMs] (8 sn) beklenir, sonra altyazılıyla başlanır (tüm kaynaklar bundan önce bittiyse beklemeden). Öncelik kapalıyken ilk uygun
 * kaynak gelir gelmez başlanır. [ordered]: o ana kadar gelenler, [SourceRanking.recommended] sırasında.
 */
class StartPolicy(private val preferDub: Boolean, private val dubWaitMs: Long = DUB_WAIT_MS) {

    fun decide(elapsedMs: Long, ordered: List<PlayableStream>, ctx: RankContext, finished: Boolean): StartDecision {
        val first = ordered.firstOrNull() ?: return if (finished) StartDecision.NoSources else StartDecision.Wait(null)
        if (!preferDub) return StartDecision.Start(first)
        val dubOk = first.dubbed && !SourceRanking.isTooHeavy(first, ctx)
        if (dubOk) return StartDecision.Start(first)
        if (finished || elapsedMs >= dubWaitMs) return StartDecision.Start(first)
        return StartDecision.Wait(dubWaitMs)
    }

    companion object {
        const val DUB_WAIT_MS = 8_000L
    }
}
