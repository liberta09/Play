package com.kaan.play.data.provider

import com.kaan.play.data.db.LiveCatalogChannelEntity
import com.kaan.play.data.db.LiveCatalogDao
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.builtins.ListSerializer

data class LiveTvCatalogChannel(
    val catalogId: Long,
    val channelKey: String,
    val sortIndex: Int,
    val title: String,
    val logoUrl: String?,
    val routes: List<LiveSourceRoute>,
)

data class LiveTvCatalog(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val channels: List<LiveTvCatalogChannel>,
)

/** Canlı TV kullanıcı listeleri için saf kurallar (Android ve logdan bağımsız). */
object LiveTvCatalogRules {
    private val ROUTES_SERIALIZER = ListSerializer(LiveSourceRoute.serializer())

    fun encodeRoutes(routes: List<LiveSourceRoute>): String =
        PlayJson.encodeToString(ROUTES_SERIALIZER, routes.distinct())

    fun decodeRoutes(routesJson: String): List<LiveSourceRoute> {
        if (routesJson.isBlank()) return emptyList()
        return runCatching { PlayJson.decodeFromString(ROUTES_SERIALIZER, routesJson) }
            .getOrDefault(emptyList())
    }

    /**
     * [channels] listesinde [channelKey] öğesini [delta] (-1 yukarı, +1 aşağı) kaydırır
     * ve sıralı `sortIndex` değerlerini `0..n-1` olarak yeniden numaralandırır.
     */
    fun computeReordered(
        channels: List<LiveCatalogChannelEntity>,
        channelKey: String,
        delta: Int,
    ): List<LiveCatalogChannelEntity>? {
        if (delta == 0 || channels.size <= 1) return null
        val sorted = channels.sortedWith(compareBy<LiveCatalogChannelEntity> { it.sortIndex }.thenBy { it.channelKey })
            .toMutableList()
        val currentIdx = sorted.indexOfFirst { it.channelKey == channelKey }
        val targetIdx = currentIdx + delta
        if (currentIdx < 0 || targetIdx !in sorted.indices) return null

        val moving = sorted[currentIdx]
        sorted[currentIdx] = sorted[targetIdx]
        sorted[targetIdx] = moving
        return sorted.mapIndexed { index, item -> item.copy(sortIndex = index) }
    }

    /** Özel liste oynatımında mevcut canlı rotalar varsa onları önceliklendirir, yoksa kayıtlı rotalara düşer. */
    fun toZapChannels(
        channels: List<LiveTvCatalogChannel>,
        liveRoutesByKey: Map<String, List<LiveSourceRoute>> = emptyMap(),
        liveLogosByKey: Map<String, String?> = emptyMap(),
    ): List<LiveZapChannel> = channels.map { channel ->
        val routes = liveRoutesByKey[channel.channelKey]?.takeIf { it.isNotEmpty() } ?: channel.routes
        val logo = liveLogosByKey[channel.channelKey]?.takeIf { !it.isNullOrBlank() } ?: channel.logoUrl
        LiveZapChannel(
            channelKey = channel.channelKey,
            title = channel.title,
            logoUrl = logo,
            routes = routes,
            isAvailable = routes.isNotEmpty(),
        )
    }

    /** Birincil kayıtlı rotadan oynatma isteği kurmak için hafif [CatalogItem] üretir. */
    fun catalogItemFor(
        channel: LiveTvCatalogChannel,
        catalogName: String,
        preferredRoutes: List<LiveSourceRoute> = channel.routes,
    ): CatalogItem? {
        val route = preferredRoutes.firstOrNull() ?: channel.routes.firstOrNull() ?: return null
        return CatalogItem(
            id = route.contentId,
            title = channel.title,
            subtitle = catalogName,
            posterUrl = channel.logoUrl,
            posterHeaders = null,
            mediaType = MediaType.Tv,
            providerId = route.pluginId,
            rawUrl = route.dataUrl ?: route.contentId,
            addonType = route.addonType,
        )
    }
}

class LiveTvCatalogRepository(
    private val dao: LiveCatalogDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val catalogs: Flow<List<LiveTvCatalog>> = combine(
        dao.observeCatalogs(),
        dao.observeAllChannels(),
    ) { catalogRows, channelRows ->
        val byCatalog = channelRows.groupBy { it.catalogId }
        catalogRows.map { row ->
            val channels = byCatalog[row.id].orEmpty()
                .sortedWith(compareBy<LiveCatalogChannelEntity> { it.sortIndex }.thenBy { it.channelKey })
                .map { entity ->
                    LiveTvCatalogChannel(
                        catalogId = entity.catalogId,
                        channelKey = entity.channelKey,
                        sortIndex = entity.sortIndex,
                        title = entity.title,
                        logoUrl = entity.logoUrl,
                        routes = LiveTvCatalogRules.decodeRoutes(entity.routesJson),
                    )
                }
            LiveTvCatalog(
                id = row.id,
                name = row.name,
                createdAt = row.createdAt,
                channels = channels,
            )
        }
    }

    suspend fun createAndAddChannel(name: String, channel: LiveZapChannel): Long? {
        val clean = name.trim().take(60)
        if (clean.isEmpty() || channel.channelKey.isBlank() || channel.routes.isEmpty()) return null
        val json = LiveTvCatalogRules.encodeRoutes(channel.routes)
        return dao.createCatalogWithChannel(
            name = clean,
            createdAt = clock(),
            channelKey = channel.channelKey,
            title = channel.title,
            logoUrl = channel.logoUrl,
            routesJson = json,
        )
    }

    suspend fun addChannel(catalogId: Long, channel: LiveZapChannel) {
        if (catalogId <= 0 || channel.channelKey.isBlank() || channel.routes.isEmpty()) return
        dao.addChannelAtEnd(
            catalogId = catalogId,
            channelKey = channel.channelKey,
            title = channel.title,
            logoUrl = channel.logoUrl,
            routesJson = LiveTvCatalogRules.encodeRoutes(channel.routes),
        )
    }

    suspend fun removeChannel(catalogId: Long, channelKey: String) {
        if (catalogId <= 0 || channelKey.isBlank()) return
        dao.removeChannel(catalogId, channelKey)
    }

    suspend fun moveChannel(catalogId: Long, channelKey: String, delta: Int) {
        if (catalogId <= 0 || delta == 0) return
        val existing = dao.channelsForCatalog(catalogId)
        val reordered = LiveTvCatalogRules.computeReordered(existing, channelKey, delta) ?: return
        dao.upsertChannels(reordered)
    }

    suspend fun renameCatalog(catalogId: Long, newName: String) {
        val clean = newName.trim().take(60)
        if (catalogId <= 0 || clean.isEmpty()) return
        dao.renameCatalog(catalogId, clean)
    }

    suspend fun deleteCatalog(catalogId: Long) {
        if (catalogId <= 0) return
        dao.deleteCatalog(catalogId)
    }
}
