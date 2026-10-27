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
    val isLive: Boolean = false,
)

/**
 * Kaynak sıralaması (Android'den bağımsız, birim testli).
 *
 * **Başlangıç sırası** ([recommended]): 1) cihazın çözemeyeceği çözünürlük (örn. 4K) ya da çok büyük dosya (~15 GB üstü) en sona;
 * 2) Türkçe dublaj önceliği açıkken dublajlılar altyazılıların önüne; 3) kullanıcının önceki seçimi kendi grubunda öne;
 * 4) doğrudan HTTP/HLS torrentten önce; 5) 1080p'ye kadar en yüksek çözünürlük (sonra 1080p üstü, sonra bilinmeyen);
 * 6) aynı çözünürlükte küçük dosya (daha çabuk açılır; boyutu bilinmeyen sonda).
 *
 * **Canlı TV yayını:** ([isLive]): eklentinin doğal katalog akış sırası korunur, çözünürlük katmanıyla çalışan HLS akışları arkaya atılmaz.
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

    private data class DecoratedRecommended(
        val stream: PlayableStream,
        val tooHeavy: Int,
        val notDubbed: Int,
        val slowHost: Int,
        val notPreferred: Int,
        val isMagnet: Int,
        val resTier: Int,
        val negRes: Int,
        val sortSize: Long,
    )

    private data class DecoratedSize(
        val stream: PlayableStream,
        val size: Long,
        val res: Int,
    )

    private data class DecoratedResolution(
        val stream: PlayableStream,
        val res: Int,
        val size: Long,
    )

    fun recommended(streams: List<PlayableStream>, ctx: RankContext): List<PlayableStream> {
        val distinct = streams.distinctBy { it.url }
        if (distinct.size <= 1) return distinct

        // Host başına isSlowHost sonucunu tek seferde önbellekle
        val slowHostCache = HashMap<String, Boolean>()
        fun checkSlow(url: String): Int {
            val host = StreamLog.hostOf(url)
            if (host == "?") return 0
            val isSlow = slowHostCache.getOrPut(host) { ctx.isSlowHost(url) }
            return if (isSlow) 1 else 0
        }

        if (ctx.isLive) {
            val decorated = distinct.map { s ->
                DecoratedRecommended(
                    stream = s,
                    tooHeavy = if (isTooHeavy(s, ctx)) 1 else 0,
                    notDubbed = 0,
                    slowHost = checkSlow(s.url),
                    notPreferred = if (matchesPreference(s, ctx.preference)) 0 else 1,
                    isMagnet = if (TorrentLinks.isMagnet(s.url)) 1 else 0,
                    resTier = 0,
                    negRes = 0,
                    sortSize = 0L,
                )
            }
            return decorated.sortedWith(
                compareBy(
                    { it.tooHeavy },
                    { it.slowHost },
                    { it.notPreferred },
                    { it.isMagnet },
                )
            ).map { it.stream }
        } else {
            val decorated = distinct.map { s ->
                DecoratedRecommended(
                    stream = s,
                    tooHeavy = if (isTooHeavy(s, ctx)) 1 else 0,
                    notDubbed = if (ctx.preferDub && !s.dubbed) 1 else 0,
                    slowHost = checkSlow(s.url),
                    notPreferred = if (matchesPreference(s, ctx.preference)) 0 else 1,
                    isMagnet = if (TorrentLinks.isMagnet(s.url)) 1 else 0,
                    resTier = resolutionTier(s),
                    negRes = -(s.resolution ?: 0),
                    sortSize = sizeOf(s, ctx.sizes) ?: Long.MAX_VALUE,
                )
            }
            return decorated.sortedWith(
                compareBy(
                    { it.tooHeavy },
                    { it.notDubbed },
                    { it.slowHost },
                    { it.notPreferred },
                    { it.isMagnet },
                    { it.resTier },
                    { it.negRes },
                    { it.sortSize },
                )
            ).map { it.stream }
        }
    }

    fun sorted(streams: List<PlayableStream>, sort: SourceSort, ctx: RankContext): List<PlayableStream> = when (sort) {
        SourceSort.Recommended -> recommended(streams, ctx)
        SourceSort.SizeDesc -> {
            val distinct = streams.distinctBy { it.url }
            if (distinct.size <= 1) distinct
            else distinct.map { s ->
                DecoratedSize(
                    stream = s,
                    size = sizeOf(s, ctx.sizes) ?: -1L,
                    res = s.resolution ?: 0,
                )
            }.sortedWith(
                compareByDescending<DecoratedSize> { it.size }.thenByDescending { it.res }
            ).map { it.stream }
        }
        SourceSort.ResolutionDesc -> {
            val distinct = streams.distinctBy { it.url }
            if (distinct.size <= 1) distinct
            else distinct.map { s ->
                DecoratedResolution(
                    stream = s,
                    res = s.resolution ?: 0,
                    size = sizeOf(s, ctx.sizes) ?: -1L,
                )
            }.sortedWith(
                compareByDescending<DecoratedResolution> { it.res }.thenByDescending { it.size }
            ).map { it.stream }
        }
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
        const val DUB_WAIT_MS = 3_500L
    }
}
