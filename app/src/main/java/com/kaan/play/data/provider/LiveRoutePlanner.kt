package com.kaan.play.data.provider

import com.kaan.play.data.stream.StreamRequest
import java.util.Locale

/** Canlı TV kanalı alternatif rota planlaması ve oturum içi başarısız rota atlama kuralları. */
object LiveRoutePlanner {

    fun routeKey(route: LiveSourceRoute): String =
        "${route.pluginId.trim()}|${route.addonType.trim().lowercase(Locale.ROOT)}|${route.contentId.trim()}|${route.dataUrl.orEmpty().trim()}"

    /** Tekil bir [StreamRequest] üzerinden canlı kaynak rotası türetir (zap listesi yoksa geri dönüş olarak). */
    fun routeFromRequest(request: StreamRequest): LiveSourceRoute? {
        if (!LiveCatalogRules.isLiveRequest(request)) return null
        val pluginId = request.pluginId?.trim().orEmpty()
        val addonType = request.addonType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val contentId = (request.addonContentId ?: request.dataUrl)?.trim().orEmpty()
        if (pluginId.isEmpty() || contentId.isEmpty()) return null
        return LiveSourceRoute(
            pluginId = pluginId,
            addonType = if (addonType == "channel") "channel" else "tv",
            contentId = contentId,
            dataUrl = request.dataUrl?.trim()?.takeIf { it.isNotEmpty() && pluginId.startsWith("cs:") },
        )
    }

    /**
     * [currentIndex] sonrasından başlayıp [routes] boyunca dönerek [failedKeys] içinde olmayan ilk rotanın indeksini
     * döndürür. Yalnızca tek rota varsa ya da diğer tüm rotalar bu oturumda başarısız olduysa null döner.
     */
    fun nextUntriedRouteIndex(
        routes: List<LiveSourceRoute>,
        currentIndex: Int,
        failedKeys: Set<String>,
    ): Int? {
        if (routes.size <= 1) return null
        val start = if (currentIndex in routes.indices) currentIndex else 0
        for (offset in 1 until routes.size) {
            val candidate = (start + offset) % routes.size
            if (routeKey(routes[candidate]) !in failedKeys) {
                return candidate
            }
        }
        return null
    }
}
