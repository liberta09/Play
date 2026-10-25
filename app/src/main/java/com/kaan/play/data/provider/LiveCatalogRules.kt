package com.kaan.play.data.provider

import java.util.Locale

/** TV rafında yalnızca eklentinin açıkça canlı kanal olarak işaretlediği öğeleri tutar. */
object LiveCatalogRules {
    private val liveTypes = setOf("tv", "channel")
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
