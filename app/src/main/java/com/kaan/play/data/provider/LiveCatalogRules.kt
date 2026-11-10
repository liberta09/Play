package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/** Canlı kanal için bir sağlayıcıdaki oynatma rotası. */
@Serializable
data class LiveSourceRoute(
    val pluginId: String,
    val addonType: String,
    val contentId: String,
    val dataUrl: String? = null,
)

/** Farklı sağlayıcılardan aynı kanala ait birleştirilmiş canlı TV kanalı. */
data class MergedLiveChannel(
    val channelKey: String,
    val title: String,
    val logoUrl: String?,
    val routes: List<LiveSourceRoute>,
    val items: List<CatalogItem>,
)

/** TV rafında yalnızca eklentinin açıkça canlı kanal olarak işaretlediği öğeleri tutar ve aynı kanalları birleştirir. */
object LiveCatalogRules {
    private val liveTypes = setOf("tv", "channel")
    private val turkishLocale = Locale.forLanguageTag("tr")
    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val qualityWordsRegex = Regex("\\b(uhd|fhd|hd|4k|8k|1080p|720p|576p)\\b")
    private val nonAlnumRegex = Regex("[^a-z0-9]+")
    private val multiSpaceRegex = Regex("\\s+")
    private val leadingDigitsRegex = Regex("^([0-9]+)")

    private val canonicalChannelPrefixes: List<String> = listOf(
        "bein sports", "bein sport", "trt spor", "trt 1", "trt 2", "trt haber", "trt belgesel",
        "trt çocuk", "trt muzik", "trt müzik", "trt avaz", "trt world", "trt türk", "trt eba",
        "trt spor yıldız", "kanal d", "kanal 7", "star tv", "show tv", "now tv", "fox tv", "atv",
        "tv8", "tv 8", "a haber", "a spor", "a para", "a news", "ntv", "cnn türk",
        "cnn international", "habertürk", "halk tv", "tele1", "tv100", "bloomberg ht", "kanal 24",
        "24 tv", "ulusal kanal", "teve2", "power türk", "powerturk", "kral tv", "dream türk",
        "number one türk", "s sport", "ssport", "tivibu spor", "exxen spor", "tabii spor",
        "eurosport", "national geographic", "animal planet", "discovery channel",
        "cartoon network", "minika çocuk", "minika go", "disney channel",
    ).map(::normalizedChannelName)
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    private val liveCatalogMarkers = listOf("live", "channel", "canlı", "canli", "kanal", "iptv")
    private val knownChannelNames = setOf(
        "0 wiospor", "wiospor", "anthology canli tv", "albanialive", "ardaspor", "aslantv", "atomspor",
        "basketballreplays", "basketballvideo", "bcsports", "canlitv", "dexter", "domatestv", "dominotv", "golgetv",
        "intersportv", "jestyayin", "kacakspor", "koalatv", "kool tv", "kralsporhd", "leotv",
        "mackeyfi", "mahsunsports", "mien tv", "mrc live", "myway tv latte",
    ).mapTo(mutableSetOf(), ::channelKey)
    private val knownLiveProviderNames = setOf(
        "wiospor", "bronzecloud", "turkspor", "btvault", "makoto'nun cloudstream reposu",
        "safarxe cloudstream", "neo türkçe eklentiler", "turkish providers repository", "latte sinetech tr",
    ).mapTo(mutableSetOf(), ::channelKey)

    fun normalizedChannelName(value: String): String {
        val lower = value.lowercase(turkishLocale).replace('ı', 'i')
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarksRegex, "")
        return nfd
            .replace(qualityWordsRegex, " ")
            .replace(nonAlnumRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun channelGroupKey(title: String): String {
        val normalized = normalizedChannelName(title)
        if (normalized.isEmpty()) return ""
        val matchedPrefix = canonicalChannelPrefixes.firstOrNull { prefix ->
            normalized == prefix || normalized.startsWith("$prefix ")
        }
        val base = if (matchedPrefix != null) {
            val remainder = if (normalized.length > matchedPrefix.length) {
                normalized.substring(matchedPrefix.length + 1)
            } else {
                ""
            }
            val firstDigits = leadingDigitsRegex.find(remainder)?.groupValues?.get(1)
            if (firstDigits != null) "$matchedPrefix $firstDigits" else matchedPrefix
        } else {
            normalized
        }
        return base.filter { it.isLetterOrDigit() }
    }

    fun routeFor(item: CatalogItem): LiveSourceRoute? {
        val providerId = item.providerId.trim()
        val title = item.title.trim()
        if (providerId.isEmpty() || title.isEmpty()) return null
        val normalizedType = item.addonType?.trim()?.lowercase(Locale.ROOT)
        val addonType = if (normalizedType in liveTypes) normalizedType!! else "tv"
        return when {
            providerId.startsWith("nuvio_addon:") -> {
                val idNum = providerId.removePrefix("nuvio_addon:").toLongOrNull()
                val contentId = item.id.trim()
                if (idNum != null && idNum > 0L && contentId.isNotEmpty()) {
                    LiveSourceRoute(pluginId = providerId, addonType = addonType, contentId = contentId, dataUrl = null)
                } else {
                    null
                }
            }
            providerId.startsWith("cs:") -> {
                val idNum = providerId.removePrefix("cs:").toLongOrNull()
                val rawUrl = item.rawUrl?.trim()
                if (idNum != null && idNum > 0L && !rawUrl.isNullOrEmpty()) {
                    LiveSourceRoute(pluginId = providerId, addonType = addonType, contentId = rawUrl, dataUrl = rawUrl)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    fun isLiveRequest(request: StreamRequest): Boolean {
        val addonType = request.addonType?.trim()?.lowercase(Locale.ROOT)
        return request.tmdbId <= 0 &&
            request.type == MediaType.Tv &&
            addonType in liveTypes &&
            !request.pluginId.isNullOrBlank()
    }

    fun mergeChannels(items: List<CatalogItem>): List<MergedLiveChannel> {
        if (items.isEmpty()) return emptyList()
        return items
            .distinctBy { "${it.providerId}:${it.id}" }
            .groupBy { item ->
                channelGroupKey(item.title).ifBlank { "${item.providerId.trim()}:${item.id.trim()}" }
            }
            .map { (key, groupItems) ->
                val title = groupItems.first().title
                val logoUrl = groupItems.firstNotNullOfOrNull { item ->
                    item.posterUrl?.trim()?.takeIf { it.isNotEmpty() }
                }
                val routes = groupItems.mapNotNull(::routeFor).distinct()
                MergedLiveChannel(
                    channelKey = key,
                    title = title,
                    logoUrl = logoUrl,
                    routes = routes,
                    items = groupItems,
                )
            }
    }

    fun isLiveType(type: String?): Boolean = type?.trim()?.lowercase(Locale.ROOT) in liveTypes

    /** Protokol türünü yanlış beyan eden addon'larda yalnız adı/ID'si canlı olduğunu belirten katalogları da dener. */
    fun isLiveCatalog(type: String?, name: String?, id: String): Boolean =
        isLiveType(type) || isKnownLiveChannel(name) || isKnownLiveChannel(id) || listOfNotNull(name, id).any { value ->
            val normalized = value.trim().lowercase(Locale.ROOT)
            liveCatalogMarkers.any(normalized::contains)
        }

    /** Kullanıcı tarafından teyit edilen CloudStream/Nuvio kanal adları; yanlış TV türü etiketini telafi eder. */
    fun isKnownLiveChannel(name: String?): Boolean = name?.let { channelKey(it) in knownChannelNames } == true

    /** Paylaşılan repo/eklenti adları yalnız CloudStream API keşfini genişletir; kanal sınıflandırması yapmaz. */
    fun isKnownLiveProvider(name: String?): Boolean = name?.let { candidate ->
        val key = channelKey(candidate)
        knownLiveProviderNames.any { key == it || key.startsWith(it) }
    } == true

    /** Bölüm adı ne olursa olsun (ör. "Ana sayfa"), içerik türü canlıysa satırı korur. */
    fun onlyLiveRows(rows: List<CatalogRow>): List<CatalogRow> = rows.mapNotNull { row ->
        val liveItems = row.items.filter { isLiveType(it.addonType) || isKnownLiveChannel(it.title) }
        if (liveItems.isEmpty()) null else row.copy(items = liveItems)
    }

    private fun channelKey(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ı', 'i')
        .filter(Char::isLetterOrDigit)
}
